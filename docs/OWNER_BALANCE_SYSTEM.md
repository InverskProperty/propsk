# Owner Balance & Payment Batch System

## Overview

This document describes the property balance ledger system for tracking money owed to property owners, batch payments, and reconciliation with historical payment data.

**Key Insight**: Balances are tracked at the **property level**, not owner level. This matches PayProp's model and supports block property workflows.

---

## The Problem

When paying landlords/owners:
1. **Rent comes in** (e.g., 10 x £1,000 = £10,000)
2. **Expenses are deducted** (e.g., 10 x £100 = £1,000)
3. **Net owed to owner** = £9,000

But actual payments may not match:
- Historical payment of £10,000 (overpaid by £1,000)
- Historical payment of £8,000 (underpaid by £1,000)

We need a way to:
- Track these discrepancies at the **property level**
- Reconcile historical data
- Support block properties that accumulate funds from units
- Manage owner "float" balances

---

## Existing Property Balance Infrastructure

Property entity already has these fields (synced from PayProp):

```java
// Current balance at property level
@Column(name = "account_balance")
private BigDecimal accountBalance;

// Minimum balance to maintain before paying owner
@Column(name = "property_account_minimum_balance")
private BigDecimal propertyAccountMinimumBalance;

// Block property support
@Column(name = "is_block_property")
private Boolean isBlockProperty;  // This property represents a block

@Column(name = "use_block_balance")
private Boolean useBlockBalance;  // Route payments through block

@Column(name = "balance_contribution_percentage")
private BigDecimal balanceContributionPercentage;  // % held as balance
```

**What's missing**: A **ledger** to track how the balance changed over time.

---

## PayProp Balance Structure (Reference)

PayProp tracks beneficiary balances with these fields:

```sql
CREATE TABLE payprop_report_beneficiary_balances (
    beneficiary_payprop_id VARCHAR(50),
    beneficiary_name VARCHAR(255),
    current_balance DECIMAL(15,2),      -- Total balance
    available_balance DECIMAL(15,2),    -- What can be paid out
    held_balance DECIMAL(15,2),         -- What's held back
    pending_balance DECIMAL(15,2),      -- Pending transactions
    property_count INT,
    total_rent_collected DECIMAL(15,2),
    commission_earned DECIMAL(15,2),
    last_payment_amount DECIMAL(15,2),
    last_payment_date DATE
);
```

**Key insight**: `current_balance` vs `available_balance` distinction is exactly what we need.

---

## Current System Components

### 1. BeneficiaryBalance (Existing)
- Tracks what agency **owes** to each owner per property
- Period-based (opening balance → transactions → closing balance)
- Already has: `totalRentAllocated`, `totalExpenses`, `totalPaymentsOut`

### 2. UnifiedAllocation (Existing)
- Individual line items from transactions
- Status: PENDING → BATCHED → PAID
- Links to PaymentBatch via `paymentBatchId`

### 3. PaymentBatch (Existing)
- Groups allocations into a payment
- Has `balanceAdjustment` field (positive or negative)
- Has `adjustmentSource` enum: NONE, BLOCK, OWNER_BALANCE

---

## Balance Model: Property-Centric (Recommended)

Based on PayProp's model and block property requirements, balances are tracked at the **property level**.

### Why Property-Level?

1. **Reconciliation context** - When £1000 owed becomes £900 paid, the £100 belongs to *that property*
2. **Block properties** - Unit properties transfer balances to block property for large payments
3. **PayProp compatibility** - Matches their `account_balance` per property
4. **Audit trail** - Clear which property generated/consumed the funds

### The Model

```
┌─────────────────────────────────────────────────────────────────┐
│  PROPERTY BALANCE LEDGER (per property)                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  123 Main St (Owner: John Smith)                               │
│  ├── Balance: £100                                             │
│  └── Ledger: DEPOSIT +£100 (Nov batch, £1000 owed, £900 paid)  │
│                                                                 │
│  456 Oak Ave (Owner: John Smith)                               │
│  ├── Balance: £200                                             │
│  └── Ledger: DEPOSIT +£200 (Oct batch)                         │
│                                                                 │
│  Maple House Block (Block Property)                            │
│  ├── Balance: £5,000                                           │
│  ├── Ledger: TRANSFER +£500 from Unit 1                        │
│  ├── Ledger: TRANSFER +£500 from Unit 2                        │
│  └── Ledger: WITHDRAW -£3,000 (Building Insurance)             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

BENEFICIARY/OWNER VIEW (Aggregate - calculated or from PayProp)
┌─────────────────────────────────────────────────────────────────┐
│  John Smith                                                     │
│  Total Balance: £300 (sum of 123 Main + 456 Oak)               │
│  Properties: 2                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Block Property Workflow

```
Unit 1 rent: £1,000 → After fees: £800 → Transfer £100 to Block
Unit 2 rent: £1,200 → After fees: £950 → Transfer £150 to Block
Unit 3 rent: £1,100 → After fees: £880 → Transfer £120 to Block
                                         ─────────────────────────
                                         Block Balance: +£370

Later: Pay £5,000 building insurance from Block Balance
```

### Owner Balance (Optional Future Enhancement)

If needed later, we can add a separate owner-level balance for:
- Cross-property transfers
- Owner float not tied to specific property
- But for now, property-level is sufficient

---

## Question 2: Reconciliation Batches

### Standard Batch (Normal Flow)
```
Allocations Selected: £9,000 (PENDING items)
Balance Adjustment:   +£1,000 (deposit to owner balance)
Actual Payment:       £8,000
```

### Reconciliation Batch (Historical)
Same mechanism, but with notes explaining:
```
Batch Type: OWNER_PAYMENT
Source: HISTORICAL_RECONCILIATION (new enum value?)
Notes: "Reconciling Mar 2024 payment - actual was £10,000, calculated was £9,000"
Balance Adjustment: -£1,000 (withdrawal from balance)
```

**Recommendation**: Use standard batches with detailed notes. No need for separate type - the `notes` field explains the reconciliation context.

---

## Proposed Solution: Property Balance Ledger

### New Entity: PropertyBalanceLedger

Tracks every movement in/out of a property's balance:

```java
@Entity
@Table(name = "property_balance_ledger")
public class PropertyBalanceLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // WHICH PROPERTY
    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    @Column(name = "property_name")
    private String propertyName;

    // OWNER (for filtering/reporting)
    @Column(name = "owner_id")
    private Long ownerId;  // Customer ID

    @Column(name = "owner_name")
    private String ownerName;

    // WHAT TYPE OF MOVEMENT
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private EntryType entryType;

    @Column(name = "amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal amount;  // Always positive

    @Column(name = "running_balance", precision = 15, scale = 2, nullable = false)
    private BigDecimal runningBalance;  // Balance after this entry

    // WHY
    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // LINKS
    @Column(name = "payment_batch_id", length = 50)
    private String paymentBatchId;  // Links to PaymentBatch

    @Column(name = "reference", length = 100)
    private String reference;  // External reference (bank ref, etc.)

    // TRANSFER SUPPORT (for block property transfers)
    @Column(name = "related_property_id")
    private Long relatedPropertyId;  // Source/destination for transfers

    @Column(name = "related_property_name")
    private String relatedPropertyName;

    // SOURCE
    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    private Source source;

    // AUDIT
    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    // ENUMS
    public enum EntryType {
        DEPOSIT,           // Money added (paid less than owed)
        WITHDRAWAL,        // Money taken (paid more than owed, or expense)
        TRANSFER_IN,       // Received from another property (block)
        TRANSFER_OUT,      // Sent to another property (to block)
        ADJUSTMENT,        // Manual correction
        OPENING_BALANCE    // Initial balance setup
    }

    public enum Source {
        PAYMENT_BATCH,       // From payment batch process
        BLOCK_TRANSFER,      // Transfer to/from block property
        MANUAL,              // Manual entry by user
        IMPORT,              // CSV/Excel import
        PAYPROP_SYNC,        // Synced from PayProp
        HISTORICAL_RECON     // Historical reconciliation
    }
}
```

### Property.accountBalance (Existing)

The `Property.accountBalance` field already exists and is synced from PayProp. The ledger adds **audit trail** for how the balance changed.

When creating a ledger entry:
1. Record the entry in `property_balance_ledger`
2. Update `Property.accountBalance` to match `runningBalance`

---

## SQL Schema

```sql
-- Property Balance Ledger - tracks all balance movements per property
CREATE TABLE property_balance_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- Which property
    property_id BIGINT NOT NULL,
    property_name VARCHAR(255),

    -- Owner (for filtering/reporting)
    owner_id BIGINT,
    owner_name VARCHAR(255),

    -- What type of movement
    entry_type ENUM('DEPOSIT', 'WITHDRAWAL', 'TRANSFER_IN', 'TRANSFER_OUT', 'ADJUSTMENT', 'OPENING_BALANCE') NOT NULL,
    amount DECIMAL(15,2) NOT NULL,              -- Always positive
    running_balance DECIMAL(15,2) NOT NULL,    -- Balance after this entry

    -- Why
    description VARCHAR(500),
    notes TEXT,

    -- Links
    payment_batch_id VARCHAR(50),               -- Links to payment_batches
    reference VARCHAR(100),                     -- External reference (bank ref)

    -- Transfer support (for block property transfers)
    related_property_id BIGINT,                 -- Source/destination property
    related_property_name VARCHAR(255),

    -- Source
    source ENUM('PAYMENT_BATCH', 'BLOCK_TRANSFER', 'MANUAL', 'IMPORT', 'PAYPROP_SYNC', 'HISTORICAL_RECON'),

    -- Audit
    entry_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,

    -- Indexes
    INDEX idx_property (property_id),
    INDEX idx_owner (owner_id),
    INDEX idx_batch (payment_batch_id),
    INDEX idx_date (entry_date),
    INDEX idx_related_property (related_property_id),

    -- Foreign keys
    FOREIGN KEY (property_id) REFERENCES properties(property_id),
    FOREIGN KEY (owner_id) REFERENCES customers(customer_id),
    FOREIGN KEY (related_property_id) REFERENCES properties(property_id)
);

-- Note: Property.account_balance already exists and stores the current balance
-- The ledger provides the audit trail; Property.account_balance is the "cache"
```

---

## UI Workflow

### 1. Property Balance Dashboard
```
┌──────────────────────────────────────────────────────────────────┐
│  PROPERTY BALANCES                                     [Search] │
├──────────────────────────────────────────────────────────────────┤
│  Property            │ Owner         │ Balance  │ Min    │Action│
├──────────────────────┼───────────────┼──────────┼────────┼──────┤
│  123 Main St         │ John Smith    │ £100     │ £0     │[View]│
│  456 Oak Ave         │ John Smith    │ £200     │ £50    │[View]│
│  Maple House (Block) │ ABC Ltd       │ £5,000   │ £1,000 │[View]│
│  Unit 1, Maple House │ ABC Ltd       │ £0       │ £0     │[View]│
└──────────────────────────────────────────────────────────────────┘

│  [Filter: All | Block Properties Only | With Balance > 0]       │
└──────────────────────────────────────────────────────────────────┘
```

### 2. Property Balance Detail View
```
┌──────────────────────────────────────────────────────────────────┐
│  123 MAIN ST - Balance Details                                  │
│  Owner: John Smith                                              │
├──────────────────────────────────────────────────────────────────┤
│  Current Balance:    £100.00                                    │
│  Minimum Balance:    £0.00                                      │
│  Available:          £100.00                                    │
├──────────────────────────────────────────────────────────────────┤
│  BALANCE HISTORY                                                │
│  ───────────────────────────────────────────────────────────────│
│  Date       │ Type        │ Amount   │ Balance  │ Reference     │
│  ───────────────────────────────────────────────────────────────│
│  29/11/2024 │ DEPOSIT     │ +£100    │ £100     │ OWNER-1129-01│
│             │             │          │          │ £1000 owed,   │
│             │             │          │          │ £900 paid     │
│  15/11/2024 │ WITHDRAWAL  │ -£50     │ £0       │ OWNER-1115-02│
│  01/11/2024 │ DEPOSIT     │ +£50     │ £50      │ Recon import  │
└──────────────────────────────────────────────────────────────────┘
│  [Add Manual Adjustment]  [Transfer to Block]  [Export History] │
└──────────────────────────────────────────────────────────────────┘
```

### 3. Block Property Balance View
```
┌──────────────────────────────────────────────────────────────────┐
│  MAPLE HOUSE BLOCK - Balance Details                            │
│  Type: Block Property                                           │
├──────────────────────────────────────────────────────────────────┤
│  Current Balance:    £5,000.00                                  │
│  Minimum Balance:    £1,000.00                                  │
│  Available:          £4,000.00                                  │
├──────────────────────────────────────────────────────────────────┤
│  CONTRIBUTING UNITS                                             │
│  ───────────────────────────────────────────────────────────────│
│  Unit 1  │ £500 transferred this period                        │
│  Unit 2  │ £500 transferred this period                        │
│  Unit 3  │ £370 transferred this period                        │
├──────────────────────────────────────────────────────────────────┤
│  BALANCE HISTORY                                                │
│  ───────────────────────────────────────────────────────────────│
│  Date       │ Type         │ Amount    │ Balance  │ Reference   │
│  ───────────────────────────────────────────────────────────────│
│  29/11/2024 │ TRANSFER_IN  │ +£500     │ £5,000   │ From Unit 1 │
│  29/11/2024 │ TRANSFER_IN  │ +£500     │ £4,500   │ From Unit 2 │
│  15/11/2024 │ WITHDRAWAL   │ -£3,000   │ £4,000   │ Insurance   │
└──────────────────────────────────────────────────────────────────┘
│  [Pay Building Expense]  [Export History]                       │
└──────────────────────────────────────────────────────────────────┘
```

### 4. Create Payment Batch with Balance Adjustment
```
┌──────────────────────────────────────────────────────────────────┐
│  CREATE PAYMENT BATCH - John Smith                              │
├──────────────────────────────────────────────────────────────────┤
│                                                                 │
│  SELECTED ALLOCATIONS (Nov 2024)                               │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ Property: 123 Main St     Owed: £1,000   Balance: £100     ││
│  │ Property: 456 Oak Ave     Owed: £2,000   Balance: £200     ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                 │
│  CALCULATION                                                    │
│  ─────────────────────────────────────                          │
│  Total Owed (from allocations):    £3,000                       │
│  Total Property Balance Available: £300                         │
│                                                                 │
│  ═══════════════════════════════════════                        │
│  PAYMENT OPTIONS                                                │
│  ─────────────────────────────────────                          │
│                                                                 │
│  Actual Payment Amount:        [£2,700    ] ← editable          │
│                                                                 │
│  BALANCE ADJUSTMENT (auto-calculated)                           │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ 123 Main St: Deposit +£150 to balance (new: £250)          ││
│  │ 456 Oak Ave: Deposit +£150 to balance (new: £350)          ││
│  │ ─────────────────────────────────────                       ││
│  │ Total held back: £300                                       ││
│  │ Final Payment:   £2,700                                     ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                 │
│  OR use existing balance to pay more:                          │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ □ Use £100 from 123 Main St balance                        ││
│  │ □ Use £200 from 456 Oak Ave balance                        ││
│  │   Final Payment: £3,300                                     ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                 │
│  Notes: [Reconciling Nov payment_____________________________ ] │
│                                                                 │
│  [Cancel]                                    [Create Batch]     │
└──────────────────────────────────────────────────────────────────┘
```

---

## Service Layer

### PropertyBalanceService

```java
@Service
public class PropertyBalanceService {

    // ===== BALANCE QUERIES =====

    // Get current balance for property
    BigDecimal getCurrentBalance(Long propertyId);

    // Get available balance (current - minimum threshold)
    BigDecimal getAvailableBalance(Long propertyId);

    // Get total balance across all properties for an owner
    BigDecimal getTotalBalanceForOwner(Long ownerId);

    // ===== BALANCE OPERATIONS =====

    // Deposit to property balance (paid less than owed)
    PropertyBalanceLedger deposit(Long propertyId, BigDecimal amount,
                                  String batchId, String description);

    // Withdraw from property balance (paid more than owed, or expense)
    PropertyBalanceLedger withdraw(Long propertyId, BigDecimal amount,
                                   String batchId, String description);

    // Transfer between properties (for block property workflow)
    void transfer(Long fromPropertyId, Long toPropertyId,
                  BigDecimal amount, String description);

    // Manual adjustment
    PropertyBalanceLedger adjust(Long propertyId, BigDecimal amount,
                                 boolean isCredit, String description, String notes);

    // Set opening balance (for initial setup/reconciliation)
    PropertyBalanceLedger setOpeningBalance(Long propertyId, BigDecimal amount,
                                            LocalDate asOfDate, String notes);

    // ===== HISTORY & REPORTING =====

    // Get ledger history for property
    List<PropertyBalanceLedger> getLedgerHistory(Long propertyId,
                                                  LocalDate from, LocalDate to);

    // Get all ledger entries for an owner across all properties
    List<PropertyBalanceLedger> getLedgerHistoryForOwner(Long ownerId,
                                                          LocalDate from, LocalDate to);

    // ===== VALIDATION =====

    // Check if withdrawal is allowed (respects minimum balance)
    boolean canWithdraw(Long propertyId, BigDecimal amount);

    // Recalculate Property.accountBalance from ledger (for data fixes)
    void recalculateBalance(Long propertyId);
}
```

### BlockBalanceService (Extension)

```java
@Service
public class BlockBalanceService {

    // Transfer contributions from unit properties to block
    void collectUnitContributions(Long blockPropertyId, LocalDate periodEnd);

    // Pay block expense from block balance
    PropertyBalanceLedger payBlockExpense(Long blockPropertyId,
                                          BigDecimal amount, String description);

    // Get summary of unit contributions
    Map<Long, BigDecimal> getUnitContributionSummary(Long blockPropertyId,
                                                      LocalDate from, LocalDate to);
}
```

---

## Integration Points

### 1. PaymentBatchService Update

When creating a batch with balance adjustment per property:

```java
public PaymentBatch createBatchWithBalanceAdjustment(
        List<Long> allocationIds,
        Long beneficiaryId,
        BigDecimal actualPaymentAmount,
        LocalDate paymentDate) {

    // Group allocations by property
    Map<Long, List<UnifiedAllocation>> allocationsByProperty =
        groupAllocationsByProperty(allocationIds);

    BigDecimal totalAllocations = calculateTotalAllocations(allocationIds);
    BigDecimal adjustment = actualPaymentAmount.subtract(totalAllocations);

    PaymentBatch batch = new PaymentBatch();
    batch.setTotalAllocations(totalAllocations);
    batch.setTotalPayment(actualPaymentAmount);

    if (adjustment.compareTo(BigDecimal.ZERO) != 0) {
        // Distribute adjustment proportionally across properties
        for (Map.Entry<Long, List<UnifiedAllocation>> entry : allocationsByProperty.entrySet()) {
            Long propertyId = entry.getKey();
            BigDecimal propertyTotal = sumAllocations(entry.getValue());
            BigDecimal proportion = propertyTotal.divide(totalAllocations, 4, RoundingMode.HALF_UP);
            BigDecimal propertyAdjustment = adjustment.multiply(proportion);

            if (adjustment.compareTo(BigDecimal.ZERO) < 0) {
                // Paying LESS - deposit to each property's balance
                propertyBalanceService.deposit(propertyId, propertyAdjustment.abs(),
                    batch.getBatchId(), "Held from " + batch.getBatchId());
            } else {
                // Paying MORE - withdraw from each property's balance
                propertyBalanceService.withdraw(propertyId, propertyAdjustment,
                    batch.getBatchId(), "Added to " + batch.getBatchId());
            }
        }

        batch.setBalanceAdjustment(adjustment.abs());
        batch.setAdjustmentSource(adjustment.compareTo(BigDecimal.ZERO) < 0
            ? AdjustmentSource.OWNER_BALANCE   // deposit
            : AdjustmentSource.OWNER_BALANCE); // withdrawal
    }

    return paymentBatchRepository.save(batch);
}
```

### 2. Excel Export Update

Add columns:
- `payment_batch_id` - which batch this allocation belongs to
- `property_balance_before` - property balance before this batch
- `property_balance_adjustment` - deposit/withdrawal amount
- `property_balance_after` - property balance after this batch

### 3. PayProp Sync Integration

When syncing from PayProp:
```java
// On PayProp sync, update Property.accountBalance
// Create PAYPROP_SYNC ledger entry if balance changed
if (!oldBalance.equals(newBalance)) {
    BigDecimal diff = newBalance.subtract(oldBalance);
    PropertyBalanceLedger.EntryType type = diff.compareTo(BigDecimal.ZERO) > 0
        ? EntryType.ADJUSTMENT : EntryType.ADJUSTMENT;

    propertyBalanceService.createSyncEntry(propertyId, diff,
        "PayProp sync - balance updated", Source.PAYPROP_SYNC);
}
```

---

## Migration Strategy

### Phase 1: Create Tables
1. Run SQL to create `owner_balance_ledger` and `owner_balance_summary`
2. No data migration yet

### Phase 2: Initialize Balances
1. For each owner, set initial balance to 0
2. OR import from PayProp's `available_balance`

### Phase 3: Historical Reconciliation
1. For each historical payment that doesn't match calculated amount:
   - Create reconciliation ledger entry
   - Update running balance
2. UI to review and approve reconciliation entries

### Phase 4: Go Live
1. Enable balance adjustments in payment batch creation
2. Train accountants on new workflow

---

## Example Scenarios

### Scenario 1: Normal Month (Pay Exactly Owed)

```
Property: 123 Main St
Allocations Owed: £1,000
Actual Payment: £1,000
Balance Adjustment: £0
Property Balance: unchanged (stays at £100)
```

### Scenario 2: Hold Back Some Funds

```
Property: 123 Main St (current balance: £100)
Allocations Owed: £1,000
Actual Payment: £900
Balance Adjustment: DEPOSIT +£100

Ledger Entry:
  Type: DEPOSIT
  Amount: £100
  Description: "Held from batch OWNER-1129-001"
  Running Balance: £200

Property.accountBalance updated: £100 → £200
```

### Scenario 3: Pay Extra from Balance

```
Property: 123 Main St (current balance: £200)
Allocations Owed: £1,000
Actual Payment: £1,100
Balance Adjustment: WITHDRAWAL -£100

Ledger Entry:
  Type: WITHDRAWAL
  Amount: £100
  Description: "Added to batch OWNER-1129-002"
  Running Balance: £100

Property.accountBalance updated: £200 → £100
```

### Scenario 4: Historical Reconciliation

```
Property: 123 Main St
Historical payment found: £900 (March 2024)
Calculated net owed at the time: £1,000
Discrepancy: £100 held at property

Create reconciliation entry:
  Type: OPENING_BALANCE or ADJUSTMENT
  Amount: £100
  Source: HISTORICAL_RECON
  Description: "Mar 2024 reconciliation - £1000 owed, £900 paid"
  Running Balance: £100
```

### Scenario 5: Block Property Transfer

```
Unit 1, Maple House (balance: £500)
  ↓ TRANSFER_OUT £500 to Block
  → Balance: £0

Maple House Block (balance: £4,500)
  ↓ TRANSFER_IN £500 from Unit 1
  → Balance: £5,000

Later: Pay £3,000 insurance from Block
  ↓ WITHDRAWAL £3,000
  → Balance: £2,000
```

### Scenario 6: Multi-Property Payment Batch

```
Owner: John Smith (2 properties)

123 Main St: Owed £1,000, Balance £100
456 Oak Ave: Owed £2,000, Balance £200

Total Owed: £3,000
Actual Payment: £2,700 (holding back £300)

Proportional split:
  123 Main: £1,000/£3,000 = 33.3% → Deposit £100
  456 Oak:  £2,000/£3,000 = 66.7% → Deposit £200

Results:
  123 Main St balance: £100 → £200
  456 Oak Ave balance: £200 → £400
```

---

## Open Questions

1. **How to handle proportional split when holding back funds?**
   - Option A: Split proportionally by owed amount (current design)
   - Option B: Let user specify per-property amounts
   - Option C: All goes to first property in batch

2. **Should we track pending balance (like PayProp)?**
   - Useful for showing payments in transit
   - May add complexity

3. **Block transfer automation?**
   - Automatic transfer on payment batch creation?
   - Or manual "collect contributions" action?

4. **PayProp sync conflict resolution?**
   - When PayProp balance differs from our calculated balance
   - Create adjustment entry? Or trust PayProp as source of truth?

---

## Implementation Checklist

### Phase 1: Core Infrastructure
- [ ] Create `PropertyBalanceLedger` entity
- [ ] Create `PropertyBalanceLedgerRepository`
- [ ] Create `PropertyBalanceService`
- [ ] Add SQL migration for `property_balance_ledger` table

### Phase 2: Integration
- [ ] Update `PaymentBatchService` for balance operations
- [ ] Update `UnifiedAllocationService` to track property context
- [ ] Add PayProp sync hooks for balance changes

### Phase 3: UI
- [ ] Property Balance Dashboard (list all properties with balances)
- [ ] Property Balance Detail View (ledger history)
- [ ] Block Property Balance View (with unit contributions)
- [ ] Update batch creation form with balance adjustment UI

### Phase 4: Historical Reconciliation
- [ ] Tool to import/set opening balances
- [ ] Reconciliation wizard for historical payment matching
- [ ] Bulk adjustment import from CSV

### Phase 5: Excel/Reporting
- [ ] Add balance columns to existing exports
- [ ] Property balance report
- [ ] Owner balance summary report (aggregate)

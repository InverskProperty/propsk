# Prestvale Properties Limited Rename Changelog

**Date**: 2026-01-07
**Purpose**: Rename owner "Udayan Bhardwaj" to "Prestvale Properties Limited"

## Changes Made

### 1. Customer Table (customers)
```sql
UPDATE customers SET name = 'Prestvale Properties Limited' WHERE customer_id = 68;
-- Changed from: "Udayan Bhardwaj"
-- Changed to: "Prestvale Properties Limited"
-- Records affected: 1
```

### 2. Beneficiaries Table (beneficiaries)
```sql
UPDATE beneficiaries
SET name = 'Prestvale Properties Limited',
    business_name = 'Prestvale Properties Limited'
WHERE pay_prop_beneficiary_id = 'v2XlA6ob1e';
-- ID: 255
-- Changed from: name="Udayan Bhardwaj", business_name="Udayan Bhardwaj"
-- Changed to: name="Prestvale Properties Limited", business_name="Prestvale Properties Limited"
-- Records affected: 1
```

### 3. Unified Allocations (unified_allocations)
```sql
UPDATE unified_allocations
SET beneficiary_name = 'Prestvale Properties Limited'
WHERE beneficiary_id = 68;
-- Changed from: beneficiary_name="Udayan Bhardwaj"
-- Changed to: beneficiary_name="Prestvale Properties Limited"
-- Records affected: 300
```

### 4. Payment Batches (payment_batches)
```sql
UPDATE payment_batches
SET beneficiary_name = 'Prestvale Properties Limited'
WHERE beneficiary_id = 68;
-- Changed from: beneficiary_name="Udayan Bhardwaj"
-- Changed to: beneficiary_name="Prestvale Properties Limited"
-- Records affected: 26
```

### 5. Historical Transactions (historical_transactions)
```sql
UPDATE historical_transactions
SET beneficiary_name = 'Prestvale Properties Limited'
WHERE owner_id = 68;
-- Changed from: beneficiary_name="Udayan Bhardwaj"
-- Changed to: beneficiary_name="Prestvale Properties Limited"
-- Records affected: 191
```

### 6. Delegated User Name Cleanup (customers)
```sql
UPDATE customers SET name = 'Achal'
WHERE customer_id = 79 AND name = 'Achal (Delegated for Udayan)';
-- Changed from: "Achal (Delegated for Udayan)"
-- Changed to: "Achal"
-- Records affected: 1
```

## Backup Tables Created

The following backup tables were created with the original data:

| Backup Table | Source Table | Records |
|--------------|--------------|---------|
| `customers_backup_prestvale_rename` | customers | 5 (IDs: 68, 79, 100, 102, 111) |
| `beneficiaries_backup_prestvale_rename` | beneficiaries | 1 (ID: 255) |
| `unified_allocations_backup_prestvale_rename` | unified_allocations | 300 |
| `payment_batches_backup_prestvale_rename` | payment_batches | 26 |
| `historical_transactions_backup_prestvale_rename` | historical_transactions | 191 |

## Rollback Instructions

If needed, rollback with:

```sql
-- Rollback customer name
UPDATE customers c
JOIN customers_backup_prestvale_rename b ON c.customer_id = b.customer_id
SET c.name = b.name;

-- Rollback beneficiary
UPDATE beneficiaries ben
JOIN beneficiaries_backup_prestvale_rename b ON ben.id = b.id
SET ben.name = b.name, ben.business_name = b.business_name;

-- Rollback unified_allocations
UPDATE unified_allocations ua
JOIN unified_allocations_backup_prestvale_rename b ON ua.id = b.id
SET ua.beneficiary_name = b.beneficiary_name;

-- Rollback payment_batches
UPDATE payment_batches pb
JOIN payment_batches_backup_prestvale_rename b ON pb.id = b.id
SET pb.beneficiary_name = b.beneficiary_name;

-- Rollback historical_transactions
UPDATE historical_transactions ht
JOIN historical_transactions_backup_prestvale_rename b ON ht.id = b.id
SET ht.beneficiary_name = b.beneficiary_name;
```

### 7. Additional Payment Batch Fix
```sql
UPDATE payment_batches SET beneficiary_name = 'Prestvale Properties Limited'
WHERE id = 144 AND beneficiary_name = 'Udayan Bhardwaj';
-- This batch had beneficiary_id=NULL so wasn't caught by initial update
-- Records affected: 1
```

### 8. UI Fix - Hardcoded Name in Homepage
```
File: src/main/resources/templates/index.html
Line: 510
Changed from: "Portfolio updates sent to Rama Talluri & Udayan Bhardwaj"
Changed to: "Portfolio updates sent to property owners"
```

## Related IDs

- **Customer ID**: 68 (PROPERTY_OWNER - renamed to Prestvale Properties Limited)
- **Customer ID**: 106 (TENANT - kept as "Udayan Bhardwaj" - actual tenant record)
- **User ID**: 62 (sajidkazmi - admin account)
- **Beneficiary ID**: 255
- **PayProp Beneficiary ID**: v2XlA6ob1e
- **Delegated Users**: 79 (Achal), 100 (Vijay), 102 (Piyush), 111 (Harshada)

## Tables NOT Updated (Raw PayProp Data)

These tables contain raw PayProp import data and will be updated automatically on next PayProp sync:
- `payprop_export_payments` - 33 records with "Udayan Bhardwaj [B]"
- `payprop_report_all_payments` - 133 records with "Udayan Bhardwaj"

## Pending Actions

1. Run PayProp sync to update raw import tables and verify no conflicts
2. Create new Udayan Bhardwaj as DELEGATED_USER when email is available
3. Verify UI displays correctly

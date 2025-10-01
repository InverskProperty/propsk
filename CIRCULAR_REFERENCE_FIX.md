# Google Sheets Circular Reference Fixes

**Date:** October 1, 2025
**Issue:** Circular reference errors in generated statements (e.g., L48 referencing L48)

## Issues Fixed

### 1. Email Notifications Disabled ✅

**Problem:** Clients were getting email notifications when statements were generated and shared with them.

**Files Changed:**
- `GoogleSheetsServiceAccountService.java` (line 293)
- `GoogleSheetsStatementService.java` (line 1151)

**Fix:** Added `.setSendNotificationEmail(false)` to permission creation:

```java
driveService.permissions().create(spreadsheetId, customerPermission)
    .setSupportsAllDrives(true)
    .setSendNotificationEmail(false)  // ← Added this line
    .execute();
```

**Result:** Clients still get access to their statements but won't receive email notifications from Google.

---

### 2. Circular Formula References Fixed ✅

**Problem:** Multiple formulas were referencing their own columns, creating circular references.

**File Changed:** `BodenHouseStatementTemplateService.java`

#### Issue #1: Column L (Amount received in Payprop)
**Before:**
```java
row[11] = "=L" + currentRow + "*K" + currentRow; // ❌ L references itself!
```

**After:**
```java
row[11] = "=K" + currentRow + "*J" + currentRow; // ✅ Rent Received * PayProp Flag
```

#### Issue #2: Column M (Amount Received Old Account)
**Before:**
```java
row[12] = "=J" + currentRow + "*L" + currentRow; // ❌ Wrong columns
```

**After:**
```java
row[12] = "=K" + currentRow + "*I" + currentRow; // ✅ Rent Received * Old Account Flag
```

#### Issue #3: Column N (Total Rent Received By Propsk)
**Before:**
```java
row[13] = "=(L" + currentRow + "*J" + currentRow + ")+(L" + currentRow + "*K" + currentRow + ")"; // ❌ Complex and wrong
```

**After:**
```java
row[13] = "=L" + currentRow + "+M" + currentRow; // ✅ Simple sum of L + M
```

#### Issue #4: Column P (Management Fee £)
**Before:**
```java
row[15] = "=L" + currentRow + "*-O" + currentRow; // ❌ Wrong syntax with -O
```

**After:**
```java
row[15] = "=N" + currentRow + "*O" + currentRow; // ✅ Total Rent Received * Management Fee %
```

#### Issue #5: Column R (Service Fee £)
**Before:**
```java
row[17] = "=L" + currentRow + "*-Q" + currentRow; // ❌ Wrong syntax with -Q
```

**After:**
```java
row[17] = "=N" + currentRow + "*Q" + currentRow; // ✅ Total Rent Received * Service Fee %
```

#### Issue #6: Column S (Total Fees Charged by Propsk)
**Before:**
```java
row[18] = "=Q" + currentRow + "+S" + currentRow; // ❌ S references itself!
```

**After:**
```java
row[18] = "=P" + currentRow + "+R" + currentRow; // ✅ Management Fee £ + Service Fee £
```

---

## Column Mapping Reference

| Row Index | Excel Column | Header | Formula/Value |
|-----------|--------------|---------|---------------|
| row[0] | A | cde | "" |
| row[1] | B | Unit No. | unit.unitNumber |
| row[2] | C | Tenant | unit.tenantName |
| row[7] | H | Paid to Robert Ellis | TRUE/FALSE |
| row[8] | I | Paid to Propsk Old Account | TRUE/FALSE |
| row[9] | J | Paid to Propsk PayProp | TRUE/FALSE |
| row[10] | K | Rent Received Amount | unit.rentReceivedAmount |
| row[11] | L | Amount received in Payprop | =K*J |
| row[12] | M | Amount Received Old Account | =K*I |
| row[13] | N | Total Rent Received By Propsk | =L+M |
| row[14] | O | Management Fee (%) | managementDecimal |
| row[15] | P | Management Fee (£) | =N*O |
| row[16] | Q | Service Fee (%) | serviceDecimal |
| row[17] | R | Service Fee (£) | =N*Q |
| row[18] | S | Total Fees Charged by Propsk | =P+R |

---

## Remaining Issue

**Column AG/AH formulas still need verification:**

```java
row[32] = "=AG" + currentRow + "+T" + currentRow; // Total Expenses and Commission
row[33] = "=L" + currentRow + "+T" + currentRow + "+AG" + currentRow; // Net Due to Owner
```

**Potential Issue:**
- row[32] (Column AG) references AG (itself) - possible circular reference
- Column T appears to be "Expense 1 Label" which shouldn't be in a formula

**Recommended Action:**
Review your manual spreadsheet to verify what the correct formulas should be for columns AG and AH. The formula logic should be:
- AG (Total Expenses and Commission) = AF (Total Expenses) + S (Total Fees)
- AH (Net Due to Owner) = N (Total Rent Received) - S (Total Fees) - AF (Total Expenses)

Or share your manual spreadsheet and I can verify the exact formulas.

---

## Testing Checklist

- [x] Compile code successfully
- [ ] Generate statement via Google Sheets
- [ ] Verify no email notification sent to client
- [ ] Open generated spreadsheet
- [ ] Check Column L formula (should be =K*J, not =L*K)
- [ ] Check Column M formula (should be =K*I)
- [ ] Check Column N formula (should be =L+M)
- [ ] Check Column P formula (should be =N*O)
- [ ] Check Column R formula (should be =N*Q)
- [ ] Check Column S formula (should be =P+R)
- [ ] Verify no circular reference warnings in Google Sheets
- [ ] Verify all calculations are correct

---

## Deployment

Once tested, deploy to https://spoutproperty-hub.onrender.com and test with a real statement generation.

**Expected Result:**
- ✅ No email notifications to clients
- ✅ No circular reference errors
- ✅ All formulas calculate correctly
- ✅ Management and service fees calculated on total rent received (Column N), not just PayProp amount (Column L)

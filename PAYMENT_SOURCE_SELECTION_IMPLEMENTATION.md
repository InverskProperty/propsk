# Payment Source Selection Implementation

**Date:** October 1, 2025
**Status:** ✅ Complete - Ready for Testing

## Overview

Implemented dynamic payment source selection for statement generation at https://spoutproperty-hub.onrender.com/statements, replacing hardcoded source values with database-driven account source filtering.

## Problem Statement

The statement generation page had hardcoded payment sources (e.g., "2=Propsk Old Account", "3=Propsk PayProp Account") and needed a redesign to:
1. Dynamically select payment sources in the pre-generation stage
2. Filter transactions by `account_source` field in `historical_transactions` table
3. Support both XLSX and Google Sheets output formats

## Solution Architecture

### 1. Database Layer
- **Table:** `historical_transactions`
- **Field:** `account_source` (VARCHAR 50)
- **Values:** "2", "3", "PAYPROP_API", "LOCAL_CRM", "CSV_IMPORT", "ROBERT_ELLIS"

### 2. Backend Components

#### A. StatementDataSource Enum (`StatementDataSource.java`)
```java
public enum StatementDataSource {
    PROPSK_OLD_ACCOUNT("Propsk Old Account", "PROPSK_OLD_ACCOUNT", "...", "2"),
    PROPSK_PAYPROP_ACCOUNT("Propsk PayProp Account", "PROPSK_PAYPROP_ACCOUNT", "...", "3"),
    PAYPROP_API_SYNC("PayProp API Live Data", "PAYPROP_API_SYNC", "...", "PAYPROP_API"),
    LOCAL_CRM_MANUAL("Local CRM Manual Entry", "LOCAL_CRM_MANUAL", "...", "LOCAL_CRM"),
    CSV_IMPORT("CSV Import", "CSV_IMPORT", "...", "CSV_IMPORT"),
    ROBERT_ELLIS("Robert Ellis Historical", "ROBERT_ELLIS", "...", "ROBERT_ELLIS");
}
```

**Key Methods:**
- `matchesAccountSource(String accountSource)` - Direct account_source matching
- `fromAccountSource(String accountSource)` - Parse account_source to enum
- `isHistorical()` - Check if historical source
- `isLive()` - Check if live/current source

#### B. BodenHouseStatementTemplateService Updates
**Added:**
- `HistoricalTransactionRepository` dependency
- `filterHistoricalTransactionsByAccountSource()` - Filter by account_source
- `isHistoricalTransactionIncluded()` - Check if transaction matches sources

#### C. StatementController Endpoints

**New Endpoint:** `/statements/property-owner/with-sources`
- **Method:** POST
- **Parameters:**
  - `propertyOwnerId` (Integer, required)
  - `fromDate` (LocalDate, required)
  - `toDate` (LocalDate, required)
  - `accountSources` (List<String>, optional) - Selected source enum names
  - `outputFormat` (String, default: "GOOGLE_SHEETS") - "XLSX" or "GOOGLE_SHEETS"

**API Endpoint:** `/statements/api/account-sources`
- **Method:** GET
- **Returns:** JSON array of available account sources
```json
[
  {
    "key": "PROPSK_OLD_ACCOUNT",
    "displayName": "Propsk Old Account",
    "description": "Historical Propsk bank account (pre-PayProp)",
    "accountSourceValue": "2",
    "isHistorical": "true",
    "isLive": "false"
  },
  ...
]
```

### 3. Frontend Components

#### Updated Form (`generate-statement.html`)
**Property Owner Statement Form:**
- Action changed from `/statements/property-owner` → `/statements/property-owner/with-sources`
- Added Payment Sources checkbox section
- Added hidden `outputFormat` input field
- Updated button handlers to set output format

**UI Features:**
- Scrollable checkbox container (max 150px height)
- "Select All Sources" checkbox
- Grouped by Historical/Live sources
- Shows source description below each option
- Default: All unchecked = include all sources

**JavaScript Functions:**
- `loadAccountSources()` - Fetch sources from API
- `populateAccountSourceCheckboxes()` - Render checkboxes
- `createSourceCheckbox()` - Generate checkbox HTML
- `toggleAllSources()` - Select/deselect all
- `setOutputFormatAndSubmit()` - Set format and submit

## Usage

### For End Users

1. **Navigate to:** https://spoutproperty-hub.onrender.com/statements
2. **Select Property Owner** from dropdown
3. **Choose Date Range** (From/To dates)
4. **Select Payment Sources:**
   - Leave all unchecked to include ALL sources
   - Check specific sources to filter (e.g., only "Propsk PayProp Account")
   - Use "Select All Sources" for convenience
5. **Choose Output Format:**
   - Click "Download XLSX" for Excel file
   - Click "Google Sheets" for online spreadsheet

### For Developers

**Example: Generate statement with only PayProp sources**
```javascript
// Frontend
const form = document.getElementById('propertyOwnerForm');
// User checks: PROPSK_PAYPROP_ACCOUNT, PAYPROP_API_SYNC
form.submit(); // Sends: accountSources=["PROPSK_PAYPROP_ACCOUNT", "PAYPROP_API_SYNC"]
```

**Backend Processing:**
```java
// Controller parses sources
Set<StatementDataSource> includedSources = parseAccountSources(accountSourceNames);
// → {PROPSK_PAYPROP_ACCOUNT, PAYPROP_API_SYNC}

// Service filters transactions
List<HistoricalTransaction> filtered = filterHistoricalTransactionsByAccountSource(
    allTransactions,
    includedSources
);
// → Only transactions where account_source IN ("3", "PAYPROP_API")
```

## Benefits

1. **No Hardcoding:** All sources dynamically loaded from enum
2. **Database-Driven:** Direct mapping to `account_source` field
3. **Flexible Filtering:** Users can mix historical and live sources
4. **Multiple Formats:** Same filtering works for XLSX and Google Sheets
5. **User-Friendly:** Clear labels and descriptions for each source
6. **Maintainable:** Add new sources by updating enum only

## Files Changed

### Backend
1. `StatementDataSource.java` - Updated enum with account_source mapping
2. `BodenHouseStatementTemplateService.java` - Added HistoricalTransactionRepository filtering
3. `StatementController.java` - Added source selection endpoints
4. `StatementGenerationRequest.java` - Fixed helper methods for new enum

### Frontend
1. `generate-statement.html` - Added payment source checkboxes and JavaScript

## Testing Checklist

- [ ] Load https://spoutproperty-hub.onrender.com/statements
- [ ] Verify account sources load in checkbox list
- [ ] Test "Select All Sources" checkbox
- [ ] Generate statement with no sources selected (should include all)
- [ ] Generate statement with only "Propsk Old Account" selected
- [ ] Generate statement with only "Propsk PayProp Account" selected
- [ ] Generate statement with multiple sources selected
- [ ] Test both XLSX download and Google Sheets generation
- [ ] Verify filtered data matches selected sources
- [ ] Check success/error messages display correctly

## Next Steps

1. **Test with real data** - Verify transactions filter correctly
2. **Add to other statement types** - Portfolio and Tenant statements
3. **Add source statistics** - Show transaction count per source
4. **Add date range validation** - Warn if no data in selected range
5. **Add audit logging** - Track which sources were used for each statement

## Notes

- Empty selection (no checkboxes checked) = include ALL sources
- This maintains backwards compatibility with existing statements
- The system uses `HistoricalTransactionRepository` for unified transaction access
- Legacy `FinancialTransaction` filtering still exists for backward compatibility

## Support

For questions or issues, contact the development team or file an issue in the repository.

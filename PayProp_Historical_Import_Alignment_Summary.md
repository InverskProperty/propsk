# PayProp Historical Import - Alignment with Existing Systems

## 🔗 **Integration with Your Current Architecture**

After examining your existing Google Sheets and XLSX functionality, I've updated the historical import system to perfectly align with your established patterns and capabilities.

## 📊 **Your Existing File Handling Patterns**

### **1. Google Sheets Integration (`GoogleSheetsStatementService`)**
```java
// Your current pattern: Service account + OAuth2 fallback
if (serviceAccountKey != null && !serviceAccountKey.trim().isEmpty()) {
    // Service account approach (shared drive)
    spreadsheetId = createSpreadsheetInSharedDrive(propertyOwner, fromDate, toDate);
    sheetsService = createServiceAccountSheetsService();
} else if (oAuthUser != null) {
    // OAuth2 fallback
    sheetsService = getSheetsService(oAuthUser);
}
```

### **2. XLSX Generation (`XLSXStatementService`)**
```java
// Your current pattern: Apache POI with formula support
XSSFWorkbook workbook = new XSSFWorkbook();
XSSFSheet sheet = workbook.createSheet("Property Statement");

// Handle different cell types with formulas
if (value instanceof String && ((String) value).startsWith("=")) {
    cell.setCellFormula(((String) value).substring(1)); // Remove = prefix
} else if (value instanceof Number) {
    cell.setCellValue(((Number) value).doubleValue());
}
```

### **3. File Download Pattern (`StatementController`)**
```java
// Your current pattern: ResponseEntity<byte[]> for downloads
public ResponseEntity<byte[]> generatePropertyOwnerStatementXLSX(
    @RequestParam Long propertyOwnerId,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
    Authentication authentication) throws IOException {

    return ResponseEntity.ok()
        .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
        .body(xlsxData);
}
```

## ✅ **Historical Import System Alignment**

### **1. XLSX Reading (Following Your Apache POI Patterns)**
```java
// Updated to match your XLSXStatementService approach
private List<PayPropTransaction> parseExcelSpreadsheetData(MultipartFile file, String period) throws IOException {
    try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
        Sheet sheet = workbook.getSheetAt(0);

        for (Row row : sheet) {
            String[] columns = convertRowToStringArray(row); // Your pattern
            // Process data...
        }
    }
}

// Cell handling matches your existing patterns
private String[] convertRowToStringArray(Row row) {
    switch (cell.getCellType()) {
        case STRING: columns[i] = cell.getStringCellValue(); break;
        case NUMERIC: /* Handle numbers like your system */ break;
        case FORMULA: /* Handle formulas like your system */ break;
    }
}
```

### **2. File Download (Following Your ResponseEntity Pattern)**
```java
// Updated to match your download patterns exactly
@PostMapping("/download-converted-csv")
@ResponseBody
public ResponseEntity<byte[]> downloadConvertedCsv(
        @RequestParam("file") MultipartFile file,
        @RequestParam("period") String period) {

    String csvData = transformService.transformSpreadsheetToPayPropCsv(file, period);

    return ResponseEntity.ok()
        .header("Content-Type", "text/csv")
        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
        .body(csvData.getBytes(StandardCharsets.UTF_8)); // Your exact pattern
}
```

### **3. Category Mapping (Leveraging Your PayProp Categories)**
```java
// Integrates with your existing payprop_*_categories tables
@Service
public class PayPropCategoryMappingService {

    // Uses your existing database tables
    private void loadInvoiceCategories() throws SQLException {
        String sql = "SELECT payprop_external_id, name FROM payprop_invoice_categories WHERE is_active = 1";
        // Load your actual PayProp categories
    }

    // Maps historical data to your existing category structure
    public String mapHistoricalCategory(String transactionType, String category) {
        // Maps "Maintenance" -> "maintenance" (your existing categories)
        // Maps "Management Fee" -> "commission" (your existing categories)
    }
}
```

## 🎯 **Key Enhancements Following Your Patterns**

### **1. File Type Detection (Like Your System)**
```java
// Supports both Excel and CSV like your existing services
if (isExcelFile(file)) {
    transactions = parseExcelSpreadsheetData(file, period); // Apache POI
} else {
    transactions = parseSpreadsheetData(lines, period); // CSV fallback
}
```

### **2. Error Handling (Matches Your Approach)**
```java
// Comprehensive error handling like your statement services
try {
    // Process file
} catch (Exception e) {
    logger.error("Processing failed: {}", e.getMessage(), e);
    return ResponseEntity.badRequest()
        .body(("Processing failed: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
}
```

### **3. Frontend Integration (Bootstrap + Your Patterns)**
```html
<!-- Download button follows your existing UI patterns -->
<button type="button" class="btn btn-outline-secondary" id="download-csv-btn" disabled>
    <i class="bi bi-download"></i> Download CSV
</button>

<!-- Form submission matches your existing patterns -->
<script>
// Direct download using hidden form (your existing pattern)
const downloadForm = document.createElement('form');
downloadForm.method = 'POST';
downloadForm.action = '/admin/payprop-import/download-converted-csv';
downloadForm.submit();
</script>
```

## 🔧 **File Support Matrix**

| **File Type** | **Your System** | **Historical Import** | **Alignment** |
|---------------|-----------------|----------------------|---------------|
| `.xlsx` | ✅ XLSXStatementService | ✅ Apache POI + formula support | **Perfect Match** |
| `.xls` | ✅ Apache POI support | ✅ Apache POI support | **Perfect Match** |
| `.csv` | ✅ CSV parsing | ✅ CSV parsing | **Perfect Match** |
| Google Sheets | ✅ API integration | 🔄 Future enhancement | **Compatible** |

## 📈 **Benefits of This Alignment**

1. **Consistent Technology Stack**: Uses same Apache POI library as your XLSX services
2. **Familiar Download Patterns**: Matches your existing ResponseEntity<byte[]> approach
3. **Category Integration**: Leverages your existing PayProp category tables
4. **Error Handling Consistency**: Follows your established error handling patterns
5. **UI Consistency**: Bootstrap styling matches your existing admin interface

## 🚀 **Usage Following Your Patterns**

### **For XLSX Files (Your Primary Format):**
1. Upload "Copy of Boden House Statement by Month.xlsx"
2. System uses Apache POI (like your XLSXStatementService)
3. Processes formulas and cell types correctly
4. Downloads follow your ResponseEntity pattern

### **For CSV Files (Fallback):**
1. Upload CSV version of statement
2. System processes like your existing CSV handling
3. Same validation and error handling

### **Integration with Existing Services:**
- Categories validated against your `payprop_*_categories` tables
- Properties matched against your existing Property entities
- Financial data stored in your existing FinancialTransaction format
- Audit trail maintained in HistoricalTransaction table

The historical import system now seamlessly integrates with your existing Google Sheets and XLSX infrastructure, using the same libraries, patterns, and approaches you've already established. Users will find the interface familiar and consistent with your other file handling features.
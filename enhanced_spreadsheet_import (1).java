package site.easy.to.build.crm.service.payprop;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Enhanced service to import ALL data from your corrected monthly statements
 * Including: Rent, Management Fees, Expenses, AND Owner Payments
 */
@Service
public class EnhancedSpreadsheetImportService {
    
    private static final Logger logger = LoggerFactory.getLogger(EnhancedSpreadsheetImportService.class);
    
    @Autowired
    private FinancialTransactionService financialTransactionService;
    
    @Autowired
    private PropertyService propertyService;
    
    /**
     * Import complete monthly statement with all transaction types
     */
    public ImportResult importCompleteMonthlyStatement(MultipartFile file, String period) throws IOException {
        logger.info("📊 Starting COMPLETE import of monthly statement: {}", file.getOriginalFilename());
        
        ImportResult result = new ImportResult();
        result.batchId = UUID.randomUUID().toString();
        
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            
            // Process each monthly sheet
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = sheet.getSheetName();
                
                // Skip the REQUESTED summary sheet
                if (sheetName.equals("REQUESTED")) {
                    continue;
                }
                
                logger.info("Processing sheet: {}", sheetName);
                
                // Extract month/year from sheet name (e.g., "February Propsk Statement")
                LocalDate periodDate = parsePeriodFromSheetName(sheetName, period);
                
                // Process all data from this sheet
                processMonthlySheet(sheet, periodDate, result);
            }
            
            // Also process REQUESTED sheet for owner payments if needed
            Sheet requestedSheet = workbook.getSheet("REQUESTED");
            if (requestedSheet != null) {
                processOwnerPaymentsFromSummary(requestedSheet, result);
            }
            
        } catch (Exception e) {
            logger.error("Import failed: {}", e.getMessage(), e);
            result.success = false;
            result.errors.add("Import failed: " + e.getMessage());
        }
        
        result.success = result.errors.isEmpty();
        logger.info("✅ Import complete: {} transactions, {} errors", 
                   result.successfulImports, result.errors.size());
        
        return result;
    }
    
    /**
     * Process a single monthly sheet extracting ALL transaction types
     */
    private void processMonthlySheet(Sheet sheet, LocalDate periodDate, ImportResult result) {
        
        // Find header row (usually row 3, index 2)
        Row headerRow = sheet.getRow(2);
        if (headerRow == null) {
            result.errors.add("No header row found in sheet: " + sheet.getSheetName());
            return;
        }
        
        // Map column indices
        Map<String, Integer> columnMap = mapColumns(headerRow);
        
        // Process each data row (starting from row 4, index 3)
        for (int rowNum = 3; rowNum <= sheet.getLastRowNum(); rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null || isEmptyRow(row)) continue;
            
            try {
                // Skip section headers and totals
                String firstCellValue = getCellStringValue(row.getCell(0));
                if (firstCellValue.contains("TOTAL") || firstCellValue.contains("PROPERTY:")) {
                    continue;
                }
                
                // Extract all data from this row
                processDataRow(row, columnMap, periodDate, result);
                
            } catch (Exception e) {
                logger.error("Error processing row {}: {}", rowNum + 1, e.getMessage());
                result.errors.add("Row " + (rowNum + 1) + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Process a single data row extracting all transaction types
     */
    private void processDataRow(Row row, Map<String, Integer> columnMap, 
                                LocalDate periodDate, ImportResult result) {
        
        // 1. Extract basic property/tenant info
        String unitNo = getCellValue(row, columnMap, "Unit No.");
        String tenant = getCellValue(row, columnMap, "Tenant");
        String propertyRef = buildPropertyReference(unitNo);
        
        // 2. Extract rent transactions
        BigDecimal rentDue = getCellBigDecimal(row, columnMap, "Rent Due Amount");
        if (rentDue != null && rentDue.compareTo(BigDecimal.ZERO) > 0) {
            createTransaction("RENT_DUE", rentDue, propertyRef, tenant, periodDate, result);
        }
        
        BigDecimal rentReceived = getCellBigDecimal(row, columnMap, "Amount Received Old Account");
        if (rentReceived != null && rentReceived.compareTo(BigDecimal.ZERO) > 0) {
            createTransaction("RENT_RECEIVED", rentReceived, propertyRef, tenant, periodDate, result);
        }
        
        // 3. Extract management fees
        BigDecimal mgmtFeeOld = getCellBigDecimal(row, columnMap, "Management Fee Propsk Old Account Amount");
        if (mgmtFeeOld != null && mgmtFeeOld.compareTo(BigDecimal.ZERO) != 0) {
            createTransaction("MANAGEMENT_FEE", mgmtFeeOld.abs(), propertyRef, tenant, periodDate, result);
        }
        
        BigDecimal mgmtFeePayProp = getCellBigDecimal(row, columnMap, "Management Fee Propsk Payprop Account Amount");
        if (mgmtFeePayProp != null && mgmtFeePayProp.compareTo(BigDecimal.ZERO) != 0) {
            createTransaction("MANAGEMENT_FEE_PAYPROP", mgmtFeePayProp.abs(), propertyRef, tenant, periodDate, result);
        }
        
        // 4. Extract expenses (4 possible expense columns)
        for (int i = 1; i <= 4; i++) {
            String expenseLabel = getCellValue(row, columnMap, "Expense " + i + " Label");
            BigDecimal expenseAmount = getCellBigDecimal(row, columnMap, "Expense " + i + " Amount");
            
            if (expenseAmount != null && expenseAmount.compareTo(BigDecimal.ZERO) != 0) {
                createExpenseTransaction(expenseLabel, expenseAmount.abs(), propertyRef, periodDate, result);
            }
        }
    }
    
    /**
     * Process owner payments from the REQUESTED summary sheet
     */
    private void processOwnerPaymentsFromSummary(Sheet sheet, ImportResult result) {
        logger.info("💰 Processing owner payments from REQUESTED sheet");
        
        // Owner payments are in specific rows (around rows 44-55 for Old Account, 51-55 for PayProp)
        
        // Find the "Payments From Propsk Old Account" section
        int oldAccountStartRow = findRowByContent(sheet, "Payments From Propsk Old Account");
        if (oldAccountStartRow > 0) {
            processOwnerPaymentSection(sheet, oldAccountStartRow, "OLD_ACCOUNT", result);
        }
        
        // Find the "Payments From Propsk PayProp Account" section  
        int payPropStartRow = findRowByContent(sheet, "Payments From Propsk PayProp Account");
        if (payPropStartRow > 0) {
            processOwnerPaymentSection(sheet, payPropStartRow, "PAYPROP_ACCOUNT", result);
        }
    }
    
    /**
     * Process owner payment section (Payment 1-4)
     */
    private void processOwnerPaymentSection(Sheet sheet, int startRow, String accountType, ImportResult result) {
        
        // Process Payment 1-4 (next 4 rows after header)
        for (int i = 1; i <= 4; i++) {
            Row paymentRow = sheet.getRow(startRow + i);
            if (paymentRow == null) continue;
            
            // Process each month's payment (columns F onwards)
            for (int col = 5; col < 20; col++) { // F=5, check up to column T
                Cell amountCell = paymentRow.getCell(col);
                if (amountCell == null) continue;
                
                BigDecimal amount = getCellBigDecimalValue(amountCell);
                if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                    
                    // Get month from header row (row 3)
                    Row monthRow = sheet.getRow(2);
                    Cell monthCell = monthRow.getCell(col);
                    String month = getCellStringValue(monthCell);
                    
                    // Create owner payment transaction
                    String description = String.format("Owner Payment %d - %s - %s", i, accountType, month);
                    createOwnerPaymentTransaction(description, amount, month, accountType, result);
                }
            }
        }
    }
    
    /**
     * Create owner payment transaction
     */
    private void createOwnerPaymentTransaction(String description, BigDecimal amount, 
                                              String month, String accountType, ImportResult result) {
        try {
            Map<String, Object> transaction = new HashMap<>();
            transaction.put("transaction_date", getDateForMonth(month));
            transaction.put("amount", amount.negate()); // Negative as it's payment OUT
            transaction.put("description", description);
            transaction.put("transaction_type", "OWNER_PAYMENT");
            transaction.put("category", "payment_to_beneficiary");
            transaction.put("payment_method", accountType);
            transaction.put("data_source", "HISTORICAL_IMPORT");
            transaction.put("batch_id", result.batchId);
            
            // Save transaction
            financialTransactionService.createTransaction(transaction);
            result.successfulImports++;
            
            logger.debug("Created owner payment: {} for £{}", description, amount);
            
        } catch (Exception e) {
            logger.error("Failed to create owner payment: {}", e.getMessage());
            result.errors.add("Owner payment error: " + e.getMessage());
        }
    }
    
    // ========== UTILITY METHODS ==========
    
    private Map<String, Integer> mapColumns(Row headerRow) {
        Map<String, Integer> columnMap = new HashMap<>();
        
        for (Cell cell : headerRow) {
            String header = getCellStringValue(cell);
            if (header != null && !header.trim().isEmpty()) {
                columnMap.put(header.trim(), cell.getColumnIndex());
            }
        }
        
        return columnMap;
    }
    
    private String getCellValue(Row row, Map<String, Integer> columnMap, String columnName) {
        Integer colIndex = columnMap.get(columnName);
        if (colIndex == null) return null;
        
        Cell cell = row.getCell(colIndex);
        return getCellStringValue(cell);
    }
    
    private BigDecimal getCellBigDecimal(Row row, Map<String, Integer> columnMap, String columnName) {
        Integer colIndex = columnMap.get(columnName);
        if (colIndex == null) return null;
        
        Cell cell = row.getCell(colIndex);
        return getCellBigDecimalValue(cell);
    }
    
    private String getCellStringValue(Cell cell) {
        if (cell == null) return null;
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf(cell.getNumericCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return null;
        }
    }
    
    private BigDecimal getCellBigDecimalValue(Cell cell) {
        if (cell == null) return null;
        
        try {
            switch (cell.getCellType()) {
                case NUMERIC:
                    return BigDecimal.valueOf(cell.getNumericCellValue());
                case STRING:
                    String value = cell.getStringCellValue()
                        .replaceAll("[£,$,]", "")
                        .trim();
                    if (value.isEmpty() || value.equals("-")) return null;
                    return new BigDecimal(value);
                case FORMULA:
                    return BigDecimal.valueOf(cell.getNumericCellValue());
                default:
                    return null;
            }
        } catch (Exception e) {
            logger.debug("Could not parse cell value as BigDecimal: {}", e.getMessage());
            return null;
        }
    }
    
    private boolean isEmptyRow(Row row) {
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getCellStringValue(cell);
                if (value != null && !value.trim().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }
    
    private int findRowByContent(Sheet sheet, String content) {
        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                Cell firstCell = row.getCell(0);
                String value = getCellStringValue(firstCell);
                if (value != null && value.contains(content)) {
                    return i;
                }
            }
        }
        return -1;
    }
    
    private LocalDate parsePeriodFromSheetName(String sheetName, String periodString) {
        // Extract month from sheet name (e.g., "February Propsk Statement" -> "February")
        String month = sheetName.replace(" Propsk Statement", "").trim();
        
        // Parse year from period string if provided
        int year = 2025; // Default
        if (periodString != null && periodString.contains("2024")) {
            year = 2024;
        }
        
        return getDateForMonthYear(month, year);
    }
    
    private LocalDate getDateForMonth(String month) {
        return getDateForMonthYear(month, 2025);
    }
    
    private LocalDate getDateForMonthYear(String month, int year) {
        Map<String, Integer> monthMap = new HashMap<>();
        monthMap.put("January", 1);
        monthMap.put("February", 2);
        monthMap.put("March", 3);
        monthMap.put("April", 4);
        monthMap.put("May", 5);
        monthMap.put("June", 6);
        monthMap.put("July", 7);
        monthMap.put("August", 8);
        monthMap.put("September", 9);
        monthMap.put("October", 10);
        monthMap.put("November", 11);
        monthMap.put("December", 12);
        
        Integer monthNum = monthMap.get(month);
        if (monthNum == null) monthNum = 1;
        
        return LocalDate.of(year, monthNum, 21); // Use 21st as standard date
    }
    
    private String buildPropertyReference(String unitNo) {
        if (unitNo == null || unitNo.trim().isEmpty()) {
            return "Unknown Property";
        }
        return unitNo.trim();
    }
    
    private void createTransaction(String type, BigDecimal amount, String propertyRef, 
                                  String tenant, LocalDate date, ImportResult result) {
        try {
            Map<String, Object> transaction = new HashMap<>();
            transaction.put("transaction_date", date);
            transaction.put("amount", amount);
            transaction.put("description", type + " - " + propertyRef);
            transaction.put("transaction_type", mapTransactionType(type));
            transaction.put("category", mapCategory(type));
            transaction.put("property_reference", propertyRef);
            transaction.put("customer_reference", tenant);
            transaction.put("data_source", "HISTORICAL_IMPORT");
            transaction.put("batch_id", result.batchId);
            
            financialTransactionService.createTransaction(transaction);
            result.successfulImports++;
            
        } catch (Exception e) {
            logger.error("Failed to create transaction: {}", e.getMessage());
            result.errors.add("Transaction error: " + e.getMessage());
        }
    }
    
    private void createExpenseTransaction(String label, BigDecimal amount, String propertyRef,
                                         LocalDate date, ImportResult result) {
        try {
            Map<String, Object> transaction = new HashMap<>();
            transaction.put("transaction_date", date);
            transaction.put("amount", amount.negate()); // Expenses are negative
            transaction.put("description", label != null ? label : "Property Expense");
            transaction.put("transaction_type", "payment_to_contractor");
            transaction.put("category", "maintenance");
            transaction.put("property_reference", propertyRef);
            transaction.put("data_source", "HISTORICAL_IMPORT");
            transaction.put("batch_id", result.batchId);
            
            financialTransactionService.createTransaction(transaction);
            result.successfulImports++;
            
        } catch (Exception e) {
            logger.error("Failed to create expense: {}", e.getMessage());
            result.errors.add("Expense error: " + e.getMessage());
        }
    }
    
    private String mapTransactionType(String type) {
        switch (type) {
            case "RENT_DUE":
            case "RENT_RECEIVED":
                return "deposit";
            case "MANAGEMENT_FEE":
            case "MANAGEMENT_FEE_PAYPROP":
                return "fee";
            case "OWNER_PAYMENT":
                return "payment_to_beneficiary";
            default:
                return "other";
        }
    }
    
    private String mapCategory(String type) {
        switch (type) {
            case "RENT_DUE":
            case "RENT_RECEIVED":
                return "rent";
            case "MANAGEMENT_FEE":
            case "MANAGEMENT_FEE_PAYPROP":
                return "commission";
            case "OWNER_PAYMENT":
                return "owner_payment";
            default:
                return "other";
        }
    }
    
    /**
     * Import result class
     */
    public static class ImportResult {
        public boolean success;
        public String batchId;
        public int totalProcessed;
        public int successfulImports;
        public int failedImports;
        public List<String> errors = new ArrayList<>();
        public List<String> warnings = new ArrayList<>();
        
        public String getSummary() {
            return String.format("Processed %d records: %d successful, %d failed", 
                               totalProcessed, successfulImports, failedImports);
        }
    }
}
package site.easy.to.build.crm.service.payprop;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Service to transform property management spreadsheets into PayProp-compatible CSV format
 *
 * Handles conversion from Robert Ellis/Propsk statement format to PayProp financial_transactions format
 * Supports:
 * - Multiple payment sources (Robert Ellis, Propsk Old Account, PayProp)
 * - Commission calculations (10% management + 5% service = 15% total)
 * - Property name matching and validation
 * - Expense categorization
 * - Parking space handling
 */
@Service
public class SpreadsheetToPayPropFormatService {

    private static final Logger logger = LoggerFactory.getLogger(SpreadsheetToPayPropFormatService.class);

    // PayProp-compatible date formatter
    private static final DateTimeFormatter PAYPROP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Property name validation patterns
    private static final Pattern FLAT_PATTERN = Pattern.compile("FLAT\\s+(\\d+)\\s+-\\s+3\\s+WEST\\s+GATE", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARKING_PATTERN = Pattern.compile("PARKING\\s+SPACE\\s+(\\d+)", Pattern.CASE_INSENSITIVE);

    // Payment method mapping
    private static final Map<String, String> PAYMENT_METHOD_MAP = Map.of(
        "Robert Ellis", "Robert Ellis",
        "Propsk Old Account", "Propsk Old Account",
        "PayProp", "PayProp",
        "Direct Payment", "Direct Payment",
        "Bank Transfer", "Bank Transfer"
    );

    /**
     * Transform spreadsheet data to PayProp CSV format
     */
    public String transformSpreadsheetToPayPropCsv(MultipartFile file, String period) throws IOException {
        logger.info("Starting transformation of spreadsheet {} for period {}", file.getOriginalFilename(), period);

        List<PayPropTransaction> transactions;

        // Handle different file types using your existing patterns
        if (isExcelFile(file)) {
            transactions = parseExcelSpreadsheetData(file, period);
        } else {
            // CSV fallback
            List<String> lines = readCsvLines(file);
            transactions = parseSpreadsheetData(lines, period);
        }

        return generatePayPropCsv(transactions);
    }

    /**
     * Parse Excel spreadsheet data using Apache POI (matching your existing XLSXStatementService pattern)
     */
    private List<PayPropTransaction> parseExcelSpreadsheetData(MultipartFile file, String period) throws IOException {
        List<PayPropTransaction> transactions = new ArrayList<>();
        LocalDate periodDate = parsePeriodDate(period);

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0); // First sheet

            boolean inPropertySection = false;
            String currentProperty = null;

            for (Row row : sheet) {
                if (row == null) continue;

                // Convert row to string array for processing
                String[] columns = convertRowToStringArray(row);
                String rowText = String.join(" ", columns);

                // Detect property section
                if (rowText.toUpperCase().contains("PROPERTY:")) {
                    inPropertySection = true;
                    currentProperty = extractPropertyName(rowText);
                    continue;
                }

                if (!inPropertySection) continue;

                // Parse individual unit/flat data
                String unitName = getColumnValue(columns, 2); // Unit No.
                String tenant = getColumnValue(columns, 3);   // Tenant
                String rentDueStr = getColumnValue(columns, 6); // Rent Due Amount
                String rentReceivedDateStr = getColumnValue(columns, 7); // Rent Received Date

                if (isValidRentRow(unitName, rentDueStr)) {
                    transactions.addAll(processRentTransaction(
                        unitName, tenant, rentDueStr, rentReceivedDateStr,
                        columns, periodDate, currentProperty
                    ));
                }

                // Handle expenses
                if (rowText.contains("BUILDING ADDITIONAL INCOME") || rowText.contains("Maintenance")) {
                    transactions.addAll(processExpenseTransaction(columns, periodDate, currentProperty));
                }
            }
        }

        logger.info("Extracted {} transactions from Excel spreadsheet", transactions.size());
        return transactions;
    }

    /**
     * Parse spreadsheet data and extract transactions
     */
    private List<PayPropTransaction> parseSpreadsheetData(List<String> lines, String period) {
        List<PayPropTransaction> transactions = new ArrayList<>();
        LocalDate periodDate = parsePeriodDate(period);

        boolean inPropertySection = false;
        String currentProperty = null;

        for (String line : lines) {
            String[] columns = parseCSVLine(line);

            if (columns.length < 10) continue; // Skip header/empty rows

            // Detect property section
            if (line.toUpperCase().contains("PROPERTY:")) {
                inPropertySection = true;
                currentProperty = extractPropertyName(line);
                continue;
            }

            if (!inPropertySection) continue;

            // Parse individual unit/flat data
            String unitName = getColumnValue(columns, 2); // Unit No.
            String tenant = getColumnValue(columns, 3);   // Tenant
            String rentDueStr = getColumnValue(columns, 6); // Rent Due Amount
            String rentReceivedDateStr = getColumnValue(columns, 7); // Rent Received Date

            if (isValidRentRow(unitName, rentDueStr)) {
                transactions.addAll(processRentTransaction(
                    unitName, tenant, rentDueStr, rentReceivedDateStr,
                    columns, periodDate, currentProperty
                ));
            }

            // Handle expenses
            if (line.contains("BUILDING ADDITIONAL INCOME") || line.contains("Maintenance")) {
                transactions.addAll(processExpenseTransaction(columns, periodDate, currentProperty));
            }
        }

        logger.info("Extracted {} transactions from spreadsheet", transactions.size());
        return transactions;
    }

    /**
     * Process rent transaction with commission calculations
     */
    private List<PayPropTransaction> processRentTransaction(
            String unitName, String tenant, String rentDueStr, String rentReceivedDateStr,
            String[] columns, LocalDate periodDate, String property) {

        List<PayPropTransaction> transactions = new ArrayList<>();

        try {
            BigDecimal rentAmount = parseAmount(rentDueStr);
            if (rentAmount.compareTo(BigDecimal.ZERO) <= 0) {
                return transactions; // Skip zero/negative amounts
            }

            LocalDate transactionDate = parseTransactionDate(rentReceivedDateStr, periodDate);
            String propertyReference = buildPropertyReference(unitName, property);
            String paymentMethod = determinePaymentMethod(columns);
            String bankReference = generateBankReference(unitName, transactionDate, paymentMethod);

            // 1. Rent Payment Transaction
            PayPropTransaction rentPayment = new PayPropTransaction();
            rentPayment.transactionDate = transactionDate;
            rentPayment.amount = rentAmount;
            rentPayment.description = String.format("Rent payment - %s",
                transactionDate.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
            rentPayment.transactionType = "deposit";
            rentPayment.category = "rent";
            rentPayment.propertyReference = propertyReference;
            rentPayment.customerReference = cleanTenantName(tenant);
            rentPayment.bankReference = bankReference;
            rentPayment.paymentMethod = paymentMethod;
            rentPayment.notes = "Monthly rent collection";

            transactions.add(rentPayment);

            // 2. Commission Transaction (15% total: 10% management + 5% service)
            BigDecimal commissionRate = new BigDecimal("15.00");
            BigDecimal commissionAmount = rentAmount
                .multiply(commissionRate)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

            PayPropTransaction commission = new PayPropTransaction();
            commission.transactionDate = transactionDate;
            commission.amount = commissionAmount.negate(); // Negative for commission
            commission.description = String.format("Commission - %s (10%% mgmt + 5%% service)",
                transactionDate.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
            commission.transactionType = "fee";
            commission.category = "commission";
            commission.propertyReference = propertyReference;
            commission.customerReference = cleanTenantName(tenant);
            commission.bankReference = generateCommissionReference(unitName, transactionDate);
            commission.paymentMethod = paymentMethod;
            commission.notes = "15% total commission";

            transactions.add(commission);

        } catch (Exception e) {
            logger.warn("Failed to process rent transaction for unit {}: {}", unitName, e.getMessage());
        }

        return transactions;
    }

    /**
     * Process expense transactions
     */
    private List<PayPropTransaction> processExpenseTransaction(String[] columns, LocalDate periodDate, String property) {
        List<PayPropTransaction> transactions = new ArrayList<>();

        // Extract expense details from columns
        String expenseLabel = getColumnValue(columns, 20); // Expense 1 Label
        String expenseAmountStr = getColumnValue(columns, 21); // Expense 1 Amount
        String expenseComment = getColumnValue(columns, 22); // Expense 1 Comment

        if (expenseLabel != null && !expenseLabel.trim().isEmpty() &&
            expenseAmountStr != null && !expenseAmountStr.trim().isEmpty()) {

            try {
                BigDecimal expenseAmount = parseAmount(expenseAmountStr);
                if (expenseAmount.compareTo(BigDecimal.ZERO) != 0) {

                    PayPropTransaction expense = new PayPropTransaction();
                    expense.transactionDate = periodDate;
                    expense.amount = expenseAmount.abs().negate(); // Ensure negative
                    expense.description = String.format("%s - %s", expenseLabel,
                        expenseComment != null ? expenseComment : "Property expense");
                    expense.transactionType = "expense";
                    expense.category = categorizeExpense(expenseLabel);
                    expense.propertyReference = property != null ? property : "BODEN HOUSE";
                    expense.customerReference = "";
                    expense.bankReference = generateExpenseReference(expenseLabel, periodDate);
                    expense.paymentMethod = "Direct Payment";
                    expense.notes = expenseComment;

                    transactions.add(expense);
                }
            } catch (Exception e) {
                logger.warn("Failed to process expense {}: {}", expenseLabel, e.getMessage());
            }
        }

        return transactions;
    }

    /**
     * Generate PayProp-compatible CSV output
     */
    private String generatePayPropCsv(List<PayPropTransaction> transactions) {
        StringBuilder csv = new StringBuilder();

        // CSV Header
        csv.append("transaction_date,amount,description,transaction_type,category,")
           .append("property_reference,customer_reference,bank_reference,payment_method,notes\n");

        // Sort by date and property for consistency
        transactions.sort(Comparator
            .comparing((PayPropTransaction t) -> t.transactionDate)
            .thenComparing(t -> t.propertyReference)
            .thenComparing(t -> t.transactionType));

        for (PayPropTransaction transaction : transactions) {
            csv.append(transaction.toCsvRow()).append("\n");
        }

        return csv.toString();
    }

    // ===== UTILITY METHODS =====

    private List<String> readCsvLines(MultipartFile file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    private String[] parseCSVLine(String line) {
        // Basic CSV parsing - handles quoted fields
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(currentField.toString().trim());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        fields.add(currentField.toString().trim());

        return fields.toArray(new String[0]);
    }

    private String getColumnValue(String[] columns, int index) {
        if (index < columns.length) {
            String value = columns[index].trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }

    private BigDecimal parseAmount(String amountStr) {
        if (amountStr == null || amountStr.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Clean amount string
        String cleaned = amountStr.trim()
            .replace("£", "")
            .replace("$", "")
            .replace(",", "")
            .replace("(", "-")
            .replace(")", "")
            .trim();

        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            logger.warn("Could not parse amount: {}", amountStr);
            return BigDecimal.ZERO;
        }
    }

    private LocalDate parseTransactionDate(String dateStr, LocalDate fallback) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return fallback;
        }

        try {
            // Try different date formats
            String cleaned = dateStr.trim();

            // DD/MM/YYYY format
            if (cleaned.matches("\\d{2}/\\d{2}/\\d{4}")) {
                String[] parts = cleaned.split("/");
                return LocalDate.of(
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[0])
                );
            }

            // Add more date format handling as needed

        } catch (Exception e) {
            logger.warn("Could not parse date: {}, using fallback", dateStr);
        }

        return fallback;
    }

    private LocalDate parsePeriodDate(String period) {
        // Extract date from period string like "22nd May 2025 to 21st Jun 2025"
        try {
            if (period.contains("to")) {
                String endDate = period.split("to")[1].trim();
                // Parse "21st Jun 2025" format
                String[] parts = endDate.split(" ");
                if (parts.length >= 3) {
                    int day = Integer.parseInt(parts[0].replaceAll("[^0-9]", ""));
                    String month = parts[1];
                    int year = Integer.parseInt(parts[2]);

                    int monthNum = switch (month.toLowerCase()) {
                        case "jan", "january" -> 1;
                        case "feb", "february" -> 2;
                        case "mar", "march" -> 3;
                        case "apr", "april" -> 4;
                        case "may" -> 5;
                        case "jun", "june" -> 6;
                        case "jul", "july" -> 7;
                        case "aug", "august" -> 8;
                        case "sep", "september" -> 9;
                        case "oct", "october" -> 10;
                        case "nov", "november" -> 11;
                        case "dec", "december" -> 12;
                        default -> 6; // Default to June
                    };

                    return LocalDate.of(year, monthNum, day);
                }
            }
        } catch (Exception e) {
            logger.warn("Could not parse period: {}", period);
        }

        return LocalDate.now(); // Fallback to current date
    }

    private String extractPropertyName(String line) {
        if (line.toUpperCase().contains("BODEN HOUSE")) {
            return "BODEN HOUSE";
        } else if (line.toUpperCase().contains("KNIGHTON HAYES")) {
            return "KNIGHTON HAYES";
        }
        return "UNKNOWN PROPERTY";
    }

    private boolean isValidRentRow(String unitName, String rentDueStr) {
        return unitName != null && !unitName.trim().isEmpty() &&
               (unitName.toUpperCase().contains("FLAT") || unitName.toUpperCase().contains("PARKING")) &&
               rentDueStr != null && !rentDueStr.trim().isEmpty() &&
               !rentDueStr.trim().equals("-");
    }

    private String buildPropertyReference(String unitName, String property) {
        if (unitName.toUpperCase().contains("FLAT")) {
            return unitName.trim(); // "FLAT 1 - 3 WEST GATE"
        } else if (unitName.toUpperCase().contains("PARKING")) {
            // Convert "PARKING SPACE 1" to "Parking Space 1"
            return unitName.toLowerCase()
                .replace("parking space", "Parking Space");
        }
        return unitName;
    }

    private String determinePaymentMethod(String[] columns) {
        // Check payment method columns
        String robertEllis = getColumnValue(columns, 8);  // Paid to Robert Ellis
        String propskOld = getColumnValue(columns, 9);    // Paid to Propsk Old Account
        String propskPayProp = getColumnValue(columns, 10); // Paid to Propsk PayProp

        if ("TRUE".equalsIgnoreCase(robertEllis)) {
            return "Robert Ellis";
        } else if ("TRUE".equalsIgnoreCase(propskOld)) {
            return "Propsk Old Account";
        } else if ("TRUE".equalsIgnoreCase(propskPayProp)) {
            return "PayProp";
        }

        return "Direct Payment"; // Default
    }

    private String generateBankReference(String unitName, LocalDate date, String paymentMethod) {
        String unitNum = extractUnitNumber(unitName);
        String monthCode = date.format(DateTimeFormatter.ofPattern("MMM")).toUpperCase();

        return switch (paymentMethod) {
            case "Robert Ellis" -> String.format("RE-%s-%s", monthCode, unitNum);
            case "Propsk Old Account" -> String.format("PROPSK-%s-%s", monthCode, unitNum);
            case "PayProp" -> String.format("PP-%s-%s", monthCode, unitNum);
            default -> String.format("RENT-%s-%s", monthCode, unitNum);
        };
    }

    private String generateCommissionReference(String unitName, LocalDate date) {
        String unitNum = extractUnitNumber(unitName);
        String monthCode = date.format(DateTimeFormatter.ofPattern("MMM")).toUpperCase();
        return String.format("COMM-%s-%s", monthCode, unitNum);
    }

    private String generateExpenseReference(String expenseLabel, LocalDate date) {
        String monthCode = date.format(DateTimeFormatter.ofPattern("MMM")).toUpperCase();
        String expenseCode = expenseLabel.substring(0, Math.min(4, expenseLabel.length())).toUpperCase();
        return String.format("EXP-%s-%s", monthCode, expenseCode);
    }

    private String extractUnitNumber(String unitName) {
        if (FLAT_PATTERN.matcher(unitName).find()) {
            return FLAT_PATTERN.matcher(unitName).replaceAll("$1").replaceAll("\\D", "");
        } else if (PARKING_PATTERN.matcher(unitName).find()) {
            return "P" + PARKING_PATTERN.matcher(unitName).replaceAll("$1").replaceAll("\\D", "");
        }
        return "00";
    }

    /**
     * Check if file is Excel format (matching your existing XLSXStatementService pattern)
     */
    private boolean isExcelFile(MultipartFile file) {
        if (file == null || file.getOriginalFilename() == null) return false;
        String filename = file.getOriginalFilename().toLowerCase();
        return filename.endsWith(".xlsx") || filename.endsWith(".xls");
    }

    /**
     * Convert Excel row to string array (following your existing patterns)
     */
    private String[] convertRowToStringArray(Row row) {
        if (row == null) return new String[0];

        int lastCellNum = row.getLastCellNum();
        String[] columns = new String[Math.max(lastCellNum, 50)]; // Ensure enough columns

        for (int i = 0; i < columns.length; i++) {
            Cell cell = row.getCell(i);
            if (cell == null) {
                columns[i] = "";
            } else {
                switch (cell.getCellType()) {
                    case STRING:
                        columns[i] = cell.getStringCellValue();
                        break;
                    case NUMERIC:
                        if (DateUtil.isCellDateFormatted(cell)) {
                            columns[i] = cell.getDateCellValue().toString();
                        } else {
                            // Format numbers without scientific notation
                            double numValue = cell.getNumericCellValue();
                            if (numValue == (long) numValue) {
                                columns[i] = String.valueOf((long) numValue);
                            } else {
                                columns[i] = String.valueOf(numValue);
                            }
                        }
                        break;
                    case BOOLEAN:
                        columns[i] = String.valueOf(cell.getBooleanCellValue()).toUpperCase();
                        break;
                    case FORMULA:
                        try {
                            // Try to get the calculated value
                            switch (cell.getCachedFormulaResultType()) {
                                case NUMERIC:
                                    double numValue = cell.getNumericCellValue();
                                    if (numValue == (long) numValue) {
                                        columns[i] = String.valueOf((long) numValue);
                                    } else {
                                        columns[i] = String.valueOf(numValue);
                                    }
                                    break;
                                case STRING:
                                    columns[i] = cell.getStringCellValue();
                                    break;
                                case BOOLEAN:
                                    columns[i] = String.valueOf(cell.getBooleanCellValue()).toUpperCase();
                                    break;
                                default:
                                    columns[i] = cell.toString();
                            }
                        } catch (Exception e) {
                            columns[i] = cell.toString();
                        }
                        break;
                    default:
                        columns[i] = cell.toString();
                }
            }
        }

        return columns;
    }

    private String cleanTenantName(String tenant) {
        if (tenant == null || tenant.trim().isEmpty()) {
            return "";
        }
        return tenant.trim().replaceAll("[\"',]", "");
    }

    private String categorizeExpense(String expenseLabel) {
        String lower = expenseLabel.toLowerCase();

        // Map to your existing PayProp maintenance categories
        if (lower.contains("maintenance") || lower.contains("repair") ||
            lower.contains("plumb") || lower.contains("paint") ||
            lower.contains("filling") || lower.contains("bathroom")) {
            return "maintenance";
        } else if (lower.contains("fire") || lower.contains("safety") ||
                   lower.contains("extinguisher") || lower.contains("alarm")) {
            return "fire_safety";
        } else if (lower.contains("washing") || lower.contains("fridge") ||
                   lower.contains("appliance") || lower.contains("white goods")) {
            return "white_goods";
        } else if (lower.contains("clear") || lower.contains("clean") ||
                   lower.contains("removal")) {
            return "clearance";
        } else if (lower.contains("bed") || lower.contains("furniture") ||
                   lower.contains("furnish") || lower.contains("mattress")) {
            return "furnishing";
        } else if (lower.contains("electric") || lower.contains("wiring") ||
                   lower.contains("socket")) {
            return "electrical";
        } else if (lower.contains("gas") || lower.contains("boiler") ||
                   lower.contains("heating")) {
            return "heating";
        }

        return "general_expense"; // Default category - aligns with PayProp structure
    }

    /**
     * Internal class representing a PayProp transaction
     */
    private static class PayPropTransaction {
        LocalDate transactionDate;
        BigDecimal amount;
        String description;
        String transactionType;
        String category;
        String propertyReference;
        String customerReference;
        String bankReference;
        String paymentMethod;
        String notes;

        public String toCsvRow() {
            return String.format("%s,%s,\"%s\",%s,%s,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"",
                transactionDate.format(PAYPROP_DATE_FORMAT),
                amount.toPlainString(),
                escapeQuotes(description),
                transactionType,
                category,
                escapeQuotes(propertyReference),
                escapeQuotes(customerReference != null ? customerReference : ""),
                escapeQuotes(bankReference),
                escapeQuotes(paymentMethod),
                escapeQuotes(notes != null ? notes : "")
            );
        }

        private String escapeQuotes(String value) {
            if (value == null) return "";
            return value.replace("\"", "\"\"");
        }
    }
}
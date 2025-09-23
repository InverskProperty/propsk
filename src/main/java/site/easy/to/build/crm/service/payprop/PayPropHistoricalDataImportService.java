package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.*;
import site.easy.to.build.crm.service.property.PropertyService;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for importing historical financial data in PayProp-compatible format
 *
 * This service handles the import of historical rent, commission, and expense data
 * into both FinancialTransaction and HistoricalTransaction tables, ensuring data
 * consistency and proper property linkage for reporting.
 */
@Service
@Transactional
public class PayPropHistoricalDataImportService {

    private static final Logger logger = LoggerFactory.getLogger(PayPropHistoricalDataImportService.class);

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    @Autowired
    private HistoricalTransactionRepository historicalTransactionRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private SpreadsheetToPayPropFormatService spreadsheetTransformService;

    @Autowired
    private PayPropCategoryMappingService categoryMappingService;

    // PayProp date format
    private static final DateTimeFormatter PAYPROP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Expected CSV header
    private static final String EXPECTED_HEADER =
        "transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes";

    /**
     * Import historical data from spreadsheet via transformation
     */
    public ImportResult importFromSpreadsheet(MultipartFile spreadsheetFile, String period, User currentUser) {
        try {
            logger.info("Starting spreadsheet import for period: {}", period);

            // Transform spreadsheet to PayProp CSV format
            String csvData = spreadsheetTransformService.transformSpreadsheetToPayPropCsv(spreadsheetFile, period);

            // Import the transformed CSV data
            return importFromPayPropCsv(csvData, currentUser, "SPREADSHEET_IMPORT_" + period.replaceAll("\\s", "_"));

        } catch (Exception e) {
            logger.error("Failed to import from spreadsheet: {}", e.getMessage(), e);
            return ImportResult.failure("Spreadsheet import failed: " + e.getMessage());
        }
    }

    /**
     * Import historical data from PayProp-compatible CSV
     */
    public ImportResult importFromPayPropCsv(MultipartFile csvFile, User currentUser) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(csvFile.getInputStream(), StandardCharsets.UTF_8))) {

            String csvContent = reader.lines().collect(Collectors.joining("\n"));
            String batchId = "CSV_IMPORT_" + System.currentTimeMillis();

            return importFromPayPropCsv(csvContent, currentUser, batchId);

        } catch (Exception e) {
            logger.error("Failed to import from CSV file: {}", e.getMessage(), e);
            return ImportResult.failure("CSV import failed: " + e.getMessage());
        }
    }

    /**
     * Core import method for PayProp CSV data
     */
    private ImportResult importFromPayPropCsv(String csvContent, User currentUser, String batchId) {
        ImportResult result = new ImportResult();
        result.batchId = batchId;
        result.startTime = LocalDateTime.now();

        try {
            String[] lines = csvContent.split("\n");

            if (lines.length < 2) {
                return ImportResult.failure("CSV file must contain header and at least one data row");
            }

            // Validate header
            String header = lines[0].trim();
            if (!header.equals(EXPECTED_HEADER)) {
                result.addWarning("CSV header doesn't match expected format. Expected: " + EXPECTED_HEADER);
            }

            // Load properties for matching
            Map<String, Property> propertyMap = loadPropertyMap();

            // Process each transaction row
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;

                try {
                    ImportedTransaction transaction = parseTransactionLine(line, i + 1);
                    if (transaction != null) {
                        processTransaction(transaction, propertyMap, currentUser, batchId, result);
                    }
                } catch (Exception e) {
                    result.addError("Row " + (i + 1) + ": " + e.getMessage());
                }
            }

            result.endTime = LocalDateTime.now();
            result.success = result.errorCount == 0;

            logger.info("Import completed. Success: {}, Processed: {}, Errors: {}, Warnings: {}",
                result.success, result.processedCount, result.errorCount, result.warningCount);

            return result;

        } catch (Exception e) {
            logger.error("Import failed: {}", e.getMessage(), e);
            return ImportResult.failure("Import failed: " + e.getMessage());
        }
    }

    /**
     * Process individual transaction and save to database
     */
    private void processTransaction(ImportedTransaction imported, Map<String, Property> propertyMap,
                                   User currentUser, String batchId, ImportResult result) {

        try {
            // Find matching property
            Property property = findMatchingProperty(imported.propertyReference, propertyMap);
            if (property == null) {
                result.addWarning("Property not found: " + imported.propertyReference);
                result.skippedCount++;
                return;
            }

            // Check for duplicate transaction
            if (isDuplicateTransaction(imported, property)) {
                result.addWarning("Skipped duplicate transaction: " + imported.description +
                                " for property " + imported.propertyReference +
                                " on " + imported.transactionDate);
                result.skippedCount++;
                return;
            }

            // Create FinancialTransaction (PayProp-compatible format)
            FinancialTransaction financialTransaction = createFinancialTransaction(imported, property, batchId);
            financialTransactionRepository.save(financialTransaction);

            // Create HistoricalTransaction (for historical tracking)
            HistoricalTransaction historicalTransaction = createHistoricalTransaction(imported, property, currentUser, batchId);
            historicalTransactionRepository.save(historicalTransaction);

            result.processedCount++;

            if (result.processedCount % 100 == 0) {
                logger.info("Processed {} transactions", result.processedCount);
            }

        } catch (Exception e) {
            result.addError("Failed to process transaction: " + e.getMessage());
            logger.error("Transaction processing error: {}", e.getMessage(), e);
        }
    }

    /**
     * Create FinancialTransaction from imported data
     */
    private FinancialTransaction createFinancialTransaction(ImportedTransaction imported, Property property, String batchId) {
        FinancialTransaction ft = new FinancialTransaction();

        // Basic transaction details
        ft.setTransactionDate(imported.transactionDate);
        ft.setAmount(imported.amount);
        ft.setDescription(imported.description);
        ft.setTransactionType(mapToFinancialTransactionType(imported.transactionType));

        // Property information
        if (property != null) {
            ft.setPropertyId(property.getPayPropId() != null ? property.getPayPropId() : property.getId().toString());
            ft.setPropertyName(property.getPropertyName());
        } else {
            ft.setPropertyName(imported.propertyReference);
        }

        // Customer/Tenant information
        ft.setTenantName(imported.customerReference);

        // Category information - map to PayProp-compatible categories
        String mappedCategory = categoryMappingService.mapHistoricalCategory(imported.transactionType, imported.category);
        ft.setCategoryName(mappedCategory);

        // Commission calculation for rent payments
        if ("rent".equals(mappedCategory) && imported.amount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal commissionRate = new BigDecimal("15.00"); // 15% total commission
            BigDecimal commissionAmount = imported.amount
                .multiply(commissionRate)
                .divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP);

            ft.setCommissionRate(commissionRate);
            ft.setCommissionAmount(commissionAmount);
            ft.setNetToOwnerAmount(imported.amount.subtract(commissionAmount));
        }

        // Data source tracking
        ft.setDataSource("HISTORICAL_IMPORT");
        ft.setPayPropBatchId(batchId);

        // PayProp integration fields
        ft.setPayPropTransactionId(imported.bankReference);

        return ft;
    }

    /**
     * Create HistoricalTransaction from imported data
     */
    private HistoricalTransaction createHistoricalTransaction(ImportedTransaction imported, Property property,
                                                            User currentUser, String batchId) {
        HistoricalTransaction ht = new HistoricalTransaction();

        // Basic transaction details
        ht.setTransactionDate(imported.transactionDate);
        ht.setAmount(imported.amount);
        ht.setDescription(imported.description);
        ht.setTransactionType(mapToHistoricalTransactionType(imported.transactionType));

        // Property and customer linking
        ht.setProperty(property);
        ht.setCreatedByUser(currentUser);

        // Classification - map to PayProp-compatible categories
        String mappedCategory = categoryMappingService.mapHistoricalCategory(imported.transactionType, imported.category);
        ht.setCategory(mappedCategory);
        ht.setSource(HistoricalTransaction.TransactionSource.historical_import);

        // Source tracking
        ht.setSourceReference(imported.bankReference);
        ht.setImportBatchId(batchId);
        ht.setBankReference(imported.bankReference);
        ht.setPaymentMethod(imported.paymentMethod);
        ht.setCounterpartyName(imported.customerReference);

        // Notes
        ht.setNotes(imported.notes);

        // Financial year calculation
        ht.setFinancialYear(HistoricalTransaction.calculateFinancialYear(imported.transactionDate));

        // Tax relevance (rent and commission are typically tax relevant)
        ht.setTaxRelevant("rent".equals(mappedCategory) || "commission".equals(mappedCategory));

        return ht;
    }

    /**
     * Parse CSV line into transaction object
     */
    private ImportedTransaction parseTransactionLine(String line, int lineNumber) {
        String[] fields = parseCsvLine(line);

        if (fields.length < 10) {
            throw new IllegalArgumentException("Insufficient fields in CSV line. Expected 10, got " + fields.length);
        }

        ImportedTransaction transaction = new ImportedTransaction();

        try {
            transaction.transactionDate = LocalDate.parse(fields[0].trim(), PAYPROP_DATE_FORMAT);
            transaction.amount = new BigDecimal(fields[1].trim());
            transaction.description = fields[2].trim();
            transaction.transactionType = fields[3].trim();
            transaction.category = fields[4].trim();
            transaction.propertyReference = fields[5].trim();
            transaction.customerReference = fields[6].trim();
            transaction.bankReference = fields[7].trim();
            transaction.paymentMethod = fields[8].trim();
            transaction.notes = fields[9].trim();

            // Validation
            validateTransaction(transaction, lineNumber);

            return transaction;

        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse transaction on line " + lineNumber + ": " + e.getMessage());
        }
    }

    /**
     * Validate imported transaction data
     */
    private void validateTransaction(ImportedTransaction transaction, int lineNumber) {
        List<String> errors = new ArrayList<>();

        if (transaction.transactionDate == null) {
            errors.add("Transaction date is required");
        }

        if (transaction.amount == null) {
            errors.add("Amount is required");
        }

        if (transaction.description == null || transaction.description.trim().isEmpty()) {
            errors.add("Description is required");
        }

        if (!isValidTransactionType(transaction.transactionType)) {
            errors.add("Invalid transaction type: " + transaction.transactionType);
        }

        // Validate category mapping
        if (transaction.category != null && !transaction.category.trim().isEmpty()) {
            String mappedCategory = categoryMappingService.mapHistoricalCategory(transaction.transactionType, transaction.category);
            if (!categoryMappingService.isValidPayPropCategory(transaction.transactionType, mappedCategory)) {
                // Add warning rather than error for invalid categories
                List<String> suggestions = categoryMappingService.suggestCategories(transaction.transactionType, transaction.category);
                if (!suggestions.isEmpty()) {
                    errors.add("Category '" + transaction.category + "' mapped to '" + mappedCategory +
                             "' may not be valid. Suggestions: " + String.join(", ", suggestions));
                }
            }
        }

        if (transaction.propertyReference == null || transaction.propertyReference.trim().isEmpty()) {
            errors.add("Property reference is required");
        }

        // Commission pairing validation for rent payments
        if ("deposit".equals(transaction.transactionType) && "rent".equals(transaction.category)) {
            if (transaction.amount.compareTo(BigDecimal.ZERO) <= 0) {
                errors.add("Rent payments must have positive amounts");
            }
        }

        if ("fee".equals(transaction.transactionType) && "commission".equals(transaction.category)) {
            if (transaction.amount.compareTo(BigDecimal.ZERO) >= 0) {
                errors.add("Commission fees must have negative amounts");
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Line " + lineNumber + " validation errors: " + String.join(", ", errors));
        }
    }

    /**
     * Load property map for fast lookups
     */
    private Map<String, Property> loadPropertyMap() {
        List<Property> properties = propertyRepository.findAll();
        Map<String, Property> propertyMap = new HashMap<>();

        for (Property property : properties) {
            // Index by property name (primary key for matching)
            if (property.getPropertyName() != null) {
                propertyMap.put(property.getPropertyName().trim(), property);
            }

            // Also index by PayProp ID if available
            if (property.getPayPropId() != null) {
                propertyMap.put(property.getPayPropId(), property);
            }
        }

        logger.info("Loaded {} properties for matching", propertyMap.size());
        return propertyMap;
    }

    /**
     * Find property matching the reference from CSV
     */
    private Property findMatchingProperty(String propertyReference, Map<String, Property> propertyMap) {
        if (propertyReference == null || propertyReference.trim().isEmpty()) {
            return null;
        }

        String trimmed = propertyReference.trim();

        // Exact match first
        Property exact = propertyMap.get(trimmed);
        if (exact != null) {
            return exact;
        }

        // Fuzzy matching for property names
        for (Map.Entry<String, Property> entry : propertyMap.entrySet()) {
            String propertyName = entry.getKey();
            if (propertyName.equalsIgnoreCase(trimmed) ||
                normalizePropertyName(propertyName).equals(normalizePropertyName(trimmed))) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * Normalize property name for fuzzy matching
     */
    private String normalizePropertyName(String name) {
        if (name == null) return "";
        return name.toLowerCase()
            .replaceAll("\\s+", " ")
            .replaceAll("[^a-z0-9\\s]", "")
            .trim();
    }

    // ===== UTILITY METHODS =====

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(currentField.toString());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        fields.add(currentField.toString());

        return fields.toArray(new String[0]);
    }

    private boolean isValidTransactionType(String type) {
        return Arrays.asList("deposit", "fee", "payment", "expense").contains(type);
    }

    private String mapToFinancialTransactionType(String csvType) {
        return switch (csvType) {
            case "deposit" -> "invoice";
            case "fee" -> "commission_payment";
            case "payment" -> "payment_to_beneficiary";
            case "expense" -> "payment_to_contractor";
            default -> csvType;
        };
    }

    private HistoricalTransaction.TransactionType mapToHistoricalTransactionType(String csvType) {
        return switch (csvType) {
            case "deposit" -> HistoricalTransaction.TransactionType.payment;
            case "fee" -> HistoricalTransaction.TransactionType.fee;
            case "payment" -> HistoricalTransaction.TransactionType.payment;
            case "expense" -> HistoricalTransaction.TransactionType.expense;
            default -> HistoricalTransaction.TransactionType.payment;
        };
    }

    /**
     * Check if transaction is a duplicate of existing data
     * Uses multiple criteria to detect duplicates across different import sources
     */
    private boolean isDuplicateTransaction(ImportedTransaction imported, Property property) {
        // Define duplicate detection criteria
        LocalDate startDate = imported.transactionDate.minusDays(2); // Allow 2-day tolerance
        LocalDate endDate = imported.transactionDate.plusDays(2);

        // Look for existing transactions with same property, similar date, and same amount
        List<FinancialTransaction> existingTransactions = financialTransactionRepository
            .findByPropertyAndDateRange(property.getPayPropId(), startDate, endDate);

        for (FinancialTransaction existing : existingTransactions) {
            // Check for exact match on key fields
            if (isExactMatch(imported, existing)) {
                logger.debug("Found exact duplicate: {} on {}", imported.description, imported.transactionDate);
                return true;
            }

            // Check for similar transaction (different sources, same financial impact)
            if (isSimilarTransaction(imported, existing)) {
                logger.debug("Found similar transaction: {} on {}", imported.description, imported.transactionDate);
                return true;
            }
        }

        return false;
    }

    /**
     * Check for exact match (same import source, same data)
     */
    private boolean isExactMatch(ImportedTransaction imported, FinancialTransaction existing) {
        return imported.amount.compareTo(existing.getAmount()) == 0 &&
               imported.transactionDate.equals(existing.getTransactionDate()) &&
               imported.description.equals(existing.getDescription());
    }

    /**
     * Check for similar transaction (different sources, same financial impact)
     */
    private boolean isSimilarTransaction(ImportedTransaction imported, FinancialTransaction existing) {
        // Same amount and date within tolerance
        boolean sameAmountAndDate = imported.amount.compareTo(existing.getAmount()) == 0 &&
                                   Math.abs(imported.transactionDate.toEpochDay() - existing.getTransactionDate().toEpochDay()) <= 1;

        if (!sameAmountAndDate) {
            return false;
        }

        // Check if descriptions are similar (rent payments, same category)
        String importedDesc = imported.description.toLowerCase();
        String existingDesc = existing.getDescription() != null ? existing.getDescription().toLowerCase() : "";

        // Consider similar if both are rent payments
        boolean bothRentPayments = (importedDesc.contains("rent") && existingDesc.contains("rent")) ||
                                  (imported.category != null && imported.category.toLowerCase().contains("rent") &&
                                   existing.getCategoryName() != null && existing.getCategoryName().toLowerCase().contains("rent"));

        // Consider similar if categories match
        boolean categoriesMatch = imported.category != null && existing.getCategoryName() != null &&
                                 imported.category.equalsIgnoreCase(existing.getCategoryName());

        return bothRentPayments || categoriesMatch;
    }

    // ===== INNER CLASSES =====

    /**
     * Represents an imported transaction before database storage
     */
    private static class ImportedTransaction {
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
    }

    /**
     * Result of import operation with detailed statistics
     */
    public static class ImportResult {
        public boolean success;
        public String batchId;
        public LocalDateTime startTime;
        public LocalDateTime endTime;
        public int processedCount = 0;
        public int skippedCount = 0;
        public int errorCount = 0;
        public int warningCount = 0;
        public List<String> errors = new ArrayList<>();
        public List<String> warnings = new ArrayList<>();

        public static ImportResult failure(String message) {
            ImportResult result = new ImportResult();
            result.success = false;
            result.errors.add(message);
            result.errorCount = 1;
            return result;
        }

        public void addError(String error) {
            errors.add(error);
            errorCount++;
        }

        public void addWarning(String warning) {
            warnings.add(warning);
            warningCount++;
        }

        public String getSummary() {
            long duration = endTime != null && startTime != null ?
                java.time.Duration.between(startTime, endTime).toSeconds() : 0;

            return String.format(
                "Import %s in %d seconds. Processed: %d, Errors: %d, Warnings: %d",
                success ? "completed successfully" : "failed",
                duration, processedCount, errorCount, warningCount
            );
        }
    }
}
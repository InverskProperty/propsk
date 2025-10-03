package site.easy.to.build.crm.service.transaction;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import site.easy.to.build.crm.entity.HistoricalTransaction;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.entity.HistoricalTransaction.TransactionType;
import site.easy.to.build.crm.entity.HistoricalTransaction.TransactionSource;
import site.easy.to.build.crm.repository.HistoricalTransactionRepository;
import site.easy.to.build.crm.repository.UserRepository;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Historical Transaction Import Service
 * 
 * Handles importing historical transaction data from various sources:
 * - JSON format for API imports
 * - CSV format for spreadsheet imports
 * - Bank statement imports
 * - Manual data entry
 * 
 * Provides property/customer matching and validation
 */
@Service
@Transactional
public class HistoricalTransactionImportService {
    
    private static final Logger log = LoggerFactory.getLogger(HistoricalTransactionImportService.class);
    
    @Autowired
    private HistoricalTransactionRepository historicalTransactionRepository;
    
    @Autowired
    private PropertyService propertyService;
    
    @Autowired
    private CustomerService customerService;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private AuthenticationUtils authenticationUtils;
    
    private final ObjectMapper objectMapper;
    
    public HistoricalTransactionImportService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    // ===== JSON IMPORT =====
    
    /**
     * Import transactions from JSON string
     */
    public ImportResult importFromJsonString(String jsonData, String batchDescription) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonData);
            return processJsonImport(rootNode, batchDescription, "manual_json_import");
        } catch (Exception e) {
            log.error("Failed to import from JSON string: {}", e.getMessage());
            return ImportResult.failure("Failed to parse JSON: " + e.getMessage());
        }
    }
    
    /**
     * Import transactions from JSON file
     */
    public ImportResult importFromJsonFile(MultipartFile file, String batchDescription) {
        try {
            JsonNode rootNode = objectMapper.readTree(file.getInputStream());
            return processJsonImport(rootNode, batchDescription, file.getOriginalFilename());
        } catch (Exception e) {
            log.error("Failed to import from JSON file {}: {}", file.getOriginalFilename(), e.getMessage());
            return ImportResult.failure("Failed to import JSON file: " + e.getMessage());
        }
    }
    
    /**
     * Process JSON import data
     */
    private ImportResult processJsonImport(JsonNode rootNode, String batchDescription, String sourceFilename) {
        String batchId = generateBatchId("JSON");
        ImportResult result = new ImportResult(batchId, sourceFilename);
        
        try {
            // Get current user
            User currentUser = getCurrentUser();
            
            // Extract batch info
            String importDescription = rootNode.has("source_description") ? 
                                     rootNode.get("source_description").asText() : batchDescription;
            
            // Process transactions array
            JsonNode transactionsNode = rootNode.get("transactions");
            if (transactionsNode == null || !transactionsNode.isArray()) {
                return ImportResult.failure("JSON must contain 'transactions' array");
            }
            
            for (JsonNode transactionNode : transactionsNode) {
                try {
                    HistoricalTransaction transaction = parseJsonTransaction(transactionNode, batchId, currentUser);
                    historicalTransactionRepository.save(transaction);
                    result.incrementSuccessful();
                } catch (Exception e) {
                    result.addError("Row " + result.getTotalProcessed() + ": " + e.getMessage());
                    result.incrementFailed();
                }
                result.incrementTotal();
            }
            
            log.info("JSON import completed: {} total, {} successful, {} failed", 
                    result.getTotalProcessed(), result.getSuccessfulImports(), result.getFailedImports());
            
        } catch (Exception e) {
            log.error("JSON import failed: {}", e.getMessage());
            return ImportResult.failure("Import failed: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Parse single transaction from JSON
     */
    private HistoricalTransaction parseJsonTransaction(JsonNode node, String batchId, User currentUser) {
        HistoricalTransaction transaction = new HistoricalTransaction();
        
        // Required fields
        transaction.setTransactionDate(parseDate(node.get("transaction_date").asText()));
        transaction.setAmount(new BigDecimal(node.get("amount").asText()));
        transaction.setDescription(node.get("description").asText());
        transaction.setTransactionType(parseTransactionType(node.get("transaction_type").asText()));
        
        // Optional fields
        if (node.has("category")) {
            transaction.setCategory(node.get("category").asText());
        }
        if (node.has("subcategory")) {
            transaction.setSubcategory(node.get("subcategory").asText());
        }
        if (node.has("bank_reference")) {
            transaction.setBankReference(node.get("bank_reference").asText());
        }
        if (node.has("payment_method")) {
            transaction.setPaymentMethod(node.get("payment_method").asText());
        }
        if (node.has("counterparty_name")) {
            transaction.setCounterpartyName(node.get("counterparty_name").asText());
        }
        if (node.has("source_reference")) {
            transaction.setSourceReference(node.get("source_reference").asText());
        }
        if (node.has("notes")) {
            transaction.setNotes(node.get("notes").asText());
        }
        
        // Set source and batch
        transaction.setSource(node.has("source") ? 
                            parseTransactionSource(node.get("source").asText()) : 
                            TransactionSource.historical_import);
        transaction.setImportBatchId(batchId);
        transaction.setCreatedByUser(currentUser);
        
        // Match property and customer
        if (node.has("property_reference")) {
            Property property = findPropertyByReference(node.get("property_reference").asText());
            transaction.setProperty(property);
        }
        if (node.has("customer_reference")) {
            Customer customer = findCustomerByReference(node.get("customer_reference").asText());
            transaction.setCustomer(customer);
        }
        
        return transaction;
    }
    
    // ===== CSV IMPORT =====
    
    /**
     * Import transactions from CSV file
     */
    public ImportResult importFromCsvFile(MultipartFile file, String batchDescription) {
        String batchId = generateBatchId("CSV");
        ImportResult result = new ImportResult(batchId, file.getOriginalFilename());
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            User currentUser = getCurrentUser();
            String headerLine = reader.readLine();
            
            if (headerLine == null) {
                return ImportResult.failure("CSV file is empty");
            }
            
            String[] headers = headerLine.split(",");
            Map<String, Integer> columnMap = buildColumnMap(headers);
            
            String line;
            int lineNumber = 1; // Start from 1 (header is line 0)
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                try {
                    String[] values = parseCsvLine(line);
                    HistoricalTransaction transaction = parseCsvTransaction(values, columnMap, batchId, currentUser);
                    historicalTransactionRepository.save(transaction);
                    result.incrementSuccessful();
                } catch (Exception e) {
                    result.addError("Line " + lineNumber + ": " + e.getMessage());
                    result.incrementFailed();
                }
                result.incrementTotal();
            }
            
            log.info("CSV import completed: {} total, {} successful, {} failed", 
                    result.getTotalProcessed(), result.getSuccessfulImports(), result.getFailedImports());
            
        } catch (IOException e) {
            log.error("Failed to read CSV file {}: {}", file.getOriginalFilename(), e.getMessage());
            return ImportResult.failure("Failed to read CSV file: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * Import transactions from CSV string (for paste functionality)
     */
    public ImportResult importFromCsvString(String csvData, String batchDescription) {
        return importFromCsvString(csvData, batchDescription, null);
    }

    /**
     * Import transactions from CSV string with optional batch ID (for batching multiple pastes)
     * This method is NOT transactional - each row is saved in its own transaction
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ImportResult importFromCsvString(String csvData, String batchDescription, String existingBatchId) {
        String batchId = (existingBatchId != null && !existingBatchId.isEmpty())
                         ? existingBatchId
                         : generateBatchId("CSV");
        ImportResult result = new ImportResult(batchId, "csv_paste_import");

        // Track fingerprints of transactions in THIS paste for Level 1 duplicate detection
        Set<String> currentPasteFingerprints = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(new java.io.StringReader(csvData))) {
            User currentUser = getCurrentUser();
            String headerLine = reader.readLine();

            if (headerLine == null) {
                return ImportResult.failure("CSV data is empty");
            }

            String[] headers = headerLine.split(",");
            Map<String, Integer> columnMap = buildColumnMap(headers);

            String line;
            int lineNumber = 1; // Start from 1 (header is line 0)

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue; // Skip empty lines
                }
                try {
                    String[] values = parseCsvLine(line);
                    HistoricalTransaction transaction = parseCsvTransaction(values, columnMap, batchId, currentUser);

                    // ===== 3-LEVEL DUPLICATE DETECTION =====
                    String duplicateLevel = checkForDuplicate(transaction, existingBatchId, currentPasteFingerprints);

                    if (duplicateLevel != null) {
                        // This is a duplicate - skip it
                        result.incrementSkipped(duplicateLevel);
                        String skipMsg = String.format("Line %d: Duplicate %s (Date: %s, Amount: %s, Desc: %s)",
                            lineNumber,
                            duplicateLevel.equals("paste") ? "within this upload" :
                            duplicateLevel.equals("batch") ? "in current batch session" :
                            "of existing transaction",
                            transaction.getTransactionDate(),
                            transaction.getAmount(),
                            transaction.getDescription().length() > 50 ?
                                transaction.getDescription().substring(0, 50) + "..." :
                                transaction.getDescription()
                        );
                        result.addSkipped(skipMsg);
                        log.debug("Skipped duplicate at line {}: {}", lineNumber, skipMsg);
                    } else {
                        // Not a duplicate - save it
                        saveTransactionInNewTransaction(transaction);
                        result.incrementSuccessful();

                        // Add to current paste fingerprints for Level 1 detection
                        currentPasteFingerprints.add(generateTransactionFingerprint(transaction));
                    }
                } catch (Exception e) {
                    // Build clear, user-friendly error message with CSV line preview
                    String errorMsg = buildUserFriendlyErrorMessage(e, lineNumber);
                    String linePreview = line.length() > 100 ? line.substring(0, 100) + "..." : line;
                    String fullError = String.format("Line %d: %s | CSV: %s", lineNumber, errorMsg, linePreview);
                    log.warn("Failed to import line {}: {}", lineNumber, errorMsg, e);
                    result.addError(fullError);
                    result.incrementFailed();
                }
                result.incrementTotal();
            }

            log.info("CSV string import completed: {} total, {} successful, {} failed, {} skipped (batch: {})",
                    result.getTotalProcessed(), result.getSuccessfulImports(),
                    result.getFailedImports(), result.getSkippedDuplicates(), batchId);

        } catch (IOException e) {
            log.error("Failed to read CSV string: {}", e.getMessage());
            return ImportResult.failure("Failed to read CSV data: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during CSV import: {}", e.getMessage(), e);
            return ImportResult.failure("Import failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Save a transaction in a new transaction (REQUIRES_NEW)
     * This prevents rollback-only errors when one transaction fails
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveTransactionInNewTransaction(HistoricalTransaction transaction) {
        historicalTransactionRepository.save(transaction);
        historicalTransactionRepository.flush(); // Force immediate persistence to catch errors
    }

    /**
     * Validate CSV string without database operations
     * This method performs all validation checks WITHOUT starting a transaction or saving to database
     */
    public ImportResult validateCsvString(String csvData) {
        ImportResult result = new ImportResult("VALIDATION", "csv_validation");

        try (BufferedReader reader = new BufferedReader(new java.io.StringReader(csvData))) {
            String headerLine = reader.readLine();

            if (headerLine == null) {
                result.addError("CSV data is empty");
                result.incrementFailed();
                return result;
            }

            String[] headers = headerLine.split(",");
            Map<String, Integer> columnMap = buildColumnMap(headers);

            // Validate required columns exist
            List<String> requiredColumns = List.of("transaction_date", "amount", "description", "transaction_type");
            List<String> missingColumns = new ArrayList<>();

            for (String required : requiredColumns) {
                if (!columnMap.containsKey(required)) {
                    missingColumns.add(required);
                }
            }

            if (!missingColumns.isEmpty()) {
                result.addError("Missing required columns: " + String.join(", ", missingColumns));
                result.incrementFailed();
                return result;
            }

            String line;
            int lineNumber = 1;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue; // Skip empty lines
                }

                try {
                    // Parse and validate the line WITHOUT database operations
                    String[] values = parseCsvLine(line);

                    // Validate required fields are present and parseable
                    validateCsvRow(values, columnMap, lineNumber);

                    result.incrementSuccessful();
                } catch (Exception e) {
                    String errorMsg = e.getMessage();
                    if (e.getCause() != null) {
                        errorMsg += " (Cause: " + e.getCause().getMessage() + ")";
                    }
                    log.debug("Validation error on line {}: {}", lineNumber, errorMsg);
                    result.addError("Line " + lineNumber + ": " + errorMsg);
                    result.incrementFailed();
                }
                result.incrementTotal();
            }

            log.info("CSV validation completed: {} total, {} valid, {} invalid",
                    result.getTotalProcessed(), result.getSuccessfulImports(), result.getFailedImports());

        } catch (IOException e) {
            log.error("Failed to read CSV string during validation: {}", e.getMessage());
            result.addError("Failed to read CSV data: " + e.getMessage());
            result.incrementFailed();
        } catch (Exception e) {
            log.error("Unexpected error during CSV validation: {}", e.getMessage(), e);
            result.addError("Validation failed: " + e.getMessage());
            result.incrementFailed();
        }

        return result;
    }

    /**
     * Validate a single CSV row without database operations
     * Checks all required fields are present and parseable
     */
    private void validateCsvRow(String[] values, Map<String, Integer> columnMap, int lineNumber) {
        // Validate transaction_date
        String dateStr = getValue(values, columnMap, "transaction_date");
        if (dateStr == null || dateStr.isEmpty()) {
            throw new IllegalArgumentException("transaction_date is required");
        }
        try {
            parseDate(dateStr);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid transaction_date format: " + dateStr + " (expected yyyy-MM-dd)");
        }

        // Validate amount
        String amountStr = getValue(values, columnMap, "amount");
        if (amountStr == null || amountStr.isEmpty()) {
            throw new IllegalArgumentException("amount is required");
        }
        try {
            new BigDecimal(amountStr);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid amount format: " + amountStr);
        }

        // Validate description
        String description = getValue(values, columnMap, "description");
        if (description == null || description.isEmpty()) {
            throw new IllegalArgumentException("description is required");
        }

        // Validate transaction_type
        String typeStr = getValue(values, columnMap, "transaction_type");
        if (typeStr == null || typeStr.isEmpty()) {
            throw new IllegalArgumentException("transaction_type is required");
        }
        try {
            parseTransactionType(typeStr);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid transaction_type: " + typeStr + " (expected: payment, invoice, fee, transfer, or adjustment)");
        }

        // Validate payment_source if present
        String paymentSource = getValue(values, columnMap, "payment_source");
        if (paymentSource != null && !paymentSource.isEmpty()) {
            try {
                validatePaymentSource(paymentSource);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid payment_source: " + paymentSource + " - " + e.getMessage());
            }
        }

        // Validate source if present
        String sourceStr = getValue(values, columnMap, "source");
        if (sourceStr != null && !sourceStr.isEmpty()) {
            try {
                parseTransactionSource(sourceStr);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid source: " + sourceStr);
            }
        }
    }

    /**
     * Build column mapping from CSV headers
     */
    private Map<String, Integer> buildColumnMap(String[] headers) {
        Map<String, Integer> columnMap = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            columnMap.put(headers[i].trim().toLowerCase(), i);
        }
        return columnMap;
    }
    
    /**
     * Parse CSV line handling quoted values
     */
    private String[] parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder currentValue = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            
            if (ch == '"') {
                inQuotes = !inQuotes;
            } else if (ch == ',' && !inQuotes) {
                values.add(currentValue.toString().trim());
                currentValue = new StringBuilder();
            } else {
                currentValue.append(ch);
            }
        }
        values.add(currentValue.toString().trim());
        
        return values.toArray(new String[0]);
    }
    
    /**
     * Parse single transaction from CSV values with enhanced dual-source and parking support
     */
    private HistoricalTransaction parseCsvTransaction(String[] values, Map<String, Integer> columnMap,
                                                    String batchId, User currentUser) {
        HistoricalTransaction transaction = new HistoricalTransaction();

        // Required fields
        transaction.setTransactionDate(parseDate(getValue(values, columnMap, "transaction_date")));
        transaction.setAmount(new BigDecimal(getValue(values, columnMap, "amount")));
        transaction.setDescription(getValue(values, columnMap, "description"));
        transaction.setTransactionType(parseTransactionType(getValue(values, columnMap, "transaction_type")));

        // Enhanced dual-source support
        String paymentSource = getValue(values, columnMap, "payment_source");
        if (paymentSource != null && !paymentSource.isEmpty()) {
            validatePaymentSource(paymentSource); // Validate payment source
            transaction.setPaymentMethod(paymentSource); // Store payment source in payment_method for now
        }

        // Note: Parking spaces are now handled as separate properties
        // No special parking space column processing needed

        // Optional fields
        setOptionalField(transaction::setCategory, values, columnMap, "category");
        setOptionalField(transaction::setSubcategory, values, columnMap, "subcategory");
        setOptionalField(transaction::setBankReference, values, columnMap, "bank_reference");
        setOptionalField(transaction::setPaymentMethod, values, columnMap, "payment_method");
        setOptionalField(transaction::setCounterpartyName, values, columnMap, "counterparty_name");
        setOptionalField(transaction::setSourceReference, values, columnMap, "source_reference");
        setOptionalField(transaction::setNotes, values, columnMap, "notes");
        
        // Set source
        String sourceStr = getValue(values, columnMap, "source");
        transaction.setSource(sourceStr != null ? 
                            parseTransactionSource(sourceStr) : 
                            TransactionSource.spreadsheet_import);
        
        transaction.setImportBatchId(batchId);
        transaction.setCreatedByUser(currentUser);
        
        // Match property and customer
        String propertyRef = getValue(values, columnMap, "property_reference");
        if (propertyRef != null && !propertyRef.isEmpty()) {
            Property property = findPropertyByReference(propertyRef);
            transaction.setProperty(property);
        }
        
        String customerRef = getValue(values, columnMap, "customer_reference");
        if (customerRef != null && !customerRef.isEmpty()) {
            Customer customer = findCustomerByReference(customerRef);
            transaction.setCustomer(customer);
        }
        
        return transaction;
    }
    
    // ===== UTILITY METHODS =====
    
    /**
     * Get value from CSV array using column map
     */
    private String getValue(String[] values, Map<String, Integer> columnMap, String columnName) {
        Integer index = columnMap.get(columnName);
        if (index == null || index >= values.length) {
            return null;
        }
        String value = values[index];
        return value.isEmpty() ? null : value;
    }
    
    /**
     * Set optional field if value exists
     */
    private void setOptionalField(java.util.function.Consumer<String> setter, String[] values, 
                                Map<String, Integer> columnMap, String columnName) {
        String value = getValue(values, columnMap, columnName);
        if (value != null) {
            setter.accept(value);
        }
    }
    
    /**
     * Parse date from string
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            throw new IllegalArgumentException("Transaction date is required");
        }
        
        // Try common date formats
        DateTimeFormatter[] formatters = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
        };
        
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (DateTimeParseException e) {
                // Try next format
            }
        }
        
        throw new IllegalArgumentException("Invalid date format: " + dateStr + 
                                         ". Supported formats: yyyy-MM-dd, dd/MM/yyyy, MM/dd/yyyy, dd-MM-yyyy, yyyy/MM/dd");
    }
    
    /**
     * Parse transaction type from string with smart mapping for maintenance-related types
     */
    private TransactionType parseTransactionType(String typeStr) {
        if (typeStr == null || typeStr.isEmpty()) {
            throw new IllegalArgumentException("Transaction type is required");
        }

        // Normalize the type string
        String normalizedType = typeStr.toLowerCase().trim();

        // Smart mapping for maintenance/expense-related transaction types
        // Map maintenance to expense to match database enum
        if (normalizedType.contains("maintenance") || normalizedType.contains("repair") ||
            normalizedType.contains("contractor") || normalizedType.contains("upkeep") ||
            normalizedType.contains("expense")) {
            return TransactionType.expense;
        }

        // Smart mapping for other common variations
        switch (normalizedType) {
            case "payment_to_contractor":
            case "contractor_payment":
            case "service_payment":
            case "cost":
            case "expenditure":
                return TransactionType.expense;
            case "rent":
            case "rental":
            case "rental_payment":
            case "parking":
                return TransactionType.payment;
            case "management_fee":
            case "commission":
            case "service_fee":
                return TransactionType.fee;
            case "owner_payment":
            case "payment_to_beneficiary":
                return TransactionType.payment;
            default:
                break;
        }

        try {
            return TransactionType.valueOf(normalizedType);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid transaction type: " + typeStr +
                                             ". Valid types: " + Arrays.toString(TransactionType.values()) +
                                             ". Note: maintenance-related types are automatically mapped to 'maintenance'.");
        }
    }

    /**
     * Validate payment source value
     */
    private void validatePaymentSource(String paymentSource) {
        if (paymentSource == null || paymentSource.isEmpty()) {
            return; // Optional field
        }

        String normalizedSource = paymentSource.toUpperCase().trim();
        switch (normalizedSource) {
            case "OLD_ACCOUNT":
            case "PAYPROP":
            case "BOTH":
                break; // Valid values
            default:
                throw new IllegalArgumentException("Invalid payment source: " + paymentSource +
                                                 ". Valid sources: OLD_ACCOUNT, PAYPROP, BOTH");
        }
    }
    
    /**
     * Parse transaction source from string
     */
    private TransactionSource parseTransactionSource(String sourceStr) {
        if (sourceStr == null || sourceStr.isEmpty()) {
            return TransactionSource.historical_import;
        }
        
        try {
            return TransactionSource.valueOf(sourceStr.toLowerCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid transaction source '{}', defaulting to historical_import", sourceStr);
            return TransactionSource.historical_import;
        }
    }
    
    /**
     * Find property by various reference patterns
     */
    private Property findPropertyByReference(String reference) {
        if (reference == null || reference.isEmpty()) {
            return null;
        }
        
        // Try exact property name match
        List<Property> properties = propertyService.findAll();
        for (Property property : properties) {
            if (reference.equalsIgnoreCase(property.getPropertyName())) {
                return property;
            }
            // Try address match
            if (property.getAddressLine1() != null && 
                reference.toLowerCase().contains(property.getAddressLine1().toLowerCase())) {
                return property;
            }
            // Try postcode match
            if (property.getPostcode() != null && 
                reference.toLowerCase().contains(property.getPostcode().toLowerCase())) {
                return property;
            }
        }
        
        log.warn("Could not find property for reference: {}", reference);
        return null;
    }
    
    /**
     * Find customer by various reference patterns
     */
    private Customer findCustomerByReference(String reference) {
        if (reference == null || reference.isEmpty()) {
            return null;
        }
        
        // Try email match first
        Customer customer = customerService.findByEmail(reference);
        if (customer != null) {
            return customer;
        }
        
        // Try name match
        List<Customer> customers = customerService.findByNameContainingIgnoreCase(reference);
        if (!customers.isEmpty()) {
            return customers.get(0); // Return first match
        }
        
        log.warn("Could not find customer for reference: {}", reference);
        return null;
    }
    
    /**
     * Generate unique batch ID
     */
    private String generateBatchId(String source) {
        return "HIST_" + source + "_" +
               LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }

    /**
     * Get recent batch summaries
     */
    public List<Object[]> getRecentBatchSummaries(org.springframework.data.domain.Pageable pageable) {
        return historicalTransactionRepository.findRecentBatchSummaries(pageable);
    }

    /**
     * Count transactions in a batch
     */
    public long countTransactionsInBatch(String batchId) {
        return historicalTransactionRepository.countByImportBatchId(batchId);
    }

    /**
     * Delete all transactions in a batch
     */
    @Transactional
    public void deleteBatch(String batchId) {
        historicalTransactionRepository.deleteByImportBatchId(batchId);
    }
    
    /**
     * Get current authenticated user
     */
    private User getCurrentUser() {
        // TODO: Fix auth handling - using default user for now
        return null; // TODO: Fix auth - temporarily disabled
    }

    /**
     * Check if transaction is duplicate
     * Returns: null if not duplicate, otherwise returns description of where duplicate was found
     */
    private String checkForDuplicate(HistoricalTransaction transaction, String currentBatchId,
                                    Set<String> currentPasteFingerprints) {
        // Generate fingerprint for this transaction
        String fingerprint = generateTransactionFingerprint(transaction);

        // LEVEL 1: Check within current paste
        if (currentPasteFingerprints.contains(fingerprint)) {
            return "paste";
        }

        // LEVEL 2: Check within current batch session (if continuing a batch)
        if (currentBatchId != null && !currentBatchId.isEmpty()) {
            List<HistoricalTransaction> duplicatesInBatch = historicalTransactionRepository.findDuplicateInBatch(
                currentBatchId,
                transaction.getTransactionDate(),
                transaction.getAmount(),
                transaction.getDescription(),
                transaction.getTransactionType(),
                transaction.getProperty() != null ? transaction.getProperty().getId() : null,
                transaction.getCustomer() != null ? transaction.getCustomer().getCustomerId() : null
            );

            if (!duplicatesInBatch.isEmpty()) {
                return "batch";
            }
        }

        // LEVEL 3: Check across all historical transactions
        List<HistoricalTransaction> duplicatesInDatabase = historicalTransactionRepository.findDuplicateTransaction(
            transaction.getTransactionDate(),
            transaction.getAmount(),
            transaction.getDescription(),
            transaction.getTransactionType(),
            transaction.getProperty() != null ? transaction.getProperty().getId() : null,
            transaction.getCustomer() != null ? transaction.getCustomer().getCustomerId() : null
        );

        if (!duplicatesInDatabase.isEmpty()) {
            return "database";
        }

        return null; // Not a duplicate
    }

    /**
     * Generate unique fingerprint for transaction
     * Used for in-memory duplicate detection within same paste
     */
    private String generateTransactionFingerprint(HistoricalTransaction transaction) {
        return String.format("%s|%s|%s|%s|%s|%s",
            transaction.getTransactionDate(),
            transaction.getAmount(),
            transaction.getDescription(),
            transaction.getTransactionType(),
            transaction.getProperty() != null ? transaction.getProperty().getId() : "null",
            transaction.getCustomer() != null ? transaction.getCustomer().getCustomerId() : "null"
        );
    }

    /**
     * Build user-friendly error message from exception
     */
    private String buildUserFriendlyErrorMessage(Exception e, int lineNumber) {
        String message = e.getMessage();

        // Handle common exception types with clearer messages
        if (e instanceof IllegalArgumentException) {
            // Check if it's a transaction_type validation error
            if (message != null && message.contains("Invalid transaction type")) {
                return message; // Already has detailed info about valid types
            }
            // These are usually validation errors - return as is
            return message != null ? message : "Invalid data format";
        } else if (e instanceof NullPointerException) {
            return "Required field is missing";
        } else if (e instanceof NumberFormatException) {
            return "Invalid number format in amount field";
        } else if (e instanceof DateTimeParseException) {
            return "Invalid date format - use yyyy-MM-dd, dd/MM/yyyy, or MM/dd/yyyy";
        } else if (e.getClass().getName().contains("DataIntegrityViolation")) {
            return "Database constraint violation - possibly duplicate transaction or invalid reference";
        } else if (e.getClass().getName().contains("ConstraintViolation")) {
            return "Data validation failed - " + (message != null ? message : "constraint violation");
        }

        // For database exceptions, extract the meaningful part
        if (message != null && message.contains("could not execute statement")) {
            if (message.contains("Duplicate entry")) {
                return "Duplicate transaction detected";
            } else if (message.contains("foreign key constraint")) {
                return "Invalid property or customer reference";
            } else if (message.contains("Data too long") || message.contains("Data truncated")) {
                // Extract which column had the issue
                if (message.contains("transaction_type")) {
                    return "Invalid transaction_type - must be one of: payment, invoice, fee, transfer, adjustment, maintenance";
                } else if (message.contains("description")) {
                    return "Description too long - maximum 500 characters";
                } else if (message.contains("category")) {
                    return "Category value too long - maximum 100 characters";
                } else {
                    return "Field value too long - please shorten the data";
                }
            }
        }

        // Build error message with cause chain if available
        if (e.getCause() != null) {
            String causeMsg = e.getCause().getMessage();
            if (causeMsg != null && !causeMsg.equals(message)) {
                // Extract meaningful error from cause
                if (causeMsg.contains("Duplicate entry")) {
                    return "Duplicate transaction detected";
                } else if (causeMsg.contains("foreign key constraint")) {
                    return "Invalid property or customer reference";
                } else {
                    return message + " (Caused by: " + causeMsg.substring(0, Math.min(100, causeMsg.length())) + ")";
                }
            }
        }

        // Return the original message if we can't simplify it
        return message != null ? message : "Unknown error - check logs for details";
    }
    
    // ===== RESULT CLASSES =====
    
    /**
     * Import result tracking
     */
    public static class ImportResult {
        private final String batchId;
        private final String sourceFilename;
        private int totalProcessed = 0;
        private int successfulImports = 0;
        private int failedImports = 0;
        private int skippedDuplicates = 0;
        private int skippedDuplicatesInPaste = 0;
        private int skippedDuplicatesInBatch = 0;
        private int skippedDuplicatesInDatabase = 0;
        private final List<String> errors = new ArrayList<>();
        private final List<String> skippedTransactions = new ArrayList<>();
        private final LocalDateTime importTime = LocalDateTime.now();
        private boolean success = true;

        public ImportResult(String batchId, String sourceFilename) {
            this.batchId = batchId;
            this.sourceFilename = sourceFilename;
        }

        public static ImportResult failure(String errorMessage) {
            ImportResult result = new ImportResult("FAILED", "");
            result.success = false;
            result.addError(errorMessage);
            return result;
        }

        public void incrementTotal() { totalProcessed++; }
        public void incrementSuccessful() { successfulImports++; }
        public void incrementFailed() { failedImports++; }
        public void incrementSkipped(String level) {
            skippedDuplicates++;
            switch (level) {
                case "paste": skippedDuplicatesInPaste++; break;
                case "batch": skippedDuplicatesInBatch++; break;
                case "database": skippedDuplicatesInDatabase++; break;
            }
        }
        public void addError(String error) { errors.add(error); }
        public void addSkipped(String skipped) { skippedTransactions.add(skipped); }

        // Getters
        public String getBatchId() { return batchId; }
        public String getSourceFilename() { return sourceFilename; }
        public int getTotalProcessed() { return totalProcessed; }
        public int getSuccessfulImports() { return successfulImports; }
        public int getFailedImports() { return failedImports; }
        public int getSkippedDuplicates() { return skippedDuplicates; }
        public int getSkippedDuplicatesInPaste() { return skippedDuplicatesInPaste; }
        public int getSkippedDuplicatesInBatch() { return skippedDuplicatesInBatch; }
        public int getSkippedDuplicatesInDatabase() { return skippedDuplicatesInDatabase; }
        public List<String> getErrors() { return errors; }
        public List<String> getSkippedTransactions() { return skippedTransactions; }
        public LocalDateTime getImportTime() { return importTime; }
        public boolean isSuccess() { return success && failedImports == 0; }

        public String getSummary() {
            if (skippedDuplicates > 0) {
                return String.format("Batch %s: %d total, %d successful, %d failed, %d skipped (duplicates)",
                                   batchId, totalProcessed, successfulImports, failedImports, skippedDuplicates);
            }
            return String.format("Batch %s: %d total, %d successful, %d failed",
                               batchId, totalProcessed, successfulImports, failedImports);
        }
    }
}

/**
 * Example Usage:
 * 
 * JSON Format:
 * {
 *   "source_description": "Historical bank data 2023",
 *   "transactions": [
 *     {
 *       "transaction_date": "2023-01-15",
 *       "amount": -1200.00,
 *       "description": "Rent payment - 123 Main St",
 *       "transaction_type": "payment",
 *       "category": "rent",
 *       "bank_reference": "TXN123456",
 *       "property_reference": "123 Main St",
 *       "customer_reference": "john@email.com",
 *       "source": "bank_import"
 *     }
 *   ]
 * }
 * 
 * CSV Format:
 * transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference
 * 2023-01-15,-1200.00,"Rent payment",payment,rent,"123 Main St","john@email.com",TXN123456
 * 2023-02-01,50.00,"Interest payment",deposit,interest,,,INT789
 */
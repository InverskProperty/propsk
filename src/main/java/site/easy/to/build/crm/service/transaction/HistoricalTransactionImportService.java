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
import site.easy.to.build.crm.repository.PropertyRepository;
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
    private PropertyRepository propertyRepository;

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
     * Clean CSV value by removing quotes and trimming whitespace
     */
    private String cleanCsvValue(String value) {
        if (value == null) {
            return null;
        }
        // Remove surrounding quotes and trim
        value = value.trim();
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value.trim();
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

        // Try exact property name match (case-insensitive) using repository method
        Property property = propertyRepository.findByPropertyNameIgnoreCase(reference.trim());
        if (property != null) {
            return property;
        }

        // Fallback: Try address or postcode match (for more flexible matching)
        List<Property> properties = propertyService.findAll();
        for (Property prop : properties) {
            // Try address match
            if (prop.getAddressLine1() != null &&
                reference.toLowerCase().contains(prop.getAddressLine1().toLowerCase())) {
                return prop;
            }
            // Try postcode match
            if (prop.getPostcode() != null &&
                reference.toLowerCase().contains(prop.getPostcode().toLowerCase())) {
                return prop;
            }
        }

        log.warn("⚠️ Property lookup failed for reference: '{}' - Transaction will be imported without property link", reference);
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
     *
     * IMPORTANT: If property lookup failed (property = null), we SKIP duplicate detection
     * to avoid false positives when multiple transactions have no property link.
     */
    private String checkForDuplicate(HistoricalTransaction transaction, String currentBatchId,
                                    Set<String> currentPasteFingerprints) {
        // Generate fingerprint for this transaction
        String fingerprint = generateTransactionFingerprint(transaction);

        // LEVEL 1: Check within current paste
        if (currentPasteFingerprints.contains(fingerprint)) {
            return "paste";
        }

        // CRITICAL: If property is NULL (property lookup failed), skip Level 2 & 3 duplicate detection
        // Reason: Multiple transactions without property links should NOT be treated as duplicates
        // They are likely different transactions for different properties that couldn't be matched
        if (transaction.getProperty() == null) {
            log.debug("Skipping Level 2 & 3 duplicate detection for transaction without property link: {}",
                     transaction.getDescription());
            return null; // Not a duplicate (or we can't reliably tell)
        }

        // LEVEL 2: Check within current batch session (if continuing a batch)
        if (currentBatchId != null && !currentBatchId.isEmpty()) {
            List<HistoricalTransaction> duplicatesInBatch = historicalTransactionRepository.findDuplicateInBatch(
                currentBatchId,
                transaction.getTransactionDate(),
                transaction.getAmount(),
                transaction.getDescription(),
                transaction.getTransactionType(),
                transaction.getProperty().getId(), // Now guaranteed to be non-null
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
            transaction.getProperty().getId(), // Now guaranteed to be non-null
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
    
    // ===== REVIEW CLASSES =====

    /**
     * Review queue for human verification workflow
     */
    public static class ReviewQueue {
        private final List<TransactionReview> reviews;
        private int totalRows = 0;
        private int perfectMatches = 0;
        private int needsReview = 0;
        private int hasIssues = 0;
        private String batchId;

        public ReviewQueue(String batchId) {
            this.batchId = batchId;
            this.reviews = new ArrayList<>();
        }

        public void addReview(TransactionReview review) {
            reviews.add(review);
            totalRows++;
            switch (review.getStatus()) {
                case PERFECT: perfectMatches++; break;
                case AMBIGUOUS_PROPERTY:
                case AMBIGUOUS_CUSTOMER:
                case POTENTIAL_DUPLICATE:
                    needsReview++; break;
                case MISSING_PROPERTY:
                case MISSING_CUSTOMER:
                case VALIDATION_ERROR:
                    hasIssues++; break;
            }
        }

        // Getters
        public List<TransactionReview> getReviews() { return reviews; }
        public int getTotalRows() { return totalRows; }
        public int getPerfectMatches() { return perfectMatches; }
        public int getNeedsReview() { return needsReview; }
        public int getHasIssues() { return hasIssues; }
        public String getBatchId() { return batchId; }
    }

    /**
     * Individual transaction review item
     */
    public static class TransactionReview {
        private int lineNumber;
        private String csvLine;
        private ReviewStatus status;
        private List<PropertyOption> propertyOptions;
        private List<CustomerOption> customerOptions;
        private DuplicateInfo duplicateInfo;
        private Map<String, Object> parsedData;
        private String errorMessage;

        // User selections (filled during review)
        private Long selectedPropertyId;
        private Long selectedCustomerId;
        private boolean skipDuplicate;
        private String userNote;

        public TransactionReview(int lineNumber, String csvLine) {
            this.lineNumber = lineNumber;
            this.csvLine = csvLine;
            this.propertyOptions = new ArrayList<>();
            this.customerOptions = new ArrayList<>();
            this.parsedData = new HashMap<>();
        }

        // Getters and Setters
        public int getLineNumber() { return lineNumber; }
        public String getCsvLine() { return csvLine; }
        public ReviewStatus getStatus() { return status; }
        public void setStatus(ReviewStatus status) { this.status = status; }
        public List<PropertyOption> getPropertyOptions() { return propertyOptions; }
        public List<CustomerOption> getCustomerOptions() { return customerOptions; }
        public DuplicateInfo getDuplicateInfo() { return duplicateInfo; }
        public void setDuplicateInfo(DuplicateInfo duplicateInfo) { this.duplicateInfo = duplicateInfo; }
        public Map<String, Object> getParsedData() { return parsedData; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public Long getSelectedPropertyId() { return selectedPropertyId; }
        public void setSelectedPropertyId(Long selectedPropertyId) { this.selectedPropertyId = selectedPropertyId; }
        public Long getSelectedCustomerId() { return selectedCustomerId; }
        public void setSelectedCustomerId(Long selectedCustomerId) { this.selectedCustomerId = selectedCustomerId; }
        public boolean isSkipDuplicate() { return skipDuplicate; }
        public void setSkipDuplicate(boolean skipDuplicate) { this.skipDuplicate = skipDuplicate; }
        public String getUserNote() { return userNote; }
        public void setUserNote(String userNote) { this.userNote = userNote; }
    }

    /**
     * Property matching option
     */
    public static class PropertyOption {
        private Long propertyId;
        private String propertyName;
        private String addressLine1;
        private String postcode;
        private int matchScore; // 100 = perfect, lower = fuzzy

        public PropertyOption(Property property, int matchScore) {
            this.propertyId = property.getId();
            this.propertyName = property.getPropertyName();
            this.addressLine1 = property.getAddressLine1();
            this.postcode = property.getPostcode();
            this.matchScore = matchScore;
        }

        // Getters
        public Long getPropertyId() { return propertyId; }
        public String getPropertyName() { return propertyName; }
        public String getAddressLine1() { return addressLine1; }
        public String getPostcode() { return postcode; }
        public int getMatchScore() { return matchScore; }
    }

    /**
     * Customer matching option
     */
    public static class CustomerOption {
        private Long customerId;
        private String fullName;
        private String email;
        private String phone;
        private int matchScore;

        public CustomerOption(Customer customer, int matchScore) {
            this.customerId = customer.getCustomerId();
            this.fullName = customer.getFirstName() + " " + customer.getLastName();
            this.email = customer.getEmail();
            this.phone = customer.getPhone();
            this.matchScore = matchScore;
        }

        // Getters
        public Long getCustomerId() { return customerId; }
        public String getFullName() { return fullName; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }
        public int getMatchScore() { return matchScore; }
    }

    /**
     * Duplicate transaction information
     */
    public static class DuplicateInfo {
        private Long existingTransactionId;
        private LocalDate transactionDate;
        private BigDecimal amount;
        private String description;
        private String propertyName;
        private String batchId;

        public DuplicateInfo(HistoricalTransaction existing) {
            this.existingTransactionId = existing.getId();
            this.transactionDate = existing.getTransactionDate();
            this.amount = existing.getAmount();
            this.description = existing.getDescription();
            this.propertyName = existing.getProperty() != null ? existing.getProperty().getPropertyName() : "Unknown";
            this.batchId = existing.getImportBatchId();
        }

        // Getters
        public Long getExistingTransactionId() { return existingTransactionId; }
        public LocalDate getTransactionDate() { return transactionDate; }
        public BigDecimal getAmount() { return amount; }
        public String getDescription() { return description; }
        public String getPropertyName() { return propertyName; }
        public String getBatchId() { return batchId; }
    }

    /**
     * Review status enum
     */
    public enum ReviewStatus {
        PERFECT,                 // Everything matched perfectly
        AMBIGUOUS_PROPERTY,      // Multiple property matches
        AMBIGUOUS_CUSTOMER,      // Multiple customer matches
        MISSING_PROPERTY,        // No property found
        MISSING_CUSTOMER,        // No customer found
        POTENTIAL_DUPLICATE,     // Matches existing transaction
        VALIDATION_ERROR         // Parse/validation error
    }

    // ===== VALIDATION & REVIEW METHODS =====

    /**
     * Validate CSV data and create review queue for human verification
     * This is Stage 1: Parse and analyze without importing
     */
    public ReviewQueue validateForReview(String csvData, String batchId) {
        log.info("🔍 [REVIEW-VALIDATE] Starting validation for batchId: {}", batchId);
        log.debug("CSV data length: {} characters", csvData != null ? csvData.length() : 0);

        ReviewQueue queue = new ReviewQueue(batchId);

        if (csvData == null || csvData.trim().isEmpty()) {
            log.warn("⚠️ [REVIEW-VALIDATE] Empty CSV data provided");
            return queue;
        }

        String[] lines = csvData.split("\\r?\\n");
        log.info("📊 [REVIEW-VALIDATE] Processing {} lines", lines.length);

        Set<String> currentPasteFingerprints = new HashSet<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            int lineNumber = i + 1;

            // Skip empty lines
            if (line.isEmpty()) {
                log.debug("⏭️ [REVIEW-VALIDATE] Line {}: Skipping empty line", lineNumber);
                continue;
            }

            log.debug("🔍 [REVIEW-VALIDATE] Line {}: Parsing...", lineNumber);
            TransactionReview review = new TransactionReview(lineNumber, line);

            try {
                // Parse the CSV line
                String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                log.debug("📋 [REVIEW-VALIDATE] Line {}: Split into {} parts", lineNumber, parts.length);

                if (parts.length < 6) {
                    review.setStatus(ReviewStatus.VALIDATION_ERROR);
                    review.setErrorMessage("Insufficient columns - expected at least 6 columns (date, amount, description, type, property, customer)");
                    queue.addReview(review);
                    continue;
                }

                // Extract and validate fields
                String dateStr = cleanCsvValue(parts[0]);
                String amountStr = cleanCsvValue(parts[1]);
                String description = cleanCsvValue(parts[2]);
                String typeStr = cleanCsvValue(parts[3]);
                String propertyRef = cleanCsvValue(parts[4]);
                String customerRef = cleanCsvValue(parts[5]);

                // Store parsed data
                review.getParsedData().put("date", dateStr);
                review.getParsedData().put("amount", amountStr);
                review.getParsedData().put("description", description);
                review.getParsedData().put("type", typeStr);
                review.getParsedData().put("propertyRef", propertyRef);
                review.getParsedData().put("customerRef", customerRef);

                // Validate date
                LocalDate transactionDate = parseDate(dateStr);
                if (transactionDate == null) {
                    review.setStatus(ReviewStatus.VALIDATION_ERROR);
                    review.setErrorMessage("Invalid date format: " + dateStr);
                    queue.addReview(review);
                    continue;
                }
                review.getParsedData().put("parsedDate", transactionDate);

                // Validate amount
                BigDecimal amount;
                try {
                    amount = new BigDecimal(amountStr.replace(",", "").replace("$", "").replace("£", ""));
                    review.getParsedData().put("parsedAmount", amount);
                } catch (NumberFormatException e) {
                    review.setStatus(ReviewStatus.VALIDATION_ERROR);
                    review.setErrorMessage("Invalid amount: " + amountStr);
                    queue.addReview(review);
                    continue;
                }

                // Validate transaction type
                TransactionType transactionType;
                try {
                    transactionType = parseTransactionType(typeStr);
                    review.getParsedData().put("parsedType", transactionType);
                } catch (IllegalArgumentException e) {
                    review.setStatus(ReviewStatus.VALIDATION_ERROR);
                    review.setErrorMessage(e.getMessage());
                    queue.addReview(review);
                    continue;
                }

                // Find property matches
                log.debug("🏠 [REVIEW-VALIDATE] Line {}: Looking for property '{}'", lineNumber, propertyRef);
                List<PropertyOption> propertyMatches = findPropertyMatches(propertyRef);
                log.info("🏠 [REVIEW-VALIDATE] Line {}: Found {} property matches for '{}'", lineNumber, propertyMatches.size(), propertyRef);

                if (!propertyMatches.isEmpty()) {
                    review.getPropertyOptions().addAll(propertyMatches);
                    if (propertyMatches.size() > 1) {
                        review.setStatus(ReviewStatus.AMBIGUOUS_PROPERTY);
                        log.warn("⚠️ [REVIEW-VALIDATE] Line {}: Ambiguous property - {} matches", lineNumber, propertyMatches.size());
                    } else {
                        log.debug("✅ [REVIEW-VALIDATE] Line {}: Single property match found (score: {})",
                            lineNumber, propertyMatches.get(0).getMatchScore());
                    }
                } else if (propertyRef != null && !propertyRef.isEmpty()) {
                    review.setStatus(ReviewStatus.MISSING_PROPERTY);
                    log.warn("❌ [REVIEW-VALIDATE] Line {}: Property '{}' not found", lineNumber, propertyRef);
                }

                // Find customer matches
                log.debug("👤 [REVIEW-VALIDATE] Line {}: Looking for customer '{}'", lineNumber, customerRef);
                List<CustomerOption> customerMatches = findCustomerMatches(customerRef);
                log.info("👤 [REVIEW-VALIDATE] Line {}: Found {} customer matches for '{}'", lineNumber, customerMatches.size(), customerRef);

                if (!customerMatches.isEmpty()) {
                    review.getCustomerOptions().addAll(customerMatches);
                    if (customerMatches.size() > 1 && review.getStatus() != ReviewStatus.AMBIGUOUS_PROPERTY) {
                        review.setStatus(ReviewStatus.AMBIGUOUS_CUSTOMER);
                        log.warn("⚠️ [REVIEW-VALIDATE] Line {}: Ambiguous customer - {} matches", lineNumber, customerMatches.size());
                    } else {
                        log.debug("✅ [REVIEW-VALIDATE] Line {}: Single customer match found (score: {})",
                            lineNumber, customerMatches.get(0).getMatchScore());
                    }
                } else if (customerRef != null && !customerRef.isEmpty()) {
                    if (review.getStatus() == null) {
                        review.setStatus(ReviewStatus.MISSING_CUSTOMER);
                        log.warn("❌ [REVIEW-VALIDATE] Line {}: Customer '{}' not found", lineNumber, customerRef);
                    }
                }

                // Check for duplicates (only if we have property match)
                if (!propertyMatches.isEmpty() && propertyMatches.get(0).getMatchScore() == 100) {
                    Property matchedProperty = propertyRepository.findById(propertyMatches.get(0).getPropertyId()).orElse(null);
                    if (matchedProperty != null) {
                        DuplicateInfo duplicateInfo = checkForDuplicateInReview(
                            transactionDate, amount, description, transactionType,
                            matchedProperty.getId(),
                            !customerMatches.isEmpty() ? customerMatches.get(0).getCustomerId() : null,
                            batchId,
                            currentPasteFingerprints
                        );

                        if (duplicateInfo != null) {
                            review.setDuplicateInfo(duplicateInfo);
                            if (review.getStatus() == null) {
                                review.setStatus(ReviewStatus.POTENTIAL_DUPLICATE);
                            }
                        }
                    }
                }

                // If no issues found, mark as PERFECT
                if (review.getStatus() == null) {
                    if (!propertyMatches.isEmpty() && propertyMatches.size() == 1 && propertyMatches.get(0).getMatchScore() == 100 &&
                        !customerMatches.isEmpty() && customerMatches.size() == 1 && customerMatches.get(0).getMatchScore() == 100) {
                        review.setStatus(ReviewStatus.PERFECT);
                        // Auto-select the perfect matches
                        review.setSelectedPropertyId(propertyMatches.get(0).getPropertyId());
                        review.setSelectedCustomerId(customerMatches.get(0).getCustomerId());
                        log.info("✅ [REVIEW-VALIDATE] Line {}: PERFECT match - property and customer both 100%", lineNumber);
                    } else {
                        // Some fields matched but not perfect
                        review.setStatus(ReviewStatus.PERFECT);
                        log.debug("✅ [REVIEW-VALIDATE] Line {}: PERFECT (with partial matches)", lineNumber);
                    }
                }

                log.info("📝 [REVIEW-VALIDATE] Line {}: Final status = {}", lineNumber, review.getStatus());

            } catch (Exception e) {
                review.setStatus(ReviewStatus.VALIDATION_ERROR);
                review.setErrorMessage("Parse error: " + e.getMessage());
                log.error("❌ [REVIEW-VALIDATE] Line {}: Parse error - {}", lineNumber, e.getMessage(), e);
            }

            queue.addReview(review);
        }

        log.info("✅ [REVIEW-VALIDATE] Validation complete: {} total, {} perfect, {} needs review, {} issues",
            queue.getTotalRows(), queue.getPerfectMatches(), queue.getNeedsReview(), queue.getHasIssues());

        return queue;
    }

    /**
     * Find property matches with fuzzy matching
     */
    private List<PropertyOption> findPropertyMatches(String propertyRef) {
        List<PropertyOption> matches = new ArrayList<>();

        if (propertyRef == null || propertyRef.trim().isEmpty()) {
            return matches;
        }

        String cleanRef = propertyRef.trim();

        // Try exact match (case-insensitive)
        Property exactMatch = propertyRepository.findByPropertyNameIgnoreCase(cleanRef);
        if (exactMatch != null) {
            matches.add(new PropertyOption(exactMatch, 100)); // Perfect match
            return matches;
        }

        // Try fuzzy matching on property name
        List<Property> allProperties = propertyService.findAll();
        for (Property prop : allProperties) {
            if (prop.getPropertyName() != null) {
                String propName = prop.getPropertyName().toLowerCase();
                String searchRef = cleanRef.toLowerCase();

                // Partial match in property name
                if (propName.contains(searchRef) || searchRef.contains(propName)) {
                    int score = calculateMatchScore(propName, searchRef);
                    if (score > 50) { // Only include reasonable matches
                        matches.add(new PropertyOption(prop, score));
                    }
                }
            }

            // Try address matching
            if (prop.getAddressLine1() != null && cleanRef.toLowerCase().contains(prop.getAddressLine1().toLowerCase())) {
                matches.add(new PropertyOption(prop, 80));
            }

            // Try postcode matching
            if (prop.getPostcode() != null && cleanRef.toLowerCase().contains(prop.getPostcode().toLowerCase())) {
                matches.add(new PropertyOption(prop, 70));
            }
        }

        // Sort by match score (highest first) and remove duplicates
        matches.sort((a, b) -> Integer.compare(b.getMatchScore(), a.getMatchScore()));

        // Remove duplicates (same property ID)
        Set<Long> seenIds = new HashSet<>();
        matches.removeIf(opt -> !seenIds.add(opt.getPropertyId()));

        // Limit to top 5 matches
        if (matches.size() > 5) {
            matches = matches.subList(0, 5);
        }

        return matches;
    }

    /**
     * Find customer matches with fuzzy matching
     */
    private List<CustomerOption> findCustomerMatches(String customerRef) {
        List<CustomerOption> matches = new ArrayList<>();

        if (customerRef == null || customerRef.trim().isEmpty()) {
            return matches;
        }

        String cleanRef = customerRef.trim();

        // Try exact match on customer ID
        try {
            Long customerId = Long.parseLong(cleanRef);
            Customer customer = customerService.findByCustomerId(customerId);
            if (customer != null) {
                matches.add(new CustomerOption(customer, 100));
                return matches;
            }
        } catch (NumberFormatException e) {
            // Not a numeric ID, continue with name matching
        }

        // Try fuzzy matching on customer name
        List<Customer> allCustomers = customerService.findAll();
        for (Customer cust : allCustomers) {
            String fullName = (cust.getFirstName() + " " + cust.getLastName()).toLowerCase();
            String searchRef = cleanRef.toLowerCase();

            // Exact name match
            if (fullName.equals(searchRef)) {
                matches.add(new CustomerOption(cust, 100));
                continue;
            }

            // Partial name match
            if (fullName.contains(searchRef) || searchRef.contains(fullName)) {
                int score = calculateMatchScore(fullName, searchRef);
                if (score > 50) {
                    matches.add(new CustomerOption(cust, score));
                }
            }

            // Email match
            if (cust.getEmail() != null && cust.getEmail().equalsIgnoreCase(cleanRef)) {
                matches.add(new CustomerOption(cust, 95));
            }
        }

        // Sort by match score and remove duplicates
        matches.sort((a, b) -> Integer.compare(b.getMatchScore(), a.getMatchScore()));

        Set<Long> seenIds = new HashSet<>();
        matches.removeIf(opt -> !seenIds.add(opt.getCustomerId()));

        // Limit to top 5 matches
        if (matches.size() > 5) {
            matches = matches.subList(0, 5);
        }

        return matches;
    }

    /**
     * Calculate match score between two strings (0-100)
     */
    private int calculateMatchScore(String s1, String s2) {
        s1 = s1.toLowerCase();
        s2 = s2.toLowerCase();

        if (s1.equals(s2)) {
            return 100;
        }

        // Simple similarity based on common characters and length
        int commonChars = 0;
        for (char c : s1.toCharArray()) {
            if (s2.indexOf(c) >= 0) {
                commonChars++;
            }
        }

        int maxLen = Math.max(s1.length(), s2.length());
        return (commonChars * 100) / maxLen;
    }

    /**
     * Check for duplicates during review phase
     */
    private DuplicateInfo checkForDuplicateInReview(LocalDate transactionDate, BigDecimal amount,
                                                     String description, TransactionType transactionType,
                                                     Long propertyId, Long customerId, String batchId,
                                                     Set<String> currentPasteFingerprints) {
        // Generate fingerprint
        String fingerprint = String.format("%s|%s|%s|%s|%s|%s",
            transactionDate, amount, description, transactionType,
            propertyId != null ? propertyId : "null",
            customerId != null ? customerId : "null"
        );

        // Check in current paste
        if (currentPasteFingerprints.contains(fingerprint)) {
            // Create a synthetic duplicate info for within-paste duplicate
            HistoricalTransaction synthetic = new HistoricalTransaction();
            synthetic.setTransactionDate(transactionDate);
            synthetic.setAmount(amount);
            synthetic.setDescription(description);
            synthetic.setImportBatchId("CURRENT_PASTE");
            return new DuplicateInfo(synthetic);
        }

        // Add to fingerprint set
        currentPasteFingerprints.add(fingerprint);

        // Check in database (only if property is not null)
        if (propertyId != null) {
            List<HistoricalTransaction> duplicates = historicalTransactionRepository.findDuplicateTransaction(
                transactionDate, amount, description, transactionType, propertyId, customerId
            );

            if (!duplicates.isEmpty()) {
                return new DuplicateInfo(duplicates.get(0));
            }

            // Check in current batch if batchId provided
            if (batchId != null && !batchId.isEmpty()) {
                List<HistoricalTransaction> batchDuplicates = historicalTransactionRepository.findDuplicateInBatch(
                    batchId, transactionDate, amount, description, transactionType, propertyId, customerId
                );

                if (!batchDuplicates.isEmpty()) {
                    return new DuplicateInfo(batchDuplicates.get(0));
                }
            }
        }

        return null;
    }

    /**
     * Process confirmed import from review queue
     * This is Stage 3: Import transactions after human verification
     */
    @Transactional
    public ImportResult processConfirmedImport(String batchId, List<TransactionReview> reviews) {
        ImportResult result = new ImportResult(batchId, "human_verified_import");

        log.info("🔍 Processing confirmed import for batch {}: {} transactions", batchId, reviews.size());

        for (TransactionReview review : reviews) {
            result.incrementTotal();

            try {
                // Skip validation errors
                if (review.getStatus() == ReviewStatus.VALIDATION_ERROR) {
                    result.addError("Line " + review.getLineNumber() + ": " + review.getErrorMessage());
                    result.incrementFailed();
                    continue;
                }

                // Skip duplicates unless user confirmed to import anyway
                if (review.getDuplicateInfo() != null && !review.isSkipDuplicate()) {
                    log.debug("Skipping duplicate at line {}", review.getLineNumber());
                    result.incrementSkipped("review");
                    continue;
                }

                // Extract parsed data
                Map<String, Object> data = review.getParsedData();
                LocalDate transactionDate = (LocalDate) data.get("parsedDate");
                BigDecimal amount = (BigDecimal) data.get("parsedAmount");
                String description = (String) data.get("description");
                TransactionType transactionType = (TransactionType) data.get("parsedType");

                // Get property (user-selected or matched)
                Property property = null;
                if (review.getSelectedPropertyId() != null) {
                    property = propertyRepository.findById(review.getSelectedPropertyId()).orElse(null);
                }

                // Get customer (user-selected or matched)
                Customer customer = null;
                if (review.getSelectedCustomerId() != null) {
                    customer = customerService.findByCustomerId(review.getSelectedCustomerId());
                }

                // Create transaction
                HistoricalTransaction transaction = new HistoricalTransaction();
                transaction.setTransactionDate(transactionDate);
                transaction.setAmount(amount);
                transaction.setDescription(description);
                transaction.setTransactionType(transactionType);
                transaction.setProperty(property);
                transaction.setCustomer(customer);
                transaction.setImportBatchId(batchId);
                transaction.setCreatedAt(LocalDateTime.now());

                // Add user note if provided
                if (review.getUserNote() != null && !review.getUserNote().isEmpty()) {
                    String notes = transaction.getNotes();
                    transaction.setNotes(notes != null ? notes + " | " + review.getUserNote() : review.getUserNote());
                }

                // Save transaction
                historicalTransactionRepository.save(transaction);
                result.incrementSuccessful();

                log.debug("✅ Imported transaction from line {}", review.getLineNumber());

            } catch (Exception e) {
                log.error("❌ Failed to import line {}: {}", review.getLineNumber(), e.getMessage(), e);
                result.addError("Line " + review.getLineNumber() + ": " + e.getMessage());
                result.incrementFailed();
            }
        }

        log.info("✅ Confirmed import complete: {} imported, {} skipped, {} errors",
            result.getSuccessfulImports(), result.getSkippedDuplicates(), result.getFailedImports());

        return result;
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
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
import site.easy.to.build.crm.entity.PaymentSource;
import site.easy.to.build.crm.entity.Invoice;
import site.easy.to.build.crm.entity.CustomerPropertyAssignment;
import site.easy.to.build.crm.entity.AssignmentType;
import site.easy.to.build.crm.entity.HistoricalTransaction.TransactionType;
import site.easy.to.build.crm.entity.HistoricalTransaction.TransactionSource;
import site.easy.to.build.crm.entity.TransactionLevel;
import site.easy.to.build.crm.repository.HistoricalTransactionRepository;
import site.easy.to.build.crm.repository.UserRepository;
import site.easy.to.build.crm.repository.PropertyRepository;
import site.easy.to.build.crm.repository.PaymentSourceRepository;
import site.easy.to.build.crm.repository.InvoiceRepository;
import site.easy.to.build.crm.repository.CustomerPropertyAssignmentRepository;
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
    private PaymentSourceRepository paymentSourceRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private CustomerPropertyAssignmentRepository assignmentRepository;

    @Autowired
    private AuthenticationUtils authenticationUtils;

    @Autowired
    private site.easy.to.build.crm.service.user.UserService userService;
    
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

        // Set transaction level based on property/customer presence
        setTransactionLevel(transaction);
        
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
        // Save the main transaction first
        HistoricalTransaction saved = historicalTransactionRepository.save(transaction);
        historicalTransactionRepository.flush(); // Force immediate persistence to catch errors

        // Check if we need to create split transactions (beneficiary allocations)
        if (saved.getIncomingTransactionAmount() != null &&
            saved.getIncomingTransactionAmount().compareTo(BigDecimal.ZERO) != 0) {

            log.debug("Creating split transactions for incoming amount: {}", saved.getIncomingTransactionAmount());
            createBeneficiaryAllocationsFromIncoming(saved);
        }
    }

    /**
     * Create beneficiary allocation and agency fee transactions from incoming payment
     * This mirrors the PayProp batch payment import logic
     */
    private void createBeneficiaryAllocationsFromIncoming(HistoricalTransaction incomingTransaction) {
        BigDecimal incomingAmount = incomingTransaction.getIncomingTransactionAmount();
        Property property = incomingTransaction.getProperty();

        if (property == null) {
            log.warn("Cannot create beneficiary allocation - no property linked to transaction {}",
                    incomingTransaction.getId());
            return;
        }

        // Get property owner (beneficiary)
        Customer owner = getPropertyOwner(property);
        if (owner == null) {
            log.warn("Cannot create beneficiary allocation - no owner found for property {}",
                    property.getPropertyName());
            return;
        }

        // Calculate commission (default 15% = 10% management + 5% service)
        BigDecimal commissionRate = property.getCommissionPercentage() != null ?
                                    property.getCommissionPercentage() :
                                    new BigDecimal("15.00");

        BigDecimal commissionAmount = incomingAmount
                .multiply(commissionRate)
                .divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP);

        // Calculate net due to owner
        BigDecimal netDueToOwner = incomingAmount.subtract(commissionAmount);

        // 1. Create OWNER ALLOCATION transaction (increases owner balance)
        HistoricalTransaction ownerAllocation = new HistoricalTransaction();
        ownerAllocation.setTransactionDate(incomingTransaction.getTransactionDate());
        ownerAllocation.setAmount(netDueToOwner.negate()); // Negative = allocation to owner
        ownerAllocation.setDescription("Owner Allocation - " + property.getPropertyName());
        ownerAllocation.setTransactionType(TransactionType.payment);
        ownerAllocation.setCategory("owner_allocation");
        ownerAllocation.setSource(incomingTransaction.getSource());
        ownerAllocation.setSourceReference(incomingTransaction.getSourceReference() + "-ALLOC");

        // Beneficiary tracking
        ownerAllocation.setBeneficiaryType("beneficiary");
        ownerAllocation.setBeneficiaryName(owner.getName());

        // Link to property and customer
        ownerAllocation.setProperty(property);
        ownerAllocation.setCustomer(owner);
        ownerAllocation.setCreatedByUser(incomingTransaction.getCreatedByUser());

        // Link to incoming transaction
        ownerAllocation.setIncomingTransactionId(incomingTransaction.getId().toString());
        ownerAllocation.setIncomingTransactionAmount(incomingAmount);
        ownerAllocation.setImportBatchId(incomingTransaction.getImportBatchId());
        ownerAllocation.setAccountSource(incomingTransaction.getAccountSource());
        ownerAllocation.setPaymentSource(incomingTransaction.getPaymentSource());

        historicalTransactionRepository.save(ownerAllocation);
        log.debug("Created owner allocation: {} - £{}", owner.getName(), netDueToOwner);

        // 2. Create AGENCY FEE transaction (agency income)
        HistoricalTransaction agencyFee = new HistoricalTransaction();
        agencyFee.setTransactionDate(incomingTransaction.getTransactionDate());
        agencyFee.setAmount(commissionAmount.negate()); // Negative = fee collected
        agencyFee.setDescription("Management Fee - " + commissionRate + "% - " + property.getPropertyName());
        agencyFee.setTransactionType(TransactionType.fee);
        agencyFee.setCategory("management_fee");
        agencyFee.setSource(incomingTransaction.getSource());
        agencyFee.setSourceReference(incomingTransaction.getSourceReference() + "-FEE");

        // Link to property (not to customer - this is agency income)
        agencyFee.setProperty(property);
        agencyFee.setCreatedByUser(incomingTransaction.getCreatedByUser());

        // Link to incoming transaction
        agencyFee.setIncomingTransactionId(incomingTransaction.getId().toString());
        agencyFee.setIncomingTransactionAmount(incomingAmount);
        agencyFee.setImportBatchId(incomingTransaction.getImportBatchId());
        agencyFee.setAccountSource(incomingTransaction.getAccountSource());
        agencyFee.setPaymentSource(incomingTransaction.getPaymentSource());

        historicalTransactionRepository.save(agencyFee);
        log.debug("Created agency fee: £{} ({}%)", commissionAmount, commissionRate);

        log.info("✅ Split incoming £{} → Owner allocation £{} + Agency fee £{}",
                incomingAmount, netDueToOwner, commissionAmount);
    }

    /**
     * Get property owner from property entity or customer_property_assignments table
     */
    private Customer getPropertyOwner(Property property) {
        // Try old direct property_owner_id field first (legacy)
        if (property.getPropertyOwnerId() != null) {
            Customer owner = customerService.findByCustomerId(property.getPropertyOwnerId());
            if (owner != null) {
                log.debug("Found owner via property_owner_id: {}", owner.getName());
                return owner;
            }
        }

        // Check customer_property_assignments junction table (modern approach)
        List<CustomerPropertyAssignment> ownerAssignments =
            assignmentRepository.findByPropertyIdAndAssignmentType(property.getId(), AssignmentType.OWNER);

        if (!ownerAssignments.isEmpty()) {
            // Get the primary owner or the first owner if no primary
            CustomerPropertyAssignment primaryOwner = ownerAssignments.stream()
                .filter(a -> a.getIsPrimary() != null && a.getIsPrimary())
                .findFirst()
                .orElse(ownerAssignments.get(0));

            Customer owner = primaryOwner.getCustomer();
            log.debug("Found owner via customer_property_assignments: {} (ownership: {}%)",
                owner.getName(), primaryOwner.getOwnershipPercentage());
            return owner;
        }

        log.warn("Property {} has no owner assigned (checked both property_owner_id and assignments)",
            property.getPropertyName());
        return null;
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

            // Validate required columns exist (only 2 required fields!)
            List<String> requiredColumns = List.of("transaction_date", "amount");
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

        // Description is OPTIONAL - will be auto-generated if missing
        // No validation needed

        // Transaction_type is OPTIONAL - will be inferred if missing
        String typeStr = getValue(values, columnMap, "transaction_type");
        if (typeStr != null && !typeStr.isEmpty()) {
            try {
                parseTransactionType(typeStr);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid transaction_type: " + typeStr + " (expected: payment, invoice, fee, expense, maintenance, adjustment)");
            }
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
        LocalDate transactionDate = parseDate(getValue(values, columnMap, "transaction_date"));
        transaction.setTransactionDate(transactionDate);

        BigDecimal amount = new BigDecimal(getValue(values, columnMap, "amount"));
        transaction.setAmount(amount);

        // SMART DEFAULTS: Get optional CSV values for defaulting logic
        String propertyRef = getValue(values, columnMap, "property_reference");
        String customerRef = getValue(values, columnMap, "customer_reference");
        String bankReference = getValue(values, columnMap, "bank_reference");
        String counterpartyName = getValue(values, columnMap, "counterparty_name");
        String beneficiaryType = getValue(values, columnMap, "beneficiary_type");
        String incomingAmountStr = getValue(values, columnMap, "incoming_transaction_amount");

        // TRANSACTION TYPE - Smart default if not provided
        String typeStr = getValue(values, columnMap, "transaction_type");
        TransactionType transactionType;
        if (typeStr != null && !typeStr.isEmpty()) {
            transactionType = parseTransactionType(typeStr);
        } else {
            // Infer transaction type from context
            transactionType = inferTransactionType(amount, beneficiaryType, incomingAmountStr);
        }
        transaction.setTransactionType(transactionType);

        // DESCRIPTION - Smart default if not provided
        String description = getValue(values, columnMap, "description");
        if (description == null || description.isEmpty()) {
            description = generateSmartDescription(transactionType, amount, propertyRef,
                                                   customerRef, bankReference, counterpartyName,
                                                   beneficiaryType, transactionDate);
        }
        transaction.setDescription(description);

        // Enhanced dual-source support - Map payment_source column to payment_sources table
        String paymentSourceCode = getValue(values, columnMap, "payment_source");
        if (paymentSourceCode != null && !paymentSourceCode.isEmpty()) {
            PaymentSource paymentSource = mapPaymentSource(paymentSourceCode);
            if (paymentSource != null) {
                transaction.setPaymentSource(paymentSource);
                // Also store code in account_source for reference
                transaction.setAccountSource(paymentSourceCode);
            }
        }

        // Beneficiary tracking for balance system
        if (beneficiaryType != null && !beneficiaryType.isEmpty()) {
            transaction.setBeneficiaryType(beneficiaryType);
        }

        // Incoming transaction tracking for split transactions
        if (incomingAmountStr != null && !incomingAmountStr.isEmpty()) {
            BigDecimal incomingAmount = new BigDecimal(incomingAmountStr);
            transaction.setIncomingTransactionAmount(incomingAmount);
            // This will be used later to create beneficiary allocations
        }

        // Note: Parking spaces are now handled as separate properties
        // No special parking space column processing needed

        // Optional fields
        setOptionalField(transaction::setCategory, values, columnMap, "category");
        log.debug("Category value: '{}' from column index: {}",
                 getValue(values, columnMap, "category"), columnMap.get("category"));
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

        // Match property and customer (variables already declared above for smart defaults)
        if (propertyRef != null && !propertyRef.isEmpty()) {
            Property property = findPropertyByReference(propertyRef);
            transaction.setProperty(property);
        }

        if (customerRef != null && !customerRef.isEmpty()) {
            Customer customer = findCustomerByReference(customerRef);
            transaction.setCustomer(customer);
        }

        // Set transaction level based on property/customer presence
        setTransactionLevel(transaction);

        // Link to lease if lease_reference is provided
        String leaseRef = getValue(values, columnMap, "lease_reference");
        if (leaseRef != null && !leaseRef.isEmpty()) {
            Optional<Invoice> lease = invoiceRepository.findByLeaseReference(leaseRef.trim());
            if (lease.isPresent()) {
                Invoice leaseEntity = lease.get();
                transaction.setInvoice(leaseEntity);
                transaction.setLeaseStartDate(leaseEntity.getStartDate());
                transaction.setLeaseEndDate(leaseEntity.getEndDate());
                transaction.setRentAmountAtTransaction(leaseEntity.getAmount());
                log.debug("✅ Transaction linked to lease: {}", leaseRef);
            } else {
                log.warn("⚠️ Lease reference '{}' not found - transaction will import without lease link", leaseRef);
            }
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
     * Validate and map payment source to PaymentSource entity
     * Maps CSV column values to actual payment_sources table records
     */
    private PaymentSource mapPaymentSource(String paymentSourceCode) {
        if (paymentSourceCode == null || paymentSourceCode.isEmpty()) {
            return null; // Optional field
        }

        String normalizedCode = paymentSourceCode.toUpperCase().trim();
        String paymentSourceName;

        // Map CSV codes to payment source names
        switch (normalizedCode) {
            case "OLD_ACCOUNT":
                paymentSourceName = "Old Account System";
                break;
            case "PAYPROP":
                paymentSourceName = "PayProp System";
                break;
            case "CALMONY":
            case "BANK":
                paymentSourceName = "Calmony Bank Account";
                break;
            default:
                throw new IllegalArgumentException("Invalid payment source: " + paymentSourceCode +
                                                 ". Valid sources: OLD_ACCOUNT, PAYPROP, CALMONY");
        }

        // Look up the payment source by name
        return paymentSourceRepository.findByName(paymentSourceName)
            .orElseThrow(() -> new IllegalArgumentException(
                "Payment source '" + paymentSourceName + "' not found in database. " +
                "Please create it first via the Payment Sources page."));
    }

    /**
     * Validate payment source value (deprecated - use mapPaymentSource instead)
     */
    @Deprecated
    private void validatePaymentSource(String paymentSource) {
        if (paymentSource == null || paymentSource.isEmpty()) {
            return; // Optional field
        }

        String normalizedSource = paymentSource.toUpperCase().trim();
        switch (normalizedSource) {
            case "OLD_ACCOUNT":
            case "PAYPROP":
            case "CALMONY":
            case "BANK":
                break; // Valid values
            default:
                throw new IllegalArgumentException("Invalid payment source: " + paymentSource +
                                                 ". Valid sources: OLD_ACCOUNT, PAYPROP, CALMONY");
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
     * Infer transaction type from context when not explicitly provided
     */
    private TransactionType inferTransactionType(BigDecimal amount, String beneficiaryType, String incomingAmountStr) {
        // Priority 1: If incoming transaction amount is present, it's a payment received
        if (incomingAmountStr != null && !incomingAmountStr.isEmpty()) {
            return TransactionType.payment;
        }

        // Priority 2: Infer from beneficiary type
        if (beneficiaryType != null && !beneficiaryType.isEmpty()) {
            switch (beneficiaryType.toLowerCase()) {
                case "beneficiary":
                    return TransactionType.payment; // Owner allocation
                case "beneficiary_payment":
                    return TransactionType.payment; // Payment to owner
                case "contractor":
                    return TransactionType.expense; // Payment to contractor
            }
        }

        // Priority 3: Infer from amount direction
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            return TransactionType.invoice; // Money in
        } else if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return TransactionType.payment; // Money out
        }

        // Default fallback
        return TransactionType.payment;
    }

    /**
     * Generate smart description when not provided in CSV
     */
    private String generateSmartDescription(TransactionType type, BigDecimal amount,
                                           String propertyRef, String customerRef,
                                           String bankReference, String counterpartyName,
                                           String beneficiaryType, LocalDate date) {
        // Priority 1: Use bank reference if available
        if (bankReference != null && !bankReference.isEmpty()) {
            return bankReference;
        }

        // Priority 2: Use counterparty name if available
        if (counterpartyName != null && !counterpartyName.isEmpty()) {
            return counterpartyName;
        }

        // Priority 3: Generate from beneficiary type
        if (beneficiaryType != null && !beneficiaryType.isEmpty()) {
            switch (beneficiaryType.toLowerCase()) {
                case "beneficiary":
                    return String.format("Owner Allocation - %s - £%.2f",
                        propertyRef != null ? propertyRef : "Property",
                        amount.abs());
                case "beneficiary_payment":
                    return String.format("Payment to Owner - %s - £%.2f",
                        customerRef != null ? customerRef : "Owner",
                        amount.abs());
                case "contractor":
                    return String.format("Contractor Payment - %s - £%.2f",
                        customerRef != null ? customerRef : "Contractor",
                        amount.abs());
            }
        }

        // Priority 4: Generate from transaction type and context
        String typeLabel = type != null ? type.getDisplayName() : "Transaction";
        StringBuilder desc = new StringBuilder(typeLabel);

        if (propertyRef != null && !propertyRef.isEmpty()) {
            desc.append(" - ").append(propertyRef);
        }

        if (customerRef != null && !customerRef.isEmpty()) {
            desc.append(" - ").append(customerRef);
        }

        desc.append(" - £").append(String.format("%.2f", amount.abs()));

        return desc.toString();
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
     * ALWAYS returns a valid user - uses fallback if necessary
     */
    private User getCurrentUser() {
        try {
            org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("⚠️ No authentication found in SecurityContext - using fallback user");
                return getFallbackUser();
            }

            String principalName = authentication.getName();
            log.info("🔐 Authenticated principal: {}", principalName);

            // Try to find user by email first
            User user = userService.findByEmail(principalName);

            if (user == null) {
                // Principal might be OAuth sub claim (Google ID like "114738897312032777033")
                // Look up User through OAuthUser relationship using email
                log.info("🔐 Email lookup failed, trying OAuth user lookup...");

                // Find all users with OAuth and check their OAuth email or ID
                user = userRepository.findAll().stream()
                    .filter(u -> u.getOauthUser() != null)
                    .filter(u -> {
                        // Check if OAuth email matches the principal
                        if (u.getOauthUser().getEmail() != null &&
                            u.getOauthUser().getEmail().equals(principalName)) {
                            return true;
                        }
                        // Also check username in case it's stored there
                        if (u.getUsername() != null && u.getUsername().equals(principalName)) {
                            return true;
                        }
                        return false;
                    })
                    .findFirst()
                    .orElse(null);

                if (user != null) {
                    log.info("✅ User found via OAuth lookup: {}", user.getEmail());
                } else {
                    log.warn("⚠️ User not found for principal: {} - using fallback user", principalName);
                    user = getFallbackUser();
                }
            } else {
                log.info("✅ User found via direct email: {}", user.getEmail());
            }

            if (user == null) {
                log.error("❌ CRITICAL: Even fallback user is null! Using emergency fallback.");
                user = getEmergencyFallbackUser();
            }

            return user;
        } catch (Exception e) {
            log.error("❌ Error getting current user - using fallback", e);
            return getFallbackUser();
        }
    }

    /**
     * Get fallback user (first OAuth user - likely admin)
     */
    private User getFallbackUser() {
        try {
            User fallbackUser = userRepository.findAll().stream()
                .filter(u -> u.getOauthUser() != null)
                .findFirst()
                .orElse(null);

            if (fallbackUser != null) {
                log.warn("⚠️ FALLBACK USER: {} (ID: {})", fallbackUser.getEmail(), fallbackUser.getId());
                return fallbackUser;
            }

            log.error("❌ No OAuth users found in database for fallback!");
            return getEmergencyFallbackUser();
        } catch (Exception e) {
            log.error("❌ Error in fallback user lookup", e);
            return getEmergencyFallbackUser();
        }
    }

    /**
     * Emergency fallback - get ANY user from database
     */
    private User getEmergencyFallbackUser() {
        try {
            User emergencyUser = userRepository.findAll().stream()
                .findFirst()
                .orElse(null);

            if (emergencyUser != null) {
                log.error("🚨 EMERGENCY FALLBACK USER: {} (ID: {})",
                    emergencyUser.getEmail(), emergencyUser.getId());
                return emergencyUser;
            }

            log.error("🚨 CRITICAL: NO USERS IN DATABASE! Import will fail.");
            return null;
        } catch (Exception e) {
            log.error("🚨 CRITICAL ERROR in emergency fallback", e);
            return null;
        }
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
                case AMBIGUOUS_LEASE:
                case POTENTIAL_DUPLICATE:
                    needsReview++; break;
                case MISSING_PROPERTY:
                case MISSING_CUSTOMER:
                case MISSING_LEASE:
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
        private List<LeaseOption> leaseOptions;
        private DuplicateInfo duplicateInfo;
        private Map<String, Object> parsedData;
        private String errorMessage;

        // User selections (filled during review)
        private Long selectedPropertyId;
        private Long selectedCustomerId;
        private Long selectedLeaseId;
        private Long selectedPaymentSourceId;
        private boolean skipDuplicate;
        private String userNote;

        public TransactionReview(int lineNumber, String csvLine) {
            this.lineNumber = lineNumber;
            this.csvLine = csvLine;
            this.propertyOptions = new ArrayList<>();
            this.customerOptions = new ArrayList<>();
            this.leaseOptions = new ArrayList<>();
            this.parsedData = new HashMap<>();
        }

        // Getters and Setters
        public int getLineNumber() { return lineNumber; }
        public String getCsvLine() { return csvLine; }
        public ReviewStatus getStatus() { return status; }
        public void setStatus(ReviewStatus status) { this.status = status; }
        public List<PropertyOption> getPropertyOptions() { return propertyOptions; }
        public List<CustomerOption> getCustomerOptions() { return customerOptions; }
        public List<LeaseOption> getLeaseOptions() { return leaseOptions; }
        public DuplicateInfo getDuplicateInfo() { return duplicateInfo; }
        public void setDuplicateInfo(DuplicateInfo duplicateInfo) { this.duplicateInfo = duplicateInfo; }
        public Map<String, Object> getParsedData() { return parsedData; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public Long getSelectedPropertyId() { return selectedPropertyId; }
        public void setSelectedPropertyId(Long selectedPropertyId) { this.selectedPropertyId = selectedPropertyId; }
        public Long getSelectedCustomerId() { return selectedCustomerId; }
        public void setSelectedCustomerId(Long selectedCustomerId) { this.selectedCustomerId = selectedCustomerId; }
        public Long getSelectedLeaseId() { return selectedLeaseId; }
        public void setSelectedLeaseId(Long selectedLeaseId) { this.selectedLeaseId = selectedLeaseId; }
        public Long getSelectedPaymentSourceId() { return selectedPaymentSourceId; }
        public void setSelectedPaymentSourceId(Long selectedPaymentSourceId) { this.selectedPaymentSourceId = selectedPaymentSourceId; }
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
            // Use the 'name' field which handles both individual and business customers
            this.fullName = customer.getName() != null ? customer.getName() :
                            (customer.getFirstName() + " " + customer.getLastName());
            this.email = customer.getEmail();
            this.phone = customer.getPhone() != null ? customer.getPhone() : customer.getMobileNumber();
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
     * Lease matching option - displays lease details for user selection
     */
    public static class LeaseOption {
        private Long leaseId;
        private String leaseReference;
        private LocalDate startDate;
        private LocalDate endDate;
        private BigDecimal rentAmount;
        private String propertyName;
        private String customerName;
        private String status; // "ACTIVE", "ENDED", "FUTURE"
        private long durationMonths;
        private int matchScore; // 100 = perfect, lower = fuzzy

        public LeaseOption(site.easy.to.build.crm.entity.Invoice lease, int matchScore) {
            this.leaseId = lease.getId();
            this.leaseReference = lease.getLeaseReference();
            this.startDate = lease.getStartDate();
            this.endDate = lease.getEndDate();
            this.rentAmount = lease.getAmount();
            this.propertyName = lease.getProperty() != null ? lease.getProperty().getPropertyName() : "Unknown";
            this.customerName = lease.getCustomer() != null ? lease.getCustomer().getName() : "Unknown";
            this.matchScore = matchScore;

            // Calculate status
            LocalDate today = LocalDate.now();
            if (startDate != null && startDate.isAfter(today)) {
                this.status = "FUTURE";
            } else if (endDate != null && endDate.isBefore(today)) {
                this.status = "ENDED";
            } else {
                this.status = "ACTIVE";
            }

            // Calculate duration in months
            if (startDate != null) {
                LocalDate effectiveEndDate = endDate != null ? endDate : LocalDate.now();
                this.durationMonths = java.time.Period.between(startDate, effectiveEndDate).toTotalMonths();
            }
        }

        // Getters
        public Long getLeaseId() { return leaseId; }
        public String getLeaseReference() { return leaseReference; }
        public LocalDate getStartDate() { return startDate; }
        public LocalDate getEndDate() { return endDate; }
        public BigDecimal getRentAmount() { return rentAmount; }
        public String getPropertyName() { return propertyName; }
        public String getCustomerName() { return customerName; }
        public String getStatus() { return status; }
        public long getDurationMonths() { return durationMonths; }
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
        AMBIGUOUS_LEASE,         // Multiple lease matches
        MISSING_PROPERTY,        // No property found
        MISSING_CUSTOMER,        // No customer found
        MISSING_LEASE,           // No lease found (when lease_reference provided but not found)
        POTENTIAL_DUPLICATE,     // Matches existing transaction
        VALIDATION_ERROR         // Parse/validation error
    }

    // ===== VALIDATION & REVIEW METHODS =====

    /**
     * Validate CSV data and create review queue for human verification
     * This is Stage 1: Parse and analyze without importing
     */
    public ReviewQueue validateForReview(String csvData, String batchId, Long paymentSourceId) {
        log.info("🔍 [REVIEW-VALIDATE] Starting validation for batchId: {}", batchId);
        log.info("💳 [REVIEW-VALIDATE] Payment Source ID: {}", paymentSourceId);
        log.debug("CSV data length: {} characters", csvData != null ? csvData.length() : 0);

        ReviewQueue queue = new ReviewQueue(batchId);

        if (csvData == null || csvData.trim().isEmpty()) {
            log.warn("⚠️ [REVIEW-VALIDATE] Empty CSV data provided");
            return queue;
        }

        String[] lines = csvData.split("\\r?\\n");
        log.info("📊 [REVIEW-VALIDATE] Processing {} lines", lines.length);

        if (lines.length == 0) {
            log.warn("⚠️ [REVIEW-VALIDATE] No lines to process");
            return queue;
        }

        // Build column map from header row (same approach as Quick Import)
        String headerLine = lines[0].trim();
        String[] headers = headerLine.split(",");
        Map<String, Integer> columnMap = buildColumnMap(headers);
        log.info("📋 [REVIEW-VALIDATE] Built column map with {} columns: {}", columnMap.size(), String.join(", ", headers));

        // Validate required columns (only 2 required fields!)
        List<String> requiredColumns = List.of("transaction_date", "amount");
        List<String> missingColumns = new ArrayList<>();
        for (String required : requiredColumns) {
            if (!columnMap.containsKey(required)) {
                missingColumns.add(required);
            }
        }
        if (!missingColumns.isEmpty()) {
            log.error("❌ [REVIEW-VALIDATE] Missing required columns: {}", String.join(", ", missingColumns));
            TransactionReview errorReview = new TransactionReview(0, "Header validation");
            errorReview.setStatus(ReviewStatus.VALIDATION_ERROR);
            errorReview.setErrorMessage("Missing required columns: " + String.join(", ", missingColumns));
            queue.addReview(errorReview);
            return queue;
        }

        Set<String> currentPasteFingerprints = new HashSet<>();

        // Process data rows (skip header at index 0)
        for (int i = 1; i < lines.length; i++) {
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
                // Parse the CSV line using same method as Quick Import
                String[] values = parseCsvLine(line);
                log.debug("📋 [REVIEW-VALIDATE] Line {}: Split into {} values", lineNumber, values.length);

                // Extract fields using column map (same as Quick Import)
                String dateStr = getValue(values, columnMap, "transaction_date");
                String amountStr = getValue(values, columnMap, "amount");
                String description = getValue(values, columnMap, "description");
                String typeStr = getValue(values, columnMap, "transaction_type");
                String propertyRef = getValue(values, columnMap, "property_reference");
                String customerRef = getValue(values, columnMap, "customer_reference");
                String counterpartyName = getValue(values, columnMap, "counterparty_name");
                String category = getValue(values, columnMap, "category");
                String paymentSourceCode = getValue(values, columnMap, "payment_source");

                // Use counterparty_name as fallback if customer_reference is empty
                String customerSearchTerm = customerRef;
                if ((customerSearchTerm == null || customerSearchTerm.isEmpty()) &&
                    counterpartyName != null && !counterpartyName.isEmpty()) {
                    customerSearchTerm = counterpartyName;
                    log.debug("👤 [REVIEW-VALIDATE] Line {}: Using counterparty_name '{}' as customer search term",
                        lineNumber, counterpartyName);
                }

                // Store parsed data
                review.getParsedData().put("date", dateStr);
                review.getParsedData().put("amount", amountStr);
                review.getParsedData().put("description", description);
                review.getParsedData().put("type", typeStr);
                review.getParsedData().put("propertyRef", propertyRef);
                review.getParsedData().put("customerRef", customerSearchTerm); // Store the actual search term used
                review.getParsedData().put("category", category);
                review.getParsedData().put("paymentSource", paymentSourceCode);
                log.debug("💳 [REVIEW-VALIDATE] Line {}: CSV payment_source = '{}'", lineNumber, paymentSourceCode);

                // Validate date
                LocalDate transactionDate = parseDate(dateStr);
                if (transactionDate == null) {
                    review.setStatus(ReviewStatus.VALIDATION_ERROR);
                    review.setErrorMessage("Invalid date format: " + dateStr);
                    queue.addReview(review);
                    continue;
                }
                review.getParsedData().put("parsedDate", transactionDate.toString()); // Convert to string for JSON

                // Validate amount
                BigDecimal amount;
                try {
                    amount = new BigDecimal(amountStr.replace(",", "").replace("$", "").replace("£", ""));
                    review.getParsedData().put("parsedAmount", amount.toString()); // Convert to string for JSON
                } catch (NumberFormatException e) {
                    review.setStatus(ReviewStatus.VALIDATION_ERROR);
                    review.setErrorMessage("Invalid amount: " + amountStr);
                    queue.addReview(review);
                    continue;
                }

                // Validate transaction type - with smart inference if not provided
                TransactionType transactionType;
                try {
                    if (typeStr != null && !typeStr.isEmpty()) {
                        // Parse explicitly provided transaction type
                        transactionType = parseTransactionType(typeStr);
                    } else {
                        // Infer transaction type from context (same logic as quick import)
                        String beneficiaryType = getValue(values, columnMap, "beneficiary_type");
                        String incomingAmountStr = getValue(values, columnMap, "incoming_transaction_amount");
                        transactionType = inferTransactionType(amount, beneficiaryType, incomingAmountStr);
                        log.debug("🔍 [REVIEW-VALIDATE] Line {}: Inferred transaction type as {} from context", lineNumber, transactionType);
                    }
                    review.getParsedData().put("parsedType", transactionType.name()); // Convert enum to string for JSON
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

                // Find customer matches (using either customer_reference or counterparty_name)
                log.debug("👤 [REVIEW-VALIDATE] Line {}: Looking for customer '{}'", lineNumber, customerSearchTerm);
                List<CustomerOption> customerMatches = findCustomerMatches(customerSearchTerm);
                log.info("👤 [REVIEW-VALIDATE] Line {}: Found {} customer matches for '{}'", lineNumber, customerMatches.size(), customerSearchTerm);

                // SPECIAL HANDLING: For owner payments, automatically add owners/beneficiaries to dropdown
                String beneficiaryType = getValue(values, columnMap, "beneficiary_type");
                // category already declared above at line 1724
                boolean isOwnerPayment = (beneficiaryType != null && "beneficiary_payment".equalsIgnoreCase(beneficiaryType.trim())) ||
                                         (category != null && "owner_payment".equalsIgnoreCase(category.trim()));

                if (isOwnerPayment) {
                    log.debug("💰 [REVIEW-VALIDATE] Line {}: Owner payment detected (beneficiary_type='{}', category='{}') - adding owners to customer options",
                        lineNumber, beneficiaryType, category);

                    // OPTION 1: If we have a property match, add the specific property owner
                    if (!propertyMatches.isEmpty()) {
                        Property matchedProperty = propertyRepository.findById(propertyMatches.get(0).getPropertyId()).orElse(null);
                        if (matchedProperty != null) {
                            Customer propertyOwner = getPropertyOwner(matchedProperty);
                            if (propertyOwner != null) {
                                log.info("👤 [REVIEW-VALIDATE] Line {}: Found property owner: {} (ID: {})",
                                    lineNumber, propertyOwner.getName(), propertyOwner.getCustomerId());

                                // Check if owner is already in customer matches
                                boolean ownerAlreadyMatched = customerMatches.stream()
                                    .anyMatch(opt -> opt.getCustomerId().equals(propertyOwner.getCustomerId()));

                                if (!ownerAlreadyMatched) {
                                    // Add owner with high match score (95) to indicate it's the property owner
                                    CustomerOption ownerOption = new CustomerOption(propertyOwner, 95);
                                    customerMatches.add(0, ownerOption); // Add at the beginning
                                    log.info("✅ [REVIEW-VALIDATE] Line {}: Added property owner '{}' to customer options",
                                        lineNumber, propertyOwner.getName());
                                }
                            }
                        }
                    }

                    // OPTION 2: If no property OR no customer matches yet, show ALL property owners/beneficiaries
                    if (customerMatches.isEmpty()) {
                        log.debug("👥 [REVIEW-VALIDATE] Line {}: No customer matches found - loading all property owners", lineNumber);
                        List<Customer> allOwners = customerService.findAll().stream()
                            .filter(c -> c.getIsPropertyOwner() != null && c.getIsPropertyOwner())
                            .toList();

                        for (Customer owner : allOwners) {
                            // Add with score 80 to indicate it's a general owner (not property-specific)
                            CustomerOption ownerOption = new CustomerOption(owner, 80);
                            customerMatches.add(ownerOption);
                        }

                        log.info("✅ [REVIEW-VALIDATE] Line {}: Added {} property owners to customer options", lineNumber, allOwners.size());
                    }
                }

                if (!customerMatches.isEmpty()) {
                    review.getCustomerOptions().addAll(customerMatches);
                    if (customerMatches.size() > 1 && review.getStatus() != ReviewStatus.AMBIGUOUS_PROPERTY) {
                        review.setStatus(ReviewStatus.AMBIGUOUS_CUSTOMER);
                        log.warn("⚠️ [REVIEW-VALIDATE] Line {}: Ambiguous customer - {} matches", lineNumber, customerMatches.size());
                    } else {
                        log.debug("✅ [REVIEW-VALIDATE] Line {}: Single customer match found (score: {})",
                            lineNumber, customerMatches.get(0).getMatchScore());
                    }
                } else if (customerSearchTerm != null && !customerSearchTerm.isEmpty()) {
                    // Only mark as missing if it's not a block-level beneficiary payment to agency
                    if (beneficiaryType == null || !"beneficiary".equalsIgnoreCase(beneficiaryType.trim())) {
                        if (review.getStatus() == null) {
                            review.setStatus(ReviewStatus.MISSING_CUSTOMER);
                            log.warn("❌ [REVIEW-VALIDATE] Line {}: Customer '{}' not found", lineNumber, customerSearchTerm);
                        }
                    } else {
                        log.debug("ℹ️ [REVIEW-VALIDATE] Line {}: Block-level beneficiary payment - customer is optional", lineNumber);
                    }
                }

                // Find lease matches (if we have property - customer is optional for lease search)
                String leaseRef = getValue(values, columnMap, "lease_reference");
                review.getParsedData().put("leaseRef", leaseRef);
                log.debug("📋 [REVIEW-VALIDATE] Line {}: Looking for lease_reference '{}'", lineNumber, leaseRef);

                // Search for lease if we have at least property match (customer is optional)
                if (!propertyMatches.isEmpty()) {
                    Property matchedProperty = propertyRepository.findById(propertyMatches.get(0).getPropertyId()).orElse(null);
                    Customer matchedCustomer = !customerMatches.isEmpty() ?
                        customerService.findByCustomerId(customerMatches.get(0).getCustomerId()) : null;

                    if (matchedProperty != null) {
                        // Search for lease (customer can be null - lease search will filter by property)
                        List<LeaseOption> leaseMatches = findLeaseMatches(leaseRef, matchedProperty, matchedCustomer, transactionDate);
                        log.info("📋 [REVIEW-VALIDATE] Line {}: Found {} lease matches", lineNumber, leaseMatches.size());

                        if (!leaseMatches.isEmpty()) {
                            review.getLeaseOptions().addAll(leaseMatches);
                            if (leaseMatches.size() > 1 && review.getStatus() != ReviewStatus.AMBIGUOUS_PROPERTY && review.getStatus() != ReviewStatus.AMBIGUOUS_CUSTOMER) {
                                review.setStatus(ReviewStatus.AMBIGUOUS_LEASE);
                                log.warn("⚠️ [REVIEW-VALIDATE] Line {}: Ambiguous lease - {} matches", lineNumber, leaseMatches.size());
                            } else if (leaseMatches.size() == 1 && leaseMatches.get(0).getMatchScore() == 100) {
                                // Auto-select perfect lease match
                                review.setSelectedLeaseId(leaseMatches.get(0).getLeaseId());
                                log.debug("✅ [REVIEW-VALIDATE] Line {}: Perfect lease match found (score: 100)", lineNumber);
                            } else {
                                log.debug("✅ [REVIEW-VALIDATE] Line {}: Single lease match found (score: {})",
                                    lineNumber, leaseMatches.get(0).getMatchScore());
                            }
                        } else if (leaseRef != null && !leaseRef.isEmpty()) {
                            // Lease reference provided but not found
                            if (review.getStatus() == null) {
                                review.setStatus(ReviewStatus.MISSING_LEASE);
                                log.warn("❌ [REVIEW-VALIDATE] Line {}: Lease reference '{}' not found", lineNumber, leaseRef);
                            }
                        }
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

            // Pre-populate payment source ID from main import page selection
            if (paymentSourceId != null) {
                review.setSelectedPaymentSourceId(paymentSourceId);
                log.debug("💳 [REVIEW-VALIDATE] Line {}: Pre-populated payment source ID: {}", lineNumber, paymentSourceId);
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
            String firstName = cust.getFirstName() != null ? cust.getFirstName().trim() : "";
            String lastName = cust.getLastName() != null ? cust.getLastName().trim() : "";
            String fullName = (firstName + " " + lastName).trim().toLowerCase();

            // Also check the 'name' field (which might have the full name)
            String customerName = cust.getName() != null ? cust.getName().trim().toLowerCase() : "";

            String searchRef = cleanRef.toLowerCase();

            // Strip common titles from both search and customer names for better matching
            String normalizedCustomerName = stripTitles(customerName);
            String normalizedFullName = stripTitles(fullName);
            String normalizedSearchRef = stripTitles(searchRef);

            // Exact match on 'name' field
            if (!customerName.isEmpty() && !customerName.startsWith("customer -") && normalizedCustomerName.equals(normalizedSearchRef)) {
                matches.add(new CustomerOption(cust, 100));
                continue;
            }

            // Exact match on first_name + last_name
            if (!fullName.isEmpty() && normalizedFullName.equals(normalizedSearchRef)) {
                matches.add(new CustomerOption(cust, 100));
                continue;
            }

            // Token-based matching: check if any significant name tokens match (partial matches allowed)
            int tokenMatchScore = calculateTokenMatchScore(normalizedCustomerName, normalizedSearchRef);
            if (tokenMatchScore >= 55) {  // Lower threshold to include partial matches (1+ word match)
                matches.add(new CustomerOption(cust, tokenMatchScore));
                continue;
            }

            // Partial match on 'name' field (if not auto-generated)
            if (!customerName.isEmpty() && !customerName.startsWith("customer -") &&
                (normalizedCustomerName.contains(normalizedSearchRef) || normalizedSearchRef.contains(normalizedCustomerName))) {
                int score = calculateMatchScore(normalizedCustomerName, normalizedSearchRef);
                if (score > 50) {
                    matches.add(new CustomerOption(cust, score));
                }
            }

            // Partial match on first_name + last_name (only if we have a name)
            if (!fullName.isEmpty() && (normalizedFullName.contains(normalizedSearchRef) || normalizedSearchRef.contains(normalizedFullName))) {
                int score = calculateMatchScore(normalizedFullName, normalizedSearchRef);
                if (score > 50) {
                    matches.add(new CustomerOption(cust, score));
                }
            }

            // Email exact match
            if (cust.getEmail() != null && cust.getEmail().equalsIgnoreCase(cleanRef)) {
                matches.add(new CustomerOption(cust, 95));
                continue;
            }

            // Email partial match (e.g., "AMOS BLYTHE" matches "amosblyth@outlook.com")
            if (cust.getEmail() != null) {
                String emailLocal = cust.getEmail().split("@")[0].toLowerCase(); // Part before @
                String emailNormalized = emailLocal.replaceAll("[^a-z]", ""); // Remove non-letters
                String searchNormalized = searchRef.replaceAll("[^a-z]", "").toLowerCase(); // Remove non-letters

                // Check if search term appears in email local part
                if (emailNormalized.contains(searchNormalized) && searchNormalized.length() > 3) {
                    int score = 85; // High score for email match
                    matches.add(new CustomerOption(cust, score));
                    continue;
                }

                // Check if all search words appear in email (e.g., "amos blythe" in "amosblyth")
                String[] searchWords = searchRef.split("\\s+");
                boolean allWordsMatch = true;
                for (String word : searchWords) {
                    String wordNormalized = word.replaceAll("[^a-z]", "").toLowerCase();
                    if (!emailNormalized.contains(wordNormalized)) {
                        allWordsMatch = false;
                        break;
                    }
                }
                if (allWordsMatch && searchWords.length > 0) {
                    matches.add(new CustomerOption(cust, 90));
                }
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
     * Find lease matches based on property, customer, and transaction date
     * Scoring:
     * - 100: Perfect match (property + customer + date within lease period)
     * - 80-90: Good match (property + customer + date close to lease period)
     * - 50-70: Possible match (property + customer + lease recently ended)
     */
    private List<LeaseOption> findLeaseMatches(String leaseRef, Property property, Customer customer, LocalDate transactionDate) {
        List<LeaseOption> matches = new ArrayList<>();

        // If lease_reference is provided, try exact lookup first
        if (leaseRef != null && !leaseRef.trim().isEmpty()) {
            Optional<Invoice> exactLease = invoiceRepository.findByLeaseReference(leaseRef.trim());
            if (exactLease.isPresent()) {
                matches.add(new LeaseOption(exactLease.get(), 100));
                return matches;
            }
        }

        // If no property or customer matched, we can't do lease matching
        if (property == null || customer == null) {
            return matches;
        }

        // Find all leases for this property + customer combination
        List<Invoice> leases = invoiceRepository.findByPropertyAndCustomerOrderByStartDateDesc(property, customer);

        for (Invoice lease : leases) {
            // Skip leases without lease_reference (not properly set up)
            if (lease.getLeaseReference() == null || lease.getLeaseReference().isEmpty()) {
                continue;
            }

            LocalDate leaseStart = lease.getStartDate();
            LocalDate leaseEnd = lease.getEndDate();

            // Score 100: Transaction date within lease period
            if (leaseStart != null && (leaseEnd == null || !transactionDate.isAfter(leaseEnd)) && !transactionDate.isBefore(leaseStart)) {
                matches.add(new LeaseOption(lease, 100));
                continue;
            }

            // Score 80-90: Transaction date close to lease period (within 30 days before/after)
            if (leaseStart != null) {
                long daysBefore = java.time.temporal.ChronoUnit.DAYS.between(transactionDate, leaseStart);
                long daysAfter = leaseEnd != null ? java.time.temporal.ChronoUnit.DAYS.between(leaseEnd, transactionDate) : -1;

                // Within 30 days before lease start
                if (daysBefore > 0 && daysBefore <= 30) {
                    int score = 90 - (int)(daysBefore / 3); // 90 down to 80
                    matches.add(new LeaseOption(lease, Math.max(score, 80)));
                    continue;
                }

                // Within 90 days after lease end (arrears payments)
                if (leaseEnd != null && daysAfter >= 0 && daysAfter <= 90) {
                    int score = 85 - (int)(daysAfter / 3); // 85 down to 55
                    matches.add(new LeaseOption(lease, Math.max(score, 55)));
                    continue;
                }
            }

            // Score 50-70: Lease exists for property+customer but date doesn't match well
            // (Include these as possible matches for user to review)
            if (matches.isEmpty() || matches.size() < 3) {
                matches.add(new LeaseOption(lease, 50));
            }
        }

        // Sort by match score (highest first)
        matches.sort((a, b) -> Integer.compare(b.getMatchScore(), a.getMatchScore()));

        // Remove duplicates (same lease ID)
        Set<Long> seenIds = new HashSet<>();
        matches.removeIf(opt -> !seenIds.add(opt.getLeaseId()));

        // Limit to top 5 matches
        if (matches.size() > 5) {
            matches = matches.subList(0, 5);
        }

        return matches;
    }

    /**
     * Strip common titles from names (Mr, Mrs, Ms, Miss, Dr, etc.)
     */
    private String stripTitles(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        // Remove common titles at the start of names
        String result = name.replaceAll("^(mr|mrs|ms|miss|dr|prof|sir|madam)\\.?\\s+", "").trim();
        // Also remove trailing/leading spaces and normalize whitespace
        return result.replaceAll("\\s+", " ");
    }

    /**
     * Calculate token-based match score: checks how many significant tokens match between names
     * This handles cases like "AMOS BLYTHE" matching "Mr Amos Blyth" even with spelling variations
     * Also returns partial matches where only some words match (e.g., "AMOS BLYTHE" vs "AMOS SMITH")
     */
    private int calculateTokenMatchScore(String name1, String name2) {
        if (name1 == null || name2 == null || name1.isEmpty() || name2.isEmpty()) {
            return 0;
        }

        // Split into tokens (words)
        String[] tokens1 = name1.toLowerCase().split("\\s+");
        String[] tokens2 = name2.toLowerCase().split("\\s+");

        // Skip very short tokens (1-2 chars) as they're often initials or noise
        java.util.List<String> significantTokens1 = new java.util.ArrayList<>();
        java.util.List<String> significantTokens2 = new java.util.ArrayList<>();

        for (String token : tokens1) {
            if (token.length() > 2) {
                significantTokens1.add(token);
            }
        }
        for (String token : tokens2) {
            if (token.length() > 2) {
                significantTokens2.add(token);
            }
        }

        if (significantTokens1.isEmpty() || significantTokens2.isEmpty()) {
            return 0;
        }

        // Check how many tokens match (exactly or with 1-char difference)
        int matchedTokens = 0;
        for (String token1 : significantTokens1) {
            for (String token2 : significantTokens2) {
                if (token1.equals(token2)) {
                    matchedTokens++;
                    break;
                } else if (Math.abs(token1.length() - token2.length()) <= 1) {
                    // Allow 1-character difference (handles "blyth" vs "blythe")
                    int editDistance = levenshteinDistance(token1, token2);
                    if (editDistance <= 1) {
                        matchedTokens++;
                        break;
                    }
                }
            }
        }

        if (matchedTokens == 0) {
            return 0;
        }

        // Calculate score based on percentage of tokens matched
        int maxTokens = Math.max(significantTokens1.size(), significantTokens2.size());
        int minTokens = Math.min(significantTokens1.size(), significantTokens2.size());

        // All tokens matched - highest score
        if (matchedTokens >= minTokens) {
            return 95;
        }

        // Partial match - scale score based on how many tokens matched
        // 1 word match = 60-70, 2 words = 75-85, etc.
        double matchRatio = (double) matchedTokens / maxTokens;
        int baseScore = 55 + (int)(matchRatio * 40);  // Scale from 55 to 95

        return baseScore;
    }

    /**
     * Calculate Levenshtein distance between two strings
     */
    private int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();

        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[len1][len2];
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

        log.info("🔍 ============================================");
        log.info("🔍 PROCESS CONFIRMED IMPORT STARTED");
        log.info("🔍 Batch ID: {}", batchId);
        log.info("🔍 Total reviews received: {}", reviews.size());
        log.info("🔍 ============================================");

        for (int i = 0; i < reviews.size(); i++) {
            TransactionReview review = reviews.get(i);
            result.incrementTotal();

            log.info("📋 ========== PROCESSING REVIEW {} of {} ==========", (i+1), reviews.size());
            log.info("📋 Line number: {}", review.getLineNumber());
            log.info("📋 Status: {}", review.getStatus());
            log.info("📋 Has duplicate info: {}", review.getDuplicateInfo() != null);
            log.info("📋 Skip duplicate flag: {}", review.isSkipDuplicate());
            log.info("📋 Selected Property ID: {}", review.getSelectedPropertyId());
            log.info("📋 Selected Customer ID: {}", review.getSelectedCustomerId());
            log.info("📋 Selected Payment Source ID: {}", review.getSelectedPaymentSourceId());
            log.info("📋 User note: {}", review.getUserNote());

            try {
                // Skip validation errors
                if (review.getStatus() == ReviewStatus.VALIDATION_ERROR) {
                    log.warn("⚠️ SKIPPING - Validation error: {}", review.getErrorMessage());
                    result.addError("Line " + review.getLineNumber() + ": " + review.getErrorMessage());
                    result.incrementFailed();
                    continue;
                }

                // Skip duplicates unless user confirmed to import anyway
                if (review.getDuplicateInfo() != null && !review.isSkipDuplicate()) {
                    log.warn("⚠️ SKIPPING - Duplicate (user did not confirm import)");
                    result.incrementSkipped("review");
                    continue;
                }

                // Extract parsed data (may be strings or already parsed objects from JSON deserialization)
                Map<String, Object> data = review.getParsedData();
                log.info("📊 Parsed data keys: {}", data.keySet());
                log.info("📊 Parsed date value: {} (type: {})", data.get("parsedDate"),
                    data.get("parsedDate") != null ? data.get("parsedDate").getClass().getSimpleName() : "null");
                log.info("📊 Parsed amount value: {} (type: {})", data.get("parsedAmount"),
                    data.get("parsedAmount") != null ? data.get("parsedAmount").getClass().getSimpleName() : "null");
                log.info("📊 Description: {}", data.get("description"));
                log.info("📊 Parsed type: {}", data.get("parsedType"));

                // Handle parsedDate - could be String or LocalDate
                LocalDate transactionDate;
                Object dateObj = data.get("parsedDate");
                if (dateObj instanceof LocalDate) {
                    transactionDate = (LocalDate) dateObj;
                    log.info("✅ Date already parsed as LocalDate: {}", transactionDate);
                } else if (dateObj instanceof String) {
                    transactionDate = parseDate((String) dateObj);
                    log.info("✅ Date parsed from string: {}", transactionDate);
                } else {
                    throw new IllegalArgumentException("parsedDate must be LocalDate or String, got: " +
                        (dateObj != null ? dateObj.getClass().getName() : "null"));
                }

                // Handle parsedAmount - could be String or BigDecimal
                BigDecimal amount;
                Object amountObj = data.get("parsedAmount");
                if (amountObj instanceof BigDecimal) {
                    amount = (BigDecimal) amountObj;
                    log.info("✅ Amount already parsed as BigDecimal: {}", amount);
                } else if (amountObj instanceof String) {
                    amount = new BigDecimal((String) amountObj);
                    log.info("✅ Amount parsed from string: {}", amount);
                } else if (amountObj instanceof Number) {
                    amount = new BigDecimal(amountObj.toString());
                    log.info("✅ Amount converted from number: {}", amount);
                } else {
                    throw new IllegalArgumentException("parsedAmount must be BigDecimal, Number, or String, got: " +
                        (amountObj != null ? amountObj.getClass().getName() : "null"));
                }

                String description = (String) data.get("description");

                // Handle parsedType - could be String or TransactionType enum
                TransactionType transactionType;
                Object typeObj = data.get("parsedType");
                if (typeObj instanceof TransactionType) {
                    transactionType = (TransactionType) typeObj;
                    log.info("✅ Type already parsed as enum: {}", transactionType);
                } else if (typeObj instanceof String) {
                    transactionType = TransactionType.valueOf((String) typeObj);
                    log.info("✅ Type parsed from string: {}", transactionType);
                } else {
                    throw new IllegalArgumentException("parsedType must be TransactionType or String, got: " +
                        (typeObj != null ? typeObj.getClass().getName() : "null"));
                }

                // Get category (optional field)
                String category = (String) data.get("category");
                log.info("📁 Category: {}", category);

                log.info("✅ Successfully parsed core fields");

                // Get property (user-selected or matched)
                Property property = null;
                if (review.getSelectedPropertyId() != null) {
                    property = propertyRepository.findById(review.getSelectedPropertyId()).orElse(null);
                    log.info("🏠 Property lookup: ID={} -> {}", review.getSelectedPropertyId(),
                            property != null ? property.getPropertyName() : "NOT FOUND");
                } else {
                    log.info("🏠 No property selected");
                }

                // Get customer (user-selected or matched)
                Customer customer = null;
                if (review.getSelectedCustomerId() != null) {
                    customer = customerService.findByCustomerId(review.getSelectedCustomerId());
                    log.info("👤 Customer lookup: ID={} -> {}", review.getSelectedCustomerId(),
                            customer != null ? customer.getFullName() : "NOT FOUND");
                } else {
                    log.info("👤 No customer selected");
                }

                // Get payment source (user-selected)
                PaymentSource paymentSource = null;
                if (review.getSelectedPaymentSourceId() != null) {
                    paymentSource = paymentSourceRepository.findById(review.getSelectedPaymentSourceId()).orElse(null);
                    log.info("💳 Payment source lookup: ID={} -> {}", review.getSelectedPaymentSourceId(),
                            paymentSource != null ? paymentSource.getName() : "NOT FOUND");
                    if (paymentSource == null) {
                        log.error("❌ CRITICAL: Payment source ID {} not found in database!", review.getSelectedPaymentSourceId());
                    }
                } else {
                    log.warn("⚠️ WARNING: No payment source ID provided in review!");
                }

                // Get lease (user-selected or matched)
                Invoice lease = null;
                if (review.getSelectedLeaseId() != null) {
                    lease = invoiceRepository.findById(review.getSelectedLeaseId()).orElse(null);
                    log.info("📋 Lease lookup: ID={} -> {}", review.getSelectedLeaseId(),
                            lease != null ? lease.getLeaseReference() : "NOT FOUND");
                    if (lease != null) {
                        log.info("📋 Lease details: {} - {} to {} (£{}/month)",
                                lease.getLeaseReference(),
                                lease.getStartDate(),
                                lease.getEndDate() != null ? lease.getEndDate() : "Ongoing",
                                lease.getAmount());
                    }
                } else {
                    log.info("📋 No lease selected");
                }

                // Get current user for createdBy field (required)
                User currentUser = getCurrentUser();
                log.info("👨‍💼 Current user: {}", currentUser != null ? currentUser.getEmail() : "NULL!");

                if (currentUser == null) {
                    throw new IllegalStateException("Cannot import transaction: current user not found");
                }

                // Create transaction
                log.info("🔨 Creating HistoricalTransaction entity...");

                // Ensure description is not blank - generate default if needed
                String finalDescription = description;
                if (finalDescription == null || finalDescription.trim().isEmpty()) {
                    // Generate smart default based on transaction type and context
                    StringBuilder defaultDesc = new StringBuilder();

                    if (transactionType != null) {
                        defaultDesc.append(transactionType.getDisplayName());
                    } else {
                        defaultDesc.append("Transaction");
                    }

                    if (property != null) {
                        defaultDesc.append(" - ").append(property.getPropertyName());
                    }

                    if (customer != null) {
                        defaultDesc.append(" - ").append(customer.getName());
                    }

                    if (category != null && !category.isEmpty()) {
                        defaultDesc.append(" (").append(category).append(")");
                    }

                    finalDescription = defaultDesc.toString();
                    log.warn("⚠️ Description was blank - generated default: '{}'", finalDescription);
                }

                HistoricalTransaction transaction = new HistoricalTransaction();
                transaction.setTransactionDate(transactionDate);
                transaction.setAmount(amount);
                transaction.setDescription(finalDescription);
                transaction.setTransactionType(transactionType);
                transaction.setCategory(category);  // Set category field
                transaction.setProperty(property);
                transaction.setCustomer(customer);
                transaction.setPaymentSource(paymentSource);
                transaction.setSource(TransactionSource.historical_import); // Set source field
                transaction.setCreatedByUser(currentUser);
                transaction.setImportBatchId(batchId);

                // Set transaction level based on property/customer presence
                setTransactionLevel(transaction);
                transaction.setCreatedAt(LocalDateTime.now());

                // Link to lease (if selected) and capture lease details at time of transaction
                if (lease != null) {
                    transaction.setInvoice(lease);
                    transaction.setLeaseStartDate(lease.getStartDate());
                    transaction.setLeaseEndDate(lease.getEndDate());
                    transaction.setRentAmountAtTransaction(lease.getAmount());
                    log.info("✅ Transaction linked to lease: {}", lease.getLeaseReference());
                }

                log.info("✅ Transaction entity created successfully");
                log.info("📝 Transaction details:");
                log.info("   - Date: {}", transaction.getTransactionDate());
                log.info("   - Amount: {}", transaction.getAmount());
                log.info("   - Type: {}", transaction.getTransactionType());
                log.info("   - Category: {}", transaction.getCategory());
                log.info("   - Description: {}", transaction.getDescription());
                log.info("   - Property: {} (ID: {})", property != null ? property.getPropertyName() : "NONE", property != null ? property.getId() : "NULL");
                log.info("   - Customer: {} (ID: {})", customer != null ? customer.getFullName() : "NONE", customer != null ? customer.getCustomerId() : "NULL");
                log.info("   - Lease: {} (ID: {})", lease != null ? lease.getLeaseReference() : "NONE", lease != null ? lease.getId() : "NULL");
                log.info("   - Payment Source: {} (ID: {})", paymentSource != null ? paymentSource.getName() : "NONE", paymentSource != null ? paymentSource.getId() : "NULL");
                log.info("   - Source: {}", transaction.getSource());
                log.info("   - Created By: {}", currentUser.getEmail());
                log.info("   - Batch ID: {}", batchId);

                // Add user note if provided
                if (review.getUserNote() != null && !review.getUserNote().isEmpty()) {
                    String notes = transaction.getNotes();
                    transaction.setNotes(notes != null ? notes + " | " + review.getUserNote() : review.getUserNote());
                    log.info("📝 User note added: {}", review.getUserNote());
                }

                // Save transaction
                log.info("💾 Attempting to save transaction to database...");
                HistoricalTransaction savedTransaction = historicalTransactionRepository.save(transaction);
                log.info("✅ ✅ ✅ TRANSACTION SAVED SUCCESSFULLY! Database ID: {}", savedTransaction.getId());

                result.incrementSuccessful();

                log.info("✅ Successfully imported transaction from line {}", review.getLineNumber());

            } catch (jakarta.validation.ConstraintViolationException cve) {
                log.error("❌ ❌ ❌ CONSTRAINT VIOLATION ON LINE {}", review.getLineNumber());
                log.error("❌ Constraint violations:");
                cve.getConstraintViolations().forEach(violation -> {
                    log.error("   - Field: {} | Invalid value: {} | Message: {}",
                        violation.getPropertyPath(),
                        violation.getInvalidValue(),
                        violation.getMessage());
                });
                log.error("❌ Full exception:", cve);

                String errorMsg = cve.getConstraintViolations().stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(java.util.stream.Collectors.joining(", "));
                result.addError("Line " + review.getLineNumber() + ": Validation failed - " + errorMsg);
                result.incrementFailed();
            } catch (Exception e) {
                log.error("❌ ❌ ❌ EXCEPTION OCCURRED WHILE PROCESSING LINE {}", review.getLineNumber());
                log.error("❌ Exception type: {}", e.getClass().getName());
                log.error("❌ Exception message: {}", e.getMessage());
                log.error("❌ Full stack trace:", e);
                result.addError("Line " + review.getLineNumber() + ": " + e.getMessage());
                result.incrementFailed();
            }

            log.info("========== END REVIEW {} ==========", (i+1));
        }

        log.info("🏁 ============================================");
        log.info("🏁 IMPORT COMPLETE");
        log.info("🏁 Total processed: {}", result.getTotalProcessed());
        log.info("🏁 Successful imports: {}", result.getSuccessfulImports());
        log.info("🏁 Skipped duplicates: {}", result.getSkippedDuplicates());
        log.info("🏁 Failed imports: {}", result.getFailedImports());
        log.info("🏁 Errors: {}", result.getErrors());
        log.info("🏁 ============================================");

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

    /**
     * Set transaction level based on property and customer presence
     * - property: Transaction specific to a single property
     * - block: Transaction for a block (property is a block property)
     * - owner: Transaction for an owner across their portfolio (no property)
     * - portfolio: Portfolio-wide transaction
     */
    private void setTransactionLevel(HistoricalTransaction transaction) {
        Property property = transaction.getProperty();
        Customer customer = transaction.getCustomer();

        if (property != null) {
            // Check if property is a block
            if (property.getPropertyType() != null &&
                "block".equalsIgnoreCase(property.getPropertyType())) {
                transaction.setTransactionLevel(TransactionLevel.block);
            } else {
                transaction.setTransactionLevel(TransactionLevel.property);
            }
        } else if (customer != null) {
            // No property but has customer = owner-level transaction
            transaction.setTransactionLevel(TransactionLevel.owner);
        } else {
            // No property and no customer = portfolio-level
            transaction.setTransactionLevel(TransactionLevel.portfolio);
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
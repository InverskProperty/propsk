package site.easy.to.build.crm.service.expense;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import site.easy.to.build.crm.dto.expense.ExpenseDocumentDTO;
import site.easy.to.build.crm.dto.expense.ExpenseInvoiceDTO;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.*;
import site.easy.to.build.crm.service.drive.SharedDriveFileService;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing expense documents including:
 * - Generated expense invoices (PDFs)
 * - Uploaded receipts and vendor invoices
 * - Linking documents to expense transactions
 */
@Service
public class ExpenseDocumentService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseDocumentService.class);

    // Folder categories for expense documents
    public static final String FOLDER_EXPENSE_INVOICES = "Expense Invoices";
    public static final String FOLDER_RECEIPTS = "Receipts";

    @Autowired
    private ExpenseDocumentRepository expenseDocumentRepository;

    @Autowired
    private GoogleDriveFileRepository googleDriveFileRepository;

    @Autowired
    private UnifiedTransactionRepository unifiedTransactionRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private CustomerPropertyAssignmentRepository customerPropertyAssignmentRepository;

    @Autowired
    private SharedDriveFileService sharedDriveFileService;

    @Autowired
    private ExpenseInvoiceService expenseInvoiceService;

    // ===== DOCUMENT RETRIEVAL =====

    /**
     * Get all expense documents for a property.
     */
    public List<ExpenseDocumentDTO> getDocumentsForProperty(Long propertyId) {
        List<ExpenseDocument> documents = expenseDocumentRepository
                .findByPropertyIdAndStatusNotOrderByCreatedAtDesc(propertyId, ExpenseDocument.DocumentStatus.ARCHIVED);

        return enrichDocumentsWithTransactionData(documents);
    }

    /**
     * Get all expense documents for a customer's properties.
     */
    public List<ExpenseDocumentDTO> getDocumentsForCustomer(Integer customerId) {
        List<ExpenseDocument> documents = expenseDocumentRepository
                .findByCustomerIdAndStatusNotOrderByCreatedAtDesc(customerId, ExpenseDocument.DocumentStatus.ARCHIVED);

        return enrichDocumentsWithTransactionData(documents);
    }

    /**
     * Get all expense documents accessible by a property owner.
     */
    public List<ExpenseDocumentDTO> getDocumentsForOwner(Integer ownerId) {
        List<ExpenseDocument> documents = expenseDocumentRepository
                .findByOwnerCustomerId(ownerId, ExpenseDocument.DocumentStatus.ARCHIVED);

        return enrichDocumentsWithTransactionData(documents);
    }

    /**
     * Get documents for a specific transaction.
     */
    public List<ExpenseDocumentDTO> getDocumentsForTransaction(Long transactionId) {
        List<ExpenseDocument> documents = expenseDocumentRepository
                .findByUnifiedTransactionIdAndStatusNot(transactionId, ExpenseDocument.DocumentStatus.ARCHIVED);

        return enrichDocumentsWithTransactionData(documents);
    }

    /**
     * Enrich documents with transaction data for display.
     */
    private List<ExpenseDocumentDTO> enrichDocumentsWithTransactionData(List<ExpenseDocument> documents) {
        List<ExpenseDocumentDTO> dtos = new ArrayList<>();

        for (ExpenseDocument doc : documents) {
            ExpenseDocumentDTO dto = ExpenseDocumentDTO.fromEntity(doc);

            // Enrich with transaction data
            if (doc.getUnifiedTransactionId() != null) {
                unifiedTransactionRepository.findById(doc.getUnifiedTransactionId())
                        .ifPresent(txn -> {
                            dto.setTransactionDate(txn.getTransactionDate());
                            dto.setTransactionDescription(txn.getDescription());
                            dto.setTransactionCategory(txn.getCategory());
                            dto.setAmount(txn.getAmount());
                            dto.setPropertyName(txn.getPropertyName());
                        });
            }

            // Enrich with Google Drive file info
            if (doc.getGoogleDriveFileId() != null) {
                googleDriveFileRepository.findById(doc.getGoogleDriveFileId())
                        .ifPresent(file -> dto.setDriveFileId(file.getDriveFileId()));
            }

            dtos.add(dto);
        }

        return dtos;
    }

    // ===== RECEIPT UPLOAD =====

    /**
     * Upload a receipt and link it to an expense transaction.
     *
     * @param transactionId The unified transaction ID
     * @param file The receipt file to upload
     * @param description Optional description
     * @param uploadedBy User ID who uploaded
     * @return The created ExpenseDocument
     */
    @Transactional
    public ExpenseDocument uploadReceipt(Long transactionId, MultipartFile file, String description, Integer uploadedBy)
            throws IOException, GeneralSecurityException {

        log.info("Uploading receipt for transaction: {}", transactionId);

        // Get transaction details
        UnifiedTransaction transaction = unifiedTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionId));

        // Upload file to Google Drive
        Property property = null;
        if (transaction.getPropertyId() != null) {
            property = propertyRepository.findById(transaction.getPropertyId()).orElse(null);
        }

        // Upload to property's Receipts folder
        Map<String, Object> uploadResult = uploadToPropertyExpenseFolder(
                transaction.getPropertyId(), FOLDER_RECEIPTS, file);

        String driveFileId = (String) uploadResult.get("id");
        String fileName = (String) uploadResult.get("name");

        // Create GoogleDriveFile record
        GoogleDriveFile driveFile = new GoogleDriveFile();
        driveFile.setDriveFileId(driveFileId);
        driveFile.setFileName(fileName);
        driveFile.setFileCategory(FOLDER_RECEIPTS);
        driveFile.setFileDescription(description);
        driveFile.setPropertyId(transaction.getPropertyId());
        driveFile.setEntityType("expense");
        driveFile.setIsActive(true);
        driveFile.setCreatedAt(LocalDateTime.now());

        // Get customer ID from property owner
        if (transaction.getPropertyId() != null) {
            Integer ownerId = findPropertyOwnerId(transaction.getPropertyId());
            driveFile.setCustomerId(ownerId);
        }

        driveFile = googleDriveFileRepository.save(driveFile);
        log.info("Created GoogleDriveFile record: {}", driveFile.getId());

        // Create ExpenseDocument record
        ExpenseDocument expenseDoc = new ExpenseDocument();
        expenseDoc.setUnifiedTransactionId(transactionId);
        expenseDoc.setGoogleDriveFileId(driveFile.getId());
        expenseDoc.setDocumentType(ExpenseDocument.DocumentType.RECEIPT);
        expenseDoc.setStatus(ExpenseDocument.DocumentStatus.AVAILABLE);
        expenseDoc.setDocumentDescription(description != null ? description : transaction.getDescription());
        expenseDoc.setPropertyId(transaction.getPropertyId());
        expenseDoc.setCreatedBy(uploadedBy);
        expenseDoc.setCreatedAt(LocalDateTime.now());

        // Extract vendor from transaction if available
        if (transaction.getDescription() != null) {
            expenseDoc.setVendorName(extractVendorFromDescription(transaction.getDescription()));
        }

        expenseDoc = expenseDocumentRepository.save(expenseDoc);
        log.info("Created ExpenseDocument record: {}", expenseDoc.getId());

        return expenseDoc;
    }

    /**
     * Upload a vendor invoice (not a receipt) and link it to a transaction.
     */
    @Transactional
    public ExpenseDocument uploadVendorInvoice(Long transactionId, MultipartFile file, String vendorName,
                                                String invoiceNumber, String description, Integer uploadedBy)
            throws IOException, GeneralSecurityException {

        log.info("Uploading vendor invoice for transaction: {}", transactionId);

        UnifiedTransaction transaction = unifiedTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionId));

        // Upload to property's Expense Invoices folder
        Map<String, Object> uploadResult = uploadToPropertyExpenseFolder(
                transaction.getPropertyId(), FOLDER_EXPENSE_INVOICES, file);

        String driveFileId = (String) uploadResult.get("id");
        String fileName = (String) uploadResult.get("name");

        // Create GoogleDriveFile record
        GoogleDriveFile driveFile = new GoogleDriveFile();
        driveFile.setDriveFileId(driveFileId);
        driveFile.setFileName(fileName);
        driveFile.setFileCategory(FOLDER_EXPENSE_INVOICES);
        driveFile.setFileDescription(description);
        driveFile.setPropertyId(transaction.getPropertyId());
        driveFile.setEntityType("expense");
        driveFile.setIsActive(true);
        driveFile.setCreatedAt(LocalDateTime.now());

        if (transaction.getPropertyId() != null) {
            Integer ownerId = findPropertyOwnerId(transaction.getPropertyId());
            driveFile.setCustomerId(ownerId);
        }

        driveFile = googleDriveFileRepository.save(driveFile);

        // Create ExpenseDocument record
        ExpenseDocument expenseDoc = new ExpenseDocument();
        expenseDoc.setUnifiedTransactionId(transactionId);
        expenseDoc.setGoogleDriveFileId(driveFile.getId());
        expenseDoc.setDocumentType(ExpenseDocument.DocumentType.VENDOR_INVOICE);
        expenseDoc.setStatus(ExpenseDocument.DocumentStatus.AVAILABLE);
        expenseDoc.setDocumentNumber(invoiceNumber);
        expenseDoc.setDocumentDescription(description);
        expenseDoc.setVendorName(vendorName);
        expenseDoc.setPropertyId(transaction.getPropertyId());
        expenseDoc.setCreatedBy(uploadedBy);
        expenseDoc.setCreatedAt(LocalDateTime.now());

        expenseDoc = expenseDocumentRepository.save(expenseDoc);
        log.info("Created ExpenseDocument record for vendor invoice: {}", expenseDoc.getId());

        return expenseDoc;
    }

    // ===== INVOICE GENERATION =====

    /**
     * Generate an expense invoice PDF for a transaction.
     *
     * @param transactionId The unified transaction ID
     * @return byte array containing the PDF
     */
    public byte[] generateExpenseInvoicePdf(Long transactionId) throws IOException {
        return expenseInvoiceService.exportToPdf(transactionId);
    }

    /**
     * Generate and store an expense invoice for a transaction.
     * Stores the PDF in Google Drive and creates an ExpenseDocument record.
     *
     * @param transactionId The unified transaction ID
     * @param createdBy User ID who generated
     * @return The created ExpenseDocument
     */
    @Transactional
    public ExpenseDocument generateAndStoreExpenseInvoice(Long transactionId, Integer createdBy)
            throws IOException, GeneralSecurityException {

        log.info("Generating and storing expense invoice for transaction: {}", transactionId);

        UnifiedTransaction transaction = unifiedTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionId));

        // Generate invoice DTO
        ExpenseInvoiceDTO invoiceDto = expenseInvoiceService.generateExpenseInvoice(transactionId);

        // Generate PDF
        byte[] pdfBytes = expenseInvoiceService.exportToPdf(invoiceDto);

        // Create a temp file to upload
        String fileName = invoiceDto.getInvoiceNumber() + ".pdf";

        // Upload to Google Drive
        // For now, we'll just store the reference without uploading to Drive
        // The PDF can be generated on-demand

        // Create ExpenseDocument record
        ExpenseDocument expenseDoc = new ExpenseDocument();
        expenseDoc.setUnifiedTransactionId(transactionId);
        expenseDoc.setDocumentType(ExpenseDocument.DocumentType.INVOICE);
        expenseDoc.setStatus(ExpenseDocument.DocumentStatus.AVAILABLE);
        expenseDoc.setDocumentNumber(invoiceDto.getInvoiceNumber());
        expenseDoc.setDocumentDescription("Generated expense invoice for " + transaction.getDescription());
        expenseDoc.setVendorName(invoiceDto.getVendorName());
        expenseDoc.setPropertyId(transaction.getPropertyId());
        expenseDoc.setCreatedBy(createdBy);
        expenseDoc.setCreatedAt(LocalDateTime.now());

        // Store generated path reference (PDF is generated on-demand)
        String generatedPath = String.format("generated/expense/%d/%s",
                transaction.getPropertyId() != null ? transaction.getPropertyId() : 0,
                invoiceDto.getInvoiceNumber());
        expenseDoc.setGeneratedDocumentPath(generatedPath);

        expenseDoc = expenseDocumentRepository.save(expenseDoc);
        log.info("Created ExpenseDocument for generated invoice: {}", expenseDoc.getId());

        return expenseDoc;
    }

    // ===== EXPENSE LIST WITH DOCUMENTS =====

    /**
     * Get expenses for a property with document availability info.
     * This is useful for showing which expenses have receipts/invoices attached.
     *
     * Expenses are identified by:
     * 1. FlowDirection.OUTGOING if set
     * 2. OR negative amounts (outflows)
     * 3. OR expense-related categories
     */
    public List<Map<String, Object>> getExpensesWithDocumentStatus(Long propertyId) {
        log.debug("Getting expenses with document status for property: {}", propertyId);

        // Get ALL transactions for property and filter for expenses
        List<UnifiedTransaction> allTransactions = unifiedTransactionRepository.findByPropertyId(propertyId);
        log.debug("Found {} total transactions for property {}", allTransactions.size(), propertyId);

        // Filter for expense transactions (outgoing/negative amounts)
        List<UnifiedTransaction> expenses = allTransactions.stream()
                .filter(tx -> isExpenseTransaction(tx))
                .collect(Collectors.toList());

        log.debug("Filtered to {} expense transactions for property {}", expenses.size(), propertyId);

        // Get all documents for property
        Map<Long, List<ExpenseDocument>> docsByTransaction = expenseDocumentRepository
                .findByPropertyIdAndStatusNotOrderByCreatedAtDesc(propertyId, ExpenseDocument.DocumentStatus.ARCHIVED)
                .stream()
                .filter(d -> d.getUnifiedTransactionId() != null)
                .collect(Collectors.groupingBy(ExpenseDocument::getUnifiedTransactionId));

        List<Map<String, Object>> result = new ArrayList<>();

        for (UnifiedTransaction expense : expenses) {
            Map<String, Object> expenseInfo = new HashMap<>();
            expenseInfo.put("transactionId", expense.getId());
            expenseInfo.put("date", expense.getTransactionDate());
            expenseInfo.put("description", expense.getDescription());
            expenseInfo.put("category", expense.getCategory());
            expenseInfo.put("amount", expense.getAmount());

            List<ExpenseDocument> docs = docsByTransaction.getOrDefault(expense.getId(), Collections.emptyList());
            expenseInfo.put("hasReceipt", docs.stream().anyMatch(d ->
                    d.getDocumentType() == ExpenseDocument.DocumentType.RECEIPT));
            expenseInfo.put("hasInvoice", docs.stream().anyMatch(d ->
                    d.getDocumentType() == ExpenseDocument.DocumentType.INVOICE ||
                    d.getDocumentType() == ExpenseDocument.DocumentType.VENDOR_INVOICE));
            expenseInfo.put("documentCount", docs.size());
            expenseInfo.put("documents", docs.stream()
                    .map(ExpenseDocumentDTO::fromEntity)
                    .collect(Collectors.toList()));

            // Get invoice source type info
            try {
                ExpenseInvoiceDTO invoiceInfo = expenseInvoiceService.generateExpenseInvoice(expense.getId());
                expenseInfo.put("invoiceSourceType", invoiceInfo.getInvoiceSourceType().name());
                expenseInfo.put("shouldGenerateInvoice", invoiceInfo.isShouldGenerateInvoice());
                expenseInfo.put("thirdPartyVendorName", invoiceInfo.getThirdPartyVendorName());
            } catch (Exception e) {
                log.warn("Could not determine invoice source type for transaction {}: {}", expense.getId(), e.getMessage());
                expenseInfo.put("invoiceSourceType", "AGENCY_GENERATED");
                expenseInfo.put("shouldGenerateInvoice", true);
                expenseInfo.put("thirdPartyVendorName", null);
            }

            result.add(expenseInfo);
        }

        log.debug("Returning {} expenses with document status for property {}", result.size(), propertyId);
        return result;
    }

    /**
     * Determine if a transaction is an expense (property costs, NOT owner payments).
     *
     * EXPENSES include: repairs, maintenance, agency fees, commissions, utilities, insurance, etc.
     * NOT EXPENSES: owner payments/disbursements (money paid TO the property owner)
     */
    private boolean isExpenseTransaction(UnifiedTransaction tx) {
        String category = tx.getCategory() != null ? tx.getCategory().toLowerCase() : "";
        String description = tx.getDescription() != null ? tx.getDescription().toLowerCase() : "";
        String transactionType = tx.getTransactionType() != null ? tx.getTransactionType().toLowerCase() : "";

        // EXCLUDE owner payments/disbursements - these are NOT expenses
        if (isOwnerPayment(category, description, transactionType)) {
            return false;
        }

        // EXCLUDE rent received - this is income, not expense
        if (isRentIncome(category, description, transactionType)) {
            return false;
        }

        // Check for expense-related categories
        if (category.contains("expense") ||
            category.contains("repair") ||
            category.contains("maintenance") ||
            category.contains("commission") ||
            category.contains("agency fee") ||
            category.contains("management fee") ||
            category.contains("insurance") ||
            category.contains("utility") ||
            category.contains("utilities") ||
            category.contains("cleaning") ||
            category.contains("gardening") ||
            category.contains("contractor") ||
            category.contains("service charge") ||
            category.contains("ground rent") ||
            category.contains("legal") ||
            category.contains("accounting")) {
            return true;
        }

        // Check description for expense indicators
        if (description.contains("repair") ||
            description.contains("maintenance") ||
            description.contains("commission") ||
            description.contains("agency fee") ||
            description.contains("management fee") ||
            description.contains("expense") ||
            description.contains("invoice") ||
            description.contains("contractor") ||
            description.contains("plumber") ||
            description.contains("electrician") ||
            description.contains("cleaning") ||
            description.contains("gardening") ||
            description.contains("insurance") ||
            description.contains("service charge")) {
            return true;
        }

        // Check transaction type
        if (transactionType.contains("expense") ||
            transactionType.contains("commission") ||
            transactionType.contains("fee")) {
            return true;
        }

        return false;
    }

    /**
     * Check if transaction is an owner payment/disbursement (NOT an expense).
     */
    private boolean isOwnerPayment(String category, String description, String transactionType) {
        return category.contains("owner payment") ||
               category.contains("owner payout") ||
               category.contains("landlord payment") ||
               category.contains("disbursement") ||
               category.contains("net to owner") ||
               category.contains("payment to owner") ||
               category.contains("payout to owner") ||
               description.contains("owner payment") ||
               description.contains("payment to owner") ||
               description.contains("landlord payment") ||
               description.contains("net to owner") ||
               description.contains("disbursement to") ||
               description.contains("payout to owner") ||
               description.contains("owner payout") ||
               transactionType.contains("owner_payment") ||
               transactionType.contains("disbursement") ||
               transactionType.contains("payout");
    }

    /**
     * Check if transaction is rent income (NOT an expense).
     */
    private boolean isRentIncome(String category, String description, String transactionType) {
        return category.contains("rent received") ||
               category.contains("rental income") ||
               category.contains("tenant payment") ||
               description.contains("rent from") ||
               description.contains("rent received") ||
               description.contains("tenant payment") ||
               description.contains("rental payment") ||
               transactionType.contains("rent") ||
               transactionType.contains("income");
    }

    // ===== HELPER METHODS =====

    /**
     * Upload file to property's expense folder.
     */
    private Map<String, Object> uploadToPropertyExpenseFolder(Long propertyId, String folderName, MultipartFile file)
            throws IOException, GeneralSecurityException {

        if (propertyId == null) {
            throw new IllegalArgumentException("Property ID is required for expense document upload");
        }

        // Use existing shared drive service to upload
        // We need to create the expense folders first if they don't exist
        return sharedDriveFileService.uploadToPropertySubfolder(propertyId, "Miscellaneous", file);
    }

    /**
     * Find the property owner customer ID.
     * Returns Integer for compatibility with GoogleDriveFile entity.
     */
    private Integer findPropertyOwnerId(Long propertyId) {
        return customerPropertyAssignmentRepository.findByPropertyIdAndAssignmentType(propertyId, AssignmentType.OWNER)
                .stream()
                .findFirst()
                .map(cpa -> {
                    Long customerId = cpa.getCustomer().getCustomerId();
                    return customerId != null ? customerId.intValue() : null;
                })
                .orElse(null);
    }

    /**
     * Extract vendor name from transaction description.
     */
    private String extractVendorFromDescription(String description) {
        if (description == null) return null;

        // Common patterns: "Payment to X", "X - invoice", etc.
        if (description.toLowerCase().startsWith("payment to ")) {
            return description.substring("payment to ".length()).split(" - ")[0].trim();
        }

        // Return first part before " - " if exists
        if (description.contains(" - ")) {
            return description.split(" - ")[0].trim();
        }

        return null;
    }

    // ===== DOCUMENT MANAGEMENT =====

    /**
     * Archive (soft delete) an expense document.
     */
    @Transactional
    public void archiveDocument(Long documentId) {
        ExpenseDocument doc = expenseDocumentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));

        doc.setStatus(ExpenseDocument.DocumentStatus.ARCHIVED);
        doc.setUpdatedAt(LocalDateTime.now());
        expenseDocumentRepository.save(doc);

        log.info("Archived expense document: {}", documentId);
    }

    /**
     * Get document by ID.
     */
    public ExpenseDocument getDocument(Long documentId) {
        return expenseDocumentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));
    }

    /**
     * Download receipt file content.
     */
    public void downloadReceipt(Long documentId, java.io.OutputStream outputStream)
            throws IOException, GeneralSecurityException {

        ExpenseDocument doc = getDocument(documentId);

        if (doc.getGoogleDriveFileId() == null) {
            throw new RuntimeException("Document has no attached file");
        }

        GoogleDriveFile driveFile = googleDriveFileRepository.findById(doc.getGoogleDriveFileId())
                .orElseThrow(() -> new RuntimeException("Drive file not found"));

        sharedDriveFileService.downloadFileContent(driveFile.getDriveFileId(), outputStream);
    }
}

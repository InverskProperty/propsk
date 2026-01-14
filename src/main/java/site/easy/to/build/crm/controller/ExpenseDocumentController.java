package site.easy.to.build.crm.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import site.easy.to.build.crm.dto.expense.ExpenseDocumentDTO;
import site.easy.to.build.crm.dto.expense.ExpenseInvoiceDTO;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.UnifiedTransactionRepository;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.expense.ExpenseDocumentService;
import site.easy.to.build.crm.service.expense.ExpenseInvoiceService;
import site.easy.to.build.crm.service.property.PropertyService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for expense document management.
 * Handles:
 * - Expense invoice PDF generation and download
 * - Receipt upload and management
 * - Document listing for portal
 */
@Controller
@RequestMapping("/expense-documents")
public class ExpenseDocumentController {

    private static final Logger log = LoggerFactory.getLogger(ExpenseDocumentController.class);

    @Autowired
    private ExpenseDocumentService expenseDocumentService;

    @Autowired
    private ExpenseInvoiceService expenseInvoiceService;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private UnifiedTransactionRepository unifiedTransactionRepository;

    // ===== PDF GENERATION ENDPOINTS =====

    /**
     * Generate and download expense invoice PDF for a transaction.
     */
    @GetMapping("/invoice/{transactionId}/pdf")
    public void downloadExpenseInvoicePdf(@PathVariable Long transactionId, HttpServletResponse response)
            throws IOException {

        log.info("Generating expense invoice PDF for transaction: {}", transactionId);

        try {
            byte[] pdfContent = expenseInvoiceService.exportToPdf(transactionId);

            // Get invoice number for filename
            ExpenseInvoiceDTO invoice = expenseInvoiceService.generateExpenseInvoice(transactionId);
            String filename = invoice.getInvoiceNumber() + ".pdf";

            response.setContentType(MediaType.APPLICATION_PDF_VALUE);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
            response.setContentLength(pdfContent.length);
            response.getOutputStream().write(pdfContent);
            response.getOutputStream().flush();

        } catch (Exception e) {
            log.error("Error generating PDF for transaction {}: {}", transactionId, e.getMessage(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error generating PDF: " + e.getMessage());
        }
    }

    /**
     * View expense invoice PDF inline (for preview).
     */
    @GetMapping("/invoice/{transactionId}/view")
    public void viewExpenseInvoicePdf(@PathVariable Long transactionId, HttpServletResponse response)
            throws IOException {

        log.info("Viewing expense invoice PDF for transaction: {}", transactionId);

        try {
            byte[] pdfContent = expenseInvoiceService.exportToPdf(transactionId);

            ExpenseInvoiceDTO invoice = expenseInvoiceService.generateExpenseInvoice(transactionId);
            String filename = invoice.getInvoiceNumber() + ".pdf";

            response.setContentType(MediaType.APPLICATION_PDF_VALUE);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"");
            response.setContentLength(pdfContent.length);
            response.getOutputStream().write(pdfContent);
            response.getOutputStream().flush();

        } catch (Exception e) {
            log.error("Error generating PDF for transaction {}: {}", transactionId, e.getMessage(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error generating PDF: " + e.getMessage());
        }
    }

    // ===== RECEIPT MANAGEMENT ENDPOINTS =====

    /**
     * Upload a receipt for an expense transaction.
     */
    @PostMapping("/receipt/upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadReceipt(
            @RequestParam("transactionId") Long transactionId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description,
            Authentication authentication) {

        log.info("Uploading receipt for transaction: {}", transactionId);

        Map<String, Object> response = new HashMap<>();

        try {
            Integer userId = getCurrentUserId(authentication);

            ExpenseDocument document = expenseDocumentService.uploadReceipt(
                    transactionId, file, description, userId);

            response.put("success", true);
            response.put("documentId", document.getId());
            response.put("message", "Receipt uploaded successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error uploading receipt: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Upload a vendor invoice for an expense transaction.
     */
    @PostMapping("/vendor-invoice/upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadVendorInvoice(
            @RequestParam("transactionId") Long transactionId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "vendorName", required = false) String vendorName,
            @RequestParam(value = "invoiceNumber", required = false) String invoiceNumber,
            @RequestParam(value = "description", required = false) String description,
            Authentication authentication) {

        log.info("Uploading vendor invoice for transaction: {}", transactionId);

        Map<String, Object> response = new HashMap<>();

        try {
            Integer userId = getCurrentUserId(authentication);

            ExpenseDocument document = expenseDocumentService.uploadVendorInvoice(
                    transactionId, file, vendorName, invoiceNumber, description, userId);

            response.put("success", true);
            response.put("documentId", document.getId());
            response.put("message", "Vendor invoice uploaded successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error uploading vendor invoice: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Download an uploaded receipt.
     */
    @GetMapping("/receipt/{documentId}/download")
    public void downloadReceipt(@PathVariable Long documentId, HttpServletResponse response)
            throws IOException {

        log.info("Downloading receipt document: {}", documentId);

        try {
            ExpenseDocument document = expenseDocumentService.getDocument(documentId);

            // Set response headers
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"receipt-" + documentId + "\"");

            expenseDocumentService.downloadReceipt(documentId, response.getOutputStream());
            response.getOutputStream().flush();

        } catch (GeneralSecurityException e) {
            log.error("Security error downloading receipt: {}", e.getMessage(), e);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
        } catch (Exception e) {
            log.error("Error downloading receipt: {}", e.getMessage(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error downloading file");
        }
    }

    // ===== DOCUMENT LISTING ENDPOINTS =====

    /**
     * Get all expense documents for a property (API).
     */
    @GetMapping("/api/property/{propertyId}")
    @ResponseBody
    public ResponseEntity<List<ExpenseDocumentDTO>> getDocumentsForProperty(@PathVariable Long propertyId) {
        List<ExpenseDocumentDTO> documents = expenseDocumentService.getDocumentsForProperty(propertyId);
        return ResponseEntity.ok(documents);
    }

    /**
     * Get all expense documents for a customer (API).
     */
    @GetMapping("/api/customer/{customerId}")
    @ResponseBody
    public ResponseEntity<List<ExpenseDocumentDTO>> getDocumentsForCustomer(@PathVariable Integer customerId) {
        List<ExpenseDocumentDTO> documents = expenseDocumentService.getDocumentsForCustomer(customerId);
        return ResponseEntity.ok(documents);
    }

    /**
     * Get documents for a specific transaction (API).
     */
    @GetMapping("/api/transaction/{transactionId}")
    @ResponseBody
    public ResponseEntity<List<ExpenseDocumentDTO>> getDocumentsForTransaction(@PathVariable Long transactionId) {
        List<ExpenseDocumentDTO> documents = expenseDocumentService.getDocumentsForTransaction(transactionId);
        return ResponseEntity.ok(documents);
    }

    /**
     * Get expenses with document status for a property (API).
     */
    @GetMapping("/api/property/{propertyId}/expenses-with-docs")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getExpensesWithDocuments(@PathVariable Long propertyId) {
        List<Map<String, Object>> expenses = expenseDocumentService.getExpensesWithDocumentStatus(propertyId);
        return ResponseEntity.ok(expenses);
    }

    // ===== DOCUMENT MANAGEMENT ENDPOINTS =====

    /**
     * Archive (soft delete) an expense document.
     */
    @PostMapping("/archive/{documentId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> archiveDocument(@PathVariable Long documentId) {
        Map<String, Object> response = new HashMap<>();

        try {
            expenseDocumentService.archiveDocument(documentId);
            response.put("success", true);
            response.put("message", "Document archived successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error archiving document: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Generate and store an expense invoice for later retrieval.
     */
    @PostMapping("/invoice/generate/{transactionId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateAndStoreInvoice(
            @PathVariable Long transactionId,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            Integer userId = getCurrentUserId(authentication);
            ExpenseDocument document = expenseDocumentService.generateAndStoreExpenseInvoice(transactionId, userId);

            response.put("success", true);
            response.put("documentId", document.getId());
            response.put("invoiceNumber", document.getDocumentNumber());
            response.put("message", "Invoice generated successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error generating invoice: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ===== PORTAL VIEW ENDPOINTS =====

    /**
     * Show expense documents page for a property (admin view).
     */
    @GetMapping("/property/{propertyId}")
    public String showPropertyExpenseDocuments(@PathVariable Long propertyId, Model model) {
        Property property = propertyService.findById(propertyId);
        if (property == null) {
            return "redirect:/properties";
        }

        List<ExpenseDocumentDTO> documents = expenseDocumentService.getDocumentsForProperty(propertyId);
        List<Map<String, Object>> expensesWithDocs = expenseDocumentService.getExpensesWithDocumentStatus(propertyId);

        model.addAttribute("property", property);
        model.addAttribute("documents", documents);
        model.addAttribute("expensesWithDocs", expensesWithDocs);

        return "expense/property-documents";
    }

    // ===== HELPER METHODS =====

    /**
     * Get current user ID from authentication.
     */
    private Integer getCurrentUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof User) {
            return ((User) principal).getId();
        }

        return null;
    }
}

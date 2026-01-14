package site.easy.to.build.crm.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import site.easy.to.build.crm.dto.expense.ExpenseDocumentDTO;
import site.easy.to.build.crm.dto.expense.ExpenseInvoiceDTO;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.CustomerPropertyAssignmentRepository;
import site.easy.to.build.crm.repository.UnifiedTransactionRepository;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.expense.ExpenseDocumentService;
import site.easy.to.build.crm.service.expense.ExpenseInvoiceService;
import site.easy.to.build.crm.service.property.PropertyService;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Customer portal controller for expense documents.
 * Allows property owners to view and download expense invoices and receipts.
 */
@Controller
@RequestMapping("/customer/expense-documents")
public class CustomerExpenseDocumentController {

    private static final Logger log = LoggerFactory.getLogger(CustomerExpenseDocumentController.class);

    @Autowired
    private ExpenseDocumentService expenseDocumentService;

    @Autowired
    private ExpenseInvoiceService expenseInvoiceService;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerPropertyAssignmentRepository customerPropertyAssignmentRepository;

    @Autowired
    private UnifiedTransactionRepository unifiedTransactionRepository;

    // ===== PORTAL VIEWS =====

    /**
     * Main expense documents page for logged-in customer.
     */
    @GetMapping
    public String expenseDocumentsPortal(HttpSession session, Model model) {
        Customer customer = getLoggedInCustomer(session);
        if (customer == null) {
            return "redirect:/customer/login";
        }

        // Get properties owned by this customer
        List<Property> properties = getCustomerProperties(customer.getCustomerId());

        // Get all expenses across all properties
        List<Map<String, Object>> allExpenses = new ArrayList<>();
        for (Property property : properties) {
            List<Map<String, Object>> propertyExpenses = expenseDocumentService.getExpensesWithDocumentStatus(property.getId());
            for (Map<String, Object> expense : propertyExpenses) {
                expense.put("propertyName", property.getPropertyName());
                expense.put("propertyId", property.getId());
            }
            allExpenses.addAll(propertyExpenses);
        }

        // Sort by date descending
        allExpenses.sort((a, b) -> {
            LocalDate dateA = (LocalDate) a.get("date");
            LocalDate dateB = (LocalDate) b.get("date");
            if (dateA == null && dateB == null) return 0;
            if (dateA == null) return 1;
            if (dateB == null) return -1;
            return dateB.compareTo(dateA);
        });

        // Calculate totals
        BigDecimal totalExpenses = allExpenses.stream()
                .map(e -> (BigDecimal) e.get("amount"))
                .filter(Objects::nonNull)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long expensesWithReceipts = allExpenses.stream()
                .filter(e -> Boolean.TRUE.equals(e.get("hasReceipt")))
                .count();

        long expensesWithInvoices = allExpenses.stream()
                .filter(e -> Boolean.TRUE.equals(e.get("hasInvoice")))
                .count();

        model.addAttribute("customer", customer);
        model.addAttribute("properties", properties);
        model.addAttribute("expenses", allExpenses);
        model.addAttribute("totalExpenses", totalExpenses);
        model.addAttribute("expenseCount", allExpenses.size());
        model.addAttribute("receiptsCount", expensesWithReceipts);
        model.addAttribute("invoicesCount", expensesWithInvoices);

        return "customer/expense-documents";
    }

    /**
     * Expense documents for a specific property.
     */
    @GetMapping("/property/{propertyId}")
    public String propertyExpenseDocuments(@PathVariable Long propertyId, HttpSession session, Model model) {
        Customer customer = getLoggedInCustomer(session);
        if (customer == null) {
            return "redirect:/customer/login";
        }

        // Verify customer has access to this property
        if (!customerHasAccessToProperty(customer.getCustomerId(), propertyId)) {
            return "redirect:/customer/expense-documents";
        }

        Property property = propertyService.findById(propertyId);
        if (property == null) {
            return "redirect:/customer/expense-documents";
        }

        List<Map<String, Object>> expenses = expenseDocumentService.getExpensesWithDocumentStatus(propertyId);

        // Sort by date descending
        expenses.sort((a, b) -> {
            LocalDate dateA = (LocalDate) a.get("date");
            LocalDate dateB = (LocalDate) b.get("date");
            if (dateA == null && dateB == null) return 0;
            if (dateA == null) return 1;
            if (dateB == null) return -1;
            return dateB.compareTo(dateA);
        });

        BigDecimal totalExpenses = expenses.stream()
                .map(e -> (BigDecimal) e.get("amount"))
                .filter(Objects::nonNull)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("customer", customer);
        model.addAttribute("property", property);
        model.addAttribute("expenses", expenses);
        model.addAttribute("totalExpenses", totalExpenses);

        return "customer/property-expense-documents";
    }

    // ===== PDF DOWNLOAD ENDPOINTS =====

    /**
     * Download expense invoice PDF for customer.
     */
    @GetMapping("/invoice/{transactionId}/pdf")
    public void downloadExpenseInvoice(@PathVariable Long transactionId, HttpSession session,
                                        HttpServletResponse response) throws IOException {

        Customer customer = getLoggedInCustomer(session);
        if (customer == null) {
            response.sendRedirect("/customer/login");
            return;
        }

        // Verify customer has access to this transaction
        UnifiedTransaction transaction = unifiedTransactionRepository.findById(transactionId).orElse(null);
        if (transaction == null || !customerHasAccessToProperty(customer.getCustomerId(), transaction.getPropertyId())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
            return;
        }

        try {
            byte[] pdfContent = expenseInvoiceService.exportToPdf(transactionId);
            ExpenseInvoiceDTO invoice = expenseInvoiceService.generateExpenseInvoice(transactionId);
            String filename = invoice.getInvoiceNumber() + ".pdf";

            response.setContentType(MediaType.APPLICATION_PDF_VALUE);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
            response.setContentLength(pdfContent.length);
            response.getOutputStream().write(pdfContent);
            response.getOutputStream().flush();

        } catch (Exception e) {
            log.error("Error generating PDF for transaction {}: {}", transactionId, e.getMessage(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error generating PDF");
        }
    }

    /**
     * View expense invoice PDF inline for customer.
     */
    @GetMapping("/invoice/{transactionId}/view")
    public void viewExpenseInvoice(@PathVariable Long transactionId, HttpSession session,
                                    HttpServletResponse response) throws IOException {

        Customer customer = getLoggedInCustomer(session);
        if (customer == null) {
            response.sendRedirect("/customer/login");
            return;
        }

        UnifiedTransaction transaction = unifiedTransactionRepository.findById(transactionId).orElse(null);
        if (transaction == null || !customerHasAccessToProperty(customer.getCustomerId(), transaction.getPropertyId())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
            return;
        }

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
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error generating PDF");
        }
    }

    // ===== RECEIPT DOWNLOAD =====

    /**
     * Download receipt for customer.
     */
    @GetMapping("/receipt/{documentId}/download")
    public void downloadReceipt(@PathVariable Long documentId, HttpSession session,
                                 HttpServletResponse response) throws IOException {

        Customer customer = getLoggedInCustomer(session);
        if (customer == null) {
            response.sendRedirect("/customer/login");
            return;
        }

        try {
            ExpenseDocument document = expenseDocumentService.getDocument(documentId);

            // Verify customer has access
            if (document.getPropertyId() != null &&
                !customerHasAccessToProperty(customer.getCustomerId(), document.getPropertyId())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
                return;
            }

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

    // ===== API ENDPOINTS =====

    /**
     * Get expense documents for customer (AJAX).
     */
    @GetMapping("/api/my-expenses")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getMyExpenses(HttpSession session) {
        Customer customer = getLoggedInCustomer(session);
        if (customer == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Map<String, Object> response = new HashMap<>();

        List<Property> properties = getCustomerProperties(customer.getCustomerId());
        List<Map<String, Object>> allExpenses = new ArrayList<>();

        for (Property property : properties) {
            List<Map<String, Object>> propertyExpenses = expenseDocumentService.getExpensesWithDocumentStatus(property.getId());
            for (Map<String, Object> expense : propertyExpenses) {
                expense.put("propertyName", property.getPropertyName());
                expense.put("propertyId", property.getId());
            }
            allExpenses.addAll(propertyExpenses);
        }

        response.put("expenses", allExpenses);
        response.put("count", allExpenses.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Get documents for a specific transaction (AJAX).
     */
    @GetMapping("/api/transaction/{transactionId}/documents")
    @ResponseBody
    public ResponseEntity<List<ExpenseDocumentDTO>> getTransactionDocuments(
            @PathVariable Long transactionId, HttpSession session) {

        Customer customer = getLoggedInCustomer(session);
        if (customer == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Verify access
        UnifiedTransaction transaction = unifiedTransactionRepository.findById(transactionId).orElse(null);
        if (transaction == null || !customerHasAccessToProperty(customer.getCustomerId(), transaction.getPropertyId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<ExpenseDocumentDTO> documents = expenseDocumentService.getDocumentsForTransaction(transactionId);
        return ResponseEntity.ok(documents);
    }

    // ===== HELPER METHODS =====

    /**
     * Get logged-in customer from session.
     */
    private Customer getLoggedInCustomer(HttpSession session) {
        Object customerObj = session.getAttribute("loggedInCustomer");
        if (customerObj instanceof Customer) {
            return (Customer) customerObj;
        }
        return null;
    }

    /**
     * Get properties owned by customer.
     */
    private List<Property> getCustomerProperties(Long customerId) {
        return customerPropertyAssignmentRepository.findByCustomerCustomerIdAndAssignmentType(
                        customerId, AssignmentType.OWNER)
                .stream()
                .map(CustomerPropertyAssignment::getProperty)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Check if customer has access to a property.
     */
    private boolean customerHasAccessToProperty(Long customerId, Long propertyId) {
        if (propertyId == null) return false;

        return customerPropertyAssignmentRepository.findByCustomerCustomerIdAndAssignmentType(
                        customerId, AssignmentType.OWNER)
                .stream()
                .anyMatch(cpa -> cpa.getProperty() != null && cpa.getProperty().getId().equals(propertyId));
    }
}

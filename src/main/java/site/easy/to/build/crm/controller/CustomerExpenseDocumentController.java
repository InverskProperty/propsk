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
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.dto.expense.ExpenseDocumentDTO;
import site.easy.to.build.crm.dto.expense.ExpenseInvoiceDTO;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.CustomerPropertyAssignmentRepository;
import site.easy.to.build.crm.repository.UnifiedTransactionRepository;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.expense.ExpenseDocumentService;
import site.easy.to.build.crm.service.expense.ExpenseInvoiceService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.util.AuthenticationUtils;

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
@RequestMapping("/property-owner/expense-documents")
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

    @Autowired
    private AuthenticationUtils authenticationUtils;

    // ===== PORTAL VIEWS =====

    /**
     * Main expense documents page for logged-in customer.
     */
    @GetMapping
    public String expenseDocumentsPortal(Model model, Authentication authentication) {
        Customer customer = getAuthenticatedPropertyOwner(authentication);
        if (customer == null) {
            return "redirect:/customer-login?error=not_found";
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

        return "property-owner/expense-documents";
    }

    /**
     * Expense documents for a specific property.
     */
    @GetMapping("/property/{propertyId}")
    public String propertyExpenseDocuments(@PathVariable Long propertyId, Model model, Authentication authentication) {
        Customer customer = getAuthenticatedPropertyOwner(authentication);
        if (customer == null) {
            return "redirect:/customer-login?error=not_found";
        }

        // Verify customer has access to this property
        if (!customerHasAccessToProperty(customer.getCustomerId(), propertyId)) {
            return "redirect:/property-owner/expense-documents";
        }

        Property property = propertyService.findById(propertyId);
        if (property == null) {
            return "redirect:/property-owner/expense-documents";
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

        return "property-owner/property-expense-documents";
    }

    // ===== PDF DOWNLOAD ENDPOINTS =====

    /**
     * Download expense invoice PDF for customer.
     */
    @GetMapping("/invoice/{transactionId}/pdf")
    public void downloadExpenseInvoice(@PathVariable Long transactionId, Authentication authentication,
                                        HttpServletResponse response) throws IOException {

        Customer customer = getAuthenticatedPropertyOwner(authentication);
        if (customer == null) {
            response.sendRedirect("/customer-login?error=not_found");
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
    public void viewExpenseInvoice(@PathVariable Long transactionId, Authentication authentication,
                                    HttpServletResponse response) throws IOException {

        Customer customer = getAuthenticatedPropertyOwner(authentication);
        if (customer == null) {
            response.sendRedirect("/customer-login?error=not_found");
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
    public void downloadReceipt(@PathVariable Long documentId, Authentication authentication,
                                 HttpServletResponse response) throws IOException {

        Customer customer = getAuthenticatedPropertyOwner(authentication);
        if (customer == null) {
            response.sendRedirect("/customer-login?error=not_found");
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
    public ResponseEntity<Map<String, Object>> getMyExpenses(Authentication authentication) {
        Customer customer = getAuthenticatedPropertyOwner(authentication);
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
            @PathVariable Long transactionId, Authentication authentication) {

        Customer customer = getAuthenticatedPropertyOwner(authentication);
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

    // ===== AUTHENTICATION HELPER - Matches PropertyOwnerController pattern =====

    /**
     * Get the authenticated property owner customer.
     * This method mirrors the authentication logic in PropertyOwnerController.
     */
    private Customer getAuthenticatedPropertyOwner(Authentication authentication) {
        if (authentication == null) {
            log.warn("No authentication provided");
            return null;
        }

        try {
            String email = null;

            // Check if user is admin/manager - they can access any property owner data
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN") ||
                                auth.getAuthority().equals("ROLE_SUPER_ADMIN") ||
                                auth.getAuthority().equals("ROLE_MANAGER"));

            if (isAdmin && userId > 0) {
                List<Customer> propertyOwners = customerService.findPropertyOwners();
                if (!propertyOwners.isEmpty()) {
                    return propertyOwners.get(0);
                }
            }

            // Try to extract email from OAuth2 authentication
            if (authentication instanceof OAuth2AuthenticationToken) {
                OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
                Object emailAttr = oauthToken.getPrincipal().getAttributes().get("email");
                if (emailAttr != null) {
                    email = emailAttr.toString();
                }
            }

            // Fallback to authentication name
            if (email == null) {
                String authName = authentication.getName();
                // Known OAuth ID mappings
                if ("107225176783195838221".equals(authName)) {
                    email = "sajidkazmi@inversk.com";
                } else if (!authName.matches("\\d+")) {
                    email = authName;
                }
            }

            // Lookup customer by email
            if (email != null && !email.trim().isEmpty()) {
                Customer customer = customerService.findByEmail(email);
                if (customer != null) {
                    boolean isPropertyOwner = customer.getCustomerType() == CustomerType.PROPERTY_OWNER;
                    boolean isDelegatedUser = customer.getCustomerType() == CustomerType.DELEGATED_USER;
                    boolean hasOwnerFlag = Boolean.TRUE.equals(customer.getIsPropertyOwner());

                    if (isPropertyOwner || isDelegatedUser || hasOwnerFlag) {
                        return customer;
                    }
                }
            }

            // Fallback: Search all customers
            List<Customer> allCustomers = customerService.findAll();
            for (Customer customer : allCustomers) {
                if (email != null && email.equals(customer.getEmail()) &&
                    (customer.getCustomerType() == CustomerType.PROPERTY_OWNER ||
                     customer.getCustomerType() == CustomerType.DELEGATED_USER ||
                     Boolean.TRUE.equals(customer.getIsPropertyOwner()))) {
                    return customer;
                }
            }

        } catch (Exception e) {
            log.error("Error getting authenticated property owner: {}", e.getMessage(), e);
        }

        return null;
    }

    /**
     * Get properties accessible by customer.
     * Uses PropertyService.findPropertiesAccessibleByCustomer() which handles:
     * - PROPERTY_OWNER type customers (owner assignment)
     * - DELEGATED_USER type customers (delegated access)
     * - Filtering inactive properties
     */
    private List<Property> getCustomerProperties(Long customerId) {
        log.debug("Getting accessible properties for customer: {}", customerId);
        List<Property> properties = propertyService.findPropertiesAccessibleByCustomer(customerId);
        log.debug("Found {} accessible properties for customer {}", properties.size(), customerId);
        return properties;
    }

    /**
     * Check if customer has access to a property.
     * Uses PropertyService.findPropertiesAccessibleByCustomer() for consistency.
     */
    private boolean customerHasAccessToProperty(Long customerId, Long propertyId) {
        if (propertyId == null) return false;

        List<Property> accessibleProperties = propertyService.findPropertiesAccessibleByCustomer(customerId);
        return accessibleProperties.stream()
                .anyMatch(p -> p.getId() != null && p.getId().equals(propertyId));
    }
}

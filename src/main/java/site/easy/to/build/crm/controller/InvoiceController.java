package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.Invoice;
import site.easy.to.build.crm.entity.Invoice.InvoiceFrequency;
import site.easy.to.build.crm.entity.Invoice.SyncStatus;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.invoice.InvoiceService;
import site.easy.to.build.crm.service.payprop.LocalToPayPropSyncService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Controller for Invoice Creation and Management
 * Supports both local-only and PayProp-synced invoices
 */
@Controller
@RequestMapping("/employee/invoice")
public class InvoiceController {

    @Autowired
    private InvoiceService invoiceService;
    
    @Autowired
    private CustomerService customerService;
    
    @Autowired
    private PropertyService propertyService;

    @Autowired(required = false)
    private LocalToPayPropSyncService localToPayPropSyncService;

    @Autowired
    private UserService userService;
    
    @Autowired
    private AuthenticationUtils authenticationUtils;

    /**
     * Show invoice creation page
     */
    @GetMapping("/create")
    public String showCreateInvoice(Model model, Authentication authentication) {
        try {
            // Get tenants and properties for dropdowns
            List<Customer> tenants = customerService.findTenants();
            List<Property> properties = propertyService.findAll();
            
            // PayProp invoice categories (from your documentation)
            model.addAttribute("invoiceCategories", getPayPropInvoiceCategories());
            model.addAttribute("tenants", tenants);
            model.addAttribute("properties", properties);
            model.addAttribute("frequencies", InvoiceFrequency.values());
            
            return "invoice/create-invoice";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading invoice creation page: " + e.getMessage());
            return "error/500";
        }
    }

    /**
     * Create new invoice
     */
    @PostMapping("/create")
    public String createInvoice(
            @RequestParam("customerId") Long customerId,
            @RequestParam("propertyId") Long propertyId,
            @RequestParam("categoryId") String categoryId,
            @RequestParam("amount") BigDecimal amount,
            @RequestParam("frequency") String frequencyStr,
            @RequestParam(value = "frequencyMonths", required = false) Integer frequencyMonths,
            @RequestParam(value = "paymentDay", required = false) Integer paymentDay,
            @RequestParam("startDate") String startDateStr,
            @RequestParam(value = "endDate", required = false) String endDateStr,
            @RequestParam("description") String description,
            @RequestParam(value = "syncToPayProp", defaultValue = "false") Boolean syncToPayProp,
            RedirectAttributes redirectAttributes,
            Authentication authentication) {
        
        try {
            System.out.println("📋 Creating invoice: Customer " + customerId + " → Property " + propertyId + 
                             " | Amount: £" + amount + " | Frequency: " + frequencyStr);
            
            // Get entities
            Customer customer = customerService.findByCustomerId(customerId);
            Property property = propertyService.findById(propertyId);
            InvoiceFrequency frequency = InvoiceFrequency.valueOf(frequencyStr);
            
            if (customer == null || property == null) {
                redirectAttributes.addFlashAttribute("error", "Customer or Property not found");
                return "redirect:/employee/invoice/create";
            }
            
            // Parse dates
            LocalDate startDate = LocalDate.parse(startDateStr);
            LocalDate endDate = null;
            if (endDateStr != null && !endDateStr.trim().isEmpty()) {
                endDate = LocalDate.parse(endDateStr);
            }
            
            // Validate payment day for recurring invoices
            if (frequency.requiresPaymentDay() && (paymentDay == null || paymentDay < 1 || paymentDay > 31)) {
                redirectAttributes.addFlashAttribute("error", 
                    "Payment day (1-31) is required for " + frequency + " invoices");
                return "redirect:/employee/invoice/create";
            }
            
            // Create invoice
            Invoice invoice = new Invoice();
            invoice.setCustomer(customer);
            invoice.setProperty(property);
            invoice.setCategoryId(categoryId);
            invoice.setAmount(amount);
            invoice.setFrequency(frequency);
            // Set custom frequency months if provided (for multi-month billing cycles like 6M, 7M)
            if (frequencyMonths != null && frequencyMonths > 0) {
                invoice.setFrequencyMonths(frequencyMonths);
            }
            invoice.setPaymentDay(paymentDay);
            invoice.setStartDate(startDate);
            invoice.setEndDate(endDate);
            invoice.setDescription(description);
            invoice.setIsActive(true);
            
            // Set sync status
            if (syncToPayProp) {
                invoice.setSyncStatus(SyncStatus.pending);
            } else {
                invoice.setSyncStatus(SyncStatus.manual);
            }
            
            // Save invoice
            Invoice savedInvoice = invoiceService.save(invoice);
            
            System.out.println("✅ Invoice created successfully: ID " + savedInvoice.getId());
            
            String successMsg = String.format("Successfully created %s invoice for %s - %s (£%.2f)", 
                frequency.getDisplayName(), customer.getName(), property.getPropertyName(), amount);
            
            // PayProp sync
            if (syncToPayProp) {
                try {
                    System.out.println("🔄 Syncing invoice to PayProp...");
                    
                    // First ensure customer and property are synced
                    String customerPayPropId = customer.getPayPropEntityId();
                    String propertyPayPropId = property.getPayPropId();
                    
                    // Sync customer if needed
                    if (customerPayPropId == null) {
                        System.out.println("🔄 Customer not synced, syncing as tenant first...");
                        customerPayPropId = localToPayPropSyncService.syncCustomerAsTenantToPayProp(customer);
                        System.out.println("✅ Customer synced as tenant: " + customerPayPropId);
                    }
                    
                    // Sync property if needed  
                    if (propertyPayPropId == null) {
                        System.out.println("🔄 Property not synced, syncing property first...");
                        propertyPayPropId = localToPayPropSyncService.syncPropertyToPayProp(property);
                        System.out.println("✅ Property synced: " + propertyPayPropId);
                    }
                    
                    // Now sync the invoice
                    String invoicePayPropId = localToPayPropSyncService.syncInvoiceToPayProp(savedInvoice);
                    System.out.println("✅ Invoice synced to PayProp: " + invoicePayPropId);
                    
                    // Update sync status
                    savedInvoice.setSyncStatus(SyncStatus.synced);
                    savedInvoice.setPaypropId(invoicePayPropId);
                    invoiceService.save(savedInvoice);
                    
                    redirectAttributes.addFlashAttribute("success", 
                        successMsg + " and synced to PayProp (ID: " + invoicePayPropId + ")");
                    
                } catch (Exception syncError) {
                    System.out.println("❌ PayProp sync failed: " + syncError.getMessage());
                    
                    // Update sync status to error
                    savedInvoice.setSyncStatus(SyncStatus.error);
                    invoiceService.save(savedInvoice);
                    
                    redirectAttributes.addFlashAttribute("warning", 
                        successMsg + " but PayProp sync failed: " + syncError.getMessage());
                }
            } else {
                redirectAttributes.addFlashAttribute("success", successMsg);
            }
            
        } catch (Exception e) {
            System.out.println("❌ Invoice creation failed: " + e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error creating invoice: " + e.getMessage());
        }
        
        return "redirect:/employee/invoice/create";
    }

    /**
     * Show all invoices
     */
    @GetMapping("")
    public String showInvoices(Model model, Authentication authentication) {
        try {
            // Get all local invoices
            List<Invoice> invoices = invoiceService.findAll();
            
            model.addAttribute("invoices", invoices);
            model.addAttribute("pageTitle", "Local Invoices");
            
            return "invoice/invoice-list";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error loading invoices: " + e.getMessage());
            return "error/500";
        }
    }

    /**
     * Delete invoice
     */
    @PostMapping("/delete/{id}")
    public String deleteInvoice(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Optional<Invoice> invoiceOpt = invoiceService.findById(id);
            if (invoiceOpt.isPresent()) {
                Invoice invoice = invoiceOpt.get();
                String description = invoice.getDescription() + " - " + 
                                   invoice.getCustomer().getName() + " (£" + invoice.getAmount() + ")";
                
                invoiceService.delete(id);
                redirectAttributes.addFlashAttribute("success", "Deleted invoice: " + description);
            } else {
                redirectAttributes.addFlashAttribute("error", "Invoice not found");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error deleting invoice: " + e.getMessage());
        }
        
        return "redirect:/employee/invoice";
    }

    /**
     * PayProp invoice categories (from your tested API findings)
     */
    private List<PayPropCategory> getPayPropInvoiceCategories() {
        return List.of(
            new PayPropCategory("Vv2XlY1ema", "Rent", true),      // System category
            new PayPropCategory("vagXVvX3RP", "Maintenance", false),
            new PayPropCategory("W5AJ5Oa1Mk", "Other", false),
            new PayPropCategory("woRZQl1mA4", "Deposit", false),
            new PayPropCategory("6EyJ6RJjbv", "Holding deposit", true)  // System category
        );
    }

    /**
     * PayProp category helper class
     */
    public static class PayPropCategory {
        private String id;
        private String name;
        private boolean isSystem;
        
        public PayPropCategory(String id, String name, boolean isSystem) {
            this.id = id;
            this.name = name;
            this.isSystem = isSystem;
        }
        
        // Getters
        public String getId() { return id; }
        public String getName() { return name; }
        public boolean isSystem() { return isSystem; }
    }
}
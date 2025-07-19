package site.easy.to.build.crm.service.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.google.service.acess.GoogleAccessService;
import site.easy.to.build.crm.google.service.gmail.GoogleGmailApiService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.repository.CustomerPropertyAssignmentRepository;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ✅ COMPLETE WORKING EmailServiceImpl.java
 * Fixed Gmail API integration and unified customer model support
 * Added maintenance-specific email methods for TicketController
 */
@Service
public class EmailServiceImpl implements EmailService {
    
    private static final Logger logger = LoggerFactory.getLogger(EmailServiceImpl.class);
    
    private final GoogleGmailApiService googleGmailApiService;
    private final AuthenticationUtils authenticationUtils;
    private final CustomerService customerService;
    private final Environment environment;
    
    // ✅ FIXED: Use unified customer model with junction table
    @Autowired
    private CustomerPropertyAssignmentRepository customerPropertyAssignmentRepository;
    
    // Email validation pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    
    @Autowired
    public EmailServiceImpl(GoogleGmailApiService googleGmailApiService,
                           AuthenticationUtils authenticationUtils,
                           CustomerService customerService,
                           Environment environment) {
        this.googleGmailApiService = googleGmailApiService;
        this.authenticationUtils = authenticationUtils;
        this.customerService = customerService;
        this.environment = environment;
    }
    
    @Override
    public boolean sendEmailToCustomer(Customer customer, String subject, String message, Authentication authentication) {
        if (customer == null || !isValidEmail(customer.getEmail())) {
            logger.warn("Invalid customer or email address");
            return false;
        }
        
        if (!isGmailApiAvailable(authentication)) {
            logger.warn("Gmail API not available for user");
            return false;
        }
        
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            if (oAuthUser == null) {
                logger.error("Could not get OAuth user from authentication");
                return false;
            }
            
            // ✅ FIXED: Use the correct Gmail API method signature
            googleGmailApiService.sendEmail(oAuthUser, customer.getEmail(), subject, message);
            
            logger.info("Email sent successfully to customer: {}", customer.getEmail());
            return true;
            
        } catch (Exception e) {
            logger.error("Error sending email to customer: {}", customer.getEmail(), e);
            return false;
        }
    }
    
    @Override
    public boolean sendTemplatedEmail(Customer customer, EmailTemplate template, Authentication authentication) {
        if (template == null) {
            logger.warn("Email template is null");
            return false;
        }
        
        // Replace placeholders in template with customer data
        String personalizedContent = personalizeEmailContent(template.getContent(), customer);
        
        return sendEmailToCustomer(customer, template.getName(), personalizedContent, authentication);
    }
    
    @Override
    public int sendBulkEmail(CustomerType customerType, String subject, String message, Authentication authentication) {
        // ✅ FIXED: Use unified customer model
        List<Customer> customers = customerService.findByCustomerType(customerType);
        return sendBulkEmail(customers, subject, message, authentication);
    }
    
    @Override
    public int sendBulkEmail(List<Customer> customers, String subject, String message, Authentication authentication) {
        if (customers == null || customers.isEmpty()) {
            logger.warn("No customers provided for bulk email");
            return 0;
        }
        
        if (!isGmailApiAvailable(authentication)) {
            logger.warn("Gmail API not available for bulk email");
            return 0;
        }
        
        int successCount = 0;
        
        for (Customer customer : customers) {
            try {
                if (sendEmailToCustomer(customer, subject, message, authentication)) {
                    successCount++;
                }
                
                // Add small delay to avoid rate limiting
                Thread.sleep(100);
                
            } catch (Exception e) {
                logger.error("Error in bulk email to customer: {}", customer.getEmail(), e);
            }
        }
        
        logger.info("Bulk email completed. Sent {} out of {} emails", successCount, customers.size());
        return successCount;
    }
    
    @Override
    public boolean sendWelcomeEmail(Customer customer, String temporaryPassword, Authentication authentication) {
        String subject = "Welcome to " + getApplicationName() + " - Your Account Details";
        String message = buildWelcomeEmailContent(customer, temporaryPassword);
        return sendEmailToCustomer(customer, subject, message, authentication);
    }
    
    @Override
    public boolean sendPasswordResetEmail(Customer customer, String resetToken, Authentication authentication) {
        String subject = "Password Reset Request - " + getApplicationName();
        String message = buildPasswordResetEmailContent(customer, resetToken);
        return sendEmailToCustomer(customer, subject, message, authentication);
    }
    
    @Override
    public boolean sendNotificationEmail(Customer customer, String subject, String message, Authentication authentication) {
        String formattedMessage = buildNotificationEmailContent(customer, message);
        return sendEmailToCustomer(customer, subject, formattedMessage, authentication);
    }
    
    @Override
    public boolean isGmailApiAvailable(Authentication authentication) {
        try {
            if (authentication instanceof UsernamePasswordAuthenticationToken) {
                // Regular user login - Gmail API not available
                return false;
            }
            
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            if (oAuthUser == null) {
                return false;
            }
            
            return oAuthUser.getGrantedScopes().contains(GoogleAccessService.SCOPE_GMAIL);
            
        } catch (Exception e) {
            logger.error("Error checking Gmail API availability", e);
            return false;
        }
    }
    
    @Override
    public boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    // ===== ✅ MISSING METHODS FROM INTERFACE (FIXED) =====

    @Override
    public boolean sendEmail(String toEmail, String subject, String message) {
        if (!isValidEmail(toEmail)) {
            logger.warn("Invalid email address: {}", toEmail);
            return false;
        }
        
        // For system emails, try to get current authentication or create a system one
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth == null || !isGmailApiAvailable(auth)) {
            logger.warn("No Gmail API available for system email to: {}", toEmail);
            // Could implement alternative email sending here (SMTP, etc.)
            return false;
        }
        
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(auth);
            if (oAuthUser == null) {
                logger.error("Could not get OAuth user for system email");
                return false;
            }
            
            googleGmailApiService.sendEmail(oAuthUser, toEmail, subject, message);
            logger.info("System email sent successfully to: {}", toEmail);
            return true;
            
        } catch (Exception e) {
            logger.error("Error sending system email to: {}", toEmail, e);
            return false;
        }
    }

    @Override
    public boolean sendSystemNotification(String toEmail, String subject, String message) {
        // Format message as system notification
        String formattedMessage = String.format(
            "System Notification\n" +
            "Generated: %s\n\n" +
            "%s\n\n" +
            "---\n" +
            "This is an automated message from %s",
            java.time.LocalDateTime.now(),
            message,
            getApplicationName()
        );
        
        return sendEmail(toEmail, "[SYSTEM] " + subject, formattedMessage);
    }

    // ===== ✅ EXISTING UNIFIED CUSTOMER MODEL METHODS =====

    @Override
    public void sendBulkEmailToPropertyOwners(String subject, String message, List<Long> ownerIds) {
        if (ownerIds == null || ownerIds.isEmpty()) {
            logger.warn("No property owner IDs provided for bulk email");
            return;
        }
        
        // ✅ FIXED: Use unified customer model
        List<Customer> propertyOwners = ownerIds.stream()
            .map(id -> customerService.findByCustomerId(id.intValue()))
            .filter(customer -> customer != null && Boolean.TRUE.equals(customer.getIsPropertyOwner()))
            .filter(customer -> isValidEmail(customer.getEmail()))
            .collect(Collectors.toList());
        
        if (!propertyOwners.isEmpty()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            int sentCount = sendBulkEmail(propertyOwners, subject, message, auth);
            logger.info("Sent bulk email to {} property owners out of {} requested", sentCount, ownerIds.size());
        }
    }

    @Override
    public void sendBulkEmailToTenants(String subject, String message, List<Long> tenantIds) {
        if (tenantIds == null || tenantIds.isEmpty()) {
            logger.warn("No tenant IDs provided for bulk email");
            return;
        }
        
        // ✅ FIXED: Use unified customer model
        List<Customer> tenants = tenantIds.stream()
            .map(id -> customerService.findByCustomerId(id.intValue()))
            .filter(customer -> customer != null && Boolean.TRUE.equals(customer.getIsTenant()))
            .filter(customer -> isValidEmail(customer.getEmail()))
            .collect(Collectors.toList());
        
        if (!tenants.isEmpty()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            int sentCount = sendBulkEmail(tenants, subject, message, auth);
            logger.info("Sent bulk email to {} tenants out of {} requested", sentCount, tenantIds.size());
        }
    }

    // ===== ✅ NEW: MAINTENANCE-SPECIFIC EMAIL METHODS (Required by TicketController) =====

    @Override
    public boolean sendMaintenanceTicketAlert(Customer propertyOwner, Ticket ticket, Authentication authentication) {
        if (propertyOwner == null || ticket == null) {
            logger.warn("Invalid property owner or ticket for maintenance alert");
            return false;
        }

        String subject = String.format("Maintenance Request - %s", ticket.getSubject());
        String message = buildMaintenanceTicketAlertMessage(propertyOwner, ticket);
        
        logger.info("Sending maintenance ticket alert for ticket #{} to property owner: {}", 
                   ticket.getTicketId(), propertyOwner.getEmail());
        
        return sendEmailToCustomer(propertyOwner, subject, message, authentication);
    }

    @Override
    public boolean sendContractorBidInvitation(Customer contractor, Ticket ticket, Authentication authentication) {
        if (contractor == null || ticket == null) {
            logger.warn("Invalid contractor or ticket for bid invitation");
            return false;
        }

        if (!Boolean.TRUE.equals(contractor.getIsContractor())) {
            logger.warn("Customer {} is not marked as a contractor", contractor.getEmail());
            return false;
        }

        String subject = String.format("Bid Invitation - %s", ticket.getSubject());
        String message = buildBidInvitationMessage(contractor, ticket);
        
        logger.info("Sending bid invitation for ticket #{} to contractor: {}", 
                   ticket.getTicketId(), contractor.getEmail());
        
        return sendEmailToCustomer(contractor, subject, message, authentication);
    }

    @Override
    public boolean sendPropertyMaintenanceAlert(Long propertyId, Ticket ticket, Authentication authentication) {
        try {
            // Find all property owners for this property
            List<Customer> propertyOwners = customerService.findByEntityTypeAndEntityId("Property", propertyId)
                .stream()
                .filter(customer -> Boolean.TRUE.equals(customer.getIsPropertyOwner()))
                .collect(Collectors.toList());
            
            for (Customer owner : propertyOwners) {
                String subject = String.format("Maintenance Alert - Property %d", propertyId);
                String message = String.format(
                    "A new maintenance ticket has been created for your property.\n\n" +
                    "Ticket #: %d\n" +
                    "Subject: %s\n" +
                    "Priority: %s\n" +
                    "Category: %s\n\n" +
                    "We will keep you updated on the progress.",
                    ticket.getTicketId(),
                    ticket.getSubject(),
                    ticket.getPriorityDisplayName(),
                    ticket.getMaintenanceCategory()
                );
                
                sendEmailToCustomer(owner, subject, message, authentication);
            }
            
            return true;
        } catch (Exception e) {
            logger.error("Error sending property maintenance alert: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean sendJobAwardNotification(Customer contractor, Ticket ticket, Authentication authentication) {
        if (contractor == null || ticket == null) {
            logger.warn("Invalid contractor or ticket for job award notification");
            return false;
        }

        String subject = String.format("Job Awarded - %s", ticket.getSubject());
        String message = buildJobAwardMessage(contractor, ticket);
        
        logger.info("Sending job award notification for ticket #{} to contractor: {}", 
                   ticket.getTicketId(), contractor.getEmail());
        
        return sendEmailToCustomer(contractor, subject, message, authentication);
    }

    @Override
    public boolean sendWorkCompletionNotification(Customer customer, Ticket ticket, Authentication authentication) {
        if (customer == null || ticket == null) {
            logger.warn("Invalid customer or ticket for work completion notification");
            return false;
        }

        String subject = String.format("Work Completed - %s", ticket.getSubject());
        String message = buildWorkCompletionMessage(customer, ticket);
        
        logger.info("Sending work completion notification for ticket #{} to customer: {}", 
                   ticket.getTicketId(), customer.getEmail());
        
        return sendEmailToCustomer(customer, subject, message, authentication);
    }

    @Override
    public boolean sendPaymentNotification(Customer customer, Ticket ticket, String paymentAmount, Authentication authentication) {
        if (customer == null || ticket == null) {
            logger.warn("Invalid customer or ticket for payment notification");
            return false;
        }

        String subject = String.format("Payment Processed - %s", ticket.getSubject());
        String message = buildPaymentNotificationMessage(customer, ticket, paymentAmount);
        
        logger.info("Sending payment notification for ticket #{} to customer: {}", 
                   ticket.getTicketId(), customer.getEmail());
        
        return sendEmailToCustomer(customer, subject, message, authentication);
    }

    // ===== ✅ EXISTING PROPERTY-BASED EMAIL METHODS =====
    
    /**
     * Send email to all tenants of a specific property
     */
    public int sendEmailToPropertyTenants(Long propertyId, String subject, String message, Authentication authentication) {
        List<CustomerPropertyAssignment> assignments = customerPropertyAssignmentRepository
            .findByPropertyId(propertyId);
        
        List<Customer> tenants = assignments.stream()
            .filter(assignment -> assignment.getAssignmentType() == AssignmentType.TENANT)
            .map(CustomerPropertyAssignment::getCustomer)
            .filter(customer -> isValidEmail(customer.getEmail()))
            .collect(Collectors.toList());
        
        logger.info("Sending email to {} tenants for property {}", tenants.size(), propertyId);
        return sendBulkEmail(tenants, subject, message, authentication);
    }
    
    /**
     * Send email to all property owners
     */
    public int sendEmailToAllPropertyOwners(String subject, String message, Authentication authentication) {
        List<Customer> propertyOwners = customerService.findPropertyOwners();
        
        List<Customer> validOwners = propertyOwners.stream()
            .filter(customer -> isValidEmail(customer.getEmail()))
            .collect(Collectors.toList());
        
        logger.info("Sending email to {} property owners", validOwners.size());
        return sendBulkEmail(validOwners, subject, message, authentication);
    }
    
    /**
     * Send email to all tenants
     */
    public int sendEmailToAllTenants(String subject, String message, Authentication authentication) {
        List<Customer> tenants = customerService.findTenants();
        
        List<Customer> validTenants = tenants.stream()
            .filter(customer -> isValidEmail(customer.getEmail()))
            .collect(Collectors.toList());
        
        logger.info("Sending email to {} tenants", validTenants.size());
        return sendBulkEmail(validTenants, subject, message, authentication);
    }
    
    /**
     * Send email to tenants with active tenancies only
     */
    public int sendEmailToActiveTenantsOnly(String subject, String message, Authentication authentication) {
        // Get all tenant assignments (active tenancies)
        List<CustomerPropertyAssignment> activeAssignments = customerPropertyAssignmentRepository
            .findByAssignmentType(AssignmentType.TENANT);
        
        List<Customer> activeTenants = activeAssignments.stream()
            .map(CustomerPropertyAssignment::getCustomer)
            .distinct() // Remove duplicates
            .filter(customer -> isValidEmail(customer.getEmail()))
            .collect(Collectors.toList());
        
        logger.info("Sending email to {} active tenants", activeTenants.size());
        return sendBulkEmail(activeTenants, subject, message, authentication);
    }

    // ===== ✅ EXISTING HELPER METHODS =====
    
    private String personalizeEmailContent(String content, Customer customer) {
        if (content == null || customer == null) {
            return content;
        }
        
        return content
            .replace("{{customer_name}}", customer.getName() != null ? customer.getName() : "")
            .replace("{{customer_email}}", customer.getEmail() != null ? customer.getEmail() : "")
            .replace("{{customer_phone}}", customer.getPhone() != null ? customer.getPhone() : "")
            .replace("{{customer_address}}", customer.getAddress() != null ? customer.getAddress() : "");
    }
    
    private String buildWelcomeEmailContent(Customer customer, String temporaryPassword) {
        return String.format(
            "Dear %s,\n\n" +
            "Welcome to %s! Your account has been created successfully.\n\n" +
            "Your login credentials:\n" +
            "Email: %s\n" +
            "Temporary Password: %s\n\n" +
            "Please log in and change your password as soon as possible.\n\n" +
            "Best regards,\n" +
            "The %s Team",
            customer.getName() != null ? customer.getName() : "Customer",
            getApplicationName(),
            customer.getEmail(),
            temporaryPassword,
            getApplicationName()
        );
    }
    
    private String buildPasswordResetEmailContent(Customer customer, String resetToken) {
        String resetUrl = getApplicationBaseUrl() + "/reset-password?token=" + resetToken;
        
        return String.format(
            "Dear %s,\n\n" +
            "You have requested a password reset for your account.\n\n" +
            "Please click the following link to reset your password:\n" +
            "%s\n\n" +
            "This link will expire in 24 hours.\n\n" +
            "If you did not request this password reset, please ignore this email.\n\n" +
            "Best regards,\n" +
            "The %s Team",
            customer.getName() != null ? customer.getName() : "Customer",
            resetUrl,
            getApplicationName()
        );
    }
    
    private String buildNotificationEmailContent(Customer customer, String message) {
        return String.format(
            "Dear %s,\n\n" +
            "%s\n\n" +
            "Best regards,\n" +
            "The %s Team",
            customer.getName() != null ? customer.getName() : "Customer",
            message,
            getApplicationName()
        );
    }

    // ===== ✅ NEW: MAINTENANCE EMAIL CONTENT BUILDERS =====

    private String buildMaintenanceTicketAlertMessage(Customer propertyOwner, Ticket ticket) {
        return String.format(
            "Dear %s,\n\n" +
            "A maintenance request has been raised for your property.\n\n" +
            "Ticket Details:\n" +
            "- Ticket #: %d\n" +
            "- Subject: %s\n" +
            "- Description: %s\n" +
            "- Priority: %s\n" +
            "- Category: %s\n" +
            "- Reported: %s\n\n" +
            "We will keep you updated on the progress and notify you when contractors are selected.\n\n" +
            "You can view the full details in your portal.\n\n" +
            "Best regards,\n" +
            "The %s Property Management Team",
            propertyOwner.getName() != null ? propertyOwner.getName() : "Property Owner",
            ticket.getTicketId(),
            ticket.getSubject() != null ? ticket.getSubject() : "Maintenance Request",
            ticket.getDescription() != null ? ticket.getDescription() : "No description provided",
            ticket.getPriorityDisplayName() != null ? ticket.getPriorityDisplayName() : "Medium",
            ticket.getMaintenanceCategory() != null ? ticket.getMaintenanceCategory() : "General",
            ticket.getCreatedAt() != null ? ticket.getCreatedAt().toString() : "Recently",
            getApplicationName()
        );
    }

    private String buildBidInvitationMessage(Customer contractor, Ticket ticket) {
        return String.format(
            "Dear %s,\n\n" +
            "You are invited to submit a bid for the following maintenance work:\n\n" +
            "Job Details:\n" +
            "- Ticket #: %d\n" +
            "- Subject: %s\n" +
            "- Description: %s\n" +
            "- Category: %s\n" +
            "- Priority: %s\n\n" +
            "Requirements:\n" +
            "- Access Required: %s\n" +
            "- Tenant Present: %s\n" +
            "- Preferred Time: %s\n\n" +
            "Please submit your bid including:\n" +
            "- Total cost estimate\n" +
            "- Estimated completion time\n" +
            "- Materials breakdown\n" +
            "- Availability to start\n\n" +
            "Deadline for submissions: 48 hours from this email\n\n" +
            "Please contact us to submit your bid or for any questions.\n\n" +
            "Best regards,\n" +
            "The %s Property Management Team",
            contractor.getName() != null ? contractor.getName() : "Contractor",
            ticket.getTicketId(),
            ticket.getSubject() != null ? ticket.getSubject() : "Maintenance Work",
            ticket.getDescription() != null ? ticket.getDescription() : "No description provided",
            ticket.getMaintenanceCategory() != null ? ticket.getMaintenanceCategory() : "General",
            ticket.getPriorityDisplayName() != null ? ticket.getPriorityDisplayName() : "Medium",
            Boolean.TRUE.equals(ticket.getAccessRequired()) ? "Yes" : "No",
            Boolean.TRUE.equals(ticket.getTenantPresentRequired()) ? "Required" : "Not Required",
            ticket.getPreferredTimeSlot() != null ? ticket.getPreferredTimeSlot() : "Flexible",
            getApplicationName()
        );
    }

    private String buildJobAwardMessage(Customer contractor, Ticket ticket) {
        return String.format(
            "Dear %s,\n\n" +
            "Congratulations! Your bid has been accepted for the following job:\n\n" +
            "Job Details:\n" +
            "- Ticket #: %d\n" +
            "- Subject: %s\n" +
            "- Description: %s\n" +
            "- Approved Amount: £%.2f\n" +
            "- Expected Start: ASAP\n\n" +
            "Next Steps:\n" +
            "1. Contact the property management team to confirm start date\n" +
            "2. Arrange access with the property contact\n" +
            "3. Complete the work as specified in your bid\n" +
            "4. Submit completion photos and any invoices\n" +
            "5. Payment will be processed upon completion and approval\n\n" +
            "Important: Please confirm receipt of this email and your intended start date within 24 hours.\n\n" +
            "Contact us for access arrangements and any questions.\n\n" +
            "Best regards,\n" +
            "The %s Property Management Team",
            contractor.getName() != null ? contractor.getName() : "Contractor",
            ticket.getTicketId(),
            ticket.getSubject() != null ? ticket.getSubject() : "Maintenance Work",
            ticket.getDescription() != null ? ticket.getDescription() : "No description provided",
            ticket.getApprovedAmount() != null ? ticket.getApprovedAmount() : java.math.BigDecimal.ZERO,
            getApplicationName()
        );
    }

    private String buildWorkCompletionMessage(Customer customer, Ticket ticket) {
        String contractorName = getContractorNameForTicket(ticket);
        
        return String.format(
            "Dear %s,\n\n" +
            "The maintenance work for ticket #%d has been completed.\n\n" +
            "Work Details:\n" +
            "- Subject: %s\n" +
            "- Description: %s\n" +
            "- Completed by: %s\n" +
            "- Completion Date: %s\n" +
            "- Final Cost: £%.2f\n\n" +
            "%s\n\n" +
            "Quality Assurance:\n" +
            "- All work has been completed to our standards\n" +
            "- Documentation is available upon request\n" +
            "- Warranty period: %d months from completion date\n\n" +
            "If you have any concerns about the work completed, please contact us within 48 hours.\n\n" +
            "Best regards,\n" +
            "The %s Property Management Team",
            customer.getName() != null ? customer.getName() : "Customer",
            ticket.getTicketId(),
            ticket.getSubject() != null ? ticket.getSubject() : "Maintenance Work",
            ticket.getDescription() != null ? ticket.getDescription() : "No description provided",
            contractorName,
            ticket.getWorkCompletedAt() != null ? ticket.getWorkCompletedAt().toString() : "Recently",
            ticket.getActualCost() != null ? ticket.getActualCost() : 
                (ticket.getApprovedAmount() != null ? ticket.getApprovedAmount() : java.math.BigDecimal.ZERO),
            Boolean.TRUE.equals(customer.getIsTenant()) ? 
                "Please inspect the work and let us know if everything is satisfactory." :
                "The invoice will be processed through your regular payment schedule.",
            ticket.getWarrantyPeriodMonths() != null ? ticket.getWarrantyPeriodMonths() : 12,
            getApplicationName()
        );
    }

    private String buildPaymentNotificationMessage(Customer customer, Ticket ticket, String paymentAmount) {
        return String.format(
            "Dear %s,\n\n" +
            "Payment has been processed for the completed maintenance work.\n\n" +
            "Payment Details:\n" +
            "- Ticket #: %d\n" +
            "- Work Description: %s\n" +
            "- Payment Amount: %s\n" +
            "- Payment Date: %s\n" +
            "- Reference: MAINT-%d\n\n" +
            "Work Summary:\n" +
            "- Contractor: %s\n" +
            "- Work Completed: %s\n" +
            "- Quality Rating: Satisfactory\n\n" +
            "This payment has been processed through your regular payment schedule.\n" +
            "A detailed invoice will be available in your portal within 24 hours.\n\n" +
            "Thank you for your prompt attention to this matter.\n\n" +
            "Best regards,\n" +
            "The %s Property Management Team",
            customer.getName() != null ? customer.getName() : "Customer",
            ticket.getTicketId(),
            ticket.getSubject() != null ? ticket.getSubject() : "Maintenance Work",
            paymentAmount,
            java.time.LocalDate.now(),
            ticket.getTicketId(),
            getContractorNameForTicket(ticket),
            ticket.getWorkCompletedAt() != null ? ticket.getWorkCompletedAt().toLocalDate().toString() : "Recently",
            getApplicationName()
        );
    }

    private String getContractorNameForTicket(Ticket ticket) {
        if (ticket.getSelectedContractorId() != null) {
            Customer contractor = customerService.findByCustomerId(ticket.getSelectedContractorId());
            if (contractor != null) {
                return contractor.getName();
            }
        }
        return "Contracted Service Provider";
    }
    
    private String getApplicationName() {
        return environment.getProperty("app.name", "CRM Application");
    }
    
    private String getApplicationBaseUrl() {
        return environment.getProperty("app.base-url", "http://localhost:8080");
    }
}
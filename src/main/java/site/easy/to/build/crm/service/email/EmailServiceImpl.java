package site.easy.to.build.crm.service.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerPropertyAssignment;
import site.easy.to.build.crm.entity.CustomerType;
import site.easy.to.build.crm.entity.EmailTemplate;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.entity.AssignmentType;
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

    // ===== ✅ FIXED: NEW UNIFIED CUSTOMER MODEL METHODS =====

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
    
    // ===== ✅ NEW: PROPERTY-BASED EMAIL METHODS =====
    
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

    // ===== HELPER METHODS =====
    
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
    
    private String getApplicationName() {
        return environment.getProperty("app.name", "CRM Application");
    }
    
    private String getApplicationBaseUrl() {
        return environment.getProperty("app.base-url", "http://localhost:8080");
    }
}
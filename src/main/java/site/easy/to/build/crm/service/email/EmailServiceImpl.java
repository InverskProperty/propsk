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
import site.easy.to.build.crm.entity.CustomerType;
import site.easy.to.build.crm.entity.EmailTemplate;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.entity.Tenant;
import site.easy.to.build.crm.entity.PropertyOwner;
import site.easy.to.build.crm.google.service.acess.GoogleAccessService;
import site.easy.to.build.crm.google.service.gmail.GoogleGmailApiService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.service.property.TenantService;
import site.easy.to.build.crm.service.property.PropertyOwnerService;
import site.easy.to.build.crm.repository.TenantRepository;
import site.easy.to.build.crm.repository.PropertyOwnerRepository;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * CORRECTED EmailServiceImpl.java - Implementation of EmailService
 */
@Service
public class EmailServiceImpl implements EmailService {
    
    private static final Logger logger = LoggerFactory.getLogger(EmailServiceImpl.class);
    
    private final GoogleGmailApiService googleGmailApiService;
    private final AuthenticationUtils authenticationUtils;
    private final UserService userService;
    private final CustomerService customerService;
    private final Environment environment;
    
    @Autowired
    private TenantService tenantService;

    @Autowired
    private PropertyOwnerService propertyOwnerService;
    
    // Add repositories for Spring Data's findAllById method
    @Autowired
    private TenantRepository tenantRepository;
    
    @Autowired
    private PropertyOwnerRepository propertyOwnerRepository;
    
    // Email validation pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    
    @Autowired
    public EmailServiceImpl(GoogleGmailApiService googleGmailApiService,
                           AuthenticationUtils authenticationUtils,
                           UserService userService,
                           CustomerService customerService,
                           Environment environment) {
        this.googleGmailApiService = googleGmailApiService;
        this.authenticationUtils = authenticationUtils;
        this.userService = userService;
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
            // ✅ FIXED: Get OAuthUser and use correct method signature
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            if (oAuthUser == null) {
                logger.error("Could not get OAuth user from authentication");
                return false;
            }
            
            // ✅ FIXED: Use correct method signature (void return, OAuthUser first param)
            googleGmailApiService.sendEmail(oAuthUser, customer.getEmail(), subject, message);
            
            logger.info("Email sent successfully to customer: {}", customer.getEmail());
            return true; // Success if no exception thrown
            
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
        String personalizedContent = personalizeEmailContent(template.getName(), customer);
        
        return sendEmailToCustomer(customer, template.getName(), personalizedContent, authentication);
    }
    
    @Override
    public int sendBulkEmail(CustomerType customerType, String subject, String message, Authentication authentication) {
        // Get customers by type - this will be enhanced once the database migration is complete
        // For now, get all customers since findByCustomerType might not be implemented yet
        List<Customer> customers = customerService.findAll();
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
        // Add standard header/footer for notifications
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
            
            // ✅ CORRECT: Get the OAuthUser correctly from AuthenticationUtils
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

    // ===== NEW METHODS FOR EMPLOYEE DASHBOARD (CORRECTED) =====

    @Override
    public void sendBulkEmailToPropertyOwners(String subject, String message, List<Long> ownerIds) {
        if (ownerIds == null || ownerIds.isEmpty()) {
            logger.warn("No property owner IDs provided for bulk email");
            return;
        }
        
        // ✅ FIXED: Use Spring Data's built-in findAllById method
        List<PropertyOwner> owners = propertyOwnerRepository.findAllById(ownerIds);
        List<Customer> customers = new ArrayList<>();
        
        // Convert PropertyOwners to Customers for your existing bulk email system
        for (PropertyOwner owner : owners) {
            if (owner.getEmailAddress() != null && !owner.getEmailAddress().isEmpty()) {
                Customer tempCustomer = createTempCustomerFromPropertyOwner(owner);
                customers.add(tempCustomer);
            }
        }
        
        if (!customers.isEmpty()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            int sentCount = sendBulkEmail(customers, subject, message, auth);
            logger.info("Sent bulk email to {} property owners out of {} requested", sentCount, ownerIds.size());
        }
    }

    @Override
    public void sendBulkEmailToTenants(String subject, String message, List<Long> tenantIds) {
        if (tenantIds == null || tenantIds.isEmpty()) {
            logger.warn("No tenant IDs provided for bulk email");
            return;
        }
        
        // ✅ FIXED: Use Spring Data's built-in findAllById method
        List<Tenant> tenants = tenantRepository.findAllById(tenantIds);
        List<Customer> customers = new ArrayList<>();
        
        // Convert Tenants to Customers for your existing bulk email system
        for (Tenant tenant : tenants) {
            if (tenant.getEmailAddress() != null && !tenant.getEmailAddress().isEmpty()) {
                Customer tempCustomer = createTempCustomerFromTenant(tenant);
                customers.add(tempCustomer);
            }
        }
        
        if (!customers.isEmpty()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            int sentCount = sendBulkEmail(customers, subject, message, auth);
            logger.info("Sent bulk email to {} tenants out of {} requested", sentCount, tenantIds.size());
        }
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

    private Customer createTempCustomerFromPropertyOwner(PropertyOwner owner) {
        Customer tempCustomer = new Customer();
        tempCustomer.setEmail(owner.getEmailAddress());
        
        if (owner.getAccountType() == site.easy.to.build.crm.entity.AccountType.BUSINESS) {
            tempCustomer.setName(owner.getBusinessName() != null ? owner.getBusinessName() : "Property Owner");
        } else {
            String fullName = "";
            if (owner.getFirstName() != null) fullName += owner.getFirstName();
            if (owner.getLastName() != null) fullName += " " + owner.getLastName();
            tempCustomer.setName(fullName.trim().isEmpty() ? "Property Owner" : fullName.trim());
        }
        
        tempCustomer.setCustomerType(CustomerType.PROPERTY_OWNER);
        tempCustomer.setPhone(owner.getPhone());
        
        // Build address from PropertyOwner fields
        StringBuilder address = new StringBuilder();
        if (owner.getAddressLine1() != null) address.append(owner.getAddressLine1());
        if (owner.getCity() != null) {
            if (address.length() > 0) address.append(", ");
            address.append(owner.getCity());
        }
        tempCustomer.setAddress(address.toString());
        
        return tempCustomer;
    }

    private Customer createTempCustomerFromTenant(Tenant tenant) {
        Customer tempCustomer = new Customer();
        tempCustomer.setEmail(tenant.getEmailAddress());
        
        if (tenant.getAccountType() == site.easy.to.build.crm.entity.AccountType.BUSINESS) {
            tempCustomer.setName(tenant.getBusinessName() != null ? tenant.getBusinessName() : "Tenant");
        } else {
            String fullName = "";
            if (tenant.getFirstName() != null) fullName += tenant.getFirstName();
            if (tenant.getLastName() != null) fullName += " " + tenant.getLastName();
            tempCustomer.setName(fullName.trim().isEmpty() ? "Tenant" : fullName.trim());
        }
        
        tempCustomer.setCustomerType(CustomerType.TENANT);
        tempCustomer.setPhone(tenant.getPhoneNumber());
        
        // Build address from Tenant fields
        StringBuilder address = new StringBuilder();
        if (tenant.getAddressLine1() != null) address.append(tenant.getAddressLine1());
        if (tenant.getCity() != null) {
            if (address.length() > 0) address.append(", ");
            address.append(tenant.getCity());
        }
        tempCustomer.setAddress(address.toString());
        
        return tempCustomer;
    }
    
    private String getApplicationName() {
        return environment.getProperty("app.name", "CRM Application");
    }
    
    private String getApplicationBaseUrl() {
        return environment.getProperty("app.base-url", "http://localhost:8080");
    }
}
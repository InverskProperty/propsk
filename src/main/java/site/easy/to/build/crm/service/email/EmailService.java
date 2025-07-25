package site.easy.to.build.crm.service.email;

import org.springframework.security.core.Authentication;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerType;
import site.easy.to.build.crm.entity.EmailTemplate;
import site.easy.to.build.crm.entity.Ticket;

import java.util.List;

/**
 * Email service for sending emails to customers, property owners, tenants, and contractors
 * Integrates with existing Google Gmail API and email template system
 * Enhanced with maintenance workflow support
 */
public interface EmailService {
    
    // ===== CORE EMAIL METHODS =====
    
    /**
     * Send email to a single customer using Gmail API
     * @param customer The customer to send email to
     * @param subject Email subject
     * @param message Email body/content
     * @param authentication Current user authentication
     * @return true if email sent successfully
     */
    boolean sendEmailToCustomer(Customer customer, String subject, String message, Authentication authentication);
    
    /**
     * Send email using a predefined template
     * @param customer The customer to send email to
     * @param template The email template to use
     * @param authentication Current user authentication
     * @return true if email sent successfully
     */
    boolean sendTemplatedEmail(Customer customer, EmailTemplate template, Authentication authentication);
    
    /**
     * Send bulk email to customers of a specific type
     * @param customerType Type of customers (PROPERTY_OWNER, TENANT, CONTRACTOR, etc.)
     * @param subject Email subject
     * @param message Email body/content
     * @param authentication Current user authentication
     * @return number of emails sent successfully
     */
    int sendBulkEmail(CustomerType customerType, String subject, String message, Authentication authentication);
    
    /**
     * Send bulk email to a list of customers
     * @param customers List of customers to email
     * @param subject Email subject
     * @param message Email body/content
     * @param authentication Current user authentication
     * @return number of emails sent successfully
     */
    int sendBulkEmail(List<Customer> customers, String subject, String message, Authentication authentication);
    
    /**
     * Send email to a single recipient (basic method for system emails)
     * @param toEmail Email address to send to
     * @param subject Email subject
     * @param message Email body/content
     * @return true if email sent successfully
     */
    boolean sendEmail(String toEmail, String subject, String message);
    
    /**
     * Send system notification (no authentication required)
     * @param toEmail Email address to send to
     * @param subject Email subject
     * @param message Email body/content
     * @return true if email sent successfully
     */
    boolean sendSystemNotification(String toEmail, String subject, String message);
    
    // ===== CUSTOMER ACCOUNT METHODS =====
    
    /**
     * Send welcome email with login credentials to a new customer
     * @param customer The new customer
     * @param temporaryPassword The temporary password for login
     * @param authentication Current user authentication
     * @return true if email sent successfully
     */
    boolean sendWelcomeEmail(Customer customer, String temporaryPassword, Authentication authentication);
    
    /**
     * Send password reset email to customer
     * @param customer The customer requesting password reset
     * @param resetToken The password reset token
     * @param authentication Current user authentication
     * @return true if email sent successfully
     */
    boolean sendPasswordResetEmail(Customer customer, String resetToken, Authentication authentication);
    
    /**
     * Send notification email about property/maintenance updates
     * @param customer The customer to notify
     * @param subject Email subject
     * @param message Notification message
     * @param authentication Current user authentication
     * @return true if email sent successfully
     */
    boolean sendNotificationEmail(Customer customer, String subject, String message, Authentication authentication);
    
    // ===== BULK EMAIL METHODS =====
    
    /**
     * Send bulk email to property owners by their IDs
     * @param subject Email subject
     * @param message Email body/content
     * @param ownerIds List of property owner IDs
     */
    void sendBulkEmailToPropertyOwners(String subject, String message, List<Long> ownerIds);
    
    /**
     * Send bulk email to tenants by their IDs
     * @param subject Email subject
     * @param message Email body/content
     * @param tenantIds List of tenant IDs
     */
    void sendBulkEmailToTenants(String subject, String message, List<Long> tenantIds);
    
    // ===== MAINTENANCE WORKFLOW METHODS =====
    
    /**
     * Send maintenance ticket creation alert to property owner
     * @param propertyOwner The property owner to notify
     * @param ticket The maintenance ticket
     * @param authentication Current user authentication
     * @return true if email sent successfully
     */
    boolean sendMaintenanceTicketAlert(Customer propertyOwner, Ticket ticket, Authentication authentication);
    
    /**
     * Send contractor bid invitation
     * @param contractor The contractor to invite
     * @param ticket The maintenance ticket
     * @param authentication Current user authentication
     * @return true if email sent successfully
     */
    boolean sendContractorBidInvitation(Customer contractor, Ticket ticket, Authentication authentication);

    /**
     * Send property maintenance alert to all property owners
     * @param propertyId The property ID
     * @param ticket The maintenance ticket
     * @param authentication Current user authentication
     * @return true if email sent successfully
     */
    boolean sendPropertyMaintenanceAlert(Long propertyId, Ticket ticket, Authentication authentication);

    /**
     * Send contractor job award notification
     * @param contractor The selected contractor
     * @param ticket The maintenance ticket
     * @param authentication Current user authentication
     * @return true if email sent successfully
     */
    boolean sendJobAwardNotification(Customer contractor, Ticket ticket, Authentication authentication);
    
    /**
     * Send work completion notification
     * @param customer The customer to notify (property owner or tenant)
     * @param ticket The completed maintenance ticket
     * @param authentication Current user authentication
     * @return true if email sent successfully
     */
    boolean sendWorkCompletionNotification(Customer customer, Ticket ticket, Authentication authentication);
    
    /**
     * Send payment processed notification
     * @param customer The customer to notify
     * @param ticket The maintenance ticket
     * @param paymentAmount The payment amount processed
     * @param authentication Current user authentication
     * @return true if email sent successfully
     */
    boolean sendPaymentNotification(Customer customer, Ticket ticket, String paymentAmount, Authentication authentication);
    
    // ===== UTILITY METHODS =====
    
    /**
     * Check if Gmail API access is available for the current user
     * @param authentication Current user authentication
     * @return true if Gmail API is accessible
     */
    boolean isGmailApiAvailable(Authentication authentication);
    
    /**
     * Validate email address format
     * @param email Email address to validate
     * @return true if email format is valid
     */
    boolean isValidEmail(String email);
}
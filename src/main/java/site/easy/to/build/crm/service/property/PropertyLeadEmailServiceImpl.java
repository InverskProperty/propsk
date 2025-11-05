package site.easy.to.build.crm.service.property;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.LeadCommunicationRepository;
import site.easy.to.build.crm.repository.LeadRepository;
import site.easy.to.build.crm.repository.PropertyRepository;
import site.easy.to.build.crm.service.email.EmailService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Implementation of PropertyLeadEmailService.
 * Handles automated email communications for property rental leads.
 */
@Service
public class PropertyLeadEmailServiceImpl implements PropertyLeadEmailService {

    private final EmailService emailService;
    private final LeadRepository leadRepository;
    private final PropertyRepository propertyRepository;
    private final LeadCommunicationRepository communicationRepository;

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a");
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");

    public PropertyLeadEmailServiceImpl(EmailService emailService,
                                        LeadRepository leadRepository,
                                        PropertyRepository propertyRepository,
                                        LeadCommunicationRepository communicationRepository) {
        this.emailService = emailService;
        this.leadRepository = leadRepository;
        this.propertyRepository = propertyRepository;
        this.communicationRepository = communicationRepository;
    }

    @Override
    @Transactional
    public boolean sendEnquiryWelcomeEmail(Lead lead) {
        String subject = "Thank you for your property enquiry";
        String message = buildEnquiryWelcomeMessage(lead);

        boolean success = sendEmailToLead(lead, subject, message);
        recordCommunication(lead, subject, message, success);
        return success;
    }

    @Override
    @Transactional
    public boolean sendViewingConfirmationEmail(PropertyViewing viewing) {
        Lead lead = viewing.getLead();
        String subject = "Viewing Confirmed: " + getPropertyAddress(viewing.getProperty());
        String message = buildViewingConfirmationMessage(viewing);

        boolean success = sendEmailToLead(lead, subject, message);
        recordCommunication(lead, subject, message, success);
        return success;
    }

    @Override
    @Transactional
    public boolean sendViewingReminderEmail(PropertyViewing viewing) {
        Lead lead = viewing.getLead();
        String subject = "Reminder: Property Viewing Tomorrow";
        String message = buildViewingReminderMessage(viewing);

        boolean success = sendEmailToLead(lead, subject, message);
        recordCommunication(lead, subject, message, success);
        return success;
    }

    @Override
    @Transactional
    public boolean sendViewingRescheduledEmail(PropertyViewing viewing) {
        Lead lead = viewing.getLead();
        String subject = "Viewing Rescheduled: " + getPropertyAddress(viewing.getProperty());
        String message = buildViewingRescheduledMessage(viewing);

        boolean success = sendEmailToLead(lead, subject, message);
        recordCommunication(lead, subject, message, success);
        return success;
    }

    @Override
    @Transactional
    public boolean sendViewingCancelledEmail(PropertyViewing viewing) {
        Lead lead = viewing.getLead();
        String subject = "Viewing Cancelled: " + getPropertyAddress(viewing.getProperty());
        String message = buildViewingCancelledMessage(viewing);

        boolean success = sendEmailToLead(lead, subject, message);
        recordCommunication(lead, subject, message, success);
        return success;
    }

    @Override
    @Transactional
    public boolean sendViewingFollowUpEmail(PropertyViewing viewing) {
        Lead lead = viewing.getLead();
        String subject = "How was your viewing at " + getPropertyAddress(viewing.getProperty()) + "?";
        String message = buildViewingFollowUpMessage(viewing);

        boolean success = sendEmailToLead(lead, subject, message);
        recordCommunication(lead, subject, message, success);
        return success;
    }

    @Override
    @Transactional
    public boolean sendApplicationInstructionsEmail(Lead lead, Property property) {
        String subject = "Next Steps: Application for " + getPropertyAddress(property);
        String message = buildApplicationInstructionsMessage(lead, property);

        boolean success = sendEmailToLead(lead, subject, message);
        recordCommunication(lead, subject, message, success);
        return success;
    }

    @Override
    @Transactional
    public boolean sendReferencingStartedEmail(Lead lead) {
        String subject = "Referencing Process Started";
        String message = buildReferencingStartedMessage(lead);

        boolean success = sendEmailToLead(lead, subject, message);
        recordCommunication(lead, subject, message, success);
        return success;
    }

    @Override
    @Transactional
    public boolean sendReferencingCompletedEmail(Lead lead, boolean approved) {
        String subject = approved ? "References Approved" : "Referencing Update";
        String message = buildReferencingCompletedMessage(lead, approved);

        boolean success = sendEmailToLead(lead, subject, message);
        recordCommunication(lead, subject, message, success);
        return success;
    }

    @Override
    @Transactional
    public boolean sendContractSigningEmail(Lead lead, Property property) {
        String subject = "Ready to Sign: Tenancy Agreement for " + getPropertyAddress(property);
        String message = buildContractSigningMessage(lead, property);

        boolean success = sendEmailToLead(lead, subject, message);
        recordCommunication(lead, subject, message, success);
        return success;
    }

    @Override
    @Transactional
    public boolean sendTenancyConfirmationEmail(Lead lead, Property property) {
        String subject = "Welcome to Your New Home!";
        String message = buildTenancyConfirmationMessage(lead, property);

        boolean success = sendEmailToLead(lead, subject, message);
        recordCommunication(lead, subject, message, success);
        return success;
    }

    @Override
    @Transactional
    public int notifyMatchingLeadsOfProperty(Property property) {
        // Find leads looking for properties in this price range and availability
        List<Lead> matchingLeads = leadRepository.findActivePropertyLeads();

        // Note: Property entity doesn't have getRentalPrice() method
        // Would need proper price field to match budget
        int sentCount = 0;

        return sentCount;
    }

    @Override
    public boolean sendVacancyNotificationToOwner(Property property) {
        // Note: Property entity doesn't have getOwner() method
        // Would need to query customer assignments separately
        return false; // Placeholder - needs proper implementation
    }

    @Override
    public boolean sendAdvertisingStartedNotificationToOwner(Property property) {
        // Note: Property entity doesn't have getOwner() method
        // Would need to query customer assignments separately
        return false; // Placeholder - needs proper implementation
    }

    @Override
    public boolean sendEnquiryNotificationToOwner(Lead lead) {
        // Note: Property entity doesn't have getOwner() method
        // Would need to query customer assignments separately
        return false; // Placeholder - needs proper implementation
    }

    @Override
    public boolean sendViewingScheduledNotificationToOwner(PropertyViewing viewing) {
        // Note: Property entity doesn't have getOwner() method
        // Would need to query customer assignments separately
        return false; // Placeholder - needs proper implementation
    }

    @Override
    public boolean sendEmailToLead(Lead lead, String subject, String message) {
        // Note: Lead entity doesn't have getEmail() method
        // Would need email to be added to Lead entity or retrieved from Customer
        return false; // Placeholder - needs proper implementation
    }

    @Override
    @Transactional
    public void recordCommunication(Lead lead, String subject, String message, boolean success) {
        LeadCommunication communication = new LeadCommunication(lead, "EMAIL", "OUTBOUND");
        communication.setSubject(subject);
        communication.setMessageContent(message);
        communication.setSentAt(LocalDateTime.now());
        communication.setStatus(success ? "SENT" : "FAILED");

        if (success) {
            communication.setDeliveredAt(LocalDateTime.now());
        }

        communicationRepository.save(communication);
    }

    // ===== MESSAGE BUILDERS =====

    private String buildEnquiryWelcomeMessage(Lead lead) {
        return String.format("""
            Dear %s,

            Thank you for your property enquiry. We've received your request and one of our team members will be in touch with you shortly.

            In the meantime, if you have any questions or would like to schedule a viewing, please don't hesitate to contact us.

            Best regards,
            Property Management Team
            """, getLeadName(lead));
    }

    private String buildViewingConfirmationMessage(PropertyViewing viewing) {
        return String.format("""
            Dear %s,

            Your property viewing has been confirmed!

            Property: %s
            Date & Time: %s
            Duration: %d minutes
            Type: %s

            We look forward to showing you the property. Please arrive on time and bring a valid form of ID.

            If you need to reschedule or have any questions, please contact us as soon as possible.

            Best regards,
            Property Management Team
            """,
            getLeadName(viewing.getLead()),
            getPropertyAddress(viewing.getProperty()),
            viewing.getScheduledDatetime().format(DATE_TIME_FORMATTER),
            viewing.getDurationMinutes(),
            viewing.getViewingType()
        );
    }

    private String buildViewingReminderMessage(PropertyViewing viewing) {
        return String.format("""
            Dear %s,

            This is a reminder about your property viewing tomorrow:

            Property: %s
            Date & Time: %s

            Please remember to bring a valid form of ID.

            We look forward to seeing you!

            Best regards,
            Property Management Team
            """,
            getLeadName(viewing.getLead()),
            getPropertyAddress(viewing.getProperty()),
            viewing.getScheduledDatetime().format(DATE_TIME_FORMATTER)
        );
    }

    private String buildViewingRescheduledMessage(PropertyViewing viewing) {
        return String.format("""
            Dear %s,

            Your property viewing has been rescheduled:

            Property: %s
            New Date & Time: %s

            We apologize for any inconvenience and look forward to seeing you at the new time.

            Best regards,
            Property Management Team
            """,
            getLeadName(viewing.getLead()),
            getPropertyAddress(viewing.getProperty()),
            viewing.getScheduledDatetime().format(DATE_TIME_FORMATTER)
        );
    }

    private String buildViewingCancelledMessage(PropertyViewing viewing) {
        return String.format("""
            Dear %s,

            Your property viewing has been cancelled:

            Property: %s
            Original Date & Time: %s

            If you would like to reschedule or view other properties, please contact us.

            Best regards,
            Property Management Team
            """,
            getLeadName(viewing.getLead()),
            getPropertyAddress(viewing.getProperty()),
            viewing.getScheduledDatetime().format(DATE_TIME_FORMATTER)
        );
    }

    private String buildViewingFollowUpMessage(PropertyViewing viewing) {
        return String.format("""
            Dear %s,

            Thank you for viewing the property at %s.

            We hope you enjoyed the viewing! If you're interested in proceeding with an application, please let us know and we'll send you the necessary information.

            If you have any questions about the property or would like to schedule another viewing, please don't hesitate to contact us.

            Best regards,
            Property Management Team
            """,
            getLeadName(viewing.getLead()),
            getPropertyAddress(viewing.getProperty())
        );
    }

    private String buildApplicationInstructionsMessage(Lead lead, Property property) {
        return String.format("""
            Dear %s,

            Thank you for your interest in %s.

            To proceed with your application, please provide the following:

            1. Completed application form
            2. Proof of identity (passport or driver's license)
            3. Proof of income (last 3 months payslips or bank statements)
            4. Previous landlord reference (if applicable)
            5. Employer reference

            We'll review your application and contact you with next steps.

            Best regards,
            Property Management Team
            """,
            getLeadName(lead),
            getPropertyAddress(property)
        );
    }

    private String buildReferencingStartedMessage(Lead lead) {
        return String.format("""
            Dear %s,

            Your referencing process has begun. We're currently conducting checks with:

            - Credit reference agencies
            - Previous landlords
            - Employers

            This typically takes 3-5 business days. We'll keep you updated on progress.

            Best regards,
            Property Management Team
            """,
            getLeadName(lead)
        );
    }

    private String buildReferencingCompletedMessage(Lead lead, boolean approved) {
        if (approved) {
            return String.format("""
                Dear %s,

                Great news! Your references have been approved.

                We're now preparing your tenancy agreement and will send it to you shortly for review and signing.

                Best regards,
                Property Management Team
                """,
                getLeadName(lead)
            );
        } else {
            return String.format("""
                Dear %s,

                Thank you for your application. Unfortunately, we're unable to proceed at this time based on the referencing results.

                If you have any questions, please contact us to discuss.

                Best regards,
                Property Management Team
                """,
                getLeadName(lead)
            );
        }
    }

    private String buildContractSigningMessage(Lead lead, Property property) {
        return String.format("""
            Dear %s,

            Your tenancy agreement is ready for signing!

            Property: %s

            Please review the attached agreement carefully. If you have any questions, please contact us before signing.

            Once signed, we'll arrange key collection and move-in details.

            Best regards,
            Property Management Team
            """,
            getLeadName(lead),
            getPropertyAddress(property)
        );
    }

    private String buildTenancyConfirmationMessage(Lead lead, Property property) {
        return String.format("""
            Dear %s,

            Welcome to your new home at %s!

            Your tenancy is now active. We've sent you a separate email with:

            - Key collection details
            - Move-in checklist
            - Emergency contact information
            - Tenant portal access credentials

            If you have any questions or need assistance, please don't hesitate to contact us.

            We wish you a wonderful tenancy!

            Best regards,
            Property Management Team
            """,
            getLeadName(lead),
            getPropertyAddress(property)
        );
    }

    private String buildPropertyAvailableMessage(Lead lead, Property property) {
        return String.format("""
            Dear %s,

            A new property matching your requirements is now available:

            Address: %s

            This property matches your search criteria. Would you like to schedule a viewing?

            Please contact us if you're interested!

            Best regards,
            Property Management Team
            """,
            getLeadName(lead),
            getPropertyAddress(property)
        );
    }

    private String buildVacancyNotificationMessage(Property property) {
        return String.format("""
            Dear Property Owner,

            Notice period has been received for your property at %s.

            Expected Vacancy Date: %s

            We'll begin preparing the property for marketing and keep you updated on progress.

            Best regards,
            Property Management Team
            """,
            getPropertyAddress(property),
            property.getExpectedVacancyDate() != null ?
                property.getExpectedVacancyDate().format(DATE_FORMATTER) : "TBC"
        );
    }

    private String buildAdvertisingStartedMessage(Property property) {
        return String.format("""
            Dear Property Owner,

            Your property at %s is now being advertised for new tenants.

            We've listed it on our website and will notify you of any enquiries and viewing requests.

            Best regards,
            Property Management Team
            """,
            getPropertyAddress(property)
        );
    }

    private String buildEnquiryNotificationToOwnerMessage(Lead lead) {
        return String.format("""
            Dear Property Owner,

            New enquiry received for your property at %s:

            Enquirer: %s
            Email: %s
            Phone: %s
            Move-in Date: %s

            We'll keep you updated on progress.

            Best regards,
            Property Management Team
            """,
            getPropertyAddress(lead.getProperty()),
            getLeadName(lead),
            "Email not available", // Lead entity doesn't have getEmail()
            lead.getPhone() != null ? lead.getPhone() : "Not provided",
            lead.getDesiredMoveInDate() != null ?
                lead.getDesiredMoveInDate().format(DATE_FORMATTER) : "Flexible"
        );
    }

    private String buildViewingScheduledToOwnerMessage(PropertyViewing viewing) {
        return String.format("""
            Dear Property Owner,

            A viewing has been scheduled for your property at %s:

            Date & Time: %s
            Viewer: %s

            We'll provide feedback after the viewing.

            Best regards,
            Property Management Team
            """,
            getPropertyAddress(viewing.getProperty()),
            viewing.getScheduledDatetime().format(DATE_TIME_FORMATTER),
            getLeadName(viewing.getLead())
        );
    }

    // ===== UTILITY METHODS =====

    private String getLeadName(Lead lead) {
        if (lead.getName() != null && !lead.getName().trim().isEmpty()) {
            return lead.getName();
        }
        return "Valued Customer";
    }

    private String getPropertyAddress(Property property) {
        // Property entity doesn't have simple getAddress() method
        if (property.getAddressLine1() != null) {
            return property.getAddressLine1() +
                   (property.getCity() != null ? ", " + property.getCity() : "");
        }
        return "the property";
    }
}

package site.easy.to.build.crm.service.property;

import site.easy.to.build.crm.entity.Lead;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.PropertyViewing;

/**
 * Service interface for property lead email communications.
 * Handles automated and manual emails for the property rental workflow.
 */
public interface PropertyLeadEmailService {

    /**
     * Send welcome email to new property enquiry lead
     *
     * @param lead Lead who made the enquiry
     * @return true if email sent successfully
     */
    boolean sendEnquiryWelcomeEmail(Lead lead);

    /**
     * Send viewing confirmation email
     *
     * @param viewing Viewing appointment details
     * @return true if email sent successfully
     */
    boolean sendViewingConfirmationEmail(PropertyViewing viewing);

    /**
     * Send viewing reminder email (24 hours before)
     *
     * @param viewing Viewing appointment details
     * @return true if email sent successfully
     */
    boolean sendViewingReminderEmail(PropertyViewing viewing);

    /**
     * Send viewing reschedule notification
     *
     * @param viewing Updated viewing appointment
     * @return true if email sent successfully
     */
    boolean sendViewingRescheduledEmail(PropertyViewing viewing);

    /**
     * Send viewing cancellation email
     *
     * @param viewing Cancelled viewing appointment
     * @return true if email sent successfully
     */
    boolean sendViewingCancelledEmail(PropertyViewing viewing);

    /**
     * Send follow-up email after viewing
     *
     * @param viewing Completed viewing appointment
     * @return true if email sent successfully
     */
    boolean sendViewingFollowUpEmail(PropertyViewing viewing);

    /**
     * Send application instructions email
     *
     * @param lead Lead who expressed interest
     * @param property Property they're interested in
     * @return true if email sent successfully
     */
    boolean sendApplicationInstructionsEmail(Lead lead, Property property);

    /**
     * Send referencing started notification
     *
     * @param lead Lead in referencing stage
     * @return true if email sent successfully
     */
    boolean sendReferencingStartedEmail(Lead lead);

    /**
     * Send referencing completed notification
     *
     * @param lead Lead who completed referencing
     * @param approved Whether references were approved
     * @return true if email sent successfully
     */
    boolean sendReferencingCompletedEmail(Lead lead, boolean approved);

    /**
     * Send contract signing invitation
     *
     * @param lead Lead ready for contracts
     * @param property Property they're leasing
     * @return true if email sent successfully
     */
    boolean sendContractSigningEmail(Lead lead, Property property);

    /**
     * Send tenancy confirmation email
     *
     * @param lead Converted lead (now tenant)
     * @param property Property they're moving into
     * @return true if email sent successfully
     */
    boolean sendTenancyConfirmationEmail(Lead lead, Property property);

    /**
     * Send property available notification to matching leads
     * Notifies leads in database who match property criteria
     *
     * @param property Newly available property
     * @return Number of notification emails sent
     */
    int notifyMatchingLeadsOfProperty(Property property);

    /**
     * Send vacancy notification to property owner
     *
     * @param property Property that became vacant
     * @return true if email sent successfully
     */
    boolean sendVacancyNotificationToOwner(Property property);

    /**
     * Send advertising started notification to property owner
     *
     * @param property Property that started advertising
     * @return true if email sent successfully
     */
    boolean sendAdvertisingStartedNotificationToOwner(Property property);

    /**
     * Send enquiry notification to property owner
     *
     * @param lead New enquiry lead
     * @return true if email sent successfully
     */
    boolean sendEnquiryNotificationToOwner(Lead lead);

    /**
     * Send viewing scheduled notification to property owner
     *
     * @param viewing Scheduled viewing
     * @return true if email sent successfully
     */
    boolean sendViewingScheduledNotificationToOwner(PropertyViewing viewing);

    /**
     * Send generic email to lead
     *
     * @param lead Lead to email
     * @param subject Email subject
     * @param message Email body
     * @return true if email sent successfully
     */
    boolean sendEmailToLead(Lead lead, String subject, String message);

    /**
     * Record email communication in lead_communications table
     *
     * @param lead Lead the communication relates to
     * @param subject Email subject
     * @param message Email body
     * @param success Whether email was sent successfully
     */
    void recordCommunication(Lead lead, String subject, String message, boolean success);
}

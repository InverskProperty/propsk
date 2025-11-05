package site.easy.to.build.crm.service.property;

import site.easy.to.build.crm.entity.Lead;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.PropertyViewing;
import site.easy.to.build.crm.entity.User;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service interface for managing property viewings.
 * Handles viewing scheduling, reminders, and feedback tracking.
 */
public interface PropertyViewingService {

    /**
     * Schedule a new viewing
     *
     * @param lead Lead viewing the property
     * @param property Property to be viewed
     * @param scheduledDateTime Date and time of viewing
     * @param durationMinutes Duration in minutes
     * @param viewingType IN_PERSON or VIRTUAL
     * @return Created viewing
     */
    PropertyViewing scheduleViewing(Lead lead, Property property, LocalDateTime scheduledDateTime,
                                    Integer durationMinutes, String viewingType);

    /**
     * Schedule a new viewing with assigned staff member
     *
     * @param lead Lead viewing the property
     * @param property Property to be viewed
     * @param scheduledDateTime Date and time of viewing
     * @param durationMinutes Duration in minutes
     * @param viewingType IN_PERSON or VIRTUAL
     * @param assignedUser Staff member conducting viewing
     * @return Created viewing
     */
    PropertyViewing scheduleViewing(Lead lead, Property property, LocalDateTime scheduledDateTime,
                                    Integer durationMinutes, String viewingType, User assignedUser);

    /**
     * Reschedule an existing viewing
     *
     * @param viewingId Viewing ID
     * @param newDateTime New date and time
     * @return Updated viewing
     */
    PropertyViewing rescheduleViewing(Long viewingId, LocalDateTime newDateTime);

    /**
     * Cancel a viewing
     *
     * @param viewingId Viewing ID
     * @return Updated viewing
     */
    PropertyViewing cancelViewing(Long viewingId);

    /**
     * Mark viewing as no-show
     *
     * @param viewingId Viewing ID
     * @return Updated viewing
     */
    PropertyViewing markAsNoShow(Long viewingId);

    /**
     * Complete a viewing with feedback
     *
     * @param viewingId Viewing ID
     * @param feedback Feedback from the viewing
     * @param interestedLevel Interest level (VERY_INTERESTED, INTERESTED, NEUTRAL, NOT_INTERESTED)
     * @return Updated viewing
     */
    PropertyViewing completeViewing(Long viewingId, String feedback, String interestedLevel);

    /**
     * Confirm a viewing
     *
     * @param viewingId Viewing ID
     * @return Updated viewing
     */
    PropertyViewing confirmViewing(Long viewingId);

    /**
     * Send viewing reminder emails
     * Sends reminders for viewings within next 24 hours that haven't been sent yet
     *
     * @return Number of reminders sent
     */
    int sendViewingReminders();

    /**
     * Get viewing by ID
     *
     * @param viewingId Viewing ID
     * @return Viewing entity
     */
    PropertyViewing getViewingById(Long viewingId);

    /**
     * Get all viewings for a lead
     *
     * @param lead Lead entity
     * @return List of viewings
     */
    List<PropertyViewing> getViewingsForLead(Lead lead);

    /**
     * Get all viewings for a property
     *
     * @param property Property entity
     * @return List of viewings
     */
    List<PropertyViewing> getViewingsForProperty(Property property);

    /**
     * Get upcoming viewings
     *
     * @return List of upcoming viewings
     */
    List<PropertyViewing> getUpcomingViewings();

    /**
     * Get viewings for today for a specific user
     *
     * @param user User entity
     * @return List of today's viewings
     */
    List<PropertyViewing> getTodaysViewingsForUser(User user);

    /**
     * Get viewings by status
     *
     * @param status Viewing status
     * @return List of viewings
     */
    List<PropertyViewing> getViewingsByStatus(String status);

    /**
     * Get viewings needing reminders
     *
     * @return List of viewings needing reminder emails
     */
    List<PropertyViewing> getViewingsNeedingReminders();

    /**
     * Get recent viewings (last N days)
     *
     * @param days Number of days to look back
     * @return List of recent viewings
     */
    List<PropertyViewing> getRecentViewings(int days);

    /**
     * Get viewings by interest level
     *
     * @param interestedLevel Interest level
     * @return List of viewings with that interest level
     */
    List<PropertyViewing> getViewingsByInterestLevel(String interestedLevel);

    /**
     * Save viewing
     *
     * @param viewing Viewing entity
     * @return Saved viewing
     */
    PropertyViewing save(PropertyViewing viewing);

    /**
     * Delete viewing
     *
     * @param viewing Viewing entity
     */
    void delete(PropertyViewing viewing);
}

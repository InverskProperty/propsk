package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Entity representing a property viewing appointment.
 * Links a lead to a property for a scheduled viewing.
 */
@Entity
@Table(name = "property_viewings")
public class PropertyViewing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id", nullable = false)
    @NotNull(message = "Lead is required")
    private Lead lead;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    @NotNull(message = "Property is required")
    private Property property;

    @Column(name = "scheduled_datetime", nullable = false)
    @NotNull(message = "Scheduled datetime is required")
    private LocalDateTime scheduledDatetime;

    @Column(name = "duration_minutes")
    private Integer durationMinutes = 30;

    @Column(name = "viewing_type")
    private String viewingType = "IN_PERSON"; // IN_PERSON or VIRTUAL

    @Column(name = "status", nullable = false)
    @NotNull(message = "Status is required")
    private String status = "SCHEDULED"; // SCHEDULED, CONFIRMED, COMPLETED, CANCELLED, NO_SHOW, RESCHEDULED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_user_id")
    private User assignedToUser;

    @Column(name = "attendees", columnDefinition = "TEXT")
    private String attendees; // JSON array of attendee information

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes; // Notes before viewing

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback; // Feedback after viewing

    @Column(name = "interested_level")
    private String interestedLevel; // VERY_INTERESTED, INTERESTED, NEUTRAL, NOT_INTERESTED

    @Column(name = "google_calendar_event_id")
    private String googleCalendarEventId;

    @Column(name = "reminder_sent")
    private Boolean reminderSent = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Constructors
    public PropertyViewing() {
    }

    public PropertyViewing(Lead lead, Property property, LocalDateTime scheduledDatetime) {
        this.lead = lead;
        this.property = property;
        this.scheduledDatetime = scheduledDatetime;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Lead getLead() {
        return lead;
    }

    public void setLead(Lead lead) {
        this.lead = lead;
    }

    public Property getProperty() {
        return property;
    }

    public void setProperty(Property property) {
        this.property = property;
    }

    public LocalDateTime getScheduledDatetime() {
        return scheduledDatetime;
    }

    public void setScheduledDatetime(LocalDateTime scheduledDatetime) {
        this.scheduledDatetime = scheduledDatetime;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getViewingType() {
        return viewingType;
    }

    public void setViewingType(String viewingType) {
        this.viewingType = viewingType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public User getAssignedToUser() {
        return assignedToUser;
    }

    public void setAssignedToUser(User assignedToUser) {
        this.assignedToUser = assignedToUser;
    }

    public String getAttendees() {
        return attendees;
    }

    public void setAttendees(String attendees) {
        this.attendees = attendees;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public String getInterestedLevel() {
        return interestedLevel;
    }

    public void setInterestedLevel(String interestedLevel) {
        this.interestedLevel = interestedLevel;
    }

    public String getGoogleCalendarEventId() {
        return googleCalendarEventId;
    }

    public void setGoogleCalendarEventId(String googleCalendarEventId) {
        this.googleCalendarEventId = googleCalendarEventId;
    }

    public Boolean getReminderSent() {
        return reminderSent;
    }

    public void setReminderSent(Boolean reminderSent) {
        this.reminderSent = reminderSent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    // Helper methods

    /**
     * Check if viewing is in the future
     */
    public boolean isUpcoming() {
        return scheduledDatetime != null && scheduledDatetime.isAfter(LocalDateTime.now());
    }

    /**
     * Check if viewing is completed
     */
    public boolean isCompleted() {
        return "COMPLETED".equalsIgnoreCase(status);
    }

    /**
     * Check if viewing can be rescheduled
     */
    public boolean canBeRescheduled() {
        return "SCHEDULED".equalsIgnoreCase(status) || "CONFIRMED".equalsIgnoreCase(status);
    }

    /**
     * Mark viewing as completed and set feedback
     */
    public void complete(String feedback, String interestedLevel) {
        this.status = "COMPLETED";
        this.feedback = feedback;
        this.interestedLevel = interestedLevel;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Cancel viewing with reason
     */
    public void cancel() {
        this.status = "CANCELLED";
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Mark as no-show
     */
    public void markAsNoShow() {
        this.status = "NO_SHOW";
        this.updatedAt = LocalDateTime.now();
    }
}

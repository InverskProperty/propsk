package site.easy.to.build.crm.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity representing tasks and reminders for property vacancy management.
 * Tracks actions needed when a property becomes vacant (inspections, repairs, marketing, etc.)
 */
@Entity
@Table(name = "property_vacancy_tasks")
public class PropertyVacancyTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    @NotNull(message = "Property is required")
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "letting_instruction_id")
    @JsonIgnore  // Completely exclude from JSON to prevent circular reference
    private LettingInstruction lettingInstruction;

    @Column(name = "task_type", nullable = false)
    @NotBlank(message = "Task type is required")
    private String taskType; // INSPECTION, PHOTOGRAPHY, REPAIRS, CLEANING, LISTING_CREATION, KEY_HANDOVER, etc.

    @Column(name = "title", nullable = false)
    @NotBlank(message = "Title is required")
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", nullable = false)
    @NotBlank(message = "Status is required")
    private String status = "PENDING"; // PENDING, IN_PROGRESS, COMPLETED, CANCELLED

    @Column(name = "priority")
    private String priority = "MEDIUM"; // LOW, MEDIUM, HIGH, URGENT

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "completed_date")
    private LocalDateTime completedDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_user_id")
    private User assignedToUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @Column(name = "auto_created")
    private Boolean autoCreated = false;

    @Column(name = "trigger_event")
    private String triggerEvent; // NOTICE_GIVEN, VACANCY_APPROACHING, etc.

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

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
    public PropertyVacancyTask() {
    }

    public PropertyVacancyTask(Property property, String taskType, String title) {
        this.property = property;
        this.taskType = taskType;
        this.title = title;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Property getProperty() {
        return property;
    }

    public void setProperty(Property property) {
        this.property = property;
    }

    public LettingInstruction getLettingInstruction() {
        return lettingInstruction;
    }

    public void setLettingInstruction(LettingInstruction lettingInstruction) {
        this.lettingInstruction = lettingInstruction;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public LocalDateTime getCompletedDate() {
        return completedDate;
    }

    public void setCompletedDate(LocalDateTime completedDate) {
        this.completedDate = completedDate;
    }

    public User getAssignedToUser() {
        return assignedToUser;
    }

    public void setAssignedToUser(User assignedToUser) {
        this.assignedToUser = assignedToUser;
    }

    public User getCreatedByUser() {
        return createdByUser;
    }

    public void setCreatedByUser(User createdByUser) {
        this.createdByUser = createdByUser;
    }

    public Boolean getAutoCreated() {
        return autoCreated;
    }

    public void setAutoCreated(Boolean autoCreated) {
        this.autoCreated = autoCreated;
    }

    public String getTriggerEvent() {
        return triggerEvent;
    }

    public void setTriggerEvent(String triggerEvent) {
        this.triggerEvent = triggerEvent;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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

    // Helper methods

    /**
     * Check if task is overdue
     */
    public boolean isOverdue() {
        return dueDate != null &&
               !isCompleted() &&
               dueDate.isBefore(LocalDate.now());
    }

    /**
     * Check if task is completed
     */
    public boolean isCompleted() {
        return "COMPLETED".equalsIgnoreCase(status);
    }

    /**
     * Check if task is pending
     */
    public boolean isPending() {
        return "PENDING".equalsIgnoreCase(status);
    }

    /**
     * Mark task as completed
     */
    public void complete() {
        this.status = "COMPLETED";
        this.completedDate = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Mark task as in progress
     */
    public void startProgress() {
        if (isPending()) {
            this.status = "IN_PROGRESS";
            this.updatedAt = LocalDateTime.now();
        }
    }

    /**
     * Cancel task
     */
    public void cancel() {
        this.status = "CANCELLED";
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Check if task is high or urgent priority
     */
    public boolean isHighPriority() {
        return "HIGH".equalsIgnoreCase(priority) || "URGENT".equalsIgnoreCase(priority);
    }

    /**
     * Get days until due
     */
    public Long getDaysUntilDue() {
        if (dueDate == null) {
            return null;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), dueDate);
    }
}

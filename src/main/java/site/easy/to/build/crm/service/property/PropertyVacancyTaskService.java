package site.easy.to.build.crm.service.property;

import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.PropertyVacancyTask;
import site.easy.to.build.crm.entity.User;

import java.time.LocalDate;
import java.util.List;

/**
 * Service interface for managing property vacancy tasks.
 * Handles task creation, assignment, and tracking.
 */
public interface PropertyVacancyTaskService {

    /**
     * Create a new task
     *
     * @param property Property for the task
     * @param taskType Task type (INSPECTION, PHOTOGRAPHY, etc.)
     * @param title Task title
     * @param description Task description
     * @param dueDate Due date
     * @param priority Priority (LOW, MEDIUM, HIGH, URGENT)
     * @return Created task
     */
    PropertyVacancyTask createTask(Property property, String taskType, String title,
                                   String description, LocalDate dueDate, String priority);

    /**
     * Create a new task with assignment
     *
     * @param property Property for the task
     * @param taskType Task type
     * @param title Task title
     * @param description Task description
     * @param dueDate Due date
     * @param priority Priority
     * @param assignedUser User to assign task to
     * @return Created task
     */
    PropertyVacancyTask createTask(Property property, String taskType, String title,
                                   String description, LocalDate dueDate, String priority,
                                   User assignedUser);

    /**
     * Get task by ID
     *
     * @param taskId Task ID
     * @return Task entity
     */
    PropertyVacancyTask getTaskById(Long taskId);

    /**
     * Complete a task
     *
     * @param taskId Task ID
     * @return Updated task
     */
    PropertyVacancyTask completeTask(Long taskId);

    /**
     * Start task progress
     *
     * @param taskId Task ID
     * @return Updated task
     */
    PropertyVacancyTask startTaskProgress(Long taskId);

    /**
     * Cancel a task
     *
     * @param taskId Task ID
     * @return Updated task
     */
    PropertyVacancyTask cancelTask(Long taskId);

    /**
     * Assign task to user
     *
     * @param taskId Task ID
     * @param user User to assign to
     * @return Updated task
     */
    PropertyVacancyTask assignTask(Long taskId, User user);

    /**
     * Update task notes
     *
     * @param taskId Task ID
     * @param notes New notes
     * @return Updated task
     */
    PropertyVacancyTask updateTaskNotes(Long taskId, String notes);

    /**
     * Get all tasks for a property
     *
     * @param property Property entity
     * @return List of tasks
     */
    List<PropertyVacancyTask> getTasksForProperty(Property property);

    /**
     * Get active tasks for a property (PENDING or IN_PROGRESS)
     *
     * @param property Property entity
     * @return List of active tasks
     */
    List<PropertyVacancyTask> getActiveTasksForProperty(Property property);

    /**
     * Get overdue tasks
     *
     * @return List of overdue tasks
     */
    List<PropertyVacancyTask> getOverdueTasks();

    /**
     * Get tasks due today
     *
     * @return List of tasks due today
     */
    List<PropertyVacancyTask> getTasksDueToday();

    /**
     * Get tasks due this week
     *
     * @return List of tasks due this week
     */
    List<PropertyVacancyTask> getTasksDueThisWeek();

    /**
     * Get tasks due between dates
     *
     * @param startDate Start date
     * @param endDate End date
     * @return List of tasks
     */
    List<PropertyVacancyTask> getTasksDueBetween(LocalDate startDate, LocalDate endDate);

    /**
     * Get tasks assigned to user
     *
     * @param user User entity
     * @return List of tasks
     */
    List<PropertyVacancyTask> getTasksForUser(User user);

    /**
     * Get active tasks for user
     *
     * @param user User entity
     * @return List of active tasks
     */
    List<PropertyVacancyTask> getActiveTasksForUser(User user);

    /**
     * Get high priority tasks (HIGH or URGENT)
     *
     * @return List of high priority tasks
     */
    List<PropertyVacancyTask> getHighPriorityTasks();

    /**
     * Get tasks by type
     *
     * @param taskType Task type
     * @return List of tasks
     */
    List<PropertyVacancyTask> getTasksByType(String taskType);

    /**
     * Get tasks by status
     *
     * @param status Task status
     * @return List of tasks
     */
    List<PropertyVacancyTask> getTasksByStatus(String status);

    /**
     * Get auto-created tasks
     *
     * @return List of auto-created tasks
     */
    List<PropertyVacancyTask> getAutoCreatedTasks();

    /**
     * Count active tasks for property
     *
     * @param property Property entity
     * @return Number of active tasks
     */
    long countActiveTasksForProperty(Property property);

    /**
     * Count overdue tasks for property
     *
     * @param property Property entity
     * @return Number of overdue tasks
     */
    long countOverdueTasksForProperty(Property property);

    /**
     * Save task
     *
     * @param task Task entity
     * @return Saved task
     */
    PropertyVacancyTask save(PropertyVacancyTask task);

    /**
     * Delete task
     *
     * @param task Task entity
     */
    void delete(PropertyVacancyTask task);
}

package site.easy.to.build.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.PropertyVacancyTask;
import site.easy.to.build.crm.entity.User;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for PropertyVacancyTask entity.
 * Handles vacancy task data access.
 */
@Repository
public interface PropertyVacancyTaskRepository extends JpaRepository<PropertyVacancyTask, Long> {

    /**
     * Find all tasks for a specific property
     */
    List<PropertyVacancyTask> findByPropertyOrderByDueDateAsc(Property property);

    /**
     * Find tasks by property and specific statuses
     */
    List<PropertyVacancyTask> findByPropertyAndStatusIn(Property property, List<String> statuses);

    /**
     * Find all pending or in-progress tasks for a property
     */
    @Query("SELECT pvt FROM PropertyVacancyTask pvt WHERE pvt.property = :property " +
           "AND pvt.status IN ('PENDING', 'IN_PROGRESS') ORDER BY pvt.dueDate ASC, pvt.priority DESC")
    List<PropertyVacancyTask> findActiveTasksForProperty(@Param("property") Property property);

    /**
     * Find overdue tasks (due date passed and not completed)
     */
    @Query("SELECT pvt FROM PropertyVacancyTask pvt WHERE pvt.dueDate < :today " +
           "AND pvt.status NOT IN ('COMPLETED', 'CANCELLED') ORDER BY pvt.dueDate ASC, pvt.priority DESC")
    List<PropertyVacancyTask> findOverdueTasks(@Param("today") LocalDate today);

    /**
     * Find tasks due within a date range
     */
    @Query("SELECT pvt FROM PropertyVacancyTask pvt WHERE pvt.dueDate BETWEEN :startDate AND :endDate " +
           "AND pvt.status NOT IN ('COMPLETED', 'CANCELLED') ORDER BY pvt.dueDate ASC")
    List<PropertyVacancyTask> findTasksDueBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Find tasks assigned to a specific user
     */
    List<PropertyVacancyTask> findByAssignedToUserOrderByDueDateAsc(User user);

    /**
     * Find active tasks assigned to a specific user
     */
    @Query("SELECT pvt FROM PropertyVacancyTask pvt WHERE pvt.assignedToUser = :user " +
           "AND pvt.status IN ('PENDING', 'IN_PROGRESS') ORDER BY pvt.dueDate ASC, pvt.priority DESC")
    List<PropertyVacancyTask> findActiveTasksForUser(@Param("user") User user);

    /**
     * Find tasks by type
     */
    List<PropertyVacancyTask> findByTaskTypeOrderByDueDateAsc(String taskType);

    /**
     * Find tasks by status
     */
    List<PropertyVacancyTask> findByStatusOrderByDueDateAsc(String status);

    /**
     * Find high priority tasks (HIGH or URGENT)
     */
    @Query("SELECT pvt FROM PropertyVacancyTask pvt WHERE pvt.priority IN ('HIGH', 'URGENT') " +
           "AND pvt.status NOT IN ('COMPLETED', 'CANCELLED') ORDER BY pvt.dueDate ASC")
    List<PropertyVacancyTask> findHighPriorityTasks();

    /**
     * Find auto-created tasks
     */
    @Query("SELECT pvt FROM PropertyVacancyTask pvt WHERE pvt.autoCreated = true ORDER BY pvt.createdAt DESC")
    List<PropertyVacancyTask> findAutoCreatedTasks();

    /**
     * Find tasks by trigger event
     */
    List<PropertyVacancyTask> findByTriggerEventOrderByCreatedAtDesc(String triggerEvent);

    /**
     * Count active tasks for a property
     */
    @Query("SELECT COUNT(pvt) FROM PropertyVacancyTask pvt WHERE pvt.property = :property " +
           "AND pvt.status IN ('PENDING', 'IN_PROGRESS')")
    long countActiveTasksForProperty(@Param("property") Property property);

    /**
     * Count overdue tasks for a property
     */
    @Query("SELECT COUNT(pvt) FROM PropertyVacancyTask pvt WHERE pvt.property = :property " +
           "AND pvt.dueDate < :today AND pvt.status NOT IN ('COMPLETED', 'CANCELLED')")
    long countOverdueTasksForProperty(@Param("property") Property property, @Param("today") LocalDate today);

    /**
     * Find tasks created by a specific user
     */
    List<PropertyVacancyTask> findByCreatedByUserOrderByCreatedAtDesc(User user);

    /**
     * Find tasks due today
     */
    @Query("SELECT pvt FROM PropertyVacancyTask pvt WHERE pvt.dueDate = :today " +
           "AND pvt.status NOT IN ('COMPLETED', 'CANCELLED') ORDER BY pvt.priority DESC")
    List<PropertyVacancyTask> findTasksDueToday(@Param("today") LocalDate today);

    /**
     * Find tasks due this week
     */
    @Query("SELECT pvt FROM PropertyVacancyTask pvt WHERE pvt.dueDate BETWEEN :today AND :endOfWeek " +
           "AND pvt.status NOT IN ('COMPLETED', 'CANCELLED') ORDER BY pvt.dueDate ASC, pvt.priority DESC")
    List<PropertyVacancyTask> findTasksDueThisWeek(@Param("today") LocalDate today, @Param("endOfWeek") LocalDate endOfWeek);

    /**
     * Delete all tasks for a property
     */
    void deleteByProperty(Property property);
}

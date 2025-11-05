package site.easy.to.build.crm.service.property;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.PropertyVacancyTask;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.repository.PropertyVacancyTaskRepository;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Implementation of PropertyVacancyTaskService.
 * Handles vacancy task creation, assignment, and tracking.
 */
@Service
public class PropertyVacancyTaskServiceImpl implements PropertyVacancyTaskService {

    private final PropertyVacancyTaskRepository taskRepository;

    public PropertyVacancyTaskServiceImpl(PropertyVacancyTaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    @Transactional
    public PropertyVacancyTask createTask(Property property, String taskType, String title,
                                          String description, LocalDate dueDate, String priority) {
        PropertyVacancyTask task = new PropertyVacancyTask(property, taskType, title);
        task.setDescription(description);
        task.setDueDate(dueDate);
        task.setPriority(priority != null ? priority : "MEDIUM");
        task.setStatus("PENDING");

        return taskRepository.save(task);
    }

    @Override
    @Transactional
    public PropertyVacancyTask createTask(Property property, String taskType, String title,
                                          String description, LocalDate dueDate, String priority,
                                          User assignedUser) {
        PropertyVacancyTask task = createTask(property, taskType, title, description, dueDate, priority);
        task.setAssignedToUser(assignedUser);
        return taskRepository.save(task);
    }

    @Override
    public PropertyVacancyTask getTaskById(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found with ID: " + taskId));
    }

    @Override
    @Transactional
    public PropertyVacancyTask completeTask(Long taskId) {
        PropertyVacancyTask task = getTaskById(taskId);
        task.complete();
        return taskRepository.save(task);
    }

    @Override
    @Transactional
    public PropertyVacancyTask startTaskProgress(Long taskId) {
        PropertyVacancyTask task = getTaskById(taskId);
        task.startProgress();
        return taskRepository.save(task);
    }

    @Override
    @Transactional
    public PropertyVacancyTask cancelTask(Long taskId) {
        PropertyVacancyTask task = getTaskById(taskId);
        task.setStatus("CANCELLED");
        return taskRepository.save(task);
    }

    @Override
    @Transactional
    public PropertyVacancyTask assignTask(Long taskId, User user) {
        PropertyVacancyTask task = getTaskById(taskId);
        task.setAssignedToUser(user);
        return taskRepository.save(task);
    }

    @Override
    @Transactional
    public PropertyVacancyTask updateTaskNotes(Long taskId, String notes) {
        PropertyVacancyTask task = getTaskById(taskId);
        task.setNotes(notes);
        return taskRepository.save(task);
    }

    @Override
    public List<PropertyVacancyTask> getTasksForProperty(Property property) {
        return taskRepository.findByPropertyOrderByDueDateAsc(property);
    }

    @Override
    public List<PropertyVacancyTask> getActiveTasksForProperty(Property property) {
        return taskRepository.findActiveTasksForProperty(property);
    }

    @Override
    public List<PropertyVacancyTask> getOverdueTasks() {
        return taskRepository.findOverdueTasks(LocalDate.now());
    }

    @Override
    public List<PropertyVacancyTask> getTasksDueToday() {
        LocalDate today = LocalDate.now();
        return taskRepository.findTasksDueBetween(today, today);
    }

    @Override
    public List<PropertyVacancyTask> getTasksDueThisWeek() {
        LocalDate today = LocalDate.now();
        LocalDate endOfWeek = today.plusDays(7);
        return taskRepository.findTasksDueBetween(today, endOfWeek);
    }

    @Override
    public List<PropertyVacancyTask> getTasksDueBetween(LocalDate startDate, LocalDate endDate) {
        return taskRepository.findTasksDueBetween(startDate, endDate);
    }

    @Override
    public List<PropertyVacancyTask> getTasksForUser(User user) {
        return taskRepository.findByAssignedToUserOrderByDueDateAsc(user);
    }

    @Override
    public List<PropertyVacancyTask> getActiveTasksForUser(User user) {
        return taskRepository.findActiveTasksForUser(user);
    }

    @Override
    public List<PropertyVacancyTask> getHighPriorityTasks() {
        return taskRepository.findHighPriorityTasks();
    }

    @Override
    public List<PropertyVacancyTask> getTasksByType(String taskType) {
        return taskRepository.findByTaskTypeOrderByDueDateAsc(taskType);
    }

    @Override
    public List<PropertyVacancyTask> getTasksByStatus(String status) {
        return taskRepository.findByStatusOrderByDueDateAsc(status);
    }

    @Override
    public List<PropertyVacancyTask> getAutoCreatedTasks() {
        return taskRepository.findAutoCreatedTasks();
    }

    @Override
    public long countActiveTasksForProperty(Property property) {
        return taskRepository.countActiveTasksForProperty(property);
    }

    @Override
    public long countOverdueTasksForProperty(Property property) {
        return taskRepository.countOverdueTasksForProperty(property, LocalDate.now());
    }

    @Override
    @Transactional
    public PropertyVacancyTask save(PropertyVacancyTask task) {
        return taskRepository.save(task);
    }

    @Override
    @Transactional
    public void delete(PropertyVacancyTask task) {
        taskRepository.delete(task);
    }
}

package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.PropertyVacancyTask;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.property.PropertyVacancyTaskService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for managing property vacancy tasks.
 * Handles task creation, assignment, and tracking.
 */
@Controller
@RequestMapping("/employee/vacancy-task")
public class PropertyVacancyTaskController {

    private final PropertyVacancyTaskService taskService;
    private final PropertyService propertyService;
    private final UserService userService;
    private final AuthenticationUtils authenticationUtils;

    @Autowired
    public PropertyVacancyTaskController(PropertyVacancyTaskService taskService,
                                         PropertyService propertyService,
                                         UserService userService,
                                         AuthenticationUtils authenticationUtils) {
        this.taskService = taskService;
        this.propertyService = propertyService;
        this.userService = userService;
        this.authenticationUtils = authenticationUtils;
    }

    /**
     * Show task dashboard
     */
    @GetMapping("/dashboard")
    public String showDashboard(Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(Long.valueOf(userId));

        if (loggedInUser.isInactiveUser()) {
            return "error/account-inactive";
        }

        List<PropertyVacancyTask> myTasks = taskService.getActiveTasksForUser(loggedInUser);
        List<PropertyVacancyTask> overdueTasks = taskService.getOverdueTasks();
        List<PropertyVacancyTask> todayTasks = taskService.getTasksDueToday();
        List<PropertyVacancyTask> weekTasks = taskService.getTasksDueThisWeek();

        model.addAttribute("myTasks", myTasks);
        model.addAttribute("overdueTasks", overdueTasks);
        model.addAttribute("todayTasks", todayTasks);
        model.addAttribute("weekTasks", weekTasks);
        model.addAttribute("user", loggedInUser);

        return "employee/vacancy-task/dashboard";
    }

    /**
     * Create new task - API endpoint
     */
    @PostMapping("/api/create")
    @ResponseBody
    public ResponseEntity<?> createTask(
            @RequestParam Long propertyId,
            @RequestParam String taskType,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long assignedUserId,
            Authentication authentication) {

        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            Property property = propertyService.findById(propertyId);
            if (property == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Property not found"));
            }

            PropertyVacancyTask task;

            if (assignedUserId != null) {
                User assignedUser = userService.findById(assignedUserId);
                task = taskService.createTask(property, taskType, title, description,
                        dueDate, priority, assignedUser);
            } else {
                task = taskService.createTask(property, taskType, title, description,
                        dueDate, priority);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("task", task);
            response.put("message", "Task created successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create task: " + e.getMessage()));
        }
    }

    /**
     * Complete task - API endpoint
     */
    @PostMapping("/api/complete/{taskId}")
    @ResponseBody
    public ResponseEntity<?> completeTask(
            @PathVariable Long taskId,
            Authentication authentication) {

        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            PropertyVacancyTask task = taskService.completeTask(taskId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("task", task);
            response.put("message", "Task completed successfully");

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to complete task"));
        }
    }

    /**
     * Start task progress - API endpoint
     */
    @PostMapping("/api/start/{taskId}")
    @ResponseBody
    public ResponseEntity<?> startTaskProgress(
            @PathVariable Long taskId,
            Authentication authentication) {

        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            PropertyVacancyTask task = taskService.startTaskProgress(taskId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("task", task);
            response.put("message", "Task started");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to start task"));
        }
    }

    /**
     * Cancel task - API endpoint
     */
    @PostMapping("/api/cancel/{taskId}")
    @ResponseBody
    public ResponseEntity<?> cancelTask(
            @PathVariable Long taskId,
            Authentication authentication) {

        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            PropertyVacancyTask task = taskService.cancelTask(taskId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("task", task);
            response.put("message", "Task cancelled");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to cancel task"));
        }
    }

    /**
     * Assign task to user - API endpoint
     */
    @PostMapping("/api/assign/{taskId}")
    @ResponseBody
    public ResponseEntity<?> assignTask(
            @PathVariable Long taskId,
            @RequestParam Long userId,
            Authentication authentication) {

        try {
            int currentUserId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(currentUserId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            User assignedUser = userService.findById(userId);
            if (assignedUser == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "User not found"));
            }

            PropertyVacancyTask task = taskService.assignTask(taskId, assignedUser);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("task", task);
            response.put("message", "Task assigned to " + assignedUser.getFirstName());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to assign task"));
        }
    }

    /**
     * Update task notes - API endpoint
     */
    @PostMapping("/api/update-notes/{taskId}")
    @ResponseBody
    public ResponseEntity<?> updateTaskNotes(
            @PathVariable Long taskId,
            @RequestParam String notes,
            Authentication authentication) {

        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            PropertyVacancyTask task = taskService.updateTaskNotes(taskId, notes);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("task", task);
            response.put("message", "Notes updated");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update notes"));
        }
    }

    /**
     * Get task by ID - API endpoint
     */
    @GetMapping("/api/{taskId}")
    @ResponseBody
    public ResponseEntity<?> getTask(
            @PathVariable Long taskId,
            Authentication authentication) {

        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            PropertyVacancyTask task = taskService.getTaskById(taskId);
            return ResponseEntity.ok(task);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve task"));
        }
    }

    /**
     * Get tasks for property - API endpoint
     */
    @GetMapping("/api/property/{propertyId}")
    @ResponseBody
    public ResponseEntity<?> getTasksForProperty(
            @PathVariable Long propertyId,
            Authentication authentication) {

        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            Property property = propertyService.findById(propertyId);
            if (property == null) {
                return ResponseEntity.notFound().build();
            }

            List<PropertyVacancyTask> tasks = taskService.getTasksForProperty(property);
            return ResponseEntity.ok(tasks);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve tasks"));
        }
    }

    /**
     * Get overdue tasks - API endpoint
     */
    @GetMapping("/api/overdue")
    @ResponseBody
    public ResponseEntity<?> getOverdueTasks(Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            List<PropertyVacancyTask> tasks = taskService.getOverdueTasks();
            return ResponseEntity.ok(tasks);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve overdue tasks"));
        }
    }

    /**
     * Get tasks due today - API endpoint
     */
    @GetMapping("/api/due-today")
    @ResponseBody
    public ResponseEntity<?> getTasksDueToday(Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            List<PropertyVacancyTask> tasks = taskService.getTasksDueToday();
            return ResponseEntity.ok(tasks);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve tasks"));
        }
    }

    /**
     * Get tasks due this week - API endpoint
     */
    @GetMapping("/api/due-this-week")
    @ResponseBody
    public ResponseEntity<?> getTasksDueThisWeek(Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            List<PropertyVacancyTask> tasks = taskService.getTasksDueThisWeek();
            return ResponseEntity.ok(tasks);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve tasks"));
        }
    }

    /**
     * Get my tasks - API endpoint
     */
    @GetMapping("/api/my-tasks")
    @ResponseBody
    public ResponseEntity<?> getMyTasks(Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            List<PropertyVacancyTask> tasks = taskService.getActiveTasksForUser(loggedInUser);
            return ResponseEntity.ok(tasks);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve tasks"));
        }
    }

    /**
     * Get high priority tasks - API endpoint
     */
    @GetMapping("/api/high-priority")
    @ResponseBody
    public ResponseEntity<?> getHighPriorityTasks(Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            List<PropertyVacancyTask> tasks = taskService.getHighPriorityTasks();
            return ResponseEntity.ok(tasks);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve tasks"));
        }
    }

    /**
     * Get task counts for property - API endpoint
     */
    @GetMapping("/api/property/{propertyId}/counts")
    @ResponseBody
    public ResponseEntity<?> getTaskCounts(
            @PathVariable Long propertyId,
            Authentication authentication) {

        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            Property property = propertyService.findById(propertyId);
            if (property == null) {
                return ResponseEntity.notFound().build();
            }

            long activeTasks = taskService.countActiveTasksForProperty(property);
            long overdueTasks = taskService.countOverdueTasksForProperty(property);

            Map<String, Object> counts = new HashMap<>();
            counts.put("activeTasks", activeTasks);
            counts.put("overdueTasks", overdueTasks);

            return ResponseEntity.ok(counts);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve task counts"));
        }
    }
}

package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.entity.Lead;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.PropertyViewing;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.service.lead.LeadService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.property.PropertyViewingService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for managing property viewings.
 * Handles viewing scheduling, reminders, and feedback.
 */
@Controller
@RequestMapping("/employee/property-viewing")
public class PropertyViewingController {

    private final PropertyViewingService viewingService;
    private final LeadService leadService;
    private final PropertyService propertyService;
    private final UserService userService;
    private final AuthenticationUtils authenticationUtils;

    @Autowired
    public PropertyViewingController(PropertyViewingService viewingService,
                                     LeadService leadService,
                                     PropertyService propertyService,
                                     UserService userService,
                                     AuthenticationUtils authenticationUtils) {
        this.viewingService = viewingService;
        this.leadService = leadService;
        this.propertyService = propertyService;
        this.userService = userService;
        this.authenticationUtils = authenticationUtils;
    }

    /**
     * Show viewing calendar/scheduler page
     */
    @GetMapping("/calendar")
    public String showCalendar(Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(Long.valueOf(userId));

        if (loggedInUser.isInactiveUser()) {
            return "error/account-inactive";
        }

        List<PropertyViewing> upcomingViewings = viewingService.getUpcomingViewings();
        List<PropertyViewing> todaysViewings = viewingService.getTodaysViewingsForUser(loggedInUser);

        model.addAttribute("upcomingViewings", upcomingViewings);
        model.addAttribute("todaysViewings", todaysViewings);
        model.addAttribute("user", loggedInUser);

        return "employee/property-viewing/calendar";
    }

    /**
     * Schedule a new viewing - API endpoint
     */
    @PostMapping("/api/schedule")
    @ResponseBody
    public ResponseEntity<?> scheduleViewing(
            @RequestParam Integer leadId,
            @RequestParam Long propertyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime scheduledDateTime,
            @RequestParam(required = false) Integer durationMinutes,
            @RequestParam(required = false) String viewingType,
            @RequestParam(required = false) Long assignedUserId,
            Authentication authentication) {

        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            Lead lead = leadService.findByLeadId(leadId);
            Property property = propertyService.findById(propertyId);

            if (lead == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Lead not found"));
            }

            if (property == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Property not found"));
            }

            PropertyViewing viewing;

            if (assignedUserId != null) {
                User assignedUser = userService.findById(assignedUserId);
                viewing = viewingService.scheduleViewing(lead, property, scheduledDateTime,
                        durationMinutes, viewingType, assignedUser);
            } else {
                viewing = viewingService.scheduleViewing(lead, property, scheduledDateTime,
                        durationMinutes, viewingType);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("viewing", viewing);
            response.put("message", "Viewing scheduled successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to schedule viewing: " + e.getMessage()));
        }
    }

    /**
     * Reschedule viewing - API endpoint
     */
    @PostMapping("/api/reschedule/{viewingId}")
    @ResponseBody
    public ResponseEntity<?> rescheduleViewing(
            @PathVariable Long viewingId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime newDateTime,
            Authentication authentication) {

        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            PropertyViewing viewing = viewingService.rescheduleViewing(viewingId, newDateTime);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("viewing", viewing);
            response.put("message", "Viewing rescheduled successfully");

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to reschedule viewing"));
        }
    }

    /**
     * Cancel viewing - API endpoint
     */
    @PostMapping("/api/cancel/{viewingId}")
    @ResponseBody
    public ResponseEntity<?> cancelViewing(
            @PathVariable Long viewingId,
            Authentication authentication) {

        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            PropertyViewing viewing = viewingService.cancelViewing(viewingId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("viewing", viewing);
            response.put("message", "Viewing cancelled successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to cancel viewing"));
        }
    }

    /**
     * Mark viewing as no-show - API endpoint
     */
    @PostMapping("/api/no-show/{viewingId}")
    @ResponseBody
    public ResponseEntity<?> markAsNoShow(
            @PathVariable Long viewingId,
            Authentication authentication) {

        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            PropertyViewing viewing = viewingService.markAsNoShow(viewingId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("viewing", viewing);
            response.put("message", "Viewing marked as no-show");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to mark viewing as no-show"));
        }
    }

    /**
     * Complete viewing with feedback - API endpoint
     */
    @PostMapping("/api/complete/{viewingId}")
    @ResponseBody
    public ResponseEntity<?> completeViewing(
            @PathVariable Long viewingId,
            @RequestParam(required = false) String feedback,
            @RequestParam(required = false) String interestedLevel,
            Authentication authentication) {

        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            PropertyViewing viewing = viewingService.completeViewing(viewingId, feedback, interestedLevel);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("viewing", viewing);
            response.put("message", "Viewing completed successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to complete viewing"));
        }
    }

    /**
     * Confirm viewing - API endpoint
     */
    @PostMapping("/api/confirm/{viewingId}")
    @ResponseBody
    public ResponseEntity<?> confirmViewing(
            @PathVariable Long viewingId,
            Authentication authentication) {

        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            PropertyViewing viewing = viewingService.confirmViewing(viewingId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("viewing", viewing);
            response.put("message", "Viewing confirmed");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to confirm viewing"));
        }
    }

    /**
     * Get viewing by ID - API endpoint
     */
    @GetMapping("/api/{viewingId}")
    @ResponseBody
    public ResponseEntity<?> getViewing(
            @PathVariable Long viewingId,
            Authentication authentication) {

        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            PropertyViewing viewing = viewingService.getViewingById(viewingId);
            return ResponseEntity.ok(viewing);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve viewing"));
        }
    }

    /**
     * Get upcoming viewings - API endpoint
     */
    @GetMapping("/api/upcoming")
    @ResponseBody
    public ResponseEntity<?> getUpcomingViewings(Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            List<PropertyViewing> viewings = viewingService.getUpcomingViewings();
            return ResponseEntity.ok(viewings);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve viewings"));
        }
    }

    /**
     * Get today's viewings for current user - API endpoint
     */
    @GetMapping("/api/today")
    @ResponseBody
    public ResponseEntity<?> getTodaysViewings(Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            List<PropertyViewing> viewings = viewingService.getTodaysViewingsForUser(loggedInUser);
            return ResponseEntity.ok(viewings);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve today's viewings"));
        }
    }

    /**
     * Get viewings for a property - API endpoint
     */
    @GetMapping("/api/property/{propertyId}")
    @ResponseBody
    public ResponseEntity<?> getViewingsForProperty(
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

            List<PropertyViewing> viewings = viewingService.getViewingsForProperty(property);
            return ResponseEntity.ok(viewings);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve viewings"));
        }
    }

    /**
     * Get viewings for a lead - API endpoint
     */
    @GetMapping("/api/lead/{leadId}")
    @ResponseBody
    public ResponseEntity<?> getViewingsForLead(
            @PathVariable Integer leadId,
            Authentication authentication) {

        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            Lead lead = leadService.findByLeadId(leadId);
            if (lead == null) {
                return ResponseEntity.notFound().build();
            }

            List<PropertyViewing> viewings = viewingService.getViewingsForLead(lead);
            return ResponseEntity.ok(viewings);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve viewings"));
        }
    }

    /**
     * Send viewing reminders manually - API endpoint
     */
    @PostMapping("/api/send-reminders")
    @ResponseBody
    public ResponseEntity<?> sendReminders(Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            int remindersSent = viewingService.sendViewingReminders();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("remindersSent", remindersSent);
            response.put("message", remindersSent + " reminder(s) sent");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to send reminders"));
        }
    }
}

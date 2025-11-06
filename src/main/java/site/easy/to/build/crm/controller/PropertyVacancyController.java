package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.entity.LettingInstruction;
import site.easy.to.build.crm.entity.InstructionStatus;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.service.LettingInstructionService;
import site.easy.to.build.crm.service.property.PropertyVacancyService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.util.AuthenticationUtils;
import site.easy.to.build.crm.util.AuthorizationUtil;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for managing property letting instruction pipeline.
 * Now uses LettingInstruction workflow instead of Property fields.
 */
@Controller
@RequestMapping("/employee/property-vacancy")
public class PropertyVacancyController {

    private final LettingInstructionService lettingInstructionService;
    private final PropertyVacancyService vacancyService;
    private final PropertyService propertyService;
    private final UserService userService;
    private final AuthenticationUtils authenticationUtils;

    @Autowired
    public PropertyVacancyController(LettingInstructionService lettingInstructionService,
                                     PropertyVacancyService vacancyService,
                                     PropertyService propertyService,
                                     UserService userService,
                                     AuthenticationUtils authenticationUtils) {
        this.lettingInstructionService = lettingInstructionService;
        this.vacancyService = vacancyService;
        this.propertyService = propertyService;
        this.userService = userService;
        this.authenticationUtils = authenticationUtils;
    }

    /**
     * Show instruction-based vacancy pipeline dashboard
     */
    @GetMapping("/dashboard")
    public String showDashboard(Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(Long.valueOf(userId));

        if (loggedInUser.isInactiveUser()) {
            return "error/account-inactive";
        }

        // Get instructions by status (mutually exclusive)
        List<LettingInstruction> instructionReceived = lettingInstructionService.getInstructionsByStatus(InstructionStatus.INSTRUCTION_RECEIVED);
        List<LettingInstruction> advertising = lettingInstructionService.getInstructionsByStatus(InstructionStatus.ADVERTISING);
        List<LettingInstruction> offerAccepted = lettingInstructionService.getInstructionsByStatus(InstructionStatus.OFFER_ACCEPTED);
        List<LettingInstruction> activeLeases = lettingInstructionService.getInstructionsByStatus(InstructionStatus.ACTIVE_LEASE);

        model.addAttribute("instructionReceivedList", instructionReceived);
        model.addAttribute("advertisingList", advertising);
        model.addAttribute("offerAcceptedList", offerAccepted);
        model.addAttribute("activeLeasList", activeLeases);

        return "employee/property-vacancy/dashboard";
    }

    /**
     * Mark property notice given - API endpoint
     */
    @PostMapping("/api/notice-given/{propertyId}")
    @ResponseBody
    public ResponseEntity<?> markNoticeGiven(
            @PathVariable Long propertyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate noticeDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expectedVacancyDate,
            Authentication authentication) {

        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            Property property = vacancyService.markNoticeGiven(propertyId, noticeDate, expectedVacancyDate);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("property", property);
            response.put("message", "Property marked as notice given. Auto-tasks created.");

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to mark property as notice given"));
        }
    }

    /**
     * Start advertising property - API endpoint
     */
    @PostMapping("/api/start-advertising/{propertyId}")
    @ResponseBody
    public ResponseEntity<?> startAdvertising(
            @PathVariable Long propertyId,
            Authentication authentication) {

        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            Property property = vacancyService.startAdvertising(propertyId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("property", property);
            response.put("message", "Property is now being advertised");

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to start advertising"));
        }
    }

    /**
     * Mark property as available - API endpoint
     */
    @PostMapping("/api/mark-available/{propertyId}")
    @ResponseBody
    public ResponseEntity<?> markAvailable(
            @PathVariable Long propertyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate availableFrom,
            Authentication authentication) {

        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            Property property = vacancyService.markAvailable(propertyId, availableFrom);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("property", property);
            response.put("message", "Property marked as available");

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to mark property as available"));
        }
    }

    /**
     * Mark property as occupied - API endpoint
     */
    @PostMapping("/api/mark-occupied/{propertyId}")
    @ResponseBody
    public ResponseEntity<?> markOccupied(
            @PathVariable Long propertyId,
            Authentication authentication) {

        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            Property property = vacancyService.markOccupied(propertyId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("property", property);
            response.put("message", "Property marked as occupied");

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to mark property as occupied"));
        }
    }

    /**
     * Get properties with notice given - API endpoint
     */
    @GetMapping("/api/properties/notice-given")
    @ResponseBody
    public ResponseEntity<?> getPropertiesWithNoticeGiven(Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            List<Property> properties = vacancyService.getPropertiesWithNoticeGiven();
            return ResponseEntity.ok(properties);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve properties"));
        }
    }

    /**
     * Get properties being advertised - API endpoint
     */
    @GetMapping("/api/properties/advertising")
    @ResponseBody
    public ResponseEntity<?> getPropertiesBeingAdvertised(Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            List<Property> properties = vacancyService.getAdvertisingProperties();
            return ResponseEntity.ok(properties);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve properties"));
        }
    }

    /**
     * Get available properties - API endpoint
     */
    @GetMapping("/api/properties/available")
    @ResponseBody
    public ResponseEntity<?> getPropertiesAvailable(Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            List<Property> properties = vacancyService.getAvailableProperties();
            return ResponseEntity.ok(properties);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve properties"));
        }
    }

    /**
     * Get properties requiring marketing attention - API endpoint
     */
    @GetMapping("/api/properties/attention")
    @ResponseBody
    public ResponseEntity<?> getPropertiesRequiringAttention(Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            List<Property> properties = vacancyService.getPropertiesRequiringMarketingAttention();
            return ResponseEntity.ok(properties);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve properties"));
        }
    }

    /**
     * Get vacancy timeline for property - API endpoint
     */
    @GetMapping("/api/property/{propertyId}/timeline")
    @ResponseBody
    public ResponseEntity<?> getVacancyTimeline(
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

            Map<String, Object> timeline = new HashMap<>();
            timeline.put("propertyId", property.getId());
            timeline.put("address", property.getAddressLine1());
            timeline.put("occupancyStatus", property.getOccupancyStatus());
            timeline.put("noticeGivenDate", property.getNoticeGivenDate());
            timeline.put("expectedVacancyDate", property.getExpectedVacancyDate());
            timeline.put("advertisingStartDate", property.getAdvertisingStartDate());
            timeline.put("availableFromDate", property.getAvailableFromDate());
            timeline.put("lastOccupancyChange", property.getLastOccupancyChange());

            if (property.getExpectedVacancyDate() != null) {
                timeline.put("daysUntilVacancy", property.getDaysUntilVacancy());
            }

            return ResponseEntity.ok(timeline);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve timeline"));
        }
    }
}

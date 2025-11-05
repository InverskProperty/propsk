package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.Invoice;
import site.easy.to.build.crm.entity.Lead;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.service.lead.LeadService;
import site.easy.to.build.crm.service.property.LeadConversionService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.util.AuthenticationUtils;
import site.easy.to.build.crm.entity.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for converting property rental leads to tenants.
 * Handles the complete conversion workflow.
 */
@Controller
@RequestMapping("/employee/lead-conversion")
public class LeadConversionController {

    private final LeadConversionService conversionService;
    private final LeadService leadService;
    private final PropertyService propertyService;
    private final UserService userService;
    private final AuthenticationUtils authenticationUtils;

    @Autowired
    public LeadConversionController(LeadConversionService conversionService,
                                    LeadService leadService,
                                    PropertyService propertyService,
                                    UserService userService,
                                    AuthenticationUtils authenticationUtils) {
        this.conversionService = conversionService;
        this.leadService = leadService;
        this.propertyService = propertyService;
        this.userService = userService;
        this.authenticationUtils = authenticationUtils;
    }

    /**
     * Show conversion dashboard
     */
    @GetMapping("/dashboard")
    public String showDashboard(Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(Long.valueOf(userId));

        if (loggedInUser.isInactiveUser()) {
            return "error/account-inactive";
        }

        List<Lead> readyLeads = conversionService.getLeadsReadyForConversion();
        List<Lead> convertedLeads = conversionService.getConvertedLeads();
        double avgDaysToConversion = conversionService.getAverageDaysToConversion();

        model.addAttribute("readyLeads", readyLeads);
        model.addAttribute("convertedLeads", convertedLeads);
        model.addAttribute("avgDaysToConversion", avgDaysToConversion);

        return "employee/lead-conversion/dashboard";
    }

    /**
     * Convert lead to tenant - API endpoint
     */
    @PostMapping("/api/convert/{leadId}")
    @ResponseBody
    public ResponseEntity<?> convertLead(
            @PathVariable Integer leadId,
            @RequestParam Long propertyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate leaseStartDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate leaseEndDate,
            @RequestParam BigDecimal monthlyRent,
            @RequestParam(required = false) BigDecimal depositAmount,
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

            Customer customer = conversionService.convertLeadToTenant(
                    lead, property, leaseStartDate, leaseEndDate, monthlyRent, depositAmount);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("customer", customer);
            response.put("lead", lead);
            response.put("message", "Lead successfully converted to tenant");

            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to convert lead: " + e.getMessage()));
        }
    }

    /**
     * Check if lead can be converted - API endpoint
     */
    @GetMapping("/api/check/{leadId}")
    @ResponseBody
    public ResponseEntity<?> checkConversionReadiness(
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

            boolean canConvert = conversionService.canConvertLead(lead);
            String status = conversionService.getConversionReadinessStatus(lead);

            Map<String, Object> response = new HashMap<>();
            response.put("canConvert", canConvert);
            response.put("status", status);
            response.put("lead", lead);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to check conversion readiness"));
        }
    }

    /**
     * Find matching properties for lead - API endpoint
     */
    @GetMapping("/api/matching-properties/{leadId}")
    @ResponseBody
    public ResponseEntity<?> findMatchingProperties(
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

            List<Property> matchingProperties = conversionService.findMatchingPropertiesForLead(lead);

            Map<String, Object> response = new HashMap<>();
            response.put("lead", lead);
            response.put("matchingProperties", matchingProperties);
            response.put("count", matchingProperties.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to find matching properties"));
        }
    }

    /**
     * Get leads ready for conversion - API endpoint
     */
    @GetMapping("/api/ready")
    @ResponseBody
    public ResponseEntity<?> getLeadsReadyForConversion(Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            List<Lead> leads = conversionService.getLeadsReadyForConversion();
            return ResponseEntity.ok(leads);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve leads"));
        }
    }

    /**
     * Get converted leads - API endpoint
     */
    @GetMapping("/api/converted")
    @ResponseBody
    public ResponseEntity<?> getConvertedLeads(Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            List<Lead> leads = conversionService.getConvertedLeads();
            return ResponseEntity.ok(leads);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve converted leads"));
        }
    }

    /**
     * Calculate conversion rate for property - API endpoint
     */
    @GetMapping("/api/conversion-rate/{propertyId}")
    @ResponseBody
    public ResponseEntity<?> getConversionRate(
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

            double conversionRate = conversionService.calculateConversionRate(property);

            Map<String, Object> response = new HashMap<>();
            response.put("propertyId", propertyId);
            response.put("conversionRate", conversionRate);
            response.put("property", property);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to calculate conversion rate"));
        }
    }

    /**
     * Get average days to conversion - API endpoint
     */
    @GetMapping("/api/stats/avg-days")
    @ResponseBody
    public ResponseEntity<?> getAverageDaysToConversion(Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if (loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is inactive"));
            }

            double avgDays = conversionService.getAverageDaysToConversion();

            Map<String, Object> response = new HashMap<>();
            response.put("averageDaysToConversion", avgDays);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to calculate average days"));
        }
    }

    /**
     * Reverse conversion (rollback) - API endpoint
     * Use with caution - for error corrections only
     */
    @PostMapping("/api/reverse/{leadId}")
    @ResponseBody
    public ResponseEntity<?> reverseConversion(
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

            Lead reversedLead = conversionService.reverseConversion(lead);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("lead", reversedLead);
            response.put("message", "Conversion reversed. Lead marked as 'lost'.");

            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to reverse conversion"));
        }
    }
}

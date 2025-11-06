package site.easy.to.build.crm.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.entity.InstructionStatus;
import site.easy.to.build.crm.entity.Lead;
import site.easy.to.build.crm.entity.LettingInstruction;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.service.LettingInstructionService;
import site.easy.to.build.crm.service.lead.LeadService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for letting instruction management
 * Handles instruction lifecycle, Kanban workspace, and reporting
 */
@Controller
@RequestMapping("/employee/letting-instruction")
public class LettingInstructionController {

    private static final Logger logger = LoggerFactory.getLogger(LettingInstructionController.class);

    @Autowired
    private LettingInstructionService lettingInstructionService;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private LeadService leadService;

    @Autowired
    private AuthenticationUtils authenticationUtils;

    // ===== DASHBOARD AND WORKSPACE VIEWS =====

    /**
     * Instruction workspace dashboard - Kanban pipeline view
     */
    @GetMapping("/workspace")
    public String instructionWorkspace(Authentication authentication, Model model) {
        List<LettingInstruction> activeInstructions = lettingInstructionService.getAllActiveInstructions();
        LettingInstructionService.InstructionSummary summary = lettingInstructionService.getInstructionSummary();

        model.addAttribute("instructions", activeInstructions);
        model.addAttribute("summary", summary);
        model.addAttribute("statuses", InstructionStatus.values());

        return "property-lifecycle/instruction-workspace";
    }

    /**
     * Lead Journey Kanban - Shows lead progression for a specific instruction
     */
    @GetMapping("/lead-pipeline")
    public String leadPipeline(@RequestParam(required = false) Long instructionId,
                               Authentication authentication,
                               Model model) {
        // Get all active instructions for the dropdown
        List<LettingInstruction> activeInstructions = lettingInstructionService.getAllActiveInstructions();
        model.addAttribute("instructions", activeInstructions);

        if (instructionId != null) {
            LettingInstruction instruction = lettingInstructionService.getInstructionWithDetails(instructionId)
                    .orElseThrow(() -> new IllegalArgumentException("Instruction not found: " + instructionId));

            model.addAttribute("selectedInstruction", instruction);

            // Group leads by status
            List<Lead> allLeads = instruction.getLeads();

            model.addAttribute("enquiryLeads",
                allLeads.stream().filter(l -> "enquiry".equals(l.getStatus())).toList());
            model.addAttribute("viewingScheduledLeads",
                allLeads.stream().filter(l -> "viewing-scheduled".equals(l.getStatus())).toList());
            model.addAttribute("viewingCompletedLeads",
                allLeads.stream().filter(l -> "viewing-completed".equals(l.getStatus())).toList());
            model.addAttribute("interestedLeads",
                allLeads.stream().filter(l -> "interested".equals(l.getStatus())).toList());
            model.addAttribute("applicationSubmittedLeads",
                allLeads.stream().filter(l -> "application-submitted".equals(l.getStatus())).toList());
            model.addAttribute("referencingLeads",
                allLeads.stream().filter(l -> "referencing".equals(l.getStatus())).toList());
            model.addAttribute("inContractsLeads",
                allLeads.stream().filter(l -> "in-contracts".equals(l.getStatus())).toList());
            model.addAttribute("convertedLeads",
                allLeads.stream().filter(l -> "converted".equals(l.getStatus())).toList());
            model.addAttribute("lostLeads",
                allLeads.stream().filter(l -> "lost".equals(l.getStatus())).toList());
        }

        return "property-lifecycle/lead-pipeline";
    }

    /**
     * Single instruction detail view
     */
    @GetMapping("/{id}/detail")
    public String instructionDetail(@PathVariable Long id,
                                    Authentication authentication,
                                    Model model) {
        LettingInstruction instruction = lettingInstructionService.getInstructionWithDetails(id)
                .orElseThrow(() -> new IllegalArgumentException("Instruction not found: " + id));

        model.addAttribute("instruction", instruction);
        model.addAttribute("statuses", InstructionStatus.values());

        return "property-lifecycle/instruction-detail";
    }

    /**
     * Letting history for a property
     */
    @GetMapping("/property/{propertyId}/history")
    public String propertyLettingHistory(@PathVariable Long propertyId,
                                         Authentication authentication,
                                         Model model) {
        List<LettingInstruction> history = lettingInstructionService.getPropertyLettingHistory(propertyId);

        model.addAttribute("propertyId", propertyId);
        model.addAttribute("history", history);

        return "property-lifecycle/letting-history";
    }

    /**
     * Active leases dashboard
     */
    @GetMapping("/active-leases")
    public String activeLeasesDashboard(Authentication authentication, Model model) {
        List<LettingInstruction> activeLeases = lettingInstructionService.getActiveLeases();
        List<LettingInstruction> expiringLeases = lettingInstructionService.getLeasesExpiringInDays(90);

        model.addAttribute("activeLeases", activeLeases);
        model.addAttribute("expiringLeases", expiringLeases);

        return "property-lifecycle/active-leases";
    }

    /**
     * Stale listings dashboard
     */
    @GetMapping("/stale-listings")
    public String staleListingsDashboard(Authentication authentication, Model model) {
        List<LettingInstruction> staleListings = lettingInstructionService.getStaleListings(30);
        List<LettingInstruction> lowPerforming = lettingInstructionService.getLowPerformingInstructions(0.1);

        model.addAttribute("staleListings", staleListings);
        model.addAttribute("lowPerforming", lowPerforming);

        return "property-lifecycle/stale-listings";
    }

    // ===== REST API ENDPOINTS =====

    /**
     * Create new letting instruction
     */
    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createInstruction(@RequestBody CreateInstructionRequest request,
                                                                 Authentication authentication) {
        try {
            Long userId = authenticationUtils.getLoggedInUserIdSecure(authentication);

            LettingInstruction instruction = lettingInstructionService.createInstruction(
                    request.getPropertyId(),
                    request.getTargetRent(),
                    request.getTargetLeaseLengthMonths(),
                    request.getExpectedVacancyDate(),
                    request.getPropertyDescription(),
                    userId
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Instruction created successfully");
            response.put("instructionId", instruction.getId());
            response.put("reference", instruction.getInstructionReference());

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.error("Error creating instruction: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            logger.error("Unexpected error creating instruction", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to create instruction");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Start preparing instruction
     */
    @PostMapping("/{id}/start-preparing")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> startPreparing(@PathVariable Long id,
                                                          Authentication authentication) {
        try {
            LettingInstruction instruction = lettingInstructionService.startPreparing(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Instruction moved to PREPARING");
            response.put("instructionId", instruction.getId());
            response.put("status", instruction.getStatus().name());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Start advertising instruction
     */
    @PostMapping("/{id}/start-advertising")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> startAdvertising(@PathVariable Long id,
                                                            @RequestBody StartAdvertisingRequest request,
                                                            Authentication authentication) {
        try {
            LettingInstruction instruction = lettingInstructionService.startAdvertising(
                    id,
                    request.getAdvertisingStartDate(),
                    request.getKeyFeatures(),
                    request.getMarketingNotes()
            );
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Instruction started advertising");
            response.put("instructionId", instruction.getId());
            response.put("status", instruction.getStatus().name());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Start viewings
     */
    @PostMapping("/{id}/start-viewings")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> startViewings(@PathVariable Long id,
                                                         Authentication authentication) {
        try {
            LettingInstruction instruction = lettingInstructionService.startViewings(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Instruction moved to VIEWINGS_IN_PROGRESS", "data", instruction));
        } catch (IllegalArgumentException | IllegalStateException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Mark offer as made
     */
    @PostMapping("/{id}/mark-offer-made")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> markOfferMade(@PathVariable Long id,
                                                         @RequestBody MarkOfferRequest request,
                                                         Authentication authentication) {
        try {
            LettingInstruction instruction = lettingInstructionService.markOfferMade(id, request.getNotes());
            return ResponseEntity.ok(Map.of("success", true, "message", "Offer marked as made", "data", instruction));
        } catch (IllegalArgumentException | IllegalStateException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Convert to active lease
     */
    @PostMapping("/{id}/convert-to-lease")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> convertToActiveLease(@PathVariable Long id,
                                                                @RequestBody ConvertToLeaseRequest request,
                                                                Authentication authentication) {
        try {
            LettingInstruction instruction = lettingInstructionService.convertToActiveLease(
                    id,
                    request.getTenantId(),
                    request.getLeaseStartDate(),
                    request.getLeaseEndDate(),
                    request.getActualRent(),
                    request.getDepositAmount(),
                    request.getLeaseSignedDate()
            );
            return ResponseEntity.ok(Map.of("success", true, "message", "Instruction converted to active lease", "data", instruction));
        } catch (IllegalArgumentException | IllegalStateException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Close instruction
     */
    @PostMapping("/{id}/close")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> closeInstruction(@PathVariable Long id,
                                                            @RequestBody CloseInstructionRequest request,
                                                            Authentication authentication) {
        try {
            LettingInstruction instruction = lettingInstructionService.closeInstruction(id, request.getClosureReason());
            return ResponseEntity.ok(Map.of("success", true, "message", "Instruction closed"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Mark offer as accepted (holding deposit paid)
     */
    @PostMapping("/{id}/mark-offer-accepted")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> markOfferAccepted(@PathVariable Long id,
                                                             @RequestBody MarkOfferAcceptedRequest request,
                                                             Authentication authentication) {
        try {
            LettingInstruction instruction = lettingInstructionService.markOfferAccepted(
                    id,
                    request.getTenantId(),
                    request.getAgreedRent(),
                    request.getNotes()
            );
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Offer accepted - holding deposit paid",
                    "instructionId", instruction.getId(),
                    "status", instruction.getStatus().name()
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Cancel instruction
     */
    @PostMapping("/{id}/cancel")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> cancelInstruction(@PathVariable Long id,
                                                             @RequestBody CancelInstructionRequest request,
                                                             Authentication authentication) {
        try {
            LettingInstruction instruction = lettingInstructionService.cancelInstruction(id, request.getReason());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Instruction cancelled",
                    "instructionId", instruction.getId(),
                    "status", instruction.getStatus().name()
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Add lead to instruction
     */
    @PostMapping("/{id}/add-lead")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addLeadToInstruction(@PathVariable Long id,
                                                                @RequestBody AddLeadRequest request,
                                                                Authentication authentication) {
        try {
            LettingInstruction instruction = lettingInstructionService.addLeadToInstruction(id, request.getLeadId());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Lead added to instruction",
                "instructionId", instruction.getId(),
                "leadCount", instruction.getLeads().size()
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Search leads for Select2 dropdown (AJAX endpoint)
     */
    @GetMapping("/search-leads")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> searchLeads(@RequestParam(value = "q", required = false, defaultValue = "") String searchTerm) {
        try {
            // If search term is empty, return empty list
            if (searchTerm.trim().isEmpty()) {
                return ResponseEntity.ok(List.of());
            }

            List<Lead> leads = leadService.searchLeads(searchTerm, 20);

            // Format for Select2 - convert to simple map with id, text, and additional info
            List<Map<String, Object>> results = leads.stream()
                    .map(lead -> {
                        Map<String, Object> result = new HashMap<>();
                        result.put("id", lead.getLeadId());

                        // Build display text: "Name - Phone - Email (ID: X)"
                        StringBuilder text = new StringBuilder(lead.getName());
                        if (lead.getPhone() != null && !lead.getPhone().isEmpty()) {
                            text.append(" - ").append(lead.getPhone());
                        }
                        if (lead.getEmail() != null && !lead.getEmail().isEmpty()) {
                            text.append(" - ").append(lead.getEmail());
                        }
                        text.append(" (ID: ").append(lead.getLeadId()).append(")");

                        result.put("text", text.toString());
                        result.put("status", lead.getStatus());
                        return result;
                    })
                    .toList();

            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error searching leads", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of());
        }
    }

    /**
     * Remove lead from instruction
     */
    @PostMapping("/{id}/remove-lead")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> removeLeadFromInstruction(@PathVariable Long id,
                                                                     @RequestBody AddLeadRequest request,
                                                                     Authentication authentication) {
        try {
            LettingInstruction instruction = lettingInstructionService.removeLeadFromInstruction(id, request.getLeadId());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Lead removed from instruction",
                "instructionId", instruction.getId(),
                "leadCount", instruction.getLeads().size()
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get instruction by ID (REST API)
     */
    @GetMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<LettingInstruction> getInstruction(@PathVariable Long id) {
        return lettingInstructionService.getInstructionById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get active instructions (REST API)
     */
    @GetMapping("/api/active")
    @ResponseBody
    public ResponseEntity<List<LettingInstruction>> getActiveInstructions() {
        List<LettingInstruction> instructions = lettingInstructionService.getActiveMarketingInstructions();
        return ResponseEntity.ok(instructions);
    }

    /**
     * Get instructions by status (REST API)
     */
    @GetMapping("/api/status/{status}")
    @ResponseBody
    public ResponseEntity<List<LettingInstruction>> getInstructionsByStatus(@PathVariable String status) {
        try {
            InstructionStatus instructionStatus = InstructionStatus.valueOf(status.toUpperCase());
            List<LettingInstruction> instructions = lettingInstructionService.getInstructionsByStatus(instructionStatus);
            return ResponseEntity.ok(instructions);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get dashboard summary (REST API)
     */
    @GetMapping("/api/summary")
    @ResponseBody
    public ResponseEntity<LettingInstructionService.InstructionSummary> getSummary() {
        LettingInstructionService.InstructionSummary summary = lettingInstructionService.getInstructionSummary();
        return ResponseEntity.ok(summary);
    }

    /**
     * Search instructions (REST API)
     */
    @GetMapping("/api/search")
    @ResponseBody
    public ResponseEntity<List<LettingInstruction>> searchInstructions(@RequestParam String query) {
        List<LettingInstruction> results = lettingInstructionService.searchInstructions(query);
        return ResponseEntity.ok(results);
    }

    /**
     * Increment viewing count
     */
    @PostMapping("/{id}/increment-viewing")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> incrementViewingCount(@PathVariable Long id) {
        try {
            LettingInstruction instruction = lettingInstructionService.incrementViewingCount(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Viewing count updated", "data", instruction));
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // ===== REQUEST DTOs =====

    public static class CreateInstructionRequest {
        private Long propertyId;
        private BigDecimal targetRent;
        private Integer targetLeaseLengthMonths;
        private LocalDate expectedVacancyDate;
        private String propertyDescription;

        // Getters and setters
        public Long getPropertyId() { return propertyId; }
        public void setPropertyId(Long propertyId) { this.propertyId = propertyId; }

        public BigDecimal getTargetRent() { return targetRent; }
        public void setTargetRent(BigDecimal targetRent) { this.targetRent = targetRent; }

        public Integer getTargetLeaseLengthMonths() { return targetLeaseLengthMonths; }
        public void setTargetLeaseLengthMonths(Integer targetLeaseLengthMonths) {
            this.targetLeaseLengthMonths = targetLeaseLengthMonths;
        }

        public LocalDate getExpectedVacancyDate() { return expectedVacancyDate; }
        public void setExpectedVacancyDate(LocalDate expectedVacancyDate) {
            this.expectedVacancyDate = expectedVacancyDate;
        }

        public String getPropertyDescription() { return propertyDescription; }
        public void setPropertyDescription(String propertyDescription) {
            this.propertyDescription = propertyDescription;
        }
    }

    public static class StartAdvertisingRequest {
        private LocalDate advertisingStartDate;
        private String keyFeatures;
        private String marketingNotes;

        // Getters and setters
        public LocalDate getAdvertisingStartDate() { return advertisingStartDate; }
        public void setAdvertisingStartDate(LocalDate advertisingStartDate) {
            this.advertisingStartDate = advertisingStartDate;
        }

        public String getKeyFeatures() { return keyFeatures; }
        public void setKeyFeatures(String keyFeatures) { this.keyFeatures = keyFeatures; }

        public String getMarketingNotes() { return marketingNotes; }
        public void setMarketingNotes(String marketingNotes) { this.marketingNotes = marketingNotes; }
    }

    public static class MarkOfferRequest {
        private String notes;

        // Getters and setters
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    public static class ConvertToLeaseRequest {
        private Long tenantId;
        private LocalDate leaseStartDate;
        private LocalDate leaseEndDate;
        private BigDecimal actualRent;
        private BigDecimal depositAmount;
        private LocalDate leaseSignedDate;

        // Getters and setters
        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

        public LocalDate getLeaseStartDate() { return leaseStartDate; }
        public void setLeaseStartDate(LocalDate leaseStartDate) { this.leaseStartDate = leaseStartDate; }

        public LocalDate getLeaseEndDate() { return leaseEndDate; }
        public void setLeaseEndDate(LocalDate leaseEndDate) { this.leaseEndDate = leaseEndDate; }

        public BigDecimal getActualRent() { return actualRent; }
        public void setActualRent(BigDecimal actualRent) { this.actualRent = actualRent; }

        public BigDecimal getDepositAmount() { return depositAmount; }
        public void setDepositAmount(BigDecimal depositAmount) { this.depositAmount = depositAmount; }

        public LocalDate getLeaseSignedDate() { return leaseSignedDate; }
        public void setLeaseSignedDate(LocalDate leaseSignedDate) { this.leaseSignedDate = leaseSignedDate; }
    }

    public static class CloseInstructionRequest {
        private String closureReason;

        // Getters and setters
        public String getClosureReason() { return closureReason; }
        public void setClosureReason(String closureReason) { this.closureReason = closureReason; }
    }

    public static class AddLeadRequest {
        private Long leadId;

        // Getters and setters
        public Long getLeadId() { return leadId; }
        public void setLeadId(Long leadId) { this.leadId = leadId; }
    }

    public static class MarkOfferAcceptedRequest {
        private Long tenantId;
        private BigDecimal agreedRent;
        private String notes;

        // Getters and setters
        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

        public BigDecimal getAgreedRent() { return agreedRent; }
        public void setAgreedRent(BigDecimal agreedRent) { this.agreedRent = agreedRent; }

        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    public static class CancelInstructionRequest {
        private String reason;

        // Getters and setters
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}

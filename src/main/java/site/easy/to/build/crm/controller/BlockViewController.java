package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.BlockRepository;
import site.easy.to.build.crm.service.portfolio.PortfolioBlockService;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for Block Management UI Views and API endpoints
 * Handles standalone block operations independent of portfolios
 */
@Controller
@RequestMapping("/blocks")
public class BlockViewController {

    private static final Logger log = LoggerFactory.getLogger(BlockViewController.class);

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private PortfolioBlockService blockService;

    @Autowired
    private AuthenticationUtils authenticationUtils;

    @Autowired
    private site.easy.to.build.crm.service.property.PropertyService propertyService;

    @Autowired
    private site.easy.to.build.crm.repository.PropertyBlockAssignmentRepository propertyBlockAssignmentRepository;

    @Autowired
    private site.easy.to.build.crm.repository.PropertyRepository propertyRepository;

    @Autowired
    private site.easy.to.build.crm.service.financial.PropertyFinancialSummaryService financialSummaryService;

    @Autowired
    private site.easy.to.build.crm.service.financial.UnifiedFinancialDataService unifiedFinancialDataService;

    // ===== UTILITY METHODS =====

    /**
     * Get logged-in user ID from authentication
     */
    private Integer getLoggedInUserId(Authentication auth) {
        try {
            return authenticationUtils.getLoggedInUserId(auth);
        } catch (Exception e) {
            log.warn("Failed to get user ID: {}", e.getMessage());
            return null;
        }
    }

    // ===== VIEW ROUTES =====

    /**
     * Display all blocks page
     * GET /blocks
     */
    @GetMapping
    public String viewAllBlocks(Model model, Authentication auth) {
        log.info("📋 Displaying all blocks page");

        // Get user info
        Integer userId = getLoggedInUserId(auth);
        model.addAttribute("userId", userId);

        return "blocks/all-blocks";
    }

    /**
     * Display single block details page
     * GET /blocks/{id}
     */
    @GetMapping("/{id}")
    public String viewBlockDetails(@PathVariable Long id, Model model, Authentication auth) {
        log.info("📖 Displaying block details for block {}", id);

        Optional<Block> blockOpt = blockRepository.findById(id);
        if (!blockOpt.isPresent()) {
            return "redirect:/blocks?error=Block+not+found";
        }

        Block block = blockOpt.get();
        model.addAttribute("block", block);

        // Get property count
        long propertyCount = blockService.getPropertyCount(id);
        model.addAttribute("propertyCount", propertyCount);

        // Get available capacity
        Integer availableCapacity = blockService.getAvailableCapacity(id);
        model.addAttribute("availableCapacity", availableCapacity);

        return "blocks/block-details";
    }

    /**
     * Display block edit page
     * GET /blocks/{id}/edit
     */
    @GetMapping("/{id}/edit")
    public String editBlockPage(@PathVariable Long id, Model model, Authentication auth) {
        log.info("✏️ Displaying block edit page for block {}", id);

        Optional<Block> blockOpt = blockRepository.findById(id);
        if (!blockOpt.isPresent()) {
            return "redirect:/blocks?error=Block+not+found";
        }

        model.addAttribute("block", blockOpt.get());
        return "blocks/edit-block";
    }

    /**
     * Display Assignment Centre page
     * GET /blocks/assignment-centre
     */
    @GetMapping("/assignment-centre")
    public String assignmentCentre(Model model, Authentication auth) {
        log.info("📊 Displaying Assignment Centre");

        // Get user info
        Integer userId = getLoggedInUserId(auth);
        model.addAttribute("userId", userId);

        return "blocks/assignment-centre";
    }

    // ===== API ENDPOINTS =====

    /**
     * Get all blocks (API endpoint)
     * GET /blocks/api/all
     */
    @GetMapping("/api/all")
    @ResponseBody
    public ResponseEntity<?> getAllBlocks(Authentication auth) {
        log.info("📋 Fetching all blocks via API");

        try {
            List<Block> blocks = blockRepository.findAll();

            // Convert to response DTOs
            List<Map<String, Object>> blockDTOs = blocks.stream()
                .map(this::convertBlockToDTO)
                .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "blocks", blockDTOs,
                "total", blockDTOs.size()
            ));

        } catch (Exception e) {
            log.error("❌ Failed to fetch blocks: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fetch blocks: " + e.getMessage()));
        }
    }

    /**
     * Get active blocks only
     * GET /blocks/api/active
     */
    @GetMapping("/api/active")
    @ResponseBody
    public ResponseEntity<?> getActiveBlocks(Authentication auth) {
        log.info("📋 Fetching active blocks via API");

        try {
            List<Block> blocks = blockRepository.findByIsActive("Y");

            List<Map<String, Object>> blockDTOs = blocks.stream()
                .map(this::convertBlockToDTO)
                .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "blocks", blockDTOs,
                "total", blockDTOs.size()
            ));

        } catch (Exception e) {
            log.error("❌ Failed to fetch active blocks: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fetch active blocks: " + e.getMessage()));
        }
    }

    /**
     * Create a standalone block (independent of portfolio)
     * POST /blocks/api/create
     */
    @PostMapping("/api/create")
    @ResponseBody
    public ResponseEntity<?> createStandaloneBlock(@RequestBody CreateBlockRequest request, Authentication auth) {
        log.info("🏗️ Creating standalone block '{}'", request.getName());

        try {
            // Get current user
            Integer userId = getLoggedInUserId(auth);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
            }

            // Validate request
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Block name is required"));
            }

            // Create block
            Block block = new Block();
            block.setName(request.getName().trim());
            block.setDescription(request.getDescription());
            block.setBlockType(request.getBlockType());
            block.setAddressLine1(request.getAddressLine1());
            block.setAddressLine2(request.getAddressLine2());
            block.setCity(request.getCity());
            block.setCounty(request.getCounty());
            block.setPostcode(request.getPostcode());
            block.setMaxProperties(request.getMaxProperties());
            block.setColorCode(request.getColorCode());
            block.setCreatedBy(userId.longValue());
            block.setIsActive("Y");

            block = blockRepository.save(block);

            // Auto-create block property for tracking block-level expenses
            Property blockProperty = new Property();
            blockProperty.setPropertyName(request.getName() + " - Block Property");
            blockProperty.setPropertyType("BLOCK");  // Special type for block expenses
            blockProperty.setAddressLine1(request.getAddressLine1());
            blockProperty.setAddressLine2(request.getAddressLine2());
            blockProperty.setCity(request.getCity());
            blockProperty.setCounty(request.getCounty());
            blockProperty.setPostcode(request.getPostcode());
            blockProperty.setCountryCode("UK");
            blockProperty.setStatus("ACTIVE");
            blockProperty.setIsArchived("N");
            blockProperty.setCreatedBy(userId.longValue());

            blockProperty = propertyRepository.save(blockProperty);

            // Link block property to block
            block.setBlockProperty(blockProperty);
            block = blockRepository.save(block);

            log.info("✅ Created standalone block {} with block property {} successfully",
                block.getId(), blockProperty.getId());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Block and block property created successfully",
                "block", convertBlockToDTO(block),
                "blockPropertyId", blockProperty.getId()
            ));

        } catch (Exception e) {
            log.error("❌ Failed to create standalone block: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to create block: " + e.getMessage()));
        }
    }

    /**
     * Update block details
     * PUT /blocks/api/{id}
     */
    @PutMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<?> updateBlock(@PathVariable Long id, @RequestBody UpdateBlockRequest request, Authentication auth) {
        log.info("🔄 Updating block {}", id);

        try {
            // Get current user
            Integer userId = getLoggedInUserId(auth);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
            }

            // Find block
            Optional<Block> blockOpt = blockRepository.findById(id);
            if (!blockOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            Block block = blockOpt.get();

            // Update fields
            if (request.getName() != null && !request.getName().trim().isEmpty()) {
                block.setName(request.getName().trim());
            }
            block.setDescription(request.getDescription());
            block.setBlockType(request.getBlockType());
            block.setAddressLine1(request.getAddressLine1());
            block.setAddressLine2(request.getAddressLine2());
            block.setCity(request.getCity());
            block.setCounty(request.getCounty());
            block.setPostcode(request.getPostcode());
            block.setMaxProperties(request.getMaxProperties());
            block.setColorCode(request.getColorCode());
            block.setUpdatedBy(userId.longValue());

            block = blockRepository.save(block);

            log.info("✅ Updated block {} successfully", id);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Block updated successfully",
                "block", convertBlockToDTO(block)
            ));

        } catch (Exception e) {
            log.error("❌ Failed to update block {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to update block: " + e.getMessage()));
        }
    }

    /**
     * Delete block
     * DELETE /blocks/api/{id}
     */
    @DeleteMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteBlock(@PathVariable Long id, Authentication auth) {
        log.info("🗑️ Deleting block {}", id);

        try {
            // Get current user
            Integer userId = getLoggedInUserId(auth);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
            }

            // Find block
            Optional<Block> blockOpt = blockRepository.findById(id);
            if (!blockOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            // Soft delete
            Block block = blockOpt.get();
            block.setIsActive("N");
            block.setUpdatedBy(userId.longValue());
            blockRepository.save(block);

            log.info("✅ Deleted block {} successfully", id);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Block deleted successfully"
            ));

        } catch (Exception e) {
            log.error("❌ Failed to delete block {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to delete block: " + e.getMessage()));
        }
    }

    /**
     * Get all properties in a block
     * GET /blocks/api/{id}/properties
     */
    @GetMapping("/api/{id}/properties")
    @ResponseBody
    public ResponseEntity<?> getBlockProperties(@PathVariable Long id, Authentication auth) {
        log.info("📋 Fetching properties for block {}", id);

        try {
            // Check if block exists
            Optional<Block> blockOpt = blockRepository.findById(id);
            if (!blockOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            Block block = blockOpt.get();
            // Use ordered query to respect display_order
            List<Property> properties = propertyBlockAssignmentRepository.findPropertiesByBlockIdOrdered(id);

            // Convert to DTOs
            List<Map<String, Object>> propertyDTOs = properties.stream()
                .map(this::convertPropertyToDTO)
                .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "blockId", id,
                "blockName", block.getName(),
                "properties", propertyDTOs,
                "count", propertyDTOs.size()
            ));

        } catch (Exception e) {
            log.error("❌ Failed to fetch properties for block {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fetch properties: " + e.getMessage()));
        }
    }

    /**
     * Get available properties that can be assigned to a block
     * GET /blocks/api/{id}/available-properties
     */
    @GetMapping("/api/{id}/available-properties")
    @ResponseBody
    public ResponseEntity<?> getAvailableProperties(@PathVariable Long id, Authentication auth) {
        log.info("📋 Fetching available properties for block {}", id);

        try {
            // Check if block exists
            Optional<Block> blockOpt = blockRepository.findById(id);
            if (!blockOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            // Get unassigned properties
            List<Property> unassignedProperties = propertyBlockAssignmentRepository.findUnassignedProperties();

            // Convert to DTOs
            List<Map<String, Object>> propertyDTOs = unassignedProperties.stream()
                .map(this::convertPropertyToDTO)
                .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "blockId", id,
                "availableProperties", propertyDTOs,
                "count", propertyDTOs.size()
            ));

        } catch (Exception e) {
            log.error("❌ Failed to fetch available properties for block {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fetch available properties: " + e.getMessage()));
        }
    }

    /**
     * Assign properties to a block
     * POST /blocks/api/{id}/assign-properties
     */
    @PostMapping("/api/{id}/assign-properties")
    @ResponseBody
    public ResponseEntity<?> assignPropertiesToBlock(
            @PathVariable Long id,
            @RequestBody AssignPropertiesRequest request,
            Authentication auth) {
        log.info("🏗️ Assigning {} properties to block {}", request.getPropertyIds().size(), id);

        try {
            // Get current user
            Integer userId = getLoggedInUserId(auth);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
            }

            // Check if block exists
            Optional<Block> blockOpt = blockRepository.findById(id);
            if (!blockOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            Block block = blockOpt.get();
            List<String> results = new ArrayList<>();
            int successCount = 0;
            int skippedCount = 0;

            for (Long propertyId : request.getPropertyIds()) {
                try {
                    // Check if already assigned
                    if (propertyBlockAssignmentRepository.isPropertyAssignedToBlock(propertyId, id)) {
                        results.add("Property " + propertyId + " already assigned to block");
                        skippedCount++;
                        continue;
                    }

                    // Assign property to block
                    propertyService.assignPropertyToBlock(propertyId, id, userId.longValue());
                    results.add("Property " + propertyId + " assigned successfully");
                    successCount++;

                } catch (Exception e) {
                    results.add("Failed to assign property " + propertyId + ": " + e.getMessage());
                    log.error("Failed to assign property {} to block {}: {}", propertyId, id, e.getMessage());
                }
            }

            log.info("✅ Assigned {} properties to block {} ({} successful, {} skipped)",
                request.getPropertyIds().size(), id, successCount, skippedCount);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Assignment completed",
                "blockId", id,
                "blockName", block.getName(),
                "totalRequested", request.getPropertyIds().size(),
                "successCount", successCount,
                "skippedCount", skippedCount,
                "results", results
            ));

        } catch (Exception e) {
            log.error("❌ Failed to assign properties to block {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to assign properties: " + e.getMessage()));
        }
    }

    /**
     * Remove a property from a block
     * DELETE /blocks/api/{id}/properties/{propertyId}
     */
    @DeleteMapping("/api/{id}/properties/{propertyId}")
    @ResponseBody
    public ResponseEntity<?> removePropertyFromBlock(
            @PathVariable Long id,
            @PathVariable Long propertyId,
            Authentication auth) {
        log.info("🗑️ Removing property {} from block {}", propertyId, id);

        try {
            // Get current user
            Integer userId = getLoggedInUserId(auth);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
            }

            // Check if block exists
            Optional<Block> blockOpt = blockRepository.findById(id);
            if (!blockOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            // Check if property is assigned to this block
            if (!propertyBlockAssignmentRepository.isPropertyAssignedToBlock(propertyId, id)) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Property is not assigned to this block"));
            }

            // Remove property from block
            propertyService.removePropertyFromBlock(propertyId, userId.longValue());

            log.info("✅ Removed property {} from block {}", propertyId, id);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Property removed from block successfully",
                "blockId", id,
                "propertyId", propertyId
            ));

        } catch (Exception e) {
            log.error("❌ Failed to remove property {} from block {}: {}", propertyId, id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to remove property: " + e.getMessage()));
        }
    }

    /**
     * Get assignment overview for Assignment Centre
     * GET /blocks/api/assignment-overview
     */
    @GetMapping("/api/assignment-overview")
    @ResponseBody
    public ResponseEntity<?> getAssignmentOverview(Authentication auth) {
        log.info("📊 Fetching assignment overview");

        try {
            // Get all active blocks
            List<Block> blocks = blockRepository.findByIsActive("Y");
            List<Map<String, Object>> blockDTOs = new ArrayList<>();

            for (Block block : blocks) {
                Map<String, Object> blockDTO = new HashMap<>();
                blockDTO.put("id", block.getId());
                blockDTO.put("name", block.getName());
                blockDTO.put("blockType", block.getBlockType());
                blockDTO.put("maxProperties", block.getMaxProperties());

                // Get property count
                long propertyCount = blockService.getPropertyCount(block.getId());
                blockDTO.put("propertyCount", propertyCount);

                // Get available capacity
                Integer availableCapacity = blockService.getAvailableCapacity(block.getId());
                blockDTO.put("availableCapacity", availableCapacity);

                blockDTOs.add(blockDTO);
            }

            // Get all unassigned properties
            List<Property> unassignedProperties = propertyBlockAssignmentRepository.findUnassignedProperties();
            List<Map<String, Object>> unassignedPropertyDTOs = unassignedProperties.stream()
                .map(this::convertPropertyToDTO)
                .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "blocks", blockDTOs,
                "unassignedProperties", unassignedPropertyDTOs,
                "totalBlocks", blockDTOs.size(),
                "totalUnassigned", unassignedPropertyDTOs.size()
            ));

        } catch (Exception e) {
            log.error("❌ Failed to fetch assignment overview: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fetch assignment overview: " + e.getMessage()));
        }
    }

    /**
     * Get financial summary for all properties in a block
     * GET /blocks/api/{id}/financial-summary
     */
    @GetMapping("/api/{id}/financial-summary")
    @ResponseBody
    public ResponseEntity<?> getBlockFinancialSummary(@PathVariable Long id, Authentication auth) {
        log.info("💰 Fetching financial summary for block {}", id);

        try {
            // Check if block exists
            Optional<Block> blockOpt = blockRepository.findById(id);
            if (!blockOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            // Get all properties in the block
            List<Property> properties = propertyBlockAssignmentRepository.findPropertiesByBlockIdOrdered(id);

            // Calculate financial summary for each property (last 12 months)
            List<Map<String, Object>> propertySummaries = new ArrayList<>();
            java.math.BigDecimal totalRent = java.math.BigDecimal.ZERO;
            java.math.BigDecimal totalExpenses = java.math.BigDecimal.ZERO;
            java.math.BigDecimal totalCommission = java.math.BigDecimal.ZERO;
            java.math.BigDecimal totalNet = java.math.BigDecimal.ZERO;
            java.math.BigDecimal totalArrears = java.math.BigDecimal.ZERO;

            for (Property property : properties) {
                try {
                    var summary = financialSummaryService.getPropertySummaryLast12Months(property.getId());

                    // Get arrears data from unified financial data service
                    Map<String, Object> unifiedSummary = unifiedFinancialDataService.getPropertyFinancialSummary(property);
                    java.math.BigDecimal arrears = (java.math.BigDecimal) unifiedSummary.getOrDefault("rentArrears", java.math.BigDecimal.ZERO);

                    Map<String, Object> propSummary = new HashMap<>();
                    propSummary.put("propertyId", property.getId());
                    propSummary.put("propertyName", property.getPropertyName());
                    propSummary.put("totalRent", summary.getTotalRent());
                    propSummary.put("totalExpenses", summary.getTotalExpenses());
                    propSummary.put("totalCommission", summary.getTotalCommission());
                    propSummary.put("netToOwner", summary.getNetToOwner());
                    propSummary.put("arrears", arrears);

                    propertySummaries.add(propSummary);

                    // Add to totals
                    totalRent = totalRent.add(summary.getTotalRent());
                    totalExpenses = totalExpenses.add(summary.getTotalExpenses());
                    totalCommission = totalCommission.add(summary.getTotalCommission());
                    totalNet = totalNet.add(summary.getNetToOwner());
                    totalArrears = totalArrears.add(arrears);

                } catch (Exception e) {
                    log.error("Failed to get financial summary for property {}: {}", property.getId(), e.getMessage());
                }
            }

            return ResponseEntity.ok(Map.of(
                "success", true,
                "blockId", id,
                "blockName", blockOpt.get().getName(),
                "period", "Last 12 Months",
                "propertySummaries", propertySummaries,
                "totals", Map.of(
                    "totalRent", totalRent,
                    "totalExpenses", totalExpenses,
                    "totalCommission", totalCommission,
                    "netToOwner", totalNet,
                    "totalArrears", totalArrears
                )
            ));

        } catch (Exception e) {
            log.error("❌ Failed to fetch financial summary for block {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fetch financial summary: " + e.getMessage()));
        }
    }

    /**
     * DEBUG: Get all properties to see what's in the system
     * GET /blocks/api/debug/all-properties
     */
    @GetMapping("/api/debug/all-properties")
    @ResponseBody
    public ResponseEntity<?> debugAllProperties(Authentication auth) {
        log.info("🐛 DEBUG: Fetching all properties");

        try {
            List<Property> allProperties = propertyRepository.findAll();
            List<Map<String, Object>> propertyDTOs = new ArrayList<>();

            for (Property p : allProperties) {
                Map<String, Object> dto = new HashMap<>();
                dto.put("id", p.getId());
                dto.put("name", p.getPropertyName());
                dto.put("type", p.getPropertyType());
                dto.put("isArchived", p.getIsArchived());

                // Check if assigned to block
                boolean isAssigned = propertyBlockAssignmentRepository.isPropertyAssignedToBlock(p.getId(), null);
                Optional<Block> block = propertyBlockAssignmentRepository.findBlockByPropertyId(p.getId());
                dto.put("isAssignedToBlock", block.isPresent());
                if (block.isPresent()) {
                    dto.put("blockName", block.get().getName());
                }

                propertyDTOs.add(dto);
            }

            return ResponseEntity.ok(Map.of(
                "success", true,
                "totalProperties", allProperties.size(),
                "properties", propertyDTOs
            ));

        } catch (Exception e) {
            log.error("❌ Failed to fetch all properties: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fetch properties: " + e.getMessage()));
        }
    }

    // ===== UTILITY METHODS =====

    private Map<String, Object> convertBlockToDTO(Block block) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", block.getId());
        dto.put("name", block.getName());
        dto.put("description", block.getDescription());
        dto.put("blockType", block.getBlockType());
        dto.put("addressLine1", block.getAddressLine1());
        dto.put("addressLine2", block.getAddressLine2());
        dto.put("city", block.getCity());
        dto.put("county", block.getCounty());
        dto.put("postcode", block.getPostcode());
        dto.put("countryCode", block.getCountryCode());
        dto.put("payPropTags", block.getPayPropTags());
        dto.put("payPropTagNames", block.getPayPropTagNames());
        dto.put("syncStatus", block.getSyncStatus());
        dto.put("lastSyncAt", block.getLastSyncAt());
        dto.put("maxProperties", block.getMaxProperties());
        dto.put("colorCode", block.getColorCode());
        dto.put("displayOrder", block.getDisplayOrder());
        dto.put("isActive", block.getIsActive());
        dto.put("createdAt", block.getCreatedAt());
        dto.put("updatedAt", block.getUpdatedAt());

        // Add block property info
        if (block.getBlockProperty() != null) {
            Property blockProp = block.getBlockProperty();
            Map<String, Object> blockPropertyInfo = new HashMap<>();
            blockPropertyInfo.put("id", blockProp.getId());
            blockPropertyInfo.put("name", blockProp.getPropertyName());
            blockPropertyInfo.put("status", blockProp.getStatus());
            dto.put("blockProperty", blockPropertyInfo);
        } else {
            dto.put("blockProperty", null);
        }

        // Add property count
        try {
            long propertyCount = blockService.getPropertyCount(block.getId());
            dto.put("propertyCount", propertyCount);
        } catch (Exception e) {
            dto.put("propertyCount", 0);
        }

        // Add available capacity
        try {
            Integer availableCapacity = blockService.getAvailableCapacity(block.getId());
            dto.put("availableCapacity", availableCapacity);
        } catch (Exception e) {
            dto.put("availableCapacity", null);
        }

        return dto;
    }

    private Map<String, Object> convertPropertyToDTO(Property property) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", property.getId());
        dto.put("propertyName", property.getPropertyName());
        dto.put("propertyType", property.getPropertyType());
        dto.put("addressLine1", property.getAddressLine1());
        dto.put("addressLine2", property.getAddressLine2());
        dto.put("city", property.getCity());
        dto.put("county", property.getCounty());
        dto.put("postcode", property.getPostcode());
        dto.put("countryCode", property.getCountryCode());
        dto.put("status", property.getStatus());
        dto.put("isArchived", property.getIsArchived());

        // Add block info if assigned
        try {
            Optional<Block> block = propertyBlockAssignmentRepository.findBlockByPropertyId(property.getId());
            if (block.isPresent()) {
                dto.put("blockId", block.get().getId());
                dto.put("blockName", block.get().getName());
            }
        } catch (Exception e) {
            // Ignore - block info is optional
        }

        return dto;
    }

    // ===== REQUEST CLASSES =====

    public static class CreateBlockRequest {
        private String name;
        private String description;
        private BlockType blockType;
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String county;
        private String postcode;
        private Integer maxProperties;
        private String colorCode;

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public BlockType getBlockType() { return blockType; }
        public void setBlockType(BlockType blockType) { this.blockType = blockType; }
        public String getAddressLine1() { return addressLine1; }
        public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }
        public String getAddressLine2() { return addressLine2; }
        public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getCounty() { return county; }
        public void setCounty(String county) { this.county = county; }
        public String getPostcode() { return postcode; }
        public void setPostcode(String postcode) { this.postcode = postcode; }
        public Integer getMaxProperties() { return maxProperties; }
        public void setMaxProperties(Integer maxProperties) { this.maxProperties = maxProperties; }
        public String getColorCode() { return colorCode; }
        public void setColorCode(String colorCode) { this.colorCode = colorCode; }
    }

    public static class UpdateBlockRequest {
        private String name;
        private String description;
        private BlockType blockType;
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String county;
        private String postcode;
        private Integer maxProperties;
        private String colorCode;

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public BlockType getBlockType() { return blockType; }
        public void setBlockType(BlockType blockType) { this.blockType = blockType; }
        public String getAddressLine1() { return addressLine1; }
        public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }
        public String getAddressLine2() { return addressLine2; }
        public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getCounty() { return county; }
        public void setCounty(String county) { this.county = county; }
        public String getPostcode() { return postcode; }
        public void setPostcode(String postcode) { this.postcode = postcode; }
        public Integer getMaxProperties() { return maxProperties; }
        public void setMaxProperties(Integer maxProperties) { this.maxProperties = maxProperties; }
        public String getColorCode() { return colorCode; }
        public void setColorCode(String colorCode) { this.colorCode = colorCode; }
    }

    public static class AssignPropertiesRequest {
        private List<Long> propertyIds;

        // Getters and setters
        public List<Long> getPropertyIds() { return propertyIds; }
        public void setPropertyIds(List<Long> propertyIds) { this.propertyIds = propertyIds; }
    }
}

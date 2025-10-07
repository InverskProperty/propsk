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

            log.info("✅ Created standalone block {} successfully", block.getId());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Block created successfully",
                "block", convertBlockToDTO(block)
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
}

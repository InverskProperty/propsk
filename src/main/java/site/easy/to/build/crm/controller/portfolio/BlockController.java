// BlockController.java - REST API endpoints for block management
package site.easy.to.build.crm.controller.portfolio;

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
import site.easy.to.build.crm.service.portfolio.PortfolioBlockService;
import site.easy.to.build.crm.service.portfolio.PortfolioBlockService.*;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for block management operations
 * Provides REST API endpoints for CRUD operations on blocks
 */
@Controller
@RequestMapping("/portfolio/internal/blocks")
public class BlockController extends PortfolioControllerBase {
    
    private static final Logger log = LoggerFactory.getLogger(BlockController.class);
    
    // PortfolioBlockService is inherited from PortfolioControllerBase as portfolioBlockService
    
    // ===== BLOCK CRUD OPERATIONS =====
    
    /**
     * Create a new block in a portfolio
     * POST /portfolio/blocks
     */
    @PostMapping
    @ResponseBody
    public ResponseEntity<?> createBlock(@RequestBody CreateBlockRequest request, Authentication auth) {
        log.info("🏗️ Creating block '{}' in portfolio {}", request.getName(), request.getPortfolioId());
        
        try {
            // Get current user
            Integer userId = getLoggedInUserId(auth);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
            }
            
            // Validate request
            if (request.getPortfolioId() == null || request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Portfolio ID and block name are required"));
            }
            
            // Check if service is available
            if (portfolioBlockService == null) {
                log.error("❌ PortfolioBlockService is not available");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Block service is not available"));
            }
            
            // Create block
            Block block = portfolioBlockService.createBlock(
                request.getPortfolioId(),
                request.getName().trim(),
                request.getDescription(),
                request.getBlockType(),
                userId.longValue()
            );
            
            log.info("✅ Created block {} successfully", block.getId());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Block created successfully",
                "block", convertToBlockResponse(block)
            ));
            
        } catch (IllegalArgumentException e) {
            log.warn("❌ Invalid block creation request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("❌ Failed to create block: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to create block: " + e.getMessage()));
        }
    }
    
    /**
     * Get block by ID
     * GET /portfolio/blocks/{id}
     */
    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> getBlock(@PathVariable Long id, Authentication auth) {
        log.info("📖 Getting block {}", id);
        
        try {
            Optional<Block> blockOpt = portfolioBlockService.findById(id);
            if (!blockOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }
            
            Block block = blockOpt.get();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "block", convertToBlockResponse(block)
            ));
            
        } catch (Exception e) {
            log.error("❌ Failed to get block {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get block: " + e.getMessage()));
        }
    }
    
    /**
     * Update block
     * PUT /portfolio/blocks/{id}
     */
    @PutMapping("/{id}")
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
            
            // Validate request
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Block name is required"));
            }
            
            // Update block
            Block block = portfolioBlockService.updateBlock(
                id,
                request.getName().trim(),
                request.getDescription(),
                request.getBlockType(),
                userId.longValue()
            );
            
            log.info("✅ Updated block {} successfully", block.getId());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Block updated successfully",
                "block", convertToBlockResponse(block)
            ));
            
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("❌ Invalid block update request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("❌ Failed to update block {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to update block: " + e.getMessage()));
        }
    }
    
    /**
     * Delete block
     * DELETE /portfolio/blocks/{id}
     */
    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteBlock(@PathVariable Long id, 
                                       @RequestParam(defaultValue = "MOVE_TO_PORTFOLIO_ONLY") String reassignmentOption,
                                       Authentication auth) {
        log.info("🗑️ Deleting block {} with option {}", id, reassignmentOption);
        
        try {
            // Get current user
            Integer userId = getLoggedInUserId(auth);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
            }
            
            // Parse reassignment option
            PropertyReassignmentOption option;
            try {
                option = PropertyReassignmentOption.valueOf(reassignmentOption.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid reassignment option: " + reassignmentOption));
            }
            
            // Delete block
            BlockDeletionResult result = portfolioBlockService.deleteBlock(id, userId.longValue(), option);
            
            if (result.isSuccess()) {
                log.info("✅ Deleted block {} successfully", id);
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", result.getMessage(),
                    "propertiesReassigned", result.getPropertiesReassigned()
                ));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", result.getMessage()));
            }
            
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("❌ Invalid block deletion request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("❌ Failed to delete block {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to delete block: " + e.getMessage()));
        }
    }
    
    // ===== PORTFOLIO BLOCK OPERATIONS =====
    
    /**
     * Get all blocks in a portfolio
     * GET /portfolio/blocks/portfolio/{portfolioId}
     */
    @GetMapping("/portfolio/{portfolioId}")
    @ResponseBody
    public ResponseEntity<?> getBlocksByPortfolio(@PathVariable Long portfolioId, Authentication auth) {
        log.info("📖 Getting blocks for portfolio {}", portfolioId);
        
        try {
            List<Block> blocks = portfolioBlockService.getBlocksByPortfolio(portfolioId);
            
            List<Map<String, Object>> blockResponses = blocks.stream()
                .map(this::convertToBlockResponse)
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "blocks", blockResponses,
                "total", blockResponses.size()
            ));
            
        } catch (Exception e) {
            log.error("❌ Failed to get blocks for portfolio {}: {}", portfolioId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get blocks: " + e.getMessage()));
        }
    }
    
    /**
     * Get blocks with property counts
     * GET /portfolio/blocks/portfolio/{portfolioId}/with-counts
     */
    @GetMapping("/portfolio/{portfolioId}/with-counts")
    @ResponseBody
    public ResponseEntity<?> getBlocksWithPropertyCounts(@PathVariable Long portfolioId, Authentication auth) {
        log.info("📊 Getting blocks with property counts for portfolio {}", portfolioId);
        
        try {
            List<BlockWithPropertyCount> blocksWithCounts = portfolioBlockService.getBlocksWithPropertyCounts(portfolioId);
            
            List<Map<String, Object>> blockResponses = blocksWithCounts.stream()
                .map(this::convertToBlockWithCountResponse)
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "blocks", blockResponses,
                "total", blockResponses.size()
            ));
            
        } catch (Exception e) {
            log.error("❌ Failed to get blocks with counts for portfolio {}: {}", portfolioId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get blocks with counts: " + e.getMessage()));
        }
    }
    
    // ===== BLOCK ORDERING =====
    
    /**
     * Reorder blocks in a portfolio
     * POST /portfolio/blocks/portfolio/{portfolioId}/reorder
     */
    @PostMapping("/portfolio/{portfolioId}/reorder")
    @ResponseBody
    public ResponseEntity<?> reorderBlocks(@PathVariable Long portfolioId, 
                                         @RequestBody Map<String, Integer> blockOrder, 
                                         Authentication auth) {
        log.info("🔄 Reordering blocks in portfolio {}", portfolioId);
        
        try {
            // Get current user
            Integer userId = getLoggedInUserId(auth);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
            }
            
            // Convert string keys to Long keys
            Map<Long, Integer> blockOrderMap = new HashMap<>();
            for (Map.Entry<String, Integer> entry : blockOrder.entrySet()) {
                try {
                    Long blockId = Long.parseLong(entry.getKey());
                    blockOrderMap.put(blockId, entry.getValue());
                } catch (NumberFormatException e) {
                    return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid block ID: " + entry.getKey()));
                }
            }
            
            // Reorder blocks
            portfolioBlockService.reorderBlocks(portfolioId, blockOrderMap, userId.longValue());
            
            log.info("✅ Reordered blocks in portfolio {} successfully", portfolioId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Blocks reordered successfully"
            ));
            
        } catch (Exception e) {
            log.error("❌ Failed to reorder blocks in portfolio {}: {}", portfolioId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to reorder blocks: " + e.getMessage()));
        }
    }
    
    /**
     * Move block up in order
     * POST /portfolio/blocks/{id}/move-up
     */
    @PostMapping("/{id}/move-up")
    @ResponseBody
    public ResponseEntity<?> moveBlockUp(@PathVariable Long id, Authentication auth) {
        log.info("⬆️ Moving block {} up", id);
        
        try {
            // Get current user
            Integer userId = getLoggedInUserId(auth);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
            }
            
            portfolioBlockService.moveBlockUp(id, userId.longValue());
            
            log.info("✅ Moved block {} up successfully", id);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Block moved up successfully"
            ));
            
        } catch (IllegalStateException e) {
            log.warn("❌ Cannot move block {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("❌ Failed to move block {} up: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to move block up: " + e.getMessage()));
        }
    }
    
    /**
     * Move block down in order
     * POST /portfolio/blocks/{id}/move-down
     */
    @PostMapping("/{id}/move-down")
    @ResponseBody
    public ResponseEntity<?> moveBlockDown(@PathVariable Long id, Authentication auth) {
        log.info("⬇️ Moving block {} down", id);
        
        try {
            // Get current user
            Integer userId = getLoggedInUserId(auth);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
            }
            
            portfolioBlockService.moveBlockDown(id, userId.longValue());
            
            log.info("✅ Moved block {} down successfully", id);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Block moved down successfully"
            ));
            
        } catch (IllegalStateException e) {
            log.warn("❌ Cannot move block {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("❌ Failed to move block {} down: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to move block down: " + e.getMessage()));
        }
    }
    
    // ===== BLOCK CAPACITY MANAGEMENT =====
    
    /**
     * Set block capacity
     * POST /portfolio/blocks/{id}/capacity
     */
    @PostMapping("/{id}/capacity")
    @ResponseBody
    public ResponseEntity<?> setBlockCapacity(@PathVariable Long id, 
                                            @RequestParam Integer maxProperties, 
                                            Authentication auth) {
        log.info("🏗️ Setting capacity for block {} to {}", id, maxProperties);
        
        try {
            // Get current user
            Integer userId = getLoggedInUserId(auth);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
            }
            
            if (maxProperties != null && maxProperties < 0) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Capacity cannot be negative"));
            }
            
            portfolioBlockService.setBlockCapacity(id, maxProperties, userId.longValue());
            
            log.info("✅ Set capacity for block {} successfully", id);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Block capacity updated successfully",
                "maxProperties", maxProperties
            ));
            
        } catch (IllegalStateException e) {
            log.warn("❌ Cannot set capacity for block {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("❌ Failed to set capacity for block {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to set block capacity: " + e.getMessage()));
        }
    }
    
    /**
     * Get block statistics for a portfolio
     * GET /portfolio/blocks/portfolio/{portfolioId}/statistics
     */
    @GetMapping("/portfolio/{portfolioId}/statistics")
    @ResponseBody
    public ResponseEntity<?> getBlockStatistics(@PathVariable Long portfolioId, Authentication auth) {
        log.info("📊 Getting block statistics for portfolio {}", portfolioId);
        
        try {
            BlockStatistics stats = portfolioBlockService.getBlockStatistics(portfolioId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "statistics", Map.of(
                    "totalBlocks", stats.getTotalBlocks(),
                    "activeBlocks", stats.getActiveBlocks(),
                    "blocksWithProperties", stats.getBlocksWithProperties(),
                    "emptyBlocks", stats.getEmptyBlocks(),
                    "totalProperties", stats.getTotalProperties(),
                    "averagePropertiesPerBlock", stats.getAveragePropertiesPerBlock()
                )
            ));
            
        } catch (Exception e) {
            log.error("❌ Failed to get block statistics for portfolio {}: {}", portfolioId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get block statistics: " + e.getMessage()));
        }
    }
    
    // ===== BLOCK VALIDATION =====
    
    /**
     * Validate block creation
     * POST /portfolio/blocks/validate
     */
    @PostMapping("/validate")
    @ResponseBody
    public ResponseEntity<?> validateBlockCreation(@RequestBody ValidateBlockRequest request, Authentication auth) {
        log.info("✅ Validating block creation for portfolio {}", request.getPortfolioId());
        
        try {
            BlockValidationResult result = portfolioBlockService.validateBlockCreation(
                request.getPortfolioId(), 
                request.getBlockName()
            );
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "valid", result.isValid(),
                "message", result.isValid() ? "Block name is valid" : result.getErrorMessage()
            ));
            
        } catch (Exception e) {
            log.error("❌ Failed to validate block creation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to validate block: " + e.getMessage()));
        }
    }
    
    // ===== UTILITY METHODS =====
    
    private Map<String, Object> convertToBlockResponse(Block block) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", block.getId());
        response.put("name", block.getName());
        response.put("description", block.getDescription());
        response.put("blockType", block.getBlockType());
        response.put("portfolioId", block.getPortfolio() != null ? block.getPortfolio().getId() : null);
        response.put("portfolioName", block.getPortfolio() != null ? block.getPortfolio().getName() : null);
        response.put("payPropTagNames", block.getPayPropTagNames());
        response.put("payPropTags", block.getPayPropTags());
        response.put("syncStatus", block.getSyncStatus());
        response.put("lastSyncAt", block.getLastSyncAt());
        response.put("maxProperties", block.getMaxProperties());
        response.put("displayOrder", block.getDisplayOrder());
        response.put("isActive", block.getIsActive());
        response.put("createdAt", block.getCreatedAt());
        response.put("updatedAt", block.getUpdatedAt());
        
        // Add property count
        long propertyCount = portfolioBlockService.getPropertyCount(block.getId());
        response.put("propertyCount", propertyCount);
        
        // Add available capacity
        Integer availableCapacity = portfolioBlockService.getAvailableCapacity(block.getId());
        response.put("availableCapacity", availableCapacity);
        
        return response;
    }
    
    private Map<String, Object> convertToBlockWithCountResponse(BlockWithPropertyCount blockWithCount) {
        Map<String, Object> response = convertToBlockResponse(blockWithCount.getBlock());
        response.put("propertyCount", blockWithCount.getPropertyCount());
        response.put("maxProperties", blockWithCount.getMaxProperties());
        
        if (blockWithCount.getMaxProperties() != null) {
            response.put("availableCapacity", 
                Math.max(0, blockWithCount.getMaxProperties() - blockWithCount.getPropertyCount()));
            response.put("capacityUtilization", 
                (blockWithCount.getPropertyCount() * 100.0) / blockWithCount.getMaxProperties());
        }
        
        return response;
    }
    
    // ===== REQUEST CLASSES =====
    
    public static class CreateBlockRequest {
        private Long portfolioId;
        private String name;
        private String description;
        private BlockType blockType;
        
        // Getters and setters
        public Long getPortfolioId() { return portfolioId; }
        public void setPortfolioId(Long portfolioId) { this.portfolioId = portfolioId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public BlockType getBlockType() { return blockType; }
        public void setBlockType(BlockType blockType) { this.blockType = blockType; }
    }
    
    public static class UpdateBlockRequest {
        private String name;
        private String description;
        private BlockType blockType;
        
        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public BlockType getBlockType() { return blockType; }
        public void setBlockType(BlockType blockType) { this.blockType = blockType; }
    }
    
    public static class ValidateBlockRequest {
        private Long portfolioId;
        private String blockName;
        
        // Getters and setters
        public Long getPortfolioId() { return portfolioId; }
        public void setPortfolioId(Long portfolioId) { this.portfolioId = portfolioId; }
        public String getBlockName() { return blockName; }
        public void setBlockName(String blockName) { this.blockName = blockName; }
    }
}
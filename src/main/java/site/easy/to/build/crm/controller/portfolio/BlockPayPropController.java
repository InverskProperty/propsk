// BlockPayPropController.java - REST API endpoints for block PayProp sync operations
package site.easy.to.build.crm.controller.portfolio;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.service.payprop.PayPropBlockSyncService;
import site.easy.to.build.crm.service.payprop.PayPropPortfolioSyncService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for block PayProp sync operations
 * Provides REST API endpoints for syncing blocks to PayProp
 */
@Controller
@RequestMapping("/portfolio/internal/blocks/payprop")
public class BlockPayPropController extends PortfolioControllerBase {
    
    private static final Logger log = LoggerFactory.getLogger(BlockPayPropController.class);
    
    @Autowired(required = false)
    private PayPropBlockSyncService blockSyncService;
    
    @Autowired(required = false) 
    private PayPropPortfolioSyncService portfolioSyncService;
    
    // ===== SINGLE BLOCK SYNC OPERATIONS =====
    
    /**
     * Sync a single block to PayProp
     * POST /portfolio/internal/blocks/payprop/{blockId}/sync
     */
    @PostMapping("/{blockId}/sync")
    @ResponseBody
    public ResponseEntity<?> syncBlockToPayProp(@PathVariable Long blockId, Authentication auth) {
        log.info("🏗️ Syncing block {} to PayProp", blockId);
        
        try {
            if (blockSyncService == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "PayProp sync service not available"));
            }
            
            // Validate block exists
            Optional<Block> blockOpt = portfolioBlockService.findById(blockId);
            if (!blockOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }
            
            Block block = blockOpt.get();

            // Check permissions - standalone blocks (no portfolio) can be synced by any authenticated user
            if (block.getPortfolio() != null) {
                Long portfolioId = block.getPortfolio().getId();
                if (!canUserEditPortfolio(portfolioId, auth)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied"));
                }
            } else {
                // For standalone blocks, just verify user is authenticated
                Integer userId = getLoggedInUserId(auth);
                if (userId == null) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Authentication required"));
                }
            }

            // Sync the block
            PayPropBlockSyncService.BlockSyncResult result = blockSyncService.syncBlockToPayProp(blockId);
            
            if (result.isSuccess()) {
                log.info("✅ Block {} synced successfully", blockId);
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", result.getMessage(),
                    "blockId", blockId,
                    "tagId", result.getTagId(),
                    "propertiesSynced", result.getPropertiesSynced()
                ));
            } else {
                log.warn("❌ Block {} sync failed: {}", blockId, result.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "success", false,
                        "error", result.getMessage(),
                        "blockId", blockId
                    ));
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to sync block {} to PayProp: {}", blockId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to sync block: " + e.getMessage()));
        }
    }
    
    // ===== BATCH SYNC OPERATIONS =====
    
    /**
     * Sync all blocks in a portfolio to PayProp
     * POST /portfolio/internal/blocks/payprop/portfolio/{portfolioId}/sync-all
     */
    @PostMapping("/portfolio/{portfolioId}/sync-all")
    @ResponseBody
    public ResponseEntity<?> syncAllBlocksInPortfolio(@PathVariable Long portfolioId, Authentication auth) {
        log.info("🏗️ Syncing all blocks in portfolio {} to PayProp", portfolioId);
        
        try {
            if (blockSyncService == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "PayProp sync service not available"));
            }
            
            // Check permissions
            if (!canUserEditPortfolio(portfolioId, auth)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied"));
            }
            
            // Validate portfolio exists
            Portfolio portfolio = portfolioService.findById(portfolioId);
            if (portfolio == null) {
                return ResponseEntity.notFound().build();
            }
            
            // Sync all blocks in portfolio
            PayPropBlockSyncService.BatchBlockSyncResult result = 
                blockSyncService.syncAllBlocksInPortfolio(portfolioId);
            
            log.info("✅ Portfolio {} block sync completed: {} succeeded, {} failed, {} skipped", 
                    portfolioId, result.getSuccessCount(), result.getFailureCount(), result.getSkippedCount());
            
            return ResponseEntity.ok(Map.of(
                "success", result.isSuccess(),
                "message", result.getMessage(),
                "portfolioId", portfolioId,
                "portfolioName", portfolio.getName(),
                "successCount", result.getSuccessCount(),
                "failureCount", result.getFailureCount(),
                "skippedCount", result.getSkippedCount(),
                "successDetails", result.getSuccessDetails(),
                "errors", result.getErrors()
            ));
            
        } catch (Exception e) {
            log.error("❌ Failed to sync blocks in portfolio {} to PayProp: {}", portfolioId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to sync portfolio blocks: " + e.getMessage()));
        }
    }
    
    /**
     * Sync all blocks that need sync (global operation)
     * POST /portfolio/internal/blocks/payprop/sync-needed
     */
    @PostMapping("/sync-needed")
    @ResponseBody
    public ResponseEntity<?> syncBlocksNeedingSync(Authentication auth) {
        log.info("🔄 Syncing all blocks needing sync to PayProp");
        
        try {
            if (blockSyncService == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "PayProp sync service not available"));
            }
            
            // For global operations, check if user has manager/employee role
            Integer userId = getLoggedInUserId(auth);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
            }
            
            // TODO: Add proper role-based authorization for global operations
            // For now, allow any authenticated user
            
            // Sync blocks needing sync
            PayPropBlockSyncService.BatchBlockSyncResult result = 
                blockSyncService.syncBlocksNeedingSync();
            
            log.info("✅ Global block sync completed: {} succeeded, {} failed", 
                    result.getSuccessCount(), result.getFailureCount());
            
            return ResponseEntity.ok(Map.of(
                "success", result.isSuccess(),
                "message", result.getMessage(),
                "successCount", result.getSuccessCount(),
                "failureCount", result.getFailureCount(),
                "successDetails", result.getSuccessDetails(),
                "errors", result.getErrors()
            ));
            
        } catch (Exception e) {
            log.error("❌ Failed to sync blocks needing sync: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to sync blocks: " + e.getMessage()));
        }
    }
    
    // ===== HIERARCHICAL SYNC OPERATIONS =====
    
    /**
     * Sync portfolio with all its blocks (hierarchical sync)
     * POST /portfolio/internal/blocks/payprop/portfolio/{portfolioId}/sync-hierarchical
     */
    @PostMapping("/portfolio/{portfolioId}/sync-hierarchical")
    @ResponseBody
    public ResponseEntity<?> syncPortfolioWithBlocks(@PathVariable Long portfolioId, Authentication auth) {
        log.info("🏗️ Starting hierarchical sync for portfolio {} with blocks", portfolioId);
        
        try {
            if (portfolioSyncService == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "PayProp portfolio sync service not available"));
            }
            
            // Check permissions
            if (!canUserEditPortfolio(portfolioId, auth)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied"));
            }
            
            // Validate portfolio exists
            Portfolio portfolio = portfolioService.findById(portfolioId);
            if (portfolio == null) {
                return ResponseEntity.notFound().build();
            }
            
            Integer userId = getLoggedInUserId(auth);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
            }
            
            // Use the hierarchical sync from PayPropPortfolioSyncService
            site.easy.to.build.crm.service.payprop.SyncResult result = 
                portfolioSyncService.syncPortfolioWithBlocks(portfolioId, (long) userId);
            
            log.info("✅ Hierarchical sync for portfolio {} completed: {}", portfolioId, result.getMessage());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("portfolioId", portfolioId);
            response.put("portfolioName", portfolio.getName());
            
            if (result.getDetails() != null) {
                response.putAll((Map<String, Object>) result.getDetails());
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Failed to sync portfolio {} hierarchically: {}", portfolioId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to sync portfolio hierarchically: " + e.getMessage()));
        }
    }
    
    // ===== BLOCK STATUS AND MONITORING =====
    
    /**
     * Get blocks needing sync for a portfolio
     * GET /portfolio/internal/blocks/payprop/portfolio/{portfolioId}/needing-sync
     */
    @GetMapping("/portfolio/{portfolioId}/needing-sync")
    @ResponseBody
    public ResponseEntity<?> getBlocksNeedingSyncInPortfolio(@PathVariable Long portfolioId, Authentication auth) {
        log.info("📊 Getting blocks needing sync in portfolio {}", portfolioId);
        
        try {
            // Check permissions
            if (!canUserEditPortfolio(portfolioId, auth)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied"));
            }
            
            // Get all blocks in portfolio
            List<Block> allBlocks = portfolioBlockService.getBlocksByPortfolio(portfolioId);
            
            // Filter blocks needing sync
            List<Block> blocksNeedingSync = allBlocks.stream()
                .filter(block -> block.getSyncStatus() == SyncStatus.pending || 
                               block.getSyncStatus() == SyncStatus.failed ||
                               (block.getPayPropTagNames() != null && !block.getPayPropTagNames().trim().isEmpty() &&
                                (block.getPayPropTags() == null || block.getPayPropTags().trim().isEmpty())))
                .collect(Collectors.toList());
            
            List<Map<String, Object>> blockData = blocksNeedingSync.stream()
                .map(block -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("id", block.getId());
                    data.put("name", block.getName());
                    data.put("syncStatus", block.getSyncStatus());
                    data.put("payPropTagNames", block.getPayPropTagNames());
                    data.put("payPropTags", block.getPayPropTags());
                    data.put("lastSyncAt", block.getLastSyncAt());
                    data.put("propertyCount", portfolioBlockService.getPropertyCount(block.getId()));
                    return data;
                })
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "portfolioId", portfolioId,
                "blocksNeedingSync", blockData,
                "totalBlocksNeedingSync", blocksNeedingSync.size(),
                "totalBlocks", allBlocks.size()
            ));
            
        } catch (Exception e) {
            log.error("❌ Failed to get blocks needing sync for portfolio {}: {}", portfolioId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get blocks needing sync: " + e.getMessage()));
        }
    }
    
    /**
     * Get global blocks needing sync
     * GET /portfolio/internal/blocks/payprop/needing-sync
     */
    @GetMapping("/needing-sync")
    @ResponseBody
    public ResponseEntity<?> getBlocksNeedingSync(Authentication auth) {
        log.info("📊 Getting all blocks needing sync");
        
        try {
            Integer userId = getLoggedInUserId(auth);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
            }
            
            if (portfolioBlockService == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Portfolio block service not available"));
            }
            
            // Get blocks needing sync
            List<Block> blocksNeedingSync = portfolioBlockService.getBlocksNeedingSync();
            
            // Group by portfolio for better organization
            Map<String, List<Map<String, Object>>> blocksByPortfolio = new HashMap<>();
            
            for (Block block : blocksNeedingSync) {
                String portfolioKey = block.getPortfolio() != null ?
                    String.format("%s (ID: %d)", block.getPortfolio().getName(), block.getPortfolio().getId()) :
                    "Standalone Blocks (No Portfolio)";

                Map<String, Object> blockData = new HashMap<>();
                blockData.put("id", block.getId());
                blockData.put("name", block.getName());
                blockData.put("syncStatus", block.getSyncStatus());
                blockData.put("payPropTagNames", block.getPayPropTagNames());
                blockData.put("payPropTags", block.getPayPropTags());
                blockData.put("lastSyncAt", block.getLastSyncAt());
                blockData.put("propertyCount", portfolioBlockService.getPropertyCount(block.getId()));
                blockData.put("portfolioId", block.getPortfolio() != null ? block.getPortfolio().getId() : null);

                blocksByPortfolio.computeIfAbsent(portfolioKey, k -> new ArrayList<>()).add(blockData);
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "totalBlocksNeedingSync", blocksNeedingSync.size(),
                "blocksByPortfolio", blocksByPortfolio
            ));
            
        } catch (Exception e) {
            log.error("❌ Failed to get blocks needing sync: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get blocks needing sync: " + e.getMessage()));
        }
    }
    
    /**
     * Get block sync status
     * GET /portfolio/internal/blocks/payprop/{blockId}/status
     */
    @GetMapping("/{blockId}/status")
    @ResponseBody
    public ResponseEntity<?> getBlockSyncStatus(@PathVariable Long blockId, Authentication auth) {
        log.info("📊 Getting sync status for block {}", blockId);
        
        try {
            // Validate block exists
            Optional<Block> blockOpt = portfolioBlockService.findById(blockId);
            if (!blockOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }
            
            Block block = blockOpt.get();

            // Check permissions - standalone blocks (no portfolio) can be accessed by any authenticated user
            if (block.getPortfolio() != null) {
                Long portfolioId = block.getPortfolio().getId();
                if (!canUserEditPortfolio(portfolioId, auth)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied"));
                }
            } else {
                // For standalone blocks, just verify user is authenticated
                Integer userId = getLoggedInUserId(auth);
                if (userId == null) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Authentication required"));
                }
            }

            long propertyCount = portfolioBlockService.getPropertyCount(blockId);
            boolean needsSync = block.getSyncStatus() == SyncStatus.pending ||
                              block.getSyncStatus() == SyncStatus.failed ||
                              (block.getPayPropTagNames() != null && !block.getPayPropTagNames().trim().isEmpty() &&
                               (block.getPayPropTags() == null || block.getPayPropTags().trim().isEmpty()));

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("blockId", blockId);
            response.put("blockName", block.getName());
            response.put("portfolioId", block.getPortfolio() != null ? block.getPortfolio().getId() : null);
            response.put("portfolioName", block.getPortfolio() != null ? block.getPortfolio().getName() : "Standalone Block");
            response.put("syncStatus", block.getSyncStatus());
            response.put("payPropTagNames", block.getPayPropTagNames());
            response.put("payPropTags", block.getPayPropTags());
            response.put("lastSyncAt", block.getLastSyncAt());
            response.put("propertyCount", propertyCount);
            response.put("needsSync", needsSync);
            response.put("canSync", blockSyncService != null);
            response.put("isActive", block.getIsActive());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Failed to get sync status for block {}: {}", blockId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get block sync status: " + e.getMessage()));
        }
    }
}
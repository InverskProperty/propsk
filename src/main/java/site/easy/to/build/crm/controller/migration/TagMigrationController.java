package site.easy.to.build.crm.controller.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import site.easy.to.build.crm.service.migration.TagNamespaceMigrationService;
import site.easy.to.build.crm.service.migration.TagNamespaceMigrationService.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for tag namespace migration operations
 * Provides endpoints to migrate existing tags to namespaced format
 */
@RestController
@RequestMapping("/api/migration/tags")
public class TagMigrationController {
    
    private static final Logger log = LoggerFactory.getLogger(TagMigrationController.class);
    
    @Autowired
    private TagNamespaceMigrationService migrationService;
    
    /**
     * Check migration status
     */
    @GetMapping("/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getMigrationStatus() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            MigrationStatus status = migrationService.checkMigrationStatus();
            
            response.put("success", true);
            response.put("portfoliosNeedingMigration", status.getPortfoliosNeedingMigration());
            response.put("blocksNeedingMigration", status.getBlocksNeedingMigration());
            response.put("totalNeedingMigration", status.getTotalNeedingMigration());
            response.put("migrationNeeded", status.isMigrationNeeded());
            
            if (status.isMigrationNeeded()) {
                response.put("message", String.format("Migration needed: %d portfolios and %d blocks require tag migration", 
                    status.getPortfoliosNeedingMigration(), status.getBlocksNeedingMigration()));
            } else {
                response.put("message", "All tags are already in namespaced format");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error checking migration status: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Error checking migration status: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Migrate portfolio tags only
     */
    @PostMapping("/portfolios")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> migratePortfolioTags(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            log.info("User {} initiated portfolio tag migration", authentication.getName());
            
            MigrationResult result = migrationService.migratePortfolioTags();
            
            response.put("success", result.isSuccessful());
            response.put("updated", result.getUpdated());
            response.put("skipped", result.getSkipped());
            response.put("errors", result.getErrors());
            response.put("message", result.getMessage());
            
            if (result.isSuccessful()) {
                log.info("Portfolio tag migration completed successfully: {} updated", result.getUpdated());
                return ResponseEntity.ok(response);
            } else {
                log.warn("Portfolio tag migration completed with errors: {} updated, {} errors", 
                    result.getUpdated(), result.getErrors());
                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).body(response);
            }
            
        } catch (Exception e) {
            log.error("Error during portfolio tag migration: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Migration failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Migrate block tags only
     */
    @PostMapping("/blocks")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> migrateBlockTags(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            log.info("User {} initiated block tag migration", authentication.getName());
            
            MigrationResult result = migrationService.migrateBlockTags();
            
            response.put("success", result.isSuccessful());
            response.put("updated", result.getUpdated());
            response.put("skipped", result.getSkipped());
            response.put("errors", result.getErrors());
            response.put("message", result.getMessage());
            
            if (result.isSuccessful()) {
                log.info("Block tag migration completed successfully: {} updated", result.getUpdated());
                return ResponseEntity.ok(response);
            } else {
                log.warn("Block tag migration completed with errors: {} updated, {} errors", 
                    result.getUpdated(), result.getErrors());
                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).body(response);
            }
            
        } catch (Exception e) {
            log.error("Error during block tag migration: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Migration failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Migrate all tags (portfolios and blocks)
     */
    @PostMapping("/all")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> migrateAllTags(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            log.info("User {} initiated complete tag migration", authentication.getName());
            
            MigrationSummary summary = migrationService.migrateAllTags();
            
            response.put("success", summary.isSuccessful());
            response.put("totalUpdated", summary.getTotalUpdated());
            response.put("totalSkipped", summary.getTotalSkipped());
            response.put("totalErrors", summary.getTotalErrors());
            
            // Portfolio details
            response.put("portfoliosUpdated", summary.getPortfolioResult().getUpdated());
            response.put("portfoliosSkipped", summary.getPortfolioResult().getSkipped());
            response.put("portfoliosErrors", summary.getPortfolioResult().getErrors());
            
            // Block details
            response.put("blocksUpdated", summary.getBlockResult().getUpdated());
            response.put("blocksSkipped", summary.getBlockResult().getSkipped());
            response.put("blocksErrors", summary.getBlockResult().getErrors());
            
            if (summary.isSuccessful()) {
                response.put("message", String.format("Migration completed successfully: %d portfolios and %d blocks updated", 
                    summary.getPortfolioResult().getUpdated(), summary.getBlockResult().getUpdated()));
                log.info("Complete tag migration successful: {} total updated", summary.getTotalUpdated());
                return ResponseEntity.ok(response);
            } else {
                response.put("message", String.format("Migration completed with errors: %d updated, %d errors", 
                    summary.getTotalUpdated(), summary.getTotalErrors()));
                log.warn("Complete tag migration finished with errors: {} updated, {} errors", 
                    summary.getTotalUpdated(), summary.getTotalErrors());
                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).body(response);
            }
            
        } catch (Exception e) {
            log.error("Error during complete tag migration: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Migration failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
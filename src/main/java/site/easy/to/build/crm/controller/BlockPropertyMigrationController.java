package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.service.migration.BlockPropertyMigrationService;

/**
 * Controller for block property migration operations.
 * Admin-only access for one-time data restructuring.
 */
@RestController
@RequestMapping("/api/admin/block-migration")
@PreAuthorize("hasRole('ADMIN')")
public class BlockPropertyMigrationController {

    @Autowired
    private BlockPropertyMigrationService migrationService;

    /**
     * Analyze block expenses (DRY RUN - no changes made)
     * GET /api/admin/block-migration/analyze
     */
    @GetMapping("/analyze")
    public ResponseEntity<String> analyzeBlockExpenses() {
        String report = migrationService.analyzeBlockExpenses();
        return ResponseEntity.ok(report);
    }

    /**
     * Execute the migration
     * POST /api/admin/block-migration/execute?dryRun=true
     *
     * @param dryRun If true, only analyze. If false, execute the migration.
     */
    @PostMapping("/execute")
    public ResponseEntity<String> executeMigration(
            @RequestParam(defaultValue = "true") boolean dryRun) {
        String report = migrationService.executeMigration(dryRun);
        return ResponseEntity.ok(report);
    }

    /**
     * Validate migration results
     * GET /api/admin/block-migration/validate
     */
    @GetMapping("/validate")
    public ResponseEntity<String> validateMigration() {
        String report = migrationService.validateMigration();
        return ResponseEntity.ok(report);
    }
}

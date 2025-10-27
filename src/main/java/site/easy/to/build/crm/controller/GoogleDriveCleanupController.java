package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.service.sheets.GoogleDriveCleanupService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for managing Google Drive storage cleanup
 */
@RestController
@RequestMapping("/hub/api/admin/google-drive-cleanup")
@PreAuthorize("hasRole('ADMIN')")
public class GoogleDriveCleanupController {

    @Autowired
    private GoogleDriveCleanupService cleanupService;

    /**
     * Get storage information
     * GET /hub/api/admin/google-drive-cleanup/storage-info
     */
    @GetMapping("/storage-info")
    public ResponseEntity<Map<String, Object>> getStorageInfo() {
        try {
            GoogleDriveCleanupService.StorageInfo info = cleanupService.getStorageInfo();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("limit", info.getLimit());
            response.put("usage", info.getUsage());
            response.put("usageInDrive", info.getUsageInDrive());
            response.put("usageFormatted", info.getUsageFormatted());
            response.put("usagePercent", String.format("%.1f%%", info.getUsagePercent()));
            response.put("spreadsheetCount", info.getSpreadsheetCount());
            response.put("spreadsheetSize", info.getSpreadsheetSize());
            response.put("spreadsheetSizeMB", info.getSpreadsheetSize() / 1024 / 1024);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to get storage info: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * List all spreadsheets
     * GET /hub/api/admin/google-drive-cleanup/list-all
     */
    @GetMapping("/list-all")
    public ResponseEntity<Map<String, Object>> listAllSpreadsheets() {
        try {
            List<GoogleDriveCleanupService.DriveFileInfo> files = cleanupService.listAllSpreadsheets();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", files.size());
            response.put("files", files);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to list spreadsheets: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Delete spreadsheets older than specified days
     * POST /hub/api/admin/google-drive-cleanup/delete-old
     * Body: { "daysToKeep": 30 }
     */
    @PostMapping("/delete-old")
    public ResponseEntity<Map<String, Object>> deleteOldSpreadsheets(@RequestBody Map<String, Integer> request) {
        try {
            Integer daysToKeep = request.getOrDefault("daysToKeep", 30);

            int deletedCount = cleanupService.deleteOldSpreadsheets(daysToKeep);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("deletedCount", deletedCount);
            response.put("message", "Deleted " + deletedCount + " spreadsheets older than " + daysToKeep + " days");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to delete old spreadsheets: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Delete ALL spreadsheets (use with extreme caution!)
     * POST /hub/api/admin/google-drive-cleanup/delete-all
     * Body: { "confirm": true }
     */
    @PostMapping("/delete-all")
    public ResponseEntity<Map<String, Object>> deleteAllSpreadsheets(@RequestBody Map<String, Boolean> request) {
        try {
            Boolean confirm = request.getOrDefault("confirm", false);

            if (!confirm) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "Must send { \"confirm\": true } to delete all spreadsheets");
                return ResponseEntity.status(400).body(error);
            }

            int deletedCount = cleanupService.deleteAllSpreadsheets();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("deletedCount", deletedCount);
            response.put("message", "Deleted ALL " + deletedCount + " spreadsheets");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to delete all spreadsheets: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Delete specific spreadsheet by ID
     * DELETE /hub/api/admin/google-drive-cleanup/{spreadsheetId}
     */
    @DeleteMapping("/{spreadsheetId}")
    public ResponseEntity<Map<String, Object>> deleteSpreadsheet(@PathVariable String spreadsheetId) {
        try {
            cleanupService.deleteSpreadsheet(spreadsheetId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Deleted spreadsheet: " + spreadsheetId);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to delete spreadsheet: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
}

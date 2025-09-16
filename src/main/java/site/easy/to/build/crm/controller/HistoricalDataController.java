package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.easy.to.build.crm.service.HistoricalDataImportService;

import java.util.HashMap;
import java.util.Map;

/**
 * Historical Data Import Controller
 *
 * Provides endpoints to import historical transaction data from CSV files
 * to populate the financial_transactions table for property owner statements.
 */
@Controller
@RequestMapping("/historical-data")
public class HistoricalDataController {

    private static final Logger log = LoggerFactory.getLogger(HistoricalDataController.class);

    @Autowired
    private HistoricalDataImportService historicalDataImportService;

    /**
     * Show the historical data import page
     */
    @GetMapping("/admin")
    public String showImportPage() {
        return "admin/historical-data-import";
    }

    /**
     * Import historical transaction data from CSV file
     */
    @PostMapping("/import")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> importHistoricalData(
            @RequestParam(defaultValue = "C:\\Users\\sajid\\crecrm\\historical_data.csv") String filePath,
            Authentication authentication) {

        log.info("🔄 Historical data import requested by user: {}",
            authentication != null ? authentication.getName() : "anonymous");

        Map<String, Object> response = new HashMap<>();

        try {
            Map<String, Object> result = historicalDataImportService.importHistoricalData(filePath);
            response.putAll(result);

            if ((Boolean) result.get("success")) {
                response.put("message", String.format(
                    "✅ Successfully imported %d transactions (%d errors)",
                    result.get("successfulInserts"), result.get("errors")));
            } else {
                response.put("message", "❌ Import failed: " + result.get("error"));
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Historical data import failed", e);
            response.put("success", false);
            response.put("message", "❌ Import failed: " + e.getMessage());
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Clear all historical import data (for reimport)
     */
    @PostMapping("/clear")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> clearHistoricalData(Authentication authentication) {

        log.info("🧹 Historical data clear requested by user: {}",
            authentication != null ? authentication.getName() : "anonymous");

        Map<String, Object> response = new HashMap<>();

        try {
            int deletedCount = historicalDataImportService.clearHistoricalData();

            response.put("success", true);
            response.put("deletedCount", deletedCount);
            response.put("message", String.format("🧹 Cleared %d historical records", deletedCount));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to clear historical data", e);
            response.put("success", false);
            response.put("message", "❌ Clear failed: " + e.getMessage());
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Check current status of historical data
     */
    @GetMapping("/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getHistoricalDataStatus() {

        Map<String, Object> response = new HashMap<>();

        try {
            // This would typically query the database for current status
            response.put("success", true);
            response.put("message", "Historical data status endpoint");
            response.put("availableEndpoints", Map.of(
                "import", "POST /historical-data/import - Import CSV data",
                "clear", "POST /historical-data/clear - Clear historical data",
                "status", "GET /historical-data/status - Check status"
            ));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get historical data status", e);
            response.put("success", false);
            response.put("message", "❌ Status check failed: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
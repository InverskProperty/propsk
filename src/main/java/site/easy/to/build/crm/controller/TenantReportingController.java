package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.easy.to.build.crm.service.reporting.TenantTurnoverReportService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tenant Reporting Controller
 * Provides REST API endpoints for tenant turnover and occupancy reporting
 */
@RestController
@RequestMapping("/api/reports/tenants")
@PreAuthorize("hasRole('EMPLOYEE')")
public class TenantReportingController {

    private static final Logger log = LoggerFactory.getLogger(TenantReportingController.class);

    @Autowired
    private TenantTurnoverReportService reportService;

    /**
     * GET /api/reports/tenants/turnover/summary
     * Get comprehensive tenant turnover summary for a date range
     */
    @GetMapping("/turnover/summary")
    public ResponseEntity<?> getTurnoverSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        try {
            // Default to last 30 days if not specified
            if (startDate == null) {
                startDate = LocalDate.now().minusDays(30);
            }
            if (endDate == null) {
                endDate = LocalDate.now();
            }

            log.info("📊 Generating turnover summary from {} to {}", startDate, endDate);

            Map<String, Object> summary = reportService.getTurnoverSummary(startDate, endDate);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", summary
            ));

        } catch (Exception e) {
            log.error("❌ Error generating turnover summary", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * GET /api/reports/tenants/turnover/by-property
     * Get turnover statistics by property
     */
    @GetMapping("/turnover/by-property")
    public ResponseEntity<?> getTurnoverByProperty(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        try {
            // Default to last 30 days if not specified
            if (startDate == null) {
                startDate = LocalDate.now().minusDays(30);
            }
            if (endDate == null) {
                endDate = LocalDate.now();
            }

            log.info("📊 Generating turnover by property from {} to {}", startDate, endDate);

            List<Map<String, Object>> propertyStats = reportService.getTurnoverByProperty(startDate, endDate);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", propertyStats,
                    "count", propertyStats.size()
            ));

        } catch (Exception e) {
            log.error("❌ Error generating turnover by property", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * GET /api/reports/tenants/upcoming-moveouts
     * Get list of tenants moving out soon
     */
    @GetMapping("/upcoming-moveouts")
    public ResponseEntity<?> getUpcomingMoveOuts(
            @RequestParam(defaultValue = "30") int days) {

        try {
            log.info("📊 Getting tenants moving out in next {} days", days);

            List<Map<String, Object>> tenants = reportService.getUpcomingMoveOuts(days);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", tenants,
                    "count", tenants.size(),
                    "days", days
            ));

        } catch (Exception e) {
            log.error("❌ Error getting upcoming move-outs", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * GET /api/reports/tenants/recent-moveouts
     * Get list of recent move-outs
     */
    @GetMapping("/recent-moveouts")
    public ResponseEntity<?> getRecentMoveOuts(
            @RequestParam(defaultValue = "30") int days) {

        try {
            log.info("📊 Getting tenants who moved out in last {} days", days);

            List<Map<String, Object>> tenants = reportService.getRecentMoveOuts(days);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", tenants,
                    "count", tenants.size(),
                    "days", days
            ));

        } catch (Exception e) {
            log.error("❌ Error getting recent move-outs", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * GET /api/reports/tenants/recent-moveins
     * Get list of recent move-ins
     */
    @GetMapping("/recent-moveins")
    public ResponseEntity<?> getRecentMoveIns(
            @RequestParam(defaultValue = "30") int days) {

        try {
            log.info("📊 Getting tenants who moved in during last {} days", days);

            List<Map<String, Object>> tenants = reportService.getRecentMoveIns(days);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", tenants,
                    "count", tenants.size(),
                    "days", days
            ));

        } catch (Exception e) {
            log.error("❌ Error getting recent move-ins", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * GET /api/reports/tenants/current
     * Get list of all current/active tenants
     */
    @GetMapping("/current")
    public ResponseEntity<?> getCurrentTenants() {

        try {
            log.info("📊 Getting all current tenants");

            List<Map<String, Object>> tenants = reportService.getCurrentTenants();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", tenants,
                    "count", tenants.size()
            ));

        } catch (Exception e) {
            log.error("❌ Error getting current tenants", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * GET /api/reports/tenants/tenancy-duration
     * Get tenancy duration statistics
     */
    @GetMapping("/tenancy-duration")
    public ResponseEntity<?> getTenancyDurationStats() {

        try {
            log.info("📊 Getting tenancy duration statistics");

            Map<String, Object> stats = reportService.getTenancyDurationStats();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", stats
            ));

        } catch (Exception e) {
            log.error("❌ Error getting tenancy duration stats", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * GET /api/reports/tenants/monthly-trend
     * Get monthly turnover trend for the last N months
     */
    @GetMapping("/monthly-trend")
    public ResponseEntity<?> getMonthlyTurnoverTrend(
            @RequestParam(defaultValue = "12") int months) {

        try {
            log.info("📊 Getting monthly turnover trend for last {} months", months);

            List<Map<String, Object>> trend = reportService.getMonthlyTurnoverTrend(months);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", trend,
                    "months", months
            ));

        } catch (Exception e) {
            log.error("❌ Error getting monthly turnover trend", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * GET /api/reports/tenants/dashboard
     * Get comprehensive dashboard data with all key metrics
     */
    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboardData() {

        try {
            log.info("📊 Generating tenant turnover dashboard");

            LocalDate today = LocalDate.now();
            LocalDate thirtyDaysAgo = today.minusDays(30);

            Map<String, Object> dashboard = new HashMap<>();

            // Summary stats (last 30 days)
            dashboard.put("summary", reportService.getTurnoverSummary(thirtyDaysAgo, today));

            // Upcoming move-outs
            dashboard.put("upcomingMoveOuts30Days", reportService.getUpcomingMoveOuts(30));
            dashboard.put("upcomingMoveOuts60Days", reportService.getUpcomingMoveOuts(60));

            // Recent activity
            dashboard.put("recentMoveOuts", reportService.getRecentMoveOuts(30));
            dashboard.put("recentMoveIns", reportService.getRecentMoveIns(30));

            // Tenancy duration stats
            dashboard.put("tenancyDurationStats", reportService.getTenancyDurationStats());

            // Monthly trend (last 6 months)
            dashboard.put("monthlyTrend", reportService.getMonthlyTurnoverTrend(6));

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", dashboard,
                    "generatedAt", today
            ));

        } catch (Exception e) {
            log.error("❌ Error generating dashboard", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }
}

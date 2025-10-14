package site.easy.to.build.crm.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import site.easy.to.build.crm.service.payprop.PayPropInvoiceToLeaseImportService;
import site.easy.to.build.crm.service.payprop.business.LeaseBasedRentCalculationService;
import site.easy.to.build.crm.service.lease.LeaseHistoryService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for Lease-Based Migration
 *
 * Endpoints to:
 * 1. Import PayProp invoices as local lease records
 * 2. View lease-based rent calculations
 * 3. Compare property-level vs lease-level income
 * 4. Identify multi-tenant properties
 */
@RestController
@RequestMapping("/api/lease-migration")
@CrossOrigin(origins = "*")
public class LeaseBasedMigrationController {

    private static final Logger log = LoggerFactory.getLogger(LeaseBasedMigrationController.class);

    @Autowired
    private PayPropInvoiceToLeaseImportService invoiceImportService;

    @Autowired
    private LeaseBasedRentCalculationService leaseRentService;

    @Autowired
    private LeaseHistoryService leaseHistoryService;

    /**
     * Import all PayProp invoices as lease records
     * POST /api/lease-migration/import
     *
     * Example response:
     * {
     *   "success": true,
     *   "totalPayPropInvoices": 45,
     *   "successfulImports": 42,
     *   "failedImports": 0,
     *   "skippedInvoices": 3,
     *   "activeLeases": 37,
     *   "durationSeconds": 5
     * }
     */
    @PostMapping("/import")
    public ResponseEntity<?> importInvoicesAsLeases() {
        log.info("🔄 API: Starting invoice → lease import");

        try {
            PayPropInvoiceToLeaseImportService.ImportResult result =
                invoiceImportService.importAllInvoicesAsLeases();

            Map<String, Object> response = new HashMap<>();
            response.put("success", result.success);
            response.put("totalPayPropInvoices", result.totalPayPropInvoices);
            response.put("successfulImports", result.successfulImports);
            response.put("failedImports", result.failedImports);
            response.put("skippedInvoices", result.skippedInvoices);
            response.put("activeLeases", result.activeLeases);
            response.put("durationSeconds", result.getDurationSeconds());

            if (!result.errors.isEmpty()) {
                response.put("errors", result.errors);
            }

            if (result.errorMessage != null) {
                response.put("errorMessage", result.errorMessage);
            }

            log.info("✅ API: Import complete - {} leases imported", result.successfulImports);

            return result.success
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(500).body(response);

        } catch (Exception e) {
            log.error("❌ API: Import failed", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Get lease statistics
     * GET /api/lease-migration/statistics
     *
     * Example response:
     * {
     *   "totalLeases": 42,
     *   "activeLeases": 37,
     *   "multiTenantProperties": 1,
     *   "totalMonthlyRent": 25340.00
     * }
     */
    @GetMapping("/statistics")
    public ResponseEntity<?> getLeaseStatistics() {
        try {
            PayPropInvoiceToLeaseImportService.LeaseStatistics stats =
                invoiceImportService.getLeaseStatistics();

            Map<String, Object> response = new HashMap<>();
            response.put("totalLeases", stats.totalLeases);
            response.put("activeLeases", stats.activeLeases);
            response.put("multiTenantProperties", stats.multiTenantProperties);
            response.put("totalMonthlyRent", stats.totalMonthlyRent);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ API: Failed to get statistics", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Calculate expected monthly income from all active leases
     * GET /api/lease-migration/income-report
     *
     * Returns detailed breakdown of income by property and lease
     */
    @GetMapping("/income-report")
    public ResponseEntity<?> getIncomeReport() {
        try {
            LeaseBasedRentCalculationService.PropertyIncomeReport report =
                leaseRentService.calculateExpectedMonthlyIncome();

            return ResponseEntity.ok(report);

        } catch (Exception e) {
            log.error("❌ API: Failed to generate income report", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Get all multi-tenant properties (properties with multiple active leases)
     * GET /api/lease-migration/multi-tenant-properties
     *
     * Example response:
     * [
     *   {
     *     "propertyId": 123,
     *     "propertyName": "Apartment 40 - 31 Watkin Road",
     *     "numberOfLeases": 3,
     *     "totalMonthlyRent": 2840.00,
     *     "leases": [
     *       {
     *         "tenantName": "Jason Barclay",
     *         "rentAmount": 900.00,
     *         "startDate": "2025-06-17"
     *       },
     *       ...
     *     ]
     *   }
     * ]
     */
    @GetMapping("/multi-tenant-properties")
    public ResponseEntity<?> getMultiTenantProperties() {
        try {
            List<LeaseBasedRentCalculationService.PropertyIncomeDetail> properties =
                leaseRentService.findMultiTenantProperties();

            return ResponseEntity.ok(properties);

        } catch (Exception e) {
            log.error("❌ API: Failed to get multi-tenant properties", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Find income discrepancies
     * GET /api/lease-migration/income-discrepancies
     *
     * Shows properties where Property.monthlyPayment differs from sum of active leases
     * This helps identify where property-level data is inaccurate
     */
    @GetMapping("/income-discrepancies")
    public ResponseEntity<?> getIncomeDiscrepancies() {
        try {
            List<LeaseBasedRentCalculationService.IncomeDiscrepancy> discrepancies =
                leaseRentService.findIncomeDiscrepancies();

            Map<String, Object> response = new HashMap<>();
            response.put("totalDiscrepancies", discrepancies.size());
            response.put("discrepancies", discrepancies);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ API: Failed to find income discrepancies", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Get active leases for a specific property
     * GET /api/lease-migration/property/{propertyId}/leases
     */
    @GetMapping("/property/{propertyId}/leases")
    public ResponseEntity<?> getPropertyLeases(@PathVariable Long propertyId) {
        try {
            List<LeaseBasedRentCalculationService.LeaseDetail> leases =
                leaseRentService.getActiveLeasesForProperty(propertyId);

            Map<String, Object> response = new HashMap<>();
            response.put("propertyId", propertyId);
            response.put("numberOfLeases", leases.size());
            response.put("leases", leases);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ API: Failed to get leases for property {}", propertyId, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Get complete lease history for a property (active + historical)
     * GET /api/lease-migration/property/{propertyId}/history
     *
     * Example response:
     * {
     *   "propertyId": 123,
     *   "propertyName": "Apartment 40 - 31 Watkin Road",
     *   "totalLeaseCount": 8,
     *   "currentLeaseCount": 3,
     *   "historicalLeaseCount": 5,
     *   "currentLeases": [...],
     *   "historicalLeases": [...]
     * }
     */
    @GetMapping("/property/{propertyId}/history")
    public ResponseEntity<?> getPropertyLeaseHistory(@PathVariable Long propertyId) {
        try {
            LeaseHistoryService.PropertyLeaseHistoryReport report =
                leaseHistoryService.getPropertyLeaseHistory(propertyId);

            if (report == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Property not found");
                return ResponseEntity.status(404).body(errorResponse);
            }

            return ResponseEntity.ok(report);

        } catch (Exception e) {
            log.error("❌ API: Failed to get lease history for property {}", propertyId, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Calculate income for a property during a specific period
     * GET /api/lease-migration/property/{propertyId}/income?start=2024-01-01&end=2024-12-31
     *
     * Example response:
     * {
     *   "propertyId": 123,
     *   "propertyName": "Apartment 40",
     *   "periodStart": "2024-01-01",
     *   "periodEnd": "2024-12-31",
     *   "totalIncome": 10800.00,
     *   "leasesActiveDuringPeriod": 2,
     *   "breakdown": [
     *     {
     *       "tenantName": "John Smith",
     *       "rentAmount": 900.00,
     *       "monthsActive": 12,
     *       "totalContribution": 10800.00
     *     }
     *   ]
     * }
     */
    @GetMapping("/property/{propertyId}/income")
    public ResponseEntity<?> getPropertyIncomeForPeriod(
            @PathVariable Long propertyId,
            @RequestParam String start,
            @RequestParam String end) {

        try {
            LocalDate startDate = LocalDate.parse(start);
            LocalDate endDate = LocalDate.parse(end);

            LeaseHistoryService.PeriodIncomeReport report =
                leaseHistoryService.calculateIncomeForPeriod(propertyId, startDate, endDate);

            if (report == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Property not found");
                return ResponseEntity.status(404).body(errorResponse);
            }

            return ResponseEntity.ok(report);

        } catch (Exception e) {
            log.error("❌ API: Failed to calculate income for property {} in period", propertyId, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Get historical leases for a property (ended leases only)
     * GET /api/lease-migration/property/{propertyId}/historical-leases
     *
     * Example response:
     * {
     *   "propertyId": 123,
     *   "historicalLeaseCount": 5,
     *   "leases": [
     *     {
     *       "invoiceId": 456,
     *       "tenantName": "John Smith",
     *       "rentAmount": 850.00,
     *       "startDate": "2023-01-01",
     *       "endDate": "2024-06-30",
     *       "status": "Ended",
     *       "durationMonths": 18
     *     }
     *   ]
     * }
     */
    @GetMapping("/property/{propertyId}/historical-leases")
    public ResponseEntity<?> getHistoricalLeases(@PathVariable Long propertyId) {
        try {
            List<LeaseHistoryService.LeaseHistoryDetail> historicalLeases =
                leaseHistoryService.getLeaseHistoryWithDetails(propertyId).stream()
                    .filter(detail -> !detail.isActive)
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("propertyId", propertyId);
            response.put("historicalLeaseCount", historicalLeases.size());
            response.put("leases", historicalLeases);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ API: Failed to get historical leases for property {}", propertyId, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Find leases that ended within a date range
     * GET /api/lease-migration/property/{propertyId}/leases-ended?start=2024-01-01&end=2024-12-31
     *
     * Example response:
     * {
     *   "propertyId": 123,
     *   "periodStart": "2024-01-01",
     *   "periodEnd": "2024-12-31",
     *   "leasesEndedCount": 2,
     *   "leases": [...]
     * }
     */
    @GetMapping("/property/{propertyId}/leases-ended")
    public ResponseEntity<?> getLeasesEndedInRange(
            @PathVariable Long propertyId,
            @RequestParam String start,
            @RequestParam String end) {

        try {
            LocalDate startDate = LocalDate.parse(start);
            LocalDate endDate = LocalDate.parse(end);

            List<LeaseHistoryService.LeaseHistoryDetail> endedLeases =
                leaseHistoryService.findLeasesEndedInRange(propertyId, startDate, endDate);

            Map<String, Object> response = new HashMap<>();
            response.put("propertyId", propertyId);
            response.put("periodStart", startDate);
            response.put("periodEnd", endDate);
            response.put("leasesEndedCount", endedLeases.size());
            response.put("leases", endedLeases);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ API: Failed to get leases ended in range for property {}", propertyId, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}

package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * PayProp Diagnostics Controller
 * Provides concise, actionable diagnostics for PayProp sync status
 */
@RestController
@RequestMapping("/api/payprop/diagnostics")
@PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
public class PayPropDiagnosticsController {

    private static final Logger log = LoggerFactory.getLogger(PayPropDiagnosticsController.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Get concise sync status - shows what worked and what didn't
     */
    @GetMapping("/sync-status")
    public ResponseEntity<Map<String, Object>> getSyncStatus() {
        Map<String, Object> status = new LinkedHashMap<>();

        try {
            // CUSTOMERS
            Map<String, Object> customers = new LinkedHashMap<>();
            Integer totalCustomers = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customers", Integer.class);
            Integer payPropCustomers = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customers WHERE payprop_synced = 1", Integer.class);

            Map<String, Integer> byType = new LinkedHashMap<>();
            jdbcTemplate.query(
                "SELECT customer_type, COUNT(*) as count FROM customers GROUP BY customer_type",
                rs -> {
                    byType.put(rs.getString("customer_type"), rs.getInt("count"));
                }
            );

            customers.put("total", totalCustomers);
            customers.put("payprop_synced", payPropCustomers);
            customers.put("by_type", byType);
            customers.put("✓ OWNERS", byType.getOrDefault("PROPERTY_OWNER", 0));
            customers.put("✓ TENANTS", byType.getOrDefault("TENANT", 0));
            customers.put("✓ CONTRACTORS", byType.getOrDefault("CONTRACTOR", 0));
            status.put("CUSTOMERS", customers);

            // PROPERTIES
            Map<String, Object> properties = new LinkedHashMap<>();
            Integer totalProperties = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM properties", Integer.class);
            Integer payPropProperties = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM properties WHERE payprop_synced = 1", Integer.class);

            properties.put("total", totalProperties);
            properties.put("payprop_synced", payPropProperties);
            status.put("PROPERTIES", properties);

            // ASSIGNMENTS
            Map<String, Object> assignments = new LinkedHashMap<>();
            Integer totalAssignments = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customer_property_assignments", Integer.class);

            Map<String, Integer> byAssignmentType = new LinkedHashMap<>();
            jdbcTemplate.query(
                "SELECT assignment_type, COUNT(*) as count FROM customer_property_assignments GROUP BY assignment_type",
                rs -> {
                    byAssignmentType.put(rs.getString("assignment_type"), rs.getInt("count"));
                }
            );

            assignments.put("total", totalAssignments);
            assignments.put("by_type", byAssignmentType);
            assignments.put("✓ OWNER_LINKS", byAssignmentType.getOrDefault("OWNER", 0));
            assignments.put("✓ TENANT_LINKS", byAssignmentType.getOrDefault("TENANT", 0));
            status.put("ASSIGNMENTS", assignments);

            // RAW DATA AVAILABILITY
            Map<String, Object> rawData = new LinkedHashMap<>();
            rawData.put("properties", jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payprop_export_properties", Integer.class));
            rawData.put("tenants", jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payprop_export_tenants", Integer.class));
            rawData.put("beneficiaries", jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payprop_export_beneficiaries", Integer.class));

            try {
                rawData.put("payments", jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payprop_export_payments", Integer.class));
            } catch (Exception e) {
                rawData.put("payments", "N/A");
            }

            status.put("RAW_DATA_IMPORTED", rawData);

            // PROBLEMS DETECTION
            List<String> problems = new ArrayList<>();

            if (payPropCustomers == 0) {
                problems.add("❌ NO PayProp customers synced - sync process didn't create customers");
            }

            if (byType.getOrDefault("PROPERTY_OWNER", 0) == 0) {
                problems.add("❌ NO OWNERS found - owners not being created as customers");
            }

            if (byType.getOrDefault("TENANT", 0) == 0) {
                problems.add("❌ NO TENANTS found - tenants not being created as customers");
            }

            if (byAssignmentType.getOrDefault("OWNER", 0) == 0) {
                problems.add("❌ NO OWNER ASSIGNMENTS - properties not linked to owners");
            }

            if (byAssignmentType.getOrDefault("TENANT", 0) == 0) {
                problems.add("❌ NO TENANT ASSIGNMENTS - properties not linked to tenants");
            }

            if ((Integer)rawData.get("beneficiaries") > 0 && byType.getOrDefault("PROPERTY_OWNER", 0) == 0) {
                problems.add("⚠️  Have beneficiary data but no owner customers - sync logic may be broken");
            }

            if ((Integer)rawData.get("tenants") > 0 && byType.getOrDefault("TENANT", 0) == 0) {
                problems.add("⚠️  Have tenant data but no tenant customers - sync logic may be broken");
            }

            if (problems.isEmpty()) {
                problems.add("✅ All entities synced successfully!");
            }

            status.put("PROBLEMS", problems);

            // RECOMMENDATIONS
            List<String> recommendations = new ArrayList<>();

            if (payPropCustomers == 0 && (Integer)rawData.get("beneficiaries") > 0) {
                recommendations.add("Run: Scope-Aware Sync button to process raw data into customers");
            }

            if (byAssignmentType.getOrDefault("OWNER", 0) == 0 && byType.getOrDefault("PROPERTY_OWNER", 0) > 0) {
                recommendations.add("Run: Link Owners button to establish property-owner relationships");
            }

            if (recommendations.isEmpty()) {
                recommendations.add("System looks healthy - monitor for any issues");
            }

            status.put("RECOMMENDATIONS", recommendations);

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            log.error("Diagnostics failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", e.getMessage(),
                "message", "Failed to get diagnostics"
            ));
        }
    }

    /**
     * Get sample of what's in customers table - helps debug
     */
    @GetMapping("/sample-customers")
    public ResponseEntity<Map<String, Object>> getSampleCustomers() {
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            // Sample PayProp customers
            List<Map<String, Object>> paypropCustomers = jdbcTemplate.queryForList(
                "SELECT id, name, customer_type, payprop_entity_id, payprop_synced " +
                "FROM customers WHERE payprop_synced = 1 LIMIT 10"
            );

            result.put("payprop_customers_sample", paypropCustomers);
            result.put("payprop_customers_count", paypropCustomers.size());

            // Sample owners
            List<Map<String, Object>> owners = jdbcTemplate.queryForList(
                "SELECT id, name, customer_type, payprop_entity_id " +
                "FROM customers WHERE customer_type = 'PROPERTY_OWNER' LIMIT 10"
            );

            result.put("owners_sample", owners);
            result.put("owners_count", owners.size());

            // Sample tenants
            List<Map<String, Object>> tenants = jdbcTemplate.queryForList(
                "SELECT id, name, customer_type, payprop_entity_id " +
                "FROM customers WHERE customer_type = 'TENANT' LIMIT 10"
            );

            result.put("tenants_sample", tenants);
            result.put("tenants_count", tenants.size());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Sample customers failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Check what's available in raw PayProp tables
     */
    @GetMapping("/raw-data-check")
    public ResponseEntity<Map<String, Object>> checkRawData() {
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            // Check beneficiaries (should become owners)
            List<Map<String, Object>> beneficiaries = jdbcTemplate.queryForList(
                "SELECT id, first_name, last_name, beneficiary_type, email " +
                "FROM payprop_export_beneficiaries LIMIT 5"
            );
            result.put("beneficiaries_sample", beneficiaries);
            result.put("beneficiaries_total", jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payprop_export_beneficiaries", Integer.class));

            // Check tenants
            List<Map<String, Object>> tenants = jdbcTemplate.queryForList(
                "SELECT id, first_name, last_name, email " +
                "FROM payprop_export_tenants LIMIT 5"
            );
            result.put("tenants_sample", tenants);
            result.put("tenants_total", jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payprop_export_tenants", Integer.class));

            // Check properties
            result.put("properties_total", jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payprop_export_properties", Integer.class));

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Raw data check failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }
}

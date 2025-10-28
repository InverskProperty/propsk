package site.easy.to.build.crm.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * One-time migration runner to populate lease_reference for existing invoices
 * This will run once on application startup and then can be deleted
 */
@Component
public class LeaseReferenceMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LeaseReferenceMigrationRunner.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            log.info("🔄 Running lease_reference migration...");

            // Check current state
            Map<String, Object> beforeStats = jdbcTemplate.queryForMap(
                "SELECT " +
                "    COUNT(*) as total_invoices, " +
                "    SUM(CASE WHEN lease_reference IS NOT NULL AND lease_reference != '' THEN 1 ELSE 0 END) as with_lease_ref, " +
                "    SUM(CASE WHEN lease_reference IS NULL OR lease_reference = '' THEN 1 ELSE 0 END) as without_lease_ref " +
                "FROM invoices " +
                "WHERE deleted_at IS NULL"
            );

            log.info("📊 Before migration: Total={}, With lease_ref={}, Without lease_ref={}",
                beforeStats.get("total_invoices"),
                beforeStats.get("with_lease_ref"),
                beforeStats.get("without_lease_ref"));

            // Run the update
            int updated = jdbcTemplate.update(
                "UPDATE invoices " +
                "SET lease_reference = CONCAT('LEASE-', id) " +
                "WHERE (lease_reference IS NULL OR lease_reference = '') " +
                "  AND deleted_at IS NULL"
            );

            log.info("✅ Updated {} invoices with LEASE-{{id}} references", updated);

            // Verify results
            Map<String, Object> afterStats = jdbcTemplate.queryForMap(
                "SELECT " +
                "    COUNT(*) as total_invoices, " +
                "    SUM(CASE WHEN lease_reference LIKE 'LEASE-%' THEN 1 ELSE 0 END) as with_uniform_ref, " +
                "    SUM(CASE WHEN lease_reference IS NOT NULL AND lease_reference NOT LIKE 'LEASE-%' THEN 1 ELSE 0 END) as with_external_ref, " +
                "    SUM(CASE WHEN lease_reference IS NULL OR lease_reference = '' THEN 1 ELSE 0 END) as still_null " +
                "FROM invoices " +
                "WHERE deleted_at IS NULL"
            );

            log.info("📊 After migration: Total={}, Uniform LEASE-*={}, External refs={}, Still null={}",
                afterStats.get("total_invoices"),
                afterStats.get("with_uniform_ref"),
                afterStats.get("with_external_ref"),
                afterStats.get("still_null"));

            log.info("✅ Lease reference migration completed successfully!");
            log.info("💡 You can now safely delete this class: LeaseReferenceMigrationRunner.java");

        } catch (Exception e) {
            log.error("❌ Lease reference migration failed: {}", e.getMessage(), e);
        }
    }
}

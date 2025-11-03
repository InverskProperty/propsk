import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * EMERGENCY MIGRATION: Fix production login issue
 *
 * Run this to add missing columns to the production database.
 *
 * Usage:
 *   1. Compile: javac RunMigration.java
 *   2. Run: java RunMigration "jdbc:mysql://host:port/db" "username" "password"
 */
public class RunMigration {

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: java RunMigration <jdbc-url> <username> <password>");
            System.err.println("Example: java RunMigration \"jdbc:mysql://localhost:3306/crm\" \"root\" \"password\"");
            System.exit(1);
        }

        String jdbcUrl = args[0];
        String username = args[1];
        String password = args[2];

        System.out.println("========================================");
        System.out.println("CRITICAL MIGRATION: Fixing Login Issue");
        System.out.println("========================================");
        System.out.println("Adding missing columns to customers table...");
        System.out.println();

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             Statement stmt = conn.createStatement()) {

            // Step 1: Add billing_period_start_day column
            System.out.println("[1/4] Adding billing_period_start_day column...");
            try {
                stmt.execute(
                    "ALTER TABLE customers " +
                    "ADD COLUMN billing_period_start_day INT DEFAULT 1 " +
                    "COMMENT 'Billing period start day (1, 22, 25, or 28)'"
                );
                System.out.println("✓ billing_period_start_day column added");
            } catch (Exception e) {
                if (e.getMessage().contains("Duplicate column")) {
                    System.out.println("⊙ billing_period_start_day already exists (skipped)");
                } else {
                    throw e;
                }
            }

            // Step 2: Add statement_email_enabled column
            System.out.println("[2/4] Adding statement_email_enabled column...");
            try {
                stmt.execute(
                    "ALTER TABLE customers " +
                    "ADD COLUMN statement_email_enabled BOOLEAN DEFAULT FALSE " +
                    "COMMENT 'Whether to email statements automatically'"
                );
                System.out.println("✓ statement_email_enabled column added");
            } catch (Exception e) {
                if (e.getMessage().contains("Duplicate column")) {
                    System.out.println("⊙ statement_email_enabled already exists (skipped)");
                } else {
                    throw e;
                }
            }

            // Step 3: Create generated_statements table
            System.out.println("[3/4] Creating generated_statements table...");
            try {
                stmt.execute(
                    "CREATE TABLE generated_statements (" +
                    "    id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "    customer_id BIGINT NOT NULL," +
                    "    period_start DATE NOT NULL," +
                    "    period_end DATE NOT NULL," +
                    "    file_name VARCHAR(255)," +
                    "    file_path VARCHAR(500)," +
                    "    file_size_bytes BIGINT," +
                    "    format VARCHAR(20) COMMENT 'EXCEL, PDF, GOOGLE_SHEETS'," +
                    "    generated_at DATETIME NOT NULL," +
                    "    generated_by VARCHAR(100) COMMENT 'User who generated it'," +
                    "    billing_period_start_day INT COMMENT 'Custom period used'," +
                    "    total_rent DECIMAL(19,2)," +
                    "    total_expenses DECIMAL(19,2)," +
                    "    total_commission DECIMAL(19,2)," +
                    "    net_to_owner DECIMAL(19,2)," +
                    "    download_count INT DEFAULT 0," +
                    "    last_downloaded_at DATETIME," +
                    "    INDEX idx_customer_id (customer_id)," +
                    "    INDEX idx_period (period_start, period_end)," +
                    "    INDEX idx_generated_at (generated_at)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 " +
                    "COMMENT='Tracks generated financial statements'"
                );
                System.out.println("✓ generated_statements table created");
            } catch (Exception e) {
                if (e.getMessage().contains("already exists")) {
                    System.out.println("⊙ generated_statements table already exists (skipped)");
                } else {
                    throw e;
                }
            }

            // Step 4: Verification
            System.out.println("[4/4] Verifying migration...");
            var rs = stmt.executeQuery(
                "SELECT COUNT(*) as cnt FROM customers WHERE billing_period_start_day IS NOT NULL"
            );
            rs.next();
            int count = rs.getInt("cnt");
            System.out.println("✓ Verified: " + count + " customers have billing_period_start_day set");

            System.out.println();
            System.out.println("========================================");
            System.out.println("✓ MIGRATION COMPLETED SUCCESSFULLY!");
            System.out.println("========================================");
            System.out.println("Login should now work. Please test at:");
            System.out.println("https://spoutproperty-hub.onrender.com/customer-login");
            System.out.println();

        } catch (Exception e) {
            System.err.println();
            System.err.println("========================================");
            System.err.println("✗ MIGRATION FAILED!");
            System.err.println("========================================");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

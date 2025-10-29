package site.easy.to.build.crm.util;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class InvoiceQueryRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public InvoiceQueryRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        if (args.length > 0 && "query-invoices".equals(args[0])) {
            runInvoiceQueries();
            System.exit(0);
        }
    }

    private void runInvoiceQueries() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("INVOICE AND FINANCIAL TRANSACTIONS ANALYSIS");
        System.out.println("=".repeat(80) + "\n");

        // Query 1: Count invoices in invoices table
        System.out.println("=== QUERY 1: TOTAL INVOICES ===");
        String query1 = "SELECT 'invoices table - total' as metric, COUNT(*) as count FROM invoices";
        List<Map<String, Object>> result1 = jdbcTemplate.queryForList(query1);
        System.out.printf("%-30s %-10s%n", "METRIC", "COUNT");
        System.out.println("-".repeat(45));
        for (Map<String, Object> row : result1) {
            System.out.printf("%-30s %-10s%n", row.get("metric"), row.get("count"));
        }

        // Query 2: Count invoices by PayProp sync status
        System.out.println("\n=== QUERY 2: INVOICES BY SOURCE ===");
        String query2 = "SELECT 'invoices table - by source' as metric, " +
                "CASE WHEN payprop_id IS NOT NULL THEN 'From PayProp' ELSE 'Locally Created' END as source, " +
                "COUNT(*) as count FROM invoices GROUP BY source";
        List<Map<String, Object>> result2 = jdbcTemplate.queryForList(query2);
        System.out.printf("%-30s %-20s %-10s%n", "METRIC", "SOURCE", "COUNT");
        System.out.println("-".repeat(65));
        for (Map<String, Object> row : result2) {
            System.out.printf("%-30s %-20s %-10s%n", row.get("metric"), row.get("source"), row.get("count"));
        }

        // Query 3: Compare with RENT_INVOICE in financial_transactions
        System.out.println("\n=== QUERY 3: RENT_INVOICE IN FINANCIAL_TRANSACTIONS ===");
        String query3 = "SELECT 'financial_transactions RENT_INVOICE' as metric, COUNT(*) as count " +
                "FROM financial_transactions WHERE data_source = 'RENT_INVOICE'";
        List<Map<String, Object>> result3 = jdbcTemplate.queryForList(query3);
        System.out.printf("%-40s %-10s%n", "METRIC", "COUNT");
        System.out.println("-".repeat(55));
        for (Map<String, Object> row : result3) {
            System.out.printf("%-40s %-10s%n", row.get("metric"), row.get("count"));
        }

        // Query 4: Check which properties have RENT_INVOICE
        System.out.println("\n=== QUERY 4: PROPERTIES WITH RENT_INVOICE ===");
        String query4 = "SELECT DISTINCT property_name, property_id FROM financial_transactions " +
                "WHERE data_source = 'RENT_INVOICE' ORDER BY property_name";
        List<Map<String, Object>> result4 = jdbcTemplate.queryForList(query4);
        System.out.printf("%-50s %-10s%n", "PROPERTY_NAME", "PROPERTY_ID");
        System.out.println("-".repeat(65));
        for (Map<String, Object> row : result4) {
            System.out.printf("%-50s %-10s%n", row.get("property_name"), row.get("property_id"));
        }

        // Query 5: Check total properties with invoices in invoices table
        System.out.println("\n=== QUERY 5: DISTINCT PROPERTIES WITH INVOICES ===");
        String query5 = "SELECT COUNT(DISTINCT property_id) as properties_with_invoices FROM invoices";
        List<Map<String, Object>> result5 = jdbcTemplate.queryForList(query5);
        System.out.printf("%-35s%n", "PROPERTIES_WITH_INVOICES");
        System.out.println("-".repeat(40));
        for (Map<String, Object> row : result5) {
            System.out.printf("%-35s%n", row.get("properties_with_invoices"));
        }

        // Query 6: Check if RENT_INVOICE records link to invoices table
        System.out.println("\n=== QUERY 6: RENT_INVOICE WITH INVOICE_ID ===");
        String query6 = "SELECT 'RENT_INVOICE with invoice_id' as check_type, COUNT(*) as count " +
                "FROM financial_transactions WHERE data_source = 'RENT_INVOICE' AND invoice_id IS NOT NULL";
        List<Map<String, Object>> result6 = jdbcTemplate.queryForList(query6);
        System.out.printf("%-35s %-10s%n", "CHECK_TYPE", "COUNT");
        System.out.println("-".repeat(50));
        for (Map<String, Object> row : result6) {
            System.out.printf("%-35s %-10s%n", row.get("check_type"), row.get("count"));
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println("QUERY EXECUTION COMPLETE");
        System.out.println("=".repeat(80) + "\n");
    }
}

import java.sql.*;
import java.util.*;

public class SearchDatabaseForPropertyID {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";
        String searchId = "KAXNvEqAXk";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC Driver not found.");
        }

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== SEARCHING ENTIRE DATABASE FOR: " + searchId + " ===\n");

            // Get all tables
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet tables = metaData.getTables("railway", null, "%", new String[]{"TABLE"});

            List<String> tableNames = new ArrayList<>();
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                tableNames.add(tableName);
            }
            tables.close();

            System.out.println("Searching " + tableNames.size() + " tables...\n");

            int totalFound = 0;

            // Search each table
            for (String tableName : tableNames) {
                try {
                    // Get all columns for this table
                    ResultSet columns = metaData.getColumns("railway", null, tableName, "%");
                    List<String> columnNames = new ArrayList<>();

                    while (columns.next()) {
                        String columnName = columns.getString("COLUMN_NAME");
                        String columnType = columns.getString("TYPE_NAME");
                        // Only search text-based columns
                        if (columnType.toLowerCase().contains("char") ||
                            columnType.toLowerCase().contains("text") ||
                            columnType.toLowerCase().contains("varchar")) {
                            columnNames.add(columnName);
                        }
                    }
                    columns.close();

                    if (columnNames.isEmpty()) {
                        continue;
                    }

                    // Build search query for this table
                    StringBuilder whereClause = new StringBuilder();
                    for (int i = 0; i < columnNames.size(); i++) {
                        if (i > 0) whereClause.append(" OR ");
                        whereClause.append("`").append(columnNames.get(i)).append("` LIKE ?");
                    }

                    String searchQuery = "SELECT COUNT(*) as count FROM `" + tableName + "` WHERE " + whereClause.toString();

                    try (PreparedStatement pstmt = conn.prepareStatement(searchQuery)) {
                        for (int i = 0; i < columnNames.size(); i++) {
                            pstmt.setString(i + 1, "%" + searchId + "%");
                        }

                        ResultSet rs = pstmt.executeQuery();
                        if (rs.next()) {
                            int count = rs.getInt("count");
                            if (count > 0) {
                                System.out.println("✓ FOUND in table: " + tableName + " (" + count + " rows)");
                                totalFound += count;

                                // Get sample rows
                                String sampleQuery = "SELECT * FROM `" + tableName + "` WHERE " + whereClause.toString() + " LIMIT 3";
                                try (PreparedStatement pstmt2 = conn.prepareStatement(sampleQuery)) {
                                    for (int i = 0; i < columnNames.size(); i++) {
                                        pstmt2.setString(i + 1, "%" + searchId + "%");
                                    }

                                    ResultSet rs2 = pstmt2.executeQuery();
                                    ResultSetMetaData rsmd = rs2.getMetaData();
                                    int colCount = rsmd.getColumnCount();

                                    System.out.println("  Sample data:");
                                    int rowNum = 0;
                                    while (rs2.next() && rowNum < 3) {
                                        rowNum++;
                                        System.out.println("  Row " + rowNum + ":");
                                        for (int i = 1; i <= colCount; i++) {
                                            String colName = rsmd.getColumnName(i);
                                            String value = rs2.getString(i);
                                            if (value != null && value.contains(searchId)) {
                                                System.out.println("    " + colName + ": " + value);
                                            }
                                        }
                                    }
                                }
                                System.out.println();
                            }
                        }
                    }

                } catch (SQLException e) {
                    // Skip tables we can't query
                    if (!e.getMessage().contains("doesn't exist")) {
                        System.out.println("  Error searching " + tableName + ": " + e.getMessage());
                    }
                }
            }

            System.out.println("\n=== SEARCH COMPLETE ===");
            System.out.println("Total rows found containing '" + searchId + "': " + totalFound);

            // Now do detailed queries on known PayProp tables
            System.out.println("\n=== DETAILED QUERY: payprop_report_icdn ===");
            String icdnQuery = """
                SELECT
                    payprop_id,
                    property_name,
                    property_payprop_id,
                    transaction_date,
                    transaction_type,
                    amount,
                    description
                FROM payprop_report_icdn
                WHERE property_payprop_id = ?
                ORDER BY transaction_date DESC
                LIMIT 10
                """;
            try (PreparedStatement pstmt = conn.prepareStatement(icdnQuery)) {
                pstmt.setString(1, searchId);
                ResultSet rs = pstmt.executeQuery();

                System.out.printf("%-20s %-25s %-15s %-20s %-12s%n",
                    "PAYPROP_ID", "PROPERTY_NAME", "DATE", "TYPE", "AMOUNT");
                System.out.println("-".repeat(100));

                while (rs.next()) {
                    System.out.printf("%-20s %-25s %-15s %-20s GBP%-9.2f%n",
                        rs.getString("payprop_id"),
                        rs.getString("property_name"),
                        rs.getDate("transaction_date"),
                        rs.getString("transaction_type"),
                        rs.getBigDecimal("amount"));
                }
            }

            System.out.println("\n=== DETAILED QUERY: payprop_export_properties ===");
            String propQuery = """
                SELECT *
                FROM payprop_export_properties
                WHERE payprop_id = ?
                """;
            try (PreparedStatement pstmt = conn.prepareStatement(propQuery)) {
                pstmt.setString(1, searchId);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    System.out.println("FOUND in payprop_export_properties:");
                    ResultSetMetaData rsmd = rs.getMetaData();
                    for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                        System.out.println("  " + rsmd.getColumnName(i) + ": " + rs.getString(i));
                    }
                } else {
                    System.out.println("NOT FOUND in payprop_export_properties");
                }
            }

            System.out.println("\n=== DETAILED QUERY: properties ===");
            String localPropQuery = """
                SELECT
                    id,
                    property_name,
                    payprop_id,
                    is_active,
                    created_at
                FROM properties
                WHERE payprop_id = ?
                """;
            try (PreparedStatement pstmt = conn.prepareStatement(localPropQuery)) {
                pstmt.setString(1, searchId);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    System.out.println("FOUND in local properties table:");
                    System.out.println("  ID: " + rs.getLong("id"));
                    System.out.println("  Name: " + rs.getString("property_name"));
                    System.out.println("  PayProp ID: " + rs.getString("payprop_id"));
                    System.out.println("  Active: " + rs.getBoolean("is_active"));
                    System.out.println("  Created: " + rs.getTimestamp("created_at"));
                } else {
                    System.out.println("NOT FOUND in local properties table");
                }
            }

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

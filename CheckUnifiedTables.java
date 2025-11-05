import java.sql.*;

public class CheckUnifiedTables {
    public static void main(String[] args) {
        String url = "jdbc:mysql://switchyard.proxy.rlwy.net:55090/railway";
        String user = "root";
        String password = "iRlCPXIbpytvKvaIAfJFZNYGEisxHHrW";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== CHECKING ALL PAYPROP TABLES ===\n");

            Statement stmt = conn.createStatement();

            // Get all payprop tables
            String tablesSql = "SHOW TABLES LIKE 'payprop%'";
            ResultSet tablesRs = stmt.executeQuery(tablesSql);

            java.util.ArrayList<String> tables = new java.util.ArrayList<>();
            while (tablesRs.next()) {
                tables.add(tablesRs.getString(1));
            }
            tablesRs.close();

            for (String tableName : tables) {
                System.out.println("TABLE: " + tableName);

                // Count rows in this table
                Statement countStmt = conn.createStatement();
                String countSql = "SELECT COUNT(*) as count FROM " + tableName;
                ResultSet countRs = countStmt.executeQuery(countSql);
                if (countRs.next()) {
                    int count = countRs.getInt("count");
                    System.out.println("  Rows: " + count);

                    if (count > 0) {
                        // Show sample data
                        Statement sampleStmt = conn.createStatement();
                        String sampleSql = "SELECT * FROM " + tableName + " LIMIT 1";
                        ResultSet sampleRs = sampleStmt.executeQuery(sampleSql);
                        ResultSetMetaData metaData = sampleRs.getMetaData();
                        int columnCount = metaData.getColumnCount();

                        System.out.println("  Sample columns:");
                        for (int i = 1; i <= columnCount; i++) {
                            System.out.println("    - " + metaData.getColumnName(i));
                        }
                        sampleRs.close();
                        sampleStmt.close();
                    }
                }
                countRs.close();
                countStmt.close();
                System.out.println();
            }

            // Check if there are batch_payments or historical tables
            System.out.println("=== CHECKING OTHER PAYMENT TABLES ===\n");
            String otherTablesSql = "SHOW TABLES LIKE '%payment%'";
            ResultSet otherTablesRs = stmt.executeQuery(otherTablesSql);

            java.util.ArrayList<String> otherTables = new java.util.ArrayList<>();
            while (otherTablesRs.next()) {
                otherTables.add(otherTablesRs.getString(1));
            }
            otherTablesRs.close();

            for (String tableName : otherTables) {
                if (!tableName.startsWith("payprop")) {
                    System.out.println("TABLE: " + tableName);

                    Statement countStmt = conn.createStatement();
                    String countSql = "SELECT COUNT(*) as count FROM " + tableName;
                    ResultSet countRs = countStmt.executeQuery(countSql);
                    if (countRs.next()) {
                        System.out.println("  Rows: " + countRs.getInt("count"));
                    }
                    countRs.close();
                    countStmt.close();
                    System.out.println();
                }
            }

            stmt.close();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

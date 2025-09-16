package site.easy.to.build.crm.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.FileReader;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Historical Data Import Service
 *
 * Imports CSV historical transaction data into the financial_transactions table
 * to make property owner statements work with real historical data.
 */
@Service
public class HistoricalDataImportService {

    private static final Logger log = LoggerFactory.getLogger(HistoricalDataImportService.class);

    @Autowired
    private DataSource dataSource;

    /**
     * Import historical data from CSV file
     */
    @Transactional
    public Map<String, Object> importHistoricalData(String csvFilePath) {
        log.info("🔄 Starting historical data import from: {}", csvFilePath);

        Map<String, Object> result = new HashMap<>();
        result.put("startTime", LocalDateTime.now());

        int totalProcessed = 0;
        int successfulInserts = 0;
        int errors = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(csvFilePath))) {
            // Skip header row
            String headerLine = reader.readLine();
            log.info("CSV Header: {}", headerLine);

            String line;
            while ((line = reader.readLine()) != null) {
                totalProcessed++;

                try {
                    if (importTransaction(line)) {
                        successfulInserts++;
                    }
                } catch (Exception e) {
                    errors++;
                    log.error("Failed to import line {}: {} - Error: {}", totalProcessed, line, e.getMessage());
                }
            }

            result.put("success", true);
            result.put("totalProcessed", totalProcessed);
            result.put("successfulInserts", successfulInserts);
            result.put("errors", errors);

            log.info("✅ Historical data import completed: {} processed, {} inserted, {} errors",
                totalProcessed, successfulInserts, errors);

        } catch (Exception e) {
            log.error("❌ Historical data import failed", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        result.put("endTime", LocalDateTime.now());
        return result;
    }

    /**
     * Import single transaction from CSV line
     */
    private boolean importTransaction(String csvLine) throws SQLException {
        // Parse CSV line: transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes
        String[] fields = parseCsvLine(csvLine);

        if (fields.length < 10) {
            log.warn("Skipping invalid CSV line (insufficient fields): {}", csvLine);
            return false;
        }

        try {
            String dateStr = fields[0].trim();
            String amountStr = fields[1].trim();
            String description = fields[2].trim().replaceAll("^\"|\"$", ""); // Remove quotes
            String transactionType = fields[3].trim();
            String category = fields[4].trim();
            String propertyReference = fields[5].trim().replaceAll("^\"|\"$", "");
            String customerReference = fields[6].trim().replaceAll("^\"|\"$", "");
            String bankReference = fields[7].trim().replaceAll("^\"|\"$", "");
            String paymentMethod = fields[8].trim();
            String notes = fields[9].trim();

            // Parse date
            LocalDate transactionDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            // Parse amount
            BigDecimal amount = new BigDecimal(amountStr);

            // Map to our financial_transactions table structure
            String insertSql = """
                INSERT INTO financial_transactions (
                    transaction_date, amount, description, transaction_type,
                    property_name, tenant_name, category_name, reference,
                    data_source, is_actual_transaction, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(insertSql)) {

                stmt.setDate(1, Date.valueOf(transactionDate));
                stmt.setBigDecimal(2, amount);
                stmt.setString(3, description);
                stmt.setString(4, mapTransactionType(transactionType));
                stmt.setString(5, propertyReference.isEmpty() ? null : propertyReference);
                stmt.setString(6, customerReference.isEmpty() ? null : customerReference);
                stmt.setString(7, category);
                stmt.setString(8, bankReference);
                stmt.setString(9, "HISTORICAL_IMPORT");
                stmt.setBoolean(10, true); // Mark as actual transaction
                stmt.setTimestamp(11, java.sql.Timestamp.valueOf(LocalDateTime.now()));
                stmt.setTimestamp(12, java.sql.Timestamp.valueOf(LocalDateTime.now()));

                int result = stmt.executeUpdate();

                if (result > 0) {
                    log.debug("✅ Imported: {} - {} - £{}", transactionDate, description, amount);
                    return true;
                } else {
                    log.warn("⚠️  No rows inserted for: {}", csvLine);
                    return false;
                }
            }

        } catch (Exception e) {
            log.error("Failed to parse transaction: {} - Error: {}", csvLine, e.getMessage());
            throw e;
        }
    }

    /**
     * Parse CSV line handling quoted fields with commas
     */
    private String[] parseCsvLine(String line) {
        // Simple CSV parser that handles quoted fields
        String[] fields = new String[10];
        int fieldIndex = 0;
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields[fieldIndex] = currentField.toString();
                currentField = new StringBuilder();
                fieldIndex++;
                if (fieldIndex >= 10) break;
            } else {
                currentField.append(c);
            }
        }

        // Add last field
        if (fieldIndex < 10) {
            fields[fieldIndex] = currentField.toString();
        }

        return fields;
    }

    /**
     * Map CSV transaction types to our system's types
     */
    private String mapTransactionType(String csvType) {
        switch (csvType.toLowerCase()) {
            case "deposit":
                return "CREDIT";
            case "fee":
                return "DEBIT";
            case "payment":
                return "CREDIT";
            default:
                return csvType.toUpperCase();
        }
    }

    /**
     * Clear all historical import data (for reimport)
     */
    @Transactional
    public int clearHistoricalData() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "DELETE FROM financial_transactions WHERE data_source = 'HISTORICAL_IMPORT'")) {

            int deletedCount = stmt.executeUpdate();
            log.info("🧹 Cleared {} historical import records", deletedCount);
            return deletedCount;

        } catch (SQLException e) {
            log.error("Failed to clear historical data", e);
            throw new RuntimeException("Failed to clear historical data", e);
        }
    }
}
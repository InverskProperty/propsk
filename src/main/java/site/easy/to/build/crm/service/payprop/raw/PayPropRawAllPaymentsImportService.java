package site.easy.to.build.crm.service.payprop.raw;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.easy.to.build.crm.service.payprop.PayPropApiClient;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * PayProp Raw All Payments Import Service
 * 
 * Imports raw payment transaction data from /report/all-payments directly into payprop_report_all_payments table.
 * ZERO BUSINESS LOGIC - stores exactly as PayProp returns.
 * 
 * This contains the actual payment flows - incoming payments, commissions, distributions.
 * CRITICAL: This is where the missing £1,100 payment should be found!
 */
@Service
public class PayPropRawAllPaymentsImportService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropRawAllPaymentsImportService.class);
    
    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private PayPropImportIssueTracker issueTracker;
    
    /**
     * Import all payment transactions from PayProp /report/all-payments endpoint
     * Uses 93-day filter to get recent payments
     */
    @Transactional
    public PayPropRawImportResult importAllPayments() {
        log.info("🔄 Starting raw all-payments import from PayProp");
        
        PayPropRawImportResult result = new PayPropRawImportResult();
        result.setStartTime(LocalDateTime.now());
        result.setEndpoint("/report/all-payments");
        
        try {
            // Fetch ALL payment transactions using historical chunking to handle 93-day limit
            // PayProp's /report/all-payments endpoint requires date ranges
            String baseEndpoint = "/report/all-payments?filter_by=reconciliation_date&include_beneficiary_info=true";
                
            log.info("🔄 Starting COMPLETE all-payments import using 93-day historical chunking");
            List<Map<String, Object>> payments = apiClient.fetchHistoricalPages(
                baseEndpoint, 
                2, // 2 years back 
                this::processPaymentItem
            );
            
            result.setTotalFetched(payments.size());
            log.info("📦 PayProp API returned: {} payment transactions", payments.size());
            
            // Import to database
            int importedCount = importPaymentsToDatabase(payments);
            result.setTotalImported(importedCount);
            
            result.setSuccess(true);
            result.setEndTime(LocalDateTime.now());
            result.setDetails(String.format("All payments imported: %d fetched, %d imported", 
                payments.size(), importedCount));
            
            log.info("✅ Raw all-payments import completed: {} fetched, {} imported", 
                payments.size(), importedCount);
            
        } catch (Exception e) {
            log.error("❌ Raw all-payments import failed", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }
        
        return result;
    }
    
    /**
     * Process individual payment item from API
     */
    private Map<String, Object> processPaymentItem(Map<String, Object> payment) {
        log.debug("Payment ID: {} Amount: {} Type: {} Status: {}", 
            payment.get("id"), 
            payment.get("amount"), 
            payment.get("type"),
            payment.get("status"));
        return payment;
    }
    
    /**
     * Import payments to database with exact PayProp structure
     */
    private int importPaymentsToDatabase(List<Map<String, Object>> payments) throws SQLException {
        
        // Clear existing data for fresh import
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM payprop_report_all_payments")) {
                int deletedCount = stmt.executeUpdate();
                log.info("Cleared {} existing payment transactions for fresh import", deletedCount);
            }
        }
        
        if (payments.isEmpty()) {
            log.warn("No payment transactions to import");
            return 0;
        }
        
        String insertSql = """
            INSERT INTO payprop_report_all_payments (
                payprop_id, amount, description, due_date, has_tax, reference,
                service_fee, transaction_fee, tax_amount, part_of_amount,
                beneficiary_payprop_id, beneficiary_name, beneficiary_type,
                category_payprop_id, category_name, incoming_transaction_id,
                incoming_transaction_amount, incoming_transaction_deposit_id, incoming_transaction_reconciliation_date,
                incoming_transaction_status, incoming_transaction_type, bank_statement_date, bank_statement_id,
                incoming_property_payprop_id, incoming_property_name, incoming_tenant_payprop_id, incoming_tenant_name,
                payment_batch_id, payment_batch_amount, payment_batch_status, payment_batch_transfer_date,
                payment_instruction_id, secondary_payment_is_child, secondary_payment_is_parent, secondary_payment_parent_id,
                reconciliation_date, sync_status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        int attemptedCount = 0;
        int actuallyInserted = 0;
        int emptyIdSkipped = 0;
        int mappingErrors = 0;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            
            for (Map<String, Object> payment : payments) {
                try {
                    String paymentId = getStringValue(payment, "id");
                    
                    // Handle empty/null IDs (PayProp data quality issue)
                    if (paymentId == null || paymentId.trim().isEmpty()) {
                        emptyIdSkipped++;
                        issueTracker.recordIssue(
                            PayPropImportIssueTracker.EMPTY_ID,
                            "/report/all-payments",
                            paymentId,
                            payment,
                            "PayProp sent payment record without ID",
                            PayPropImportIssueTracker.FINANCIAL_DATA_MISSING
                        );
                        continue;
                    }
                    
                    setPaymentParameters(stmt, payment);
                    stmt.addBatch();
                    attemptedCount++;
                    
                    // Execute batch every 25 items
                    if (attemptedCount % 25 == 0) {
                        int batchInserted = executeBatchWithAccurateCount(stmt, attemptedCount);
                        actuallyInserted += batchInserted;
                    }
                    
                } catch (Exception e) {
                    mappingErrors++;
                    issueTracker.recordIssue(
                        PayPropImportIssueTracker.MAPPING_ERROR,
                        "/report/all-payments",
                        getStringValue(payment, "id"),
                        payment,
                        e.getMessage(),
                        PayPropImportIssueTracker.FINANCIAL_DATA_MISSING
                    );
                    log.error("Failed to prepare payment for import: {}", 
                        payment.get("id"), e);
                }
            }
            
            // Execute remaining batch
            if (attemptedCount % 25 != 0) {
                int finalBatchInserted = executeBatchWithAccurateCount(stmt, attemptedCount);
                actuallyInserted += finalBatchInserted;
            }
            
            // Provide accurate summary
            log.info("📊 ACCURATE IMPORT SUMMARY:");
            log.info("   Total fetched from API: {}", payments.size());
            log.info("   Skipped (empty ID): {}", emptyIdSkipped);
            log.info("   Mapping errors: {}", mappingErrors);
            log.info("   Attempted to insert: {}", attemptedCount);
            log.info("   Actually inserted: {}", actuallyInserted);
            log.info("   Failed FK constraints: {}", attemptedCount - actuallyInserted);
            
            // Update the final count with ACTUAL inserts, not attempts
            importedCount = actuallyInserted;
        }
        
        return importedCount;
    }
    
    /**
     * Set parameters for payment insert statement
     */
    private void setPaymentParameters(PreparedStatement stmt, Map<String, Object> payment) 
            throws SQLException {
        
        int paramIndex = 1;
        
        // Map PayProp API fields to database columns (37 total parameters)
        stmt.setString(paramIndex++, getStringValue(payment, "id")); // payprop_id
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(payment, "amount")); // amount
        stmt.setString(paramIndex++, getStringValue(payment, "description")); // description
        stmt.setDate(paramIndex++, getDateValue(payment, "due_date")); // due_date
        setBooleanParameter(stmt, paramIndex++, getBooleanValue(payment, "has_tax")); // has_tax
        stmt.setString(paramIndex++, getStringValue(payment, "reference")); // reference
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(payment, "service_fee")); // service_fee
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(payment, "transaction_fee")); // transaction_fee
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(payment, "tax_amount")); // tax_amount
        stmt.setBigDecimal(paramIndex++, getBigDecimalValue(payment, "part_of_amount")); // part_of_amount
        
        // Beneficiary info
        stmt.setString(paramIndex++, getNestedStringValue(payment, "beneficiary", "id")); // beneficiary_payprop_id
        stmt.setString(paramIndex++, getNestedStringValue(payment, "beneficiary", "name")); // beneficiary_name
        stmt.setString(paramIndex++, getNestedStringValue(payment, "beneficiary", "type")); // beneficiary_type
        
        // Category info  
        stmt.setString(paramIndex++, getNestedStringValue(payment, "category", "id")); // category_payprop_id
        stmt.setString(paramIndex++, getNestedStringValue(payment, "category", "name")); // category_name
        
        // Incoming transaction details
        stmt.setString(paramIndex++, getNestedStringValue(payment, "incoming_transaction", "id")); // incoming_transaction_id
        stmt.setBigDecimal(paramIndex++, getNestedBigDecimalValue(payment, "incoming_transaction", "amount")); // incoming_transaction_amount
        stmt.setString(paramIndex++, getNestedStringValue(payment, "incoming_transaction", "deposit_id")); // incoming_transaction_deposit_id
        stmt.setDate(paramIndex++, getNestedDateValue(payment, "incoming_transaction", "reconciliation_date")); // incoming_transaction_reconciliation_date
        stmt.setString(paramIndex++, getNestedStringValue(payment, "incoming_transaction", "status")); // incoming_transaction_status
        stmt.setString(paramIndex++, getNestedStringValue(payment, "incoming_transaction", "type")); // incoming_transaction_type
        
        // Bank statement info
        stmt.setDate(paramIndex++, getNestedDateValue(payment, "bank_statement", "date")); // bank_statement_date
        stmt.setString(paramIndex++, getNestedStringValue(payment, "bank_statement", "id")); // bank_statement_id
        
        // Property info from incoming transaction
        stmt.setString(paramIndex++, getNestedStringValue(payment, "incoming_transaction", "property", "id")); // incoming_property_payprop_id
        stmt.setString(paramIndex++, getNestedStringValue(payment, "incoming_transaction", "property", "name")); // incoming_property_name
        
        // Tenant info from incoming transaction  
        stmt.setString(paramIndex++, getNestedStringValue(payment, "incoming_transaction", "tenant", "id")); // incoming_tenant_payprop_id
        stmt.setString(paramIndex++, getNestedStringValue(payment, "incoming_transaction", "tenant", "name")); // incoming_tenant_name
        
        // Payment batch info
        stmt.setString(paramIndex++, getNestedStringValue(payment, "payment_batch", "id")); // payment_batch_id
        stmt.setBigDecimal(paramIndex++, getNestedBigDecimalValue(payment, "payment_batch", "amount")); // payment_batch_amount
        stmt.setString(paramIndex++, getNestedStringValue(payment, "payment_batch", "status")); // payment_batch_status
        stmt.setDate(paramIndex++, getNestedDateValue(payment, "payment_batch", "transfer_date")); // payment_batch_transfer_date
        
        // Payment instruction and secondary payment info
        stmt.setString(paramIndex++, getNestedStringValue(payment, "payment_instruction", "id")); // payment_instruction_id
        setBooleanParameter(stmt, paramIndex++, getNestedBooleanValue(payment, "secondary_payment", "is_child")); // secondary_payment_is_child
        setBooleanParameter(stmt, paramIndex++, getNestedBooleanValue(payment, "secondary_payment", "is_parent")); // secondary_payment_is_parent
        stmt.setString(paramIndex++, getNestedStringValue(payment, "secondary_payment", "parent_payment_id")); // secondary_payment_parent_id
        
        // Final fields
        stmt.setDate(paramIndex++, getDateValue(payment, "reconciliation_date")); // reconciliation_date
        stmt.setString(paramIndex++, "active"); // sync_status (default)
    }
    
    // Helper methods (same as other import services)
    private String getStringValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        return map.get(key).toString();
    }
    
    private BigDecimal getBigDecimalValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        try {
            return new BigDecimal(map.get(key).toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to convert {} to BigDecimal: {}", key, map.get(key));
            return null;
        }
    }
    
    private Boolean getBooleanValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }
    
    private java.sql.Date getDateValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        try {
            String dateStr = map.get(key).toString();
            return java.sql.Date.valueOf(dateStr);
        } catch (Exception e) {
            log.warn("Failed to convert {} to Date: {}", key, map.get(key));
            return null;
        }
    }
    
    private Timestamp getTimestampValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        try {
            String timestampStr = map.get(key).toString();
            return Timestamp.valueOf(timestampStr);
        } catch (Exception e) {
            log.warn("Failed to convert {} to Timestamp: {}", key, map.get(key));
            return null;
        }
    }
    
    /**
     * Helper method to safely set boolean parameters, handling null values
     */
    private void setBooleanParameter(PreparedStatement stmt, int paramIndex, Boolean value) 
            throws SQLException {
        if (value == null) {
            stmt.setNull(paramIndex, java.sql.Types.BOOLEAN);
        } else {
            stmt.setBoolean(paramIndex, value);
        }
    }
    
    // Nested value helper methods for complex PayProp data structures
    private String getNestedStringValue(Map<String, Object> map, String... keys) {
        if (map == null || keys.length == 0) return null;
        
        Object current = map;
        for (String key : keys) {
            if (!(current instanceof Map)) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> currentMap = (Map<String, Object>) current;
            current = currentMap.get(key);
            if (current == null) return null;
        }
        return current.toString();
    }
    
    private BigDecimal getNestedBigDecimalValue(Map<String, Object> map, String... keys) {
        if (map == null || keys.length == 0) return null;
        
        Object current = map;
        for (String key : keys) {
            if (!(current instanceof Map)) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> currentMap = (Map<String, Object>) current;
            current = currentMap.get(key);
            if (current == null) return null;
        }
        try {
            return new BigDecimal(current.toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to convert nested value to BigDecimal: {}", current);
            return null;
        }
    }
    
    private java.sql.Date getNestedDateValue(Map<String, Object> map, String... keys) {
        if (map == null || keys.length == 0) return null;
        
        Object current = map;
        for (String key : keys) {
            if (!(current instanceof Map)) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> currentMap = (Map<String, Object>) current;
            current = currentMap.get(key);
            if (current == null) return null;
        }
        try {
            String dateStr = current.toString();
            return java.sql.Date.valueOf(dateStr);
        } catch (Exception e) {
            log.warn("Failed to convert nested value to Date: {}", current);
            return null;
        }
    }
    
    private Boolean getNestedBooleanValue(Map<String, Object> map, String... keys) {
        if (map == null || keys.length == 0) return null;
        
        Object current = map;
        for (String key : keys) {
            if (!(current instanceof Map)) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> currentMap = (Map<String, Object>) current;
            current = currentMap.get(key);
            if (current == null) return null;
        }
        
        if (current instanceof Boolean) {
            return (Boolean) current;
        }
        return Boolean.parseBoolean(current.toString());
    }
    
    /**
     * Execute batch with accurate success counting and detailed error reporting
     * Returns the actual number of successfully inserted records
     */
    private int executeBatchWithAccurateCount(PreparedStatement stmt, int batchCount) {
        try {
            int[] updateCounts = stmt.executeBatch();
            int successfulInserts = 0;
            
            // Count actual successful inserts
            for (int count : updateCounts) {
                if (count > 0) successfulInserts++;
            }
            
            log.debug("✅ Batch {}: {} records attempted, {} successfully inserted", 
                batchCount, updateCounts.length, successfulInserts);
            return successfulInserts;
            
        } catch (java.sql.BatchUpdateException e) {
            int[] updateCounts = e.getUpdateCounts();
            int successfulInserts = 0;
            int failedInserts = 0;
            
            // Count what actually succeeded vs failed
            for (int count : updateCounts) {
                if (count > 0) {
                    successfulInserts++;
                } else if (count == PreparedStatement.EXECUTE_FAILED) {
                    failedInserts++;
                }
            }
            
            // Detailed error analysis
            String errorMessage = e.getMessage();
            if (errorMessage.contains("foreign key constraint fails")) {
                // Extract which FK constraint failed
                String constraintDetails = extractConstraintDetails(errorMessage);
                
                log.warn("⚠️  Batch {}: FK constraint failures - {}", batchCount, constraintDetails);
                log.warn("   {} succeeded, {} failed due to missing references", successfulInserts, failedInserts);
                
                issueTracker.recordIssue(
                    PayPropImportIssueTracker.CONSTRAINT_VIOLATION,
                    "/report/all-payments",
                    "batch-" + batchCount,
                    null,
                    String.format("FK constraint failure: %s. %d succeeded, %d failed", 
                        constraintDetails, successfulInserts, failedInserts),
                    PayPropImportIssueTracker.FINANCIAL_DATA_MISSING
                );
                
            } else {
                log.error("❌ Batch {}: Non-FK error - {}", batchCount, errorMessage);
                throw new RuntimeException("Non-foreign-key batch failure: " + errorMessage, e);
            }
            
            return successfulInserts;
            
        } catch (Exception e) {
            log.error("❌ Batch {}: Unexpected error - {}", batchCount, e.getMessage());
            throw new RuntimeException("Unexpected batch execution failure: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extract specific foreign key constraint details from error message
     */
    private String extractConstraintDetails(String errorMessage) {
        if (errorMessage.contains("payment_instruction_id")) {
            return "payment_instruction_id → payprop_export_invoices (Missing invoice instructions)";
        } else if (errorMessage.contains("incoming_property_payprop_id")) {
            return "incoming_property_payprop_id → payprop_export_properties (Missing properties)";
        } else {
            // Try to extract constraint name from message
            if (errorMessage.contains("CONSTRAINT `")) {
                int start = errorMessage.indexOf("CONSTRAINT `") + 12;
                int end = errorMessage.indexOf("`", start);
                if (end > start) {
                    return "Constraint: " + errorMessage.substring(start, end);
                }
            }
            return "Unknown FK constraint";
        }
    }
}
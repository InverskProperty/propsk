package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import site.easy.to.build.crm.repository.*;
import site.easy.to.build.crm.service.payprop.PayPropRealTimeSyncService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PayProp Sync Monitoring Service
 * Monitors sync health, tracks issues, and provides comprehensive reporting
 */
@Service
public class PayPropSyncMonitoringService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropSyncMonitoringService.class);
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private PaymentRepository paymentRepository;
    
    @Autowired
    private CustomerRepository customerRepository;
    
    @Autowired
    private PropertyRepository propertyRepository;
    
    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;
    
    @Autowired(required = false)
    private PayPropRealTimeSyncService realTimeSyncService;
    
    /**
     * Generate comprehensive health report
     */
    public SyncHealthReport generateHealthReport() {
        SyncHealthReport report = new SyncHealthReport();
        
        try {
            // Check orphaned entities
            report.setOrphanedProperties(countOrphanedProperties());
            report.setOrphanedTenants(countOrphanedTenants());
            report.setOrphanedBeneficiaries(countOrphanedBeneficiaries());
            
            // Check sync rates
            report.setPropertySyncRate(calculatePropertySyncRate());
            report.setCustomerSyncRate(calculateCustomerSyncRate());
            report.setPaymentSyncRate(calculatePaymentSyncRate());
            report.setTransactionSyncRate(calculateTransactionSyncRate());
            
            // Check data quality
            report.setPropertiesWithoutRent(countPropertiesWithoutRent());
            report.setPropertiesWithoutOwner(countPropertiesWithoutOwner());
            report.setCustomersWithoutEmail(countCustomersWithoutEmail());
            report.setTransactionsWithoutProperty(countTransactionsWithoutProperty());
            
            // Check recent sync activity
            report.setLastSyncTime(getLastSyncTime());
            report.setRecentErrors(getRecentSyncErrors());
            report.setRecentSyncCount(getRecentSyncCount());
            
            // Financial health
            report.setTotalTransactionAmount(getTotalTransactionAmount());
            report.setAverageCommissionRate(getAverageCommissionRate());
            report.setMissingCommissions(countMissingCommissions());
            
            // Calculate overall health score
            report.calculateHealthScore();
            
        } catch (Exception e) {
            log.error("Error generating health report: {}", e.getMessage(), e);
            report.setHealthScore(0);
            report.addError("Failed to generate complete health report: " + e.getMessage());
        }
        
        return report;
    }
    
    /**
     * Generate real-time sync health report
     */
    public RealTimeSyncReport generateRealTimeSyncReport() {
        RealTimeSyncReport report = new RealTimeSyncReport();
        
        try {
            // Check if real-time sync is enabled and healthy
            if (realTimeSyncService != null) {
                report.setEnabled(true);
                report.setHealthy(realTimeSyncService.isHealthy());
                
                // Get sync statistics
                Map<String, Object> stats = realTimeSyncService.getSyncStatistics();
                report.setCircuitBreakerOpen((Boolean) stats.get("circuit_breaker_open"));
                report.setConsecutiveFailures((Integer) stats.get("consecutive_failures"));
                report.setActiveSyncOperations((Integer) stats.get("active_sync_operations"));
                report.setRateLimitPerSecond((Double) stats.get("rate_limit_permits_per_second"));
                
                // Calculate real-time sync efficiency
                report.setRealtimeSyncRate(calculateRealtimeSyncRate());
                report.setBatchFallbackRate(calculateBatchFallbackRate());
                
            } else {
                report.setEnabled(false);
                report.setHealthy(false);
            }
            
            // Check recent ticket updates
            report.setRecentCriticalUpdates(countRecentCriticalUpdates());
            report.setRecentTicketUpdates(countRecentTicketUpdates());
            
        } catch (Exception e) {
            log.error("Error generating real-time sync report: {}", e.getMessage(), e);
            report.setHealthy(false);
            report.addError("Failed to generate real-time sync report: " + e.getMessage());
        }
        
        return report;
    }
    
    /**
     * Calculate real-time sync success rate
     */
    private double calculateRealtimeSyncRate() {
        String sql = """
            SELECT 
                COUNT(*) as total,
                SUM(CASE WHEN pay_prop_synced = 1 THEN 1 ELSE 0 END) as synced
            FROM trigger_ticket 
            WHERE type = 'maintenance' 
            AND pay_prop_ticket_id IS NOT NULL
            AND updated_at >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
            AND status IN ('resolved', 'in-progress')
        """;
        
        try {
            Map<String, Object> result = jdbcTemplate.queryForMap(sql);
            int total = ((Number) result.get("total")).intValue();
            int synced = ((Number) result.get("synced")).intValue();
            
            return total > 0 ? (synced * 100.0 / total) : 100.0;
        } catch (Exception e) {
            log.warn("Could not calculate real-time sync rate: {}", e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * Calculate batch fallback rate
     */
    private double calculateBatchFallbackRate() {
        String sql = """
            SELECT 
                COUNT(*) as total,
                SUM(CASE WHEN pay_prop_synced = 0 THEN 1 ELSE 0 END) as unsynced
            FROM trigger_ticket 
            WHERE type = 'maintenance' 
            AND pay_prop_ticket_id IS NOT NULL
            AND updated_at >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
            AND status IN ('resolved', 'in-progress')
        """;
        
        try {
            Map<String, Object> result = jdbcTemplate.queryForMap(sql);
            int total = ((Number) result.get("total")).intValue();
            int unsynced = ((Number) result.get("unsynced")).intValue();
            
            return total > 0 ? (unsynced * 100.0 / total) : 0.0;
        } catch (Exception e) {
            log.warn("Could not calculate batch fallback rate: {}", e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * Count recent critical ticket updates
     */
    private int countRecentCriticalUpdates() {
        String sql = """
            SELECT COUNT(*) 
            FROM trigger_ticket 
            WHERE type = 'maintenance' 
            AND updated_at >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
            AND (status IN ('resolved', 'in-progress') OR urgency_level = 'emergency')
        """;
        
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }
    
    /**
     * Count recent ticket updates
     */
    private int countRecentTicketUpdates() {
        String sql = """
            SELECT COUNT(*) 
            FROM trigger_ticket 
            WHERE type = 'maintenance' 
            AND updated_at >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
        """;
        
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }
    
    /**
     * Count orphaned properties
     */
    private int countOrphanedProperties() {
        String sql = """
            SELECT COUNT(DISTINCT ft.property_id) 
            FROM financial_transactions ft 
            LEFT JOIN properties p ON p.payprop_id = ft.property_id 
            WHERE ft.property_id IS NOT NULL AND p.id IS NULL
        """;
        
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }
    
    /**
     * Count orphaned tenants
     */
    private int countOrphanedTenants() {
        String sql = """
            SELECT COUNT(DISTINCT ft.tenant_id) 
            FROM financial_transactions ft 
            LEFT JOIN customers c ON c.payprop_entity_id = ft.tenant_id 
            WHERE ft.tenant_id IS NOT NULL AND c.customer_id IS NULL
        """;
        
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }
    
    /**
     * Count orphaned beneficiaries
     */
    private int countOrphanedBeneficiaries() {
        String sql = """
            SELECT COUNT(DISTINCT p.beneficiary_id) 
            FROM payments p 
            LEFT JOIN customers c ON c.customer_id = p.beneficiary_id 
            WHERE p.beneficiary_id IS NOT NULL AND c.customer_id IS NULL
        """;
        
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }
    
    /**
     * Calculate property sync rate
     */
    private double calculatePropertySyncRate() {
        String sql = """
            SELECT 
                COUNT(*) as total,
                SUM(CASE WHEN payprop_id IS NOT NULL THEN 1 ELSE 0 END) as synced
            FROM properties
        """;
        
        Map<String, Object> result = jdbcTemplate.queryForMap(sql);
        int total = ((Number) result.get("total")).intValue();
        int synced = ((Number) result.get("synced")).intValue();
        
        return total > 0 ? (synced * 100.0 / total) : 0;
    }
    
    /**
     * Calculate customer sync rate
     */
    private double calculateCustomerSyncRate() {
        String sql = """
            SELECT 
                COUNT(*) as total,
                SUM(CASE WHEN payprop_entity_id IS NOT NULL THEN 1 ELSE 0 END) as synced
            FROM customers
        """;
        
        Map<String, Object> result = jdbcTemplate.queryForMap(sql);
        int total = ((Number) result.get("total")).intValue();
        int synced = ((Number) result.get("synced")).intValue();
        
        return total > 0 ? (synced * 100.0 / total) : 0;
    }
    
    /**
     * Calculate payment sync rate
     */
    private double calculatePaymentSyncRate() {
        String sql = """
            SELECT 
                COUNT(*) as total,
                SUM(CASE WHEN pay_prop_payment_id IS NOT NULL THEN 1 ELSE 0 END) as synced
            FROM payments
        """;
        
        Map<String, Object> result = jdbcTemplate.queryForMap(sql);
        int total = ((Number) result.get("total")).intValue();
        int synced = ((Number) result.get("synced")).intValue();
        
        return total > 0 ? (synced * 100.0 / total) : 0;
    }
    
    /**
     * Calculate transaction sync rate
     */
    private double calculateTransactionSyncRate() {
        String sql = """
            SELECT 
                COUNT(*) as total,
                SUM(CASE WHEN pay_prop_transaction_id IS NOT NULL THEN 1 ELSE 0 END) as synced
            FROM financial_transactions
        """;
        
        Map<String, Object> result = jdbcTemplate.queryForMap(sql);
        int total = ((Number) result.get("total")).intValue();
        int synced = ((Number) result.get("synced")).intValue();
        
        return total > 0 ? (synced * 100.0 / total) : 0;
    }
    
    /**
     * Count properties without rent amount
     */
    private int countPropertiesWithoutRent() {
        String sql = """
            SELECT COUNT(*) 
            FROM properties 
            WHERE monthly_payment IS NULL OR monthly_payment = 0
        """;
        
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }
    
    /**
     * Count properties without owner
     */
    private int countPropertiesWithoutOwner() {
        String sql = """
            SELECT COUNT(DISTINCT p.id)
            FROM properties p
            LEFT JOIN customer_property_assignments cpa ON p.id = cpa.property_id 
                AND cpa.assignment_type = 'OWNER'
            WHERE cpa.id IS NULL
        """;
        
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }
    
    /**
     * Count customers without email
     */
    private int countCustomersWithoutEmail() {
        String sql = """
            SELECT COUNT(*) 
            FROM customers 
            WHERE (email IS NULL OR email = '') 
            AND payprop_entity_id IS NOT NULL
        """;
        
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }
    
    /**
     * Count transactions without property
     */
    private int countTransactionsWithoutProperty() {
        String sql = """
            SELECT COUNT(*) 
            FROM financial_transactions 
            WHERE property_id IS NULL
        """;
        
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }
    
    /**
     * Get last sync time
     */
    private LocalDateTime getLastSyncTime() {
        String sql = """
            SELECT MAX(created_at) 
            FROM financial_transactions
        """;
        
        return jdbcTemplate.queryForObject(sql, LocalDateTime.class);
    }
    
    /**
     * Get recent sync errors (placeholder - would need error logging table)
     */
    private List<String> getRecentSyncErrors() {
        // In a real implementation, this would query an error log table
        return new ArrayList<>();
    }
    
    /**
     * Get count of recently synced records
     */
    private int getRecentSyncCount() {
        String sql = """
            SELECT COUNT(*) 
            FROM financial_transactions 
            WHERE created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
        """;
        
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }
    
    /**
     * Get total transaction amount
     */
    private double getTotalTransactionAmount() {
        String sql = """
            SELECT COALESCE(SUM(amount), 0) 
            FROM financial_transactions 
            WHERE is_actual_transaction = 1
        """;
        
        Number amount = jdbcTemplate.queryForObject(sql, Number.class);
        return amount != null ? amount.doubleValue() : 0;
    }
    
    /**
     * Get average commission rate
     */
    private double getAverageCommissionRate() {
        String sql = """
            SELECT AVG(commission_rate) 
            FROM financial_transactions 
            WHERE commission_rate IS NOT NULL AND commission_rate > 0
        """;
        
        Number rate = jdbcTemplate.queryForObject(sql, Number.class);
        return rate != null ? rate.doubleValue() : 0;
    }
    
    /**
     * Count missing commissions
     */
    private int countMissingCommissions() {
        String sql = """
            SELECT COUNT(*) 
            FROM financial_transactions 
            WHERE transaction_type = 'invoice' 
            AND category_name NOT LIKE '%deposit%'
            AND (commission_rate IS NULL OR calculated_commission_amount IS NULL)
        """;
        
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }
    
    /**
     * Real-Time Sync Report DTO
     */
    public static class RealTimeSyncReport {
        private boolean enabled;
        private boolean healthy;
        private boolean circuitBreakerOpen;
        private int consecutiveFailures;
        private int activeSyncOperations;
        private double rateLimitPerSecond;
        private double realtimeSyncRate;
        private double batchFallbackRate;
        private int recentCriticalUpdates;
        private int recentTicketUpdates;
        private List<String> errors = new ArrayList<>();
        
        public String getHealthStatus() {
            if (!enabled) return "DISABLED";
            if (!healthy) return "UNHEALTHY";
            if (circuitBreakerOpen) return "CIRCUIT_OPEN";
            if (realtimeSyncRate < 80) return "DEGRADED";
            return "HEALTHY";
        }
        
        public String getHealthDescription() {
            String status = getHealthStatus();
            switch (status) {
                case "DISABLED":
                    return "Real-time sync is disabled - only batch sync active";
                case "UNHEALTHY":
                    return "Real-time sync is experiencing issues";
                case "CIRCUIT_OPEN":
                    return "Circuit breaker is open - all updates falling back to batch";
                case "DEGRADED":
                    return "Real-time sync is working but with reduced efficiency";
                case "HEALTHY":
                    return "Real-time sync is operating normally";
                default:
                    return "Unknown status";
            }
        }
        
        public void addError(String error) {
            if (errors == null) {
                errors = new ArrayList<>();
            }
            errors.add(error);
        }
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public boolean isHealthy() { return healthy; }
        public void setHealthy(boolean healthy) { this.healthy = healthy; }
        
        public boolean isCircuitBreakerOpen() { return circuitBreakerOpen; }
        public void setCircuitBreakerOpen(boolean circuitBreakerOpen) { this.circuitBreakerOpen = circuitBreakerOpen; }
        
        public int getConsecutiveFailures() { return consecutiveFailures; }
        public void setConsecutiveFailures(int consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }
        
        public int getActiveSyncOperations() { return activeSyncOperations; }
        public void setActiveSyncOperations(int activeSyncOperations) { this.activeSyncOperations = activeSyncOperations; }
        
        public double getRateLimitPerSecond() { return rateLimitPerSecond; }
        public void setRateLimitPerSecond(double rateLimitPerSecond) { this.rateLimitPerSecond = rateLimitPerSecond; }
        
        public double getRealtimeSyncRate() { return realtimeSyncRate; }
        public void setRealtimeSyncRate(double realtimeSyncRate) { this.realtimeSyncRate = realtimeSyncRate; }
        
        public double getBatchFallbackRate() { return batchFallbackRate; }
        public void setBatchFallbackRate(double batchFallbackRate) { this.batchFallbackRate = batchFallbackRate; }
        
        public int getRecentCriticalUpdates() { return recentCriticalUpdates; }
        public void setRecentCriticalUpdates(int recentCriticalUpdates) { this.recentCriticalUpdates = recentCriticalUpdates; }
        
        public int getRecentTicketUpdates() { return recentTicketUpdates; }
        public void setRecentTicketUpdates(int recentTicketUpdates) { this.recentTicketUpdates = recentTicketUpdates; }
        
        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }
    }
    
    /**
     * Sync Health Report DTO
     */
    public static class SyncHealthReport {
        private int orphanedProperties;
        private int orphanedTenants;
        private int orphanedBeneficiaries;
        private double propertySyncRate;
        private double customerSyncRate;
        private double paymentSyncRate;
        private double transactionSyncRate;
        private int propertiesWithoutRent;
        private int propertiesWithoutOwner;
        private int customersWithoutEmail;
        private int transactionsWithoutProperty;
        private LocalDateTime lastSyncTime;
        private List<String> recentErrors = new ArrayList<>();
        private int recentSyncCount;
        private double totalTransactionAmount;
        private double averageCommissionRate;
        private int missingCommissions;
        private int healthScore; // 0-100
        
        public void calculateHealthScore() {
            int score = 100;
            
            // Deduct for orphaned entities (max -30 points)
            if (orphanedProperties > 0) score -= Math.min(10, orphanedProperties);
            if (orphanedTenants > 0) score -= Math.min(10, orphanedTenants);
            if (orphanedBeneficiaries > 0) score -= Math.min(10, orphanedBeneficiaries);
            
            // Deduct for low sync rates (max -20 points)
            if (paymentSyncRate < 50) score -= 10;
            else if (paymentSyncRate < 80) score -= 5;
            
            if (transactionSyncRate < 50) score -= 10;
            else if (transactionSyncRate < 80) score -= 5;
            
            // Deduct for data quality issues (max -30 points)
            if (propertiesWithoutRent > 10) score -= 10;
            if (propertiesWithoutOwner > 5) score -= 10;
            if (customersWithoutEmail > 20) score -= 5;
            if (transactionsWithoutProperty > 50) score -= 5;
            
            // Deduct for missing commissions (max -10 points)
            if (missingCommissions > 100) score -= 10;
            else if (missingCommissions > 50) score -= 5;
            
            // Deduct for recent errors (max -10 points)
            if (recentErrors != null && recentErrors.size() > 5) score -= 10;
            else if (recentErrors != null && recentErrors.size() > 0) score -= 5;
            
            this.healthScore = Math.max(0, score);
        }
        
        public String getHealthStatus() {
            if (healthScore >= 90) return "EXCELLENT";
            if (healthScore >= 70) return "GOOD";
            if (healthScore >= 50) return "FAIR";
            if (healthScore >= 30) return "POOR";
            return "CRITICAL";
        }
        
        public void addError(String error) {
            if (recentErrors == null) {
                recentErrors = new ArrayList<>();
            }
            recentErrors.add(error);
        }
        
        // Getters and setters
        public int getOrphanedProperties() { return orphanedProperties; }
        public void setOrphanedProperties(int orphanedProperties) { this.orphanedProperties = orphanedProperties; }
        
        public int getOrphanedTenants() { return orphanedTenants; }
        public void setOrphanedTenants(int orphanedTenants) { this.orphanedTenants = orphanedTenants; }
        
        public int getOrphanedBeneficiaries() { return orphanedBeneficiaries; }
        public void setOrphanedBeneficiaries(int orphanedBeneficiaries) { this.orphanedBeneficiaries = orphanedBeneficiaries; }
        
        public double getPropertySyncRate() { return propertySyncRate; }
        public void setPropertySyncRate(double propertySyncRate) { this.propertySyncRate = propertySyncRate; }
        
        public double getCustomerSyncRate() { return customerSyncRate; }
        public void setCustomerSyncRate(double customerSyncRate) { this.customerSyncRate = customerSyncRate; }
        
        public double getPaymentSyncRate() { return paymentSyncRate; }
        public void setPaymentSyncRate(double paymentSyncRate) { this.paymentSyncRate = paymentSyncRate; }
        
        public double getTransactionSyncRate() { return transactionSyncRate; }
        public void setTransactionSyncRate(double transactionSyncRate) { this.transactionSyncRate = transactionSyncRate; }
        
        public int getPropertiesWithoutRent() { return propertiesWithoutRent; }
        public void setPropertiesWithoutRent(int propertiesWithoutRent) { this.propertiesWithoutRent = propertiesWithoutRent; }
        
        public int getPropertiesWithoutOwner() { return propertiesWithoutOwner; }
        public void setPropertiesWithoutOwner(int propertiesWithoutOwner) { this.propertiesWithoutOwner = propertiesWithoutOwner; }
        
        public int getCustomersWithoutEmail() { return customersWithoutEmail; }
        public void setCustomersWithoutEmail(int customersWithoutEmail) { this.customersWithoutEmail = customersWithoutEmail; }
        
        public int getTransactionsWithoutProperty() { return transactionsWithoutProperty; }
        public void setTransactionsWithoutProperty(int transactionsWithoutProperty) { this.transactionsWithoutProperty = transactionsWithoutProperty; }
        
        public LocalDateTime getLastSyncTime() { return lastSyncTime; }
        public void setLastSyncTime(LocalDateTime lastSyncTime) { this.lastSyncTime = lastSyncTime; }
        
        public List<String> getRecentErrors() { return recentErrors; }
        public void setRecentErrors(List<String> recentErrors) { this.recentErrors = recentErrors; }
        
        public int getRecentSyncCount() { return recentSyncCount; }
        public void setRecentSyncCount(int recentSyncCount) { this.recentSyncCount = recentSyncCount; }
        
        public double getTotalTransactionAmount() { return totalTransactionAmount; }
        public void setTotalTransactionAmount(double totalTransactionAmount) { this.totalTransactionAmount = totalTransactionAmount; }
        
        public double getAverageCommissionRate() { return averageCommissionRate; }
        public void setAverageCommissionRate(double averageCommissionRate) { this.averageCommissionRate = averageCommissionRate; }
        
        public int getMissingCommissions() { return missingCommissions; }
        public void setMissingCommissions(int missingCommissions) { this.missingCommissions = missingCommissions; }
        
        public int getHealthScore() { return healthScore; }
        public void setHealthScore(int healthScore) { this.healthScore = healthScore; }
        
        public String getHealthStatusDescription() {
            String status = getHealthStatus();
            switch (status) {
                case "EXCELLENT":
                    return "System is running smoothly with minimal issues";
                case "GOOD":
                    return "System is healthy with some minor issues to address";
                case "FAIR":
                    return "System has several issues that need attention";
                case "POOR":
                    return "System has significant issues requiring immediate attention";
                case "CRITICAL":
                    return "System has critical issues that must be resolved urgently";
                default:
                    return "Unknown status";
            }
        }
    }
}
package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import site.easy.to.build.crm.repository.*;

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
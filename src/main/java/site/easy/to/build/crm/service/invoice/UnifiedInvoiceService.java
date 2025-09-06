package site.easy.to.build.crm.service.invoice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.repository.InvoiceRepository;
import site.easy.to.build.crm.service.property.PropertyService;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/**
 * Unified Invoice Service - Implements "PayProp Winner" Logic
 * 
 * Core Rule: If property.isPayPropSynced() → use payprop_export_invoices
 *           Else → use local invoices table
 * 
 * This service provides a unified view without duplicating data sources
 */
@Service
@Transactional(readOnly = true)
public class UnifiedInvoiceService {
    
    private static final Logger log = LoggerFactory.getLogger(UnifiedInvoiceService.class);
    
    @Autowired
    private InvoiceRepository localInvoiceRepository;
    
    @Autowired
    private PropertyService propertyService;
    
    @Autowired
    private DataSource dataSource;
    
    // ===== UNIFIED INVOICE RETRIEVAL =====
    
    /**
     * Get all invoices for a property - respects PayProp winner logic
     */
    public List<UnifiedInvoiceView> getInvoicesForProperty(Long propertyId) {
        Property property = propertyService.findById(propertyId);
        if (property == null) {
            return Collections.emptyList();
        }
        
        if (property.isPayPropSynced() && property.isActive()) {
            log.debug("Property {} is PayProp synced - using PayProp invoices", propertyId);
            return getPayPropInvoicesForProperty(property);
        } else {
            log.debug("Property {} is NOT PayProp synced - using local invoices", propertyId);
            return getLocalInvoicesForProperty(property);
        }
    }
    
    /**
     * Get all invoices for a customer across all their properties
     */
    public List<UnifiedInvoiceView> getInvoicesForCustomer(Customer customer) {
        List<UnifiedInvoiceView> allInvoices = new ArrayList<>();
        
        // Get all properties for this customer (via assignments)
        // This would need to integrate with your assignment system
        List<Property> customerProperties = getPropertiesForCustomer(customer);
        
        for (Property property : customerProperties) {
            allInvoices.addAll(getInvoicesForProperty(property.getId()));
        }
        
        return allInvoices;
    }
    
    /**
     * Get unified invoice summary for reporting
     */
    public UnifiedInvoiceSummary getInvoiceSummary() {
        UnifiedInvoiceSummary summary = new UnifiedInvoiceSummary();
        
        // Count PayProp properties and their invoices
        List<Property> payPropProperties = propertyService.findAll().stream()
                .filter(p -> p.isPayPropSynced() && p.isActive())
                .toList();
        
        List<Property> localProperties = propertyService.findAll().stream()
                .filter(p -> !p.isPayPropSynced() && p.isActive())
                .toList();
        
        summary.setPayPropProperties(payPropProperties.size());
        summary.setLocalProperties(localProperties.size());
        
        // Count invoices from each source
        summary.setPayPropInvoices(countPayPropInvoices());
        summary.setLocalInvoices(countLocalInvoices());
        
        // Calculate totals
        summary.setPayPropInvoiceAmount(getTotalPayPropInvoiceAmount());
        summary.setLocalInvoiceAmount(getTotalLocalInvoiceAmount());
        
        return summary;
    }
    
    // ===== PAYPROP DATA ACCESS =====
    
    /**
     * Get PayProp invoices for a property from payprop_export_invoices
     */
    private List<UnifiedInvoiceView> getPayPropInvoicesForProperty(Property property) {
        List<UnifiedInvoiceView> invoices = new ArrayList<>();
        
        String sql = """
            SELECT 
                payprop_id,
                description,
                gross_amount,
                frequency,
                frequency_code,
                payment_day,
                from_date,
                to_date,
                property_name,
                tenant_display_name,
                category_name,
                is_active_instruction,
                last_modified_at
            FROM payprop_export_invoices 
            WHERE property_payprop_id = ? 
            AND is_active_instruction = TRUE
            ORDER BY from_date DESC
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, property.getPayPropId());
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UnifiedInvoiceView invoice = new UnifiedInvoiceView();
                    invoice.setSource(InvoiceSource.PAYPROP);
                    invoice.setSourceId(rs.getString("payprop_id"));
                    invoice.setDescription(rs.getString("description"));
                    invoice.setAmount(rs.getBigDecimal("gross_amount"));
                    invoice.setFrequency(rs.getString("frequency"));
                    invoice.setPaymentDay(rs.getInt("payment_day"));
                    invoice.setStartDate(rs.getDate("from_date") != null ? 
                                       rs.getDate("from_date").toLocalDate() : null);
                    invoice.setEndDate(rs.getDate("to_date") != null ? 
                                     rs.getDate("to_date").toLocalDate() : null);
                    invoice.setPropertyName(rs.getString("property_name"));
                    invoice.setCustomerName(rs.getString("tenant_display_name"));
                    invoice.setCategoryName(rs.getString("category_name"));
                    invoice.setIsActive(rs.getBoolean("is_active_instruction"));
                    invoice.setLastModified(rs.getTimestamp("last_modified_at") != null ?
                                          rs.getTimestamp("last_modified_at").toLocalDateTime() : null);
                    
                    invoices.add(invoice);
                }
            }
            
        } catch (SQLException e) {
            log.error("Failed to get PayProp invoices for property {}: {}", 
                     property.getPayPropId(), e.getMessage());
        }
        
        log.debug("Retrieved {} PayProp invoices for property {}", 
                 invoices.size(), property.getPropertyName());
        return invoices;
    }
    
    // ===== LOCAL DATA ACCESS =====
    
    /**
     * Get local invoices for a property from invoices table
     */
    private List<UnifiedInvoiceView> getLocalInvoicesForProperty(Property property) {
        return localInvoiceRepository.findActiveInvoicesForProperty(property, LocalDate.now())
                .stream()
                .map(this::convertToUnifiedView)
                .toList();
    }
    
    /**
     * Convert local Invoice entity to unified view
     */
    private UnifiedInvoiceView convertToUnifiedView(site.easy.to.build.crm.entity.Invoice invoice) {
        UnifiedInvoiceView view = new UnifiedInvoiceView();
        view.setSource(InvoiceSource.LOCAL);
        view.setSourceId(invoice.getId().toString());
        view.setDescription(invoice.getDescription());
        view.setAmount(invoice.getAmount());
        view.setFrequency(invoice.getFrequency().getDisplayName());
        view.setPaymentDay(invoice.getPaymentDay());
        view.setStartDate(invoice.getStartDate());
        view.setEndDate(invoice.getEndDate());
        view.setPropertyName(invoice.getProperty().getPropertyName());
        view.setCustomerName(invoice.getCustomer().getName());
        view.setCategoryName(invoice.getCategoryName());
        view.setIsActive(invoice.getIsActive());
        view.setLastModified(invoice.getUpdatedAt());
        
        return view;
    }
    
    // ===== HELPER METHODS =====
    
    /**
     * Get properties for a customer (placeholder - integrate with assignment system)
     */
    private List<Property> getPropertiesForCustomer(Customer customer) {
        // TODO: Integrate with your CustomerPropertyAssignmentService
        // For now, return empty list
        return Collections.emptyList();
    }
    
    /**
     * Count PayProp invoices
     */
    private long countPayPropInvoices() {
        String sql = "SELECT COUNT(*) FROM payprop_export_invoices WHERE is_active_instruction = TRUE";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            return rs.next() ? rs.getLong(1) : 0;
            
        } catch (SQLException e) {
            log.error("Failed to count PayProp invoices: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * Count local invoices
     */
    private long countLocalInvoices() {
        return localInvoiceRepository.countActiveInvoices();
    }
    
    /**
     * Get total PayProp invoice amount
     */
    private BigDecimal getTotalPayPropInvoiceAmount() {
        String sql = "SELECT SUM(gross_amount) FROM payprop_export_invoices WHERE is_active_instruction = TRUE";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            return rs.next() ? (rs.getBigDecimal(1) != null ? rs.getBigDecimal(1) : BigDecimal.ZERO) : BigDecimal.ZERO;
            
        } catch (SQLException e) {
            log.error("Failed to get total PayProp invoice amount: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * Get total local invoice amount
     */
    private BigDecimal getTotalLocalInvoiceAmount() {
        // This would need to be implemented in InvoiceRepository
        return BigDecimal.ZERO; // Placeholder
    }
    
    // ===== INNER CLASSES =====
    
    /**
     * Unified view of invoice data from either PayProp or local source
     */
    public static class UnifiedInvoiceView {
        private InvoiceSource source;
        private String sourceId; // PayProp ID or local invoice ID
        private String description;
        private BigDecimal amount;
        private String frequency;
        private Integer paymentDay;
        private LocalDate startDate;
        private LocalDate endDate;
        private String propertyName;
        private String customerName;
        private String categoryName;
        private Boolean isActive;
        private java.time.LocalDateTime lastModified;
        
        // Getters and setters
        public InvoiceSource getSource() { return source; }
        public void setSource(InvoiceSource source) { this.source = source; }
        
        public String getSourceId() { return sourceId; }
        public void setSourceId(String sourceId) { this.sourceId = sourceId; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        
        public String getFrequency() { return frequency; }
        public void setFrequency(String frequency) { this.frequency = frequency; }
        
        public Integer getPaymentDay() { return paymentDay; }
        public void setPaymentDay(Integer paymentDay) { this.paymentDay = paymentDay; }
        
        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
        
        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
        
        public String getPropertyName() { return propertyName; }
        public void setPropertyName(String propertyName) { this.propertyName = propertyName; }
        
        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }
        
        public String getCategoryName() { return categoryName; }
        public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
        
        public Boolean getIsActive() { return isActive; }
        public void setIsActive(Boolean isActive) { this.isActive = isActive; }
        
        public java.time.LocalDateTime getLastModified() { return lastModified; }
        public void setLastModified(java.time.LocalDateTime lastModified) { this.lastModified = lastModified; }
        
        /**
         * Get display badge for source
         */
        public String getSourceBadge() {
            return switch (source) {
                case PAYPROP -> "PayProp";
                case LOCAL -> "Local";
            };
        }
        
        /**
         * Check if this invoice is currently active
         */
        public boolean isCurrentlyActive() {
            if (!Boolean.TRUE.equals(isActive)) {
                return false;
            }
            
            LocalDate today = LocalDate.now();
            if (startDate != null && startDate.isAfter(today)) {
                return false;
            }
            
            return endDate == null || !endDate.isBefore(today);
        }
    }
    
    /**
     * Invoice source enum
     */
    public enum InvoiceSource {
        PAYPROP("PayProp", "#007bff"),      // Blue
        LOCAL("Local", "#28a745");          // Green  
        
        private final String displayName;
        private final String badgeColor;
        
        InvoiceSource(String displayName, String badgeColor) {
            this.displayName = displayName;
            this.badgeColor = badgeColor;
        }
        
        public String getDisplayName() { return displayName; }
        public String getBadgeColor() { return badgeColor; }
    }
    
    /**
     * Summary statistics for unified invoice system
     */
    public static class UnifiedInvoiceSummary {
        private int payPropProperties;
        private int localProperties;
        private long payPropInvoices;
        private long localInvoices;
        private BigDecimal payPropInvoiceAmount;
        private BigDecimal localInvoiceAmount;
        
        // Getters and setters
        public int getPayPropProperties() { return payPropProperties; }
        public void setPayPropProperties(int payPropProperties) { this.payPropProperties = payPropProperties; }
        
        public int getLocalProperties() { return localProperties; }
        public void setLocalProperties(int localProperties) { this.localProperties = localProperties; }
        
        public long getPayPropInvoices() { return payPropInvoices; }
        public void setPayPropInvoices(long payPropInvoices) { this.payPropInvoices = payPropInvoices; }
        
        public long getLocalInvoices() { return localInvoices; }
        public void setLocalInvoices(long localInvoices) { this.localInvoices = localInvoices; }
        
        public BigDecimal getPayPropInvoiceAmount() { return payPropInvoiceAmount; }
        public void setPayPropInvoiceAmount(BigDecimal payPropInvoiceAmount) { this.payPropInvoiceAmount = payPropInvoiceAmount; }
        
        public BigDecimal getLocalInvoiceAmount() { return localInvoiceAmount; }
        public void setLocalInvoiceAmount(BigDecimal localInvoiceAmount) { this.localInvoiceAmount = localInvoiceAmount; }
        
        public int getTotalProperties() {
            return payPropProperties + localProperties;
        }
        
        public long getTotalInvoices() {
            return payPropInvoices + localInvoices;
        }
        
        public BigDecimal getTotalAmount() {
            return (payPropInvoiceAmount != null ? payPropInvoiceAmount : BigDecimal.ZERO)
                    .add(localInvoiceAmount != null ? localInvoiceAmount : BigDecimal.ZERO);
        }
    }
}
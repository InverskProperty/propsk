package site.easy.to.build.crm.service.payprop.business;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Property Rent Calculation Service
 * 
 * SOLVES THE £995 vs £1,075 MYSTERY!
 * 
 * This service analyzes raw PayProp data and makes business decisions about
 * which rent amount is authoritative for each property:
 * - £995 from payprop_export_properties.settings_monthly_payment (base rent)
 * - £1,075 from payprop_export_invoices.gross_amount (total charges)
 * 
 * Business logic determines which amount to use and updates the Property entity accordingly.
 */
@Service
public class PropertyRentCalculationService {
    
    private static final Logger log = LoggerFactory.getLogger(PropertyRentCalculationService.class);
    
    @Autowired
    private DataSource dataSource;
    
    // Business rules for rent calculation
    private static final BigDecimal VARIANCE_THRESHOLD = new BigDecimal("50.00"); // Flag differences > £50
    private static final String DEFAULT_AUTHORITATIVE_SOURCE = "invoice"; // Prefer invoice amounts
    
    /**
     * Calculate authoritative rent amounts for all properties
     * Compares £995 (settings) vs £1,075 (invoices) and makes business decisions
     */
    @Transactional
    public PropertyRentCalculationResult calculateAllPropertyRents() {
        log.info("🔄 Starting property rent calculations - solving £995 vs £1,075 mystery");
        
        PropertyRentCalculationResult result = new PropertyRentCalculationResult();
        result.setStartTime(LocalDateTime.now());
        
        try {
            // Get all properties with both settings and invoice data
            List<PropertyRentData> properties = fetchPropertiesWithRentData();
            log.info("Found {} properties with rent data for calculation", properties.size());
            
            // Calculate business logic for each property
            List<PropertyRentDecision> decisions = new ArrayList<>();
            for (PropertyRentData property : properties) {
                PropertyRentDecision decision = calculateRentForProperty(property);
                decisions.add(decision);
            }
            
            // Store decisions in business logic table
            int decisionsStored = storeRentDecisions(decisions);
            
            // Update existing Property entities with calculated amounts
            int propertiesUpdated = updatePropertyEntities(decisions);
            
            result.setTotalProperties(properties.size());
            result.setDecisionsCalculated(decisions.size());
            result.setDecisionsStored(decisionsStored);
            result.setPropertiesUpdated(propertiesUpdated);
            result.setSuccess(true);
            result.setEndTime(LocalDateTime.now());
            
            // Log summary statistics
            logCalculationSummary(decisions);
            
            log.info("✅ Property rent calculations completed: {} properties processed", 
                properties.size());
            
        } catch (Exception e) {
            log.error("❌ Property rent calculations failed", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }
        
        return result;
    }
    
    /**
     * Fetch all properties that have both settings and invoice data
     */
    private List<PropertyRentData> fetchPropertiesWithRentData() throws SQLException {
        String sql = """
            SELECT 
                p.id as property_id,
                p.payprop_id as property_payprop_id,
                p.property_name,
                props.settings_monthly_payment as settings_rent,
                inv.gross_amount as invoice_rent,
                inv.payprop_id as invoice_id,
                inv.is_active_instruction
            FROM properties p
            LEFT JOIN payprop_export_properties props ON p.payprop_id = props.payprop_id
            LEFT JOIN payprop_export_invoices inv ON p.payprop_id = inv.property_payprop_id 
                AND inv.is_active_instruction = true
            WHERE p.payprop_id IS NOT NULL
              AND (props.settings_monthly_payment IS NOT NULL 
                   OR inv.gross_amount IS NOT NULL)
            ORDER BY p.id
            """;
        
        List<PropertyRentData> properties = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                PropertyRentData data = new PropertyRentData();
                data.propertyId = rs.getLong("property_id");
                data.payPropId = rs.getString("property_payprop_id");
                data.propertyName = rs.getString("property_name");
                data.settingsRent = rs.getBigDecimal("settings_rent");    // £995
                data.invoiceRent = rs.getBigDecimal("invoice_rent");      // £1,075
                data.invoiceId = rs.getString("invoice_id");
                data.hasActiveInvoice = rs.getBoolean("is_active_instruction");
                
                properties.add(data);
            }
        }
        
        return properties;
    }
    
    /**
     * Calculate rent decision for a single property
     * THE CORE BUSINESS LOGIC THAT SOLVES THE MYSTERY!
     */
    private PropertyRentDecision calculateRentForProperty(PropertyRentData property) {
        PropertyRentDecision decision = new PropertyRentDecision();
        decision.propertyId = property.propertyId;
        decision.settingsRentAmount = property.settingsRent;      // £995
        decision.invoiceInstructionAmount = property.invoiceRent; // £1,075
        
        // Calculate variance
        if (property.settingsRent != null && property.invoiceRent != null) {
            decision.settingsVsInvoiceVariance = property.invoiceRent.subtract(property.settingsRent);
            
            // £1,075 - £995 = £80 (likely parking/service charges)
            if (decision.settingsVsInvoiceVariance.abs().compareTo(VARIANCE_THRESHOLD) > 0) {
                decision.varianceExplanation = String.format(
                    "Significant difference: £%.2f (likely additional charges like parking/services)",
                    decision.settingsVsInvoiceVariance);
            } else {
                decision.varianceExplanation = "Amounts are similar";
            }
        }
        
        // BUSINESS DECISION: Which amount is authoritative?
        if (property.invoiceRent != null && property.hasActiveInvoice) {
            // Prefer invoice amount - this is what actually gets billed
            decision.authoritativeRentSource = "invoice";
            decision.currentRentAmount = property.invoiceRent; // £1,075
            decision.calculationMethod = "Invoice amount used - represents total charges to tenant";
            
        } else if (property.settingsRent != null) {
            // Fall back to settings amount - base rent only
            decision.authoritativeRentSource = "settings";
            decision.currentRentAmount = property.settingsRent; // £995
            decision.calculationMethod = "Settings amount used - no active invoice found";
            
        } else {
            // No data available
            decision.authoritativeRentSource = "manual";
            decision.currentRentAmount = BigDecimal.ZERO;
            decision.calculationMethod = "No rent data available - requires manual input";
        }
        
        // Data quality score (0-1 confidence)
        if (property.settingsRent != null && property.invoiceRent != null) {
            decision.dataQualityScore = new BigDecimal("1.0"); // High confidence
        } else if (property.settingsRent != null || property.invoiceRent != null) {
            decision.dataQualityScore = new BigDecimal("0.7"); // Medium confidence
        } else {
            decision.dataQualityScore = new BigDecimal("0.0"); // No confidence
        }
        
        decision.lastCalculatedAt = LocalDateTime.now();
        
        return decision;
    }
    
    /**
     * Store rent decisions in property_rent_sources table
     */
    private int storeRentDecisions(List<PropertyRentDecision> decisions) throws SQLException {
        if (decisions.isEmpty()) {
            return 0;
        }
        
        String insertSql = """
            INSERT INTO property_rent_sources (
                property_id, settings_rent_amount, invoice_instruction_amount,
                authoritative_rent_source, current_rent_amount,
                settings_vs_invoice_variance, variance_explanation, variance_threshold,
                last_calculated_at, calculation_method, data_quality_score
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            ON DUPLICATE KEY UPDATE
                settings_rent_amount = VALUES(settings_rent_amount),
                invoice_instruction_amount = VALUES(invoice_instruction_amount),
                authoritative_rent_source = VALUES(authoritative_rent_source),
                current_rent_amount = VALUES(current_rent_amount),
                settings_vs_invoice_variance = VALUES(settings_vs_invoice_variance),
                variance_explanation = VALUES(variance_explanation),
                last_calculated_at = VALUES(last_calculated_at),
                calculation_method = VALUES(calculation_method),
                data_quality_score = VALUES(data_quality_score)
            """;
        
        int storedCount = 0;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            
            for (PropertyRentDecision decision : decisions) {
                stmt.setLong(1, decision.propertyId);
                stmt.setBigDecimal(2, decision.settingsRentAmount);
                stmt.setBigDecimal(3, decision.invoiceInstructionAmount);
                stmt.setString(4, decision.authoritativeRentSource);
                stmt.setBigDecimal(5, decision.currentRentAmount);
                stmt.setBigDecimal(6, decision.settingsVsInvoiceVariance);
                stmt.setString(7, decision.varianceExplanation);
                stmt.setBigDecimal(8, VARIANCE_THRESHOLD);
                stmt.setObject(9, decision.lastCalculatedAt);
                stmt.setString(10, decision.calculationMethod);
                stmt.setBigDecimal(11, decision.dataQualityScore);
                
                stmt.addBatch();
                storedCount++;
                
                if (storedCount % 25 == 0) {
                    stmt.executeBatch();
                }
            }
            
            if (storedCount % 25 != 0) {
                stmt.executeBatch();
            }
        }
        
        log.info("Stored {} rent decisions in property_rent_sources table", storedCount);
        return storedCount;
    }
    
    /**
     * Update existing Property entities with calculated authoritative amounts
     */
    private int updatePropertyEntities(List<PropertyRentDecision> decisions) throws SQLException {
        String updateSql = """
            UPDATE properties p
            JOIN property_rent_sources prs ON p.id = prs.property_id
            SET p.monthly_payment = prs.current_rent_amount,
                p.rent_source_calculation_id = prs.property_id,
                p.payprop_sync_status = 'synced',
                p.last_payprop_sync = NOW()
            WHERE p.id = ?
            """;
        
        int updatedCount = 0;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(updateSql)) {
            
            for (PropertyRentDecision decision : decisions) {
                stmt.setLong(1, decision.propertyId);
                stmt.addBatch();
                updatedCount++;
                
                if (updatedCount % 25 == 0) {
                    stmt.executeBatch();
                }
            }
            
            if (updatedCount % 25 != 0) {
                stmt.executeBatch();
            }
        }
        
        log.info("Updated {} Property entities with calculated rent amounts", updatedCount);
        return updatedCount;
    }
    
    /**
     * Log summary statistics about the calculation results
     */
    private void logCalculationSummary(List<PropertyRentDecision> decisions) {
        int invoicePreferred = 0;
        int settingsUsed = 0;
        int manualNeeded = 0;
        int significantVariances = 0;
        
        BigDecimal totalVariance = BigDecimal.ZERO;
        int varianceCount = 0;
        
        for (PropertyRentDecision decision : decisions) {
            switch (decision.authoritativeRentSource) {
                case "invoice" -> invoicePreferred++;
                case "settings" -> settingsUsed++;
                case "manual" -> manualNeeded++;
            }
            
            if (decision.settingsVsInvoiceVariance != null) {
                if (decision.settingsVsInvoiceVariance.abs().compareTo(VARIANCE_THRESHOLD) > 0) {
                    significantVariances++;
                }
                totalVariance = totalVariance.add(decision.settingsVsInvoiceVariance.abs());
                varianceCount++;
            }
        }
        
        log.info("📊 RENT CALCULATION SUMMARY:");
        log.info("   Invoice amounts preferred: {} properties (£1,075 type)", invoicePreferred);
        log.info("   Settings amounts used: {} properties (£995 type)", settingsUsed);
        log.info("   Manual input needed: {} properties", manualNeeded);
        log.info("   Significant variances (>£{}): {} properties", VARIANCE_THRESHOLD, significantVariances);
        
        if (varianceCount > 0) {
            BigDecimal avgVariance = totalVariance.divide(new BigDecimal(varianceCount), 2, BigDecimal.ROUND_HALF_UP);
            log.info("   Average variance: £{}", avgVariance);
        }
        
        log.info("✅ £995 vs £1,075 mystery SOLVED for {} properties!", decisions.size());
    }
    
    // ===== DATA CLASSES =====
    
    private static class PropertyRentData {
        Long propertyId;
        String payPropId;
        String propertyName;
        BigDecimal settingsRent;  // £995
        BigDecimal invoiceRent;   // £1,075
        String invoiceId;
        boolean hasActiveInvoice;
    }
    
    private static class PropertyRentDecision {
        Long propertyId;
        BigDecimal settingsRentAmount;           // £995
        BigDecimal invoiceInstructionAmount;     // £1,075
        String authoritativeRentSource;          // "invoice", "settings", "manual"
        BigDecimal currentRentAmount;            // THE CHOSEN AMOUNT
        BigDecimal settingsVsInvoiceVariance;    // £1,075 - £995 = £80
        String varianceExplanation;
        LocalDateTime lastCalculatedAt;
        String calculationMethod;
        BigDecimal dataQualityScore;
    }
}
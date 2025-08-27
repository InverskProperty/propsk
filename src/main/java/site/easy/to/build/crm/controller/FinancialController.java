package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.entity.FinancialTransaction;
import site.easy.to.build.crm.entity.Payment;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.repository.FinancialTransactionRepository;
import site.easy.to.build.crm.repository.PaymentRepository;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.property.PropertyServiceImpl;
import site.easy.to.build.crm.util.AuthorizationUtil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * FinancialController - Handles financial data display for properties and customers
 * Separate from PropertyController to avoid any existing issues
 */
@RestController
@RequestMapping("/employee")
public class FinancialController {

    @Autowired
    private PropertyService propertyService;
    
    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;
    
    @Autowired
    private PaymentRepository paymentRepository;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Value("${crm.data.source:LEGACY}")
    private String dataSource;

    /**
     * Get financial summary for a specific property
     * Supports both PayProp ID strings and numeric property IDs
     */
    @GetMapping("/property/{id}/financial-summary")
    public ResponseEntity<Map<String, Object>> getPropertyFinancialSummary(
            @PathVariable("id") String id, 
            Authentication authentication) {
        
        // Get financial summary for property
        
        try {
            // Find property by ID or PayProp ID
            Property property = null;
            if (id.length() > 5 && !id.matches("\\d+")) {
                // PayProp ID string format
                property = ((PropertyServiceImpl) propertyService).findByPayPropIdString(id);
            } else {
                // Numeric ID
                property = propertyService.findById(Long.parseLong(id));
            }
            
            if (property == null) {
                return ResponseEntity.notFound().build();
            }
            
            // Basic authorization check
            if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") && 
                !AuthorizationUtil.hasRole(authentication, "ROLE_EMPLOYEE")) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Access denied");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
            }
            
            // Get financial data using property object
            Map<String, Object> financialData = calculatePropertyFinancialSummary(property);
            
            return ResponseEntity.ok(financialData);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Error loading financial data: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * Get financial summary for a customer (property owner)
     */
    @GetMapping("/customer/{id}/financial-summary")
    public ResponseEntity<Map<String, Object>> getCustomerFinancialSummary(
            @PathVariable("id") Integer customerId,
            Authentication authentication) {
        
        try {
            // Basic authorization check
            if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") && 
                !AuthorizationUtil.hasRole(authentication, "ROLE_EMPLOYEE")) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Access denied");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
            }
            
            // Get financial data for all properties owned by this customer
            Map<String, Object> financialData = calculateCustomerFinancialSummary(customerId);
            
            return ResponseEntity.ok(financialData);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Error loading customer financial data: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * DEBUG: Add missing payprop_id column to property table
     */
    @GetMapping("/debug/add-payprop-column")
    public ResponseEntity<Map<String, Object>> addPayPropColumn() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // First check if column exists
            String checkQuery = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'property' AND COLUMN_NAME = 'payprop_id'";
            Integer columnExists = jdbcTemplate.queryForObject(checkQuery, Integer.class);
            
            if (columnExists == 0) {
                // Add the column
                String addColumnQuery = "ALTER TABLE property ADD COLUMN payprop_id VARCHAR(32) NULL";
                jdbcTemplate.execute(addColumnQuery);
                result.put("column_added", true);
                result.put("message", "payprop_id column added to property table");
            } else {
                result.put("column_added", false);
                result.put("message", "payprop_id column already exists");
            }
            
            result.put("status", "success");
            
        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("status", "error");
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * DEBUG: Populate PayProp IDs in property table from existing PayProp data
     */
    @GetMapping("/debug/populate-payprop-ids")
    public ResponseEntity<Map<String, Object>> populatePayPropIds() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get unique PayProp IDs from the payments table
            String getPayPropIdsQuery = """
                SELECT DISTINCT pap.payprop_id, pep.name as property_name, pep.address_first_line, pep.address_city
                FROM payprop_report_all_payments pap
                LEFT JOIN payprop_entity_property pep ON pap.payprop_id = pep.payprop_id
                WHERE pap.payprop_id IS NOT NULL
                LIMIT 100
                """;
            
            List<Map<String, Object>> payPropProperties = jdbcTemplate.queryForList(getPayPropIdsQuery);
            result.put("found_payprop_properties", payPropProperties.size());
            
            int updatedCount = 0;
            for (Map<String, Object> ppProperty : payPropProperties) {
                String payPropId = (String) ppProperty.get("payprop_id");
                String propertyName = (String) ppProperty.get("property_name");
                String addressLine1 = (String) ppProperty.get("address_first_line");
                String city = (String) ppProperty.get("address_city");
                
                if (payPropId != null) {
                    // Try to match with existing properties by name or address
                    String matchQuery = """
                        UPDATE property 
                        SET payprop_id = ?
                        WHERE payprop_id IS NULL 
                        AND (
                            property_name LIKE ? 
                            OR property_name LIKE ?
                            OR (? IS NOT NULL AND property_name LIKE ?)
                        )
                        LIMIT 1
                        """;
                    
                    int updated = jdbcTemplate.update(matchQuery, 
                        payPropId,
                        "%" + (addressLine1 != null ? addressLine1 : "") + "%",
                        "%" + (propertyName != null ? propertyName : "") + "%",
                        city,
                        "%" + (city != null ? city : "") + "%"
                    );
                    updatedCount += updated;
                }
            }
            
            result.put("properties_updated", updatedCount);
            result.put("status", "success");
            
        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("status", "error");
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * DEBUG: Update properties table with correct PayProp IDs from export properties
     */
    @GetMapping("/debug/link-properties")
    public ResponseEntity<Map<String, Object>> linkPropertiesToPayProp() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Link properties to export properties by address matching
            String linkQuery = """
                UPDATE properties p
                INNER JOIN payprop_export_properties pep ON (
                    p.address_line_1 LIKE CONCAT('%', pep.address_first_line, '%')
                    OR pep.address_first_line LIKE CONCAT('%', p.address_line_1, '%')
                    OR (p.city = pep.address_city AND 
                        (SUBSTRING_INDEX(p.address_line_1, ' ', 1) = SUBSTRING_INDEX(pep.address_first_line, ' ', 1)))
                )
                SET p.payprop_id = pep.payprop_id
                WHERE p.payprop_id != pep.payprop_id
                """;
                
            int updatedRows = jdbcTemplate.update(linkQuery);
            result.put("properties_linked", updatedRows);
            
            // Check how many properties now have matching PayProp IDs
            String checkQuery = """
                SELECT COUNT(*) as linked_properties
                FROM properties p
                INNER JOIN payprop_export_properties pep ON p.payprop_id = pep.payprop_id
                """;
            Integer linkedCount = jdbcTemplate.queryForObject(checkQuery, Integer.class);
            result.put("total_linked_properties", linkedCount);
            
            result.put("status", "success");
            
        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("status", "error");
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * DEBUG: Test endpoint to check PayProp data availability
     */
    @GetMapping("/debug/payprop-data")
    public ResponseEntity<Map<String, Object>> debugPayPropData() {
        Map<String, Object> debug = new HashMap<>();
        
        try {
            // Test if PayProp tables exist and have data
            String testQuery = "SELECT COUNT(*) as payment_count FROM payprop_report_all_payments LIMIT 1";
            Integer paymentCount = jdbcTemplate.queryForObject(testQuery, Integer.class);
            debug.put("payprop_payments_count", paymentCount);
            
            String icdenQuery = "SELECT COUNT(*) as icdn_count FROM payprop_report_icdn LIMIT 1";
            Integer icdnCount = jdbcTemplate.queryForObject(icdenQuery, Integer.class);
            debug.put("payprop_icdn_count", icdnCount);
            
            debug.put("data_source_setting", dataSource);
            debug.put("status", "success");
            
            // Check property table structure and PayProp ID field
            try {
                String propertyQuery = "SELECT COUNT(*) as property_count FROM property WHERE payprop_id IS NOT NULL";
                Integer propCount = jdbcTemplate.queryForObject(propertyQuery, Integer.class);
                debug.put("properties_with_payprop_id", propCount);
            } catch (Exception propertyError) {
                debug.put("property_query_error", propertyError.getMessage());
                
                // Try alternative column names
                try {
                    String altQuery1 = "SELECT COUNT(*) as property_count FROM property WHERE pay_prop_id IS NOT NULL";
                    Integer altCount = jdbcTemplate.queryForObject(altQuery1, Integer.class);
                    debug.put("properties_with_pay_prop_id", altCount);
                } catch (Exception e1) {
                    debug.put("pay_prop_id_error", e1.getMessage());
                }
                
                // Check table structure - try a different approach
                try {
                    String columnQuery = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'property' AND COLUMN_NAME LIKE '%prop%'";
                    List<String> columns = jdbcTemplate.queryForList(columnQuery, String.class);
                    debug.put("property_payprop_columns", columns);
                    
                    // Also get a sample of actual columns
                    String allColumnsQuery = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'property' LIMIT 10";
                    List<String> allColumns = jdbcTemplate.queryForList(allColumnsQuery, String.class);
                    debug.put("property_sample_columns", allColumns);
                    
                } catch (Exception e2) {
                    debug.put("column_check_error", e2.getMessage());
                    
                    // Try basic table check
                    try {
                        String basicQuery = "SELECT COUNT(*) FROM property LIMIT 1";
                        Integer count = jdbcTemplate.queryForObject(basicQuery, Integer.class);
                        debug.put("property_table_exists", true);
                        debug.put("property_row_count", count);
                    } catch (Exception e3) {
                        debug.put("property_table_exists", false);
                    }
                }
            }
            
        } catch (Exception e) {
            debug.put("error", e.getMessage());
            debug.put("status", "error");
        }
        
        return ResponseEntity.ok(debug);
    }

    /**
     * Calculate financial summary for a specific property using PayProp data
     */
    private Map<String, Object> calculatePropertyFinancialSummary(Property property) {
        Map<String, Object> summary = new HashMap<>();
        
        // Calculate financial summary for property
        
        try {
            if (property == null) {
                return getEmptyFinancialSummary();
            }
            
            // Use PayProp data if available and configured
            if ("PAYPROP".equals(dataSource) && property.getPayPropId() != null) {
                return calculatePayPropFinancialSummary(property);
            } else {
                // Fallback to legacy calculation
                return calculateLegacyFinancialSummary(property);
            }
            
        } catch (Exception e) {
            summary = getEmptyFinancialSummary();
            summary.put("error", "Calculation error: " + e.getMessage());
        }
        
        return summary;
    }
    
    /**
     * Calculate financial summary using PayProp transaction data
     */
    private Map<String, Object> calculatePayPropFinancialSummary(Property property) {
        Map<String, Object> summary = new HashMap<>();
        String payPropId = property.getPayPropId();
        
        System.out.println("DEBUG: calculatePayPropFinancialSummary called for property: " + property.getId());
        System.out.println("DEBUG: PayProp ID: " + payPropId);
        System.out.println("DEBUG: Data source setting: " + dataSource);
        
        try {
            // Query PayProp all-payments table for comprehensive transaction data
            // FIXED: Use incoming_property_payprop_id to link transactions to properties
            String paymentsQuery = """
                SELECT 
                    pap.payprop_id,
                    pap.amount,
                    pap.service_fee,
                    pap.transaction_fee,
                    pap.description,
                    pap.reference,
                    pap.category_name,
                    pap.incoming_property_name,
                    pap.incoming_tenant_name,
                    pap.payment_batch_transfer_date,
                    pap.incoming_transaction_type,
                    pap.beneficiary_name,
                    pap.beneficiary_type,
                    pap.incoming_transaction_amount,
                    pap.payment_batch_status,
                    pap.imported_at
                FROM payprop_report_all_payments pap 
                WHERE pap.incoming_property_payprop_id = ? 
                ORDER BY pap.payment_batch_transfer_date DESC, pap.imported_at DESC
                LIMIT 100
                """;
                
            List<Map<String, Object>> transactions = jdbcTemplate.queryForList(paymentsQuery, payPropId);
            
            // Calculate totals
            BigDecimal totalIncoming = BigDecimal.ZERO;
            BigDecimal totalOutgoing = BigDecimal.ZERO;
            BigDecimal totalCommissions = BigDecimal.ZERO;
            BigDecimal currentBalance = BigDecimal.ZERO;
            
            // Month-to-date calculations
            LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
            BigDecimal monthlyIncoming = BigDecimal.ZERO;
            BigDecimal monthlyOutgoing = BigDecimal.ZERO;
            
            // Transaction categorization
            Map<String, BigDecimal> incomeByCategory = new HashMap<>();
            Map<String, BigDecimal> expensesByCategory = new HashMap<>();
            
            for (Map<String, Object> transaction : transactions) {
                BigDecimal amount = (BigDecimal) transaction.get("amount");
                BigDecimal serviceFee = (BigDecimal) transaction.get("service_fee");
                BigDecimal transactionFee = (BigDecimal) transaction.get("transaction_fee");
                String category = (String) transaction.get("category_name");
                java.sql.Date transferDate = (java.sql.Date) transaction.get("payment_batch_transfer_date");
                
                // Determine transaction type based on category
                boolean isIncoming = "Owner".equals(category) || amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
                
                if (amount != null) {
                    if (isIncoming) {
                        totalIncoming = totalIncoming.add(amount);
                        if (category != null) {
                            incomeByCategory.merge(category, amount, BigDecimal::add);
                        }
                        
                        // Monthly calculation
                        if (transferDate != null && !transferDate.toLocalDate().isBefore(monthStart)) {
                            monthlyIncoming = monthlyIncoming.add(amount);
                        }
                    } else {
                        // Make outgoing amounts positive for display
                        BigDecimal positiveAmount = amount.abs();
                        totalOutgoing = totalOutgoing.add(positiveAmount);
                        if (category != null) {
                            expensesByCategory.merge(category, positiveAmount, BigDecimal::add);
                        }
                        
                        // Monthly calculation
                        if (transferDate != null && !transferDate.toLocalDate().isBefore(monthStart)) {
                            monthlyOutgoing = monthlyOutgoing.add(positiveAmount);
                        }
                    }
                }
                
                // Calculate commissions from fees
                if (serviceFee != null) {
                    totalCommissions = totalCommissions.add(serviceFee);
                }
                if (transactionFee != null) {
                    totalCommissions = totalCommissions.add(transactionFee);
                }
            }
            
            // Get current account balance from ICDN data
            String balanceQuery = """
                SELECT 
                    SUM(CASE WHEN pi.distribution_type = 'Revenue' THEN pi.amount ELSE -pi.amount END) as current_balance
                FROM payprop_report_icdn pi 
                WHERE pi.payprop_id = ?
                """;
                
            try {
                BigDecimal balance = jdbcTemplate.queryForObject(balanceQuery, BigDecimal.class, payPropId);
                currentBalance = balance != null ? balance : BigDecimal.ZERO;
            } catch (Exception e) {
                currentBalance = BigDecimal.ZERO;
            }
            
            // Get recent transaction details for display
            List<Map<String, Object>> recentTransactions = transactions.stream()
                .limit(10)
                .collect(Collectors.toList());
            
            // Build comprehensive summary
            summary.put("totalIncoming", totalIncoming);
            summary.put("totalOutgoing", totalOutgoing);
            summary.put("netIncome", totalIncoming.subtract(totalOutgoing));
            summary.put("totalCommissions", totalCommissions);
            summary.put("currentBalance", currentBalance);
            summary.put("transactionCount", transactions.size());
            
            // Monthly data
            summary.put("monthlyIncoming", monthlyIncoming);
            summary.put("monthlyOutgoing", monthlyOutgoing);
            summary.put("monthlyNet", monthlyIncoming.subtract(monthlyOutgoing));
            
            // Category breakdowns
            summary.put("incomeByCategory", incomeByCategory);
            summary.put("expensesByCategory", expensesByCategory);
            
            // Transaction details
            summary.put("recentTransactions", recentTransactions);
            
            // Property info
            summary.put("propertyName", property.getPropertyName());
            summary.put("payPropId", payPropId);
            summary.put("monthlyRent", property.getMonthlyPayment());
            
        } catch (Exception e) {
            return getEmptyFinancialSummary();
        }
        
        return summary;
    }
    
    /**
     * Legacy financial calculation using original tables
     */
    private Map<String, Object> calculateLegacyFinancialSummary(Property property) {
        Map<String, Object> summary = new HashMap<>();
        
        try {
            // Get financial transactions using legacy approach
            List<FinancialTransaction> allTransactions = financialTransactionRepository.findAll();
            List<FinancialTransaction> propertyTransactions = allTransactions.stream()
                .filter(t -> property.getId().toString().equals(t.getPropertyId()))
                .collect(Collectors.toList());
            
            // Calculate totals from financial transactions
            BigDecimal totalIncome = propertyTransactions.stream()
                .filter(t -> "invoice".equals(t.getTransactionType()))
                .map(FinancialTransaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
            BigDecimal totalCommissions = propertyTransactions.stream()
                .map(FinancialTransaction::getCommissionAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            summary.put("totalIncoming", totalIncome);
            summary.put("totalCommissions", totalCommissions);
            summary.put("netIncome", totalIncome.subtract(totalCommissions));
            summary.put("transactionCount", propertyTransactions.size());
            summary.put("recentTransactions", propertyTransactions.stream().limit(10).collect(Collectors.toList()));
            
        } catch (Exception e) {
            return getEmptyFinancialSummary();
        }
        
        return summary;
    }
    
    /**
     * Return empty financial summary with safe defaults
     */
    private Map<String, Object> getEmptyFinancialSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalIncoming", BigDecimal.ZERO);
        summary.put("totalOutgoing", BigDecimal.ZERO);
        summary.put("netIncome", BigDecimal.ZERO);
        summary.put("totalCommissions", BigDecimal.ZERO);
        summary.put("currentBalance", BigDecimal.ZERO);
        summary.put("transactionCount", 0);
        summary.put("monthlyIncoming", BigDecimal.ZERO);
        summary.put("monthlyOutgoing", BigDecimal.ZERO);
        summary.put("monthlyNet", BigDecimal.ZERO);
        summary.put("recentTransactions", List.of());
        summary.put("incomeByCategory", new HashMap<>());
        summary.put("expensesByCategory", new HashMap<>());
        return summary;
    }
    
    /**
     * Calculate financial summary for a customer (across all their properties)
     */
    private Map<String, Object> calculateCustomerFinancialSummary(Integer customerId) {
        Map<String, Object> summary = new HashMap<>();
        
        try {
            // Get all properties for this customer
            List<Property> customerProperties = propertyService.findByPropertyOwnerId(customerId.longValue());
            List<Long> propertyIds = customerProperties.stream()
                .map(Property::getId)
                .collect(Collectors.toList());
            
            // Get all financial transactions for customer's properties
            List<FinancialTransaction> allTransactions = financialTransactionRepository.findAll();
            List<FinancialTransaction> customerTransactions = allTransactions.stream()
                .filter(t -> propertyIds.contains(t.getPropertyId()))
                .collect(Collectors.toList());
            
            // Calculate totals
            BigDecimal totalIncome = customerTransactions.stream()
                .filter(t -> "invoice".equals(t.getTransactionType()))
                .map(FinancialTransaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
            BigDecimal totalCommissions = customerTransactions.stream()
                .map(FinancialTransaction::getCommissionAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
            BigDecimal netOwnerIncome = customerTransactions.stream()
                .map(FinancialTransaction::getNetToOwnerAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            summary.put("totalIncome", totalIncome);
            summary.put("totalCommissions", totalCommissions);
            summary.put("netOwnerIncome", netOwnerIncome);
            summary.put("transactionCount", customerTransactions.size());
            summary.put("propertyCount", customerProperties.size());
            
            // Property breakdown
            List<Map<String, Object>> propertyBreakdown = customerProperties.stream()
                .map(property -> {
                    Map<String, Object> propData = new HashMap<>();
                    propData.put("propertyId", property.getId());
                    propData.put("propertyName", property.getPropertyName());
                    propData.put("monthlyRent", property.getMonthlyPayment());
                    
                    // Calculate property-specific totals
                    BigDecimal propIncome = customerTransactions.stream()
                        .filter(t -> property.getId().equals(t.getPropertyId()) && 
                                   "invoice".equals(t.getTransactionType()))
                        .map(FinancialTransaction::getAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                    propData.put("totalIncome", propIncome);
                    
                    return propData;
                })
                .collect(Collectors.toList());
                
            summary.put("propertyBreakdown", propertyBreakdown);
            
        } catch (Exception e) {
            summary.put("error", "Customer calculation error: " + e.getMessage());
            summary.put("totalIncome", BigDecimal.ZERO);
            summary.put("totalCommissions", BigDecimal.ZERO);
            summary.put("netOwnerIncome", BigDecimal.ZERO);
        }
        
        return summary;
    }
}
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
        
        System.out.println("DEBUG: Financial summary endpoint called for ID: " + id);
        System.out.println("DEBUG: Data source config: " + dataSource);
        
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
                
                // Check table structure
                try {
                    String columnQuery = "SHOW COLUMNS FROM property LIKE '%prop%'";
                    List<Map<String, Object>> columns = jdbcTemplate.queryForList(columnQuery);
                    debug.put("property_payprop_columns", columns);
                } catch (Exception e2) {
                    debug.put("column_check_error", e2.getMessage());
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
        
        System.out.println("DEBUG: calculatePropertyFinancialSummary called");
        System.out.println("DEBUG: Property ID: " + (property != null ? property.getId() : "null"));
        System.out.println("DEBUG: PayProp ID: " + (property != null ? property.getPayPropId() : "null"));
        System.out.println("DEBUG: Data source: " + dataSource);
        
        try {
            if (property == null) {
                System.out.println("DEBUG: Property is null, returning empty summary");
                return getEmptyFinancialSummary();
            }
            
            // Use PayProp data if available and configured
            if ("PAYPROP".equals(dataSource) && property.getPayPropId() != null) {
                System.out.println("DEBUG: Using PayProp data source");
                return calculatePayPropFinancialSummary(property);
            } else {
                System.out.println("DEBUG: Using legacy data source (dataSource=" + dataSource + ", payPropId=" + property.getPayPropId() + ")");
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
            String paymentsQuery = """
                SELECT 
                    pap.payprop_id,
                    pap.amount,
                    pap.commission_amount,
                    pap.net_amount,
                    pap.transaction_type,
                    pap.transaction_date,
                    pap.description,
                    pap.reference,
                    pap.invoice_category,
                    pap.payment_category,
                    pap.balance_impact,
                    pap.is_incoming,
                    pap.incoming_transaction_id,
                    pap.created_at
                FROM payprop_report_all_payments pap 
                WHERE pap.payprop_id = ? 
                ORDER BY pap.transaction_date DESC, pap.created_at DESC
                LIMIT 100
                """;
                
            System.out.println("DEBUG: Executing query for PayProp ID: " + payPropId);
            List<Map<String, Object>> transactions = jdbcTemplate.queryForList(paymentsQuery, payPropId);
            System.out.println("DEBUG: Found " + transactions.size() + " transactions");
            
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
                BigDecimal commissionAmount = (BigDecimal) transaction.get("commission_amount");
                Boolean isIncoming = (Boolean) transaction.get("is_incoming");
                String category = (String) transaction.get("invoice_category");
                LocalDateTime transactionDate = (LocalDateTime) transaction.get("transaction_date");
                
                if (amount != null) {
                    if (Boolean.TRUE.equals(isIncoming)) {
                        totalIncoming = totalIncoming.add(amount);
                        if (category != null) {
                            incomeByCategory.merge(category, amount, BigDecimal::add);
                        }
                        
                        // Monthly calculation
                        if (transactionDate != null && !transactionDate.toLocalDate().isBefore(monthStart)) {
                            monthlyIncoming = monthlyIncoming.add(amount);
                        }
                    } else {
                        totalOutgoing = totalOutgoing.add(amount);
                        if (category != null) {
                            expensesByCategory.merge(category, amount, BigDecimal::add);
                        }
                        
                        // Monthly calculation
                        if (transactionDate != null && !transactionDate.toLocalDate().isBefore(monthStart)) {
                            monthlyOutgoing = monthlyOutgoing.add(amount);
                        }
                    }
                }
                
                if (commissionAmount != null) {
                    totalCommissions = totalCommissions.add(commissionAmount);
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
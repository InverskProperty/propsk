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
import site.easy.to.build.crm.util.AuthenticationUtils;

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
    
    @Autowired
    private AuthenticationUtils authenticationUtils;

    @Value("${crm.data.source:LEGACY}")
    private String dataSource;

    
    /**
     * Get financial summary for a customer (property owner)
     */
    @GetMapping("/customer/{id}/financial-summary")
    public ResponseEntity<Map<String, Object>> getCustomerFinancialSummary(
            @PathVariable("id") Integer customerId,
            Authentication authentication) {
        
        try {
            // Authorization check for customer financial data access
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            boolean hasAccess = false;

            // DEBUG: Show authentication details
            System.out.println("🔐 FinancialController: Authorization check for user: " + authentication.getName());
            System.out.println("   Authentication type: " + authentication.getClass().getSimpleName());
            System.out.println("   User ID: " + userId);
            System.out.println("   Requested customer ID: " + customerId);
            System.out.println("   Authorities:");
            authentication.getAuthorities().forEach(auth ->
                System.out.println("     - " + auth.getAuthority()));

            // Role checks with debug output
            boolean hasManager = AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER");
            boolean hasEmployee = AuthorizationUtil.hasRole(authentication, "ROLE_EMPLOYEE");
            boolean hasOidc = AuthorizationUtil.hasRole(authentication, "ROLE_OIDC_USER");
            boolean hasOwner = AuthorizationUtil.hasRole(authentication, "ROLE_OWNER");
            boolean hasPropertyOwner = AuthorizationUtil.hasRole(authentication, "ROLE_PROPERTY_OWNER");

            System.out.println("   Role checks:");
            System.out.println("     - ROLE_MANAGER: " + hasManager);
            System.out.println("     - ROLE_EMPLOYEE: " + hasEmployee);
            System.out.println("     - ROLE_OIDC_USER: " + hasOidc);
            System.out.println("     - ROLE_OWNER: " + hasOwner);
            System.out.println("     - ROLE_PROPERTY_OWNER: " + hasPropertyOwner);

            if (hasManager || hasEmployee || hasOidc) {
                hasAccess = true;
                System.out.println("✅ Access granted: Admin/Employee/OIDC user");
            } else if ((hasOwner || hasPropertyOwner) && customerId.equals(userId)) {
                // Property owners can only see their own financial data
                hasAccess = true;
                System.out.println("✅ Access granted: Property owner accessing own data");
            } else {
                System.out.println("❌ Access denied: No matching roles or not owner");
            }

            if (!hasAccess) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Access denied");
                error.put("debug_user_id", userId);
                error.put("debug_customer_id", customerId);
                error.put("debug_roles", authentication.getAuthorities().stream()
                    .map(auth -> auth.getAuthority()).collect(Collectors.toList()));
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
     * DEBUG: Check specific property PayProp ID and matching
     */
    @GetMapping("/debug/property/{id}")
    public ResponseEntity<Map<String, Object>> debugProperty(@PathVariable("id") String id) {
        Map<String, Object> debug = new HashMap<>();
        
        try {
            // Find property
            Property property = propertyService.findById(Long.parseLong(id));
            if (property == null) {
                debug.put("error", "Property not found");
                return ResponseEntity.ok(debug);
            }
            
            debug.put("property_id", property.getId());
            debug.put("property_name", property.getPropertyName());
            debug.put("current_payprop_id", property.getPayPropId());
            debug.put("address_line_1", property.getAddressLine1());
            debug.put("city", property.getCity());
            
            // Try to find matching export property
            String matchQuery = """
                SELECT payprop_id, address_first_line, address_city, name
                FROM payprop_export_properties 
                WHERE address_first_line LIKE ? OR address_city = ?
                LIMIT 3
                """;
            
            List<Map<String, Object>> matches = jdbcTemplate.queryForList(matchQuery, 
                "%" + property.getAddressLine1() + "%", 
                property.getCity());
            debug.put("potential_matches", matches);
            
            debug.put("status", "success");
            
        } catch (Exception e) {
            debug.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(debug);
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
     * ADMIN: Add piyush as owner/customer to all uday's properties
     */
    @PostMapping("/admin/assign-piyush-to-uday-properties")
    public ResponseEntity<Map<String, Object>> assignPiyushToUdayProperties(Authentication authentication) {
        Map<String, Object> result = new HashMap<>();

        // Security check - only allow admin users
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") &&
            !AuthorizationUtil.hasRole(authentication, "ROLE_OIDC_USER")) {
            result.put("error", "Access denied - admin only");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
        }

        try {
            // Step 1: Find uday's customer ID
            String findUdayQuery = "SELECT customer_id, full_name, email FROM customer WHERE email LIKE '%uday%' OR full_name LIKE '%uday%' OR email = 'uday@sunflaguk.com'";
            List<Map<String, Object>> udayResults = jdbcTemplate.queryForList(findUdayQuery);

            if (udayResults.isEmpty()) {
                result.put("error", "Uday customer not found");
                return ResponseEntity.ok(result);
            }

            Map<String, Object> udayCustomer = udayResults.get(0);
            Long udayCustomerId = ((Number) udayCustomer.get("customer_id")).longValue();
            result.put("uday_customer", udayCustomer);

            // Step 2: Find piyush's customer ID
            String findPiyushQuery = "SELECT customer_id, full_name, email FROM customer WHERE email LIKE '%piyush%' OR full_name LIKE '%piyush%'";
            List<Map<String, Object>> piyushResults = jdbcTemplate.queryForList(findPiyushQuery);

            if (piyushResults.isEmpty()) {
                result.put("error", "Piyush customer not found");
                return ResponseEntity.ok(result);
            }

            Map<String, Object> piyushCustomer = piyushResults.get(0);
            Long piyushCustomerId = ((Number) piyushCustomer.get("customer_id")).longValue();
            result.put("piyush_customer", piyushCustomer);

            // Step 3: Find all properties assigned to uday
            String findUdayPropertiesQuery = """
                SELECT DISTINCT p.property_id, p.property_name, p.address_line_1
                FROM property p
                INNER JOIN customer_property_assignment cpa ON p.property_id = cpa.property_id
                WHERE cpa.customer_id = ? AND cpa.assignment_type = 'OWNER'
                """;

            List<Map<String, Object>> udayProperties = jdbcTemplate.queryForList(findUdayPropertiesQuery, udayCustomerId);
            result.put("uday_properties_count", udayProperties.size());
            result.put("uday_properties", udayProperties);

            // Step 4: Add piyush as OWNER to all uday's properties
            int assignmentsAdded = 0;
            for (Map<String, Object> property : udayProperties) {
                Long propertyId = ((Number) property.get("property_id")).longValue();

                // Check if assignment already exists
                String checkQuery = "SELECT COUNT(*) FROM customer_property_assignment WHERE customer_id = ? AND property_id = ? AND assignment_type = 'OWNER'";
                Integer existingCount = jdbcTemplate.queryForObject(checkQuery, Integer.class, piyushCustomerId, propertyId);

                if (existingCount == 0) {
                    // Insert new assignment
                    String insertQuery = """
                        INSERT INTO customer_property_assignment (customer_id, property_id, assignment_type, created_at, created_by)
                        VALUES (?, ?, 'OWNER', NOW(), ?)
                        """;

                    jdbcTemplate.update(insertQuery, piyushCustomerId, propertyId, authentication.getName());
                    assignmentsAdded++;
                }
            }

            result.put("assignments_added", assignmentsAdded);
            result.put("message", String.format("Successfully added piyush as owner to %d of uday's properties", assignmentsAdded));
            result.put("status", "success");

        } catch (Exception e) {
            result.put("error", "Database error: " + e.getMessage());
            result.put("status", "error");
            e.printStackTrace();
        }

        return ResponseEntity.ok(result);
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
            // Query combined invoices and payments in chronological order
            String chronologicalQuery = """
                SELECT 
                    date,
                    type,
                    tenant_name,
                    amount,
                    commission_amount,
                    owner_amount,
                    description,
                    reference
                FROM (
                    -- Invoices
                    SELECT 
                        transaction_date as date,
                        'Invoice' as type,
                        tenant_name,
                        amount,
                        0 as commission_amount,
                        0 as owner_amount,
                        CONCAT(category_name, ' invoice') as description,
                        reference
                    FROM payprop_report_icdn 
                    WHERE property_payprop_id = ?
                    
                    UNION ALL
                    
                    -- Payments with distributions (grouped by transaction ID)
                    SELECT 
                        payment_batch_transfer_date as date,
                        'Payment' as type,
                        incoming_tenant_name as tenant_name,
                        incoming_transaction_amount as amount,
                        SUM(CASE WHEN category_name = 'Commission' THEN amount ELSE 0 END) as commission_amount,
                        SUM(CASE WHEN category_name = 'Owner' THEN amount ELSE 0 END) as owner_amount,
                        MAX(incoming_transaction_type) as description,
                        GROUP_CONCAT(DISTINCT reference) as reference
                    FROM payprop_report_all_payments 
                    WHERE incoming_property_payprop_id = ?
                    GROUP BY incoming_transaction_id, payment_batch_transfer_date, incoming_tenant_name, incoming_transaction_amount
                ) combined
                ORDER BY date DESC
                LIMIT 100
                """;
                
            System.out.println("DEBUG: Executing chronological query for PayProp ID: " + payPropId);
            List<Map<String, Object>> transactions = jdbcTemplate.queryForList(chronologicalQuery, payPropId, payPropId);
            System.out.println("DEBUG: Found " + transactions.size() + " transactions");
            
            // Calculate totals from chronological data
            BigDecimal totalInvoiced = BigDecimal.ZERO;
            BigDecimal totalReceived = BigDecimal.ZERO;
            BigDecimal totalCommissions = BigDecimal.ZERO;
            BigDecimal totalToOwners = BigDecimal.ZERO;
            
            // Month-to-date calculations
            LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
            BigDecimal monthlyInvoiced = BigDecimal.ZERO;
            BigDecimal monthlyReceived = BigDecimal.ZERO;
            
            // Transaction categorization
            Map<String, BigDecimal> invoicesByType = new HashMap<>();
            Map<String, BigDecimal> paymentsByType = new HashMap<>();
            
            for (Map<String, Object> transaction : transactions) {
                String type = (String) transaction.get("type");
                BigDecimal amount = (BigDecimal) transaction.get("amount");
                BigDecimal commissionAmount = (BigDecimal) transaction.get("commission_amount");
                BigDecimal ownerAmount = (BigDecimal) transaction.get("owner_amount");
                java.sql.Date transactionDate = (java.sql.Date) transaction.get("date");
                String description = (String) transaction.get("description");
                
                if (amount != null) {
                    if ("Invoice".equals(type)) {
                        // Invoice - money owed
                        totalInvoiced = totalInvoiced.add(amount);
                        if (description != null) {
                            invoicesByType.merge(description, amount, BigDecimal::add);
                        }
                        
                        // Monthly calculation
                        if (transactionDate != null && !transactionDate.toLocalDate().isBefore(monthStart)) {
                            monthlyInvoiced = monthlyInvoiced.add(amount);
                        }
                    } else if ("Payment".equals(type)) {
                        // Payment - money received
                        totalReceived = totalReceived.add(amount);
                        if (description != null) {
                            paymentsByType.merge(description, amount, BigDecimal::add);
                        }
                        
                        // Add commission and owner amounts
                        if (commissionAmount != null) {
                            totalCommissions = totalCommissions.add(commissionAmount);
                        }
                        if (ownerAmount != null) {
                            totalToOwners = totalToOwners.add(ownerAmount);
                        }
                        
                        // Monthly calculation
                        if (transactionDate != null && !transactionDate.toLocalDate().isBefore(monthStart)) {
                            monthlyReceived = monthlyReceived.add(amount);
                        }
                    }
                }
            }
            
            // Calculate outstanding balance (invoiced - received)
            BigDecimal outstandingBalance = totalInvoiced.subtract(totalReceived);
            
            // Get recent transaction details for display (already in chronological order)
            List<Map<String, Object>> recentTransactions = transactions.stream()
                .limit(20)  // Show more transactions for chronological view
                .collect(Collectors.toList());
            
            // Build comprehensive summary with new structure
            summary.put("totalInvoiced", totalInvoiced);
            summary.put("totalReceived", totalReceived);
            summary.put("totalIncoming", totalReceived);  // For frontend compatibility
            summary.put("totalCommissions", totalCommissions);
            summary.put("totalToOwners", totalToOwners);
            summary.put("netIncome", totalToOwners);  // Net to owner for frontend
            summary.put("outstandingBalance", outstandingBalance);
            summary.put("transactionCount", transactions.size());
            
            // Monthly data
            summary.put("monthlyInvoiced", monthlyInvoiced);
            summary.put("monthlyReceived", monthlyReceived);
            summary.put("monthlyNet", monthlyReceived.subtract(monthlyInvoiced));
            
            // Category breakdowns
            summary.put("invoicesByType", invoicesByType);
            summary.put("paymentsByType", paymentsByType);
            
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
            // Get all properties for this customer (with delegated user filtering)
            List<Property> customerProperties = propertyService.findPropertiesAccessibleByCustomer(customerId.longValue());
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
package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.entity.FinancialTransaction;
import site.easy.to.build.crm.entity.Payment;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.repository.FinancialTransactionRepository;
import site.easy.to.build.crm.repository.PaymentRepository;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.util.AuthorizationUtil;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    /**
     * Get financial summary for a specific property
     */
    @GetMapping("/property/{id}/financial-summary")
    public ResponseEntity<Map<String, Object>> getPropertyFinancialSummary(
            @PathVariable("id") Long propertyId, 
            Authentication authentication) {
        
        try {
            // Check if property exists
            Property property = propertyService.findById(propertyId);
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
            
            // Get financial data
            Map<String, Object> financialData = calculatePropertyFinancialSummary(propertyId);
            
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
     * Calculate financial summary for a specific property
     */
    private Map<String, Object> calculatePropertyFinancialSummary(Long propertyId) {
        Map<String, Object> summary = new HashMap<>();
        
        try {
            // Get financial transactions for this property
            List<FinancialTransaction> allTransactions = financialTransactionRepository.findAll();
            List<FinancialTransaction> propertyTransactions = allTransactions.stream()
                .filter(t -> propertyId.equals(t.getPropertyId()))
                .collect(Collectors.toList());
            
            // Get payments for this property
            List<Payment> allPayments = paymentRepository.findAll();
            List<Payment> propertyPayments = allPayments.stream()
                .filter(p -> propertyId.equals(p.getPropertyId()))
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
                
            BigDecimal netOwnerIncome = propertyTransactions.stream()
                .map(FinancialTransaction::getNetToOwnerAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Get recent transactions (last 5)
            List<FinancialTransaction> recentTransactions = propertyTransactions.stream()
                .sorted((t1, t2) -> {
                    if (t1.getTransactionDate() == null && t2.getTransactionDate() == null) return 0;
                    if (t1.getTransactionDate() == null) return 1;
                    if (t2.getTransactionDate() == null) return -1;
                    return t2.getTransactionDate().compareTo(t1.getTransactionDate());
                })
                .limit(5)
                .collect(Collectors.toList());
            
            // Prepare response
            summary.put("totalIncome", totalIncome);
            summary.put("totalCommissions", totalCommissions);
            summary.put("netOwnerIncome", netOwnerIncome);
            summary.put("transactionCount", propertyTransactions.size());
            summary.put("paymentCount", propertyPayments.size());
            summary.put("recentTransactions", recentTransactions);
            
            // Calculate current month stats
            LocalDate now = LocalDate.now();
            LocalDate monthStart = now.withDayOfMonth(1);
            
            BigDecimal currentMonthIncome = propertyTransactions.stream()
                .filter(t -> t.getTransactionDate() != null && 
                           !t.getTransactionDate().isBefore(monthStart) &&
                           "invoice".equals(t.getTransactionType()))
                .map(FinancialTransaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
            summary.put("currentMonthIncome", currentMonthIncome);
            
        } catch (Exception e) {
            // Return safe defaults if calculation fails
            summary.put("totalIncome", BigDecimal.ZERO);
            summary.put("totalCommissions", BigDecimal.ZERO);
            summary.put("netOwnerIncome", BigDecimal.ZERO);
            summary.put("transactionCount", 0);
            summary.put("paymentCount", 0);
            summary.put("recentTransactions", List.of());
            summary.put("currentMonthIncome", BigDecimal.ZERO);
            summary.put("error", "Calculation error: " + e.getMessage());
        }
        
        return summary;
    }
    
    /**
     * Calculate financial summary for a customer (across all their properties)
     */
    private Map<String, Object> calculateCustomerFinancialSummary(Integer customerId) {
        Map<String, Object> summary = new HashMap<>();
        
        try {
            // Get all properties for this customer
            List<Property> customerProperties = propertyService.findByPropertyOwnerId(customerId);
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
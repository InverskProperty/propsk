package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.entity.FinancialTransaction;
import site.easy.to.build.crm.entity.Invoice;
import site.easy.to.build.crm.repository.FinancialTransactionRepository;
import site.easy.to.build.crm.repository.InvoiceRepository;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for retrieving lease transaction reports
 * Provides endpoints to query financial transactions for leases within date ranges
 */
@RestController
@RequestMapping("/api/lease-transactions")
public class LeaseTransactionReportController {

    private final InvoiceRepository invoiceRepository;
    private final FinancialTransactionRepository financialTransactionRepository;

    @Autowired
    public LeaseTransactionReportController(
            InvoiceRepository invoiceRepository,
            FinancialTransactionRepository financialTransactionRepository) {
        this.invoiceRepository = invoiceRepository;
        this.financialTransactionRepository = financialTransactionRepository;
    }

    /**
     * Get all active leases (invoices) with transactions for a date range
     *
     * Example: GET /api/lease-transactions/report?startDate=2025-10-22&endDate=2025-11-21
     */
    @GetMapping("/report")
    public ResponseEntity<Map<String, Object>> getLeaseTransactionsReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        System.out.println("📊 LEASE TRANSACTION REPORT REQUEST:");
        System.out.println("   Start Date: " + startDate);
        System.out.println("   End Date: " + endDate);

        // Get all active leases within the date range
        List<Invoice> activeLeases = invoiceRepository.findByStartDateBetween(startDate, endDate);

        System.out.println("   Found " + activeLeases.size() + " leases starting in this period");

        // Build report for each lease
        List<Map<String, Object>> leaseReports = new ArrayList<>();

        for (Invoice lease : activeLeases) {
            Map<String, Object> leaseReport = new HashMap<>();

            // Lease details
            leaseReport.put("leaseId", lease.getId());
            leaseReport.put("leaseReference", lease.getLeaseReference());
            leaseReport.put("startDate", lease.getStartDate());
            leaseReport.put("endDate", lease.getEndDate());
            leaseReport.put("amount", lease.getAmount());
            leaseReport.put("frequency", lease.getFrequency());
            leaseReport.put("isActive", lease.getIsActive());

            // Customer details
            if (lease.getCustomer() != null) {
                leaseReport.put("customerId", lease.getCustomer().getCustomerId());
                leaseReport.put("customerName", lease.getCustomer().getName());
                leaseReport.put("customerEmail", lease.getCustomer().getEmail());
            }

            // Property details
            if (lease.getProperty() != null) {
                leaseReport.put("propertyId", lease.getProperty().getId());
                leaseReport.put("propertyName", lease.getProperty().getPropertyName());
                leaseReport.put("propertyAddress", lease.getProperty().getAddress());
            }

            // Get all transactions for this property in the date range
            List<FinancialTransaction> transactions = new ArrayList<>();
            if (lease.getProperty() != null && lease.getProperty().getPayPropId() != null) {
                transactions = financialTransactionRepository
                        .findByPropertyIdAndTransactionDateBetween(
                                lease.getProperty().getPayPropId(),
                                startDate,
                                endDate);
            }

            System.out.println("   Lease #" + lease.getId() + " (" + lease.getLeaseReference() + "): " +
                             transactions.size() + " transactions");

            // Format transactions
            List<Map<String, Object>> formattedTransactions = transactions.stream()
                    .map(this::formatTransaction)
                    .collect(Collectors.toList());

            leaseReport.put("transactions", formattedTransactions);
            leaseReport.put("transactionCount", transactions.size());

            leaseReports.add(leaseReport);
        }

        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("startDate", startDate);
        response.put("endDate", endDate);
        response.put("leaseCount", leaseReports.size());
        response.put("leases", leaseReports);
        response.put("timestamp", new Date());

        System.out.println("✅ Report complete: " + leaseReports.size() + " leases");

        return ResponseEntity.ok(response);
    }

    /**
     * Get all transactions for all properties in a date range (not lease-specific)
     *
     * Example: GET /api/lease-transactions/all?startDate=2025-10-22&endDate=2025-11-21
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllTransactions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        System.out.println("📊 ALL TRANSACTIONS REQUEST:");
        System.out.println("   Start Date: " + startDate);
        System.out.println("   End Date: " + endDate);

        // Get all transactions in date range
        List<FinancialTransaction> transactions = financialTransactionRepository
                .findByTransactionDateBetween(startDate, endDate);

        System.out.println("   Found " + transactions.size() + " total transactions");

        // Format transactions
        List<Map<String, Object>> formattedTransactions = transactions.stream()
                .map(this::formatTransaction)
                .collect(Collectors.toList());

        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("startDate", startDate);
        response.put("endDate", endDate);
        response.put("transactionCount", transactions.size());
        response.put("transactions", formattedTransactions);
        response.put("timestamp", new Date());

        System.out.println("✅ Retrieved " + transactions.size() + " transactions");

        return ResponseEntity.ok(response);
    }

    /**
     * Get transactions for a specific lease by ID
     *
     * Example: GET /api/lease-transactions/lease/123?startDate=2025-10-22&endDate=2025-11-21
     */
    @GetMapping("/lease/{leaseId}")
    public ResponseEntity<Map<String, Object>> getLeaseTransactions(
            @PathVariable Long leaseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        System.out.println("📊 LEASE TRANSACTION REQUEST:");
        System.out.println("   Lease ID: " + leaseId);
        System.out.println("   Start Date: " + startDate);
        System.out.println("   End Date: " + endDate);

        // Find the lease
        Optional<Invoice> leaseOpt = invoiceRepository.findById(leaseId);

        if (!leaseOpt.isPresent()) {
            System.out.println("❌ Lease not found: " + leaseId);
            return ResponseEntity.notFound().build();
        }

        Invoice lease = leaseOpt.get();

        // Get transactions
        List<FinancialTransaction> transactions = new ArrayList<>();
        if (lease.getProperty() != null && lease.getProperty().getPayPropId() != null) {
            transactions = financialTransactionRepository
                    .findByPropertyIdAndTransactionDateBetween(
                            lease.getProperty().getPayPropId(),
                            startDate,
                            endDate);
        }

        System.out.println("   Found " + transactions.size() + " transactions");

        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("leaseId", lease.getId());
        response.put("leaseReference", lease.getLeaseReference());
        response.put("startDate", startDate);
        response.put("endDate", endDate);
        response.put("transactionCount", transactions.size());

        List<Map<String, Object>> formattedTransactions = transactions.stream()
                .map(this::formatTransaction)
                .collect(Collectors.toList());
        response.put("transactions", formattedTransactions);
        response.put("timestamp", new Date());

        System.out.println("✅ Report complete");

        return ResponseEntity.ok(response);
    }

    /**
     * Get summary statistics for transactions in a date range
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getTransactionSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        System.out.println("📊 TRANSACTION SUMMARY REQUEST:");
        System.out.println("   Start Date: " + startDate);
        System.out.println("   End Date: " + endDate);

        List<FinancialTransaction> transactions = financialTransactionRepository
                .findByTransactionDateBetween(startDate, endDate);

        Map<String, Object> summary = new HashMap<>();
        summary.put("startDate", startDate);
        summary.put("endDate", endDate);
        summary.put("totalTransactions", transactions.size());
        summary.put("totalAmount", transactions.stream()
                .map(FinancialTransaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        summary.put("totalCommission", transactions.stream()
                .map(FinancialTransaction::getCommissionAmount)
                .filter(Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        summary.put("totalNetToOwner", transactions.stream()
                .map(FinancialTransaction::getNetToOwnerAmount)
                .filter(Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));

        // Group by transaction type
        Map<String, Long> byType = transactions.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getTransactionType() != null ? t.getTransactionType() : "UNKNOWN",
                        Collectors.counting()));
        summary.put("transactionsByType", byType);

        // Group by property
        Map<String, Long> byProperty = transactions.stream()
                .filter(t -> t.getPropertyName() != null)
                .collect(Collectors.groupingBy(
                        FinancialTransaction::getPropertyName,
                        Collectors.counting()));
        summary.put("transactionsByProperty", byProperty);

        summary.put("timestamp", new Date());

        System.out.println("✅ Summary complete");

        return ResponseEntity.ok(summary);
    }

    /**
     * Helper method to format a transaction for JSON response
     */
    private Map<String, Object> formatTransaction(FinancialTransaction transaction) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", transaction.getId());
        map.put("payPropTransactionId", transaction.getPayPropTransactionId());
        map.put("transactionDate", transaction.getTransactionDate());
        map.put("amount", transaction.getAmount());
        map.put("matchedAmount", transaction.getMatchedAmount());
        map.put("transactionType", transaction.getTransactionType());
        map.put("description", transaction.getDescription());
        map.put("propertyId", transaction.getPropertyId());
        map.put("propertyName", transaction.getPropertyName());
        map.put("tenantId", transaction.getTenantId());
        map.put("tenantName", transaction.getTenantName());
        map.put("categoryId", transaction.getCategoryId());
        map.put("categoryName", transaction.getCategoryName());
        map.put("commissionAmount", transaction.getCommissionAmount());
        map.put("commissionRate", transaction.getCommissionRate());
        map.put("netToOwnerAmount", transaction.getNetToOwnerAmount());
        map.put("dataSource", transaction.getDataSource());
        return map;
    }
}

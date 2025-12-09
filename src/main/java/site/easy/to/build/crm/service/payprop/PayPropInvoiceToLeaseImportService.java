package site.easy.to.build.crm.service.payprop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import site.easy.to.build.crm.entity.AssignmentType;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerPropertyAssignment;
import site.easy.to.build.crm.entity.CustomerType;
import site.easy.to.build.crm.entity.Invoice;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.Invoice.InvoiceFrequency;
import site.easy.to.build.crm.entity.Invoice.SyncStatus;
import site.easy.to.build.crm.repository.CustomerPropertyAssignmentRepository;
import site.easy.to.build.crm.repository.CustomerRepository;
import site.easy.to.build.crm.repository.InvoiceRepository;
import site.easy.to.build.crm.repository.PropertyRepository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service to import PayProp invoices as local lease records
 *
 * KEY CONCEPT: Each PayProp invoice represents a distinct lease/tenancy
 * - tenant_payprop_id = WHO (tenant)
 * - property_payprop_id = WHERE (property)
 * - from_date / to_date = WHEN (lease period)
 * - gross_amount = HOW MUCH (rent)
 *
 * This service imports these invoice records into the local 'invoices' table,
 * effectively creating lease records that can handle:
 * - Multiple tenants per property
 * - Mid-month tenant changes
 * - Different rent amounts per tenancy
 */
@Service
@Transactional
public class PayPropInvoiceToLeaseImportService {

    private static final Logger log = LoggerFactory.getLogger(PayPropInvoiceToLeaseImportService.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private CustomerPropertyAssignmentRepository assignmentRepository;

    /**
     * Import all PayProp invoices as lease records
     * Handles the Apartment 40 case: 3 different invoices = 3 different leases
     */
    public ImportResult importAllInvoicesAsLeases() {
        log.info("🔄 Starting PayProp invoice → lease import");

        ImportResult result = new ImportResult();
        result.startTime = LocalDateTime.now();

        try {
            // Fetch all PayProp invoices
            List<PayPropInvoiceData> payPropInvoices = fetchPayPropInvoices();
            log.info("Found {} PayProp invoices to import", payPropInvoices.size());

            result.totalPayPropInvoices = payPropInvoices.size();

            // Import each invoice as a lease
            for (PayPropInvoiceData invoiceData : payPropInvoices) {
                try {
                    Invoice lease = importInvoiceAsLease(invoiceData);
                    if (lease != null) {
                        result.successfulImports++;
                        if (invoiceData.isActiveInstruction) {
                            result.activeLeases++;
                        }
                    } else {
                        result.skippedInvoices++;
                    }
                } catch (Exception e) {
                    log.error("Failed to import invoice {}: {}", invoiceData.paypropId, e.getMessage());
                    result.failedImports++;
                    result.errors.add("Invoice " + invoiceData.paypropId + ": " + e.getMessage());
                }
            }

            result.success = true;
            result.endTime = LocalDateTime.now();

            log.info("✅ Invoice import complete: {} succeeded, {} failed, {} skipped out of {} total",
                    result.successfulImports, result.failedImports, result.skippedInvoices, result.totalPayPropInvoices);
            log.info("   Active leases: {}", result.activeLeases);

        } catch (Exception e) {
            log.error("❌ Invoice import failed", e);
            result.success = false;
            result.errorMessage = e.getMessage();
            result.endTime = LocalDateTime.now();
        }

        return result;
    }

    /**
     * Fetch all PayProp invoices from raw export table
     */
    private List<PayPropInvoiceData> fetchPayPropInvoices() throws SQLException {
        String sql = """
            SELECT
                payprop_id,
                property_payprop_id,
                tenant_payprop_id,
                property_name,
                tenant_display_name,
                tenant_business_name,
                tenant_first_name,
                tenant_last_name,
                gross_amount,
                from_date,
                to_date,
                frequency,
                frequency_code,
                payment_day,
                description,
                category_payprop_id,
                category_name,
                vat,
                vat_amount,
                is_active_instruction,
                account_type,
                debit_order
            FROM payprop_export_invoices
            ORDER BY property_payprop_id, from_date
            """;

        List<PayPropInvoiceData> invoices = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                PayPropInvoiceData data = new PayPropInvoiceData();
                data.paypropId = rs.getString("payprop_id");
                data.propertyPayPropId = rs.getString("property_payprop_id");
                data.tenantPayPropId = rs.getString("tenant_payprop_id");
                data.propertyName = rs.getString("property_name");
                data.tenantDisplayName = rs.getString("tenant_display_name");
                data.grossAmount = rs.getBigDecimal("gross_amount");

                java.sql.Date fromDate = rs.getDate("from_date");
                data.fromDate = fromDate != null ? fromDate.toLocalDate() : null;

                java.sql.Date toDate = rs.getDate("to_date");
                data.toDate = toDate != null ? toDate.toLocalDate() : null;

                data.frequency = rs.getString("frequency");
                data.frequencyCode = rs.getString("frequency_code");
                data.paymentDay = rs.getObject("payment_day") != null ? rs.getInt("payment_day") : null;
                data.description = rs.getString("description");
                data.categoryPayPropId = rs.getString("category_payprop_id");
                data.categoryName = rs.getString("category_name");
                data.vat = rs.getBoolean("vat");
                data.vatAmount = rs.getBigDecimal("vat_amount");
                data.isActiveInstruction = rs.getBoolean("is_active_instruction");
                data.accountType = rs.getString("account_type");
                data.debitOrder = rs.getBoolean("debit_order");

                invoices.add(data);
            }
        }

        return invoices;
    }

    /**
     * Import a single PayProp invoice as a lease record
     * Returns null if already imported
     */
    private Invoice importInvoiceAsLease(PayPropInvoiceData data) {
        // Check if already imported by PayProp ID
        if (invoiceRepository.findByPaypropId(data.paypropId).isPresent()) {
            log.debug("Invoice {} already imported, skipping", data.paypropId);
            return null;
        }

        // Find or resolve customer
        Customer customer = findOrResolveCustomer(data.tenantPayPropId, data.tenantDisplayName);
        if (customer == null) {
            log.warn("Cannot import invoice {}: Customer not found for tenant {}",
                    data.paypropId, data.tenantPayPropId);
            throw new RuntimeException("Customer not found for tenant: " + data.tenantPayPropId);
        }

        // Find or resolve property
        Property property = propertyRepository.findByPayPropId(data.propertyPayPropId).orElse(null);
        if (property == null) {
            log.warn("Cannot import invoice {}: Property not found for {}",
                    data.paypropId, data.propertyPayPropId);
            throw new RuntimeException("Property not found: " + data.propertyPayPropId);
        }

        // ✅ FIX: Check if there's an existing active lease for this property WITHOUT a PayProp ID
        // This handles cases where a lease was manually created (like BODEN-BLOCK-2025) and now
        // PayProp is sending an invoice for it. We should link to the existing lease, not create a duplicate.
        List<Invoice> existingLeases = invoiceRepository.findByPropertyAndIsActiveTrue(property);
        for (Invoice existingLease : existingLeases) {
            if (existingLease.getPaypropId() == null) {
                // Found an existing lease without PayProp ID - link it instead of creating new
                log.info("✅ Found existing lease {} for property {} without PayProp ID. Linking to PayProp invoice {}",
                        existingLease.getLeaseReference(), property.getPropertyName(), data.paypropId);

                existingLease.setPaypropId(data.paypropId);
                existingLease.setPaypropCustomerId(data.tenantPayPropId);
                existingLease.setSyncStatus(SyncStatus.synced);
                existingLease.setPaypropLastSync(LocalDateTime.now());

                return invoiceRepository.save(existingLease);
            }
        }

        // Create invoice (lease) record
        Invoice invoice = new Invoice();

        // PayProp sync fields
        invoice.setPaypropId(data.paypropId);
        invoice.setPaypropCustomerId(data.tenantPayPropId);
        invoice.setSyncStatus(SyncStatus.synced);
        invoice.setPaypropLastSync(LocalDateTime.now());

        // Relationships
        invoice.setCustomer(customer);
        invoice.setProperty(property);

        // Financial details
        invoice.setAmount(data.grossAmount);
        invoice.setVatIncluded(data.vat);
        invoice.setVatAmount(data.vatAmount);

        // Category
        invoice.setCategoryId(data.categoryPayPropId != null ? data.categoryPayPropId : "rent");
        invoice.setCategoryName(data.categoryName);

        // Frequency
        invoice.setFrequency(parseFrequency(data.frequency));
        invoice.setFrequencyCode(data.frequencyCode);
        invoice.setFrequencyMonths(parseFrequencyMonths(data.frequency));
        invoice.setPaymentDay(data.paymentDay);

        // Dates (this is the LEASE PERIOD)
        invoice.setStartDate(data.fromDate != null ? data.fromDate : LocalDate.now());
        invoice.setEndDate(data.toDate); // null if ongoing

        // Description
        String description = data.description != null && !data.description.trim().isEmpty()
            ? data.description
            : String.format("Lease for %s at %s", data.tenantDisplayName, data.propertyName);
        invoice.setDescription(description);

        // Status
        invoice.setIsActive(data.isActiveInstruction);
        invoice.setIsDebitOrder(data.debitOrder);

        // Account type
        if ("business".equalsIgnoreCase(data.accountType)) {
            invoice.setAccountType(site.easy.to.build.crm.entity.AccountType.business);
        } else {
            invoice.setAccountType(site.easy.to.build.crm.entity.AccountType.individual);
        }

        // Save invoice
        Invoice savedInvoice = invoiceRepository.save(invoice);

        // Create corresponding tenant assignment
        CustomerPropertyAssignment assignment = new CustomerPropertyAssignment();
        assignment.setCustomer(customer);
        assignment.setProperty(property);
        assignment.setAssignmentType(AssignmentType.TENANT);
        assignment.setStartDate(data.fromDate != null ? data.fromDate : LocalDate.now());
        assignment.setEndDate(data.toDate);
        assignment.setCreatedAt(LocalDateTime.now());
        assignment.setPaypropInvoiceId(savedInvoice.getId().toString());
        assignment.setSyncStatus("SYNCED");

        assignmentRepository.save(assignment);

        log.info("✅ Imported invoice {} as lease with tenant assignment: {} at {} (£{}/{})",
                data.paypropId, data.tenantDisplayName, data.propertyName,
                data.grossAmount, data.frequency);

        return savedInvoice;
    }

    /**
     * Find customer by PayProp tenant ID, creating placeholder if not found
     */
    private Customer findOrResolveCustomer(String tenantPayPropId, String displayName) {
        if (tenantPayPropId == null) {
            return null;
        }

        // Try to find existing customer
        Customer existing = customerRepository.findByPayPropEntityId(tenantPayPropId);
        if (existing != null) {
            return existing;
        }

        // Customer not found - check payprop_export_tenants for more info
        Map<String, String> tenantInfo = fetchTenantInfo(tenantPayPropId);

        // Create placeholder customer
        log.warn("⚠️ Customer not found for PayProp tenant {}. Creating placeholder customer: {}",
                 tenantPayPropId, displayName);

        Customer newCustomer = new Customer();
        newCustomer.setName(displayName != null ? displayName : "PayProp Tenant " + tenantPayPropId);
        newCustomer.setPayPropEntityId(tenantPayPropId);
        newCustomer.setCustomerType(CustomerType.TENANT);
        newCustomer.setIsTenant(true);

        // Add tenant info if available
        if (tenantInfo != null) {
            newCustomer.setEmail(tenantInfo.get("email"));
            newCustomer.setPhone(tenantInfo.get("phone"));
            newCustomer.setFirstName(tenantInfo.get("first_name"));
            newCustomer.setLastName(tenantInfo.get("last_name"));
        }

        // Set sync metadata
        newCustomer.setDataSource(site.easy.to.build.crm.entity.DataSource.PAYPROP);
        newCustomer.setNotes("Auto-created from PayProp invoice instruction. Tenant not found in customer database.");
        newCustomer.setCountry("United Kingdom");
        newCustomer.setCountryCode("GB");
        newCustomer.setCreatedAt(LocalDateTime.now());

        // PayProp sync fields
        newCustomer.setPayPropSynced(true);
        newCustomer.setPayPropLastSync(LocalDateTime.now());
        newCustomer.setPayPropSyncStatus(site.easy.to.build.crm.entity.SyncStatus.synced);

        // Save and return
        Customer saved = customerRepository.save(newCustomer);
        log.info("✅ Created placeholder customer ID {} for PayProp tenant {}",
                 saved.getCustomerId(), tenantPayPropId);

        return saved;
    }

    /**
     * Fetch additional tenant info from payprop_export_tenants table
     */
    private Map<String, String> fetchTenantInfo(String tenantPayPropId) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = """
                SELECT first_name, last_name, email_address, mobile_number
                FROM payprop_export_tenants
                WHERE payprop_id = ?
                LIMIT 1
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, tenantPayPropId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Map<String, String> info = new HashMap<>();
                        info.put("first_name", rs.getString("first_name"));
                        info.put("last_name", rs.getString("last_name"));
                        info.put("email", rs.getString("email_address"));
                        info.put("phone", rs.getString("mobile_number"));
                        return info;
                    }
                }
            }
        } catch (SQLException e) {
            log.warn("Could not fetch tenant info for {}: {}", tenantPayPropId, e.getMessage());
        }

        return null;
    }

    /**
     * Parse frequency string to enum
     */
    private InvoiceFrequency parseFrequency(String frequency) {
        if (frequency == null) {
            return InvoiceFrequency.monthly; // Default to monthly
        }

        return switch (frequency.toUpperCase()) {
            case "M", "MONTHLY" -> InvoiceFrequency.monthly;
            case "W", "WEEKLY" -> InvoiceFrequency.weekly;
            case "D", "DAILY" -> InvoiceFrequency.daily;
            case "Q", "QUARTERLY" -> InvoiceFrequency.quarterly;
            case "Y", "YEARLY", "ANNUAL" -> InvoiceFrequency.yearly;
            case "O", "ONE_TIME", "ONCE" -> InvoiceFrequency.one_time;
            default -> InvoiceFrequency.monthly;
        };
    }

    /**
     * Parse frequency string to numeric months.
     * Handles arbitrary patterns like "6M", "7M", "18M" as well as standard codes.
     * Returns the billing cycle length in months.
     *
     * @param frequency PayProp frequency code (e.g., "M", "6M", "Q", "Y")
     * @return Number of months in the billing cycle (default 1 for monthly)
     */
    private int parseFrequencyMonths(String frequency) {
        if (frequency == null || frequency.trim().isEmpty()) {
            return 1; // Default to monthly
        }

        String upper = frequency.toUpperCase().trim();

        // Handle numeric patterns: "6M", "7M", "2M", "18M", etc.
        if (upper.matches("\\d+M")) {
            try {
                return Integer.parseInt(upper.replace("M", ""));
            } catch (NumberFormatException e) {
                log.warn("Failed to parse numeric frequency '{}', defaulting to monthly", frequency);
                return 1;
            }
        }

        // Handle standard frequency codes
        return switch (upper) {
            case "M", "MONTHLY" -> 1;
            case "W", "WEEKLY" -> 0;      // Weekly - special handling needed
            case "2W", "BI_WEEKLY", "BIWEEKLY" -> 0;  // Bi-weekly
            case "4W", "FOUR_WEEKLY" -> 0; // Four-weekly
            case "D", "DAILY" -> 0;        // Daily - special handling needed
            case "Q", "QUARTERLY" -> 3;
            case "2M", "BI_MONTHLY", "BIMONTHLY" -> 2;
            case "Y", "YEARLY", "ANNUAL", "A" -> 12;
            case "O", "ONE_TIME", "ONCE" -> 0;  // One-time - no recurring cycle
            default -> {
                log.warn("Unknown frequency code '{}', defaulting to monthly", frequency);
                yield 1;
            }
        };
    }

    /**
     * Get statistics about current lease situation
     */
    @Transactional(readOnly = true)
    public LeaseStatistics getLeaseStatistics() {
        LeaseStatistics stats = new LeaseStatistics();

        // Count total leases (invoices)
        stats.totalLeases = invoiceRepository.count();

        // Count active leases
        stats.activeLeases = invoiceRepository.countActiveInvoices();

        // Count properties with multiple active leases
        List<Invoice> activeInvoices = invoiceRepository.findCurrentlyActiveInvoices(LocalDate.now());
        Map<Long, Integer> propertyCounts = new HashMap<>();
        for (Invoice invoice : activeInvoices) {
            Long propId = invoice.getProperty().getId();
            propertyCounts.put(propId, propertyCounts.getOrDefault(propId, 0) + 1);
        }

        stats.multiTenantProperties = (int) propertyCounts.values().stream()
            .filter(count -> count > 1)
            .count();

        // Calculate total monthly rent from active leases
        stats.totalMonthlyRent = activeInvoices.stream()
            .map(Invoice::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return stats;
    }

    // ===== DATA CLASSES =====

    private static class PayPropInvoiceData {
        String paypropId;
        String propertyPayPropId;
        String tenantPayPropId;
        String propertyName;
        String tenantDisplayName;
        BigDecimal grossAmount;
        LocalDate fromDate;
        LocalDate toDate;
        String frequency;
        String frequencyCode;
        Integer paymentDay;
        String description;
        String categoryPayPropId;
        String categoryName;
        boolean vat;
        BigDecimal vatAmount;
        boolean isActiveInstruction;
        String accountType;
        boolean debitOrder;
    }

    public static class ImportResult {
        public boolean success;
        public LocalDateTime startTime;
        public LocalDateTime endTime;
        public int totalPayPropInvoices;
        public int successfulImports;
        public int failedImports;
        public int skippedInvoices;
        public int activeLeases;
        public String errorMessage;
        public List<String> errors = new ArrayList<>();

        public long getDurationSeconds() {
            if (startTime != null && endTime != null) {
                return java.time.Duration.between(startTime, endTime).getSeconds();
            }
            return 0;
        }
    }

    public static class LeaseStatistics {
        public long totalLeases;
        public long activeLeases;
        public int multiTenantProperties;
        public BigDecimal totalMonthlyRent;
    }
}

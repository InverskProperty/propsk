package site.easy.to.build.crm.service.payprop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.Invoice;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.Invoice.InvoiceFrequency;
import site.easy.to.build.crm.entity.Invoice.SyncStatus;
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
        // Check if already imported
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

        // Save
        Invoice savedInvoice = invoiceRepository.save(invoice);

        log.info("✅ Imported invoice {} as lease: {} at {} (£{}/{})",
                data.paypropId, data.tenantDisplayName, data.propertyName,
                data.grossAmount, data.frequency);

        return savedInvoice;
    }

    /**
     * Find customer by PayProp tenant ID
     */
    private Customer findOrResolveCustomer(String tenantPayPropId, String displayName) {
        if (tenantPayPropId == null) {
            return null;
        }

        // Try to find by payprop_entity_id in customer table
        return customerRepository.findByPayPropEntityId(tenantPayPropId);

        // TODO: If not found, could create placeholder customer or try to match by name
    }

    /**
     * Parse frequency string to enum
     */
    private InvoiceFrequency parseFrequency(String frequency) {
        if (frequency == null) {
            return InvoiceFrequency.M; // Default to monthly
        }

        return switch (frequency.toUpperCase()) {
            case "M", "MONTHLY" -> InvoiceFrequency.M;
            case "W", "WEEKLY" -> InvoiceFrequency.W;
            case "Q", "QUARTERLY" -> InvoiceFrequency.Q;
            case "Y", "YEARLY", "ANNUAL" -> InvoiceFrequency.Y;
            case "O", "ONE_TIME", "ONCE" -> InvoiceFrequency.O;
            default -> InvoiceFrequency.M;
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

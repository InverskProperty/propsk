package site.easy.to.build.crm.service.payprop.business;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import site.easy.to.build.crm.entity.Invoice;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.repository.InvoiceRepository;
import site.easy.to.build.crm.repository.PropertyRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lease-Based Rent Calculation Service
 *
 * REPLACES property-level rent calculation with lease-level calculation
 *
 * KEY CHANGE:
 * OLD: Property.monthlyPayment (single value per property)
 * NEW: Sum of Invoice.amount for all active leases on that property
 *
 * This solves the "Apartment 40" problem:
 * - Jason Barclay: £900/month (one lease)
 * - Michel Mabondo: £1,040/month (another lease)
 * - Neha Minocha: £900/month (third lease)
 * = £2,840/month total for the property (not a single value!)
 */
@Service
@Transactional
public class LeaseBasedRentCalculationService {

    private static final Logger log = LoggerFactory.getLogger(LeaseBasedRentCalculationService.class);

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    /**
     * Get total rent for a property (sum of all active leases)
     * This is the CORRECT way to calculate property income in a lease-based system
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalRentForProperty(Long propertyId) {
        Property property = propertyRepository.findById(propertyId).orElse(null);
        if (property == null) {
            return BigDecimal.ZERO;
        }

        return getTotalRentForProperty(property);
    }

    /**
     * Get total rent for a property (sum of all active leases)
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalRentForProperty(Property property) {
        LocalDate today = LocalDate.now();

        // Find all active leases (invoices) for this property
        List<Invoice> activeLeases = invoiceRepository.findActiveInvoicesForProperty(property, today);

        // Sum the rent amounts
        BigDecimal totalRent = activeLeases.stream()
            .map(Invoice::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return totalRent;
    }

    /**
     * Get rent for a specific lease (invoice)
     */
    @Transactional(readOnly = true)
    public BigDecimal getRentForLease(Long invoiceId) {
        return invoiceRepository.findById(invoiceId)
            .map(Invoice::getAmount)
            .orElse(BigDecimal.ZERO);
    }

    /**
     * Get all active leases for a property
     * Returns list of lease details including tenant name and rent amount
     */
    @Transactional(readOnly = true)
    public List<LeaseDetail> getActiveLeasesForProperty(Long propertyId) {
        Property property = propertyRepository.findById(propertyId).orElse(null);
        if (property == null) {
            return new ArrayList<>();
        }

        LocalDate today = LocalDate.now();
        List<Invoice> activeLeases = invoiceRepository.findActiveInvoicesForProperty(property, today);

        List<LeaseDetail> details = new ArrayList<>();
        for (Invoice lease : activeLeases) {
            LeaseDetail detail = new LeaseDetail();
            detail.invoiceId = lease.getId();
            detail.propertyId = property.getId();
            detail.propertyName = property.getPropertyName();
            detail.tenantId = lease.getCustomer().getCustomerId();
            detail.tenantName = lease.getCustomer().getName();
            detail.rentAmount = lease.getAmount();
            detail.startDate = lease.getStartDate();
            detail.endDate = lease.getEndDate();
            detail.frequency = lease.getFrequency().getDisplayName();
            detail.isActive = lease.isCurrentlyActive();

            details.add(detail);
        }

        return details;
    }

    /**
     * Calculate expected monthly income for all properties
     * Returns total expected income based on active leases
     */
    @Transactional(readOnly = true)
    public PropertyIncomeReport calculateExpectedMonthlyIncome() {
        PropertyIncomeReport report = new PropertyIncomeReport();

        List<Property> allProperties = propertyRepository.findAll();

        BigDecimal totalIncome = BigDecimal.ZERO;
        int propertiesWithLeases = 0;
        int totalActiveLeases = 0;

        Map<Long, PropertyIncomeDetail> propertyDetails = new HashMap<>();

        for (Property property : allProperties) {
            List<LeaseDetail> leases = getActiveLeasesForProperty(property.getId());

            if (!leases.isEmpty()) {
                propertiesWithLeases++;
                totalActiveLeases += leases.size();

                BigDecimal propertyIncome = leases.stream()
                    .map(lease -> lease.rentAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

                totalIncome = totalIncome.add(propertyIncome);

                PropertyIncomeDetail detail = new PropertyIncomeDetail();
                detail.propertyId = property.getId();
                detail.propertyName = property.getPropertyName();
                detail.numberOfLeases = leases.size();
                detail.totalMonthlyRent = propertyIncome;
                detail.leases = leases;

                propertyDetails.put(property.getId(), detail);
            }
        }

        report.totalProperties = allProperties.size();
        report.propertiesWithActiveLeases = propertiesWithLeases;
        report.totalActiveLeases = totalActiveLeases;
        report.totalMonthlyIncome = totalIncome;
        report.propertyDetails = propertyDetails;

        return report;
    }

    /**
     * Find properties with multiple active leases (multi-tenant)
     */
    @Transactional(readOnly = true)
    public List<PropertyIncomeDetail> findMultiTenantProperties() {
        PropertyIncomeReport report = calculateExpectedMonthlyIncome();

        return report.propertyDetails.values().stream()
            .filter(detail -> detail.numberOfLeases > 1)
            .toList();
    }

    /**
     * Compare lease-based income vs property field
     * Shows discrepancies between Property.monthlyPayment and actual lease total
     */
    @Transactional(readOnly = true)
    public List<IncomeDiscrepancy> findIncomeDiscrepancies() {
        List<IncomeDiscrepancy> discrepancies = new ArrayList<>();

        List<Property> properties = propertyRepository.findAll();

        for (Property property : properties) {
            BigDecimal propertyFieldValue = property.getMonthlyPayment() != null
                ? property.getMonthlyPayment()
                : BigDecimal.ZERO;

            BigDecimal leaseBasedValue = getTotalRentForProperty(property);

            // If there's a difference, it's a discrepancy
            if (propertyFieldValue.compareTo(leaseBasedValue) != 0) {
                IncomeDiscrepancy discrepancy = new IncomeDiscrepancy();
                discrepancy.propertyId = property.getId();
                discrepancy.propertyName = property.getPropertyName();
                discrepancy.propertyFieldRent = propertyFieldValue;
                discrepancy.leaseBasedRent = leaseBasedValue;
                discrepancy.difference = leaseBasedValue.subtract(propertyFieldValue);
                discrepancy.activeLeases = getActiveLeasesForProperty(property.getId());

                discrepancies.add(discrepancy);
            }
        }

        return discrepancies;
    }

    // ===== DATA CLASSES =====

    public static class LeaseDetail {
        public Long invoiceId;
        public Long propertyId;
        public String propertyName;
        public Long tenantId;
        public String tenantName;
        public BigDecimal rentAmount;
        public LocalDate startDate;
        public LocalDate endDate;
        public String frequency;
        public boolean isActive;
    }

    public static class PropertyIncomeDetail {
        public Long propertyId;
        public String propertyName;
        public int numberOfLeases;
        public BigDecimal totalMonthlyRent;
        public List<LeaseDetail> leases;
    }

    public static class PropertyIncomeReport {
        public int totalProperties;
        public int propertiesWithActiveLeases;
        public int totalActiveLeases;
        public BigDecimal totalMonthlyIncome;
        public Map<Long, PropertyIncomeDetail> propertyDetails;
    }

    public static class IncomeDiscrepancy {
        public Long propertyId;
        public String propertyName;
        public BigDecimal propertyFieldRent;
        public BigDecimal leaseBasedRent;
        public BigDecimal difference;
        public List<LeaseDetail> activeLeases;
    }
}

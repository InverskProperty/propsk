package site.easy.to.build.crm.service.lease;

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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lease History Service - Query and Report on Historical Leases
 *
 * This service handles queries for PAST LEASES (where end_date is in the past).
 *
 * Use cases:
 * - View all tenants who previously lived at a property
 * - Calculate historical income for specific periods
 * - Generate tenancy history reports
 * - Track lease turnover
 */
@Service
@Transactional
public class LeaseHistoryService {

    private static final Logger log = LoggerFactory.getLogger(LeaseHistoryService.class);

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    /**
     * Get all historical leases for a property (leases that have ended)
     * Excludes currently active leases
     *
     * @param propertyId Property ID
     * @return List of ended leases, sorted by most recent end date first
     */
    @Transactional(readOnly = true)
    public List<Invoice> getHistoricalLeasesForProperty(Long propertyId) {
        Property property = propertyRepository.findById(propertyId).orElse(null);
        if (property == null) {
            log.warn("Property {} not found", propertyId);
            return new ArrayList<>();
        }

        List<Invoice> allLeases = invoiceRepository.findByProperty(property);

        LocalDate today = LocalDate.now();

        return allLeases.stream()
                .filter(lease -> !lease.isCurrentlyActive())
                .filter(lease -> lease.getEndDate() != null && lease.getEndDate().isBefore(today))
                .sorted(Comparator.comparing(Invoice::getEndDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /**
     * Get complete lease history for a property (active + historical)
     * Returns detailed information about each lease
     *
     * @param propertyId Property ID
     * @return List of lease history details, sorted by most recent start date first
     */
    @Transactional(readOnly = true)
    public List<LeaseHistoryDetail> getLeaseHistoryWithDetails(Long propertyId) {
        Property property = propertyRepository.findById(propertyId).orElse(null);
        if (property == null) {
            log.warn("Property {} not found", propertyId);
            return new ArrayList<>();
        }

        List<Invoice> allLeases = invoiceRepository.findByProperty(property);

        return allLeases.stream()
                .map(lease -> {
                    LeaseHistoryDetail detail = new LeaseHistoryDetail();
                    detail.invoiceId = lease.getId();
                    detail.tenantId = lease.getCustomer().getCustomerId();
                    detail.tenantName = lease.getCustomer().getName();
                    detail.rentAmount = lease.getAmount();
                    detail.startDate = lease.getStartDate();
                    detail.endDate = lease.getEndDate();
                    detail.status = lease.isCurrentlyActive() ? "Active" : "Ended";
                    detail.isActive = lease.isCurrentlyActive();

                    LocalDate endDateForCalc = lease.getEndDate() != null
                            ? lease.getEndDate()
                            : LocalDate.now();
                    detail.durationMonths = calculateDurationMonths(lease.getStartDate(), endDateForCalc);

                    return detail;
                })
                .sorted(Comparator.comparing((LeaseHistoryDetail d) -> d.startDate).reversed())
                .toList();
    }

    /**
     * Calculate total income for a property during a specific period
     * Includes all leases that were active at any point during the period
     *
     * NOTE: This is a simplified calculation. For precise income, you should:
     * - Pro-rate rent for partial months
     * - Use actual payment data from payments table
     * - Account for frequency (weekly, monthly, quarterly, etc.)
     *
     * @param propertyId Property ID
     * @param periodStart Period start date
     * @param periodEnd Period end date
     * @return Expected total income for the period
     */
    @Transactional(readOnly = true)
    public PeriodIncomeReport calculateIncomeForPeriod(Long propertyId, LocalDate periodStart, LocalDate periodEnd) {
        Property property = propertyRepository.findById(propertyId).orElse(null);
        if (property == null) {
            log.warn("Property {} not found", propertyId);
            return null;
        }

        List<Invoice> allLeases = invoiceRepository.findByProperty(property);

        // Find leases that were active during the period
        List<Invoice> activeDuringPeriod = allLeases.stream()
                .filter(lease -> leaseActiveInPeriod(lease, periodStart, periodEnd))
                .toList();

        PeriodIncomeReport report = new PeriodIncomeReport();
        report.propertyId = property.getId();
        report.propertyName = property.getPropertyName();
        report.periodStart = periodStart;
        report.periodEnd = periodEnd;
        report.leasesActiveDuringPeriod = activeDuringPeriod.size();
        report.breakdown = new ArrayList<>();

        BigDecimal totalIncome = BigDecimal.ZERO;

        for (Invoice lease : activeDuringPeriod) {
            LeaseIncomeContribution contribution = new LeaseIncomeContribution();
            contribution.invoiceId = lease.getId();
            contribution.tenantName = lease.getCustomer().getName();
            contribution.rentAmount = lease.getAmount();

            // Calculate how many months this lease was active during the period
            LocalDate leaseStart = lease.getStartDate().isBefore(periodStart) ? periodStart : lease.getStartDate();
            LocalDate leaseEnd = (lease.getEndDate() == null || lease.getEndDate().isAfter(periodEnd))
                    ? periodEnd
                    : lease.getEndDate();

            long monthsActive = calculateDurationMonths(leaseStart, leaseEnd);
            contribution.monthsActive = monthsActive;

            // Simplified income calculation (assumes monthly rent)
            BigDecimal contribution_amount = lease.getAmount().multiply(BigDecimal.valueOf(monthsActive));
            contribution.totalContribution = contribution_amount;

            totalIncome = totalIncome.add(contribution_amount);
            report.breakdown.add(contribution);
        }

        report.totalIncome = totalIncome;

        return report;
    }

    /**
     * Get full property history including current and past leases
     *
     * @param propertyId Property ID
     * @return Complete lease history report
     */
    @Transactional(readOnly = true)
    public PropertyLeaseHistoryReport getPropertyLeaseHistory(Long propertyId) {
        Property property = propertyRepository.findById(propertyId).orElse(null);
        if (property == null) {
            return null;
        }

        PropertyLeaseHistoryReport report = new PropertyLeaseHistoryReport();
        report.propertyId = property.getId();
        report.propertyName = property.getPropertyName();

        List<Invoice> allLeases = invoiceRepository.findByProperty(property);

        LocalDate today = LocalDate.now();

        List<Invoice> currentLeases = allLeases.stream()
                .filter(Invoice::isCurrentlyActive)
                .toList();

        List<Invoice> historicalLeases = allLeases.stream()
                .filter(lease -> !lease.isCurrentlyActive())
                .filter(lease -> lease.getEndDate() != null && lease.getEndDate().isBefore(today))
                .toList();

        report.currentLeaseCount = currentLeases.size();
        report.historicalLeaseCount = historicalLeases.size();
        report.totalLeaseCount = allLeases.size();

        // Convert to details
        report.currentLeases = currentLeases.stream()
                .map(this::convertToDetail)
                .sorted(Comparator.comparing((LeaseHistoryDetail d) -> d.startDate).reversed())
                .toList();

        report.historicalLeases = historicalLeases.stream()
                .map(this::convertToDetail)
                .sorted(Comparator.comparing((LeaseHistoryDetail d) -> d.endDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        return report;
    }

    /**
     * Find leases that ended within a specific date range
     *
     * @param propertyId Property ID
     * @param startDate Range start
     * @param endDate Range end
     * @return List of leases that ended in the date range
     */
    @Transactional(readOnly = true)
    public List<LeaseHistoryDetail> findLeasesEndedInRange(Long propertyId, LocalDate startDate, LocalDate endDate) {
        Property property = propertyRepository.findById(propertyId).orElse(null);
        if (property == null) {
            return new ArrayList<>();
        }

        List<Invoice> allLeases = invoiceRepository.findByProperty(property);

        return allLeases.stream()
                .filter(lease -> lease.getEndDate() != null)
                .filter(lease -> !lease.getEndDate().isBefore(startDate) && !lease.getEndDate().isAfter(endDate))
                .map(this::convertToDetail)
                .sorted(Comparator.comparing((LeaseHistoryDetail d) -> d.endDate).reversed())
                .toList();
    }

    // ===== HELPER METHODS =====

    /**
     * Check if a lease was active at any point during the specified period
     */
    private boolean leaseActiveInPeriod(Invoice lease, LocalDate periodStart, LocalDate periodEnd) {
        // Lease started before period ended
        boolean startedBeforeEnd = !lease.getStartDate().isAfter(periodEnd);

        // Lease ended after period started (or still ongoing)
        boolean endedAfterStart = lease.getEndDate() == null || !lease.getEndDate().isBefore(periodStart);

        return startedBeforeEnd && endedAfterStart;
    }

    /**
     * Calculate duration in months between two dates
     */
    private long calculateDurationMonths(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return 0;
        }
        return ChronoUnit.MONTHS.between(start, end);
    }

    /**
     * Convert Invoice to LeaseHistoryDetail
     */
    private LeaseHistoryDetail convertToDetail(Invoice lease) {
        LeaseHistoryDetail detail = new LeaseHistoryDetail();
        detail.invoiceId = lease.getId();
        detail.tenantId = lease.getCustomer().getCustomerId();
        detail.tenantName = lease.getCustomer().getName();
        detail.rentAmount = lease.getAmount();
        detail.startDate = lease.getStartDate();
        detail.endDate = lease.getEndDate();
        detail.status = lease.isCurrentlyActive() ? "Active" : "Ended";
        detail.isActive = lease.isCurrentlyActive();

        LocalDate endDateForCalc = lease.getEndDate() != null ? lease.getEndDate() : LocalDate.now();
        detail.durationMonths = calculateDurationMonths(lease.getStartDate(), endDateForCalc);

        return detail;
    }

    // ===== DATA CLASSES =====

    public static class LeaseHistoryDetail {
        public Long invoiceId;
        public Long tenantId;
        public String tenantName;
        public BigDecimal rentAmount;
        public LocalDate startDate;
        public LocalDate endDate;
        public String status;
        public boolean isActive;
        public long durationMonths;

        // Getters for sorting
        public LocalDate getStartDate() { return startDate; }
        public LocalDate getEndDate() { return endDate; }
    }

    public static class PeriodIncomeReport {
        public Long propertyId;
        public String propertyName;
        public LocalDate periodStart;
        public LocalDate periodEnd;
        public BigDecimal totalIncome;
        public int leasesActiveDuringPeriod;
        public List<LeaseIncomeContribution> breakdown;
    }

    public static class LeaseIncomeContribution {
        public Long invoiceId;
        public String tenantName;
        public BigDecimal rentAmount;
        public long monthsActive;
        public BigDecimal totalContribution;
    }

    public static class PropertyLeaseHistoryReport {
        public Long propertyId;
        public String propertyName;
        public int totalLeaseCount;
        public int currentLeaseCount;
        public int historicalLeaseCount;
        public List<LeaseHistoryDetail> currentLeases;
        public List<LeaseHistoryDetail> historicalLeases;
    }
}

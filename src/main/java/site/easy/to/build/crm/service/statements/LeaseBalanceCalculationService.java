package site.easy.to.build.crm.service.statements;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.Invoice;
import site.easy.to.build.crm.entity.Payment;
import site.easy.to.build.crm.repository.PaymentRepository;
import site.easy.to.build.crm.repository.FinancialTransactionRepository;
import site.easy.to.build.crm.repository.HistoricalTransactionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Service for calculating lease-based financial data
 * Handles rent due calculations, payment allocations, and tenant balance tracking
 */
@Service
public class LeaseBalanceCalculationService {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private HistoricalTransactionRepository historicalTransactionRepository;

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    /**
     * Calculate rent due for a lease within a specific date range
     * Handles prorating for partial periods
     *
     * @param lease The lease/invoice
     * @param periodStart Statement period start
     * @param periodEnd Statement period end
     * @return Rent due amount for the period
     */
    public BigDecimal calculateRentDue(Invoice lease, LocalDate periodStart, LocalDate periodEnd) {
        if (lease == null || periodStart == null || periodEnd == null) {
            return BigDecimal.ZERO;
        }

        // Determine the actual overlap between lease period and statement period
        LocalDate leaseStart = lease.getStartDate();
        LocalDate leaseEnd = lease.getEndDate(); // May be null for ongoing leases

        // If lease hasn't started yet in this period, no rent due
        if (leaseStart.isAfter(periodEnd)) {
            return BigDecimal.ZERO;
        }

        // If lease has ended before this period, no rent due
        if (leaseEnd != null && leaseEnd.isBefore(periodStart)) {
            return BigDecimal.ZERO;
        }

        // Calculate actual overlap
        LocalDate effectiveStart = leaseStart.isBefore(periodStart) ? periodStart : leaseStart;
        LocalDate effectiveEnd = periodEnd;
        if (leaseEnd != null && leaseEnd.isBefore(periodEnd)) {
            effectiveEnd = leaseEnd;
        }

        // Calculate based on frequency
        switch (lease.getFrequency()) {
            case monthly:
                return calculateMonthlyRentDue(lease.getAmount(), effectiveStart, effectiveEnd);
            case weekly:
                return calculateWeeklyRentDue(lease.getAmount(), effectiveStart, effectiveEnd);
            case quarterly:
                return calculateQuarterlyRentDue(lease.getAmount(), effectiveStart, effectiveEnd);
            case yearly:
                return calculateYearlyRentDue(lease.getAmount(), effectiveStart, effectiveEnd);
            case once_off:
                // If lease start is within period, charge full amount
                if (!leaseStart.isBefore(periodStart) && !leaseStart.isAfter(periodEnd)) {
                    return lease.getAmount();
                }
                return BigDecimal.ZERO;
            default:
                return BigDecimal.ZERO;
        }
    }

    /**
     * Calculate monthly rent due (with prorating for partial months)
     */
    private BigDecimal calculateMonthlyRentDue(BigDecimal monthlyAmount, LocalDate start, LocalDate end) {
        // Count number of months
        long days = ChronoUnit.DAYS.between(start, end) + 1; // Inclusive

        // If less than a month, prorate
        if (days < 28) {
            // Prorate based on days (assume 30 days per month for simplicity)
            BigDecimal dailyRate = monthlyAmount.divide(new BigDecimal("30"), 6, RoundingMode.HALF_UP);
            return dailyRate.multiply(new BigDecimal(days)).setScale(2, RoundingMode.HALF_UP);
        }

        // Calculate number of full months
        long months = ChronoUnit.MONTHS.between(start, end);

        // If the period is partial (doesn't end on month boundary), add one more month
        if (end.getDayOfMonth() >= start.getDayOfMonth() || months == 0) {
            months = months + 1;
        }

        return monthlyAmount.multiply(new BigDecimal(months));
    }

    /**
     * Calculate weekly rent due
     */
    private BigDecimal calculateWeeklyRentDue(BigDecimal weeklyAmount, LocalDate start, LocalDate end) {
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        long weeks = days / 7;
        if (days % 7 > 0) {
            weeks++; // Include partial week
        }
        return weeklyAmount.multiply(new BigDecimal(weeks));
    }

    /**
     * Calculate quarterly rent due
     */
    private BigDecimal calculateQuarterlyRentDue(BigDecimal quarterlyAmount, LocalDate start, LocalDate end) {
        long months = ChronoUnit.MONTHS.between(start, end);
        long quarters = months / 3;
        if (months % 3 > 0) {
            quarters++; // Include partial quarter
        }
        return quarterlyAmount.multiply(new BigDecimal(Math.max(1, quarters)));
    }

    /**
     * Calculate yearly rent due
     */
    private BigDecimal calculateYearlyRentDue(BigDecimal yearlyAmount, LocalDate start, LocalDate end) {
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days >= 365) {
            return yearlyAmount;
        }
        // Prorate for partial year
        BigDecimal dailyRate = yearlyAmount.divide(new BigDecimal("365"), 6, RoundingMode.HALF_UP);
        return dailyRate.multiply(new BigDecimal(days)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate total payments received for a lease within a date range
     *
     * @param leaseId The lease ID
     * @param periodStart Period start date
     * @param periodEnd Period end date
     * @return Total payments received
     */
    public BigDecimal calculatePaymentsReceived(Long leaseId, LocalDate periodStart, LocalDate periodEnd) {
        if (leaseId == null) {
            return BigDecimal.ZERO;
        }

        // Get payments from Payment table
        List<Payment> payments = paymentRepository.findByInvoiceIdAndPaymentDateBetween(
            leaseId, periodStart, periodEnd);

        BigDecimal total = payments.stream()
            .map(Payment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Also check HistoricalTransaction for historical payments
        BigDecimal historicalTotal = historicalTransactionRepository
            .findPaymentsByInvoiceIdAndDateRange(leaseId, periodStart, periodEnd)
            .stream()
            .map(tx -> tx.getAmount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return total.add(historicalTotal);
    }

    /**
     * Calculate the most recent payment date for a lease within a period
     */
    public LocalDate getLastPaymentDate(Long leaseId, LocalDate periodStart, LocalDate periodEnd) {
        if (leaseId == null) {
            return null;
        }

        List<Payment> payments = paymentRepository.findByInvoiceIdAndPaymentDateBetween(
            leaseId, periodStart, periodEnd);

        return payments.stream()
            .map(Payment::getPaymentDate)
            .max(LocalDate::compareTo)
            .orElse(null);
    }

    /**
     * Calculate cumulative tenant balance from lease start to a specific date
     * Balance = Total Rent Due - Total Payments Received
     * Positive = Tenant owes money (arrears)
     * Negative = Tenant has credit
     *
     * @param lease The lease/invoice
     * @param asOfDate Calculate balance as of this date
     * @return Cumulative balance
     */
    public BigDecimal calculateTenantBalance(Invoice lease, LocalDate asOfDate) {
        if (lease == null || asOfDate == null) {
            return BigDecimal.ZERO;
        }

        LocalDate leaseStart = lease.getStartDate();

        // If lease hasn't started yet, balance is zero
        if (leaseStart.isAfter(asOfDate)) {
            return BigDecimal.ZERO;
        }

        // Calculate total rent due from lease start to asOfDate
        BigDecimal totalRentDue = calculateRentDue(lease, leaseStart, asOfDate);

        // Calculate total payments received from lease start to asOfDate
        BigDecimal totalPayments = calculatePaymentsReceived(lease.getId(), leaseStart, asOfDate);

        // Balance = Rent Due - Payments Received
        return totalRentDue.subtract(totalPayments);
    }

    /**
     * Calculate management fee based on commission rate
     *
     * @param amount Base amount (usually rent received)
     * @param percentage Fee percentage (e.g., 15 for 15%)
     * @return Fee amount
     */
    public BigDecimal calculateManagementFee(BigDecimal amount, BigDecimal percentage) {
        if (amount == null || percentage == null) {
            return BigDecimal.ZERO;
        }

        return amount.multiply(percentage)
            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate total expenses for a lease within a date range
     * Includes both FinancialTransaction and HistoricalTransaction expenses
     */
    public BigDecimal calculateTotalExpenses(Long leaseId, LocalDate periodStart, LocalDate periodEnd) {
        if (leaseId == null) {
            return BigDecimal.ZERO;
        }

        // Get expenses from FinancialTransaction
        BigDecimal financialExpenses = financialTransactionRepository
            .findExpensesByInvoiceIdAndDateRange(leaseId, periodStart, periodEnd)
            .stream()
            .map(tx -> tx.getAmount().abs()) // Expenses are typically negative
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Get expenses from HistoricalTransaction
        BigDecimal historicalExpenses = historicalTransactionRepository
            .findExpensesByInvoiceIdAndDateRange(leaseId, periodStart, periodEnd)
            .stream()
            .map(tx -> tx.getAmount().abs())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return financialExpenses.add(historicalExpenses);
    }
}

package site.easy.to.build.crm.service.invoice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import site.easy.to.build.crm.entity.Invoice;
import site.easy.to.build.crm.repository.InvoiceRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Rent Calculation Service
 *
 * Replicates complex Excel rent due calculations for statements
 * Handles:
 * - Monthly rent due dates
 * - Prorated rent for partial months
 * - Tenancy start/end date handling
 * - Statement period alignment
 */
@Service
public class RentCalculationService {

    private static final Logger log = LoggerFactory.getLogger(RentCalculationService.class);

    @Autowired
    private InvoiceRepository invoiceRepository;

    /**
     * Calculate rent due for a property during a statement period
     *
     * Matches Excel logic:
     * - If tenancy ended before period: £0
     * - If tenancy ongoing: Full monthly rent
     * - If tenancy started mid-period: Prorated by days
     * - If tenancy ended mid-period: Prorated by days
     *
     * @param invoice The lease/invoice instruction
     * @param statementPeriodStart Statement period start (e.g., 1st of month)
     * @param statementPeriodEnd Statement period end (e.g., last day of month)
     * @return Rent due amount for this period
     */
    public BigDecimal calculateRentDueForPeriod(Invoice invoice, LocalDate statementPeriodStart, LocalDate statementPeriodEnd) {

        // Get lease dates
        LocalDate tenancyStart = invoice.getStartDate();
        LocalDate tenancyEnd = invoice.getEndDate(); // Can be null for ongoing tenancies
        BigDecimal monthlyRent = invoice.getAmount();
        Integer rentDueDay = invoice.getPaymentDay(); // Day of month rent is due

        // Validation
        if (tenancyStart == null || monthlyRent == null) {
            log.warn("Cannot calculate rent: missing tenancy start or rent amount");
            return BigDecimal.ZERO;
        }

        // Check if tenancy is active during statement period
        if (!isTenancyActiveDuringPeriod(tenancyStart, tenancyEnd, statementPeriodStart, statementPeriodEnd)) {
            log.debug("Tenancy not active during period {} to {}", statementPeriodStart, statementPeriodEnd);
            return BigDecimal.ZERO;
        }

        // Calculate the billing cycle for this period
        // The billing cycle starts on the rent due day
        LocalDate billingCycleStart = calculateBillingCycleStart(rentDueDay, statementPeriodStart, tenancyStart);
        LocalDate billingCycleEnd = calculateBillingCycleEnd(rentDueDay, statementPeriodEnd);

        // Handle prorating for partial months
        return calculateProratedRent(monthlyRent, billingCycleStart, billingCycleEnd, tenancyStart, tenancyEnd);
    }

    /**
     * Check if tenancy was active at any point during the statement period
     */
    private boolean isTenancyActiveDuringPeriod(LocalDate tenancyStart, LocalDate tenancyEnd,
                                                 LocalDate periodStart, LocalDate periodEnd) {
        // Tenancy must have started before or during the period
        if (tenancyStart.isAfter(periodEnd)) {
            return false;
        }

        // If tenancy has ended, it must have ended during or after the period
        if (tenancyEnd != null && tenancyEnd.isBefore(periodStart)) {
            return false;
        }

        return true;
    }

    /**
     * Calculate billing cycle start date based on rent due day
     *
     * Examples:
     * - Rent due on 5th, period starts 1st → billing starts 5th
     * - Rent due on 5th, tenancy started 10th → billing starts 10th (can't charge before tenancy)
     */
    private LocalDate calculateBillingCycleStart(Integer rentDueDay, LocalDate periodStart, LocalDate tenancyStart) {
        // If no rent due day specified, use tenancy start date's day
        int dueDay = (rentDueDay != null && rentDueDay > 0) ? rentDueDay : tenancyStart.getDayOfMonth();

        // Calculate the rent due date within the period
        LocalDate rentDueDateInPeriod;
        try {
            rentDueDateInPeriod = LocalDate.of(periodStart.getYear(), periodStart.getMonth(), dueDay);
        } catch (Exception e) {
            // Handle invalid day (e.g., 31st in February) - use last day of month
            YearMonth ym = YearMonth.of(periodStart.getYear(), periodStart.getMonth());
            rentDueDateInPeriod = ym.atEndOfMonth();
        }

        // If rent due date is before period start, move to previous month
        if (rentDueDateInPeriod.isBefore(periodStart)) {
            YearMonth previousMonth = YearMonth.from(rentDueDateInPeriod).minusMonths(1);
            try {
                rentDueDateInPeriod = previousMonth.atDay(dueDay);
            } catch (Exception e) {
                rentDueDateInPeriod = previousMonth.atEndOfMonth();
            }
        }

        // Billing cannot start before tenancy started
        if (tenancyStart.isAfter(rentDueDateInPeriod)) {
            return tenancyStart;
        }

        return rentDueDateInPeriod;
    }

    /**
     * Calculate billing cycle end date
     */
    private LocalDate calculateBillingCycleEnd(Integer rentDueDay, LocalDate periodEnd) {
        // If no rent due day, use end of period
        if (rentDueDay == null || rentDueDay <= 0) {
            return periodEnd;
        }

        // Calculate next rent due date (one day before next billing period)
        int dueDay = rentDueDay;
        LocalDate nextRentDueDate;

        try {
            YearMonth nextMonth = YearMonth.from(periodEnd).plusMonths(1);
            nextRentDueDate = nextMonth.atDay(dueDay);
        } catch (Exception e) {
            YearMonth nextMonth = YearMonth.from(periodEnd).plusMonths(1);
            nextRentDueDate = nextMonth.atEndOfMonth();
        }

        // Billing cycle ends one day before next rent due date
        LocalDate billingEnd = nextRentDueDate.minusDays(1);

        // But cannot extend beyond statement period
        if (billingEnd.isAfter(periodEnd)) {
            return periodEnd;
        }

        return billingEnd;
    }

    /**
     * Calculate prorated rent based on actual days in billing cycle
     *
     * Matches Excel formula logic:
     * - Full month = full rent
     * - Partial month = (days in period / 30) * monthly rent
     */
    private BigDecimal calculateProratedRent(BigDecimal monthlyRent,
                                            LocalDate billingStart,
                                            LocalDate billingEnd,
                                            LocalDate tenancyStart,
                                            LocalDate tenancyEnd) {

        // Determine actual period to charge for
        LocalDate chargeStart = billingStart;
        LocalDate chargeEnd = billingEnd;

        // Cannot charge before tenancy started
        if (tenancyStart.isAfter(chargeStart)) {
            chargeStart = tenancyStart;
        }

        // Cannot charge after tenancy ended
        if (tenancyEnd != null && tenancyEnd.isBefore(chargeEnd)) {
            chargeEnd = tenancyEnd;
        }

        // Check if there's anything to charge
        if (chargeStart.isAfter(chargeEnd)) {
            return BigDecimal.ZERO;
        }

        // Calculate days to charge (inclusive)
        long daysToCharge = ChronoUnit.DAYS.between(chargeStart, chargeEnd) + 1;

        // Check if this is a full billing period (30 days or more)
        long fullBillingPeriodDays = ChronoUnit.DAYS.between(billingStart, billingEnd) + 1;

        if (daysToCharge >= 30 || daysToCharge == fullBillingPeriodDays) {
            // Full month rent
            log.debug("Full month rent: £{} ({} days)", monthlyRent, daysToCharge);
            return monthlyRent;
        }

        // Prorated rent: (days / 30) * monthly rent
        // Using 30-day month for consistency with Excel formula
        BigDecimal daysDecimal = new BigDecimal(daysToCharge);
        BigDecimal proratedRent = monthlyRent
            .multiply(daysDecimal)
            .divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP);

        log.debug("Prorated rent: £{} ({} days / 30 * £{})", proratedRent, daysToCharge, monthlyRent);

        // Use MIN(prorated, monthly) to never exceed monthly rent
        return proratedRent.min(monthlyRent);
    }

    /**
     * Get rent due day for a period (day of month)
     * Returns empty string if tenancy not active
     *
     * Matches Excel: =IF(AND(E14<=$B$9, OR(F14="", F14>=$B$8)), DAY(E14), "")
     */
    public String getRentDueDayForPeriod(Invoice invoice, LocalDate periodStart, LocalDate periodEnd) {
        LocalDate tenancyStart = invoice.getStartDate();
        LocalDate tenancyEnd = invoice.getEndDate();

        // Check if tenancy is active during period
        if (!isTenancyActiveDuringPeriod(tenancyStart, tenancyEnd, periodStart, periodEnd)) {
            return "";
        }

        // Return the day of month from tenancy start (or payment_day if specified)
        if (invoice.getPaymentDay() != null && invoice.getPaymentDay() > 0) {
            return String.valueOf(invoice.getPaymentDay());
        }

        if (tenancyStart != null) {
            return String.valueOf(tenancyStart.getDayOfMonth());
        }

        return "";
    }

    /**
     * Calculate total rent due for a property over a date range
     * Handles multiple leases if property had tenant turnover
     *
     * IMPROVED: Calculates rent per BILLING PERIOD (payment cycle) not per calendar month
     * This correctly handles partial months when tenancy ends mid-cycle
     */
    public BigDecimal calculateTotalRentDue(Long propertyId, LocalDate fromDate, LocalDate toDate) {
        List<Invoice> leases = invoiceRepository.findByPropertyIdAndDateRange(propertyId, fromDate, toDate);

        BigDecimal totalDue = BigDecimal.ZERO;

        for (Invoice lease : leases) {
            BigDecimal leaseDue = calculateRentDueForLease(lease, fromDate, toDate);
            totalDue = totalDue.add(leaseDue);

            log.debug("Lease {} total due: £{}", lease.getId(), leaseDue);
        }

        log.info("Total rent due for property {} ({} to {}): £{}",
            propertyId, fromDate, toDate, totalDue);

        return totalDue;
    }

    /**
     * Calculate rent due for a single lease over a date range
     * Uses billing cycles (payment date to payment date) rather than calendar months
     */
    private BigDecimal calculateRentDueForLease(Invoice lease, LocalDate fromDate, LocalDate toDate) {
        LocalDate tenancyStart = lease.getStartDate();
        LocalDate tenancyEnd = lease.getEndDate();
        BigDecimal monthlyRent = lease.getAmount();
        Integer paymentDay = lease.getPaymentDay();

        if (tenancyStart == null || monthlyRent == null) {
            return BigDecimal.ZERO;
        }

        // Check if tenancy overlaps with the query period
        if (!isTenancyActiveDuringPeriod(tenancyStart, tenancyEnd, fromDate, toDate)) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalDue = BigDecimal.ZERO;
        int dueDay = (paymentDay != null && paymentDay > 0) ? paymentDay : tenancyStart.getDayOfMonth();

        // Start from the first payment date on or after fromDate
        LocalDate currentPaymentDate = findFirstPaymentDate(tenancyStart, dueDay, fromDate);

        // Iterate through each payment date within the period
        while (!currentPaymentDate.isAfter(toDate)) {
            // Check if this payment is within the tenancy period
            if (!currentPaymentDate.isBefore(tenancyStart)) {
                // Calculate the billing period this payment covers (payment date to day before next payment)
                LocalDate nextPaymentDate = getNextPaymentDate(currentPaymentDate, dueDay);
                LocalDate billingPeriodEnd = nextPaymentDate.minusDays(1);

                // If tenancy ends before billing period end, prorate
                LocalDate effectiveEnd = billingPeriodEnd;
                if (tenancyEnd != null && tenancyEnd.isBefore(effectiveEnd)) {
                    effectiveEnd = tenancyEnd;
                }

                // Only charge if the billing period overlaps with query period
                if (!effectiveEnd.isBefore(fromDate)) {
                    // Calculate days to charge
                    LocalDate chargeStart = currentPaymentDate.isBefore(fromDate) ? fromDate : currentPaymentDate;
                    LocalDate chargeEnd = effectiveEnd.isAfter(toDate) ? toDate : effectiveEnd;

                    if (!chargeStart.isAfter(chargeEnd)) {
                        long daysInCharge = ChronoUnit.DAYS.between(chargeStart, chargeEnd) + 1;
                        long daysInFullCycle = ChronoUnit.DAYS.between(currentPaymentDate, billingPeriodEnd) + 1;

                        BigDecimal paymentDue;

                        // If charging for the full billing cycle, charge full rent
                        if (daysInCharge >= 30 || (daysInCharge == daysInFullCycle && !effectiveEnd.isBefore(billingPeriodEnd))) {
                            paymentDue = monthlyRent;
                            log.debug("Payment {} - Full month: £{} ({} days)", currentPaymentDate, paymentDue, daysInCharge);
                        } else {
                            // Prorate based on days
                            paymentDue = monthlyRent
                                .multiply(BigDecimal.valueOf(daysInCharge))
                                .divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);
                            log.debug("Payment {} - Prorated: £{} ({} days / 30)", currentPaymentDate, paymentDue, daysInCharge);
                        }

                        totalDue = totalDue.add(paymentDue);
                    }
                }
            }

            // Move to next payment date
            currentPaymentDate = getNextPaymentDate(currentPaymentDate, dueDay);

            // Safety check: if we've passed the tenancy end or query end, stop
            if (tenancyEnd != null && currentPaymentDate.isAfter(tenancyEnd)) {
                break;
            }
            if (currentPaymentDate.isAfter(toDate.plusMonths(1))) {
                break;
            }
        }

        return totalDue;
    }

    /**
     * Find the first payment date on or after the given start date
     */
    private LocalDate findFirstPaymentDate(LocalDate tenancyStart, int paymentDay, LocalDate fromDate) {
        LocalDate searchDate = fromDate.isBefore(tenancyStart) ? tenancyStart : fromDate;

        // Try current month
        LocalDate paymentDate = getPaymentDateInMonth(searchDate.getYear(), searchDate.getMonthValue(), paymentDay);

        if (paymentDate.isBefore(searchDate)) {
            // Payment day already passed this month, use next month
            paymentDate = getPaymentDateInMonth(
                searchDate.plusMonths(1).getYear(),
                searchDate.plusMonths(1).getMonthValue(),
                paymentDay
            );
        }

        // But cannot be before tenancy start
        if (paymentDate.isBefore(tenancyStart)) {
            // Use tenancy start date as first payment
            return tenancyStart;
        }

        return paymentDate;
    }

    /**
     * Get the payment date for a specific month
     */
    private LocalDate getPaymentDateInMonth(int year, int month, int dayOfMonth) {
        try {
            return LocalDate.of(year, month, dayOfMonth);
        } catch (Exception e) {
            // Invalid day (e.g., 31st in February), use last day of month
            YearMonth ym = YearMonth.of(year, month);
            return ym.atEndOfMonth();
        }
    }

    /**
     * Get the next payment date after the given date
     */
    private LocalDate getNextPaymentDate(LocalDate currentDate, int paymentDay) {
        YearMonth nextMonth = YearMonth.from(currentDate).plusMonths(1);
        try {
            return nextMonth.atDay(paymentDay);
        } catch (Exception e) {
            return nextMonth.atEndOfMonth();
        }
    }
}

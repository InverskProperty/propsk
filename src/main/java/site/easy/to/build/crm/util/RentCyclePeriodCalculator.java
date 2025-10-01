package site.easy.to.build.crm.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for calculating rent cycle periods for statement generation.
 *
 * Rent cycles are typically monthly periods that align with rent due dates,
 * not calendar months. For example:
 * - 22nd Jan 2025 to 21st Feb 2025
 * - 22nd Feb 2025 to 21st Mar 2025
 */
public class RentCyclePeriodCalculator {

    /**
     * Represents a single rent cycle period
     */
    public static class RentCyclePeriod {
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final String periodName;

        public RentCyclePeriod(LocalDate startDate, LocalDate endDate, String periodName) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.periodName = periodName;
        }

        public LocalDate getStartDate() {
            return startDate;
        }

        public LocalDate getEndDate() {
            return endDate;
        }

        public String getPeriodName() {
            return periodName;
        }

        /**
         * Get sheet name for this period
         * Example: "January 22 - February 21 2025"
         */
        public String getSheetName() {
            DateTimeFormatter monthDay = DateTimeFormatter.ofPattern("MMMM d");
            DateTimeFormatter year = DateTimeFormatter.ofPattern("yyyy");

            String start = startDate.format(monthDay);
            String end = endDate.format(monthDay);
            String yr = endDate.format(year);

            return start + " - " + end + " " + yr;
        }

        /**
         * Get display name for UI/reports
         * Example: "Jan 22 - Feb 21, 2025"
         */
        public String getDisplayName() {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d");
            DateTimeFormatter yearFormatter = DateTimeFormatter.ofPattern("yyyy");

            return startDate.format(formatter) + " - " +
                   endDate.format(formatter) + ", " +
                   endDate.format(yearFormatter);
        }
    }

    /**
     * Split a date range into monthly rent cycle periods.
     *
     * The first period starts at fromDate and runs approximately one month.
     * Subsequent periods follow the same pattern.
     *
     * @param fromDate Start date of overall range
     * @param toDate End date of overall range
     * @return List of rent cycle periods covering the range
     */
    public static List<RentCyclePeriod> calculateMonthlyPeriods(LocalDate fromDate, LocalDate toDate) {
        List<RentCyclePeriod> periods = new ArrayList<>();

        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must be before or equal to toDate");
        }

        LocalDate currentStart = fromDate;
        int periodNumber = 1;

        while (currentStart.isBefore(toDate) || currentStart.equals(toDate)) {
            // Calculate end date: roughly one month from start, or toDate if sooner
            LocalDate potentialEnd = currentStart.plusMonths(1).minusDays(1);
            LocalDate currentEnd = potentialEnd.isAfter(toDate) ? toDate : potentialEnd;

            // Create period name
            String periodName = "Period " + periodNumber + " (" +
                              currentStart.format(DateTimeFormatter.ofPattern("MMM yyyy")) + ")";

            periods.add(new RentCyclePeriod(currentStart, currentEnd, periodName));

            // Move to next period
            currentStart = currentEnd.plusDays(1);
            periodNumber++;

            // Safety check: if we're past toDate, stop
            if (currentStart.isAfter(toDate)) {
                break;
            }
        }

        return periods;
    }

    /**
     * Determine if a date range should be split into multiple periods.
     *
     * Generally, ranges longer than 31 days should be split.
     *
     * @param fromDate Start date
     * @param toDate End date
     * @return true if range should be split into multiple periods
     */
    public static boolean shouldSplitIntoPeriods(LocalDate fromDate, LocalDate toDate) {
        long daysBetween = ChronoUnit.DAYS.between(fromDate, toDate);
        return daysBetween > 31; // More than a month
    }

    /**
     * Get a human-readable description of the overall period
     *
     * @param fromDate Start date
     * @param toDate End date
     * @return Description like "3 months (Jan 2025 - Mar 2025)"
     */
    public static String getPeriodDescription(LocalDate fromDate, LocalDate toDate) {
        List<RentCyclePeriod> periods = calculateMonthlyPeriods(fromDate, toDate);
        int monthCount = periods.size();

        DateTimeFormatter monthYear = DateTimeFormatter.ofPattern("MMM yyyy");
        String startMonth = fromDate.format(monthYear);
        String endMonth = toDate.format(monthYear);

        if (monthCount == 1) {
            return "1 month (" + startMonth + ")";
        } else if (startMonth.equals(endMonth)) {
            return monthCount + " periods (" + startMonth + ")";
        } else {
            return monthCount + " months (" + startMonth + " - " + endMonth + ")";
        }
    }
}

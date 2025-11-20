package site.easy.to.build.crm.service.property;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.AssignmentType;
import site.easy.to.build.crm.entity.CustomerPropertyAssignment;
import site.easy.to.build.crm.repository.CustomerPropertyAssignmentRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Service for calculating property occupancy statistics
 * Calculates days occupied and occupancy percentage over various time periods
 */
@Service
public class PropertyOccupancyService {

    @Autowired
    private CustomerPropertyAssignmentRepository customerPropertyAssignmentRepository;

    /**
     * Calculate occupancy for a property over the last 365 days
     */
    public OccupancyStats calculateOccupancyLast12Months(Long propertyId) {
        LocalDate today = LocalDate.now();
        LocalDate oneYearAgo = today.minusDays(365);

        return calculateOccupancy(propertyId, oneYearAgo, today);
    }

    /**
     * Calculate occupancy for a property over a specific date range
     */
    public OccupancyStats calculateOccupancy(Long propertyId, LocalDate startDate, LocalDate endDate) {
        // Get all tenant assignments for this property
        List<CustomerPropertyAssignment> tenantAssignments =
            customerPropertyAssignmentRepository.findByPropertyIdAndAssignmentType(propertyId, AssignmentType.TENANT);

        long totalDaysInPeriod = ChronoUnit.DAYS.between(startDate, endDate);
        long totalOccupiedDays = 0;

        for (CustomerPropertyAssignment assignment : tenantAssignments) {
            // Calculate overlap between assignment period and our analysis period
            LocalDate assignmentStart = assignment.getStartDate();
            LocalDate assignmentEnd = assignment.getEndDate();

            // Skip if assignment is completely outside our period
            if (assignmentStart != null) {
                // If assignment ended before our period, skip
                if (assignmentEnd != null && assignmentEnd.isBefore(startDate)) {
                    continue;
                }
                // If assignment starts after our period, skip
                if (assignmentStart.isAfter(endDate)) {
                    continue;
                }

                // Calculate overlap
                LocalDate overlapStart = assignmentStart.isBefore(startDate) ? startDate : assignmentStart;
                LocalDate overlapEnd = assignmentEnd == null ? endDate :
                                       (assignmentEnd.isAfter(endDate) ? endDate : assignmentEnd);

                long daysOccupied = ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1; // +1 to include both days
                totalOccupiedDays += Math.max(0, daysOccupied);
            }
        }

        // Calculate percentage
        BigDecimal occupancyPercentage = BigDecimal.ZERO;
        if (totalDaysInPeriod > 0) {
            occupancyPercentage = BigDecimal.valueOf(totalOccupiedDays)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalDaysInPeriod), 2, RoundingMode.HALF_UP);
        }

        // Calculate vacancy days
        long vacantDays = totalDaysInPeriod - totalOccupiedDays;

        return new OccupancyStats(
            propertyId,
            startDate,
            endDate,
            totalDaysInPeriod,
            totalOccupiedDays,
            vacantDays,
            occupancyPercentage
        );
    }

    /**
     * Get current occupancy status (is property currently occupied?)
     */
    public boolean isCurrentlyOccupied(Long propertyId) {
        List<CustomerPropertyAssignment> activeAssignments =
            customerPropertyAssignmentRepository.findByPropertyIdAndAssignmentType(propertyId, AssignmentType.TENANT);

        LocalDate today = LocalDate.now();
        return activeAssignments.stream()
            .anyMatch(assignment ->
                assignment.getStartDate() != null &&
                !assignment.getStartDate().isAfter(today) &&
                (assignment.getEndDate() == null || assignment.getEndDate().isAfter(today))
            );
    }

    /**
     * Get current tenant assignment
     */
    public CustomerPropertyAssignment getCurrentTenantAssignment(Long propertyId) {
        List<CustomerPropertyAssignment> activeAssignments =
            customerPropertyAssignmentRepository.findByPropertyIdAndAssignmentType(propertyId, AssignmentType.TENANT);

        LocalDate today = LocalDate.now();
        return activeAssignments.stream()
            .filter(assignment ->
                assignment.getStartDate() != null &&
                !assignment.getStartDate().isAfter(today) &&
                (assignment.getEndDate() == null || assignment.getEndDate().isAfter(today))
            )
            .findFirst()
            .orElse(null);
    }

    /**
     * DTO for occupancy statistics
     */
    public static class OccupancyStats {
        private final Long propertyId;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final long totalDays;
        private final long occupiedDays;
        private final long vacantDays;
        private final BigDecimal occupancyPercentage;

        public OccupancyStats(Long propertyId, LocalDate startDate, LocalDate endDate,
                            long totalDays, long occupiedDays, long vacantDays,
                            BigDecimal occupancyPercentage) {
            this.propertyId = propertyId;
            this.startDate = startDate;
            this.endDate = endDate;
            this.totalDays = totalDays;
            this.occupiedDays = occupiedDays;
            this.vacantDays = vacantDays;
            this.occupancyPercentage = occupancyPercentage;
        }

        // Getters
        public Long getPropertyId() { return propertyId; }
        public LocalDate getStartDate() { return startDate; }
        public LocalDate getEndDate() { return endDate; }
        public long getTotalDays() { return totalDays; }
        public long getOccupiedDays() { return occupiedDays; }
        public long getVacantDays() { return vacantDays; }
        public BigDecimal getOccupancyPercentage() { return occupancyPercentage; }

        public boolean isFullyOccupied() {
            return occupancyPercentage.compareTo(BigDecimal.valueOf(100)) == 0;
        }

        public boolean isFullyVacant() {
            return occupancyPercentage.compareTo(BigDecimal.ZERO) == 0;
        }
    }
}

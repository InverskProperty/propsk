package site.easy.to.build.crm.service.reporting;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.easy.to.build.crm.entity.PayPropTenantComplete;
import site.easy.to.build.crm.repository.PayPropTenantCompleteRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tenant Turnover Reporting Service
 * Provides analytics and reporting for tenant turnover, occupancy, and retention
 */
@Service
public class TenantTurnoverReportService {

    private static final Logger log = LoggerFactory.getLogger(TenantTurnoverReportService.class);

    @Autowired
    private PayPropTenantCompleteRepository tenantRepository;

    /**
     * Get comprehensive tenant turnover summary
     */
    public Map<String, Object> getTurnoverSummary(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> summary = new HashMap<>();

        LocalDate today = LocalDate.now();

        // Current tenant counts
        Long currentTenantsCount = tenantRepository.countCurrentTenants(today);
        summary.put("currentTenantsCount", currentTenantsCount);

        // Move-outs in period
        Long moveOutsInPeriod = tenantRepository.countTenantsMovedOutBetween(startDate, endDate);
        summary.put("moveOutsInPeriod", moveOutsInPeriod);

        // Move-ins in period
        Long moveInsInPeriod = tenantRepository.countTenantsMovedInBetween(startDate, endDate);
        summary.put("moveInsInPeriod", moveInsInPeriod);

        // Net change
        Long netChange = moveInsInPeriod - moveOutsInPeriod;
        summary.put("netChange", netChange);

        // Turnover rate (move-outs / current tenants * 100)
        BigDecimal turnoverRate = BigDecimal.ZERO;
        if (currentTenantsCount > 0) {
            turnoverRate = BigDecimal.valueOf(moveOutsInPeriod)
                    .divide(BigDecimal.valueOf(currentTenantsCount), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        summary.put("turnoverRatePercent", turnoverRate);

        // Upcoming move-outs (next 30 days)
        LocalDate thirtyDaysFromNow = today.plusDays(30);
        List<PayPropTenantComplete> upcomingMoveOuts =
                tenantRepository.findTenantsMovingOutSoon(today, thirtyDaysFromNow);
        summary.put("upcomingMoveOuts30Days", upcomingMoveOuts.size());

        // Upcoming move-outs (next 60 days)
        LocalDate sixtyDaysFromNow = today.plusDays(60);
        List<PayPropTenantComplete> upcomingMoveOuts60 =
                tenantRepository.findTenantsMovingOutSoon(today, sixtyDaysFromNow);
        summary.put("upcomingMoveOuts60Days", upcomingMoveOuts60.size());

        summary.put("periodStartDate", startDate);
        summary.put("periodEndDate", endDate);
        summary.put("reportGeneratedAt", today);

        log.info("Generated turnover summary: {} current tenants, {} move-outs, {}% turnover rate",
                currentTenantsCount, moveOutsInPeriod, turnoverRate);

        return summary;
    }

    /**
     * Get list of tenants moving out soon
     */
    public List<Map<String, Object>> getUpcomingMoveOuts(int days) {
        LocalDate today = LocalDate.now();
        LocalDate futureDate = today.plusDays(days);

        List<PayPropTenantComplete> tenants =
                tenantRepository.findTenantsMovingOutSoon(today, futureDate);

        return tenants.stream()
                .map(this::convertToSummaryDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get list of recent move-outs
     */
    public List<Map<String, Object>> getRecentMoveOuts(int days) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(days);

        List<PayPropTenantComplete> tenants =
                tenantRepository.findTenantsMovedOutBetween(startDate, today);

        return tenants.stream()
                .map(this::convertToSummaryDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get list of recent move-ins
     */
    public List<Map<String, Object>> getRecentMoveIns(int days) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(days);

        List<PayPropTenantComplete> tenants =
                tenantRepository.findTenantsMovedInBetween(startDate, today);

        return tenants.stream()
                .map(this::convertToSummaryDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get current tenants list
     */
    public List<Map<String, Object>> getCurrentTenants() {
        LocalDate today = LocalDate.now();
        List<PayPropTenantComplete> tenants = tenantRepository.findCurrentTenants(today);

        return tenants.stream()
                .map(this::convertToSummaryDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get turnover stats by property
     */
    public List<Map<String, Object>> getTurnoverByProperty(LocalDate startDate, LocalDate endDate) {
        List<String> propertyIds = tenantRepository.findAllPropertyIdsWithTenants();
        LocalDate today = LocalDate.now();

        return propertyIds.stream()
                .map(propertyId -> {
                    Map<String, Object> propertyStats = new HashMap<>();
                    propertyStats.put("propertyId", propertyId);

                    // Current tenants for this property
                    List<PayPropTenantComplete> currentTenants =
                            tenantRepository.findCurrentTenantsByPropertyId(propertyId, today);
                    propertyStats.put("currentTenants", currentTenants.size());

                    // Move-outs for this property in period
                    List<PayPropTenantComplete> allTenants =
                            tenantRepository.findByPropertyId(propertyId);
                    long moveOuts = allTenants.stream()
                            .filter(t -> t.getTenancyEndDate() != null)
                            .filter(t -> !t.getTenancyEndDate().isBefore(startDate) &&
                                       !t.getTenancyEndDate().isAfter(endDate))
                            .count();
                    propertyStats.put("moveOutsInPeriod", moveOuts);

                    // Move-ins for this property in period
                    long moveIns = allTenants.stream()
                            .filter(t -> t.getTenancyStartDate() != null)
                            .filter(t -> !t.getTenancyStartDate().isBefore(startDate) &&
                                       !t.getTenancyStartDate().isAfter(endDate))
                            .count();
                    propertyStats.put("moveInsInPeriod", moveIns);

                    // Turnover rate for this property
                    BigDecimal turnoverRate = BigDecimal.ZERO;
                    if (currentTenants.size() > 0) {
                        turnoverRate = BigDecimal.valueOf(moveOuts)
                                .divide(BigDecimal.valueOf(currentTenants.size()), 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100));
                    }
                    propertyStats.put("turnoverRatePercent", turnoverRate);

                    return propertyStats;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get tenancy duration statistics
     */
    public Map<String, Object> getTenancyDurationStats() {
        Object[] stats = tenantRepository.getOverallTenancyDurationStats();

        Map<String, Object> result = new HashMap<>();
        if (stats != null && stats.length >= 3) {
            result.put("averageTenancyMonths", stats[0] != null ?
                    BigDecimal.valueOf(((Number) stats[0]).doubleValue())
                            .setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            result.put("minTenancyMonths", stats[1] != null ?
                    BigDecimal.valueOf(((Number) stats[1]).doubleValue())
                            .setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            result.put("maxTenancyMonths", stats[2] != null ?
                    BigDecimal.valueOf(((Number) stats[2]).doubleValue())
                            .setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        } else {
            result.put("averageTenancyMonths", BigDecimal.ZERO);
            result.put("minTenancyMonths", BigDecimal.ZERO);
            result.put("maxTenancyMonths", BigDecimal.ZERO);
        }

        return result;
    }

    /**
     * Get monthly turnover trend (last N months)
     */
    public List<Map<String, Object>> getMonthlyTurnoverTrend(int months) {
        List<Map<String, Object>> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int i = months - 1; i >= 0; i--) {
            LocalDate monthStart = today.minusMonths(i).withDayOfMonth(1);
            LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);

            Map<String, Object> monthData = new HashMap<>();
            monthData.put("month", monthStart.getMonth().toString());
            monthData.put("year", monthStart.getYear());
            monthData.put("startDate", monthStart);
            monthData.put("endDate", monthEnd);

            Long moveOuts = tenantRepository.countTenantsMovedOutBetween(monthStart, monthEnd);
            Long moveIns = tenantRepository.countTenantsMovedInBetween(monthStart, monthEnd);

            monthData.put("moveOuts", moveOuts);
            monthData.put("moveIns", moveIns);
            monthData.put("netChange", moveIns - moveOuts);

            trend.add(monthData);
        }

        return trend;
    }

    /**
     * Convert tenant to summary DTO for API responses
     */
    private Map<String, Object> convertToSummaryDTO(PayPropTenantComplete tenant) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("payPropId", tenant.getPayPropId());
        dto.put("name", tenant.getFullName());
        dto.put("email", tenant.getEmail());
        dto.put("phone", tenant.getPhone());
        dto.put("mobile", tenant.getMobile());
        dto.put("propertyId", tenant.getCurrentPropertyId());
        dto.put("tenancyStartDate", tenant.getTenancyStartDate());
        dto.put("tenancyEndDate", tenant.getTenancyEndDate());
        dto.put("monthlyRent", tenant.getMonthlyRentAmount());
        dto.put("isActive", tenant.isCurrentlyActive());
        dto.put("status", tenant.getTenantStatus());

        // Calculate tenancy length if applicable
        if (tenant.getTenancyStartDate() != null) {
            LocalDate endDate = tenant.getTenancyEndDate() != null ?
                    tenant.getTenancyEndDate() : LocalDate.now();
            long daysInTenancy = ChronoUnit.DAYS.between(tenant.getTenancyStartDate(), endDate);
            dto.put("tenancyLengthDays", daysInTenancy);
            dto.put("tenancyLengthMonths",
                    BigDecimal.valueOf(daysInTenancy / 30.0)
                            .setScale(1, RoundingMode.HALF_UP));
        }

        // Days until move-out (if applicable)
        if (tenant.getTenancyEndDate() != null && tenant.isCurrentlyActive()) {
            long daysUntilMoveOut = ChronoUnit.DAYS.between(LocalDate.now(), tenant.getTenancyEndDate());
            dto.put("daysUntilMoveOut", daysUntilMoveOut);
        }

        return dto;
    }
}

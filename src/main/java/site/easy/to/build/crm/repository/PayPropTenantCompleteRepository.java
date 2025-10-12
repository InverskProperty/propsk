package site.easy.to.build.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.PayPropTenantComplete;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for PayProp Tenant Complete data
 * Used for tenant turnover reporting and analytics
 */
@Repository
public interface PayPropTenantCompleteRepository extends JpaRepository<PayPropTenantComplete, String> {

    /**
     * Find all current/active tenants (no end date or end date in future)
     */
    @Query("SELECT t FROM PayPropTenantComplete t WHERE t.tenancyEndDate IS NULL OR t.tenancyEndDate > :today")
    List<PayPropTenantComplete> findCurrentTenants(@Param("today") LocalDate today);

    /**
     * Find all past tenants (end date in past)
     */
    @Query("SELECT t FROM PayPropTenantComplete t WHERE t.tenancyEndDate IS NOT NULL AND t.tenancyEndDate < :today")
    List<PayPropTenantComplete> findPastTenants(@Param("today") LocalDate today);

    /**
     * Find tenants moving out soon (within specified days)
     */
    @Query("SELECT t FROM PayPropTenantComplete t WHERE t.tenancyEndDate IS NOT NULL " +
           "AND t.tenancyEndDate > :today AND t.tenancyEndDate <= :futureDate")
    List<PayPropTenantComplete> findTenantsMovingOutSoon(
            @Param("today") LocalDate today,
            @Param("futureDate") LocalDate futureDate
    );

    /**
     * Find tenants who moved out in a date range
     */
    @Query("SELECT t FROM PayPropTenantComplete t WHERE t.tenancyEndDate IS NOT NULL " +
           "AND t.tenancyEndDate >= :startDate AND t.tenancyEndDate <= :endDate")
    List<PayPropTenantComplete> findTenantsMovedOutBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Find tenants who moved in during a date range
     */
    @Query("SELECT t FROM PayPropTenantComplete t WHERE t.tenancyStartDate IS NOT NULL " +
           "AND t.tenancyStartDate >= :startDate AND t.tenancyStartDate <= :endDate")
    List<PayPropTenantComplete> findTenantsMovedInBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Count current/active tenants
     */
    @Query("SELECT COUNT(t) FROM PayPropTenantComplete t WHERE t.tenancyEndDate IS NULL OR t.tenancyEndDate > :today")
    Long countCurrentTenants(@Param("today") LocalDate today);

    /**
     * Count tenants who moved out in a date range
     */
    @Query("SELECT COUNT(t) FROM PayPropTenantComplete t WHERE t.tenancyEndDate IS NOT NULL " +
           "AND t.tenancyEndDate >= :startDate AND t.tenancyEndDate <= :endDate")
    Long countTenantsMovedOutBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Count tenants who moved in during a date range
     */
    @Query("SELECT COUNT(t) FROM PayPropTenantComplete t WHERE t.tenancyStartDate IS NOT NULL " +
           "AND t.tenancyStartDate >= :startDate AND t.tenancyStartDate <= :endDate")
    Long countTenantsMovedInBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Find tenants by property ID
     */
    @Query("SELECT t FROM PayPropTenantComplete t WHERE t.currentPropertyId = :propertyId")
    List<PayPropTenantComplete> findByPropertyId(@Param("propertyId") String propertyId);

    /**
     * Find current tenants by property ID
     */
    @Query("SELECT t FROM PayPropTenantComplete t WHERE t.currentPropertyId = :propertyId " +
           "AND (t.tenancyEndDate IS NULL OR t.tenancyEndDate > :today)")
    List<PayPropTenantComplete> findCurrentTenantsByPropertyId(
            @Param("propertyId") String propertyId,
            @Param("today") LocalDate today
    );

    /**
     * Find all unique property IDs that have tenants
     */
    @Query("SELECT DISTINCT t.currentPropertyId FROM PayPropTenantComplete t WHERE t.currentPropertyId IS NOT NULL")
    List<String> findAllPropertyIdsWithTenants();

    /**
     * Find all unique property IDs that have current tenants
     */
    @Query("SELECT DISTINCT t.currentPropertyId FROM PayPropTenantComplete t " +
           "WHERE t.currentPropertyId IS NOT NULL AND (t.tenancyEndDate IS NULL OR t.tenancyEndDate > :today)")
    List<String> findPropertyIdsWithCurrentTenants(@Param("today") LocalDate today);

    /**
     * Get tenancy duration statistics
     * Returns list of [propertyId, avgDurationMonths, minDurationMonths, maxDurationMonths]
     */
    @Query("SELECT t.currentPropertyId, " +
           "AVG(FUNCTION('DATEDIFF', t.tenancyEndDate, t.tenancyStartDate) / 30.0) as avgMonths, " +
           "MIN(FUNCTION('DATEDIFF', t.tenancyEndDate, t.tenancyStartDate) / 30.0) as minMonths, " +
           "MAX(FUNCTION('DATEDIFF', t.tenancyEndDate, t.tenancyStartDate) / 30.0) as maxMonths " +
           "FROM PayPropTenantComplete t " +
           "WHERE t.tenancyStartDate IS NOT NULL AND t.tenancyEndDate IS NOT NULL " +
           "GROUP BY t.currentPropertyId")
    List<Object[]> getTenancyDurationStatsByProperty();

    /**
     * Get overall tenancy duration statistics
     */
    @Query("SELECT " +
           "AVG(FUNCTION('DATEDIFF', t.tenancyEndDate, t.tenancyStartDate) / 30.0) as avgMonths, " +
           "MIN(FUNCTION('DATEDIFF', t.tenancyEndDate, t.tenancyStartDate) / 30.0) as minMonths, " +
           "MAX(FUNCTION('DATEDIFF', t.tenancyEndDate, t.tenancyStartDate) / 30.0) as maxMonths " +
           "FROM PayPropTenantComplete t " +
           "WHERE t.tenancyStartDate IS NOT NULL AND t.tenancyEndDate IS NOT NULL")
    Object[] getOverallTenancyDurationStats();
}

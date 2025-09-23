package site.easy.to.build.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.TenantBalance;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TenantBalanceRepository extends JpaRepository<TenantBalance, Long> {

    /**
     * Find tenant balance for specific tenant and statement period
     */
    Optional<TenantBalance> findByTenantIdAndStatementPeriod(String tenantId, LocalDate statementPeriod);

    /**
     * Find tenant balance for specific property and statement period
     */
    Optional<TenantBalance> findByPropertyIdAndStatementPeriod(String propertyId, LocalDate statementPeriod);

    /**
     * Get the most recent balance for a tenant (for previous balance calculation)
     */
    @Query("SELECT tb FROM TenantBalance tb WHERE tb.tenantId = :tenantId " +
           "AND tb.statementPeriod < :currentPeriod " +
           "ORDER BY tb.statementPeriod DESC")
    Optional<TenantBalance> findMostRecentBalanceForTenant(@Param("tenantId") String tenantId,
                                                          @Param("currentPeriod") LocalDate currentPeriod);

    /**
     * Get the most recent balance for a property (for previous balance calculation)
     */
    @Query("SELECT tb FROM TenantBalance tb WHERE tb.propertyId = :propertyId " +
           "AND tb.statementPeriod < :currentPeriod " +
           "ORDER BY tb.statementPeriod DESC")
    Optional<TenantBalance> findMostRecentBalanceForProperty(@Param("propertyId") String propertyId,
                                                            @Param("currentPeriod") LocalDate currentPeriod);

    /**
     * Find all balances for a tenant (for history)
     */
    List<TenantBalance> findByTenantIdOrderByStatementPeriodDesc(String tenantId);

    /**
     * Find all balances for a property (for history)
     */
    List<TenantBalance> findByPropertyIdOrderByStatementPeriodDesc(String propertyId);

    /**
     * Find tenants in arrears for a specific period
     */
    @Query("SELECT tb FROM TenantBalance tb WHERE tb.statementPeriod = :period " +
           "AND tb.runningBalance > 0")
    List<TenantBalance> findTenantsInArrears(@Param("period") LocalDate period);

    /**
     * Find all balances for a specific statement period
     */
    List<TenantBalance> findByStatementPeriod(LocalDate statementPeriod);

    /**
     * Get running balance history for a tenant within date range
     */
    @Query("SELECT tb FROM TenantBalance tb WHERE tb.tenantId = :tenantId " +
           "AND tb.statementPeriod BETWEEN :fromDate AND :toDate " +
           "ORDER BY tb.statementPeriod ASC")
    List<TenantBalance> findBalanceHistoryForTenant(@Param("tenantId") String tenantId,
                                                   @Param("fromDate") LocalDate fromDate,
                                                   @Param("toDate") LocalDate toDate);
}
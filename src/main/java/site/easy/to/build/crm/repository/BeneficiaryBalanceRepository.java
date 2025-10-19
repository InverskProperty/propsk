// BeneficiaryBalanceRepository.java - Repository for beneficiary balances
package site.easy.to.build.crm.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.BeneficiaryBalance;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.Property;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BeneficiaryBalanceRepository extends JpaRepository<BeneficiaryBalance, Long> {
    
    // ===== ENTITY RELATIONSHIP QUERIES (NEW) =====

    /**
     * Find current balance for customer and property
     */
    @Query("SELECT bb FROM BeneficiaryBalance bb WHERE bb.customer = :customer AND bb.property = :property " +
           "AND bb.balanceDate = (SELECT MAX(bb2.balanceDate) FROM BeneficiaryBalance bb2 " +
           "WHERE bb2.customer = :customer AND bb2.property = :property)")
    Optional<BeneficiaryBalance> findCurrentBalance(@Param("customer") Customer customer,
                                                     @Param("property") Property property);

    /**
     * Find balance for customer, property and specific date
     */
    Optional<BeneficiaryBalance> findByCustomerAndPropertyAndBalanceDate(Customer customer,
                                                                          Property property,
                                                                          LocalDate balanceDate);

    /**
     * Find all balances for a customer
     */
    List<BeneficiaryBalance> findByCustomer(Customer customer);

    /**
     * Find all balances for a property
     */
    List<BeneficiaryBalance> findByProperty(Property property);

    /**
     * Find balances for customer and property
     */
    List<BeneficiaryBalance> findByCustomerAndProperty(Customer customer, Property property);

    /**
     * Find active balances for a customer
     */
    @Query("SELECT bb FROM BeneficiaryBalance bb WHERE bb.customer = :customer " +
           "AND bb.status = 'ACTIVE' ORDER BY bb.balanceDate DESC")
    List<BeneficiaryBalance> findActiveBalancesByCustomer(@Param("customer") Customer customer);

    /**
     * Find overdrawn balances (negative) for all properties
     */
    @Query("SELECT bb FROM BeneficiaryBalance bb WHERE bb.balanceAmount < 0 " +
           "AND bb.status = 'ACTIVE' ORDER BY bb.balanceAmount")
    List<BeneficiaryBalance> findOverdrawnBalances();

    /**
     * Find balances due for payment (above threshold)
     */
    @Query("SELECT bb FROM BeneficiaryBalance bb WHERE bb.balanceAmount > :threshold " +
           "AND bb.status = 'ACTIVE' ORDER BY bb.balanceAmount DESC")
    List<BeneficiaryBalance> findBalancesDueForPayment(@Param("threshold") BigDecimal threshold);

    // ===== BENEFICIARY-BASED QUERIES (LEGACY) =====

    /**
     * Find balance by beneficiary ID (global balance)
     * @deprecated Use findCurrentBalance with Customer and Property entities instead
     */
    @Deprecated
    BeneficiaryBalance findByBeneficiaryId(Long beneficiaryId);

    /**
     * Find balance by beneficiary and property (property-specific balance)
     * @deprecated Use findByCustomerAndProperty instead
     */
    @Deprecated
    BeneficiaryBalance findByBeneficiaryIdAndPropertyId(Long beneficiaryId, Long propertyId);

    /**
     * Find all balances for a beneficiary
     * @deprecated Use findByCustomer instead
     */
    @Deprecated
    List<BeneficiaryBalance> findAllByBeneficiaryId(Long beneficiaryId);

    /**
     * Find balances for multiple beneficiaries
     * @deprecated Use findByCustomer instead
     */
    @Deprecated
    List<BeneficiaryBalance> findByBeneficiaryIdIn(List<Long> beneficiaryIds);

    // ===== PROPERTY-BASED QUERIES =====
    
    /**
     * Find all balances for a specific property
     */
    List<BeneficiaryBalance> findByPropertyId(Long propertyId);
    
    /**
     * Find balances for multiple properties
     */
    List<BeneficiaryBalance> findByPropertyIdIn(List<Long> propertyIds);
    
    /**
     * Find global balances (not property-specific)
     */
    List<BeneficiaryBalance> findByPropertyIdIsNull();
    
    /**
     * Find property-specific balances
     */
    List<BeneficiaryBalance> findByPropertyIdIsNotNull();

    // ===== BALANCE AMOUNT QUERIES =====
    
    /**
     * Find positive balances (money owed to beneficiaries)
     */
    @Query("SELECT bb FROM BeneficiaryBalance bb WHERE bb.balanceAmount > 0")
    List<BeneficiaryBalance> findPositiveBalances();
    
    /**
     * Find negative balances (credit balances)
     */
    @Query("SELECT bb FROM BeneficiaryBalance bb WHERE bb.balanceAmount < 0")
    List<BeneficiaryBalance> findNegativeBalances();
    
    /**
     * Find zero balances
     */
    @Query("SELECT bb FROM BeneficiaryBalance bb WHERE bb.balanceAmount = 0")
    List<BeneficiaryBalance> findZeroBalances();
    
    /**
     * Find balances above a threshold
     */
    List<BeneficiaryBalance> findByBalanceAmountGreaterThan(BigDecimal threshold);
    
    /**
     * Find balances below a threshold
     */
    List<BeneficiaryBalance> findByBalanceAmountLessThan(BigDecimal threshold);
    
    /**
     * Find balances within a range
     */
    List<BeneficiaryBalance> findByBalanceAmountBetween(BigDecimal minAmount, BigDecimal maxAmount);

    // ===== STATUS-BASED QUERIES =====
    
    /**
     * Find balances by status
     */
    List<BeneficiaryBalance> findByStatus(String status);
    
    /**
     * Find active balances
     */
    @Query("SELECT bb FROM BeneficiaryBalance bb WHERE bb.status = 'ACTIVE'")
    List<BeneficiaryBalance> findActiveBalances();
    
    /**
     * Find cleared balances
     */
    List<BeneficiaryBalance> findByIsCleared(String isCleared);
    
    /**
     * Find uncleared balances
     */
    @Query("SELECT bb FROM BeneficiaryBalance bb WHERE bb.isCleared = 'N'")
    List<BeneficiaryBalance> findUnclearedBalances();
    
    /**
     * Find cleared balances
     */
    @Query("SELECT bb FROM BeneficiaryBalance bb WHERE bb.isCleared = 'Y'")
    List<BeneficiaryBalance> findClearedBalances();

    // ===== DATE-BASED QUERIES =====
    
    /**
     * Find balances by specific date
     */
    List<BeneficiaryBalance> findByBalanceDate(LocalDate balanceDate);
    
    /**
     * Find balances within date range
     */
    List<BeneficiaryBalance> findByBalanceDateBetween(LocalDate startDate, LocalDate endDate);
    
    /**
     * Find balances from a specific date onwards
     */
    List<BeneficiaryBalance> findByBalanceDateGreaterThanEqual(LocalDate fromDate);
    
    /**
     * Find recently updated balances
     */
    @Query("SELECT bb FROM BeneficiaryBalance bb WHERE bb.lastUpdated >= :cutoffDateTime ORDER BY bb.lastUpdated DESC")
    List<BeneficiaryBalance> findRecentlyUpdated(@Param("cutoffDateTime") java.time.LocalDateTime cutoffDateTime);
    
    /**
     * Find balances cleared within date range
     */
    List<BeneficiaryBalance> findByClearedDateBetween(LocalDate startDate, LocalDate endDate);

    // ===== ANALYTICAL QUERIES =====
    
    /**
     * Sum total outstanding balances
     */
    @Query("SELECT COALESCE(SUM(bb.balanceAmount), 0) FROM BeneficiaryBalance bb WHERE bb.balanceAmount > 0 AND bb.isCleared = 'N'")
    BigDecimal sumOutstandingBalances();
    
    /**
     * Sum balances for a specific beneficiary
     */
    @Query("SELECT COALESCE(SUM(bb.balanceAmount), 0) FROM BeneficiaryBalance bb WHERE bb.beneficiaryId = :beneficiaryId")
    BigDecimal sumBalancesByBeneficiary(@Param("beneficiaryId") Long beneficiaryId);
    
    /**
     * Sum balances for a specific property
     */
    @Query("SELECT COALESCE(SUM(bb.balanceAmount), 0) FROM BeneficiaryBalance bb WHERE bb.propertyId = :propertyId")
    BigDecimal sumBalancesByProperty(@Param("propertyId") Long propertyId);
    
    /**
     * Count balances by status
     */
    @Query("SELECT bb.status, COUNT(bb) FROM BeneficiaryBalance bb GROUP BY bb.status")
    List<Object[]> countBalancesByStatus();
    
    /**
     * Get balance summary by beneficiary
     */
    @Query("SELECT bb.beneficiaryId, COUNT(bb), SUM(bb.balanceAmount) FROM BeneficiaryBalance bb GROUP BY bb.beneficiaryId")
    List<Object[]> getBalanceSummaryByBeneficiary();

    // ===== SEARCH AND FILTERING =====
    
    /**
     * Search balances by description or notes
     */
    @Query("SELECT bb FROM BeneficiaryBalance bb WHERE " +
           "LOWER(bb.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(bb.notes) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<BeneficiaryBalance> searchByDescriptionOrNotes(@Param("searchTerm") String searchTerm);
    
    /**
     * Advanced balance search
     */
    @Query("SELECT bb FROM BeneficiaryBalance bb WHERE " +
           "(:beneficiaryId IS NULL OR bb.beneficiaryId = :beneficiaryId) AND " +
           "(:propertyId IS NULL OR bb.propertyId = :propertyId) AND " +
           "(:minAmount IS NULL OR bb.balanceAmount >= :minAmount) AND " +
           "(:maxAmount IS NULL OR bb.balanceAmount <= :maxAmount) AND " +
           "(:status IS NULL OR bb.status = :status) AND " +
           "(:isCleared IS NULL OR bb.isCleared = :isCleared) AND " +
           "(:startDate IS NULL OR bb.balanceDate >= :startDate) AND " +
           "(:endDate IS NULL OR bb.balanceDate <= :endDate)")
    List<BeneficiaryBalance> searchBalances(@Param("beneficiaryId") Long beneficiaryId,
                                          @Param("propertyId") Long propertyId,
                                          @Param("minAmount") BigDecimal minAmount,
                                          @Param("maxAmount") BigDecimal maxAmount,
                                          @Param("status") String status,
                                          @Param("isCleared") String isCleared,
                                          @Param("startDate") LocalDate startDate,
                                          @Param("endDate") LocalDate endDate,
                                          Pageable pageable);

    // ===== REPORTING QUERIES =====
    
    /**
     * Get outstanding balances report (positive balances only)
     */
    @Query("SELECT bb FROM BeneficiaryBalance bb WHERE bb.balanceAmount > 0 AND bb.isCleared = 'N' ORDER BY bb.balanceAmount DESC")
    List<BeneficiaryBalance> getOutstandingBalancesReport();
    
    /**
     * Get credit balances report (negative balances)
     */
    @Query("SELECT bb FROM BeneficiaryBalance bb WHERE bb.balanceAmount < 0 ORDER BY bb.balanceAmount")
    List<BeneficiaryBalance> getCreditBalancesReport();
    
    /**
     * Get aged balances (balances older than specified days)
     */
    @Query("SELECT bb FROM BeneficiaryBalance bb WHERE bb.balanceDate <= :cutoffDate AND bb.isCleared = 'N' ORDER BY bb.balanceDate")
    List<BeneficiaryBalance> getAgedBalances(@Param("cutoffDate") LocalDate cutoffDate);

    // ===== MAINTENANCE QUERIES =====
    
    /**
     * Find duplicate balances (same beneficiary and property)
     */
    @Query("SELECT bb1 FROM BeneficiaryBalance bb1 WHERE EXISTS " +
           "(SELECT bb2 FROM BeneficiaryBalance bb2 WHERE bb2.beneficiaryId = bb1.beneficiaryId " +
           "AND bb2.propertyId = bb1.propertyId AND bb2.id != bb1.id)")
    List<BeneficiaryBalance> findDuplicateBalances();
    
    /**
     * Find balances with missing beneficiary
     */
    @Query("SELECT bb FROM BeneficiaryBalance bb WHERE NOT EXISTS " +
           "(SELECT b FROM Beneficiary b WHERE b.id = bb.beneficiaryId)")
    List<BeneficiaryBalance> findOrphanedBalances();

    // ===== PAGINATION SUPPORT =====
    
    /**
     * Find recent balances with pagination
     */
    List<BeneficiaryBalance> findByOrderByBalanceDateDesc(Pageable pageable);
    
    /**
     * Find balances by beneficiary with pagination
     */
    List<BeneficiaryBalance> findByBeneficiaryIdOrderByBalanceDateDesc(Long beneficiaryId, Pageable pageable);
    
    /**
     * Find balances by property with pagination
     */
    List<BeneficiaryBalance> findByPropertyIdOrderByBalanceDateDesc(Long propertyId, Pageable pageable);
}
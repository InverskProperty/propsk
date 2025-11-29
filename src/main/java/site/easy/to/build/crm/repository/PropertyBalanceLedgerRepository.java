package site.easy.to.build.crm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.PropertyBalanceLedger;
import site.easy.to.build.crm.entity.PropertyBalanceLedger.EntryType;
import site.easy.to.build.crm.entity.PropertyBalanceLedger.Source;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyBalanceLedgerRepository extends JpaRepository<PropertyBalanceLedger, Long> {

    // ===== BASIC QUERIES =====

    List<PropertyBalanceLedger> findByPropertyIdOrderByEntryDateDescCreatedAtDesc(Long propertyId);

    List<PropertyBalanceLedger> findByOwnerIdOrderByEntryDateDescCreatedAtDesc(Long ownerId);

    List<PropertyBalanceLedger> findByPaymentBatchId(String paymentBatchId);

    // ===== DATE RANGE QUERIES =====

    List<PropertyBalanceLedger> findByPropertyIdAndEntryDateBetweenOrderByEntryDateAscCreatedAtAsc(
            Long propertyId, LocalDate startDate, LocalDate endDate);

    List<PropertyBalanceLedger> findByOwnerIdAndEntryDateBetweenOrderByEntryDateAscCreatedAtAsc(
            Long ownerId, LocalDate startDate, LocalDate endDate);

    // ===== PAGINATED QUERIES =====

    Page<PropertyBalanceLedger> findByPropertyIdOrderByEntryDateDescCreatedAtDesc(
            Long propertyId, Pageable pageable);

    Page<PropertyBalanceLedger> findByOwnerIdOrderByEntryDateDescCreatedAtDesc(
            Long ownerId, Pageable pageable);

    // ===== LATEST ENTRY QUERIES =====

    /**
     * Get the most recent ledger entry for a property
     * Used to get the current running balance
     */
    Optional<PropertyBalanceLedger> findFirstByPropertyIdOrderByEntryDateDescCreatedAtDesc(Long propertyId);

    /**
     * Get the most recent ledger entry for a property before a specific date
     * Used for point-in-time balance lookups
     */
    @Query("SELECT p FROM PropertyBalanceLedger p WHERE p.propertyId = :propertyId " +
           "AND p.entryDate <= :asOfDate ORDER BY p.entryDate DESC, p.createdAt DESC LIMIT 1")
    Optional<PropertyBalanceLedger> findLatestEntryAsOfDate(
            @Param("propertyId") Long propertyId,
            @Param("asOfDate") LocalDate asOfDate);

    // ===== TRANSFER QUERIES =====

    /**
     * Find all transfers TO a property (TRANSFER_IN)
     */
    List<PropertyBalanceLedger> findByPropertyIdAndEntryTypeOrderByEntryDateDesc(
            Long propertyId, EntryType entryType);

    /**
     * Find all transfers between two properties
     */
    @Query("SELECT p FROM PropertyBalanceLedger p WHERE " +
           "(p.propertyId = :propertyId AND p.relatedPropertyId = :relatedPropertyId) OR " +
           "(p.propertyId = :relatedPropertyId AND p.relatedPropertyId = :propertyId) " +
           "ORDER BY p.entryDate DESC")
    List<PropertyBalanceLedger> findTransfersBetweenProperties(
            @Param("propertyId") Long propertyId,
            @Param("relatedPropertyId") Long relatedPropertyId);

    /**
     * Find all transfers to a block property within a date range
     */
    @Query("SELECT p FROM PropertyBalanceLedger p WHERE p.propertyId = :blockPropertyId " +
           "AND p.entryType = 'TRANSFER_IN' AND p.entryDate BETWEEN :startDate AND :endDate " +
           "ORDER BY p.entryDate ASC")
    List<PropertyBalanceLedger> findTransfersToBlock(
            @Param("blockPropertyId") Long blockPropertyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // ===== AGGREGATION QUERIES =====

    /**
     * Sum all deposits for a property within a date range
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PropertyBalanceLedger p " +
           "WHERE p.propertyId = :propertyId AND p.entryType = 'DEPOSIT' " +
           "AND p.entryDate BETWEEN :startDate AND :endDate")
    BigDecimal sumDepositsForProperty(
            @Param("propertyId") Long propertyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Sum all withdrawals for a property within a date range
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PropertyBalanceLedger p " +
           "WHERE p.propertyId = :propertyId AND p.entryType = 'WITHDRAWAL' " +
           "AND p.entryDate BETWEEN :startDate AND :endDate")
    BigDecimal sumWithdrawalsForProperty(
            @Param("propertyId") Long propertyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Sum transfers in to a block property within a date range
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PropertyBalanceLedger p " +
           "WHERE p.propertyId = :propertyId AND p.entryType = 'TRANSFER_IN' " +
           "AND p.entryDate BETWEEN :startDate AND :endDate")
    BigDecimal sumTransfersInForProperty(
            @Param("propertyId") Long propertyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Calculate total balance for an owner across all properties
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN p.entryType IN ('DEPOSIT', 'TRANSFER_IN', 'OPENING_BALANCE') " +
           "THEN p.amount ELSE -p.amount END), 0) " +
           "FROM PropertyBalanceLedger p WHERE p.ownerId = :ownerId")
    BigDecimal calculateTotalBalanceForOwner(@Param("ownerId") Long ownerId);

    // ===== SOURCE QUERIES =====

    List<PropertyBalanceLedger> findByPropertyIdAndSourceOrderByEntryDateDesc(
            Long propertyId, Source source);

    /**
     * Find entries from PayProp sync for reconciliation
     */
    @Query("SELECT p FROM PropertyBalanceLedger p WHERE p.source = 'PAYPROP_SYNC' " +
           "AND p.entryDate >= :since ORDER BY p.entryDate DESC")
    List<PropertyBalanceLedger> findPayPropSyncEntriesSince(@Param("since") LocalDate since);

    // ===== BATCH QUERIES =====

    /**
     * Find all entries linked to a specific payment batch
     */
    List<PropertyBalanceLedger> findByPaymentBatchIdOrderByPropertyIdAsc(String paymentBatchId);

    /**
     * Count entries for a batch
     */
    long countByPaymentBatchId(String paymentBatchId);

    // ===== REPORTING QUERIES =====

    /**
     * Get properties with non-zero balance
     */
    @Query("SELECT DISTINCT p.propertyId FROM PropertyBalanceLedger p " +
           "WHERE p.runningBalance != 0 ORDER BY p.propertyId")
    List<Long> findPropertiesWithBalance();

    /**
     * Get summary of balance movements by entry type for a property
     */
    @Query("SELECT p.entryType, COUNT(p), COALESCE(SUM(p.amount), 0) " +
           "FROM PropertyBalanceLedger p WHERE p.propertyId = :propertyId " +
           "AND p.entryDate BETWEEN :startDate AND :endDate " +
           "GROUP BY p.entryType")
    List<Object[]> getBalanceMovementSummary(
            @Param("propertyId") Long propertyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Get unit contribution summary for a block property
     */
    @Query("SELECT p.relatedPropertyId, p.relatedPropertyName, COALESCE(SUM(p.amount), 0) " +
           "FROM PropertyBalanceLedger p WHERE p.propertyId = :blockPropertyId " +
           "AND p.entryType = 'TRANSFER_IN' AND p.entryDate BETWEEN :startDate AND :endDate " +
           "GROUP BY p.relatedPropertyId, p.relatedPropertyName")
    List<Object[]> getUnitContributionSummary(
            @Param("blockPropertyId") Long blockPropertyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // ===== EXISTENCE CHECKS =====

    boolean existsByPropertyId(Long propertyId);

    boolean existsByPaymentBatchId(String paymentBatchId);

    /**
     * Check if property has any entries (for opening balance validation)
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END " +
           "FROM PropertyBalanceLedger p WHERE p.propertyId = :propertyId")
    boolean hasAnyEntries(@Param("propertyId") Long propertyId);
}

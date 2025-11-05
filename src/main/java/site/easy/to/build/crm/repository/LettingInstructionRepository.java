package site.easy.to.build.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.InstructionStatus;
import site.easy.to.build.crm.entity.LettingInstruction;
import site.easy.to.build.crm.entity.Property;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for LettingInstruction entity
 * Provides data access methods for letting instruction management
 */
@Repository
public interface LettingInstructionRepository extends JpaRepository<LettingInstruction, Long> {

    // ===== BASIC QUERIES =====

    /**
     * Find all instructions for a property, ordered by creation date (newest first)
     * Use case: View letting history for a property
     */
    List<LettingInstruction> findByPropertyOrderByCreatedAtDesc(Property property);

    /**
     * Find all instructions for a property with specific status
     * Use case: Check if property currently has active instruction
     */
    List<LettingInstruction> findByPropertyAndStatus(Property property, InstructionStatus status);

    /**
     * Find instruction by unique reference
     * Use case: Look up instruction by reference code
     */
    Optional<LettingInstruction> findByInstructionReference(String instructionReference);

    /**
     * Find all instructions by status
     * Use case: Dashboard views filtered by status
     */
    List<LettingInstruction> findByStatus(InstructionStatus status);

    /**
     * Find all instructions by status, ordered by creation date
     */
    List<LettingInstruction> findByStatusOrderByCreatedAtDesc(InstructionStatus status);

    // ===== ACTIVE INSTRUCTION QUERIES =====

    /**
     * Find active instruction for a property (ADVERTISING, VIEWINGS_IN_PROGRESS, OFFER_MADE)
     * Use case: Check if property is currently being marketed
     */
    @Query("SELECT li FROM LettingInstruction li WHERE li.property = :property " +
           "AND li.status IN (site.easy.to.build.crm.entity.InstructionStatus.ADVERTISING, " +
           "site.easy.to.build.crm.entity.InstructionStatus.VIEWINGS_IN_PROGRESS, " +
           "site.easy.to.build.crm.entity.InstructionStatus.OFFER_MADE) " +
           "ORDER BY li.createdAt DESC")
    Optional<LettingInstruction> findActiveInstructionForProperty(@Param("property") Property property);

    /**
     * Find all properties with active marketing instructions
     * Use case: Vacancy pipeline dashboard
     */
    @Query("SELECT li FROM LettingInstruction li WHERE " +
           "li.status IN (site.easy.to.build.crm.entity.InstructionStatus.ADVERTISING, " +
           "site.easy.to.build.crm.entity.InstructionStatus.VIEWINGS_IN_PROGRESS, " +
           "site.easy.to.build.crm.entity.InstructionStatus.OFFER_MADE) " +
           "ORDER BY li.advertisingStartDate DESC")
    List<LettingInstruction> findAllActiveMarketingInstructions();

    /**
     * Find all instructions with active leases
     * Use case: Currently occupied properties dashboard
     */
    @Query("SELECT li FROM LettingInstruction li WHERE li.status = site.easy.to.build.crm.entity.InstructionStatus.ACTIVE_LEASE " +
           "AND (li.leaseEndDate IS NULL OR li.leaseEndDate >= CURRENT_DATE) " +
           "ORDER BY li.leaseStartDate DESC")
    List<LettingInstruction> findAllActiveLeases();

    // ===== TENANT QUERIES =====

    /**
     * Find all instructions for a specific tenant
     * Use case: Tenant letting history
     */
    List<LettingInstruction> findByTenantOrderByLeaseStartDateDesc(Customer tenant);

    /**
     * Find current active lease for a tenant
     * Use case: Check if tenant currently has active lease
     */
    @Query("SELECT li FROM LettingInstruction li WHERE li.tenant = :tenant " +
           "AND li.status = site.easy.to.build.crm.entity.InstructionStatus.ACTIVE_LEASE " +
           "AND (li.leaseEndDate IS NULL OR li.leaseEndDate >= CURRENT_DATE)")
    Optional<LettingInstruction> findActiveLeaseForTenant(@Param("tenant") Customer tenant);

    // ===== DATE RANGE QUERIES =====

    /**
     * Find instructions that started advertising within a date range
     * Use case: Marketing performance reports
     */
    @Query("SELECT li FROM LettingInstruction li WHERE " +
           "li.advertisingStartDate BETWEEN :startDate AND :endDate " +
           "ORDER BY li.advertisingStartDate DESC")
    List<LettingInstruction> findByAdvertisingDateRange(@Param("startDate") LocalDate startDate,
                                                         @Param("endDate") LocalDate endDate);

    /**
     * Find instructions where leases started within a date range
     * Use case: Lease commencement reports
     */
    @Query("SELECT li FROM LettingInstruction li WHERE " +
           "li.leaseStartDate BETWEEN :startDate AND :endDate " +
           "ORDER BY li.leaseStartDate DESC")
    List<LettingInstruction> findByLeaseStartDateRange(@Param("startDate") LocalDate startDate,
                                                        @Param("endDate") LocalDate endDate);

    /**
     * Find instructions where leases end within a date range
     * Use case: Upcoming lease expiry alerts
     */
    @Query("SELECT li FROM LettingInstruction li WHERE " +
           "li.leaseEndDate BETWEEN :startDate AND :endDate " +
           "AND li.status = site.easy.to.build.crm.entity.InstructionStatus.ACTIVE_LEASE " +
           "ORDER BY li.leaseEndDate ASC")
    List<LettingInstruction> findByLeaseEndDateRange(@Param("startDate") LocalDate startDate,
                                                      @Param("endDate") LocalDate endDate);

    /**
     * Find leases expiring in the next N days
     * Use case: Renewal reminder system
     */
    @Query("SELECT li FROM LettingInstruction li WHERE " +
           "li.status = site.easy.to.build.crm.entity.InstructionStatus.ACTIVE_LEASE " +
           "AND li.leaseEndDate BETWEEN CURRENT_DATE AND :futureDate " +
           "ORDER BY li.leaseEndDate ASC")
    List<LettingInstruction> findLeasesExpiringBefore(@Param("futureDate") LocalDate futureDate);

    // ===== METRICS AND REPORTING QUERIES =====

    /**
     * Find instructions that were successfully converted (have tenant and lease)
     * Use case: Conversion rate analysis
     */
    @Query("SELECT li FROM LettingInstruction li WHERE " +
           "li.status IN (site.easy.to.build.crm.entity.InstructionStatus.ACTIVE_LEASE, " +
           "site.easy.to.build.crm.entity.InstructionStatus.CLOSED) " +
           "AND li.tenant IS NOT NULL " +
           "AND li.leaseStartDate IS NOT NULL")
    List<LettingInstruction> findSuccessfulConversions();

    /**
     * Calculate average days to fill for successfully let properties
     * Use case: Performance benchmarking
     */
    @Query("SELECT AVG(li.daysToFill) FROM LettingInstruction li WHERE " +
           "li.daysToFill IS NOT NULL " +
           "AND li.status IN (site.easy.to.build.crm.entity.InstructionStatus.ACTIVE_LEASE, " +
           "site.easy.to.build.crm.entity.InstructionStatus.CLOSED)")
    Double calculateAverageDaysToFill();

    /**
     * Calculate average conversion rate
     * Use case: Marketing effectiveness metrics
     */
    @Query("SELECT AVG(li.conversionRate) FROM LettingInstruction li WHERE " +
           "li.conversionRate IS NOT NULL " +
           "AND li.status IN (site.easy.to.build.crm.entity.InstructionStatus.ACTIVE_LEASE, " +
           "site.easy.to.build.crm.entity.InstructionStatus.CLOSED)")
    Double calculateAverageConversionRate();

    /**
     * Count instructions by status
     * Use case: Dashboard statistics
     */
    @Query("SELECT li.status, COUNT(li) FROM LettingInstruction li GROUP BY li.status")
    List<Object[]> countByStatus();

    /**
     * Find instructions with low conversion rates (below threshold)
     * Use case: Identify struggling listings
     */
    @Query("SELECT li FROM LettingInstruction li WHERE " +
           "li.conversionRate IS NOT NULL " +
           "AND li.conversionRate < :threshold " +
           "AND li.status IN (site.easy.to.build.crm.entity.InstructionStatus.ADVERTISING, " +
           "site.easy.to.build.crm.entity.InstructionStatus.VIEWINGS_IN_PROGRESS) " +
           "ORDER BY li.conversionRate ASC")
    List<LettingInstruction> findLowPerformingInstructions(@Param("threshold") Double threshold);

    /**
     * Find instructions that have been advertising for more than N days without conversion
     * Use case: Stale listing alerts
     */
    @Query("SELECT li FROM LettingInstruction li WHERE " +
           "li.status IN (site.easy.to.build.crm.entity.InstructionStatus.ADVERTISING, " +
           "site.easy.to.build.crm.entity.InstructionStatus.VIEWINGS_IN_PROGRESS) " +
           "AND li.advertisingStartDate IS NOT NULL " +
           "AND li.advertisingStartDate <= :thresholdDate " +
           "ORDER BY li.advertisingStartDate ASC")
    List<LettingInstruction> findStaleListings(@Param("thresholdDate") LocalDate thresholdDate);

    // ===== SEARCH QUERIES =====

    /**
     * Search instructions by property name or address
     * Use case: Search functionality
     */
    @Query("SELECT li FROM LettingInstruction li WHERE " +
           "LOWER(li.property.propertyName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(li.property.addressLine1) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(li.property.addressLine2) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(li.property.addressLine3) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "ORDER BY li.createdAt DESC")
    List<LettingInstruction> searchByPropertyNameOrAddress(@Param("searchTerm") String searchTerm);

    /**
     * Search instructions by reference or tenant name
     * Use case: Advanced search
     */
    @Query("SELECT li FROM LettingInstruction li LEFT JOIN li.tenant t WHERE " +
           "LOWER(li.instructionReference) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(t.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "ORDER BY li.createdAt DESC")
    List<LettingInstruction> searchByReferenceOrTenant(@Param("searchTerm") String searchTerm);

    // ===== KANBAN PIPELINE QUERIES =====

    /**
     * Find instructions for Kanban pipeline view with lead counts
     * Use case: Instruction workspace Kanban board
     */
    @Query("SELECT li, COUNT(l) as leadCount FROM LettingInstruction li " +
           "LEFT JOIN li.leads l " +
           "WHERE li.status IN (site.easy.to.build.crm.entity.InstructionStatus.ADVERTISING, " +
           "site.easy.to.build.crm.entity.InstructionStatus.VIEWINGS_IN_PROGRESS, " +
           "site.easy.to.build.crm.entity.InstructionStatus.OFFER_MADE) " +
           "GROUP BY li " +
           "ORDER BY li.advertisingStartDate DESC")
    List<Object[]> findActiveInstructionsWithLeadCounts();

    /**
     * Find instruction with all related entities eagerly fetched
     * Use case: Instruction workspace detail view (avoid N+1 queries)
     */
    @Query("SELECT DISTINCT li FROM LettingInstruction li " +
           "LEFT JOIN FETCH li.property " +
           "LEFT JOIN FETCH li.tenant " +
           "LEFT JOIN FETCH li.createdByUser " +
           "WHERE li.id = :instructionId")
    Optional<LettingInstruction> findByIdWithDetails(@Param("instructionId") Long instructionId);

    // ===== VALIDATION QUERIES =====

    /**
     * Check if property has any active or in-progress instructions
     * Use case: Prevent duplicate active instructions
     */
    @Query("SELECT COUNT(li) > 0 FROM LettingInstruction li WHERE li.property = :property " +
           "AND li.status IN (site.easy.to.build.crm.entity.InstructionStatus.INSTRUCTION_RECEIVED, " +
           "site.easy.to.build.crm.entity.InstructionStatus.PREPARING, " +
           "site.easy.to.build.crm.entity.InstructionStatus.ADVERTISING, " +
           "site.easy.to.build.crm.entity.InstructionStatus.VIEWINGS_IN_PROGRESS, " +
           "site.easy.to.build.crm.entity.InstructionStatus.OFFER_MADE, " +
           "site.easy.to.build.crm.entity.InstructionStatus.ACTIVE_LEASE)")
    boolean hasActiveInstructionOrLease(@Param("property") Property property);

    /**
     * Count active leases for tenant
     * Use case: Validate tenant doesn't have multiple active leases
     */
    @Query("SELECT COUNT(li) FROM LettingInstruction li WHERE li.tenant = :tenant " +
           "AND li.status = site.easy.to.build.crm.entity.InstructionStatus.ACTIVE_LEASE " +
           "AND (li.leaseEndDate IS NULL OR li.leaseEndDate >= CURRENT_DATE)")
    long countActiveLeasesByTenant(@Param("tenant") Customer tenant);
}

package site.easy.to.build.crm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.Invoice;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.Invoice.SyncStatus;
import site.easy.to.build.crm.entity.Invoice.InvoiceFrequency;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Invoice Repository - Data access layer for invoice management
 * Follows patterns from CustomerRepository and PropertyRepository
 */
@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    
    // ===== BASIC FINDERS =====
    
    /**
     * Find invoice by PayProp ID (for sync operations)
     */
    Optional<Invoice> findByPaypropId(String paypropId);
    
    /**
     * Find all invoices for a specific customer
     */
    List<Invoice> findByCustomer(Customer customer);
    
    /**
     * Find all invoices for a specific property
     */
    List<Invoice> findByProperty(Property property);

    /**
     * Find all active invoices for a specific property
     * Used to check for existing leases when importing from PayProp
     */
    List<Invoice> findByPropertyAndIsActiveTrue(Property property);

    /**
     * Find all invoices for a customer-property combination
     */
    List<Invoice> findByCustomerAndProperty(Customer customer, Property property);
    
    // ===== SYNC STATUS FINDERS =====
    
    /**
     * Find all invoices with specific sync status
     */
    List<Invoice> findBySyncStatus(SyncStatus syncStatus);
    
    /**
     * Find all pending sync invoices
     */
    @Query("SELECT i FROM Invoice i WHERE i.syncStatus = 'pending' AND i.isActive = true AND i.deletedAt IS NULL")
    List<Invoice> findPendingSyncInvoices();
    
    /**
     * Find all invoices that need PayProp sync
     */
    @Query("SELECT i FROM Invoice i WHERE i.paypropId IS NULL AND i.syncStatus = 'pending' " +
           "AND i.isActive = true AND i.deletedAt IS NULL " +
           "AND i.startDate <= :today AND (i.endDate IS NULL OR i.endDate >= :today)")
    List<Invoice> findInvoicesNeedingSync(@Param("today") LocalDate today);
    
    /**
     * Find invoices with sync errors
     */
    @Query("SELECT i FROM Invoice i WHERE i.syncStatus = 'error' AND i.deletedAt IS NULL")
    List<Invoice> findSyncErrorInvoices();
    
    // ===== ACTIVE INVOICE FINDERS =====
    
    /**
     * Find all currently active invoices (not deleted, active flag true, within date range)
     */
    @Query("SELECT i FROM Invoice i WHERE i.isActive = true AND i.deletedAt IS NULL " +
           "AND i.startDate <= :today AND (i.endDate IS NULL OR i.endDate >= :today)")
    List<Invoice> findCurrentlyActiveInvoices(@Param("today") LocalDate today);
    
    /**
     * Find active invoices for a specific customer
     */
    @Query("SELECT i FROM Invoice i WHERE i.customer = :customer AND i.isActive = true AND i.deletedAt IS NULL " +
           "AND i.startDate <= :today AND (i.endDate IS NULL OR i.endDate >= :today)")
    List<Invoice> findActiveInvoicesForCustomer(@Param("customer") Customer customer, @Param("today") LocalDate today);
    
    /**
     * Find active invoices for a specific property
     */
    @Query("SELECT i FROM Invoice i WHERE i.property = :property AND i.isActive = true AND i.deletedAt IS NULL " +
           "AND i.startDate <= :today AND (i.endDate IS NULL OR i.endDate >= :today)")
    List<Invoice> findActiveInvoicesForProperty(@Param("property") Property property, @Param("today") LocalDate today);
    
    // ===== FREQUENCY AND SCHEDULING FINDERS =====
    
    /**
     * Find invoices by frequency
     */
    List<Invoice> findByFrequency(InvoiceFrequency frequency);
    
    /**
     * Find monthly invoices due on a specific day
     */
    @Query("SELECT i FROM Invoice i WHERE i.frequency = 'M' AND i.paymentDay = :paymentDay " +
           "AND i.isActive = true AND i.deletedAt IS NULL " +
           "AND i.startDate <= :today AND (i.endDate IS NULL OR i.endDate >= :today)")
    List<Invoice> findMonthlyInvoicesByPaymentDay(@Param("paymentDay") Integer paymentDay, 
                                                  @Param("today") LocalDate today);
    
    /**
     * Find recurring invoices (not one-time)
     */
    @Query("SELECT i FROM Invoice i WHERE i.frequency != 'O' AND i.isActive = true AND i.deletedAt IS NULL")
    List<Invoice> findRecurringInvoices();
    
    // ===== CATEGORY AND AMOUNT FINDERS =====
    
    /**
     * Find invoices by category
     */
    List<Invoice> findByCategoryId(String categoryId);
    
    /**
     * Find invoices by category and active status
     */
    @Query("SELECT i FROM Invoice i WHERE i.categoryId = :categoryId AND i.isActive = true AND i.deletedAt IS NULL")
    List<Invoice> findActiveByCategoryId(@Param("categoryId") String categoryId);
    
    /**
     * Find rent invoices (category = 'rent' or contains 'rent')
     */
    @Query("SELECT i FROM Invoice i WHERE (i.categoryId = 'rent' OR LOWER(i.categoryName) LIKE '%rent%') " +
           "AND i.isActive = true AND i.deletedAt IS NULL")
    List<Invoice> findRentInvoices();
    
    // ===== DATE RANGE FINDERS =====
    
    /**
     * Find invoices starting within date range
     */
    @Query("SELECT i FROM Invoice i WHERE i.startDate BETWEEN :startDate AND :endDate " +
           "AND i.deletedAt IS NULL ORDER BY i.startDate ASC")
    List<Invoice> findByStartDateBetween(@Param("startDate") LocalDate startDate, 
                                        @Param("endDate") LocalDate endDate);
    
    /**
     * Find invoices ending within date range
     */
    @Query("SELECT i FROM Invoice i WHERE i.endDate BETWEEN :startDate AND :endDate " +
           "AND i.deletedAt IS NULL ORDER BY i.endDate ASC")
    List<Invoice> findByEndDateBetween(@Param("startDate") LocalDate startDate, 
                                      @Param("endDate") LocalDate endDate);
    
    /**
     * Find invoices created within date range
     */
    List<Invoice> findByCreatedAtBetween(java.time.LocalDateTime start, java.time.LocalDateTime end);
    
    // ===== SEARCH AND PAGINATION =====
    
    /**
     * Search invoices by description, customer name, or property name
     */
    @Query("SELECT i FROM Invoice i " +
           "LEFT JOIN i.customer c " +
           "LEFT JOIN i.property p " +
           "WHERE (LOWER(i.description) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(p.propertyName) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND i.deletedAt IS NULL")
    Page<Invoice> searchInvoices(@Param("keyword") String keyword, Pageable pageable);
    
    /**
     * Find invoices for a user (created by the user)
     */
    @Query("SELECT i FROM Invoice i WHERE i.createdByUser.id = :userId AND i.deletedAt IS NULL " +
           "ORDER BY i.createdAt DESC")
    Page<Invoice> findByCreatedByUserId(@Param("userId") Long userId, Pageable pageable);
    
    /**
     * Find all active invoices with pagination
     */
    @Query("SELECT i FROM Invoice i WHERE i.isActive = true AND i.deletedAt IS NULL " +
           "ORDER BY i.createdAt DESC")
    Page<Invoice> findActiveInvoices(Pageable pageable);
    
    // ===== STATISTICS AND REPORTING =====
    
    /**
     * Count active invoices
     */
    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.isActive = true AND i.deletedAt IS NULL")
    long countActiveInvoices();
    
    /**
     * Count invoices by sync status
     */
    long countBySyncStatus(SyncStatus syncStatus);
    
    /**
     * Count invoices for a customer
     */
    long countByCustomer(Customer customer);
    
    /**
     * Count invoices for a property
     */
    long countByProperty(Property property);
    
    /**
     * Get total invoice amount by customer
     */
    @Query("SELECT SUM(i.amount) FROM Invoice i WHERE i.customer = :customer " +
           "AND i.isActive = true AND i.deletedAt IS NULL")
    java.math.BigDecimal getTotalAmountByCustomer(@Param("customer") Customer customer);
    
    /**
     * Get total invoice amount by property
     */
    @Query("SELECT SUM(i.amount) FROM Invoice i WHERE i.property = :property " +
           "AND i.isActive = true AND i.deletedAt IS NULL")
    java.math.BigDecimal getTotalAmountByProperty(@Param("property") Property property);
    
    // ===== CUSTOM BUSINESS LOGIC QUERIES =====
    
    /**
     * Find invoices that might be duplicates (same customer, property, amount, frequency)
     */
    @Query("SELECT i FROM Invoice i WHERE EXISTS (" +
           "SELECT i2 FROM Invoice i2 WHERE i2.id != i.id " +
           "AND i2.customer = i.customer AND i2.property = i.property " +
           "AND i2.amount = i.amount AND i2.frequency = i.frequency " +
           "AND i2.categoryId = i.categoryId " +
           "AND i2.isActive = true AND i2.deletedAt IS NULL) " +
           "AND i.isActive = true AND i.deletedAt IS NULL")
    List<Invoice> findPotentialDuplicates();
    
    /**
     * Find invoices ending soon (within next 30 days)
     */
    @Query("SELECT i FROM Invoice i WHERE i.endDate BETWEEN :today AND :futureDate " +
           "AND i.isActive = true AND i.deletedAt IS NULL " +
           "ORDER BY i.endDate ASC")
    List<Invoice> findInvoicesEndingSoon(@Param("today") LocalDate today, 
                                        @Param("futureDate") LocalDate futureDate);
    
    /**
     * Find invoices without PayProp sync (local only)
     */
    @Query("SELECT i FROM Invoice i WHERE i.paypropId IS NULL AND i.deletedAt IS NULL")
    List<Invoice> findLocalOnlyInvoices();
    
    /**
     * Find invoices by internal reference
     */
    Optional<Invoice> findByInternalReference(String internalReference);
    
    /**
     * Find invoices by external reference (from PayProp)
     */
    Optional<Invoice> findByExternalReference(String externalReference);

    // ===== LEASE REFERENCE METHODS (FOR HISTORICAL TRANSACTION IMPORT) =====

    /**
     * Find invoice by lease reference
     * Used during historical transaction import to link transactions to leases
     */
    Optional<Invoice> findByLeaseReference(String leaseReference);

    /**
     * Check if lease reference is already used
     */
    boolean existsByLeaseReference(String leaseReference);

    /**
     * Find lease for property, customer, and matching period
     * Used to find/create leases during historical import
     */
    @Query("SELECT i FROM Invoice i WHERE i.property = :property AND i.customer = :customer " +
           "AND i.startDate = :startDate " +
           "AND (:endDate IS NULL AND i.endDate IS NULL OR i.endDate = :endDate) " +
           "AND i.deletedAt IS NULL " +
           "ORDER BY i.createdAt DESC")
    List<Invoice> findByPropertyAndCustomerAndPeriod(
        @Param("property") Property property,
        @Param("customer") Customer customer,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find leases for property and customer (all periods)
     * Used to show lease options during historical transaction review
     */
    @Query("SELECT i FROM Invoice i WHERE i.property = :property AND i.customer = :customer " +
           "AND i.deletedAt IS NULL " +
           "ORDER BY i.startDate DESC")
    List<Invoice> findByPropertyAndCustomerOrderByStartDateDesc(
        @Param("property") Property property,
        @Param("customer") Customer customer
    );

    // ===== SOFT DELETE SUPPORT =====
    
    /**
     * Find deleted invoices
     */
    @Query("SELECT i FROM Invoice i WHERE i.deletedAt IS NOT NULL")
    List<Invoice> findDeletedInvoices();
    
    /**
     * Find all invoices including deleted ones
     */
    @Query("SELECT i FROM Invoice i")
    List<Invoice> findAllIncludingDeleted();
    
    // ===== UTILITY METHODS =====
    
    /**
     * Check if invoice exists for customer and property combination
     */
    boolean existsByCustomerAndPropertyAndDeletedAtIsNull(Customer customer, Property property);
    
    /**
     * Check if PayProp ID is already used
     */
    boolean existsByPaypropId(String paypropId);
    
    /**
     * Check if internal reference is already used
     */
    boolean existsByInternalReference(String internalReference);

    /**
     * Find all invoices for a property that overlap with a date range
     * Used for calculating total rent due in a period
     * UPDATED: Include both active and inactive leases (ended leases still count for historical rent due)
     * Only exclude soft-deleted records
     */
    @Query("SELECT i FROM Invoice i WHERE i.property.id = :propertyId " +
           "AND i.startDate <= :endDate " +
           "AND (i.endDate IS NULL OR i.endDate >= :startDate) " +
           "AND i.deletedAt IS NULL")
    List<Invoice> findByPropertyIdAndDateRange(
        @Param("propertyId") Long propertyId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find all invoices (leases) for a property that overlap with a date range
     * Used for lease-based statement generation
     * UPDATED: Include both active and inactive leases for historical statements
     */
    @Query("SELECT i FROM Invoice i WHERE i.property = :property " +
           "AND i.startDate <= :endDate " +
           "AND (i.endDate IS NULL OR i.endDate >= :startDate) " +
           "AND i.deletedAt IS NULL " +
           "ORDER BY i.startDate, i.leaseReference")
    List<Invoice> findByPropertyAndDateRangeOverlap(
        @Param("property") Property property,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find ALL leases for a property regardless of dates
     * Used for statement generation where all leases should be visible
     * (rent_due will be £0 for leases not active in the period)
     */
    @Query("SELECT i FROM Invoice i WHERE i.property = :property " +
           "AND i.invoiceType = 'lease' " +
           "AND i.deletedAt IS NULL " +
           "ORDER BY i.startDate, i.leaseReference")
    List<Invoice> findAllLeasesByProperty(@Param("property") Property property);
}
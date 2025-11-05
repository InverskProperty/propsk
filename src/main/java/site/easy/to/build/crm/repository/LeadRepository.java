package site.easy.to.build.crm.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.Lead;
import site.easy.to.build.crm.entity.LeadType;
import site.easy.to.build.crm.entity.Property;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LeadRepository extends JpaRepository<Lead, Integer> {
    public Lead findByLeadId(int id);

    public List<Lead> findByCustomerCustomerId(int customerId);
    public List<Lead> findByManagerId(int userId);

    public List<Lead> findByEmployeeId(int userId);

    Lead findByMeetingId(String meetingId);

    public List<Lead> findByEmployeeIdOrderByCreatedAtDesc(int employeeId, Pageable pageable);

    public List<Lead> findByManagerIdOrderByCreatedAtDesc(int managerId, Pageable pageable);

    public List<Lead> findByCustomerCustomerIdOrderByCreatedAtDesc(int customerId, Pageable pageable);

    long countByEmployeeId(int employeeId);

    long countByManagerId(int managerId);
    long countByCustomerCustomerId(int customerId);

    void deleteAllByCustomer(Customer customer);

    // ============================================================
    // Property Lead Queries
    // ============================================================

    /**
     * Find leads by lead type
     */
    List<Lead> findByLeadTypeOrderByCreatedAtDesc(LeadType leadType);

    /**
     * Find property rental leads
     */
    @Query("SELECT l FROM Lead l WHERE l.leadType = 'PROPERTY_RENTAL' ORDER BY l.createdAt DESC")
    List<Lead> findPropertyLeads();

    /**
     * Find leads by property
     */
    List<Lead> findByPropertyOrderByCreatedAtDesc(Property property);

    /**
     * Find active property leads (not converted, lost, or archived)
     */
    @Query("SELECT l FROM Lead l WHERE l.leadType = 'PROPERTY_RENTAL' " +
           "AND l.status NOT IN ('converted', 'lost', 'archived') ORDER BY l.createdAt DESC")
    List<Lead> findActivePropertyLeads();

    /**
     * Find property leads by status
     */
    @Query("SELECT l FROM Lead l WHERE l.leadType = 'PROPERTY_RENTAL' AND l.status = :status ORDER BY l.createdAt DESC")
    List<Lead> findPropertyLeadsByStatus(@Param("status") String status);

    /**
     * Find leads for a property by status
     */
    List<Lead> findByPropertyAndStatusOrderByCreatedAtDesc(Property property, String status);

    /**
     * Find active leads for a property
     */
    @Query("SELECT l FROM Lead l WHERE l.property = :property AND l.status NOT IN ('converted', 'lost', 'archived') ORDER BY l.createdAt DESC")
    List<Lead> findActiveLeadsForProperty(@Param("property") Property property);

    /**
     * Find leads by source
     */
    List<Lead> findByLeadSourceOrderByCreatedAtDesc(String leadSource);

    /**
     * Find leads with desired move-in date in range
     */
    @Query("SELECT l FROM Lead l WHERE l.leadType = 'PROPERTY_RENTAL' " +
           "AND l.desiredMoveInDate BETWEEN :startDate AND :endDate ORDER BY l.desiredMoveInDate ASC")
    List<Lead> findByDesiredMoveInDateBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Find leads by budget range (monthly rent within lead's budget)
     */
    @Query("SELECT l FROM Lead l WHERE l.leadType = 'PROPERTY_RENTAL' " +
           "AND (:rent BETWEEN l.budgetMin AND l.budgetMax OR l.budgetMin IS NULL OR l.budgetMax IS NULL) " +
           "ORDER BY l.createdAt DESC")
    List<Lead> findLeadsMatchingBudget(@Param("rent") java.math.BigDecimal rent);

    /**
     * Find converted leads
     */
    @Query("SELECT l FROM Lead l WHERE l.leadType = 'PROPERTY_RENTAL' AND l.status = 'converted' ORDER BY l.convertedAt DESC")
    List<Lead> findConvertedPropertyLeads();

    /**
     * Find leads converted between dates
     */
    @Query("SELECT l FROM Lead l WHERE l.leadType = 'PROPERTY_RENTAL' " +
           "AND l.convertedAt BETWEEN :startDate AND :endDate ORDER BY l.convertedAt DESC")
    List<Lead> findLeadsConvertedBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Count property leads by status
     */
    @Query("SELECT COUNT(l) FROM Lead l WHERE l.leadType = 'PROPERTY_RENTAL' AND l.status = :status")
    long countPropertyLeadsByStatus(@Param("status") String status);

    /**
     * Count active property leads
     */
    @Query("SELECT COUNT(l) FROM Lead l WHERE l.leadType = 'PROPERTY_RENTAL' " +
           "AND l.status NOT IN ('converted', 'lost', 'archived')")
    long countActivePropertyLeads();

    /**
     * Find leads with pets
     */
    @Query("SELECT l FROM Lead l WHERE l.leadType = 'PROPERTY_RENTAL' AND l.hasPets = true " +
           "AND l.status NOT IN ('converted', 'lost', 'archived') ORDER BY l.createdAt DESC")
    List<Lead> findLeadsWithPets();

    /**
     * Find leads by employment status
     */
    @Query("SELECT l FROM Lead l WHERE l.leadType = 'PROPERTY_RENTAL' AND l.employmentStatus = :status ORDER BY l.createdAt DESC")
    List<Lead> findByEmploymentStatus(@Param("status") String status);

    /**
     * Find leads assigned to property owner (through property)
     */
    @Query("SELECT l FROM Lead l WHERE l.property.propertyOwnerId = :ownerId ORDER BY l.createdAt DESC")
    List<Lead> findLeadsByPropertyOwner(@Param("ownerId") Long ownerId);
}

package site.easy.to.build.crm.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerType;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // ===== EXISTING METHODS =====
    Customer findByCustomerId(Long customerId);
    // REMOVED: findByProfileId - Customer entity doesn't have a profileId field
    // Authentication now uses email-based lookup instead (see PropertyOwnerBlockController)
    Customer findByEmail(String email);
    List<Customer> findByUserId(Long userId);
    List<Customer> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    long countByUserId(Long userId);
    List<Customer> findByIsPropertyOwner(Boolean isPropertyOwner);
    List<Customer> findByIsTenant(Boolean isTenant);
    List<Customer> findByIsContractor(Boolean isContractor);

    // ===== SEARCH METHODS =====
    @Query("SELECT c FROM Customer c WHERE " +
           "LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.phone) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.city) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Customer> findByKeyword(@Param("keyword") String keyword);

    List<Customer> findByEmailContainingIgnoreCase(String email);
    List<Customer> findByNameContainingIgnoreCase(String name);

    /**
    * Find all customers who are contractors
    * @return List of contractor customers
    */
    List<Customer> findByIsContractorTrue();
    
    // ✅ ADDED: Missing search method for email functionality
    List<Customer> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String name, String email);

    // ===== CUSTOMER TYPE METHODS =====
    @Query("SELECT c FROM Customer c WHERE c.customerType = :customerType")
    List<Customer> findByCustomerType(@Param("customerType") CustomerType customerType);
    
    @Query("SELECT c FROM Customer c WHERE c.customerType = :customerType AND c.entityId = :propertyId")
    List<Customer> findByCustomerTypeAndEntityId(@Param("customerType") CustomerType customerType, 
                                                 @Param("propertyId") Long propertyId);

    // ===== PAYPROP INTEGRATION METHODS =====
    Customer findByPayPropEntityId(String payPropEntityId);
    
    @Query("SELECT c FROM Customer c WHERE c.payPropSynced = false AND c.customerType IN :customerTypes")
    List<Customer> findByPayPropSyncedFalseAndCustomerTypeIn(@Param("customerTypes") List<CustomerType> customerTypes);

    @Query("SELECT c FROM Customer c WHERE c.payPropSynced = :synced")
    List<Customer> findByPayPropSynced(@Param("synced") Boolean synced);

    @Query("SELECT c FROM Customer c WHERE c.payPropCustomerId IS NOT NULL")
    List<Customer> findByPayPropCustomerIdIsNotNull();

    @Query("SELECT c FROM Customer c WHERE c.payPropCustomerId IS NULL AND c.customerType IN :customerTypes")
    List<Customer> findByPayPropCustomerIdIsNullAndCustomerTypeIn(@Param("customerTypes") List<CustomerType> customerTypes);

    // ===== PROPERTY MANAGEMENT METHODS =====
    @Query("SELECT c FROM Customer c WHERE c.entityType = 'Property' AND c.entityId = :propertyId")
    List<Customer> findByPropertyId(@Param("propertyId") Long propertyId);

    @Query("SELECT c FROM Customer c WHERE c.customerType = :customerType AND c.entityType = 'Property'")
    List<Customer> findByCustomerTypeAndEntityType(@Param("customerType") CustomerType customerType);

    // ===== SPECIALIZED QUERIES =====
    @Query("SELECT c FROM Customer c WHERE c.customerType = 'PROPERTY_OWNER' OR c.isPropertyOwner = true")
    List<Customer> findPropertyOwners();

    @Query("SELECT c FROM Customer c WHERE c.customerType = 'TENANT' OR c.isTenant = true")
    List<Customer> findTenants();

    @Query("SELECT c FROM Customer c WHERE c.customerType = 'CONTRACTOR' OR c.isContractor = true")
    List<Customer> findContractors();

    @Query("SELECT c FROM Customer c WHERE " +
           "(c.customerType = 'PROPERTY_OWNER' OR c.isPropertyOwner = true) AND " +
           "c.city = :city")
    List<Customer> findPropertyOwnersByCity(@Param("city") String city);

    @Query("SELECT c FROM Customer c WHERE " +
           "(c.customerType = 'TENANT' OR c.isTenant = true) AND " +
           "c.description LIKE '%Active%'")
    List<Customer> findActiveTenants();

    @Query("SELECT c FROM Customer c WHERE " +
           "(c.customerType = 'CONTRACTOR' OR c.isContractor = true) AND " +
           "(c.description IS NULL OR c.description NOT LIKE '%Inactive%')")
    List<Customer> findAvailableContractors();

    // ===== REPORTING QUERIES =====
    @Query("SELECT COUNT(c) FROM Customer c WHERE c.customerType = :customerType")
    long countByCustomerType(@Param("customerType") CustomerType customerType);

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.isPropertyOwner = true OR c.customerType = 'PROPERTY_OWNER'")
    long countPropertyOwners();

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.isTenant = true OR c.customerType = 'TENANT'")
    long countTenants();

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.isContractor = true OR c.customerType = 'CONTRACTOR'")
    long countContractors();

    // ✅ ADDED: Additional compatibility methods from paste.txt
    @Query("SELECT c FROM Customer c WHERE c.customerType = 'PROPERTY_OWNER' OR c.isPropertyOwner = true")
    List<Customer> findAllPropertyOwners();
    
    @Query("SELECT c FROM Customer c WHERE c.customerType = 'TENANT' OR c.isTenant = true")
    List<Customer> findAllTenants();
    
    @Query("SELECT c FROM Customer c WHERE c.customerType = 'CONTRACTOR' OR c.isContractor = true")
    List<Customer> findAllContractors();

        // NEW METHODS FOR STATEMENT GENERATION:
    
    /**
     * Find customers assigned to a specific property
     */
    List<Customer> findByAssignedPropertyId(Long propertyId);
    
    /**
     * Find customers by entity type and entity ID (for property assignments)
     */
    List<Customer> findByEntityTypeAndEntityId(String entityType, Long entityId);

    
    
    /**
     * Find active tenants for a property (not moved out)
     * @deprecated Use CustomerPropertyAssignmentService instead - this uses deprecated direct FK
     */
    @Deprecated
    @Query("SELECT c FROM Customer c WHERE c.assignedPropertyId = :propertyId " +
           "AND (c.isTenant = true OR c.customerType = 'TENANT') " +
           "AND (c.moveOutDate IS NULL OR c.moveOutDate > :currentDate)")
    List<Customer> findActiveTenantsForProperty(@Param("propertyId") Long propertyId, 
                                               @Param("currentDate") LocalDate currentDate);

    /**
     * Find customer by OAuth user ID
     * @param oauthUserId The OAuth user's ID from oauth_users table
     * @return Customer if found, null otherwise
     */
    @Query("SELECT c FROM Customer c WHERE c.oauthUserId = :oauthUserId")
    Customer findByOauthUserId(@Param("oauthUserId") Integer oauthUserId);
    
    /**
     * Find all customers for a specific OAuth user
     * @param oauthUserId The OAuth user's ID
     * @return List of customers
     */
    @Query("SELECT c FROM Customer c WHERE c.oauthUserId = :oauthUserId")
    List<Customer> findAllByOauthUserId(@Param("oauthUserId") Integer oauthUserId);
    
    /**
     * Check if customer exists for OAuth user
     * @param oauthUserId The OAuth user's ID
     * @return true if exists
     */
    boolean existsByOauthUserId(Integer oauthUserId);
    
    /**
     * Update OAuth user ID for a customer
     */
    @Modifying
    @Query("UPDATE Customer c SET c.oauthUserId = :oauthUserId WHERE c.customerId = :customerId")
    void updateOauthUserId(@Param("customerId") Long customerId, @Param("oauthUserId") Integer oauthUserId);

}
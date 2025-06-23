package site.easy.to.build.crm.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerType;

import java.util.List;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Integer> {

    // ===== EXISTING METHODS =====
    Customer findByCustomerId(int customerId);
    Customer findByEmail(String email);
    List<Customer> findByUserId(int userId);
    List<Customer> findByUserIdOrderByCreatedAtDesc(int userId, Pageable pageable);
    long countByUserId(int userId);
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

    // ===== CUSTOMER TYPE METHODS =====
    @Query("SELECT c FROM Customer c WHERE c.customerType = :customerType")
    List<Customer> findByCustomerType(@Param("customerType") CustomerType customerType);
    
    @Query("SELECT c FROM Customer c WHERE c.customerType = :customerType AND c.entityId = :propertyId")
    List<Customer> findByCustomerTypeAndEntityId(@Param("customerType") CustomerType customerType, 
                                                 @Param("propertyId") Long propertyId);

    // ===== PAYPROP INTEGRATION METHODS =====
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

    // ===== REPORTING QUERIES (FIXED - NO DUPLICATES) =====
    @Query("SELECT COUNT(c) FROM Customer c WHERE c.customerType = :customerType")
    long countByCustomerType(@Param("customerType") CustomerType customerType);

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.isPropertyOwner = true OR c.customerType = 'PROPERTY_OWNER'")
    long countPropertyOwners();

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.isTenant = true OR c.customerType = 'TENANT'")
    long countTenants();

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.isContractor = true OR c.customerType = 'CONTRACTOR'")
    long countContractors();
}
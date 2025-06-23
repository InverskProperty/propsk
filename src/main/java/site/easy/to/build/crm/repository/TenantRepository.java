package site.easy.to.build.crm.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.AccountType;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.Tenant;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {
    
    // Find by PayProp ID (unique)
    Optional<Tenant> findByPayPropId(String payPropId);
    
    // Find by customer ID
    Optional<Tenant> findByPayPropCustomerId(String payPropCustomerId);
    
    // Find by email address
    Optional<Tenant> findByEmailAddress(String emailAddress);
    
    // Find by property ID
    List<Tenant> findByPropertyId(Long propertyId);
    
    // Find by account type
    List<Tenant> findByAccountType(AccountType accountType);
    
    // Find by status
    List<Tenant> findByStatus(String status);
    
    // Find by tenancy status
    List<Tenant> findByTenancyStatus(String tenancyStatus);
    
    // Find by mobile number
    Optional<Tenant> findByMobileNumber(String mobileNumber);
    
    // Find by first and last name
    List<Tenant> findByFirstNameAndLastName(String firstName, String lastName);
    
    // Find by business name
    List<Tenant> findByBusinessName(String businessName);
    
    // Find active tenants
    List<Tenant> findByStatusOrderByCreatedAtDesc(String status);
    
    // Find tenants by property and status
    List<Tenant> findByPropertyIdAndStatus(Long propertyId, String status);
    
    // Find tenants with move-in date range
    List<Tenant> findByMoveInDateBetween(LocalDate startDate, LocalDate endDate);
    
    // Find tenants with upcoming move-out dates
    List<Tenant> findByMoveOutDateBetween(LocalDate startDate, LocalDate endDate);
    
    // Find tenants by city
    List<Tenant> findByCity(String city);
    
    // Find tenants by postcode
    List<Tenant> findByPostcode(String postcode);
    
    // Find tenants with email notifications enabled
    @Query("SELECT t FROM Tenant t WHERE t.notifyEmail = CASE WHEN :notifyEmail = true THEN 'Y' ELSE 'N' END")
    List<Tenant> findByNotifyEmail(@Param("notifyEmail") Boolean notifyEmail);

    // Find tenants with SMS notifications enabled  
    @Query("SELECT t FROM Tenant t WHERE t.notifyText = CASE WHEN :notifyText = true THEN 'Y' ELSE 'N' END")
    List<Tenant> findByNotifySms(@Param("notifyText") Boolean notifyText);

    // Find tenants with email notifications enabled (String-based)
    List<Tenant> findByNotifyEmail(String notifyEmail);

    // Find tenants with SMS notifications enabled (String-based, using correct field name)
    List<Tenant> findByNotifyText(String notifyText);
    
    // Find tenants requiring guarantor
    List<Tenant> findByGuarantorRequired(String guarantorRequired);
    
    // Find tenants who accept DSS
    List<Tenant> findByDssAccepted(String dssAccepted);
    
    // Find pet owners
    List<Tenant> findByPetOwner(String petOwner);
    
    // Find smokers
    List<Tenant> findBySmoker(String smoker);
    
    // Find by tags containing
    List<Tenant> findByTagsContaining(String tag);
    
    // Find by created by user with pagination
    List<Tenant> findByCreatedByOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    // Count by status
    long countByStatus(String status);
    
    // Count by property
    long countByPropertyId(Long propertyId);
    
    // Count by created by user
    long countByCreatedBy(Long userId);
    
    // Custom search query
    @Query("SELECT t FROM Tenant t WHERE " +
           "(:firstName IS NULL OR LOWER(t.firstName) LIKE LOWER(CONCAT('%', :firstName, '%'))) AND " +
           "(:lastName IS NULL OR LOWER(t.lastName) LIKE LOWER(CONCAT('%', :lastName, '%'))) AND " +
           "(:businessName IS NULL OR LOWER(t.businessName) LIKE LOWER(CONCAT('%', :businessName, '%'))) AND " +
           "(:emailAddress IS NULL OR LOWER(t.emailAddress) LIKE LOWER(CONCAT('%', :emailAddress, '%'))) AND " +
           "(:city IS NULL OR LOWER(t.city) LIKE LOWER(CONCAT('%', :city, '%'))) AND " +
           "(:postcode IS NULL OR LOWER(t.postcode) LIKE LOWER(CONCAT('%', :postcode, '%'))) AND " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:accountType IS NULL OR t.accountType = :accountType) AND " +
           "(:propertyId IS NULL OR t.property.id = :propertyId)")
    List<Tenant> searchTenants(@Param("firstName") String firstName,
                              @Param("lastName") String lastName,
                              @Param("businessName") String businessName,
                              @Param("emailAddress") String emailAddress,
                              @Param("city") String city,
                              @Param("postcode") String postcode,
                              @Param("status") String status,
                              @Param("accountType") AccountType accountType,
                              @Param("propertyId") Long propertyId,
                              Pageable pageable);
    
    // Find tenants with bank account details
    @Query("SELECT t FROM Tenant t WHERE t.hasBankAccount = true")
    List<Tenant> findTenantsWithBankAccount();
    
    // Find tenants by full name (for individual accounts)
    @Query("SELECT t FROM Tenant t WHERE t.accountType = 'INDIVIDUAL' AND " +
           "LOWER(CONCAT(COALESCE(t.firstName, ''), ' ', COALESCE(t.lastName, ''))) " +
           "LIKE LOWER(CONCAT('%', :fullName, '%'))")
    List<Tenant> findByFullNameContaining(@Param("fullName") String fullName);
    
    // Find by full address
    @Query("SELECT t FROM Tenant t WHERE " +
           "LOWER(CONCAT(COALESCE(t.addressLine1, ''), ' ', COALESCE(t.addressLine2, ''), ' ', " +
           "COALESCE(t.addressLine3, ''), ' ', COALESCE(t.city, ''), ' ', COALESCE(t.postcode, ''))) " +
           "LIKE LOWER(CONCAT('%', :address, '%'))")
    List<Tenant> findByFullAddressContaining(@Param("address") String address);
    
    // Find current tenants (no move-out date or future move-out date)
    @Query("SELECT t FROM Tenant t WHERE t.moveOutDate IS NULL OR t.moveOutDate > :currentDate")
    List<Tenant> findCurrentTenants(@Param("currentDate") LocalDate currentDate);
    
    // Find tenants with upcoming rent reviews
    @Query("SELECT t FROM Tenant t WHERE t.moveInDate IS NOT NULL AND " +
           "t.moveInDate <= :cutoffDate")
    List<Tenant> findTenantsForRentReview(@Param("cutoffDate") LocalDate cutoffDate);
    
    // PayProp Integration Methods
    
    // Find tenants not synced to PayProp
    List<Tenant> findByPayPropIdIsNull();
    
    // Find tenants already synced to PayProp
    List<Tenant> findByPayPropIdIsNotNull();
    
    // Find tenants ready for PayProp sync (all required fields present)
    @Query("SELECT t FROM Tenant t WHERE t.payPropId IS NULL AND " +
           "((t.accountType = 'INDIVIDUAL' AND t.firstName IS NOT NULL AND t.firstName != '' AND t.lastName IS NOT NULL AND t.lastName != '') OR " +
           "(t.accountType = 'BUSINESS' AND t.businessName IS NOT NULL AND t.businessName != '')) AND " +
           "t.emailAddress IS NOT NULL AND t.emailAddress != ''")
    List<Tenant> findTenantsReadyForPayPropSync();
    
    // Find tenants with missing PayProp required fields
    @Query("SELECT t FROM Tenant t WHERE t.payPropId IS NULL AND " +
           "((t.accountType = 'INDIVIDUAL' AND (t.firstName IS NULL OR t.firstName = '' OR t.lastName IS NULL OR t.lastName = '')) OR " +
           "(t.accountType = 'BUSINESS' AND (t.businessName IS NULL OR t.businessName = '')) OR " +
           "t.emailAddress IS NULL OR t.emailAddress = '')")
    List<Tenant> findTenantsWithMissingPayPropFields();
    
    // Find active tenants not synced to PayProp
    @Query("SELECT t FROM Tenant t WHERE t.status = 'Active' AND t.payPropId IS NULL")
    List<Tenant> findActiveTenantsPendingSync();

}
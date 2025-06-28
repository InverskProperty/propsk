package site.easy.to.build.crm.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.AccountType;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.PaymentMethod;
import site.easy.to.build.crm.entity.PropertyOwner;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyOwnerRepository extends JpaRepository<PropertyOwner, Long> {
    
    // Find by PayProp ID (unique)
    Optional<PropertyOwner> findByPayPropId(String payPropId);
    
    // Find by customer ID
    Optional<PropertyOwner> findByPayPropCustomerId(String payPropCustomerId);

    Optional<PropertyOwner> findByCustomerCustomerId(Integer customerId);
    
    // Find by property ID
    List<PropertyOwner> findByPropertyId(Long propertyId);
    
    // Find by customer ID FK (legacy)
    List<PropertyOwner> findByCustomerIdFk(Integer customerIdFk);
    
    // Find by email address
    Optional<PropertyOwner> findByEmailAddress(String emailAddress);
    
    // Find by account type
    List<PropertyOwner> findByAccountType(AccountType accountType);
    
    // Find by payment method
    List<PropertyOwner> findByPaymentMethod(PaymentMethod paymentMethod);
    
    // Find by status
    List<PropertyOwner> findByStatus(String status);
    
    // Find by first and last name
    List<PropertyOwner> findByFirstNameAndLastName(String firstName, String lastName);
    
    // Find by business name
    List<PropertyOwner> findByBusinessName(String businessName);
    
    // Find primary owners
    List<PropertyOwner> findByIsPrimaryOwner(String isPrimaryOwner);
    
    // Find primary owner for a property
    @Query("SELECT po FROM PropertyOwner po WHERE po.propertyId = :propertyId AND po.isPrimaryOwner = 'Y'")
    Optional<PropertyOwner> findPrimaryOwnerByPropertyId(@Param("propertyId") Long propertyId);
    
    // Find owners who receive rent payments
    List<PropertyOwner> findByReceiveRentPayments(String receiveRentPayments);
    
    // Find owners who receive statements
    List<PropertyOwner> findByReceiveStatements(String receiveStatements);
    
    // Find emergency contacts
    List<PropertyOwner> findByContactForEmergencies(String contactForEmergencies);
    
    // Find by relationship type
    List<PropertyOwner> findByRelationshipType(String relationshipType);
    
    // Find by mobile number
    Optional<PropertyOwner> findByMobile(String mobile);
    
    // Find by ownership percentage range
    List<PropertyOwner> findByOwnershipPercentageBetween(BigDecimal minPercentage, BigDecimal maxPercentage);
    
    // Find by start date range
    List<PropertyOwner> findByStartDateBetween(LocalDate startDate, LocalDate endDate);
    
    // Find by end date range
    List<PropertyOwner> findByEndDateBetween(LocalDate startDate, LocalDate endDate);
    
    // Find active owners (no end date or future end date)
    @Query("SELECT po FROM PropertyOwner po WHERE po.endDate IS NULL OR po.endDate > :currentDate")
    List<PropertyOwner> findActiveOwners(@Param("currentDate") LocalDate currentDate);
    
    // Find active owners for a property
    @Query("SELECT po FROM PropertyOwner po WHERE po.propertyId = :propertyId AND " +
           "(po.endDate IS NULL OR po.endDate > :currentDate)")
    List<PropertyOwner> findActiveOwnersByPropertyId(@Param("propertyId") Long propertyId, 
                                                    @Param("currentDate") LocalDate currentDate);
    
    // Find by city
    List<PropertyOwner> findByCity(String city);
    
    // Find by country
    List<PropertyOwner> findByCountry(String country);
    
    // Find by email enabled
    List<PropertyOwner> findByEmailEnabled(Boolean emailEnabled);
    
    // Find by payment advice enabled
    List<PropertyOwner> findByPaymentAdviceEnabled(Boolean paymentAdviceEnabled);
    
    // Find with international payment method
    List<PropertyOwner> findByPaymentMethodAndCountryNot(PaymentMethod paymentMethod, String country);
    
    // Find by created by user with pagination
    List<PropertyOwner> findByCreatedByOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    // Count by status
    long countByStatus(String status);
    
    // Count by property
    long countByPropertyId(Long propertyId);
    
    // Count by account type
    long countByAccountType(AccountType accountType);
    
    // Count by payment method
    long countByPaymentMethod(PaymentMethod paymentMethod);
    
    // Custom search query
    @Query("SELECT po FROM PropertyOwner po WHERE " +
           "(:firstName IS NULL OR LOWER(po.firstName) LIKE LOWER(CONCAT('%', :firstName, '%'))) AND " +
           "(:lastName IS NULL OR LOWER(po.lastName) LIKE LOWER(CONCAT('%', :lastName, '%'))) AND " +
           "(:businessName IS NULL OR LOWER(po.businessName) LIKE LOWER(CONCAT('%', :businessName, '%'))) AND " +
           "(:emailAddress IS NULL OR LOWER(po.emailAddress) LIKE LOWER(CONCAT('%', :emailAddress, '%'))) AND " +
           "(:city IS NULL OR LOWER(po.city) LIKE LOWER(CONCAT('%', :city, '%'))) AND " +
           "(:status IS NULL OR po.status = :status) AND " +
           "(:accountType IS NULL OR po.accountType = :accountType) AND " +
           "(:paymentMethod IS NULL OR po.paymentMethod = :paymentMethod) AND " +
           "(:propertyId IS NULL OR po.propertyId = :propertyId)")
    List<PropertyOwner> searchPropertyOwners(@Param("firstName") String firstName,
                                           @Param("lastName") String lastName,
                                           @Param("businessName") String businessName,
                                           @Param("emailAddress") String emailAddress,
                                           @Param("city") String city,
                                           @Param("status") String status,
                                           @Param("accountType") AccountType accountType,
                                           @Param("paymentMethod") PaymentMethod paymentMethod,
                                           @Param("propertyId") Long propertyId,
                                           Pageable pageable);
    
    // ✅ FIXED: Find owners with IBAN (international accounts)
    @Query("SELECT po FROM PropertyOwner po WHERE po.iban IS NOT NULL AND LENGTH(TRIM(po.iban)) > 0")
    List<PropertyOwner> findOwnersWithIban();
    
    // ✅ FIXED: Find owners with SWIFT code
    @Query("SELECT po FROM PropertyOwner po WHERE po.swiftCode IS NOT NULL AND LENGTH(TRIM(po.swiftCode)) > 0")
    List<PropertyOwner> findOwnersWithSwiftCode();
    
    // Find by full name (for individual accounts)
    @Query("SELECT po FROM PropertyOwner po WHERE po.accountType = 'INDIVIDUAL' AND " +
           "LOWER(CONCAT(COALESCE(po.firstName, ''), ' ', COALESCE(po.lastName, ''))) " +
           "LIKE LOWER(CONCAT('%', :fullName, '%'))")
    List<PropertyOwner> findByFullNameContaining(@Param("fullName") String fullName);
    
    // Find ownership distribution for a property
    @Query("SELECT po FROM PropertyOwner po WHERE po.propertyId = :propertyId " +
           "ORDER BY po.ownershipPercentage DESC")
    List<PropertyOwner> findOwnershipDistributionByPropertyId(@Param("propertyId") Long propertyId);
    
    // Validate total ownership percentage for a property
    @Query("SELECT COALESCE(SUM(po.ownershipPercentage), 0) FROM PropertyOwner po " +
           "WHERE po.propertyId = :propertyId AND " +
           "(po.endDate IS NULL OR po.endDate > :currentDate)")
    BigDecimal getTotalOwnershipPercentageByPropertyId(@Param("propertyId") Long propertyId,
                                                       @Param("currentDate") LocalDate currentDate);
    
    // Find owners by bank account details
    @Query("SELECT po FROM PropertyOwner po WHERE " +
           "po.bankAccountNumber = :accountNumber AND po.branchCode = :branchCode")
    List<PropertyOwner> findByBankDetails(@Param("accountNumber") String accountNumber,
                                         @Param("branchCode") String branchCode);

    // ======= PAYPROP INTEGRATION METHODS =======
    
    // Find by customer reference
    List<PropertyOwner> findByCustomerReference(String customerReference);
    
    // Count by created by user
    long countByCreatedBy(Long createdBy);
    
    // Find owners not synced with PayProp
    List<PropertyOwner> findByPayPropIdIsNull();
    
    // Find owners synced with PayProp
    List<PropertyOwner> findByPayPropIdIsNotNull();
    
    // Find by payment method and country
    List<PropertyOwner> findByPaymentMethodAndCountry(PaymentMethod paymentMethod, String country);
    
    // ✅ FIXED: Find property owners ready for PayProp sync
    @Query("SELECT po FROM PropertyOwner po WHERE po.payPropId IS NULL AND " +
           "((po.accountType = 'INDIVIDUAL' AND po.firstName IS NOT NULL AND LENGTH(TRIM(po.firstName)) > 0 AND po.lastName IS NOT NULL AND LENGTH(TRIM(po.lastName)) > 0) OR " +
           "(po.accountType = 'BUSINESS' AND po.businessName IS NOT NULL AND LENGTH(TRIM(po.businessName)) > 0)) AND " +
           "po.emailAddress IS NOT NULL AND LENGTH(TRIM(po.emailAddress)) > 0 AND " +
           "po.paymentMethod IS NOT NULL")
    List<PropertyOwner> findPropertyOwnersReadyForSync();
    
    // ✅ FIXED: Find property owners with missing PayProp required fields
    @Query("SELECT po FROM PropertyOwner po WHERE po.payPropId IS NULL AND " +
           "((po.accountType = 'INDIVIDUAL' AND (po.firstName IS NULL OR LENGTH(TRIM(po.firstName)) = 0 OR po.lastName IS NULL OR LENGTH(TRIM(po.lastName)) = 0)) OR " +
           "(po.accountType = 'BUSINESS' AND (po.businessName IS NULL OR LENGTH(TRIM(po.businessName)) = 0)) OR " +
           "po.emailAddress IS NULL OR LENGTH(TRIM(po.emailAddress)) = 0 OR " +
           "po.paymentMethod IS NULL)")
    List<PropertyOwner> findPropertyOwnersWithMissingPayPropFields();
}
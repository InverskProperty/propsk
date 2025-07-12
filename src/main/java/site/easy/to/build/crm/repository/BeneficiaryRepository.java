// BeneficiaryRepository.java - Repository for payment recipients
package site.easy.to.build.crm.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.Beneficiary;
import site.easy.to.build.crm.entity.BeneficiaryType;
import site.easy.to.build.crm.entity.AccountType;
import site.easy.to.build.crm.entity.PaymentMethod;

import java.util.List;

@Repository
public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {
    
    // ===== PAYPROP INTEGRATION QUERIES =====
    
    /**
     * Find beneficiary by PayProp beneficiary ID (for sync deduplication)
     */
    Beneficiary findByPayPropBeneficiaryId(String payPropBeneficiaryId);
    
    /**
     * Find beneficiary by PayProp customer ID
     */
    Beneficiary findByPayPropCustomerId(String payPropCustomerId);
    
    /**
     * Check if beneficiary exists by PayProp ID
     */
    boolean existsByPayPropBeneficiaryId(String payPropBeneficiaryId);
    
    /**
     * Find all beneficiaries with PayProp IDs (synced beneficiaries)
     */
    List<Beneficiary> findByPayPropBeneficiaryIdIsNotNull();
    
    /**
     * Find beneficiaries missing PayProp IDs (need sync)
     */
    List<Beneficiary> findByPayPropBeneficiaryIdIsNull();

    // ===== BENEFICIARY TYPE QUERIES =====
    
    /**
     * Find beneficiaries by type
     */
    List<Beneficiary> findByBeneficiaryType(BeneficiaryType beneficiaryType);
    
    /**
     * Find active beneficiaries by type
     */
    @Query("SELECT b FROM Beneficiary b WHERE b.beneficiaryType = :beneficiaryType AND b.isActive = 'Y'")
    List<Beneficiary> findActiveBeneficiariesByType(@Param("beneficiaryType") BeneficiaryType beneficiaryType);
    
    /**
     * Find property owner beneficiaries
     */
    @Query("SELECT b FROM Beneficiary b WHERE b.beneficiaryType = 'BENEFICIARY' AND b.isActive = 'Y'")
    List<Beneficiary> findPropertyOwners();
    
    /**
     * Find contractor beneficiaries
     */
    @Query("SELECT b FROM Beneficiary b WHERE b.beneficiaryType IN ('BENEFICIARY', 'GLOBAL_BENEFICIARY') AND b.isActive = 'Y'")
    List<Beneficiary> findContractors();

    // ===== ACCOUNT TYPE QUERIES =====
    
    /**
     * Find beneficiaries by account type
     */
    List<Beneficiary> findByAccountType(AccountType accountType);
    
    /**
     * Find business beneficiaries
     */
    @Query("SELECT b FROM Beneficiary b WHERE b.accountType = 'business' AND b.isActive = 'Y'")
    List<Beneficiary> findBusinessBeneficiaries();
    
    /**
     * Find individual beneficiaries
     */
    @Query("SELECT b FROM Beneficiary b WHERE b.accountType = 'individual' AND b.isActive = 'Y'")
    List<Beneficiary> findIndividualBeneficiaries();

    // ===== PAYMENT METHOD QUERIES =====
    
    /**
     * Find beneficiaries by payment method
     */
    List<Beneficiary> findByPaymentMethod(PaymentMethod paymentMethod);
    
    /**
     * Find beneficiaries using local payments
     */
    @Query("SELECT b FROM Beneficiary b WHERE b.paymentMethod = 'local' AND b.isActive = 'Y'")
    List<Beneficiary> findLocalPaymentBeneficiaries();
    
    /**
     * Find beneficiaries using international payments
     */
    @Query("SELECT b FROM Beneficiary b WHERE b.paymentMethod = 'international' AND b.isActive = 'Y'")
    List<Beneficiary> findInternationalPaymentBeneficiaries();

    // ===== STATUS-BASED QUERIES =====
    
    /**
     * Find active beneficiaries
     */
    List<Beneficiary> findByIsActive(String isActive);
    
    /**
     * Find active beneficiaries ordered by name
     */
    @Query("SELECT b FROM Beneficiary b WHERE b.isActive = 'Y' ORDER BY b.name")
    List<Beneficiary> findActiveBeneficiariesOrderedByName();
    
    /**
     * Find inactive beneficiaries
     */
    @Query("SELECT b FROM Beneficiary b WHERE b.isActive = 'N'")
    List<Beneficiary> findInactiveBeneficiaries();

    // ===== NAME AND CONTACT SEARCHES =====
    
    /**
     * Find beneficiary by exact name
     */
    Beneficiary findByName(String name);
    
    /**
     * Find beneficiaries by name (case insensitive)
     */
    List<Beneficiary> findByNameContainingIgnoreCase(String name);
    
    /**
     * Find beneficiary by email
     */
    Beneficiary findByEmail(String email);
    
    /**
     * Find beneficiaries by email (case insensitive)
     */
    List<Beneficiary> findByEmailContainingIgnoreCase(String email);
    
    /**
     * Find beneficiaries by business name
     */
    List<Beneficiary> findByBusinessNameContainingIgnoreCase(String businessName);
    
    /**
     * Find beneficiaries by first and last name
     */
    List<Beneficiary> findByFirstNameContainingIgnoreCaseAndLastNameContainingIgnoreCase(String firstName, String lastName);

    // ===== LOCATION-BASED QUERIES =====
    
    /**
     * Find beneficiaries by city
     */
    List<Beneficiary> findByCity(String city);
    
    /**
     * Find beneficiaries by city (case insensitive)
     */
    List<Beneficiary> findByCityContainingIgnoreCase(String city);
    
    /**
     * Find beneficiaries by postal code
     */
    List<Beneficiary> findByPostalCode(String postalCode);
    
    /**
     * Find beneficiaries by country
     */
    List<Beneficiary> findByCountryCode(String countryCode);

    // ===== BANK DETAILS QUERIES =====
    
    /**
     * Find beneficiaries with bank account details
     */
    @Query("SELECT b FROM Beneficiary b WHERE b.bankAccountNumber IS NOT NULL AND b.bankAccountNumber != ''")
    List<Beneficiary> findBeneficiariesWithBankDetails();
    
    /**
     * Find beneficiaries missing bank details
     */
    @Query("SELECT b FROM Beneficiary b WHERE b.bankAccountNumber IS NULL OR b.bankAccountNumber = ''")
    List<Beneficiary> findBeneficiariesMissingBankDetails();
    
    /**
     * Find beneficiaries by bank name
     */
    List<Beneficiary> findByBankNameContainingIgnoreCase(String bankName);
    
    /**
     * Find beneficiaries with IBAN
     */
    @Query("SELECT b FROM Beneficiary b WHERE b.iban IS NOT NULL AND b.iban != ''")
    List<Beneficiary> findBeneficiariesWithIban();

    // ===== ADVANCED SEARCH =====
    
    /**
     * Search beneficiaries by multiple criteria
     */
    @Query("SELECT b FROM Beneficiary b WHERE " +
           "(:name IS NULL OR LOWER(b.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
           "(:email IS NULL OR LOWER(b.email) LIKE LOWER(CONCAT('%', :email, '%'))) AND " +
           "(:beneficiaryType IS NULL OR b.beneficiaryType = :beneficiaryType) AND " +
           "(:accountType IS NULL OR b.accountType = :accountType) AND " +
           "(:city IS NULL OR LOWER(b.city) LIKE LOWER(CONCAT('%', :city, '%'))) AND " +
           "(:isActive IS NULL OR b.isActive = :isActive)")
    List<Beneficiary> searchBeneficiaries(@Param("name") String name,
                                         @Param("email") String email,
                                         @Param("beneficiaryType") BeneficiaryType beneficiaryType,
                                         @Param("accountType") AccountType accountType,
                                         @Param("city") String city,
                                         @Param("isActive") String isActive,
                                         Pageable pageable);
    
    /**
     * Search beneficiaries by keyword (name, email, business name)
     */
    @Query("SELECT b FROM Beneficiary b WHERE " +
           "LOWER(b.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(b.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(b.businessName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(b.firstName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(b.lastName) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Beneficiary> searchByKeyword(@Param("keyword") String keyword);

    // ===== REPORTING QUERIES =====
    
    /**
     * Count beneficiaries by type
     */
    @Query("SELECT b.beneficiaryType, COUNT(b) FROM Beneficiary b GROUP BY b.beneficiaryType")
    List<Object[]> countBeneficiariesByType();
    
    /**
     * Count beneficiaries by account type
     */
    @Query("SELECT b.accountType, COUNT(b) FROM Beneficiary b GROUP BY b.accountType")
    List<Object[]> countBeneficiariesByAccountType();
    
    /**
     * Count beneficiaries by payment method
     */
    @Query("SELECT b.paymentMethod, COUNT(b) FROM Beneficiary b GROUP BY b.paymentMethod")
    List<Object[]> countBeneficiariesByPaymentMethod();
    
    /**
     * Count active vs inactive beneficiaries
     */
    @Query("SELECT b.isActive, COUNT(b) FROM Beneficiary b GROUP BY b.isActive")
    List<Object[]> countBeneficiariesByStatus();

    // ===== UTILITY QUERIES =====
    
    /**
     * Check if email exists (for validation)
     */
    boolean existsByEmail(String email);
    
    /**
     * Check if email exists excluding specific ID (for updates)
     */
    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM Beneficiary b WHERE b.email = :email AND b.id != :excludeId")
    boolean existsByEmailExcludingId(@Param("email") String email, @Param("excludeId") Long excludeId);
    
    /**
     * Find recently created beneficiaries
     */
    List<Beneficiary> findByOrderByCreatedAtDesc(Pageable pageable);
    
    /**
     * Find recently updated beneficiaries
     */
    List<Beneficiary> findByOrderByUpdatedAtDesc(Pageable pageable);
    
    /**
     * Count total active beneficiaries
     */
    @Query("SELECT COUNT(b) FROM Beneficiary b WHERE b.isActive = 'Y'")
    long countActiveBeneficiaries();
}
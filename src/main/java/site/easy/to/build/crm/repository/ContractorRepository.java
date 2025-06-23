package site.easy.to.build.crm.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.Contractor;
import site.easy.to.build.crm.entity.Customer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContractorRepository extends JpaRepository<Contractor, Long> {
    
    // Find by company name
    Optional<Contractor> findByCompanyName(String companyName);
    
    // Find by email address
    Optional<Contractor> findByEmailAddress(String emailAddress);
    
    // Find by mobile number
    Optional<Contractor> findByMobileNumber(String mobileNumber);
    
    // Find by phone number
    Optional<Contractor> findByPhoneNumber(String phoneNumber);
    
    // Find by contact person
    List<Contractor> findByContactPerson(String contactPerson);
    
    // Find by status
    List<Contractor> findByStatus(String status);
    
    // Find active contractors
    List<Contractor> findByStatusOrderByCreatedAtDesc(String status);
    
    // Find preferred contractors
    List<Contractor> findByPreferredContractor(String preferredContractor);
    
    // Find 24/7 available contractors
    List<Contractor> findByAvailable247(String available247);
    
    // Find contractors with emergency contact
    List<Contractor> findByEmergencyContact(String emergencyContact);
    
    // Find by city
    List<Contractor> findByCity(String city);
    
    // Find by postcode
    List<Contractor> findByPostcode(String postcode);
    
    // Find by county
    List<Contractor> findByCounty(String county);
    
    // Find by tags containing
    List<Contractor> findByTagsContaining(String tag);
    
    // Find Gas Safe certified contractors
    @Query("SELECT c FROM Contractor c WHERE c.gasSafeNumber IS NOT NULL AND c.gasSafeNumber != '' " +
           "AND c.gasSafeExpiry IS NOT NULL AND c.gasSafeExpiry > :currentDate")
    List<Contractor> findGasSafeCertified(@Param("currentDate") LocalDateTime currentDate);
    
    // Find NICEIC certified contractors
    @Query("SELECT c FROM Contractor c WHERE c.niceicNumber IS NOT NULL AND c.niceicNumber != '' " +
           "AND c.niceicExpiry IS NOT NULL AND c.niceicExpiry > :currentDate")
    List<Contractor> findNiceicCertified(@Param("currentDate") LocalDateTime currentDate);
    
    // Find contractors with valid insurance
    @Query("SELECT c FROM Contractor c WHERE c.insuranceExpiry IS NOT NULL AND c.insuranceExpiry > :currentDate")
    List<Contractor> findWithValidInsurance(@Param("currentDate") LocalDateTime currentDate);
    
    // Find contractors by hourly rate range
    List<Contractor> findByHourlyRateBetween(BigDecimal minRate, BigDecimal maxRate);
    
    // Find contractors by call out charge range
    List<Contractor> findByCallOutChargeBetween(BigDecimal minCharge, BigDecimal maxCharge);
    
    // Find contractors by minimum charge range
    List<Contractor> findByMinimumChargeBetween(BigDecimal minCharge, BigDecimal maxCharge);
    
    // Find contractors by rating range
    List<Contractor> findByRatingBetween(BigDecimal minRating, BigDecimal maxRating);
    
    // Find contractors by average response time
    List<Contractor> findByAverageResponseTimeLessThanEqual(Integer maxResponseTime);
    
    // Find contractors with completed jobs
    @Query("SELECT c FROM Contractor c WHERE c.completedJobs IS NOT NULL AND c.completedJobs > 0")
    List<Contractor> findWithCompletedJobs();
    
    // Find contractors by completion rate
    @Query("SELECT c FROM Contractor c WHERE c.totalJobs IS NOT NULL AND c.totalJobs > 0 " +
           "AND (CAST(c.completedJobs AS double) / CAST(c.totalJobs AS double) * 100) >= :minCompletionRate")
    List<Contractor> findByCompletionRateGreaterThanEqual(@Param("minCompletionRate") Double minCompletionRate);
    
    // Find contractors by working days
    List<Contractor> findByWorkingDaysContaining(String day);
    
    // Find contractors by working hours
    List<Contractor> findByWorkingHours(String workingHours);
    
    // Find contractors by payment terms
    List<Contractor> findByPaymentTerms(String paymentTerms);
    
    // Find by created by user with pagination
    List<Contractor> findByCreatedByOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    // Count by status
    long countByStatus(String status);
    
    // Count by preferred contractor status
    long countByPreferredContractor(String preferredContractor);
    
    // Count by created by user
    long countByCreatedBy(Long userId);
    
    // Custom search query
    @Query("SELECT c FROM Contractor c WHERE " +
           "(:companyName IS NULL OR LOWER(c.companyName) LIKE LOWER(CONCAT('%', :companyName, '%'))) AND " +
           "(:contactPerson IS NULL OR LOWER(c.contactPerson) LIKE LOWER(CONCAT('%', :contactPerson, '%'))) AND " +
           "(:emailAddress IS NULL OR LOWER(c.emailAddress) LIKE LOWER(CONCAT('%', :emailAddress, '%'))) AND " +
           "(:city IS NULL OR LOWER(c.city) LIKE LOWER(CONCAT('%', :city, '%'))) AND " +
           "(:postcode IS NULL OR LOWER(c.postcode) LIKE LOWER(CONCAT('%', :postcode, '%'))) AND " +
           "(:status IS NULL OR c.status = :status) AND " +
           "(:available247 IS NULL OR c.available247 = :available247) AND " +
           "(:preferredContractor IS NULL OR c.preferredContractor = :preferredContractor)")
    List<Contractor> searchContractors(@Param("companyName") String companyName,
                                      @Param("contactPerson") String contactPerson,
                                      @Param("emailAddress") String emailAddress,
                                      @Param("city") String city,
                                      @Param("postcode") String postcode,
                                      @Param("status") String status,
                                      @Param("available247") String available247,
                                      @Param("preferredContractor") String preferredContractor,
                                      Pageable pageable);
    
    // Find contractors with expiring certifications
    @Query("SELECT c FROM Contractor c WHERE " +
           "(c.gasSafeExpiry IS NOT NULL AND c.gasSafeExpiry BETWEEN :startDate AND :endDate) OR " +
           "(c.niceicExpiry IS NOT NULL AND c.niceicExpiry BETWEEN :startDate AND :endDate) OR " +
           "(c.insuranceExpiry IS NOT NULL AND c.insuranceExpiry BETWEEN :startDate AND :endDate)")
    List<Contractor> findWithExpiringCertifications(@Param("startDate") LocalDateTime startDate,
                                                   @Param("endDate") LocalDateTime endDate);
    
    // Find contractors by service area (using address components)
    @Query("SELECT c FROM Contractor c WHERE " +
           "LOWER(CONCAT(COALESCE(c.addressLine1, ''), ' ', COALESCE(c.addressLine2, ''), ' ', " +
           "COALESCE(c.addressLine3, ''), ' ', COALESCE(c.city, ''), ' ', COALESCE(c.postcode, ''))) " +
           "LIKE LOWER(CONCAT('%', :area, '%'))")
    List<Contractor> findByServiceArea(@Param("area") String area);
    
    // Find top rated contractors
    @Query("SELECT c FROM Contractor c WHERE c.rating IS NOT NULL ORDER BY c.rating DESC")
    List<Contractor> findTopRatedContractors(Pageable pageable);
    
    // Find contractors by insurance amount range
    List<Contractor> findByInsuranceAmountBetween(BigDecimal minAmount, BigDecimal maxAmount);
    
    // Find contractors with specific Gas Safe number
    Optional<Contractor> findByGasSafeNumber(String gasSafeNumber);
    
    // Find contractors with specific NICEIC number
    Optional<Contractor> findByNiceicNumber(String niceicNumber);
    
    // Find contractors with specific VAT number
    Optional<Contractor> findByVatNumber(String vatNumber);
    
    // Find contractors with specific company registration
    Optional<Contractor> findByCompanyRegistration(String companyRegistration);
    
    // Find contractors available for emergency work
    @Query("SELECT c FROM Contractor c WHERE c.emergencyContact = 'Y' AND c.status = 'Active'")
    List<Contractor> findAvailableForEmergency();
    
    // Find contractors by job title
    List<Contractor> findByJobTitle(String jobTitle);
    
    // Find contractors with bank account details
    @Query("SELECT c FROM Contractor c WHERE c.accountNumber IS NOT NULL AND c.sortCode IS NOT NULL")
    List<Contractor> findWithBankDetails();
    
    // Find contractors by bank name
    List<Contractor> findByBankName(String bankName);
    
    // Find contractors with website
    @Query("SELECT c FROM Contractor c WHERE c.website IS NOT NULL AND c.website != ''")
    List<Contractor> findWithWebsite();
    
    // Find contractors by full address
    @Query("SELECT c FROM Contractor c WHERE " +
           "LOWER(CONCAT(COALESCE(c.addressLine1, ''), ' ', COALESCE(c.addressLine2, ''), ' ', " +
           "COALESCE(c.addressLine3, ''), ' ', COALESCE(c.city, ''), ' ', COALESCE(c.postcode, ''))) " +
           "LIKE LOWER(CONCAT('%', :address, '%'))")
    List<Contractor> findByFullAddressContaining(@Param("address") String address);
    
    // Find contractors with high completion rates and ratings
    @Query("SELECT c FROM Contractor c WHERE c.totalJobs IS NOT NULL AND c.totalJobs > 0 " +
           "AND (CAST(c.completedJobs AS double) / CAST(c.totalJobs AS double) * 100) >= :minCompletionRate " +
           "AND (c.rating IS NULL OR c.rating >= :minRating) " +
           "ORDER BY c.rating DESC, (CAST(c.completedJobs AS double) / CAST(c.totalJobs AS double)) DESC")
    List<Contractor> findHighPerformingContractors(@Param("minCompletionRate") Double minCompletionRate,
                                                  @Param("minRating") BigDecimal minRating,
                                                  Pageable pageable);
    
    // Find contractors needing certification renewal within days
    @Query("SELECT c FROM Contractor c WHERE " +
           "(c.gasSafeExpiry IS NOT NULL AND c.gasSafeExpiry <= :renewalDate) OR " +
           "(c.niceicExpiry IS NOT NULL AND c.niceicExpiry <= :renewalDate) OR " +
           "(c.insuranceExpiry IS NOT NULL AND c.insuranceExpiry <= :renewalDate)")
    List<Contractor> findNeedingCertificationRenewal(@Param("renewalDate") LocalDateTime renewalDate);

    Optional<Contractor> findByCustomer(Customer customer);  
    List<Contractor> findByCustomerCustomerId(Integer customerId);
}
package site.easy.to.build.crm.service.property;

import site.easy.to.build.crm.entity.AccountType;
import site.easy.to.build.crm.entity.Tenant;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TenantService {

    Tenant findById(Long id);
    
    Optional<Tenant> findByPayPropId(String payPropId);
    
    Optional<Tenant> findByPayPropCustomerId(String payPropCustomerId);
    
    Optional<Tenant> findByEmailAddress(String emailAddress);
    
    Optional<Tenant> findByMobileNumber(String mobileNumber);
    
    List<Tenant> findByPropertyId(Long propertyId);
    
    List<Tenant> findByAccountType(AccountType accountType);
    
    List<Tenant> findByStatus(String status);
    
    List<Tenant> findByTenancyStatus(String tenancyStatus);
    
    List<Tenant> findByFirstNameAndLastName(String firstName, String lastName);
    
    List<Tenant> findByBusinessName(String businessName);
    
    List<Tenant> findActiveTenants();
    
    List<Tenant> findActiveTenantsForProperty(Long propertyId);
    
    List<Tenant> findCurrentTenants();
    
    List<Tenant> findByCity(String city);
    
    List<Tenant> findByPostcode(String postcode);
    
    List<Tenant> findTenantsByMoveInDateRange(LocalDate startDate, LocalDate endDate);
    
    List<Tenant> findTenantsWithUpcomingMoveOut(LocalDate endDate);
    
    List<Tenant> findTenantsWithEmailNotifications();
    
    List<Tenant> findTenantsWithSmsNotifications();
    
    List<Tenant> findTenantsRequiringGuarantor();
    
    List<Tenant> findDssAcceptedTenants();
    
    List<Tenant> findPetOwnerTenants();
    
    List<Tenant> findSmokerTenants();
    
    List<Tenant> findTenantsWithBankAccount();
    
    List<Tenant> searchByFullName(String fullName);
    
    List<Tenant> searchByAddress(String address);
    
    List<Tenant> findTenantsForRentReview(LocalDate reviewDate);
    
    List<Tenant> getRecentTenants(Long userId, int limit);
    
    List<Tenant> searchTenants(String firstName, String lastName, String businessName, 
                              String emailAddress, String city, String postcode, 
                              String status, AccountType accountType, Long propertyId, int limit);
    
    List<Tenant> findAll();
    
    Tenant save(Tenant tenant);
    
    void delete(Tenant tenant);
    
    void deleteById(Long id);
    
    long countByStatus(String status);
    
    long countByPropertyId(Long propertyId);
    
    long countByCreatedBy(Long userId);
    
    long getTotalTenants();
    
    // Business logic methods
    boolean isTenantActive(Long tenantId);
    
    void markTenantAsActive(Long tenantId);
    
    void markTenantAsInactive(Long tenantId);
    
    void setMoveOutDate(Long tenantId, LocalDate moveOutDate);
    
    List<Tenant> findExpiredTenancies(LocalDate currentDate);
    
    boolean hasValidBankDetails(Long tenantId);
    
    void updateNotificationPreferences(Long tenantId, Boolean emailEnabled, Boolean smsEnabled);
    
    // PayProp sync methods - ADD THESE
    List<Tenant> findTenantsReadyForPayPropSync();
    List<Tenant> findByPayPropIdIsNull();
    List<Tenant> findByPayPropIdIsNotNull();
}
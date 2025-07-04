package site.easy.to.build.crm.service.property;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.AccountType;
import site.easy.to.build.crm.entity.Tenant;
import site.easy.to.build.crm.repository.TenantRepository;
import java.util.stream.Collectors;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class TenantServiceImpl implements TenantService {

    private final TenantRepository tenantRepository;

    public TenantServiceImpl(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public Tenant findById(Long id) {
        return tenantRepository.findById(id).orElse(null);
    }

    // Add this method to TenantServiceImpl.java if it doesn't exist:
    @Override
    public long getTotalCount() {
        return tenantRepository.count();
    }
    
    @Override
    public List<Tenant> findByIdIn(List<Long> ids) {
        return tenantRepository.findByIdIn(ids);
    }
    
    @Override
    public Optional<Tenant> findByPayPropId(String payPropId) {
        return tenantRepository.findByPayPropId(payPropId);
    }

    @Override
    public Optional<Tenant> findByPayPropCustomerId(String payPropCustomerId) {
        return tenantRepository.findByPayPropCustomerId(payPropCustomerId);
    }

    @Override
    public Optional<Tenant> findByEmailAddress(String emailAddress) {
        return tenantRepository.findByEmailAddress(emailAddress);
    }

    @Override
    public Optional<Tenant> findByMobileNumber(String mobileNumber) {
        return tenantRepository.findByMobileNumber(mobileNumber);
    }

    @Override
    public List<Tenant> findByPropertyId(Long propertyId) {
        return tenantRepository.findByPropertyId(propertyId);
    }

    @Override
    public List<Tenant> findByAccountType(AccountType accountType) {
        return tenantRepository.findByAccountType(accountType);
    }

    @Override
    public List<Tenant> findByStatus(String status) {
        return tenantRepository.findByStatus(status);
    }

    @Override
    public List<Tenant> findByTenancyStatus(String tenancyStatus) {
        return tenantRepository.findByTenancyStatus(tenancyStatus);
    }

    @Override
    public List<Tenant> findByFirstNameAndLastName(String firstName, String lastName) {
        return tenantRepository.findByFirstNameAndLastName(firstName, lastName);
    }

    @Override
    public List<Tenant> findByBusinessName(String businessName) {
        return tenantRepository.findByBusinessName(businessName);
    }

    @Override
    public List<Tenant> findActiveTenants() {
        return tenantRepository.findByStatusOrderByCreatedAtDesc("Active");
    }

    @Override
    public List<Tenant> findActiveTenantsForProperty(Long propertyId) {
        return tenantRepository.findByPropertyIdAndStatus(propertyId, "Active");
    }

    @Override
    public List<Tenant> findCurrentTenants() {
        return tenantRepository.findCurrentTenants(LocalDate.now());
    }

    @Override
    public List<Tenant> findByCity(String city) {
        return tenantRepository.findByCity(city);
    }

    @Override
    public List<Tenant> findByPostcode(String postcode) {
        return tenantRepository.findByPostcode(postcode);
    }

    @Override
    public List<Tenant> findTenantsByMoveInDateRange(LocalDate startDate, LocalDate endDate) {
        return tenantRepository.findByMoveInDateBetween(startDate, endDate);
    }

    @Override
    public List<Tenant> findTenantsWithUpcomingMoveOut(LocalDate endDate) {
        return tenantRepository.findByMoveOutDateBetween(LocalDate.now(), endDate);
    }

    @Override
    public List<Tenant> findTenantsWithEmailNotifications() {
        return tenantRepository.findByNotifyEmail(true);
    }

    @Override
    public List<Tenant> findTenantsWithSmsNotifications() {
        return tenantRepository.findByNotifySms(true);
    }

    @Override
    public List<Tenant> findTenantsRequiringGuarantor() {
        return tenantRepository.findByGuarantorRequired("Y");
    }

    @Override
    public List<Tenant> findDssAcceptedTenants() {
        return tenantRepository.findByDssAccepted("Y");
    }

    @Override
    public List<Tenant> findPetOwnerTenants() {
        return tenantRepository.findByPetOwner("Y");
    }

    @Override
    public List<Tenant> findSmokerTenants() {
        return tenantRepository.findBySmoker("Y");
    }

    @Override
    public List<Tenant> findTenantsWithBankAccount() {
        return tenantRepository.findTenantsWithBankAccount();
    }

    @Override
    public List<Tenant> searchByFullName(String fullName) {
        return tenantRepository.findByFullNameContaining(fullName);
    }

    @Override
    public List<Tenant> searchByAddress(String address) {
        return tenantRepository.findByFullAddressContaining(address);
    }

    @Override
    public List<Tenant> findTenantsForRentReview(LocalDate reviewDate) {
        return tenantRepository.findTenantsForRentReview(reviewDate);
    }

    @Override
    public List<Tenant> getRecentTenants(Long userId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return tenantRepository.findByCreatedByOrderByCreatedAtDesc(userId, pageable);
    }

    @Override
    public List<Tenant> searchTenants(String firstName, String lastName, String businessName, 
                                     String emailAddress, String city, String postcode, 
                                     String status, AccountType accountType, Long propertyId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return tenantRepository.searchTenants(firstName, lastName, businessName, emailAddress, 
                                            city, postcode, status, accountType, propertyId, pageable);
    }

    @Override
    public List<Tenant> findAll() {
        return tenantRepository.findAll();
    }

    @Override
    public Tenant save(Tenant tenant) {
        return tenantRepository.save(tenant);
    }

    @Override
    public void delete(Tenant tenant) {
        tenantRepository.delete(tenant);
    }

    @Override
    public void deleteById(Long id) {
        tenantRepository.deleteById(id);
    }

    @Override
    public long countByStatus(String status) {
        return tenantRepository.countByStatus(status);
    }

    @Override
    public long countByPropertyId(Long propertyId) {
        return tenantRepository.countByPropertyId(propertyId);
    }

    @Override
    public long countByCreatedBy(Long userId) {
        return tenantRepository.countByCreatedBy(userId);
    }

    @Override
    public long getTotalTenants() {
        return tenantRepository.count();
    }

    // Business logic methods
    @Override
    public boolean isTenantActive(Long tenantId) {
        Tenant tenant = findById(tenantId);
        return tenant != null && "Active".equalsIgnoreCase(tenant.getStatus());
    }

    @Override
    public void markTenantAsActive(Long tenantId) {
        Tenant tenant = findById(tenantId);
        if (tenant != null) {
            tenant.setStatus("Active");
            tenant.setTenancyStatus("Active");
            save(tenant);
        }
    }

    @Override
    public void markTenantAsInactive(Long tenantId) {
        Tenant tenant = findById(tenantId);
        if (tenant != null) {
            tenant.setStatus("Inactive");
            tenant.setTenancyStatus("Ended");
            save(tenant);
        }
    }

    @Override
    public void setMoveOutDate(Long tenantId, LocalDate moveOutDate) {
        Tenant tenant = findById(tenantId);
        if (tenant != null) {
            tenant.setMoveOutDate(moveOutDate);
            if (moveOutDate != null && !moveOutDate.isAfter(LocalDate.now())) {
                markTenantAsInactive(tenantId);
            }
            save(tenant);
        }
    }

    @Override
    public List<Tenant> findExpiredTenancies(LocalDate currentDate) {
        return tenantRepository.findByMoveOutDateBetween(LocalDate.of(1900, 1, 1), currentDate);
    }

    @Override
    public boolean hasValidBankDetails(Long tenantId) {
        Tenant tenant = findById(tenantId);
        return tenant != null && 
               tenant.getAccountNumber() != null && !tenant.getAccountNumber().trim().isEmpty() &&
               tenant.getSortCode() != null && !tenant.getSortCode().trim().isEmpty() &&
               tenant.getAccountName() != null && !tenant.getAccountName().trim().isEmpty();
    }

    @Override
    public void updateNotificationPreferences(Long tenantId, Boolean emailEnabled, Boolean smsEnabled) {
        Tenant tenant = findById(tenantId);
        if (tenant != null) {
            tenant.setNotifyEmailFromBoolean(emailEnabled);
            tenant.setNotifyTextFromBoolean(smsEnabled);
            save(tenant);
        }
    }

    // PayProp sync methods - ADD THESE IMPLEMENTATIONS
    @Override
    public List<Tenant> findTenantsReadyForPayPropSync() {
        return tenantRepository.findTenantsReadyForPayPropSync();
    }

    @Override
    public List<Tenant> findByPayPropIdIsNull() {
        return tenantRepository.findByPayPropIdIsNull();
    }

    @Override
    public List<Tenant> findByPayPropIdIsNotNull() {
        return tenantRepository.findByPayPropIdIsNotNull();
    }
}
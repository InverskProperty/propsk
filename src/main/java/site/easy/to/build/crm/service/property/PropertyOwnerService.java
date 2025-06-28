package site.easy.to.build.crm.service.property;

import site.easy.to.build.crm.entity.PropertyOwner;
import site.easy.to.build.crm.entity.AccountType;
import site.easy.to.build.crm.entity.PaymentMethod;

import java.util.List;
import java.util.Optional;

public interface PropertyOwnerService {

    // Core CRUD operations
    PropertyOwner findById(Long id);
    List<PropertyOwner> findAll();
    PropertyOwner save(PropertyOwner propertyOwner);
    void delete(PropertyOwner propertyOwner);
    void deleteById(Long id);

    // PayProp integration methods
    Optional<PropertyOwner> findByPayPropId(String payPropId);
    Optional<PropertyOwner> findByPayPropCustomerId(String payPropCustomerId);
    Optional<PropertyOwner> findByEmailAddress(String emailAddress);
    List<PropertyOwner> findByCustomerReference(String customerReference);
    
    // 🔧 FIXED: Added missing PayProp sync methods
    List<PropertyOwner> findByPayPropIdIsNull();
    List<PropertyOwner> findByPayPropIdIsNotNull();
    
    // Search methods
    List<PropertyOwner> findByAccountType(AccountType accountType);
    List<PropertyOwner> findByPaymentMethod(PaymentMethod paymentMethod);
    List<PropertyOwner> findByFirstNameAndLastName(String firstName, String lastName);
    List<PropertyOwner> findByBusinessName(String businessName);
    List<PropertyOwner> searchByName(String name);
    
    // User-based queries
    List<PropertyOwner> getRecentPropertyOwners(Long userId, int limit);
    long countByCreatedBy(Long userId);
    long getTotalPropertyOwners();
    
    // PayProp sync methods
    List<PropertyOwner> findPropertyOwnersNeedingSync();
    void markPropertyOwnerAsSynced(Long propertyOwnerId, String payPropId);
    List<PropertyOwner> findPropertyOwnersByPayPropSyncStatus(boolean synced);
    
    // PayProp validation methods
    List<PropertyOwner> findPropertyOwnersReadyForSync();
    boolean isPropertyOwnerReadyForPayPropSync(Long propertyOwnerId);
    List<PropertyOwner> findPropertyOwnersWithMissingPayPropFields();
    
    // Business logic methods
    boolean hasValidBankDetails(Long propertyOwnerId);
    boolean isInternationalPayment(Long propertyOwnerId);
    List<PropertyOwner> findByPaymentMethodAndCountry(PaymentMethod paymentMethod, String country);
}
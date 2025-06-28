package site.easy.to.build.crm.service.property;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.PropertyOwner;
import site.easy.to.build.crm.entity.AccountType;
import site.easy.to.build.crm.entity.PaymentMethod;
import site.easy.to.build.crm.repository.PropertyOwnerRepository;

import java.util.List;
import java.util.Optional;

@Service
public class PropertyOwnerServiceImpl implements PropertyOwnerService {

    private final PropertyOwnerRepository propertyOwnerRepository;

    @Autowired
    public PropertyOwnerServiceImpl(PropertyOwnerRepository propertyOwnerRepository) {
        this.propertyOwnerRepository = propertyOwnerRepository;
    }

    // Core CRUD operations
    @Override
    public PropertyOwner findById(Long id) {
        return propertyOwnerRepository.findById(id).orElse(null);
    }

    @Override
    public List<PropertyOwner> findAll() {
        return propertyOwnerRepository.findAll();
    }

    @Override
    public PropertyOwner save(PropertyOwner propertyOwner) {
        return propertyOwnerRepository.save(propertyOwner);
    }

    @Override
    public void delete(PropertyOwner propertyOwner) {
        propertyOwnerRepository.delete(propertyOwner);
    }

    @Override
    public void deleteById(Long id) {
        propertyOwnerRepository.deleteById(id);
    }

    // PayProp integration methods
    @Override
    public Optional<PropertyOwner> findByPayPropId(String payPropId) {
        return propertyOwnerRepository.findByPayPropId(payPropId);
    }

    @Override
    public Optional<PropertyOwner> findByPayPropCustomerId(String payPropCustomerId) {
        return propertyOwnerRepository.findByPayPropCustomerId(payPropCustomerId);
    }

    @Override
    public List<PropertyOwner> findByCustomerReference(String customerReference) {
        return propertyOwnerRepository.findByCustomerReference(customerReference);
    }

    // 🔧 FIXED: Added missing PayProp sync methods
    @Override
    public List<PropertyOwner> findByPayPropIdIsNull() {
        return propertyOwnerRepository.findByPayPropIdIsNull();
    }

    @Override
    public List<PropertyOwner> findByPayPropIdIsNotNull() {
        return propertyOwnerRepository.findByPayPropIdIsNotNull();
    }

    // Search methods
    @Override
    public List<PropertyOwner> findByAccountType(AccountType accountType) {
        return propertyOwnerRepository.findByAccountType(accountType);
    }

    @Override
    public List<PropertyOwner> findByPaymentMethod(PaymentMethod paymentMethod) {
        return propertyOwnerRepository.findByPaymentMethod(paymentMethod);
    }

    @Override
    public List<PropertyOwner> findByFirstNameAndLastName(String firstName, String lastName) {
        return propertyOwnerRepository.findByFirstNameAndLastName(firstName, lastName);
    }

    @Override
    public List<PropertyOwner> findByBusinessName(String businessName) {
        return propertyOwnerRepository.findByBusinessName(businessName);
    }

    @Override
    public Optional<PropertyOwner> findByEmailAddress(String emailAddress) {
        return propertyOwnerRepository.findByEmailAddress(emailAddress);
    }

    @Override
    public List<PropertyOwner> searchByName(String name) {
        return propertyOwnerRepository.findByFullNameContaining(name);
    }

    // User-based queries
    @Override
    public List<PropertyOwner> getRecentPropertyOwners(Long userId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return propertyOwnerRepository.findByCreatedByOrderByCreatedAtDesc(userId, pageable);
    }

    @Override
    public long countByCreatedBy(Long userId) {
        return propertyOwnerRepository.countByCreatedBy(userId);
    }

    @Override
    public long getTotalPropertyOwners() {
        return propertyOwnerRepository.count();
    }

    // PayProp sync methods
    @Override
    public List<PropertyOwner> findPropertyOwnersNeedingSync() {
        return propertyOwnerRepository.findByPayPropIdIsNull();
    }

    @Override
    public void markPropertyOwnerAsSynced(Long propertyOwnerId, String payPropId) {
        PropertyOwner owner = findById(propertyOwnerId);
        if (owner != null) {
            owner.setPayPropId(payPropId);
            save(owner);
        }
    }

    @Override
    public List<PropertyOwner> findPropertyOwnersByPayPropSyncStatus(boolean synced) {
        return synced ? propertyOwnerRepository.findByPayPropIdIsNotNull() : 
                       propertyOwnerRepository.findByPayPropIdIsNull();
    }

    // PayProp validation methods
    @Override
    public List<PropertyOwner> findPropertyOwnersReadyForSync() {
        return propertyOwnerRepository.findPropertyOwnersReadyForSync();
    }

    @Override
    public boolean isPropertyOwnerReadyForPayPropSync(Long propertyOwnerId) {
        PropertyOwner owner = findById(propertyOwnerId);
        if (owner == null) {
            return false;
        }
        
        // Check account type specific requirements
        if (owner.getAccountType() == AccountType.INDIVIDUAL) {
            if (owner.getFirstName() == null || owner.getFirstName().trim().isEmpty() ||
                owner.getLastName() == null || owner.getLastName().trim().isEmpty()) {
                return false;
            }
        } else {
            if (owner.getBusinessName() == null || owner.getBusinessName().trim().isEmpty()) {
                return false;
            }
        }
        
        // Check payment method specific requirements
        if (owner.getPaymentMethod() == PaymentMethod.LOCAL) {
            return owner.getBankAccountName() != null && 
                   owner.getBankAccountNumber() != null && 
                   owner.getBranchCode() != null;
        }
        
        if (owner.getPaymentMethod() == PaymentMethod.INTERNATIONAL) {
            // Address required for international
            boolean hasAddress = owner.getAddressLine1() != null && 
                               owner.getCity() != null && 
                               owner.getCountry() != null;
            
            // Either IBAN or account number + SWIFT required
            boolean hasIban = owner.getIban() != null && !owner.getIban().trim().isEmpty();
            boolean hasAccountAndSwift = owner.getInternationalAccountNumber() != null && 
                                       owner.getSwiftCode() != null;
            
            return hasAddress && (hasIban || hasAccountAndSwift);
        }
        
        return true; // CHEQUE method has minimal requirements
    }

    @Override
    public List<PropertyOwner> findPropertyOwnersWithMissingPayPropFields() {
        return propertyOwnerRepository.findPropertyOwnersWithMissingPayPropFields();
    }

    // Business logic methods
    @Override
    public boolean hasValidBankDetails(Long propertyOwnerId) {
        PropertyOwner owner = findById(propertyOwnerId);
        if (owner == null) {
            return false;
        }
        
        if (owner.getPaymentMethod() == PaymentMethod.LOCAL) {
            return owner.getBankAccountName() != null && 
                   owner.getBankAccountNumber() != null && 
                   owner.getBranchCode() != null;
        }
        
        if (owner.getPaymentMethod() == PaymentMethod.INTERNATIONAL) {
            boolean hasIban = owner.getIban() != null && !owner.getIban().trim().isEmpty();
            boolean hasAccountAndSwift = owner.getInternationalAccountNumber() != null && 
                                       owner.getSwiftCode() != null;
            return hasIban || hasAccountAndSwift;
        }
        
        return owner.getPaymentMethod() == PaymentMethod.CHEQUE; // Cheque doesn't need bank details
    }

    @Override
    public boolean isInternationalPayment(Long propertyOwnerId) {
        PropertyOwner owner = findById(propertyOwnerId);
        return owner != null && owner.getPaymentMethod() == PaymentMethod.INTERNATIONAL;
    }

    @Override
    public List<PropertyOwner> findByPaymentMethodAndCountry(PaymentMethod paymentMethod, String country) {
        return propertyOwnerRepository.findByPaymentMethodAndCountry(paymentMethod, country);
    }
}
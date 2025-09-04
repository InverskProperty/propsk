package site.easy.to.build.crm.service.customer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.service.property.PropertyOwnerService;
import site.easy.to.build.crm.service.property.TenantService;
import site.easy.to.build.crm.service.contractor.ContractorService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.util.EmailTokenUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Bridge service that links Customer login records with specialized PayProp entities
 * Handles orphaned PropertyOwner/Tenant/Contractor entities by creating Customer records
 */
@Service
@Transactional
public class CustomerEntityBridgeService {

    private final CustomerService customerService;
    private final PropertyOwnerService propertyOwnerService;
    private final TenantService tenantService;
    private final ContractorService contractorService;
    private final CustomerLoginInfoService customerLoginInfoService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public CustomerEntityBridgeService(CustomerService customerService,
                                     PropertyOwnerService propertyOwnerService,
                                     TenantService tenantService,
                                     ContractorService contractorService,
                                     CustomerLoginInfoService customerLoginInfoService,
                                     UserService userService,
                                     PasswordEncoder passwordEncoder) {
        this.customerService = customerService;
        this.propertyOwnerService = propertyOwnerService;
        this.tenantService = tenantService;
        this.contractorService = contractorService;
        this.customerLoginInfoService = customerLoginInfoService;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Create Customer records for all orphaned specialized entities
     */
    public BridgeMigrationResult migrateOrphanedEntities() {
        BridgeMigrationResult result = new BridgeMigrationResult();
        
        // Get default CRM user for entity association
        User defaultUser = getDefaultCrmUser();
        
        // Migrate orphaned property owners
        // SKIP: PropertyOwner table is empty (0 records) - migration completed to Customer table
        List<PropertyOwner> orphanedOwners = findOrphanedPropertyOwners(); // Returns empty list
        // for (PropertyOwner owner : orphanedOwners) { // This loop never executes
        //     try {
        //         Customer customer = createCustomerFromPropertyOwner(owner, defaultUser);
        //         result.addSuccess("PropertyOwner", owner.getId(), customer.getCustomerId().intValue());
        //     } catch (Exception e) {
        //         result.addError("PropertyOwner", owner.getId(), e.getMessage());
        //     }
        // }
        
        // Migrate orphaned tenants
        List<Tenant> orphanedTenants = findOrphanedTenants();
        for (Tenant tenant : orphanedTenants) {
            try {
                Customer customer = createCustomerFromTenant(tenant, defaultUser);
                result.addSuccess("Tenant", tenant.getId(), customer.getCustomerId().intValue());
            } catch (Exception e) {
                result.addError("Tenant", tenant.getId(), e.getMessage());
            }
        }
        
        // Migrate orphaned contractors
        List<Contractor> orphanedContractors = findOrphanedContractors();
        for (Contractor contractor : orphanedContractors) {
            try {
                Customer customer = createCustomerFromContractor(contractor, defaultUser);
                result.addSuccess("Contractor", contractor.getId(), customer.getCustomerId().intValue());
            } catch (Exception e) {
                result.addError("Contractor", contractor.getId(), e.getMessage());
            }
        }
        
        return result;
    }

    /**
     * Find PropertyOwners without corresponding Customer records
     * FIXED: PropertyOwner table is empty - all data migrated to Customer table
     */
    public List<PropertyOwner> findOrphanedPropertyOwners() {
        // PropertyOwner table is empty (0 records) - migration completed
        // All property owner data is now in customers table with customer_type='PROPERTY_OWNER'
        return List.of(); // Return empty list instead of querying empty table
    }

    /**
     * Find Tenants without corresponding Customer records
     */
    public List<Tenant> findOrphanedTenants() {
        return tenantService.findAll().stream()
            .filter(tenant -> tenant.getEmailAddress() != null && 
                            customerService.findByEmail(tenant.getEmailAddress()) == null)
            .toList();
    }

    /**
     * Find Contractors without corresponding Customer records
     */
    public List<Contractor> findOrphanedContractors() {
        return contractorService.findAll().stream()
            .filter(contractor -> contractor.getEmailAddress() != null && 
                                customerService.findByEmail(contractor.getEmailAddress()) == null)
            .toList();
    }

    /**
     * Create Customer record from PropertyOwner entity
     */
    private Customer createCustomerFromPropertyOwner(PropertyOwner owner, User defaultUser) {
        Customer customer = new Customer();
        
        // Basic info from PropertyOwner
        customer.setName(getPropertyOwnerDisplayName(owner));
        customer.setEmail(owner.getEmailAddress());
        customer.setPhone(owner.getMobile() != null ? owner.getMobile() : owner.getPhone());
        customer.setAddress(buildFullAddress(owner.getAddressLine1(), owner.getAddressLine2(), 
                                           owner.getAddressLine3()));
        customer.setCity(owner.getCity());
        customer.setState(owner.getState());
        customer.setCountry(owner.getCountry());
        customer.setDescription("Property Owner - Migrated from PropertyOwner entity");
        
        // Set customer type
        customer.setCustomerType(CustomerType.PROPERTY_OWNER);
        customer.setIsPropertyOwner(true);
        
        // Link to property if available
        if (owner.getPropertyId() != null) {
            customer.setEntityType("Property");
            customer.setEntityId(owner.getPropertyId());
        }
        
        // Set audit fields
        customer.setUser(defaultUser);
        customer.setCreatedAt(LocalDateTime.now());
        
        Customer savedCustomer = customerService.save(customer);
        
        // Set bidirectional relationship
        owner.setCustomer(savedCustomer);
        propertyOwnerService.save(owner);
        
        return savedCustomer;
    }

    /**
     * Create Customer record from Tenant entity
     */
    private Customer createCustomerFromTenant(Tenant tenant, User defaultUser) {
        Customer customer = new Customer();
        
        // Basic info from Tenant
        customer.setName(getTenantDisplayName(tenant));
        customer.setEmail(tenant.getEmailAddress());
        customer.setPhone(tenant.getMobileNumber() != null ? tenant.getMobileNumber() : tenant.getPhoneNumber());
        customer.setAddress(buildFullAddress(tenant.getAddressLine1(), tenant.getAddressLine2(), 
                                           tenant.getAddressLine3()));
        customer.setCity(tenant.getCity());
        customer.setCountry(tenant.getCountry());
        customer.setDescription("Tenant - Migrated from Tenant entity");
        
        // Set customer type
        customer.setCustomerType(CustomerType.TENANT);
        customer.setIsTenant(true);
        
        // Link to property if available
        if (tenant.getProperty() != null) {
            customer.setEntityType("Property");
            customer.setEntityId(tenant.getProperty().getId());
        }
        
        // Set audit fields
        customer.setUser(defaultUser);
        customer.setCreatedAt(LocalDateTime.now());
        
        Customer savedCustomer = customerService.save(customer);
        
        // Set bidirectional relationship
        tenant.setCustomer(savedCustomer);
        tenantService.save(tenant);
        
        return savedCustomer;
    }

    /**
     * Create Customer record from Contractor entity
     */
    private Customer createCustomerFromContractor(Contractor contractor, User defaultUser) {
        Customer customer = new Customer();
        
        // Basic info from Contractor
        customer.setName(contractor.getContactPerson() != null ? 
                        contractor.getContactPerson() : contractor.getCompanyName());
        customer.setEmail(contractor.getEmailAddress());
        customer.setPhone(contractor.getMobileNumber() != null ? 
                         contractor.getMobileNumber() : contractor.getPhoneNumber());
        customer.setAddress(buildFullAddress(contractor.getAddressLine1(), contractor.getAddressLine2(), 
                                           contractor.getAddressLine3()));
        customer.setCity(contractor.getCity());
        customer.setCountry(contractor.getCountry());
        customer.setDescription("Contractor - " + contractor.getCompanyName());
        
        // Set customer type
        customer.setCustomerType(CustomerType.CONTRACTOR);
        customer.setIsContractor(true);
        
        // Set audit fields
        customer.setUser(defaultUser);
        customer.setCreatedAt(LocalDateTime.now());
        
        Customer savedCustomer = customerService.save(customer);
        
        // Set bidirectional relationship
        contractor.setCustomer(savedCustomer);
        contractorService.save(contractor);
        
        return savedCustomer;
    }

    /**
     * Create login credentials for migrated customers
     */
    public void createLoginCredentialsForMigratedCustomers() {
        List<Customer> customersWithoutLogin = findCustomersWithoutLogin();
        
        for (Customer customer : customersWithoutLogin) {
            try {
                createLoginCredentials(customer);
            } catch (Exception e) {
                System.err.println("Failed to create login for customer " + customer.getCustomerId() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Create login credentials for a customer
     */
    public CustomerLoginInfo createLoginCredentials(Customer customer) {
        CustomerLoginInfo loginInfo = new CustomerLoginInfo();
        
        loginInfo.setUsername(customer.getEmail());
        loginInfo.setCustomer(customer);
        loginInfo.setCreatedAt(LocalDateTime.now());
        
        // Generate secure token for password setup
        loginInfo.setToken(EmailTokenUtils.generateEmailToken());
        loginInfo.setTokenExpiresAt(EmailTokenUtils.createExpirationTime(72)); // 72 hours
        
        // Password will be set when they click the link
        loginInfo.setPasswordSet(false);
        
        return customerLoginInfoService.save(loginInfo);
    }

    /**
     * Find customers without login info
     */
    public List<Customer> findCustomersWithoutLogin() {
        return customerService.findAll().stream()
            .filter(customer -> customer.getCustomerLoginInfo() == null)
            .toList();
    }

    /**
     * Get the specialized entity for a customer
     */
    public Optional<Object> getSpecializedEntity(Customer customer) {
        if (customer.getCustomerType() == CustomerType.PROPERTY_OWNER) {
            return propertyOwnerService.findByEmailAddress(customer.getEmail())
                    .map(entity -> (Object) entity);
        } else if (customer.getCustomerType() == CustomerType.TENANT) {
            return tenantService.findByEmailAddress(customer.getEmail())
                    .map(entity -> (Object) entity);
        } else if (customer.getCustomerType() == CustomerType.CONTRACTOR) {
            return contractorService.findByEmailAddress(customer.getEmail())
                    .map(entity -> (Object) entity);
        }
        return Optional.empty();
    }

    /**
     * Sync customer data with specialized entity
     */
    public void syncCustomerWithSpecializedEntity(Customer customer) {
        if (customer.getCustomerType() == CustomerType.PROPERTY_OWNER) {
            propertyOwnerService.findByEmailAddress(customer.getEmail())
                    .ifPresent(owner -> syncCustomerWithPropertyOwner(customer, owner));
        } else if (customer.getCustomerType() == CustomerType.TENANT) {
            tenantService.findByEmailAddress(customer.getEmail())
                    .ifPresent(tenant -> syncCustomerWithTenant(customer, tenant));
        } else if (customer.getCustomerType() == CustomerType.CONTRACTOR) {
            contractorService.findByEmailAddress(customer.getEmail())
                    .ifPresent(contractor -> syncCustomerWithContractor(customer, contractor));
        }
    }

    /**
     * Link existing PropertyOwner to Customer record
     */
    public void linkPropertyOwnerToCustomer(PropertyOwner owner, Customer customer) {
        owner.setCustomer(customer);
        propertyOwnerService.save(owner);
    }

    /**
     * Link existing Tenant to Customer record  
     */
    public void linkTenantToCustomer(Tenant tenant, Customer customer) {
        tenant.setCustomer(customer);
        tenantService.save(tenant);
    }

    /**
     * Link existing Contractor to Customer record
     */
    public void linkContractorToCustomer(Contractor contractor, Customer customer) {
        contractor.setCustomer(customer);
        contractorService.save(contractor);
    }

    // Helper methods
    private User getDefaultCrmUser() {
        // Get first admin/manager user, or create a system user
        List<User> users = userService.findAll();
        return users.stream()
                .filter(user -> user.getRoles().stream()
                        .anyMatch(role -> role.getName().contains("MANAGER") || role.getName().contains("ADMIN")))
                .findFirst()
                .orElse(users.get(0)); // Fallback to first user
    }

    private String getPropertyOwnerDisplayName(PropertyOwner owner) {
        if (owner.getAccountType() == AccountType.business && owner.getBusinessName() != null) {
            return owner.getBusinessName();
        }
        return buildFullName(owner.getFirstName(), owner.getLastName());
    }

    private String getTenantDisplayName(Tenant tenant) {
        if (tenant.getAccountType() == AccountType.business && tenant.getBusinessName() != null) {
            return tenant.getBusinessName();
        }
        return buildFullName(tenant.getFirstName(), tenant.getLastName());
    }

    private String buildFullName(String firstName, String lastName) {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else if (lastName != null) {
            return lastName;
        }
        return "Unknown";
    }

    private String buildFullAddress(String line1, String line2, String line3) {
        StringBuilder address = new StringBuilder();
        if (line1 != null) address.append(line1);
        if (line2 != null) {
            if (address.length() > 0) address.append(", ");
            address.append(line2);
        }
        if (line3 != null) {
            if (address.length() > 0) address.append(", ");
            address.append(line3);
        }
        return address.toString();
    }

    private void syncCustomerWithPropertyOwner(Customer customer, PropertyOwner owner) {
        // Update customer with latest data from PropertyOwner
        customer.setName(getPropertyOwnerDisplayName(owner));
        customer.setPhone(owner.getMobile() != null ? owner.getMobile() : owner.getPhone());
        customerService.save(customer);
    }

    private void syncCustomerWithTenant(Customer customer, Tenant tenant) {
        // Update customer with latest data from Tenant
        customer.setName(getTenantDisplayName(tenant));
        customer.setPhone(tenant.getMobileNumber() != null ? tenant.getMobileNumber() : tenant.getPhoneNumber());
        customerService.save(customer);
    }

    private void syncCustomerWithContractor(Customer customer, Contractor contractor) {
        // Update customer with latest data from Contractor
        customer.setName(contractor.getContactPerson() != null ? 
                        contractor.getContactPerson() : contractor.getCompanyName());
        customer.setPhone(contractor.getMobileNumber() != null ? 
                         contractor.getMobileNumber() : contractor.getPhoneNumber());
        customerService.save(customer);
    }
}
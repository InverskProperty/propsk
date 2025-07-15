package site.easy.to.build.crm.service.customer;

import org.checkerframework.checker.units.qual.C;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerType;
import site.easy.to.build.crm.entity.Ticket;
import site.easy.to.build.crm.entity.Lead;
import site.easy.to.build.crm.entity.Contract;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface CustomerService {

    // EXISTING METHODS - Keep unchanged
    public Customer findByCustomerId(int customerId);

    // Add this method to your CustomerService interface
    public boolean existsById(Integer customerId);

    public List<Customer> findByUserId(int userId);

    public Customer findByEmail(String email);

    public List<Customer> findAll();

    public Customer save(Customer customer);

    public void delete(Customer customer);

    public List<Customer> getRecentCustomers(int userId, int limit);

    long countByUserId(int userId);

    // NEW SEARCH METHODS - Work immediately with existing data
    List<Customer> findByKeyword(String keyword);
    
    List<Customer> findByEmailContainingIgnoreCase(String email);
    
    List<Customer> findByNameContainingIgnoreCase(String name);

    // NEW METHODS - Property Management Extensions
    
    /**
     * Property Management Extensions
     */
    List<Customer> findByCustomerType(CustomerType customerType);
    
    List<Customer> findByCustomerTypeAndPropertyId(CustomerType customerType, Long propertyId);
    
    List<Customer> findPropertyOwners();
    
    List<Customer> findTenants();
    
    List<Customer> findContractors();
    
    List<Customer> findByIsPropertyOwner(Boolean isPropertyOwner);
    
    List<Customer> findByIsTenant(Boolean isTenant);
    
    List<Customer> findByIsContractor(Boolean isContractor);
    
    /**
     * Property Owner specific methods
     */
    Customer findPropertyOwnerByEmail(String email);
    
    List<Customer> findPropertyOwnersByPortfolioValue(BigDecimal minValue, BigDecimal maxValue);
    
    void updateOwnerPortfolioStats(Long customerId, BigDecimal portfolioValue, Integer totalProperties);
    
    /**
     * Tenant specific methods
     */

    List<Customer> findByAssignedPropertyId(Long propertyId);
    List<Customer> findByEntityTypeAndEntityId(String entityType, Long entityId);
    Customer findTenantByEmail(String email);
    
    List<Customer> findTenantsByProperty(Long propertyId);
    
    Customer findActiveTenantForProperty(Long propertyId);

    Customer findByPayPropEntityId(String payPropEntityId);
    
    void assignTenantToProperty(Long customerId, Long propertyId, BigDecimal monthlyRent, LocalDate startDate);
    
    void endTenancy(Long customerId, LocalDate endDate);

    /**
     * Find tenant customer assigned to a specific property
     */
    Customer findTenantByPropertyId(Long propertyId);
        
    /**
     * Communication methods (leverage existing email system)
     */
    void sendPortfolioUpdateToOwner(Long customerId, String message);
    
    void sendMaintenanceNotificationToTenant(Long customerId, String message);
    
    void sendBulkEmailToCustomerType(CustomerType customerType, String subject, String message);
    
    /**
     * Integration with existing ticket/lead/contract systems
     */
    List<Ticket> getTicketsForCustomer(Long customerId);
    
    List<Lead> getLeadsForCustomer(Long customerId);
    
    List<Contract> getContractsForCustomer(Long customerId);
    
    /**
     * PayProp integration methods
     */
    void syncCustomerWithPayProp(Long customerId);
    
    List<Customer> findCustomersNeedingPayPropSync();
    
    void updatePayPropCustomerId(Long customerId, String payPropId);

    List<Customer> findByPayPropSynced(Boolean synced);
    
    long countByCustomerType(CustomerType customerType);
}
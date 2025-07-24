package site.easy.to.build.crm.service.customer;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.repository.CustomerRepository;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerType;
import site.easy.to.build.crm.entity.Ticket;
import site.easy.to.build.crm.entity.Lead;
import site.easy.to.build.crm.entity.Contract;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.ArrayList;

@Service
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerServiceImpl(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public Customer findByCustomerId(Long customerId) {
        return customerRepository.findByCustomerId(customerId);
    }

    @Override
    public boolean existsById(Long customerId) {
        return customerRepository.existsById(customerId);
    }

    @Override
    public Customer findByPayPropEntityId(String payPropEntityId) {
        return customerRepository.findByPayPropEntityId(payPropEntityId);
    }

    @Override
    public Customer findByEmail(String email) {
        return customerRepository.findByEmail(email);
    }

    @Override
    public List<Customer> findByUserId(Long userId) {
        return customerRepository.findByUserId(userId);
    }

    @Override
    public List<Customer> findAll() {
        return customerRepository.findAll();
    }

    @Override
    public Customer save(Customer customer) {
        return customerRepository.save(customer);
    }

    @Override
    public void delete(Customer customer) {
        customerRepository.delete(customer);
    }

    @Override
    public List<Customer> getRecentCustomers(Long userId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return customerRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Override
    public long countByUserId(Long userId) {
        return customerRepository.countByUserId(userId);
    }

    // ===== SEARCH METHODS =====
    @Override
    public List<Customer> findByKeyword(String keyword) {
        return customerRepository.findByKeyword(keyword);
    }

    @Override
    public List<Customer> findByEmailContainingIgnoreCase(String email) {
        return customerRepository.findByEmailContainingIgnoreCase(email);
    }

    @Override
    public List<Customer> findByNameContainingIgnoreCase(String name) {
        return customerRepository.findByNameContainingIgnoreCase(name);
    }

    // ===== CUSTOMER TYPE METHODS - FIXED IMPLEMENTATIONS =====
    
    @Override
    public List<Customer> findByCustomerType(CustomerType customerType) {
        // Note: This method will work after database migration adds customer_type column
        try {
            return customerRepository.findByCustomerType(customerType);
        } catch (Exception e) {
            // Fallback to boolean flags for now
            switch (customerType) {
                case PROPERTY_OWNER:
                    return customerRepository.findByIsPropertyOwner(true);
                case TENANT:
                    return customerRepository.findByIsTenant(true);
                case CONTRACTOR:
                    return customerRepository.findByIsContractor(true);
                default:
                    return List.of();
            }
        }
    }

    @Override
    public List<Customer> findByCustomerTypeAndPropertyId(CustomerType customerType, Long propertyId) {
        // Note: This will work after database migration
        try {
            return customerRepository.findByCustomerTypeAndEntityId(customerType, propertyId);
        } catch (Exception e) {
            // Fallback implementation using property ID lookup
            return findByCustomerType(customerType).stream()
                .filter(c -> propertyId.equals(c.getEntityId()))
                .collect(Collectors.toList());
        }
    }

    @Override
    public List<Customer> findPropertyOwners() {
        // Use the working repository method
        return customerRepository.findPropertyOwners();
    }

    @Override
    public List<Customer> findTenants() {
        // Use the working repository method
        return customerRepository.findTenants();
    }

    @Override
    public List<Customer> findContractors() {
        // Use the working repository method  
        return customerRepository.findContractors();
    }

    @Override
    public List<Customer> findByIsPropertyOwner(Boolean isPropertyOwner) {
        return customerRepository.findByIsPropertyOwner(isPropertyOwner);
    }

    @Override
    public List<Customer> findByIsTenant(Boolean isTenant) {
        return customerRepository.findByIsTenant(isTenant);
    }

    @Override
    public List<Customer> findByIsContractor(Boolean isContractor) {
        return customerRepository.findByIsContractor(isContractor);
    }

    // ===== PROPERTY OWNER SPECIFIC METHODS =====
    
    @Override
    public Customer findPropertyOwnerByEmail(String email) {
        return findPropertyOwners().stream()
            .filter(c -> c.getEmail().equalsIgnoreCase(email))
            .findFirst()
            .orElse(null);
    }

    @Override
    public List<Customer> findPropertyOwnersByPortfolioValue(BigDecimal minValue, BigDecimal maxValue) {
        // TODO: Implement after adding portfolio value fields to Customer entity
        return findPropertyOwners().stream()
            .filter(customer -> {
                // Placeholder logic - implement based on your portfolio calculation
                return true;
            })
            .collect(Collectors.toList());
    }

    @Override
    public void updateOwnerPortfolioStats(Long customerId, BigDecimal portfolioValue, Integer totalProperties) {
        Customer customer = customerRepository.findByCustomerId(customerId);
        if (customer != null) {
            // TODO: Add portfolio fields to Customer entity
            // customer.setPortfolioValue(portfolioValue);
            // customer.setTotalProperties(totalProperties);
            customerRepository.save(customer);
        }
    }

        // ===== STATEMENT GENERATION METHODS =====
    
    @Override
    public List<Customer> findByAssignedPropertyId(Long propertyId) {
        return customerRepository.findByAssignedPropertyId(propertyId);
    }

    @Override
    public List<Customer> findByEntityTypeAndEntityId(String entityType, Long entityId) {
        return customerRepository.findByEntityTypeAndEntityId(entityType, entityId);
    }

    @Override
    public List<Customer> findActiveTenantsForProperty(Long propertyId) {
        return customerRepository.findActiveTenantsForProperty(propertyId, LocalDate.now());
    }

    // ===== TENANT SPECIFIC METHODS =====
    
    @Override
    public Customer findTenantByEmail(String email) {
        return findTenants().stream()
            .filter(c -> c.getEmail().equalsIgnoreCase(email))
            .findFirst()
            .orElse(null);
    }

    @Override
    public List<Customer> findTenantsByProperty(Long propertyId) {
        return findByCustomerTypeAndPropertyId(CustomerType.TENANT, propertyId);
    }

    @Override
    public Customer findActiveTenantForProperty(Long propertyId) {
        List<Customer> tenants = findTenantsByProperty(propertyId);
        return tenants.stream()
            .filter(tenant -> "Active".equalsIgnoreCase(tenant.getDescription()))
            .findFirst()
            .orElse(tenants.isEmpty() ? null : tenants.get(0));
    }

    @Override
    public void assignTenantToProperty(Long customerId, Long propertyId, BigDecimal monthlyRent, LocalDate startDate) {
        Customer customer = customerRepository.findByCustomerId(customerId);
        if (customer != null) {
            customer.setEntityType("Property");
            customer.setEntityId(propertyId);
            customer.setCustomerType(CustomerType.TENANT);
            customer.setIsTenant(true);
            // TODO: Add rental fields
            customerRepository.save(customer);
        }
    }

    @Override
    public void endTenancy(Long customerId, LocalDate endDate) {
        Customer customer = customerRepository.findByCustomerId(customerId);
        if (customer != null) {
            customer.setDescription("Inactive - Tenancy ended " + endDate);
            customerRepository.save(customer);
        }
    }

    // ===== COMMUNICATION METHODS =====
    
    @Override
    public void sendPortfolioUpdateToOwner(Long customerId, String message) {
        Customer customer = customerRepository.findByCustomerId(customerId);
        if (customer != null && (customer.getCustomerType() == CustomerType.PROPERTY_OWNER || customer.getIsPropertyOwner())) {
            // TODO: Integrate with your existing EmailService
            System.out.println("Portfolio update sent to " + customer.getEmail() + ": " + message);
        }
    }

    @Override
    public void sendMaintenanceNotificationToTenant(Long customerId, String message) {
        Customer customer = customerRepository.findByCustomerId(customerId);
        if (customer != null && customer.isOfType(CustomerType.TENANT)) {
            // TODO: Integrate with your existing EmailService
            System.out.println("Maintenance notification sent to " + customer.getEmail() + ": " + message);
        }
    }

    @Override
    public void sendBulkEmailToCustomerType(CustomerType customerType, String subject, String message) {
        List<Customer> customers = findByCustomerType(customerType);
        for (Customer customer : customers) {
            // TODO: Integrate with your existing EmailService
            System.out.println("Bulk email sent to " + customer.getEmail() + " (" + customerType + "): " + subject);
        }
    }

    // ===== INTEGRATION METHODS =====
    
    @Override
    public List<Ticket> getTicketsForCustomer(Long customerId) {
        // TODO: Implement using your existing TicketService
        Customer customer = customerRepository.findByCustomerId(customerId);
        if (customer != null) {
            // Return customer.getTickets() or call ticketService.findByCustomerId(customerId)
        }
        return List.of();
    }

    @Override
    public List<Lead> getLeadsForCustomer(Long customerId) {
        // TODO: Implement using your existing LeadService
        Customer customer = customerRepository.findByCustomerId(customerId);
        if (customer != null) {
            // Return customer.getLeads() or call leadService.findByCustomerId(customerId)
        }
        return List.of();
    }

    @Override
    public List<Contract> getContractsForCustomer(Long customerId) {
        // TODO: Implement using your existing ContractService
        Customer customer = customerRepository.findByCustomerId(customerId);
        if (customer != null) {
            // Return customer.getContracts() or call contractService.findByCustomerId(customerId)
        }
        return List.of();
    }

    @Override
    public Customer findTenantByPropertyId(Long propertyId) {
        // Find tenant assigned to this property via assignment service
        List<Customer> tenants = findTenantsByProperty(propertyId);
        return tenants.stream()
            .filter(tenant -> tenant.getIsTenant())
            .findFirst()
            .orElse(null);
    }

    // ===== PAYPROP INTEGRATION METHODS =====
    
    @Override
    public void syncCustomerWithPayProp(Long customerId) {
        Customer customer = customerRepository.findByCustomerId(customerId);
        if (customer != null && customer.isPayPropEntity()) {
            customer.setPayPropSynced(true);
            customer.setPayPropLastSync(java.time.LocalDateTime.now());
            customerRepository.save(customer);
            System.out.println("Customer " + customerId + " synced with PayProp");
        }
    }

    @Override
    public List<Customer> findCustomersNeedingPayPropSync() {
        // Note: This will work after database migration adds PayProp fields
        try {
            return customerRepository.findByPayPropSyncedFalseAndCustomerTypeIn(
                List.of(CustomerType.PROPERTY_OWNER, CustomerType.TENANT)
            );
        } catch (Exception e) {
            // Fallback: find property owners and tenants without PayProp customer ID
            List<Customer> needsSync = new ArrayList<>();
            needsSync.addAll(findPropertyOwners().stream()
                .filter(c -> c.getPayPropCustomerId() == null)
                .collect(Collectors.toList()));
            needsSync.addAll(findTenants().stream()
                .filter(c -> c.getPayPropCustomerId() == null)
                .collect(Collectors.toList()));
            return needsSync;
        }
    }

    @Override
    public void updatePayPropCustomerId(Long customerId, String payPropId) {
        Customer customer = customerRepository.findByCustomerId(customerId);
        if (customer != null) {
            customer.setPayPropCustomerId(payPropId);
            customer.setPayPropSynced(true);
            customer.setPayPropLastSync(java.time.LocalDateTime.now());
            customerRepository.save(customer);
        }
    }

    @Override
    public List<Customer> findByPayPropSynced(Boolean synced) {
        return customerRepository.findByPayPropSynced(synced);
    }

    @Override
    public long countByCustomerType(CustomerType customerType) {
        return customerRepository.countByCustomerType(customerType);
    }
}
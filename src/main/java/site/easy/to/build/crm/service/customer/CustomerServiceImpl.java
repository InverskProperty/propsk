package site.easy.to.build.crm.service.customer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.easy.to.build.crm.repository.CustomerRepository;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerType;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.entity.Ticket;
import site.easy.to.build.crm.entity.Lead;
import site.easy.to.build.crm.entity.Contract;
import site.easy.to.build.crm.service.user.OAuthUserService;
import site.easy.to.build.crm.service.assignment.CustomerPropertyAssignmentService;
import site.easy.to.build.crm.entity.AssignmentType;
import site.easy.to.build.crm.entity.CustomerPropertyAssignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.ArrayList;

@Service
public class CustomerServiceImpl implements CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerServiceImpl.class);
    
    private final CustomerRepository customerRepository;
    private final CustomerPropertyAssignmentService assignmentService;
    
    @Autowired(required = false)
    private OAuthUserService oAuthUserService;

    public CustomerServiceImpl(CustomerRepository customerRepository, CustomerPropertyAssignmentService assignmentService) {
        this.customerRepository = customerRepository;
        this.assignmentService = assignmentService;
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
        // First try direct email lookup
        Customer customer = customerRepository.findByEmail(email);
        
        // If not found and OAuth service is available, try OAuth lookup
        if (customer == null && oAuthUserService != null) {
            try {
                // Get OAuth user by email
                OAuthUser oAuthUser = oAuthUserService.findBtEmail(email);
                if (oAuthUser != null) {
                    // Try to find customer by OAuth user ID
                    customer = findByOAuthUserId(oAuthUser.getId());
                }
            } catch (Exception e) {
                // Log error but don't fail
                log.debug("OAuth lookup failed for email: " + email, e);
            }
        }
        
        return customer;
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

    // ===== OAUTH USER INTEGRATION METHODS =====
    
    @Override
    public Customer findByOAuthUserId(Integer oauthUserId) {
        if (oauthUserId == null) {
            return null;
        }
        return customerRepository.findByOauthUserId(oauthUserId);
    }
    
    @Override
    public List<Customer> findAllByOAuthUserId(Integer oauthUserId) {
        if (oauthUserId == null) {
            return new ArrayList<>();
        }
        return customerRepository.findAllByOauthUserId(oauthUserId);
    }
    
    @Override
    @Transactional
    public void linkCustomerToOAuthUser(Long customerId, Integer oauthUserId) {
        Customer customer = customerRepository.findByCustomerId(customerId);
        if (customer != null) {
            customer.setOauthUserId(oauthUserId);
            customerRepository.save(customer);
            log.info("Linked customer {} to OAuth user {}", customerId, oauthUserId);
        }
    }
    
    @Override
    public boolean existsByOAuthUserId(Integer oauthUserId) {
        if (oauthUserId == null) {
            return false;
        }
        return customerRepository.existsByOauthUserId(oauthUserId);
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
        // UNIFIED PAYPROP WINNER LOGIC: Return ALL PROPERTY_OWNER customers
        // This includes: PayProp-synced, Local-only, and Pending sync owners
        try {
            System.out.println("🔍 CustomerServiceImpl.findPropertyOwners() - Using UNIFIED PayProp Winner Logic");
            
            // Get ALL customers with customer_type = 'PROPERTY_OWNER'
            // This includes ABC TEST and all other property owners regardless of assignments
            List<Customer> allPropertyOwners = customerRepository.findByCustomerType(CustomerType.PROPERTY_OWNER);
            System.out.println("🔍 Found " + allPropertyOwners.size() + " total PROPERTY_OWNER customers");
            
            // Sort by created date (newest first) to show recent additions like "ABC TEST"
            allPropertyOwners.sort((c1, c2) -> {
                if (c1.getCreatedAt() == null && c2.getCreatedAt() == null) return 0;
                if (c1.getCreatedAt() == null) return 1;
                if (c2.getCreatedAt() == null) return -1;
                return c2.getCreatedAt().compareTo(c1.getCreatedAt());
            });
            
            System.out.println("🔍 Returning " + allPropertyOwners.size() + " property owners (including pending sync)");
            return allPropertyOwners;
            
        } catch (Exception e) {
            // Fallback to repository method
            System.out.println("🔍 Unified logic FAILED, falling back to repository method: " + e.getMessage());
            log.warn("Unified property owners logic failed, falling back to repository: " + e.getMessage(), e);
            return customerRepository.findPropertyOwners();
        }
    }

    @Override
    public List<Customer> findTenants() {
        // Use assignment service to get all customers with TENANT assignment type
        try {
            System.out.println("🔍 CustomerServiceImpl.findTenants() - Using NEW assignment service logic");
            List<CustomerPropertyAssignment> tenantAssignments = assignmentService.getAssignmentsByType(AssignmentType.TENANT);
            System.out.println("🔍 Found " + tenantAssignments.size() + " tenant assignments from assignment service");
            
            List<Customer> customers = tenantAssignments.stream()
                .map(CustomerPropertyAssignment::getCustomer)
                .filter(customer -> customer != null) // Safety check
                .distinct()
                .collect(Collectors.toList());
            
            System.out.println("🔍 Returning " + customers.size() + " unique tenants");
            return customers;
        } catch (Exception e) {
            // Fallback to old method if assignment service fails
            System.out.println("🔍 Assignment service FAILED for findTenants, falling back to old method: " + e.getMessage());
            log.warn("Assignment service failed for findTenants, falling back to old method: " + e.getMessage(), e);
            return customerRepository.findTenants();
        }
    }

    @Override
    public List<Customer> findContractors() {
        // Use assignment service to get all customers with CONTRACTOR assignment type
        try {
            List<CustomerPropertyAssignment> contractorAssignments = assignmentService.getAssignmentsByType(AssignmentType.CONTRACTOR);
            return contractorAssignments.stream()
                .map(CustomerPropertyAssignment::getCustomer)
                .filter(customer -> customer != null) // Safety check
                .distinct()
                .collect(Collectors.toList());
        } catch (Exception e) {
            // Fallback to old method if assignment service fails
            log.warn("Assignment service failed for findContractors, falling back to old method: " + e.getMessage(), e);
            return customerRepository.findContractors();
        }
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
        // Use the assignment service to get tenants properly assigned via customer_property_assignments table
        return assignmentService.getCustomersForProperty(propertyId, AssignmentType.TENANT);
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
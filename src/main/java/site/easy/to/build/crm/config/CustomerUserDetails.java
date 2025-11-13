package site.easy.to.build.crm.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerLoginInfo;
import site.easy.to.build.crm.entity.CustomerType;
import site.easy.to.build.crm.entity.Role;
import site.easy.to.build.crm.repository.CustomerLoginInfoRepository;
import site.easy.to.build.crm.repository.CustomerRepository;
import site.easy.to.build.crm.service.role.RoleService;

import java.util.ArrayList;
import java.util.List;

@Service
public class CustomerUserDetails implements UserDetailsService {

    @Autowired
    CustomerLoginInfoRepository customerLoginInfoRepository;
    @Autowired
    CustomerRepository customerRepository;
    @Autowired
    RoleService roleService;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        System.out.println("=== DEBUG: CustomerUserDetails.loadUserByUsername ===");
        System.out.println("DEBUG: Looking up email: '" + email + "'");
        System.out.println("DEBUG: Email is null: " + (email == null));
        System.out.println("DEBUG: Email is empty: " + (email != null && email.isEmpty()));
        System.out.println("DEBUG: Email length: " + (email != null ? email.length() : "null"));
        
        CustomerLoginInfo customerLoginInfo = customerLoginInfoRepository.findByUsername(email);
        
        if (customerLoginInfo == null) {
            System.out.println("DEBUG: CustomerLoginInfo is null for email: " + email);
            throw new UsernameNotFoundException("Customer not found with email: " + email);
        }
        
        System.out.println("DEBUG: Found CustomerLoginInfo with ID: " + customerLoginInfo.getId());
        System.out.println("DEBUG: Account locked status: " + customerLoginInfo.isAccountLocked());
        System.out.println("DEBUG: Stored password hash: " + customerLoginInfo.getPassword());
        
        // Check if account is locked
        if (customerLoginInfo.isAccountLocked()) {
            System.out.println("DEBUG: Account is locked for email: " + email);
            throw new UsernameNotFoundException("Account is locked: " + email);
        }

        // SIMPLE FIX: Use the existing findByEmail method instead of JPA relationship
        Customer customer = customerRepository.findByEmail(email);
        
        System.out.println("DEBUG: Customer: " + (customer != null ? customer.getCustomerId() : "null"));
        System.out.println("DEBUG: Customer type: " + (customer != null ? customer.getCustomerType() : "null"));
        System.out.println("DEBUG: Is property owner: " + (customer != null ? customer.getIsPropertyOwner() : "null"));
        System.out.println("DEBUG: Is tenant: " + (customer != null ? customer.getIsTenant() : "null"));
        System.out.println("DEBUG: Is contractor: " + (customer != null ? customer.getIsContractor() : "null"));
        
        String roleName = determineRoleByCustomerType(customer);
        System.out.println("DEBUG: Determined role name: " + roleName);
        
        Role role = roleService.findByName(roleName);
        System.out.println("DEBUG: Found role from service: " + (role != null ? role.getName() : "null"));
        
        if (role == null) {
            System.out.println("DEBUG: Role is null, falling back to ROLE_CUSTOMER");
            role = roleService.findByName("ROLE_CUSTOMER");
            System.out.println("DEBUG: Fallback role: " + (role != null ? role.getName() : "still null"));
        }

        if (role == null) {
            System.out.println("ERROR: Could not find any role, even ROLE_CUSTOMER!");
            throw new UsernameNotFoundException("No valid role found for user: " + email);
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(role.getName()));
        System.out.println("DEBUG: Final authorities: " + authorities);

        System.out.println("=== DEBUG: Creating UserDetails ===");
        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                customerLoginInfo.getEmail(),
                customerLoginInfo.getPassword(),
                !customerLoginInfo.isAccountLocked(), // enabled
                true, // accountNonExpired
                true, // credentialsNonExpired
                !customerLoginInfo.isAccountLocked(), // accountNonLocked
                authorities
        );
        
        System.out.println("DEBUG: UserDetails created successfully");
        System.out.println("DEBUG: Username: " + userDetails.getUsername());
        System.out.println("DEBUG: Authorities: " + userDetails.getAuthorities());
        System.out.println("DEBUG: Enabled: " + userDetails.isEnabled());
        System.out.println("DEBUG: Account non-locked: " + userDetails.isAccountNonLocked());
        
        return userDetails;
    }
    
    /**
     * Map customer types to Spring Security roles
     */
    private String determineRoleByCustomerType(Customer customer) {
        System.out.println("=== DEBUG: determineRoleByCustomerType ===");
        if (customer == null) {
            System.out.println("DEBUG: Customer is null, returning ROLE_CUSTOMER");
            return "ROLE_CUSTOMER";
        }
        
        CustomerType customerType = customer.getCustomerType();
        System.out.println("DEBUG: Customer type enum: " + customerType);
        
        // If customer type is set, use it
        if (customerType != null) {
            switch (customerType) {
                case PROPERTY_OWNER:
                    System.out.println("DEBUG: CustomerType is PROPERTY_OWNER, returning ROLE_PROPERTY_OWNER");
                    return "ROLE_PROPERTY_OWNER";
                case DELEGATED_USER:
                    System.out.println("DEBUG: CustomerType is DELEGATED_USER, returning ROLE_PROPERTY_OWNER");
                    return "ROLE_PROPERTY_OWNER";
                case TENANT:
                    System.out.println("DEBUG: CustomerType is TENANT, returning ROLE_TENANT");
                    return "ROLE_TENANT";
                case CONTRACTOR:
                    System.out.println("DEBUG: CustomerType is CONTRACTOR, returning ROLE_CONTRACTOR");
                    return "ROLE_CONTRACTOR";
                case EMPLOYEE:
                    System.out.println("DEBUG: CustomerType is EMPLOYEE, returning ROLE_EMPLOYEE");
                    return "ROLE_EMPLOYEE";
                case MANAGER:
                    System.out.println("DEBUG: CustomerType is MANAGER, returning ROLE_PROPERTY_OWNER (for customer-login access)");
                    return "ROLE_PROPERTY_OWNER"; // Managers access customer-login portal, not employee portal
                case ADMIN:
                    System.out.println("DEBUG: CustomerType is ADMIN, returning ROLE_ADMIN");
                    return "ROLE_ADMIN";
                case SUPER_ADMIN:
                    System.out.println("DEBUG: CustomerType is SUPER_ADMIN, returning ROLE_SUPER_ADMIN");
                    return "ROLE_SUPER_ADMIN";
                case REGULAR_CUSTOMER:
                default:
                    System.out.println("DEBUG: CustomerType is " + customerType + ", returning ROLE_CUSTOMER");
                    return "ROLE_CUSTOMER";
            }
        }
        
        // Fallback to legacy boolean flags for backwards compatibility
        System.out.println("DEBUG: CustomerType is null, checking boolean flags");
        System.out.println("DEBUG: isPropertyOwner: " + customer.getIsPropertyOwner());
        System.out.println("DEBUG: isTenant: " + customer.getIsTenant());
        System.out.println("DEBUG: isContractor: " + customer.getIsContractor());
        
        if (Boolean.TRUE.equals(customer.getIsPropertyOwner())) {
            System.out.println("DEBUG: Boolean flag isPropertyOwner=true, returning ROLE_PROPERTY_OWNER");
            return "ROLE_PROPERTY_OWNER";
        }
        if (Boolean.TRUE.equals(customer.getIsTenant())) {
            System.out.println("DEBUG: Boolean flag isTenant=true, returning ROLE_TENANT");
            return "ROLE_TENANT";
        }
        if (Boolean.TRUE.equals(customer.getIsContractor())) {
            System.out.println("DEBUG: Boolean flag isContractor=true, returning ROLE_CONTRACTOR");
            return "ROLE_CONTRACTOR";
        }
        
        System.out.println("DEBUG: No matches, returning default ROLE_CUSTOMER");
        return "ROLE_CUSTOMER";
    }
}
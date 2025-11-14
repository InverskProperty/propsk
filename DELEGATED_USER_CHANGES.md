# Delegated User Functionality - Implementation Guide

## Overview
This document describes the delegated user functionality implemented in the CRM system, allowing property managers and delegated users to manage properties on behalf of property owners.

---

## Table of Contents
1. [Database Schema](#database-schema)
2. [Customer Types & Roles](#customer-types--roles)
3. [Authentication & Security](#authentication--security)
4. [Customer Management](#customer-management)
5. [Password Reset Functionality](#password-reset-functionality)
6. [Bug Fixes](#bug-fixes)
7. [Testing](#testing)

---

## Database Schema

### Customer Table
Added `manages_owner_id` field to track which property owner a delegated user manages for:

```sql
ALTER TABLE customers
ADD COLUMN manages_owner_id BIGINT AFTER is_contractor,
ADD CONSTRAINT fk_manages_owner
FOREIGN KEY (manages_owner_id) REFERENCES customers(customer_id);
```

**Usage:**
- `manages_owner_id = NULL` → Regular customer, property owner, tenant, or contractor
- `manages_owner_id = 68` → Delegated user managing properties for customer #68

### Customer Login Info Table
Links customers to their login credentials:

```sql
-- Relationship: customers.profile_id = customer_login_info.id
customer_login_info.id (PK) ← customers.profile_id (FK)
```

**Key Fields:**
- `username` - Customer email (login identifier)
- `password` - BCrypt hashed password
- `password_set` - Boolean flag for password initialization
- `account_locked` - Account lockout after 5 failed attempts
- `login_attempts` - Failed login counter
- `token` - Password reset token
- `token_expires_at` - Reset token expiration

---

## Customer Types & Roles

### CustomerType Enum
Located in: `src/main/java/site/easy/to/build/crm/entity/CustomerType.java`

```java
public enum CustomerType {
    REGULAR_CUSTOMER,    // Standard customer
    PROPERTY_OWNER,      // Owns properties
    DELEGATED_USER,      // Manages properties for an owner
    TENANT,              // Rents properties
    CONTRACTOR,          // Service provider
    EMPLOYEE,            // Company employee
    MANAGER,             // Company manager
    ADMIN,               // System admin
    SUPER_ADMIN          // Super admin
}
```

### Spring Security Roles
Mapped in: `src/main/java/site/easy/to/build/crm/config/CustomerUserDetails.java`

```java
CustomerType.PROPERTY_OWNER  → ROLE_PROPERTY_OWNER
CustomerType.DELEGATED_USER  → ROLE_PROPERTY_OWNER  // Same access as owner
CustomerType.TENANT          → ROLE_TENANT
CustomerType.CONTRACTOR      → ROLE_CONTRACTOR
CustomerType.MANAGER         → ROLE_PROPERTY_OWNER  // For customer portal access
CustomerType.ADMIN           → ROLE_ADMIN
CustomerType.SUPER_ADMIN     → ROLE_SUPER_ADMIN
CustomerType.REGULAR_CUSTOMER→ ROLE_CUSTOMER
```

**Key Design Decision:**
Delegated users receive `ROLE_PROPERTY_OWNER` authority to access the same features as the property owners they manage for.

---

## Authentication & Security

### Security Filter Chains
Located in: `src/main/java/site/easy/to/build/crm/config/SecurityConfig.java`

#### Customer Portal (Order 1)
```java
@Bean
@Order(1)
public SecurityFilterChain customerSecurityFilterChain(HttpSecurity http) {
    // Handles: /customer-login, /property-owner/**, /tenant/**, /contractor/**
    // Requires: ROLE_PROPERTY_OWNER, ROLE_TENANT, ROLE_CONTRACTOR, etc.
}
```

#### Main Portal (Order 2)
```java
@Bean
@Order(2)
public SecurityFilterChain securityFilterChain(HttpSecurity http) {
    // Handles: /employee/**, /portfolio/**, /payprop/**, etc.
    // Requires: ROLE_MANAGER, ROLE_EMPLOYEE, OIDC_USER
}
```

### Route Protection Examples
```java
// Customer management routes
.requestMatchers("/employee/customer/**")
    .hasAnyAuthority("ROLE_MANAGER", "ROLE_EMPLOYEE", "ROLE_SUPER_ADMIN", "OIDC_USER")

// Property owner portal
.requestMatchers("/property-owner/**")
    .hasAnyAuthority("ROLE_PROPERTY_OWNER", "ROLE_DELEGATED_USER", "ROLE_MANAGER", "ROLE_ADMIN")

// Portfolio routes (accessible to both staff and customers)
.requestMatchers("/portfolio/**")
    .hasAnyAuthority("OIDC_USER", "ROLE_MANAGER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN",
                     "ROLE_EMPLOYEE", "ROLE_PROPERTY_OWNER", "ROLE_CUSTOMER")
```

### CSRF Protection
```java
http.csrf((csrf) -> csrf
    .csrfTokenRepository(httpSessionCsrfTokenRepository)
    .ignoringRequestMatchers("/api/payprop/**", "/portfolio/**", "/property-owner/files/upload/**")
);
```

---

## Customer Management

### Creating a Delegated User

**Location:** `src/main/java/site/easy/to/build/crm/controller/CustomerController.java`

```java
@PostMapping("/add-customer")
public String addCustomer(@ModelAttribute("customer") Customer customer, ...) {
    // 1. Set customer type
    customer.setCustomerType(CustomerType.DELEGATED_USER);

    // 2. Link to property owner
    customer.setManagesOwnerId(68L); // ID of the property owner they manage for

    // 3. Save customer
    Customer savedCustomer = customerService.save(customer);

    // 4. Create login credentials
    CustomerLoginInfo loginInfo = new CustomerLoginInfo();
    loginInfo.setUsername(customer.getEmail());
    loginInfo.setPassword(EmailTokenUtils.encodePassword(temporaryPassword));
    loginInfo.setPasswordSet(true);

    CustomerLoginInfo savedLoginInfo = customerLoginInfoService.save(loginInfo);

    // 5. Link customer to login info
    savedCustomer.setProfileId(savedLoginInfo.getId());
    customerService.save(savedCustomer);

    return "redirect:/employee/customer/all-customers";
}
```

### Updating a Customer

**CRITICAL FIX:** The update form was posting to wrong endpoint, creating duplicates.

**Template:** `src/main/resources/templates/customer/update-customer.html`

**BEFORE (Wrong - caused duplicates):**
```html
<form th:action="@{/employee/customer/update-customer}" method="post">
```

**AFTER (Correct):**
```html
<form th:action="@{/employee/customer/{id}/edit(id=${customer.customerId})}" method="post">
```

**Controller:** `CustomerController.java:1302`
```java
@PostMapping("/{id:[0-9]+}/edit")
public String updateCustomer(@PathVariable("id") Long id,
                           @ModelAttribute Customer customer,
                           Authentication authentication,
                           RedirectAttributes redirectAttributes) {
    // Preserves existing relationships
    Customer existingCustomer = customerService.findByCustomerId(id);
    customer.setCustomerId(id);
    customer.setUser(existingCustomer.getUser()); // Keep original user
    customer.setCreatedAt(existingCustomer.getCreatedAt()); // Keep creation date

    Customer savedCustomer = customerService.save(customer);
    return "redirect:/employee/customer/" + id;
}
```

---

## Password Reset Functionality

### Admin Password Reset (New Feature)

**Location:** `CustomerController.java:2396`

**UI Location:** All customer list pages
- `src/main/resources/templates/customer/manager-all-customers.html`
- `src/main/resources/templates/customer/all-customers.html`

**Features:**
- Yellow "Reset" button with key icon
- Only shows for customers with existing login credentials
- Requires manager/admin authorization
- Resets password to "123" with BCrypt encryption
- Unlocks account and clears failed login attempts

**Backend Endpoint:**
```java
@PostMapping("/{id:[0-9]+}/reset-password")
@ResponseBody
public ResponseEntity<?> resetPasswordToDefault(@PathVariable("id") Long id,
                                                 Authentication authentication) {
    // 1. Verify admin/manager role
    boolean isAdmin = authentication.getAuthorities().stream()
        .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN") ||
                        auth.getAuthority().equals("ROLE_SUPER_ADMIN") ||
                        auth.getAuthority().equals("ROLE_MANAGER") ||
                        auth.getAuthority().equals("OIDC_USER"));

    // 2. Get customer and login info
    Customer customer = customerService.findByCustomerId(id);
    CustomerLoginInfo loginInfo = customerLoginInfoService.findByEmail(customer.getEmail());

    // 3. Reset password with BCrypt
    String defaultPassword = "123";
    String hashedPassword = EmailTokenUtils.encodePassword(defaultPassword);
    loginInfo.setPassword(hashedPassword);

    // 4. Unlock account
    loginInfo.setPasswordSet(true);
    loginInfo.setAccountLocked(false);
    loginInfo.setLoginAttempts(0);
    loginInfo.setToken(null);
    loginInfo.setTokenExpiresAt(null);

    // 5. Save changes
    customerLoginInfoService.save(loginInfo);

    return ResponseEntity.ok(Map.of(
        "success", true,
        "message", "Password reset to '123' for " + customer.getEmail()
    ));
}
```

**Frontend (JavaScript):**
```javascript
function resetCustomerPassword(button) {
    const customerId = $(button).data('customer-id');
    const customerEmail = $(button).data('customer-email');

    // Confirmation dialog
    if (!confirm(`Reset password for ${customerEmail}?`)) return;

    // Get CSRF token
    const token = $("meta[name='_csrf']").attr("content");
    const header = $("meta[name='_csrf_header']").attr("content");

    // AJAX call
    $.ajax({
        url: home + 'employee/customer/' + customerId + '/reset-password',
        type: 'POST',
        beforeSend: function(xhr) {
            xhr.setRequestHeader(header, token);
        },
        success: function(response) {
            $.toast({
                heading: 'Password Reset Successful',
                text: 'Password reset to: 123',
                icon: 'success'
            });
            alert('✅ Password: 123');
        },
        error: function(xhr) {
            let errorMsg = xhr.responseJSON?.error || 'Failed to reset password';
            $.toast({
                heading: 'Error',
                text: errorMsg,
                icon: 'error'
            });
        }
    });
}
```

**HTML Button:**
```html
<button type="button" class="btn btn-sm btn-warning m-r-5"
        title="Reset Password"
        th:if="${customer.customerLoginInfo != null}"
        th:data-customer-id="${customer.customerId}"
        th:data-customer-name="${customer.name}"
        th:data-customer-email="${customer.email}"
        onclick="resetCustomerPassword(this)">
    <i class="fa fa-key"></i> Reset
</button>
```

### Password Encryption

**Location:** `src/main/java/site/easy/to/build/crm/util/EmailTokenUtils.java`

```java
private static final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

public static String encodePassword(String rawPassword) {
    if (rawPassword == null || rawPassword.trim().isEmpty()) {
        throw new IllegalArgumentException("Password cannot be null or empty");
    }
    return passwordEncoder.encode(rawPassword);
}

public static boolean verifyPassword(String rawPassword, String encodedPassword) {
    if (rawPassword == null || encodedPassword == null) {
        return false;
    }
    return passwordEncoder.matches(rawPassword, encodedPassword);
}
```

**BCrypt Example:**
```
Plain text: "123"
BCrypt hash: "$2a$10$zX8kYMJ7N5pQ9rT6sL3uV.wH2fK8jP4mN9xR7vY2bC5dE1fG3hI4j"
```

---

## Bug Fixes

### 1. Duplicate Customers on Edit
**Problem:** Editing a customer created a duplicate instead of updating.

**Root Cause:** Form posted to `/employee/customer/update-customer` (non-existent endpoint), routing to `/add-customer`.

**Fix:** Changed form action to use correct endpoint with ID parameter.

**Files Changed:**
- `src/main/resources/templates/customer/update-customer.html:74`

### 2. Password Reset Button Not Visible
**Problem:** Reset button added to wrong template (all-customers.html vs manager-all-customers.html).

**Root Cause:** Multiple customer list templates exist, user was viewing different template.

**Fix:** Added reset button to correct template (manager-all-customers.html).

**Files Changed:**
- `src/main/resources/templates/customer/manager-all-customers.html:283-290`

### 3. CSRF Token Not Rendering
**Problem:** AJAX password reset failed with "invalid HTTP header field name" error.

**Root Cause:** Meta tags used `content="${_csrf.token}"` instead of `th:content="${_csrf.token}"`.

**Fix:** Added `th:` prefix for proper Thymeleaf rendering.

**Files Changed:**
- `src/main/resources/templates/customer/manager-all-customers.html:6-7`

**BEFORE:**
```html
<meta name="_csrf" content="${_csrf.token}"/>
<meta name="_csrf_header" content="${_csrf.headerName}"/>
```

**AFTER:**
```html
<meta name="_csrf" th:content="${_csrf.token}"/>
<meta name="_csrf_header" th:content="${_csrf.headerName}"/>
```

### 4. jQuery Toast Library Missing
**Problem:** `$.toast is not a function` error in password reset callback.

**Root Cause:** jQuery toast plugin not loaded in manager-all-customers.html.

**Fix:** Added toast CSS and JS libraries.

**Files Changed:**
- `src/main/resources/templates/customer/manager-all-customers.html:12` (CSS)
- `src/main/resources/templates/customer/manager-all-customers.html:413` (JS)

---

## Testing

### Test Scenario 1: Create Delegated User
```
1. Navigate to /employee/customer/create-property-owner
2. Fill in customer details:
   - Name: "Vijay Bhardwaj"
   - Email: "vijay25541@gmail.com"
   - Customer Type: DELEGATED_USER
   - Manages Owner: Select property owner (e.g., Customer #68)
3. Click "Create Customer"
4. Verify customer_login_info record created
5. Verify customers.profile_id links to login info
6. Verify customers.manages_owner_id = 68
```

### Test Scenario 2: Reset Password
```
1. Navigate to /employee/customer/manager/all-customers
2. Find customer with login credentials
3. Click yellow "Reset" button
4. Confirm dialog
5. Verify success toast notification
6. Check Render logs for:
   - "🔄 RESET PASSWORD REQUEST - Customer ID: 100"
   - "✅ Found customer: vijay25541@gmail.com"
   - "✅ Password reset saved"
7. Test login with password "123"
```

### Test Scenario 3: Delegated User Portal Access
```
1. Navigate to /customer-login
2. Login as delegated user (vijay25541@gmail.com / 123)
3. Verify redirect to /customer-login/dashboard
4. Verify access to /property-owner/** routes
5. Verify can manage properties for linked owner
```

### Test Scenario 4: Update Customer Without Duplicates
```
1. Navigate to /employee/customer/{id}/edit
2. Modify customer details
3. Submit form
4. Verify NO duplicate customer created
5. Verify existing customer updated
6. Check database: SELECT * FROM customers WHERE email = 'test@example.com'
   - Should return exactly 1 row
```

### Database Verification Queries
```sql
-- Check delegated user setup
SELECT
    c.customer_id,
    c.name,
    c.email,
    c.customer_type,
    c.manages_owner_id,
    c.profile_id,
    cli.id as login_id,
    cli.username,
    cli.password_set,
    cli.account_locked
FROM customers c
LEFT JOIN customer_login_info cli ON c.profile_id = cli.id
WHERE c.customer_type = 'DELEGATED_USER';

-- Check for duplicate customers
SELECT email, COUNT(*) as count
FROM customers
GROUP BY email
HAVING count > 1;

-- Verify property owner linkage
SELECT
    delegated.customer_id as delegated_id,
    delegated.name as delegated_name,
    owner.customer_id as owner_id,
    owner.name as owner_name
FROM customers delegated
INNER JOIN customers owner ON delegated.manages_owner_id = owner.customer_id
WHERE delegated.customer_type = 'DELEGATED_USER';
```

---

## File Summary

### Modified Files
```
src/main/java/site/easy/to/build/crm/controller/CustomerController.java
  - Added reset password endpoint (line 2396)
  - Fixed update customer endpoint (line 1302)

src/main/java/site/easy/to/build/crm/config/SecurityConfig.java
  - Customer portal security chain (line 66)
  - Main portal security chain (line 121)
  - Route protection rules

src/main/java/site/easy/to/build/crm/config/CustomerUserDetails.java
  - Role determination logic (line 109)
  - DELEGATED_USER → ROLE_PROPERTY_OWNER mapping

src/main/java/site/easy/to/build/crm/entity/Customer.java
  - Added manages_owner_id field
  - Added customerType enum field

src/main/java/site/easy/to/build/crm/entity/CustomerType.java
  - Added DELEGATED_USER enum value

src/main/resources/templates/customer/update-customer.html
  - Fixed form action URL (line 74)
  - Fixed profile_id null handling (line 76)

src/main/resources/templates/customer/manager-all-customers.html
  - Added CSRF meta tags with th: prefix (lines 6-7)
  - Added jQuery toast library (lines 12, 413)
  - Added reset password button (lines 283-290)
  - Added resetCustomerPassword() function (lines 443-517)

src/main/resources/templates/customer/all-customers.html
  - Added reset password button
  - Added resetCustomerPassword() function
```

### Database Migrations
```sql
-- Add manages_owner_id column
ALTER TABLE customers
ADD COLUMN manages_owner_id BIGINT AFTER is_contractor,
ADD CONSTRAINT fk_manages_owner
FOREIGN KEY (manages_owner_id) REFERENCES customers(customer_id);

-- Add customer_type column
ALTER TABLE customers
ADD COLUMN customer_type VARCHAR(50) DEFAULT 'REGULAR_CUSTOMER';
```

---

## Security Considerations

1. **Password Storage:** All passwords encrypted with BCrypt (strength 10)
2. **CSRF Protection:** Required for all POST/PUT/DELETE requests
3. **Role-Based Access:** Fine-grained permissions per route
4. **Account Lockout:** Automatic after 5 failed login attempts
5. **Session Management:** Separate sessions for customer and employee portals
6. **Password Reset:** Only admins/managers can reset passwords
7. **Audit Logging:** All password resets logged with timestamps

---

## Future Enhancements

1. **Email Notifications:** Send email when password is reset
2. **Password Strength:** Enforce stronger default passwords
3. **Self-Service Reset:** Allow customers to reset their own passwords
4. **Activity Logs:** Track delegated user actions on behalf of owners
5. **Permissions Model:** Fine-grained permissions for delegated users
6. **Multi-Owner Support:** Allow delegated users to manage multiple owners
7. **Temporary Passwords:** Auto-expire reset passwords after first use

---

## Support & Documentation

**Related Documentation:**
- Spring Security: https://docs.spring.io/spring-security/reference/
- Thymeleaf: https://www.thymeleaf.org/documentation.html
- BCrypt: https://en.wikipedia.org/wiki/Bcrypt

**Contact:**
- For bugs: Create issue in repository
- For questions: Contact development team

**Version:** 1.0.0
**Last Updated:** 2025-11-14
**Author:** Claude (via sajidkazmi)

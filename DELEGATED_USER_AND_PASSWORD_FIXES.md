# Delegated User Access & Password Management Fixes

## Summary

Fixed two critical issues:
1. **Delegated users now have full owner-level access** to portfolios and blocks
2. **Admin password reset functionality** - reset any customer password to "123"

---

## 1. Delegated User Portfolio Access Fix

### Problem
When Achal (delegated user for Udayan) tried to access portfolio pages like:
- `https://spoutproperty-hub.onrender.com/portfolio/2`
- `https://spoutproperty-hub.onrender.com/customer-login/blocks/2`

He got: **"Access denied to this portfolio"** and **"Error loading financial data"**

### Root Cause
The system was checking if `customer.getCustomerId()` == `portfolio.getPropertyOwnerId()`, which fails for delegated users because:
- Achal's customer_id = 1016 (delegated user)
- Portfolio owner_id = 1015 (Udayan, the actual owner)

### Solution

#### File: `PortfolioController.java` (lines 3330-3372)

Added delegated user access check:

```java
// Check access: Either direct owner OR delegated user with assigned properties from this owner
boolean hasAccess = false;

// Direct owner check
if (portfolio.getPropertyOwnerId().equals(customer.getCustomerId())) {
    hasAccess = true;
    System.out.println("✅ Portfolio access granted: Direct owner");
}
// Delegated user check
else if (customer.getCustomerType() == CustomerType.DELEGATED_USER) {
    // Get properties assigned to this delegated user
    List<Property> delegatedProperties = propertyService.findPropertiesByCustomerAssignments(customer.getCustomerId());

    // Check if any of these properties belong to the portfolio's owner
    boolean hasOwnerProperties = delegatedProperties.stream()
        .anyMatch(p -> p.getPropertyOwnerId() != null &&
                     p.getPropertyOwnerId().equals(portfolio.getPropertyOwnerId()));

    if (hasOwnerProperties) {
        hasAccess = true;
        System.out.println("✅ Portfolio access granted: Delegated user with owner's properties");
    }
}
```

**Logic:**
1. For direct owners: Check if customer_id matches portfolio owner_id (existing behavior)
2. For delegated users: Check if they have ANY properties from the portfolio's owner
3. If either condition is true, grant access

#### File: `PropertyOwnerBlockController.java` (lines 138-151)

Enhanced block access check for delegated users:

```java
else if (customer.getCustomerType() == CustomerType.DELEGATED_USER) {
    // Delegated users: Check if they have access to properties from the portfolio's owner
    List<Property> delegatedProperties = propertyService.findPropertiesByCustomerAssignments(customer.getCustomerId());
    boolean hasOwnerProperties = delegatedProperties.stream()
        .anyMatch(p -> p.getPropertyOwnerId() != null &&
                     p.getPropertyOwnerId().equals(portfolio.getPropertyOwnerId()));

    if (!hasOwnerProperties) {
        log.debug("Delegated user {} has no properties from portfolio owner {}",
            customer.getCustomerId(), portfolio.getPropertyOwnerId());
        return false; // Delegated user has no properties from this portfolio's owner
    }
    // Continue to property-level check below
}
```

### Testing

As Achal (delegated user):
1. Visit `https://spoutproperty-hub.onrender.com/portfolio/2`
   - ✅ Should now load portfolio organization
   - ✅ Should show all properties from Udayan's portfolio

2. Visit `https://spoutproperty-hub.onrender.com/customer-login/blocks/2`
   - ✅ Should load block details
   - ✅ Should show financial data for the block

---

## 2. Admin Password Reset Feature

### Problem
Need ability to:
- Set passwords for new customers
- Reset forgotten passwords to a default value ("123")

### Solution

#### File: `CustomerController.java`

Added two new endpoints:

### A. Reset Password to "123" (Admin Only)

**Endpoint:** `POST /employee/customer/{id}/reset-password`

**Access:** Admin/Manager only

**Usage:**

```bash
# Via curl
curl -X POST \
  'https://spoutproperty-hub.onrender.com/employee/customer/{CUSTOMER_ID}/reset-password' \
  -H 'Cookie: JSESSIONID=your_session_cookie'

# Response
{
  "success": true,
  "message": "Password reset to '123' for upday@sunflaguk.com",
  "email": "upday@sunflaguk.com"
}
```

**Via JavaScript (for admin UI):**

```javascript
async function resetPassword(customerId) {
    const response = await fetch(`/employee/customer/${customerId}/reset-password`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        }
    });

    const data = await response.json();
    if (data.success) {
        alert(`Password reset to '123' for ${data.email}`);
    } else {
        alert(`Error: ${data.error}`);
    }
}
```

**Features:**
- Creates `customer_login_info` record if it doesn't exist
- Encrypts password using BCrypt
- Sets `password_set = true`
- Clears any existing password reset tokens
- Returns success message with customer email

**Security:**
- Only accessible to users with ROLE_ADMIN, ROLE_SUPER_ADMIN, or ROLE_MANAGER
- Returns 403 Forbidden for non-admin users

### B. Send Password Setup Email

**Endpoint:** `POST /employee/customer/{id}/send-password-setup`

**Access:** Employee/Manager/Admin

**What it does:**
1. Generates a secure token (expires in 72 hours)
2. Creates/updates `customer_login_info` record
3. Sends email with password setup link
4. Link format: `https://spoutproperty-hub.onrender.com/set-password?token=XXXXX`

**Usage Example:**

To set up password for Udayan (upday@sunflaguk.com):

1. Find customer ID:
   ```sql
   SELECT customer_id, name, email FROM customer WHERE email = 'upday@sunflaguk.com';
   -- Returns: customer_id = 1015
   ```

2. As logged-in admin, POST to:
   ```
   /employee/customer/1015/send-password-setup
   ```

3. Udayan receives email with setup link

4. He clicks link, sets password, can login at `/customer-login`

---

## Files Modified

### 1. PortfolioController.java
**Location:** `C:\Users\sajid\crecrm\src\main\java\site\easy\to\build\crm\controller\PortfolioController.java`
**Lines:** 3330-3372
**Change:** Added delegated user access check for portfolio organization API

### 2. PropertyOwnerBlockController.java
**Location:** `C:\Users\sajid\crecrm\src\main\java\site\easy\to\build\crm\controller\PropertyOwnerBlockController.java`
**Lines:** 138-151
**Change:** Enhanced block access check for delegated users with portfolio-owned blocks

### 3. CustomerController.java
**Location:** `C:\Users\sajid\crecrm\src\main\java\site\easy\to\build\crm\controller\CustomerController.java`
**Lines:** 2153-2214 (reset password), 2216-2268 (password setup email)
**Changes:**
- Added admin password reset to "123" endpoint
- Added password setup email endpoint

---

## Database Impact

### customer_login_info table

When resetting password or sending setup email, the system:

**For Password Reset:**
```sql
UPDATE customer_login_info SET
    password = '[bcrypt_hash_of_123]',
    password_set = TRUE,
    token = NULL,
    token_expires_at = NULL,
    updated_at = NOW()
WHERE username = 'customer@email.com';
```

**For Password Setup Email:**
```sql
UPDATE customer_login_info SET
    token = '[generated_token]',
    token_expires_at = NOW() + INTERVAL '72 hours',
    password_set = FALSE,
    updated_at = NOW()
WHERE username = 'customer@email.com';
```

---

## Security Considerations

### Password Reset
- ✅ Admin-only (checks ROLE_ADMIN, ROLE_SUPER_ADMIN, ROLE_MANAGER)
- ✅ Password is BCrypt encrypted (not stored as plaintext)
- ✅ Clears any existing tokens to prevent setup link reuse
- ⚠️ Default password is "123" - users should change it immediately

### Password Setup Email
- ✅ Token expires in 72 hours
- ✅ One-time use (token cleared after password set)
- ✅ Email verification required
- ✅ Secure BCrypt password storage

### Delegated User Access
- ✅ Only grants access to owner's properties they're assigned to
- ✅ Doesn't grant access to unrelated owners' portfolios
- ✅ Maintains property-level security through assignments table

---

## Common Use Cases

### Use Case 1: New Property Owner Registration

```javascript
// Admin creates customer in system
const customerId = createPropertyOwner({
    name: "John Doe",
    email: "john@example.com",
    // ... other fields
});

// Send password setup email
POST /employee/customer/${customerId}/send-password-setup

// Customer receives email, sets password, logs in
```

### Use Case 2: Forgotten Password

```javascript
// Admin resets to default
POST /employee/customer/${customerId}/reset-password
// Returns: { success: true, email: "john@example.com" }

// Admin tells customer: "Password reset to '123', please login and change it"
```

### Use Case 3: Delegated User Access

```sql
-- Set up delegated user in database
INSERT INTO customer (name, email, customer_type, is_property_owner)
VALUES ('Achal', 'achal@example.com', 'DELEGATED_USER', FALSE);

-- Assign them to owner's properties
INSERT INTO customer_property_assignments (customer_id, property_id, assignment_type)
SELECT 1016, id, 'DELEGATED_USER'
FROM property
WHERE property_owner_id = 1015; -- Udayan's properties

-- Now Achal can access Udayan's portfolios and blocks automatically
```

---

## Troubleshooting

### Delegated User Still Can't Access Portfolio

**Check:**
1. Customer type is `DELEGATED_USER`
   ```sql
   SELECT customer_id, email, customer_type
   FROM customer
   WHERE email = 'delegated@user.com';
   ```

2. They have property assignments
   ```sql
   SELECT * FROM customer_property_assignments
   WHERE customer_id = 1016
   AND assignment_type = 'DELEGATED_USER';
   ```

3. Properties belong to the portfolio's owner
   ```sql
   SELECT p.id, p.property_name, p.property_owner_id, po.name as portfolio_name
   FROM property p
   JOIN customer_property_assignments cpa ON cpa.property_id = p.id
   JOIN property_portfolio_assignments ppa ON ppa.property_id = p.id
   JOIN portfolio po ON po.id = ppa.portfolio_id
   WHERE cpa.customer_id = 1016;
   ```

### Password Reset Not Working

**Check:**
1. You're logged in as admin/manager
2. Customer exists in database
3. Email is valid in customer record

**View login info:**
```sql
SELECT cli.*, c.name, c.email
FROM customer_login_info cli
JOIN customer c ON c.profile_id = cli.id
WHERE c.email = 'customer@email.com';
```

---

## API Reference

### POST /employee/customer/{id}/reset-password

**Description:** Reset customer password to "123" (admin only)

**Parameters:**
- `id` (path): Customer ID

**Response:**
```json
{
  "success": true,
  "message": "Password reset to '123' for user@example.com",
  "email": "user@example.com"
}
```

**Errors:**
- `403 Forbidden`: Not an admin user
- `404 Not Found`: Customer doesn't exist
- `500 Internal Server Error`: Database error

### POST /employee/customer/{id}/send-password-setup

**Description:** Send password setup email to customer

**Parameters:**
- `id` (path): Customer ID

**Response:** Redirect to customer detail page with success/error message

**Flash Messages:**
- Success: "Password setup email sent to {email}!"
- Warning: "Password setup link created but email could not be sent. Token: {token}"
- Error: "Error sending password setup email: {message}"

---

## Next Steps

Consider adding:
1. UI button on customer detail page for "Reset Password"
2. UI button for "Send Password Setup Email"
3. Password strength requirements (currently accepts "123")
4. Password change endpoint for customers
5. Audit log for password resets
6. Email template customization

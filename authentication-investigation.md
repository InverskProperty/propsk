# Authentication Investigation Report

## Overview
This investigation examines the current user authentication and property ownership system to identify issues with user setup and access control.

## Current System Analysis

### 1. User/Customer Entity Structure

#### User Entity (`User.java:19`)
- **Purpose**: Internal CRM users (employees, managers, admins)
- **Key Fields**:
  - `username`, `email`, `password`
  - `roles` (ManyToMany relationship)
  - `status` (active/inactive/suspended)
  - `isPasswordSet` boolean
  - OAuth integration via `oauthUser`
- **Roles**: Uses Spring Security roles for internal access control

#### Customer Entity (`Customer.java:19`)
- **Purpose**: External customers who can access property owner portal
- **Key Fields**:
  - `name`, `email` (with unique validation)
  - `user` (ManyToOne to User - POTENTIAL ISSUE)
  - `customerType` enum (PROPERTY_OWNER, TENANT, CONTRACTOR, etc.)
  - Boolean flags: `isPropertyOwner`, `isTenant`, `isContractor`
  - PayProp integration fields
- **Login**: Uses separate `CustomerLoginInfo` entity for authentication

#### PropertyOwner Entity (`PropertyOwner.java:14`)
- **Purpose**: Links customers to properties with ownership details
- **Key Relationships**:
  - `customerIdFk` (foreign key to Customer)
  - `propertyId` (foreign key to Property)
  - Ownership percentages and management rights

### 2. Authentication Systems

#### Dual Authentication Setup
The system has **two separate authentication chains**:

1. **Customer Authentication** (`SecurityConfig.java:66`)
   - Routes: `/customer-login`, `/property-owner/**`, `/tenant/**`, `/contractor/**`
   - Uses `CustomerUserDetails` service
   - Success handler: `CustomerLoginSuccessHandler`
   - Separate login form and session management

2. **Internal User Authentication** (`SecurityConfig.java:117`)
   - All other routes including `/portfolio/**`
   - Uses `CrmUserDetails` service
   - Supports OAuth2 login
   - Different role structure (ROLE_MANAGER, ROLE_EMPLOYEE, etc.)

### 3. Identified Issues

#### Issue 1: Confusing User-Customer Relationship
**Problem**: `Customer.java:97` has a `ManyToOne` relationship to `User`
```java
@ManyToOne
@JoinColumn(name = "user_id", nullable=false)
private User user;
```

**Impact**: This creates confusion about whether customers are internal users or external portal users. The relationship suggests every customer must have an internal User account.

#### Issue 2: Mixed Access Control
**Problem**: Portfolio access (`SecurityConfig.java:213-225`) allows both internal roles and customer roles:
```java
.requestMatchers("/portfolio/**").hasAnyAuthority("OIDC_USER", "ROLE_MANAGER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_EMPLOYEE", "ROLE_PROPERTY_OWNER", "ROLE_CUSTOMER")
```

**Impact**: Creates confusion about who should access what functionality.

#### Issue 3: Redundant Customer Classification
**Problem**: Multiple ways to classify customer types:
- `CustomerType` enum (PROPERTY_OWNER, TENANT, etc.)
- Boolean flags (`isPropertyOwner`, `isTenant`, `isContractor`)
- Separate `PropertyOwner` entity

**Impact**: Inconsistent data and potential conflicts.

#### Issue 4: Property Ownership vs Portal Access
**Problem**: The system conflates:
- **Property ownership** (legal/financial relationship)
- **Portal access** (ability to log in and view reports)

**Current Confusion**: People like "Piyush who works for the owner" get created as property owners when they should only have portal access.

### 4. Root Cause Analysis

The fundamental issue is **mixing two distinct concepts**:

1. **Property Ownership** - Legal/financial relationship to properties
2. **Portal User** - Someone who can log in to access reports and functionality

**Example Scenarios That Don't Work Properly**:
- Piyush works for Uday but isn't an owner → Gets incorrectly created as owner
- Achal needs access to Prestvale/Uday properties → No clear way to grant access without ownership

### 5. Recommended Solution Architecture

#### A. Separate User Classes Clearly

1. **Internal Users** (`User` entity)
   - CRM employees, managers, admins
   - Access internal functionality
   - OAuth authentication

2. **Portal Users** (enhanced `Customer` entity)
   - External users who can access property owner portal
   - Can view properties they have access to (not necessarily own)
   - Form-based authentication

3. **Property Owners** (`PropertyOwner` entity)
   - Legal/financial ownership records
   - Linked to properties with ownership percentages
   - May or may not have portal access

#### B. Access Control Model

1. **Portal User Access** via new `CustomerPropertyAccess` entity:
   ```sql
   customer_property_access:
   - customer_id
   - property_id
   - access_type (OWNER, DELEGATED, VIEW_ONLY)
   - granted_by (who gave access)
   - granted_date
   ```

2. **Property Ownership** remains in `PropertyOwner`:
   - Legal ownership percentages
   - Financial responsibilities
   - Management rights

#### C. User Creation Workflow

1. **Property Owner Portal Users**:
   - Create `Customer` record with portal login
   - Grant property access via `CustomerPropertyAccess`
   - Optionally create `PropertyOwner` if they legally own

2. **Delegated Access** (like Achal for Prestvale):
   - Create `Customer` record for Achal
   - Grant access to Prestvale properties via `CustomerPropertyAccess`
   - No `PropertyOwner` record needed

3. **Employee Access** (like Piyush):
   - Create `Customer` record for Piyush
   - Grant access to relevant properties
   - Mark as employee/agent in customer type

## Detailed Analysis of Current User Creation Process

### Customer Creation Workflow (`CustomerController.java:1286`)

The current `addCustomer` method shows several problematic patterns:

1. **Forced User Association** (`CustomerController.java:1342`)
   ```java
   customer.setUser(user); // Links every customer to internal User
   ```
   **Problem**: This creates a mandatory relationship between external customers and internal CRM users.

2. **Automatic Property Owner Classification** (`CustomerController.java:1361-1367`)
   ```java
   } else if ("PROPERTY_OWNER".equals(finalCustomerType)) {
       customer.setIsPropertyOwner(true);
       customer.setCustomerType(CustomerType.PROPERTY_OWNER);
   ```
   **Problem**: Anyone created as "PROPERTY_OWNER" type automatically gets owner status.

3. **Missing Delegation Mechanism**
   - No way to create users with property access but without ownership
   - No mechanism to grant Achal access to Prestvale/Uday properties without making him an owner

### Property Assignment Logic (`CustomerPropertyAssignmentService.java:24`)

The system uses `CustomerPropertyAssignment` for tracking relationships:
- **Assignment Types**: OWNER, TENANT, CONTRACTOR, MANAGER
- **Usage**: Property filtering is based on these assignments (`CustomerController.java:218-224`)

**Critical Finding**: The system already has the infrastructure for non-ownership access through the `MANAGER` assignment type, but it's not being used properly in the UI workflows.

### Authentication Role Mapping (`CustomerUserDetails.java:109`)

Portal users get roles based on customer type:
- `PROPERTY_OWNER` → `ROLE_PROPERTY_OWNER`
- `TENANT` → `ROLE_TENANT`
- `CONTRACTOR` → `ROLE_CONTRACTOR`

**Issue**: No role for "delegated access" or "property manager" that isn't an owner.

## Immediate Actions Needed

### 1. **Audit Existing Data**
   - Identify users like Piyush who are incorrectly classified as property owners
   - Find customers who should have delegated access rather than ownership

### 2. **Implement Proper User Types**
   Add new customer types to handle delegation:
   ```java
   // In CustomerType.java
   PROPERTY_MANAGER("Property Manager", "property_manager"),
   DELEGATED_USER("Delegated User", "delegated_user"),
   ```

### 3. **Fix User Creation Workflow**
   - Remove mandatory User association from Customer entity
   - Add property access assignment without ownership
   - Create UI for delegated access setup

### 4. **Separate Ownership from Portal Access**
   - Use `CustomerPropertyAssignment` with `MANAGER` type for delegated access
   - Reserve `OWNER` assignments for actual legal/financial ownership
   - Update security configuration to allow `ROLE_PROPERTY_MANAGER` access

### 5. **Clean Up Customer Creation**
   ```java
   // Instead of automatic property owner creation
   if ("PROPERTY_ACCESS".equals(finalCustomerType)) {
       customer.setCustomerType(CustomerType.DELEGATED_USER);
       // Don't set isPropertyOwner=true
       // Create assignment with MANAGER type instead of OWNER
   }
   ```

## Specific Solutions for Current Issues

### For Piyush (Employee of Owner)
1. Change customer type from `PROPERTY_OWNER` to `DELEGATED_USER`
2. Create `CustomerPropertyAssignment` with type `MANAGER`
3. Remove any `PropertyOwner` records for Piyush

### For Achal (Needs Prestvale/Uday Access)
1. Create Customer record with type `DELEGATED_USER`
2. Create `CustomerPropertyAssignment` records linking Achal to Prestvale/Uday properties with type `MANAGER`
3. Ensure `ROLE_PROPERTY_MANAGER` has appropriate portal access

## Implementation Priority

1. **Immediate**: Add `DELEGATED_USER` and `PROPERTY_MANAGER` customer types
2. **High**: Update role mapping to support delegated access
3. **High**: Create UI for assigning property access without ownership
4. **Medium**: Audit and reclassify existing incorrectly created users
5. **Medium**: Remove mandatory User-Customer relationship

## Impact Analysis: Role-Based Access Ramifications

### Critical Areas Affected by Proposed Changes

#### 1. **SecurityConfig Role Mappings** (`SecurityConfig.java`)

**Current Role Dependencies:**
- `/property-owner/**` → `ROLE_PROPERTY_OWNER`, `ROLE_MANAGER`, `ROLE_ADMIN`
- `/portfolio/**` → Includes `ROLE_PROPERTY_OWNER`, `ROLE_CUSTOMER`
- `/employee/property/**` → Includes `PROPERTY_OWNER` role
- `/property/**` → Includes `PROPERTY_OWNER` role

**Impact**: Adding `PROPERTY_MANAGER` role requires updating **11 security matchers** in SecurityConfig.

#### 2. **Authentication Role Determination**

**CustomerUserDetails.java:109** - Role mapping logic:
```java
if (Boolean.TRUE.equals(customer.getIsPropertyOwner())) {
    return "ROLE_PROPERTY_OWNER";
}
```

**CustomerLoginSuccessHandler.java:196** - Dashboard routing:
```java
if (Boolean.TRUE.equals(customer.getIsPropertyOwner())) {
    return "/property-owner/dashboard";
}
```

**Impact**: Both services need logic to handle new `PROPERTY_MANAGER` customer type.

#### 3. **Service Layer Dependencies**

**CustomerServiceImpl.java:203** - Property owner identification:
```java
public List<Customer> findPropertyOwners() {
    List<Customer> allPropertyOwners = customerRepository.findByCustomerType(CustomerType.PROPERTY_OWNER);
}
```

**Repository Layer** - Multiple queries depend on property owner flags:
- `findByIsPropertyOwner(Boolean)`
- `findPropertyOwners()`
- `findPropertyOwnersByCity(String)`

**Impact**: Service methods need to be updated to include delegated users in appropriate contexts.

#### 4. **Controller Access Patterns**

**46 files** contain role-based access checks. Key patterns:
- Property owner email functionality
- Portfolio access controls
- Financial data access
- Property management features

**CustomerController.java:534** - Email filtering:
```java
.filter(customer -> customer != null && Boolean.TRUE.equals(customer.getIsPropertyOwner()))
```

#### 5. **Frontend Route Access**

**Template Access Implications:**
- Property owner dashboard access
- Portfolio creation and management
- Financial report generation
- Property assignment interfaces

### Breaking Changes Required

#### **High Impact Changes**

1. **SecurityConfig Updates** (11 locations)
   ```java
   // OLD
   .hasAnyAuthority("ROLE_PROPERTY_OWNER", "ROLE_MANAGER")

   // NEW
   .hasAnyAuthority("ROLE_PROPERTY_OWNER", "ROLE_PROPERTY_MANAGER", "ROLE_MANAGER")
   ```

2. **Role Determination Logic** (3 authentication classes)
   ```java
   // Add to CustomerUserDetails.java
   case PROPERTY_MANAGER:
       return "ROLE_PROPERTY_MANAGER";
   case DELEGATED_USER:
       return "ROLE_PROPERTY_MANAGER"; // Or new role
   ```

3. **Service Method Updates** (CustomerServiceImpl and others)
   ```java
   // findPropertyOwners() may need to include delegated users
   // Or create separate findPropertyAccessUsers() method
   ```

#### **Medium Impact Changes**

1. **Controller Authorization Checks** (20+ files)
   - Email service access
   - Portfolio management
   - Financial data access

2. **Database Queries** (Repository layer)
   - Property owner lookups need to include delegated access
   - Assignment-based queries vs. customer type queries

#### **Low Impact Changes**

1. **Frontend Templates**
   - Dashboard routing logic
   - Menu item visibility
   - Feature access controls

### Migration Strategy & Compatibility

#### **Phase 1: Additive Changes (Non-Breaking)**
1. Add new CustomerType enums (`PROPERTY_MANAGER`, `DELEGATED_USER`)
2. Add new roles to SecurityConfig alongside existing ones
3. Update authentication services to handle new types
4. Create new assignment workflows

#### **Phase 2: Data Migration**
1. Identify incorrectly classified property owners
2. Convert to delegated users with appropriate assignments
3. Test access controls with converted users

#### **Phase 3: Cleanup (Breaking)**
1. Remove redundant boolean flags (`isPropertyOwner`)
2. Consolidate authentication logic
3. Update service methods to use assignments over customer types

### Estimated Effort

- **Critical Files**: 11 (SecurityConfig, Authentication services)
- **High Impact Files**: 20+ (Controllers with role checks)
- **Medium Impact Files**: 46 (All files with role-based access)
- **Database Changes**: CustomerType enum, potential data migration
- **Testing Required**: All property owner workflows, portfolio access, financial reports

### Risk Assessment

**High Risk**: Breaking existing property owner access during migration
**Medium Risk**: Performance impact from assignment-based queries vs. customer type queries
**Low Risk**: Frontend routing and template access

## CRITICAL UPDATE: PayProp Integration Conflict

### The Real Problem Revealed

Your insight about PayProp imports has uncovered the **core issue**: When PayProp syncs real legal property owners, they will conflict with the current "fictional" property owners (like Piyush and others who are actually employees/delegated users).

### PayProp Import Process Analysis

**How PayProp Creates Property Owners:**
1. **PayPropRawBeneficiariesImportService.java:48** - Imports real beneficiaries from `/export/beneficiaries?owners=true`
2. **DataSource.PAYPROP** - PayProp-imported customers are tracked with `dataSource = PAYPROP`
3. **CustomerType.PROPERTY_OWNER** - PayProp beneficiaries become Customer records with `PROPERTY_OWNER` type

### Data Integrity Crisis

**Current State:**
- Piyush (employee) → `Customer` with `CustomerType.PROPERTY_OWNER` and `dataSource = MANUAL`
- Future: Real Owner from PayProp → `Customer` with `CustomerType.PROPERTY_OWNER` and `dataSource = PAYPROP`

**Conflict Scenarios:**
1. **Same Property, Multiple "Owners"**: Property has both fictional owner (Piyush) and real PayProp owner
2. **Role Confusion**: System can't distinguish between legal owners and delegated users
3. **Access Control Chaos**: Both real and fictional owners get same permissions
4. **Reporting Inconsistency**: Financial reports will show incorrect ownership data

### Solution Framework

#### **Immediate Action Required: Data Source Separation**

The system already has the infrastructure to solve this via `DataSource` enum:

```java
// Current fictional owners
customer.dataSource = DataSource.MANUAL
customer.customerType = CustomerType.PROPERTY_OWNER  // WRONG

// Should be
customer.dataSource = DataSource.MANUAL
customer.customerType = CustomerType.DELEGATED_USER  // NEW TYPE NEEDED
```

#### **Recommended Implementation**

1. **Add New Customer Type**:
   ```java
   // In CustomerType.java
   DELEGATED_USER("Delegated User", "delegated_user"),
   ```

2. **Update Role Mapping**:
   ```java
   // In CustomerUserDetails.java
   case DELEGATED_USER:
       return "ROLE_PROPERTY_OWNER"; // Same permissions, different classification
   ```

3. **Data Migration Strategy**:
   ```java
   // Convert fictional owners to delegated users
   UPDATE customers
   SET customer_type = 'DELEGATED_USER'
   WHERE data_source = 'MANUAL'
   AND customer_type = 'PROPERTY_OWNER'
   AND [identified as employee/delegated user]
   ```

### Migration Benefits

**This approach solves multiple problems:**
1. **PayProp Compatibility**: Real owners come in as `PROPERTY_OWNER` + `PAYPROP`
2. **Access Preservation**: Delegated users keep same permissions via role mapping
3. **Data Integrity**: Clear separation between legal ownership and portal access
4. **Future-Proof**: When PayProp imports real owners, no conflicts occur

### Implementation Phases

#### **Phase 1: Prepare for PayProp (Critical)**
1. Add `DELEGATED_USER` customer type
2. Update authentication services to map `DELEGATED_USER` → `ROLE_PROPERTY_OWNER`
3. Test with existing users to ensure no permission loss

#### **Phase 2: Data Classification**
1. Audit existing `PROPERTY_OWNER` customers with `dataSource = MANUAL`
2. Identify which are actually employees/delegated users (like Piyush)
3. Convert to `DELEGATED_USER` type

#### **Phase 3: PayProp Integration**
1. Import real property owners from PayProp as `PROPERTY_OWNER` + `PAYPROP`
2. Verify no conflicts with existing data
3. Update property assignments to reflect real ownership

### Estimated Impact

**Low Risk Solution**: Adding `DELEGATED_USER` type with same role mapping as `PROPERTY_OWNER` means:
- **Zero permission changes** for existing users
- **No UI changes** required initially
- **Minimal code changes** (1 enum addition + 1 case statement)
- **Clean data separation** for PayProp integration

## Next Steps

The PayProp integration deadline makes this **urgent**. The simplest solution is to add `DELEGATED_USER` customer type that maps to the same role as property owners, allowing clean data separation without breaking existing functionality.
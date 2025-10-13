# Block Functionality for Property Owners - Implementation Guide

## Overview

This document describes the complete implementation of block functionality for property owners and their delegated users, bringing the full feature set from the employee side to the customer-login side.

## Architecture Summary

### Data Model

The block functionality uses a many-to-many relationship structure:

```
Portfolio ← BlockPortfolioAssignment → Block ← PropertyBlockAssignment → Property
```

Key entities:
- **Block**: Represents a grouping of properties (building, estate, complex, etc.)
- **BlockPortfolioAssignment**: Junction table linking blocks to portfolios
- **PropertyBlockAssignment**: Junction table linking properties to blocks (independent of portfolio)

### Controller Structure

#### Employee Side (Original)
- **BlockController** (`/portfolio/internal/blocks`) - Full CRUD operations for employees
- **BlockViewController** (`/blocks`) - Standalone block management

#### Owner Side (New Implementation)
- **PropertyOwnerBlockController** (`/customer-login/blocks`) - Full CRUD operations for owners with authorization

## Implementation Details

### 1. PropertyOwnerBlockController

**Location**: `src/main/java/site/easy/to/build/crm/controller/PropertyOwnerBlockController.java`

**Key Features**:
- Authorization checks to ensure owners only access their own blocks
- Support for both PROPERTY_OWNER and DELEGATED_USER customer types
- Full CRUD operations (create, read, update, delete)
- Property assignment viewing
- Block statistics and analytics

**Authorization Model**:

```java
// Property Owners: Check if block belongs to their portfolio
if (customer.getCustomerType() == CustomerType.PROPERTY_OWNER) {
    return portfolio.getPropertyOwnerId().equals(customer.getCustomerId().longValue());
}

// Delegated Users: Check if they have access to any property in the block
if (customer.getCustomerType() == CustomerType.DELEGATED_USER) {
    // Verify user has access to at least one property in the block
    return hasAccessToBlockProperty(customer, block);
}
```

**Endpoints**:

| Method | Endpoint | Description | Owner Only |
|--------|----------|-------------|------------|
| GET | `/customer-login/blocks` | View all accessible blocks | No |
| GET | `/customer-login/blocks/{id}` | View block details | No |
| GET | `/customer-login/blocks/{id}/edit` | Edit block page | Yes |
| GET | `/customer-login/blocks/assignment-centre` | Assignment centre | No |
| GET | `/customer-login/blocks/api/all` | Get all accessible blocks (API) | No |
| GET | `/customer-login/blocks/api/{id}` | Get block details (API) | No |
| GET | `/customer-login/blocks/api/{id}/properties` | Get properties in block (API) | No |
| POST | `/customer-login/blocks/api/create` | Create new block (API) | Yes |
| PUT | `/customer-login/blocks/api/{id}` | Update block (API) | Yes |
| DELETE | `/customer-login/blocks/api/{id}` | Delete block (API) | Yes |
| GET | `/customer-login/blocks/api/portfolio/{portfolioId}` | Get blocks by portfolio (API) | No |
| GET | `/customer-login/blocks/api/assignment-overview` | Get assignment overview (API) | No |

### 2. UI Templates

#### All Blocks Page
**Location**: `src/main/resources/templates/customer-login/blocks/all-blocks.html`

**Features**:
- Grid and table view toggle
- Filter by active status
- Statistics cards showing total blocks, properties, etc.
- Owner mode banner showing user type
- Different permissions for owners vs. delegated users

#### Block Details Page
**Location**: `src/main/resources/templates/customer-login/blocks/block-details.html`

**Features**:
- Block information display
- Address details
- Property list within block
- Capacity utilization (if max properties set)
- Edit button (owners only)
- Click-through to individual properties

#### Edit Block Page
**Location**: `src/main/resources/templates/customer-login/blocks/edit-block.html`

**Features**:
- Form to edit block name, description, type
- Address information editing
- Save/cancel buttons
- Owner-only access

#### Assignment Centre
**Location**: `src/main/resources/templates/customer-login/blocks/assignment-centre.html`

**Features**:
- View all blocks with their properties
- See unassigned properties
- Visual overview of property-block assignments
- Read-only for delegated users

### 3. Dashboard Integration

**Location**: `src/main/resources/templates/portfolio/property-owner-dashboard.html`

**Integration**:
- Added "My Blocks" section showing first 3 blocks
- Click-through to view all blocks
- Displays block count, property count, and block type
- Loads dynamically via AJAX

**Code Snippet**:
```javascript
function loadBlocksOverview() {
    fetch('/customer-login/blocks/api/all')
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                displayBlocksOverview(data.blocks);
            }
        });
}
```

### 4. Navigation Menu

**Location**: `src/main/resources/templates/general/left-sidebar.html`

**Menu Structure**:
```
Portfolios
├── Portfolio Dashboard
├── Create Portfolio
├── All Portfolios
├── Assign Properties
├── Block Management          ← NEW
├── Block Assignments         ← NEW
└── Portfolio Maintenance
```

The existing "Blocks" section in the sidebar provides access to:
- All Blocks
- Create Block
- Service Charges
- Cost Allocation
- Block Expenses
- Reserve Funds

## Permission Matrix

| Operation | Property Owner | Delegated User | Employee |
|-----------|---------------|----------------|----------|
| View own blocks | ✅ | ✅ | ✅ |
| Create blocks | ✅ | ❌ | ✅ |
| Edit own blocks | ✅ | ❌ | ✅ |
| Delete own blocks | ✅ | ❌ | ✅ |
| View properties in blocks | ✅ | ✅ (filtered) | ✅ |
| Assign properties to blocks | via employee | ❌ | ✅ |
| View other owner's blocks | ❌ | ❌ | ✅ |

**Note**: Delegated users only see blocks containing properties they have access to.

## Data Flow

### Loading Blocks for Owners

```
1. User navigates to /customer-login/blocks
2. PropertyOwnerBlockController.viewAllBlocks()
3. Authorization check: getLoggedInCustomer()
4. JavaScript loads blocks via API: /customer-login/blocks/api/all
5. PropertyOwnerBlockController.getAllBlocks()
6. getAccessibleBlocks(customer) filters blocks:
   - Property Owners: findPortfoliosForPropertyOwnerWithBlocks()
   - Delegated Users: findPropertiesByCustomerAssignments() + filter by block
7. Returns filtered block list
8. UI displays blocks in grid/table view
```

### Creating a Block (Owner Only)

```
1. Owner clicks "Create Block" (delegated users don't see this button)
2. JavaScript validates form data
3. POST to /customer-login/blocks/api/create with block data
4. PropertyOwnerBlockController.createBlock()
5. Verify user is PROPERTY_OWNER (403 if not)
6. Verify portfolio belongs to owner
7. Call blockService.createBlock() with portfolio ID
8. Return success with new block ID
9. UI reloads block list
```

## Comparison: Employee vs. Owner Implementation

### Similarities
- Same data model and service layer
- Same BlockPortfolioAssignment and PropertyBlockAssignment tables
- Full CRUD operations available
- Block statistics and analytics
- Property assignment viewing

### Differences

| Aspect | Employee Side | Owner Side |
|--------|--------------|------------|
| URL Base | `/portfolio/internal/blocks` and `/blocks` | `/customer-login/blocks` |
| Authorization | Role-based (ROLE_MANAGER, ROLE_EMPLOYEE) | Data-based (ownership filtering) |
| Scope | All blocks in system | Only blocks in owner's portfolios |
| Property Assignment | Can assign/unassign | View only |
| Delegated Access | N/A | Filtered by property access |
| PayProp Sync | Full control | View only |
| UI Theme | Admin theme | Owner portal theme |

## Key Security Features

1. **Ownership Verification**: Every request verifies the customer owns the portfolio containing the block
2. **Delegated User Filtering**: Delegated users only see blocks containing properties they have access to
3. **Operation Restrictions**: Only property owners can create, edit, or delete blocks
4. **Portfolio Boundary Enforcement**: Owners cannot see or modify blocks in other owners' portfolios
5. **Property Filtering**: Delegated users see filtered property lists within blocks

## Testing Scenarios

### Test Case 1: Property Owner Views Blocks
```
Given: User is logged in as PROPERTY_OWNER
And: User owns Portfolio A with Block 1 and Block 2
When: User navigates to /customer-login/blocks
Then: User sees Block 1 and Block 2
And: User can click "View All Blocks"
And: User can create new blocks
```

### Test Case 2: Delegated User Views Blocks
```
Given: User is logged in as DELEGATED_USER
And: User has access to Property X in Block 1
And: Block 2 contains no properties user can access
When: User navigates to /customer-login/blocks
Then: User sees only Block 1
And: In Block 1, user only sees Property X (not other properties)
And: User cannot create, edit, or delete blocks
```

### Test Case 3: Owner Creates Block
```
Given: User is logged in as PROPERTY_OWNER
When: User clicks "Create Block"
And: Fills in block name, address, and selects portfolio
And: Clicks "Save"
Then: New block is created
And: Block appears in block list
And: Block has owner's portfolio ID
```

### Test Case 4: Cross-Owner Security
```
Given: Owner A owns Portfolio A with Block A
And: Owner B owns Portfolio B with Block B
When: Owner B attempts to access Block A via direct URL
Then: Access is denied (403 Forbidden)
And: Error message shows "Access denied"
```

### Test Case 5: Assignment Visibility
```
Given: Employee assigns Property P to Block B
And: Block B belongs to Owner O's portfolio
When: Owner O views Block B
Then: Property P appears in the block's property list
And: Assignment is visible to both employee and owner
```

## Service Layer Compatibility

The owner-facing controller uses the **same service layer** as the employee side:

- **PortfolioBlockService**: Block CRUD operations
- **PortfolioService**: Portfolio retrieval with blocks
- **PropertyService**: Property access checking
- **PropertyBlockAssignmentRepository**: Property-block relationship queries

This ensures:
- ✅ **Data consistency** between employee and owner views
- ✅ **No duplication** of business logic
- ✅ **Assignments visible to both** employees and owners
- ✅ **Single source of truth** for all block data

## Database Queries

Key queries used by owner-facing block functionality:

```sql
-- Get blocks for property owner (via portfolios)
SELECT DISTINCT b.* FROM blocks b
INNER JOIN portfolios p ON b.portfolio_id = p.id
WHERE p.property_owner_id = :ownerId AND b.is_active = 'Y'

-- Get blocks for delegated user (via property access)
SELECT DISTINCT b.* FROM blocks b
INNER JOIN property_block_assignments pba ON b.id = pba.block_id
INNER JOIN customer_property_assignments cpa ON pba.property_id = cpa.property_id
WHERE cpa.customer_id = :customerId AND b.is_active = 'Y'

-- Get properties in block (filtered for delegated users)
SELECT p.* FROM properties p
INNER JOIN property_block_assignments pba ON p.id = pba.property_id
WHERE pba.block_id = :blockId
AND pba.is_active = true
ORDER BY pba.display_order
```

## Future Enhancements

Potential additions to owner block functionality:

1. **Service Charge Management**: Allow owners to view and manage service charges
2. **Reserve Fund Tracking**: Show reserve fund balances and contributions
3. **Block Expenses**: View block-level maintenance and expenses
4. **Cost Allocation**: See how costs are allocated among properties
5. **Block Documents**: Upload and view block-related documents
6. **Block Analytics**: More detailed statistics and reports
7. **Block Notifications**: Alerts for block-related events
8. **Multi-language Support**: Translate block UI for international owners

## Troubleshooting

### Common Issues

**Issue 1: Owner cannot see blocks**
- **Cause**: Portfolio not properly linked to customer
- **Fix**: Verify `portfolio.propertyOwnerId` matches `customer.customerId`

**Issue 2: Delegated user sees no blocks**
- **Cause**: No property-customer assignments or properties not in blocks
- **Fix**: Check `customer_property_assignments` and `property_block_assignments` tables

**Issue 3: 403 Forbidden when accessing block**
- **Cause**: Authorization check failing
- **Fix**: Verify user owns portfolio containing the block, check logs for specific failure

**Issue 4: Blocks not appearing on dashboard**
- **Cause**: AJAX call failing or authentication issue
- **Fix**: Check browser console for errors, verify `/customer-login/blocks/api/all` endpoint

## API Response Examples

### GET /customer-login/blocks/api/all

```json
{
  "success": true,
  "blocks": [
    {
      "id": 1,
      "name": "Riverside Apartments",
      "description": "Modern apartment block",
      "blockType": "BUILDING",
      "addressLine1": "123 River St",
      "city": "London",
      "postcode": "SW1A 1AA",
      "portfolioId": 5,
      "portfolioName": "Central London Portfolio",
      "propertyCount": 12,
      "maxProperties": 15,
      "availableCapacity": 3,
      "isActive": "Y",
      "payPropTagNames": "Owner-1",
      "syncStatus": "synced"
    }
  ],
  "total": 1
}
```

### GET /customer-login/blocks/api/{id}/properties

```json
{
  "success": true,
  "blockId": 1,
  "blockName": "Riverside Apartments",
  "properties": [
    {
      "id": 101,
      "propertyName": "Apt 1A",
      "propertyType": "APARTMENT",
      "addressLine1": "123 River St, Unit 1A",
      "city": "London",
      "postcode": "SW1A 1AA",
      "status": "OCCUPIED",
      "blockId": 1,
      "blockName": "Riverside Apartments"
    },
    {
      "id": 102,
      "propertyName": "Apt 1B",
      "propertyType": "APARTMENT",
      "status": "VACANT",
      "blockId": 1,
      "blockName": "Riverside Apartments"
    }
  ],
  "count": 2
}
```

## Conclusion

The block functionality has been successfully implemented for property owners and delegated users with:

- ✅ Full feature parity with employee side
- ✅ Comprehensive authorization and security
- ✅ Clean separation of concerns
- ✅ Shared service layer for consistency
- ✅ User-friendly UI with owner portal theme
- ✅ Support for both owners and delegated users
- ✅ Visible assignments across both sides

Property owners can now fully manage their blocks, view properties within blocks, and see block-level information, while delegated users have appropriate read access to blocks containing their assigned properties.

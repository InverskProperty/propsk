# PayProp API Tags - Comprehensive Guide

## Overview

Tags in the PayProp API provide a flexible way to categorize and organize entities (tenants, properties, and beneficiaries). They allow for better data management and filtering capabilities across your property management portfolio.

## Tag System Architecture

### Supported Entity Types
- **tenant** - Tag individual tenants
- **property** - Tag properties in your portfolio
- **beneficiary** - Tag beneficiaries for payment organization

### Tag Identification
- **External ID**: Alphanumeric identifier (10-32 characters, pattern: `^[a-zA-Z0-9]+$`)
- **Name**: Human-readable tag name (1-32 characters, pattern: `^[a-zA-Z0-9_\-\s]+$`)

## API Endpoints Reference

### 1. Get Tags
**Endpoint**: `GET /tags`

**Purpose**: Retrieve all tags with optional filtering

**Parameters**:
- `rows` (optional): Number of rows to return (1-25)
- `page` (optional): Page number (minimum: 1)
- `external_id` (optional): Filter by specific tag external ID
- `name` (optional): Filter tags by name
- `entity_type` (optional): Filter tags linked to specific entity type
- `entity_id` (optional): Filter tags linked to specific entity (use with entity_type)

**Example Request**:
```bash
GET /api/agency/v1.1/tags?entity_type=tenant&rows=10&page=1
```

**Response Structure**:
```json
{
  "items": [
    {
      "id": "tag_external_id",
      "name": "tag_name"
    }
  ],
  "pagination": {
    "page": 1,
    "rows": 10,
    "total_rows": 25,
    "total_pages": 3
  }
}
```

### 2. Create Tag
**Endpoint**: `POST /tags`

**Purpose**: Create a new tag (returns existing tag if name already exists)

**Request Body**:
```json
{
  "name": "High Priority"
}
```

**Response**:
```json
{
  "id": "generated_external_id",
  "name": "High Priority"
}
```

**Key Behavior**: If a tag with the given name already exists, the existing tag will be returned instead of creating a duplicate.

### 3. Update Tag
**Endpoint**: `PUT /tags/{external_id}`

**Purpose**: Update a tag's name (with merge capability)

**Request Body**:
```json
{
  "name": "Updated Tag Name"
}
```

**Important**: If a tag with the new name already exists, the target tag will be merged into the existing tag.

### 4. Delete Tag
**Endpoint**: `DELETE /tags/{external_id}`

**Purpose**: Permanently delete a tag and all its entity associations

**Response**:
```json
{
  "message": "Tag has been successfully deleted."
}
```

**Critical**: This operation removes the tag and ALL its links to entities (tenants, properties, beneficiaries).

### 5. Link Tags with Entity
**Endpoint**: `POST /tags/entities/{entity_type}/{entity_id}`

**Purpose**: Associate one or more tags with a specific entity

**Parameters**:
- `entity_type`: One of `tenant`, `property`, `beneficiary`
- `entity_id`: External ID of the entity

**Request Body**:
```json
{
  "tags": [
    "tag_name_or_external_id_1",
    "tag_name_or_external_id_2"
  ]
}
```

**Response**:
```json
{
  "items": [
    {
      "id": "tag_external_id_1",
      "name": "tag_name_1"
    },
    {
      "id": "tag_external_id_2", 
      "name": "tag_name_2"
    }
  ]
}
```

### 6. Get Tagged Entities
**Endpoint**: `GET /tags/{external_id}/entities`

**Purpose**: Retrieve all entities linked to a specific tag

**Parameters**:
- `entity_type` (optional): Filter by entity type
- `sort_by` (optional): Sort by `type` or `name`
- `sort_direction` (optional): `asc` or `desc`

**Response Structure**:
```json
{
  "entities": [
    {
      "id": "entity_external_id",
      "type": "tenant",
      "name": "Entity Display Name"
    }
  ]
}
```

### 7. Delete Tag-Entity Link
**Endpoint**: `DELETE /tags/{external_id}/entities`

**Purpose**: Remove association between a tag and specific entity

**Parameters**:
- `entity_type`: Required - `tenant`, `property`, or `beneficiary`
- `entity_id`: Required - External ID of the entity

**Response**:
```json
{
  "message": "Tag link successfully removed from entity."
}
```

## Tag Management Best Practices

### 1. Tag Naming Convention
- Use descriptive, consistent naming
- Consider hierarchical naming (e.g., "Priority_High", "Priority_Medium")
- Avoid special characters except underscore, hyphen, and spaces
- Keep names concise but meaningful

### 2. Tag Organization Strategies

**By Property Type**:
- `Commercial`
- `Residential`
- `Student_Housing`

**By Status**:
- `Active`
- `Maintenance_Required`
- `Under_Review`

**By Priority**:
- `High_Priority`
- `Standard`
- `Low_Priority`

**By Geographic Location**:
- `Downtown`
- `Suburbs`
- `City_Center`

### 3. Entity Tagging Workflows

**For Tenants**:
```bash
# Tag a tenant as high priority
POST /tags/entities/tenant/zJBv2E5aJQ
{
  "tags": ["High_Priority", "VIP_Client"]
}
```

**For Properties**:
```bash
# Tag a property with multiple characteristics
POST /tags/entities/property/D8eJPwZG7j
{
  "tags": ["Commercial", "Downtown", "Maintenance_Required"]
}
```

**For Beneficiaries**:
```bash
# Tag a beneficiary for payment categorization
POST /tags/entities/beneficiary/G1OByGaaXM
{
  "tags": ["International_Payment", "Monthly_Recipient"]
}
```

## Deletion and Data Management

### Tag Deletion Impact
When you delete a tag:
1. **Tag Record**: Permanently removed from the system
2. **Entity Links**: All associations with tenants, properties, and beneficiaries are removed
3. **No Recovery**: Deletion is permanent and cannot be undone
4. **No Cascade**: Entities themselves are not affected, only the tag associations

### Safe Deletion Workflow
1. **Check Associations**: Use `GET /tags/{external_id}/entities` to see what entities are linked
2. **Document Impact**: Record which entities will lose the tag
3. **Consider Alternatives**: Maybe rename or merge instead of delete
4. **Execute Deletion**: Use `DELETE /tags/{external_id}` when certain

### Bulk Operations Considerations
- No native bulk delete endpoint exists
- For multiple tag deletions, implement client-side iteration
- Consider rate limiting (API allows max 5 requests per second)

## Error Handling

### Common Error Responses

**400 Bad Request**:
```json
{
  "errors": [
    {
      "message": "Invalid tag name format"
    }
  ],
  "status": 400
}
```

**403 Forbidden**:
```json
{
  "errors": [
    {
      "message": "Insufficient privileges"
    }
  ],
  "status": 403
}
```

**404 Not Found**:
```json
{
  "errors": [
    {
      "message": "Tag not found"
    }
  ],
  "status": 404
}
```

## Integration Examples

### Complete Tag Management Flow

```javascript
// 1. Create a new tag
const createTag = async (tagName) => {
  const response = await fetch('/api/agency/v1.1/tags', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'APIkey YOUR_API_KEY'
    },
    body: JSON.stringify({ name: tagName })
  });
  return response.json();
};

// 2. Link tag to multiple entities
const linkTagToEntities = async (tagId, entityType, entityIds) => {
  const promises = entityIds.map(entityId => 
    fetch(`/api/agency/v1.1/tags/entities/${entityType}/${entityId}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'APIkey YOUR_API_KEY'
      },
      body: JSON.stringify({ tags: [tagId] })
    })
  );
  return Promise.all(promises);
};

// 3. Get all entities with specific tag
const getTaggedEntities = async (tagId, entityType = null) => {
  const params = entityType ? `?entity_type=${entityType}` : '';
  const response = await fetch(`/api/agency/v1.1/tags/${tagId}/entities${params}`, {
    headers: {
      'Authorization': 'APIkey YOUR_API_KEY'
    }
  });
  return response.json();
};

// 4. Safe tag deletion with confirmation
const safeDeleteTag = async (tagId) => {
  // First, check what entities are linked
  const linkedEntities = await getTaggedEntities(tagId);
  
  if (linkedEntities.entities.length > 0) {
    console.log(`Warning: Tag is linked to ${linkedEntities.entities.length} entities`);
    // Implement confirmation logic here
  }
  
  // Proceed with deletion
  const response = await fetch(`/api/agency/v1.1/tags/${tagId}`, {
    method: 'DELETE',
    headers: {
      'Authorization': 'APIkey YOUR_API_KEY'
    }
  });
  return response.json();
};
```

## Advanced Use Cases

### 1. Tag-Based Filtering for Reports
Use tags to filter entities in other API endpoints that support entity filtering.

### 2. Automated Tag Assignment
Implement business logic to automatically assign tags based on entity properties:
- High-value properties get "Premium" tag
- Overdue tenants get "Collection_Required" tag
- International beneficiaries get "International" tag

### 3. Tag Hierarchy Management
While the API doesn't enforce hierarchy, implement naming conventions:
- `Category_Subcategory` (e.g., `Property_Commercial`, `Property_Residential`)
- Use consistent separators for programmatic parsing

### 4. Bulk Tag Operations
```javascript
// Bulk tag assignment to multiple entities
const bulkTagAssignment = async (tagNames, entityType, entityIds) => {
  const operations = [];
  
  for (const entityId of entityIds) {
    operations.push(
      fetch(`/api/agency/v1.1/tags/entities/${entityType}/${entityId}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'APIkey YOUR_API_KEY'
        },
        body: JSON.stringify({ tags: tagNames })
      })
    );
  }
  
  return Promise.all(operations);
};
```

## Security and Permissions

### Authentication Requirements
- All tag operations require valid API authentication
- Use either API Key or OAuth 2.0 Bearer token
- API Key format: `Authorization: APIkey YOUR_API_KEY`
- Bearer token format: `Authorization: Bearer YOUR_ACCESS_TOKEN`

### Permission Levels
- Tag operations require appropriate permissions on the associated entities
- Ensure your API credentials have access to the entity types you're tagging
- Some operations may be restricted based on user roles (agent vs agency)

## Pagination Constraints and Limitations

### Tag List Pagination (`GET /tags`)
- **Maximum Rows Per Page**: 25 (hard limit)
- **Minimum Rows**: 1
- **Page Numbering**: Starts at 1 (not 0-indexed)
- **Default Behavior**: If no `rows` specified, API uses default pagination
- **Total Tracking**: Response includes `total_rows` and `total_pages` for navigation

**Pagination Response Structure**:
```json
{
  "items": [...],
  "pagination": {
    "page": 1,        // Current page (1-indexed)
    "rows": 25,       // Items per page (max 25)
    "total_rows": 150, // Total items available
    "total_pages": 6   // Total pages available
  }
}
```

**Pagination Constraints That Can Cause Issues**:

1. **Row Limit Enforcement**: Requesting `rows=100` will be rejected
2. **Page Boundary Errors**: Requesting page beyond `total_pages` returns 400 error
3. **Zero-Based Confusion**: Using `page=0` will cause validation error
4. **Large Dataset Handling**: No way to get all tags in single request if you have >25 tags

### Entity Linking Constraints

**Tag Array Limitations**:
- No explicit limit mentioned in spec for tags array size in link requests
- However, request body size limits may apply
- Recommend batching large tag assignments

**Entity ID Validation**:
- Must be 10-32 characters
- Must match pattern `^[a-zA-Z0-9]+# PayProp API Tags - Comprehensive Guide

## Overview

Tags in the PayProp API provide a flexible way to categorize and organize entities (tenants, properties, and beneficiaries). They allow for better data management and filtering capabilities across your property management portfolio.

## Tag System Architecture

### Supported Entity Types
- **tenant** - Tag individual tenants
- **property** - Tag properties in your portfolio
- **beneficiary** - Tag beneficiaries for payment organization

### Tag Identification
- **External ID**: Alphanumeric identifier (10-32 characters, pattern: `^[a-zA-Z0-9]+$`)
- **Name**: Human-readable tag name (1-32 characters, pattern: `^[a-zA-Z0-9_\-\s]+$`)

## API Endpoints Reference

### 1. Get Tags
**Endpoint**: `GET /tags`

**Purpose**: Retrieve all tags with optional filtering

**Parameters**:
- `rows` (optional): Number of rows to return (1-25)
- `page` (optional): Page number (minimum: 1)
- `external_id` (optional): Filter by specific tag external ID
- `name` (optional): Filter tags by name
- `entity_type` (optional): Filter tags linked to specific entity type
- `entity_id` (optional): Filter tags linked to specific entity (use with entity_type)

**Example Request**:
```bash
GET /api/agency/v1.1/tags?entity_type=tenant&rows=10&page=1
```

**Response Structure**:
```json
{
  "items": [
    {
      "id": "tag_external_id",
      "name": "tag_name"
    }
  ],
  "pagination": {
    "page": 1,
    "rows": 10,
    "total_rows": 25,
    "total_pages": 3
  }
}
```

### 2. Create Tag
**Endpoint**: `POST /tags`

**Purpose**: Create a new tag (returns existing tag if name already exists)

**Request Body**:
```json
{
  "name": "High Priority"
}
```

**Response**:
```json
{
  "id": "generated_external_id",
  "name": "High Priority"
}
```

**Key Behavior**: If a tag with the given name already exists, the existing tag will be returned instead of creating a duplicate.

### 3. Update Tag
**Endpoint**: `PUT /tags/{external_id}`

**Purpose**: Update a tag's name (with merge capability)

**Request Body**:
```json
{
  "name": "Updated Tag Name"
}
```

**Important**: If a tag with the new name already exists, the target tag will be merged into the existing tag.

### 4. Delete Tag
**Endpoint**: `DELETE /tags/{external_id}`

**Purpose**: Permanently delete a tag and all its entity associations

**Response**:
```json
{
  "message": "Tag has been successfully deleted."
}
```

**Critical**: This operation removes the tag and ALL its links to entities (tenants, properties, beneficiaries).

### 5. Link Tags with Entity
**Endpoint**: `POST /tags/entities/{entity_type}/{entity_id}`

**Purpose**: Associate one or more tags with a specific entity

**Parameters**:
- `entity_type`: One of `tenant`, `property`, `beneficiary`
- `entity_id`: External ID of the entity

**Request Body**:
```json
{
  "tags": [
    "tag_name_or_external_id_1",
    "tag_name_or_external_id_2"
  ]
}
```

**Response**:
```json
{
  "items": [
    {
      "id": "tag_external_id_1",
      "name": "tag_name_1"
    },
    {
      "id": "tag_external_id_2", 
      "name": "tag_name_2"
    }
  ]
}
```

### 6. Get Tagged Entities
**Endpoint**: `GET /tags/{external_id}/entities`

**Purpose**: Retrieve all entities linked to a specific tag

**Parameters**:
- `entity_type` (optional): Filter by entity type
- `sort_by` (optional): Sort by `type` or `name`
- `sort_direction` (optional): `asc` or `desc`

**Response Structure**:
```json
{
  "entities": [
    {
      "id": "entity_external_id",
      "type": "tenant",
      "name": "Entity Display Name"
    }
  ]
}
```

### 7. Delete Tag-Entity Link
**Endpoint**: `DELETE /tags/{external_id}/entities`

**Purpose**: Remove association between a tag and specific entity

**Parameters**:
- `entity_type`: Required - `tenant`, `property`, or `beneficiary`
- `entity_id`: Required - External ID of the entity

**Response**:
```json
{
  "message": "Tag link successfully removed from entity."
}
```

## Tag Management Best Practices

### 1. Tag Naming Convention
- Use descriptive, consistent naming
- Consider hierarchical naming (e.g., "Priority_High", "Priority_Medium")
- Avoid special characters except underscore, hyphen, and spaces
- Keep names concise but meaningful

### 2. Tag Organization Strategies

**By Property Type**:
- `Commercial`
- `Residential`
- `Student_Housing`

**By Status**:
- `Active`
- `Maintenance_Required`
- `Under_Review`

**By Priority**:
- `High_Priority`
- `Standard`
- `Low_Priority`

**By Geographic Location**:
- `Downtown`
- `Suburbs`
- `City_Center`

### 3. Entity Tagging Workflows

**For Tenants**:
```bash
# Tag a tenant as high priority
POST /tags/entities/tenant/zJBv2E5aJQ
{
  "tags": ["High_Priority", "VIP_Client"]
}
```

**For Properties**:
```bash
# Tag a property with multiple characteristics
POST /tags/entities/property/D8eJPwZG7j
{
  "tags": ["Commercial", "Downtown", "Maintenance_Required"]
}
```

**For Beneficiaries**:
```bash
# Tag a beneficiary for payment categorization
POST /tags/entities/beneficiary/G1OByGaaXM
{
  "tags": ["International_Payment", "Monthly_Recipient"]
}
```

## Deletion and Data Management

### Tag Deletion Impact
When you delete a tag:
1. **Tag Record**: Permanently removed from the system
2. **Entity Links**: All associations with tenants, properties, and beneficiaries are removed
3. **No Recovery**: Deletion is permanent and cannot be undone
4. **No Cascade**: Entities themselves are not affected, only the tag associations

### Safe Deletion Workflow
1. **Check Associations**: Use `GET /tags/{external_id}/entities` to see what entities are linked
2. **Document Impact**: Record which entities will lose the tag
3. **Consider Alternatives**: Maybe rename or merge instead of delete
4. **Execute Deletion**: Use `DELETE /tags/{external_id}` when certain

### Bulk Operations Considerations
- No native bulk delete endpoint exists
- For multiple tag deletions, implement client-side iteration
- Consider rate limiting (API allows max 5 requests per second)

## Error Handling

### Common Error Responses

**400 Bad Request**:
```json
{
  "errors": [
    {
      "message": "Invalid tag name format"
    }
  ],
  "status": 400
}
```

**403 Forbidden**:
```json
{
  "errors": [
    {
      "message": "Insufficient privileges"
    }
  ],
  "status": 403
}
```

**404 Not Found**:
```json
{
  "errors": [
    {
      "message": "Tag not found"
    }
  ],
  "status": 404
}
```

## Integration Examples

### Complete Tag Management Flow

```javascript
// 1. Create a new tag
const createTag = async (tagName) => {
  const response = await fetch('/api/agency/v1.1/tags', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'APIkey YOUR_API_KEY'
    },
    body: JSON.stringify({ name: tagName })
  });
  return response.json();
};

// 2. Link tag to multiple entities
const linkTagToEntities = async (tagId, entityType, entityIds) => {
  const promises = entityIds.map(entityId => 
    fetch(`/api/agency/v1.1/tags/entities/${entityType}/${entityId}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'APIkey YOUR_API_KEY'
      },
      body: JSON.stringify({ tags: [tagId] })
    })
  );
  return Promise.all(promises);
};

// 3. Get all entities with specific tag
const getTaggedEntities = async (tagId, entityType = null) => {
  const params = entityType ? `?entity_type=${entityType}` : '';
  const response = await fetch(`/api/agency/v1.1/tags/${tagId}/entities${params}`, {
    headers: {
      'Authorization': 'APIkey YOUR_API_KEY'
    }
  });
  return response.json();
};

// 4. Safe tag deletion with confirmation
const safeDeleteTag = async (tagId) => {
  // First, check what entities are linked
  const linkedEntities = await getTaggedEntities(tagId);
  
  if (linkedEntities.entities.length > 0) {
    console.log(`Warning: Tag is linked to ${linkedEntities.entities.length} entities`);
    // Implement confirmation logic here
  }
  
  // Proceed with deletion
  const response = await fetch(`/api/agency/v1.1/tags/${tagId}`, {
    method: 'DELETE',
    headers: {
      'Authorization': 'APIkey YOUR_API_KEY'
    }
  });
  return response.json();
};
```

## Advanced Use Cases

### 1. Tag-Based Filtering for Reports
Use tags to filter entities in other API endpoints that support entity filtering.

### 2. Automated Tag Assignment
Implement business logic to automatically assign tags based on entity properties:
- High-value properties get "Premium" tag
- Overdue tenants get "Collection_Required" tag
- International beneficiaries get "International" tag

### 3. Tag Hierarchy Management
While the API doesn't enforce hierarchy, implement naming conventions:
- `Category_Subcategory` (e.g., `Property_Commercial`, `Property_Residential`)
- Use consistent separators for programmatic parsing

### 4. Bulk Tag Operations
```javascript
// Bulk tag assignment to multiple entities
const bulkTagAssignment = async (tagNames, entityType, entityIds) => {
  const operations = [];
  
  for (const entityId of entityIds) {
    operations.push(
      fetch(`/api/agency/v1.1/tags/entities/${entityType}/${entityId}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'APIkey YOUR_API_KEY'
        },
        body: JSON.stringify({ tags: tagNames })
      })
    );
  }
  
  return Promise.all(operations);
};
```

## Security and Permissions

### Authentication Requirements
- All tag operations require valid API authentication
- Use either API Key or OAuth 2.0 Bearer token
- API Key format: `Authorization: APIkey YOUR_API_KEY`
- Bearer token format: `Authorization: Bearer YOUR_ACCESS_TOKEN`

### Permission Levels
- Tag operations require appropriate permissions on the associated entities
- Ensure your API credentials have access to the entity types you're tagging
- Some operations may be restricted based on user roles (agent vs agency)


- Case-sensitive matching

### Tag Name and ID Constraints

**Tag Name Restrictions**:
- **Length**: 1-32 characters (inclusive)
- **Pattern**: `^[a-zA-Z0-9_\-\s]+# PayProp API Tags - Comprehensive Guide

## Overview

Tags in the PayProp API provide a flexible way to categorize and organize entities (tenants, properties, and beneficiaries). They allow for better data management and filtering capabilities across your property management portfolio.

## Tag System Architecture

### Supported Entity Types
- **tenant** - Tag individual tenants
- **property** - Tag properties in your portfolio
- **beneficiary** - Tag beneficiaries for payment organization

### Tag Identification
- **External ID**: Alphanumeric identifier (10-32 characters, pattern: `^[a-zA-Z0-9]+$`)
- **Name**: Human-readable tag name (1-32 characters, pattern: `^[a-zA-Z0-9_\-\s]+$`)

## API Endpoints Reference

### 1. Get Tags
**Endpoint**: `GET /tags`

**Purpose**: Retrieve all tags with optional filtering

**Parameters**:
- `rows` (optional): Number of rows to return (1-25)
- `page` (optional): Page number (minimum: 1)
- `external_id` (optional): Filter by specific tag external ID
- `name` (optional): Filter tags by name
- `entity_type` (optional): Filter tags linked to specific entity type
- `entity_id` (optional): Filter tags linked to specific entity (use with entity_type)

**Example Request**:
```bash
GET /api/agency/v1.1/tags?entity_type=tenant&rows=10&page=1
```

**Response Structure**:
```json
{
  "items": [
    {
      "id": "tag_external_id",
      "name": "tag_name"
    }
  ],
  "pagination": {
    "page": 1,
    "rows": 10,
    "total_rows": 25,
    "total_pages": 3
  }
}
```

### 2. Create Tag
**Endpoint**: `POST /tags`

**Purpose**: Create a new tag (returns existing tag if name already exists)

**Request Body**:
```json
{
  "name": "High Priority"
}
```

**Response**:
```json
{
  "id": "generated_external_id",
  "name": "High Priority"
}
```

**Key Behavior**: If a tag with the given name already exists, the existing tag will be returned instead of creating a duplicate.

### 3. Update Tag
**Endpoint**: `PUT /tags/{external_id}`

**Purpose**: Update a tag's name (with merge capability)

**Request Body**:
```json
{
  "name": "Updated Tag Name"
}
```

**Important**: If a tag with the new name already exists, the target tag will be merged into the existing tag.

### 4. Delete Tag
**Endpoint**: `DELETE /tags/{external_id}`

**Purpose**: Permanently delete a tag and all its entity associations

**Response**:
```json
{
  "message": "Tag has been successfully deleted."
}
```

**Critical**: This operation removes the tag and ALL its links to entities (tenants, properties, beneficiaries).

### 5. Link Tags with Entity
**Endpoint**: `POST /tags/entities/{entity_type}/{entity_id}`

**Purpose**: Associate one or more tags with a specific entity

**Parameters**:
- `entity_type`: One of `tenant`, `property`, `beneficiary`
- `entity_id`: External ID of the entity

**Request Body**:
```json
{
  "tags": [
    "tag_name_or_external_id_1",
    "tag_name_or_external_id_2"
  ]
}
```

**Response**:
```json
{
  "items": [
    {
      "id": "tag_external_id_1",
      "name": "tag_name_1"
    },
    {
      "id": "tag_external_id_2", 
      "name": "tag_name_2"
    }
  ]
}
```

### 6. Get Tagged Entities
**Endpoint**: `GET /tags/{external_id}/entities`

**Purpose**: Retrieve all entities linked to a specific tag

**Parameters**:
- `entity_type` (optional): Filter by entity type
- `sort_by` (optional): Sort by `type` or `name`
- `sort_direction` (optional): `asc` or `desc`

**Response Structure**:
```json
{
  "entities": [
    {
      "id": "entity_external_id",
      "type": "tenant",
      "name": "Entity Display Name"
    }
  ]
}
```

### 7. Delete Tag-Entity Link
**Endpoint**: `DELETE /tags/{external_id}/entities`

**Purpose**: Remove association between a tag and specific entity

**Parameters**:
- `entity_type`: Required - `tenant`, `property`, or `beneficiary`
- `entity_id`: Required - External ID of the entity

**Response**:
```json
{
  "message": "Tag link successfully removed from entity."
}
```

## Tag Management Best Practices

### 1. Tag Naming Convention
- Use descriptive, consistent naming
- Consider hierarchical naming (e.g., "Priority_High", "Priority_Medium")
- Avoid special characters except underscore, hyphen, and spaces
- Keep names concise but meaningful

### 2. Tag Organization Strategies

**By Property Type**:
- `Commercial`
- `Residential`
- `Student_Housing`

**By Status**:
- `Active`
- `Maintenance_Required`
- `Under_Review`

**By Priority**:
- `High_Priority`
- `Standard`
- `Low_Priority`

**By Geographic Location**:
- `Downtown`
- `Suburbs`
- `City_Center`

### 3. Entity Tagging Workflows

**For Tenants**:
```bash
# Tag a tenant as high priority
POST /tags/entities/tenant/zJBv2E5aJQ
{
  "tags": ["High_Priority", "VIP_Client"]
}
```

**For Properties**:
```bash
# Tag a property with multiple characteristics
POST /tags/entities/property/D8eJPwZG7j
{
  "tags": ["Commercial", "Downtown", "Maintenance_Required"]
}
```

**For Beneficiaries**:
```bash
# Tag a beneficiary for payment categorization
POST /tags/entities/beneficiary/G1OByGaaXM
{
  "tags": ["International_Payment", "Monthly_Recipient"]
}
```

## Deletion and Data Management

### Tag Deletion Impact
When you delete a tag:
1. **Tag Record**: Permanently removed from the system
2. **Entity Links**: All associations with tenants, properties, and beneficiaries are removed
3. **No Recovery**: Deletion is permanent and cannot be undone
4. **No Cascade**: Entities themselves are not affected, only the tag associations

### Safe Deletion Workflow
1. **Check Associations**: Use `GET /tags/{external_id}/entities` to see what entities are linked
2. **Document Impact**: Record which entities will lose the tag
3. **Consider Alternatives**: Maybe rename or merge instead of delete
4. **Execute Deletion**: Use `DELETE /tags/{external_id}` when certain

### Bulk Operations Considerations
- No native bulk delete endpoint exists
- For multiple tag deletions, implement client-side iteration
- Consider rate limiting (API allows max 5 requests per second)

## Error Handling

### Common Error Responses

**400 Bad Request**:
```json
{
  "errors": [
    {
      "message": "Invalid tag name format"
    }
  ],
  "status": 400
}
```

**403 Forbidden**:
```json
{
  "errors": [
    {
      "message": "Insufficient privileges"
    }
  ],
  "status": 403
}
```

**404 Not Found**:
```json
{
  "errors": [
    {
      "message": "Tag not found"
    }
  ],
  "status": 404
}
```

## Integration Examples

### Complete Tag Management Flow

```javascript
// 1. Create a new tag
const createTag = async (tagName) => {
  const response = await fetch('/api/agency/v1.1/tags', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'APIkey YOUR_API_KEY'
    },
    body: JSON.stringify({ name: tagName })
  });
  return response.json();
};

// 2. Link tag to multiple entities
const linkTagToEntities = async (tagId, entityType, entityIds) => {
  const promises = entityIds.map(entityId => 
    fetch(`/api/agency/v1.1/tags/entities/${entityType}/${entityId}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'APIkey YOUR_API_KEY'
      },
      body: JSON.stringify({ tags: [tagId] })
    })
  );
  return Promise.all(promises);
};

// 3. Get all entities with specific tag
const getTaggedEntities = async (tagId, entityType = null) => {
  const params = entityType ? `?entity_type=${entityType}` : '';
  const response = await fetch(`/api/agency/v1.1/tags/${tagId}/entities${params}`, {
    headers: {
      'Authorization': 'APIkey YOUR_API_KEY'
    }
  });
  return response.json();
};

// 4. Safe tag deletion with confirmation
const safeDeleteTag = async (tagId) => {
  // First, check what entities are linked
  const linkedEntities = await getTaggedEntities(tagId);
  
  if (linkedEntities.entities.length > 0) {
    console.log(`Warning: Tag is linked to ${linkedEntities.entities.length} entities`);
    // Implement confirmation logic here
  }
  
  // Proceed with deletion
  const response = await fetch(`/api/agency/v1.1/tags/${tagId}`, {
    method: 'DELETE',
    headers: {
      'Authorization': 'APIkey YOUR_API_KEY'
    }
  });
  return response.json();
};
```

## Advanced Use Cases

### 1. Tag-Based Filtering for Reports
Use tags to filter entities in other API endpoints that support entity filtering.

### 2. Automated Tag Assignment
Implement business logic to automatically assign tags based on entity properties:
- High-value properties get "Premium" tag
- Overdue tenants get "Collection_Required" tag
- International beneficiaries get "International" tag

### 3. Tag Hierarchy Management
While the API doesn't enforce hierarchy, implement naming conventions:
- `Category_Subcategory` (e.g., `Property_Commercial`, `Property_Residential`)
- Use consistent separators for programmatic parsing

### 4. Bulk Tag Operations
```javascript
// Bulk tag assignment to multiple entities
const bulkTagAssignment = async (tagNames, entityType, entityIds) => {
  const operations = [];
  
  for (const entityId of entityIds) {
    operations.push(
      fetch(`/api/agency/v1.1/tags/entities/${entityType}/${entityId}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'APIkey YOUR_API_KEY'
        },
        body: JSON.stringify({ tags: tagNames })
      })
    );
  }
  
  return Promise.all(operations);
};
```

## Security and Permissions

### Authentication Requirements
- All tag operations require valid API authentication
- Use either API Key or OAuth 2.0 Bearer token
- API Key format: `Authorization: APIkey YOUR_API_KEY`
- Bearer token format: `Authorization: Bearer YOUR_ACCESS_TOKEN`

### Permission Levels
- Tag operations require appropriate permissions on the associated entities
- Ensure your API credentials have access to the entity types you're tagging
- Some operations may be restricted based on user roles (agent vs agency)

 (alphanumeric, underscore, hyphen, space only)
- **Case Sensitivity**: Names are case-sensitive
- **Uniqueness**: Names must be unique within your agency
- **Whitespace**: Leading/trailing whitespace handling not specified

**External ID Constraints**:
- **Length**: 10-32 characters (inclusive)  
- **Pattern**: `^[a-zA-Z0-9]+# PayProp API Tags - Comprehensive Guide

## Overview

Tags in the PayProp API provide a flexible way to categorize and organize entities (tenants, properties, and beneficiaries). They allow for better data management and filtering capabilities across your property management portfolio.

## Tag System Architecture

### Supported Entity Types
- **tenant** - Tag individual tenants
- **property** - Tag properties in your portfolio
- **beneficiary** - Tag beneficiaries for payment organization

### Tag Identification
- **External ID**: Alphanumeric identifier (10-32 characters, pattern: `^[a-zA-Z0-9]+$`)
- **Name**: Human-readable tag name (1-32 characters, pattern: `^[a-zA-Z0-9_\-\s]+$`)

## API Endpoints Reference

### 1. Get Tags
**Endpoint**: `GET /tags`

**Purpose**: Retrieve all tags with optional filtering

**Parameters**:
- `rows` (optional): Number of rows to return (1-25)
- `page` (optional): Page number (minimum: 1)
- `external_id` (optional): Filter by specific tag external ID
- `name` (optional): Filter tags by name
- `entity_type` (optional): Filter tags linked to specific entity type
- `entity_id` (optional): Filter tags linked to specific entity (use with entity_type)

**Example Request**:
```bash
GET /api/agency/v1.1/tags?entity_type=tenant&rows=10&page=1
```

**Response Structure**:
```json
{
  "items": [
    {
      "id": "tag_external_id",
      "name": "tag_name"
    }
  ],
  "pagination": {
    "page": 1,
    "rows": 10,
    "total_rows": 25,
    "total_pages": 3
  }
}
```

### 2. Create Tag
**Endpoint**: `POST /tags`

**Purpose**: Create a new tag (returns existing tag if name already exists)

**Request Body**:
```json
{
  "name": "High Priority"
}
```

**Response**:
```json
{
  "id": "generated_external_id",
  "name": "High Priority"
}
```

**Key Behavior**: If a tag with the given name already exists, the existing tag will be returned instead of creating a duplicate.

### 3. Update Tag
**Endpoint**: `PUT /tags/{external_id}`

**Purpose**: Update a tag's name (with merge capability)

**Request Body**:
```json
{
  "name": "Updated Tag Name"
}
```

**Important**: If a tag with the new name already exists, the target tag will be merged into the existing tag.

### 4. Delete Tag
**Endpoint**: `DELETE /tags/{external_id}`

**Purpose**: Permanently delete a tag and all its entity associations

**Response**:
```json
{
  "message": "Tag has been successfully deleted."
}
```

**Critical**: This operation removes the tag and ALL its links to entities (tenants, properties, beneficiaries).

### 5. Link Tags with Entity
**Endpoint**: `POST /tags/entities/{entity_type}/{entity_id}`

**Purpose**: Associate one or more tags with a specific entity

**Parameters**:
- `entity_type`: One of `tenant`, `property`, `beneficiary`
- `entity_id`: External ID of the entity

**Request Body**:
```json
{
  "tags": [
    "tag_name_or_external_id_1",
    "tag_name_or_external_id_2"
  ]
}
```

**Response**:
```json
{
  "items": [
    {
      "id": "tag_external_id_1",
      "name": "tag_name_1"
    },
    {
      "id": "tag_external_id_2", 
      "name": "tag_name_2"
    }
  ]
}
```

### 6. Get Tagged Entities
**Endpoint**: `GET /tags/{external_id}/entities`

**Purpose**: Retrieve all entities linked to a specific tag

**Parameters**:
- `entity_type` (optional): Filter by entity type
- `sort_by` (optional): Sort by `type` or `name`
- `sort_direction` (optional): `asc` or `desc`

**Response Structure**:
```json
{
  "entities": [
    {
      "id": "entity_external_id",
      "type": "tenant",
      "name": "Entity Display Name"
    }
  ]
}
```

### 7. Delete Tag-Entity Link
**Endpoint**: `DELETE /tags/{external_id}/entities`

**Purpose**: Remove association between a tag and specific entity

**Parameters**:
- `entity_type`: Required - `tenant`, `property`, or `beneficiary`
- `entity_id`: Required - External ID of the entity

**Response**:
```json
{
  "message": "Tag link successfully removed from entity."
}
```

## Tag Management Best Practices

### 1. Tag Naming Convention
- Use descriptive, consistent naming
- Consider hierarchical naming (e.g., "Priority_High", "Priority_Medium")
- Avoid special characters except underscore, hyphen, and spaces
- Keep names concise but meaningful

### 2. Tag Organization Strategies

**By Property Type**:
- `Commercial`
- `Residential`
- `Student_Housing`

**By Status**:
- `Active`
- `Maintenance_Required`
- `Under_Review`

**By Priority**:
- `High_Priority`
- `Standard`
- `Low_Priority`

**By Geographic Location**:
- `Downtown`
- `Suburbs`
- `City_Center`

### 3. Entity Tagging Workflows

**For Tenants**:
```bash
# Tag a tenant as high priority
POST /tags/entities/tenant/zJBv2E5aJQ
{
  "tags": ["High_Priority", "VIP_Client"]
}
```

**For Properties**:
```bash
# Tag a property with multiple characteristics
POST /tags/entities/property/D8eJPwZG7j
{
  "tags": ["Commercial", "Downtown", "Maintenance_Required"]
}
```

**For Beneficiaries**:
```bash
# Tag a beneficiary for payment categorization
POST /tags/entities/beneficiary/G1OByGaaXM
{
  "tags": ["International_Payment", "Monthly_Recipient"]
}
```

## Deletion and Data Management

### Tag Deletion Impact
When you delete a tag:
1. **Tag Record**: Permanently removed from the system
2. **Entity Links**: All associations with tenants, properties, and beneficiaries are removed
3. **No Recovery**: Deletion is permanent and cannot be undone
4. **No Cascade**: Entities themselves are not affected, only the tag associations

### Safe Deletion Workflow
1. **Check Associations**: Use `GET /tags/{external_id}/entities` to see what entities are linked
2. **Document Impact**: Record which entities will lose the tag
3. **Consider Alternatives**: Maybe rename or merge instead of delete
4. **Execute Deletion**: Use `DELETE /tags/{external_id}` when certain

### Bulk Operations Considerations
- No native bulk delete endpoint exists
- For multiple tag deletions, implement client-side iteration
- Consider rate limiting (API allows max 5 requests per second)

## Error Handling

### Common Error Responses

**400 Bad Request**:
```json
{
  "errors": [
    {
      "message": "Invalid tag name format"
    }
  ],
  "status": 400
}
```

**403 Forbidden**:
```json
{
  "errors": [
    {
      "message": "Insufficient privileges"
    }
  ],
  "status": 403
}
```

**404 Not Found**:
```json
{
  "errors": [
    {
      "message": "Tag not found"
    }
  ],
  "status": 404
}
```

## Integration Examples

### Complete Tag Management Flow

```javascript
// 1. Create a new tag
const createTag = async (tagName) => {
  const response = await fetch('/api/agency/v1.1/tags', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'APIkey YOUR_API_KEY'
    },
    body: JSON.stringify({ name: tagName })
  });
  return response.json();
};

// 2. Link tag to multiple entities
const linkTagToEntities = async (tagId, entityType, entityIds) => {
  const promises = entityIds.map(entityId => 
    fetch(`/api/agency/v1.1/tags/entities/${entityType}/${entityId}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'APIkey YOUR_API_KEY'
      },
      body: JSON.stringify({ tags: [tagId] })
    })
  );
  return Promise.all(promises);
};

// 3. Get all entities with specific tag
const getTaggedEntities = async (tagId, entityType = null) => {
  const params = entityType ? `?entity_type=${entityType}` : '';
  const response = await fetch(`/api/agency/v1.1/tags/${tagId}/entities${params}`, {
    headers: {
      'Authorization': 'APIkey YOUR_API_KEY'
    }
  });
  return response.json();
};

// 4. Safe tag deletion with confirmation
const safeDeleteTag = async (tagId) => {
  // First, check what entities are linked
  const linkedEntities = await getTaggedEntities(tagId);
  
  if (linkedEntities.entities.length > 0) {
    console.log(`Warning: Tag is linked to ${linkedEntities.entities.length} entities`);
    // Implement confirmation logic here
  }
  
  // Proceed with deletion
  const response = await fetch(`/api/agency/v1.1/tags/${tagId}`, {
    method: 'DELETE',
    headers: {
      'Authorization': 'APIkey YOUR_API_KEY'
    }
  });
  return response.json();
};
```

## Advanced Use Cases

### 1. Tag-Based Filtering for Reports
Use tags to filter entities in other API endpoints that support entity filtering.

### 2. Automated Tag Assignment
Implement business logic to automatically assign tags based on entity properties:
- High-value properties get "Premium" tag
- Overdue tenants get "Collection_Required" tag
- International beneficiaries get "International" tag

### 3. Tag Hierarchy Management
While the API doesn't enforce hierarchy, implement naming conventions:
- `Category_Subcategory` (e.g., `Property_Commercial`, `Property_Residential`)
- Use consistent separators for programmatic parsing

### 4. Bulk Tag Operations
```javascript
// Bulk tag assignment to multiple entities
const bulkTagAssignment = async (tagNames, entityType, entityIds) => {
  const operations = [];
  
  for (const entityId of entityIds) {
    operations.push(
      fetch(`/api/agency/v1.1/tags/entities/${entityType}/${entityId}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'APIkey YOUR_API_KEY'
        },
        body: JSON.stringify({ tags: tagNames })
      })
    );
  }
  
  return Promise.all(operations);
};
```

## Security and Permissions

### Authentication Requirements
- All tag operations require valid API authentication
- Use either API Key or OAuth 2.0 Bearer token
- API Key format: `Authorization: APIkey YOUR_API_KEY`
- Bearer token format: `Authorization: Bearer YOUR_ACCESS_TOKEN`

### Permission Levels
- Tag operations require appropriate permissions on the associated entities
- Ensure your API credentials have access to the entity types you're tagging
- Some operations may be restricted based on user roles (agent vs agency)

 (alphanumeric only, no special characters)
- **System Generated**: You cannot specify custom external IDs
- **Immutable**: External IDs cannot be changed after creation

## Rate Limiting and Performance

### API Rate Limits
- **Global Limit**: Maximum 5 requests per second across all endpoints
- **Penalty**: Exceeding rate limit results in 429 status code
- **Lockout**: Failed requests block ALL subsequent requests for 30 seconds
- **Scope**: Rate limiting applies per API key/token, not per endpoint

**Rate Limit Error Response**:
```json
{
  "errors": [
    {
      "message": "Too many requests"
    }
  ],
  "status": 429
}
```

### Performance Optimization Strategies
- **Batch Operations**: Group multiple tag operations where possible
- **Cache Management**: Cache tag lookups to reduce API calls
- **Pagination Planning**: Calculate total pages before iterating
- **Rate Limiting**: Implement client-side rate limiting with delays
- **Retry Logic**: Implement exponential backoff for 429 errors

**Recommended Rate Limiting Implementation**:
```javascript
class PayPropAPIClient {
  constructor() {
    this.requestQueue = [];
    this.lastRequestTime = 0;
    this.requestInterval = 250; // 4 requests per second (safely under 5)
  }

  async makeRequest(url, options) {
    const now = Date.now();
    const timeSinceLastRequest = now - this.lastRequestTime;
    
    if (timeSinceLastRequest < this.requestInterval) {
      await new Promise(resolve => 
        setTimeout(resolve, this.requestInterval - timeSinceLastRequest)
      );
    }
    
    this.lastRequestTime = Date.now();
    return fetch(url, options);
  }
}

## Potential Issues and Edge Cases

### 1. Pagination Issues

**Issue**: Requesting too many rows
```bash
GET /tags?rows=100  # Will fail - max is 25
```
**Error Response**:
```json
{
  "errors": [{"message": "Invalid rows parameter"}],
  "status": 400
}
```

**Issue**: Page out of bounds
```bash
GET /tags?page=10  # When only 3 pages exist
```
**Solution**: Always check `total_pages` in response before requesting specific pages

**Issue**: Large dataset iteration
```javascript
// WRONG - Will hit rate limits and may miss data
const getAllTags = async () => {
  let allTags = [];
  let page = 1;
  let hasMore = true;
  
  while (hasMore) {
    const response = await fetch(`/tags?page=${page}&rows=25`);
    const data = await response.json();
    allTags.push(...data.items);
    
    hasMore = page < data.pagination.total_pages;
    page++;
    // Missing rate limiting - will trigger 429 errors
  }
  return allTags;
};

// CORRECT - With proper rate limiting
const getAllTagsSafely = async () => {
  let allTags = [];
  let page = 1;
  let hasMore = true;
  
  while (hasMore) {
    const response = await fetch(`/tags?page=${page}&rows=25`);
    const data = await response.json();
    allTags.push(...data.items);
    
    hasMore = page < data.pagination.total_pages;
    page++;
    
    // Rate limiting delay
    if (hasMore) {
      await new Promise(resolve => setTimeout(resolve, 250));
    }
  }
  return allTags;
};
```

### 2. Tag Name and ID Validation Issues

**Issue**: Invalid characters in tag names
```javascript
// These will fail validation
const invalidNames = [
  "Tag@Name",     // @ symbol not allowed
  "Tag#Priority", // # symbol not allowed
  "Tag/Category", // / symbol not allowed
  "",             // Empty string not allowed
  "A".repeat(33)  // Too long (>32 chars)
];
```

**Issue**: Case sensitivity confusion
```javascript
// These are treated as DIFFERENT tags
await createTag("High Priority");
await createTag("high priority"); 
await createTag("HIGH PRIORITY");
// Results in 3 separate tags
```

**Issue**: Whitespace handling
```javascript
// Potential whitespace issues (behavior not documented)
await createTag(" Spaced Tag ");  // Leading/trailing spaces
await createTag("Double  Space"); // Multiple spaces
// Test these scenarios in your environment
```

### 3. Entity Linking Issues

**Issue**: Linking non-existent tags
```javascript
// If tag doesn't exist, behavior is undefined in spec
await fetch('/tags/entities/tenant/tenantId', {
  method: 'POST',
  body: JSON.stringify({ 
    tags: ["NonExistentTag"] // May fail or auto-create
  })
});
```

**Issue**: Linking to non-existent entities
```javascript
// Will return 404 if entity doesn't exist or you lack access
await fetch('/tags/entities/tenant/InvalidTenantId', {
  method: 'POST',
  body: JSON.stringify({ tags: ["ValidTag"] })
});
```

**Issue**: Mixed tag references
```javascript
// Mixing external IDs and names in same request
await fetch('/tags/entities/tenant/tenantId', {
  method: 'POST',
  body: JSON.stringify({ 
    tags: [
      "tagExternalId123",  // External ID
      "My Tag Name"        // Tag name
    ]
  })
});
// API should handle this, but test thoroughly
```

### 4. Deletion and Concurrency Issues

**Issue**: Deleting tags with active references
```javascript
// Tag deletion removes ALL entity links immediately
// No confirmation or warning in API response
await fetch('/tags/tagId', { method: 'DELETE' });
// All tenant/property/beneficiary links are gone forever
```

**Issue**: Concurrent modifications
```javascript
// User A deletes tag while User B is linking it
// No optimistic locking mentioned in API spec
// Could result in race conditions
```

**Issue**: Cascade deletion behavior
```javascript
// Deleting an entity doesn't automatically remove tag links
// May result in orphaned references (needs verification)
```

### 5. Authentication and Permission Edge Cases

**Issue**: Token expiration during batch operations
```javascript
// Long-running tag operations may encounter token expiry
const batchTagOperations = async (operations) => {
  for (let i = 0; i < operations.length; i++) {
    try {
      await operations[i]();
    } catch (error) {
      if (error.status === 401) {
        // Token expired mid-operation
        // Need to refresh and retry from current position
        await refreshToken();
        await operations[i](); // Retry current operation
      }
    }
    await new Promise(resolve => setTimeout(resolve, 250));
  }
};
```

**Issue**: Insufficient permissions on entities
```javascript
// You might have permission to create tags but not link to specific entities
// Error won't occur until linking attempt
```

### 6. Data Consistency Issues

**Issue**: Tag merge during update
```javascript
// If you rename a tag to an existing name, tags get merged
await updateTag("tagId1", { name: "ExistingTagName" });
// tagId1 entities now belong to existing tag
// tagId1 is deleted - this is permanent!
```

**Issue**: Large dataset synchronization
```javascript
// Getting consistent view of tags across multiple API calls
// Tags might be created/deleted between paginated requests
const page1 = await getTags({ page: 1 });
// Someone creates new tags here
const page2 = await getTags({ page: 2 }); 
// page2 might have shifted content
```

## Robust Error Handling Patterns

### Comprehensive Error Handler
```javascript
const handleTagAPIError = (error, operation, context = {}) => {
  const errorMap = {
    400: {
      message: "Bad Request - Check parameters",
      retry: false,
      actions: ["Validate input parameters", "Check API documentation"]
    },
    401: {
      message: "Unauthorized - Token issue", 
      retry: true,
      actions: ["Refresh authentication token", "Check API key validity"]
    },
    403: {
      message: "Forbidden - Insufficient permissions",
      retry: false, 
      actions: ["Check user permissions", "Verify entity access rights"]
    },
    404: {
      message: "Not Found - Tag or entity doesn't exist",
      retry: false,
      actions: ["Verify tag/entity ID", "Check if entity was deleted"]
    },
    429: {
      message: "Rate Limited - Too many requests",
      retry: true,
      actions: ["Implement rate limiting", "Add delays between requests"]
    },
    500: {
      message: "Server Error - PayProp internal issue",
      retry: true,
      actions: ["Retry after delay", "Contact PayProp support"]
    }
  };

  const errorInfo = errorMap[error.status] || {
    message: "Unknown error",
    retry: false,
    actions: ["Check error details", "Contact support"]
  };

  console.error(`Tag API Error in ${operation}:`, {
    status: error.status,
    message: errorInfo.message,
    context,
    suggestedActions: errorInfo.actions,
    canRetry: errorInfo.retry
  });

  return errorInfo;
};
```

### Retry Logic with Exponential Backoff
```javascript
const retryWithBackoff = async (operation, maxRetries = 3) => {
  for (let attempt = 1; attempt <= maxRetries; attempt++) {
    try {
      return await operation();
    } catch (error) {
      const errorInfo = handleTagAPIError(error, 'retry-operation');
      
      if (!errorInfo.retry || attempt === maxRetries) {
        throw error;
      }
      
      const delay = Math.pow(2, attempt) * 1000; // Exponential backoff
      console.log(`Retrying in ${delay}ms (attempt ${attempt}/${maxRetries})`);
      await new Promise(resolve => setTimeout(resolve, delay));
    }
  }
};
```

## Production-Ready Implementation Checklist

### Before Going Live
- [ ] **Rate Limiting**: Implement client-side rate limiting (4 req/sec max)
- [ ] **Error Handling**: Comprehensive error handling for all status codes
- [ ] **Retry Logic**: Exponential backoff for retryable errors (401, 429, 500)
- [ ] **Input Validation**: Client-side validation for tag names and entity IDs
- [ ] **Pagination**: Proper handling of paginated responses with bounds checking
- [ ] **Authentication**: Token refresh logic for long-running operations
- [ ] **Logging**: Detailed logging for debugging and monitoring
- [ ] **Testing**: Test edge cases like concurrent modifications and large datasets
- [ ] **Backup Strategy**: Plan for tag data backup/recovery
- [ ] **Performance Monitoring**: Monitor API response times and error rates

### Monitoring and Alerting
```javascript
const tagAPIMetrics = {
  requestCount: 0,
  errorCount: 0,
  rateLimitHits: 0,
  averageResponseTime: 0,
  
  logRequest(operation, responseTime, success) {
    this.requestCount++;
    if (!success) this.errorCount++;
    this.averageResponseTime = (this.averageResponseTime + responseTime) / 2;
    
    // Alert on high error rate
    const errorRate = this.errorCount / this.requestCount;
    if (errorRate > 0.1) { // 10% error rate threshold
      console.warn(`High tag API error rate: ${(errorRate * 100).toFixed(1)}%`);
    }
  },
  
  logRateLimit() {
    this.rateLimitHits++;
    console.warn(`Rate limit hit. Total hits: ${this.rateLimitHits}`);
  }
};
```

This comprehensive guide now includes all the critical constraints, edge cases, and potential issues you might encounter when working with tags in the PayProp API, including detailed pagination limitations, validation constraints, and production-ready error handling patterns.
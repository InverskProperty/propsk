# CRM Web Application

This CRM (Customer Relationship Management) application is built using Spring Boot MVC, Thymeleaf, Hibernate, MySQL, and Java 17. The application provides a comprehensive solution for managing customer interactions, tasks, appointments, and communication. It also integrates with various Google services, including Google Drive, Gmail, and Google Calendar, to enhance productivity and collaboration.

## **Prerequisites**

Before installing the CRM application, ensure the following:

- Java 17 is installed on your machine.
- MySQL database is set up and running.
- Obtain valid MySQL connection details (URL, username, password).
- Obtain Google API credentials for integration with Google services (Drive, Gmail, Calendar).

## Installation

To install and run the CRM application, follow these steps:

1. Clone the repository from GitHub.
2. Configure the MySQL database connection details in the `application.properties` file:

```
spring.datasource.url=jdbc:mysql://localhost:3306/crm?createDatabaseIfNotExist=true
spring.datasource.username=YourUserName
spring.datasource.password=YourPassword
spring.jpa.hibernate.ddl-auto=none
spring.sql.init.mode=always
```

Replace `YourUserName` and `YourPassword` with your MySQL database credentials.

1. **Set up the necessary Google API credentials for Google integration:**
    - Go to the [Google Cloud Console](https://console.cloud.google.com/).
    - Create a new project or select an existing project.
    - Enable the necessary APIs for your project (e.g., Google Drive, Gmail, Calendar).
    - In the project dashboard, navigate to the **Credentials** section.
    - Click on **Create Credentials** and select **OAuth client ID**.
    - Configure the OAuth consent screen with the required information.
    - Choose the application type as **Web application**.
    - Add the authorized redirect URIs in the **Authorized redirect URIs** section. For example:
        - `http://localhost:8080/login/oauth2/code/google`
        - `http://localhost:8080/employee/settings/handle-granted-access`
        Replace `localhost:8080` with the base URL of your CRM application.
    - Complete the setup and note down the **Client ID** and **Client Secret**.
2. **Modify the Google API scopes for accessing Google services**:
    
    While setting up the Google API credentials, you need to add the required scopes to define the level of access the application has to your Google account. The required scopes depend on the specific features you want to use. Here are the scopes for common Google services:
    
    - Google Drive: `https://www.googleapis.com/auth/drive`
    - Gmail: `https://www.googleapis.com/auth/gmail.readonly`
    - Google Calendar: `https://www.googleapis.com/auth/calendar`
        
        During the setup of your Google credentials, find the section to add the API scopes and include the scopes relevant to the features you intend to use.
        
        [![non-sensitive scopes](https://github.com/wp-ahmed/crm/assets/54330098/f1bc7026-591a-4d40-affa-e038e29591b2)](https://github.com/wp-ahmed/crm/assets/54330098/f1bc7026-591a-4d40-affa-e038e29591b2)

        ![sensitive scopes](https://github.com/wp-ahmed/crm/assets/54330098/14d82922-0904-45d0-9874-da18c90fb352)

        ![restricted scopes](https://github.com/wp-ahmed/crm/assets/54330098/b76a5cf8-c342-42e9-9848-6d0844f83575)

        
3. **Configure the redirect URI for the Google authentication flow:**

```
spring.security.oauth2.client.registration.google.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}

```

1. Customize the authorization and authentication URLs for the application if needed:

```
spring.security.oauth2.client.provider.google.authorization-uri=https://accounts.google.com/o/oauth2/auth
spring.security.oauth2.client.provider.google.token-uri=https://accounts.google.com/o/oauth2/token

```

1. Build the application using Maven:

```bash
mvn clean install

```

1. Run the application:

```bash
mvn spring-boot:run

```

1. Access the CRM application in your web browser at `http://localhost:8080`.

## Features

### User Authentication and Authorization

- Users can log in using their regular credentials or choose to log in using their Google accounts.
- Google login allows users to grant access to Google Drive, Gmail, and Google Calendar.

### Google Drive Integration

- Users can create, delete, and share files and folders with colleagues directly from the CRM application.
- Integration with Google Drive enables seamless collaboration and document management.

### Google Calendar Integration

- Integrated with FullCalendar JS library, users can easily manage their calendar, create, edit, and delete meetings.
- Automated email notifications are sent to attendees when meetings are scheduled or modified.

### Google Gmail Integration

- Users can send emails, save drafts, and manage their inbox, sent items, drafts, and trash directly within the CRM application.
- Gmail integration streamlines communication and enables efficient email management.

### User Roles and Permissions

- The application supports different roles, including Manager, Employee, Sales, and Customers.
- Each role has specific access and permissions tailored to their responsibilities.

### Manager Role

- Managers have access to all features and functionalities in the CRM application.
- They can create new users, assign specific roles to users, define new roles, and manage access to different pages for employees.
- Managers can assign tickets and leads to employees for efficient task allocation.

### Employee Role

- Employees have access to their assigned tickets, leads, contracts, and task history.
- They can manage their customers and create new tickets.
- Employees receive email notifications for newly assigned tasks (configurable in user settings).

### Customer Role

- Customers have access to their tickets, leads, and contracts.
- They receive email notifications for any changes to their tickets, leads, or contracts.
- Customers can manage their notification preferences in their settings.

### Leads Management

- Users can create, update, delete, and view leads.
- Integration with Google Drive allows automatic saving of lead attachments.
- Integration with Google Calendar enables scheduling meetings with customers.

### Tickets Management

- Users can create, update, delete, and view tickets.
- Integration with Google Drive allows automatic saving of ticket attachments.
- Integration with Google Calendar enables scheduling meetings related to tickets.

### Contracts Management

- Users can create, update, delete, and view contracts.
- Contracts can include details such as amount, start and end dates, description, and attachments.
- Integration with Google Drive allows uploading and sharing contracts with customers.

### Email Templates and Campaigns

- Users can create personalized email templates using the Unlayer library's drag-and-drop functionality.
- Email campaigns can be created using the predefined templates.

### User Settings

- Users can configure email settings and Google service access from their settings page.
- Email settings allow employees to enable or disable the automatic sending of emails to customers using predefined email templates when tickets, leads, or other objects are updated.
- Google settings allow users to manage access to Google services, enabling or disabling integration with Google Drive, Gmail, and Google Calendar.

### Screenshots

![login](https://github.com/wp-ahmed/crm/assets/54330098/2cb1fe3f-6e9f-4696-aa03-672893c17af3)
![Ticket details](https://github.com/wp-ahmed/crm/assets/54330098/a7aa060b-7724-4f7e-814d-6f0150d447aa)
![create user](https://github.com/wp-ahmed/crm/assets/54330098/e7b161bb-7555-4a83-9511-3a138ebb61f3)
![show users](https://github.com/wp-ahmed/crm/assets/54330098/3535c32b-560d-4896-a33f-25ad98853c01)
![profile details](https://github.com/wp-ahmed/crm/assets/54330098/bc0e33c7-20b7-4384-8532-5f569740869a)
![Google services](https://github.com/wp-ahmed/crm/assets/54330098/23dd8852-b3e7-40e8-b962-a0b72a14f08e)
![Create google drive folder](https://github.com/wp-ahmed/crm/assets/54330098/b169882a-c48a-49da-859f-fbcbb41430df)
![Create google drive file](https://github.com/wp-ahmed/crm/assets/54330098/94e6e672-3ecf-4ded-91c9-dcfa48a37cd4)
![Listing Drive folder and files](https://github.com/wp-ahmed/crm/assets/54330098/b9832bcf-7b9a-4e82-a137-7ac6ea851b47)
![Compose email](https://github.com/wp-ahmed/crm/assets/54330098/ef4d6d74-1c72-46ce-847a-a8c6df740561)
![Calendar events](https://github.com/wp-ahmed/crm/assets/54330098/7d6b6dde-ba45-4e62-ba9f-f887b287f49d)
![Adding new calendar event](https://github.com/wp-ahmed/crm/assets/54330098/cdaacedb-1bfb-4bf9-8348-afc6424e56c5)
![Adding calendar event](https://github.com/wp-ahmed/crm/assets/54330098/8d88b0cd-717a-4305-a3b9-80bc8747b146)
![inbox emails](https://github.com/wp-ahmed/crm/assets/54330098/c31563e8-956f-4cfb-84fd-b6e8b2003ac9)
![Email notification settings](https://github.com/wp-ahmed/crm/assets/54330098/d2793a76-3c35-4f1d-a4a0-3944b78c409a)
![customer details](https://github.com/wp-ahmed/crm/assets/54330098/964b4af6-1be4-4396-970f-3b4fa96b3843)
![create new ticket](https://github.com/wp-ahmed/crm/assets/54330098/72fa8161-abe2-4cff-b4d1-805d8092f1c4)
![show tickets](https://github.com/wp-ahmed/crm/assets/54330098/694eb71c-a20b-45aa-b2e5-04f08f459ad0)
![create email templates](https://github.com/wp-ahmed/crm/assets/54330098/90e9093e-81aa-41c3-a9a6-0956df3b3716)
![contract details](https://github.com/wp-ahmed/crm/assets/54330098/b5819c49-e8fa-4a81-9e42-df81fdba2cec)

---

# System Architecture & Data Model

## Core Entity Model

The CRM uses a sophisticated entity relationship model designed to support property management operations with multi-tenant properties and flexible customer relationships.

### Primary Entities

#### Customer Entity
- **Purpose**: Umbrella entity for all customer types
- **Customer Types**:
  - `TENANT` - Renters occupying properties
  - `PROPERTY_OWNER` - Property owners/landlords
  - `CONTRACTOR` - Service providers
  - `BENEFICIARY` - Payment recipients
- **Account Types**: `individual`, `business`
- **Key Fields**:
  - `customer_id` (Primary Key)
  - `customer_type`, `is_property_owner`, `is_tenant`
  - `payprop_customer_id` (PayProp integration)
  - `email`, `phone_number`, `mobile_number`
  - Banking details (account_name, account_number, sort_code)

#### Property Entity
- **Purpose**: Represents rental properties and units
- **Key Fields**:
  - `id` (Primary Key)
  - `property_name`, `property_type`
  - `monthly_payment` (expected rent)
  - `property_owner_id` (FK to Customer)
  - `payprop_id` (PayProp integration)
  - `block_id` (FK to Block)

#### Tenant Entity
- **Status**: UNUSED (0 records in database)
- **Note**: Legacy entity - all tenant functionality handled through Customer entity with assignment tables

#### Block Entity
- **Purpose**: Groups related properties (e.g., apartment buildings, housing developments, commercial complexes)
- **Block Types**: Residential, Commercial, Mixed-Use, Student Housing, Retirement, Social Housing, Industrial, Retail, Office
- **Key Features**:
  - Many-to-many relationships with portfolios via `block_portfolio_assignments`
  - Service charge distribution and expense tracking
  - Virtual block properties for block-level financial management
  - PayProp tag synchronization
  - Hierarchical property organization within portfolios
- **Financial Tracking**: Annual service charges, ground rent, insurance, reserve funds
- **Capacity Management**: Optional maximum property limits per block
- 📖 **See detailed documentation**: [Block-Based Portfolio Management System](#block-based-portfolio-management-system) section below

#### Portfolio Entity
- **Purpose**: Logical grouping of properties for management/reporting
- **Features**: Multiple assignment types, PayProp tag synchronization

## Assignment-Based Relationship System

The system uses **junction/assignment tables** to manage many-to-many relationships, enabling sophisticated relationship tracking and multi-tenant support.

### CustomerPropertyAssignment Table

**Purpose**: Links customers to properties with specific roles

```sql
Table: customer_property_assignments
- id (PK)
- customer_id (FK to customers.customer_id)
- property_id (FK to properties.id)
- assignment_type (ENUM: OWNER, TENANT, CONTRACTOR, MANAGER)
- ownership_percentage (for co-owners)
- is_primary (boolean)
- start_date, end_date (tenancy periods)
- payprop_invoice_id (PayProp sync)
```

**Current Usage** (as of data analysis):
- **70 total assignments**
- **34 OWNER assignments** (2 property owners managing 34 properties)
- **36 TENANT assignments** (36 tenants across 32 properties)

**Multi-Tenant Support Example**:
```
Property: Apartment 40 - 31 Watkin Road
├─ OWNER:  Customer 69 (ramakrishnasai.talluri@gmail.com)
├─ TENANT: Customer 27 (Jason Barclay)
├─ TENANT: Customer 19 (Neha Minocha)
└─ TENANT: Customer 5 (Michel & Sandra)

Total Monthly Rent: £2,786.40
├─ Jason Barclay:   £810.00
├─ Neha Minocha:    £702.00
└─ Michel & Sandra: £1,274.40
```

### PropertyPortfolioAssignment Table

**Purpose**: Links properties to portfolios with assignment types

```sql
Table: property_portfolio_assignments
- id (PK)
- property_id (FK)
- portfolio_id (FK)
- block_id (FK, optional)
- assignment_type (ENUM: PRIMARY, SECONDARY, TAG)
- sync_status (PayProp sync tracking)
```

**Features**:
- Properties can belong to multiple portfolios
- Hierarchical assignment (Portfolio + Block)
- PayProp tag synchronization

**Current Usage**: 41 assignments across 1 active portfolio

### BlockPortfolioAssignment Table

**Purpose**: Links blocks to portfolios

```sql
Table: block_portfolio_assignments
- id (PK)
- block_id (FK)
- portfolio_id (FK)
- assignment_type (ENUM: PRIMARY, SHARED)
```

**Features**:
- Blocks can be in multiple portfolios
- Supports shared block management

## Block-Based Portfolio Management System

The CRM includes a sophisticated block-based portfolio management system that enables hierarchical organization of properties within portfolios. This system supports multi-building property management, service charge distribution, and detailed financial tracking at the block level.

### Block Entity Structure

**Purpose**: Groups related properties (e.g., apartment buildings, housing developments, commercial complexes)

```sql
Table: blocks
Core Fields:
- id (PK)
- name (e.g., "Boden House NG10", "Riverside Apartments")
- description
- block_type (ENUM: RESIDENTIAL, COMMERCIAL, MIXED_USE, STUDENT_HOUSING, RETIREMENT, SOCIAL_HOUSING, INDUSTRIAL, RETAIL, OFFICE)
- portfolio_id (Legacy FK - deprecated, use block_portfolio_assignments)
- property_owner_id (FK to customers.customer_id)

Location:
- address_line1, address_line2
- city, county, postcode, country, country_code

Capacity & Organization:
- max_properties (capacity limit)
- display_order (for UI organization)
- is_active (Y/N)

Financial Fields:
- annual_service_charge (Total annual service charge)
- service_charge_frequency (MONTHLY, QUARTERLY, ANNUAL)
- ground_rent_annual
- insurance_annual
- reserve_fund_contribution
- allocation_method (EQUAL, BY_SQFT, BY_BEDROOMS, CUSTOM)
- service_charge_account (Dedicated bank account)

Block Property:
- block_property_id (FK to properties - block as financial entity)

PayProp Integration:
- payprop_tags (CSV of PayProp tag IDs)
- payprop_tag_names (Human-readable tag names)
- last_sync_at
- sync_status (PENDING, SYNCED, FAILED)
```

### Hierarchical Organization Model

The system supports a three-tier organizational hierarchy:

```
Portfolio (e.g., "Nottingham Properties")
├── Block 1: Boden House NG10
│   ├── Flat 1 - Boden House
│   ├── Flat 2 - Boden House
│   ├── Flat 3 - Boden House
│   └── [Block Property - Virtual Entity]
├── Block 2: Riverside Apartments
│   ├── Apartment 40 - Watkin Road
│   ├── Apartment 41 - Watkin Road
│   └── [Block Property - Virtual Entity]
└── Unassigned Properties
    ├── Standalone Property 1
    └── Standalone Property 2
```

**Key Features**:
- **Many-to-Many Relationships**: Blocks can belong to multiple portfolios
- **Flexible Assignment**: Properties can be assigned directly to portfolios OR via blocks
- **Virtual Block Properties**: Each block can have an associated virtual property (property_type='BLOCK') for block-level financial management
- **Capacity Management**: Blocks can have maximum property limits

### Block Types

The system supports various block types to accommodate different property management scenarios:

- **RESIDENTIAL**: Standard apartment buildings, housing developments
- **COMMERCIAL**: Office buildings, shopping centers
- **MIXED_USE**: Buildings with both residential and commercial units
- **STUDENT_HOUSING**: Purpose-built student accommodations (PBSA)
- **RETIREMENT**: Retirement communities, assisted living
- **SOCIAL_HOUSING**: Council housing, housing association properties
- **INDUSTRIAL**: Warehouses, industrial estates
- **RETAIL**: Shopping centers, retail parks
- **OFFICE**: Office complexes, business centers

### Block Assignment Tables

#### block_portfolio_assignments
**Purpose**: Manages many-to-many relationships between blocks and portfolios

```sql
Table: block_portfolio_assignments
- id (PK)
- block_id (FK to blocks)
- portfolio_id (FK to portfolios)
- assignment_type (ENUM: PRIMARY, SHARED)
- assigned_at, assigned_by
- is_active (boolean)
- display_order (for UI organization)
- notes
```

**Assignment Types**:
- `PRIMARY`: Block's primary portfolio affiliation
- `SHARED`: Block is shared across multiple portfolios

#### property_block_assignments
**Purpose**: Links properties to blocks independently of portfolio assignments

```sql
Table: property_block_assignments
- id (PK)
- property_id (FK to properties)
- block_id (FK to blocks)
- assigned_at, assigned_by
- is_active (boolean)
- display_order (for property ordering within blocks)
- notes
```

**Features**:
- Independent of portfolio assignments
- Properties can be reassigned between blocks without affecting portfolio membership
- Display order enables custom property sorting within blocks
- Supports block-level service charge allocation

#### Enhanced property_portfolio_assignments
The existing property-portfolio assignment table now includes optional block assignment:

```sql
Table: property_portfolio_assignments (Enhanced)
- id (PK)
- property_id (FK)
- portfolio_id (FK)
- block_id (FK, optional) ← NEW: Hierarchical assignment
- assignment_type (ENUM: PRIMARY, SECONDARY, TAG)
- sync_status (PayProp sync tracking)
```

**Hierarchical Assignment Pattern**:
```java
// Property assigned to portfolio via block
PropertyPortfolioAssignment assignment = new PropertyPortfolioAssignment();
assignment.setPropertyId(propertyId);
assignment.setPortfolioId(portfolioId);
assignment.setBlockId(blockId);  // Hierarchical link
assignment.setAssignmentType(AssignmentType.PRIMARY);
```

### Virtual Block Properties

Virtual block properties are special property entities (property_type='BLOCK') that represent the block itself for financial management purposes.

**Purpose**:
- Track block-level income (e.g., service charges collected)
- Record block-level expenses (maintenance, insurance)
- Manage block bank accounts
- Generate block-level financial statements

**Example**:
```
Block: Boden House NG10 (Block ID: 2)
└── Virtual Property: "Boden House NG10 - Block Property" (Property ID: 69, Type: BLOCK)
    - Used for service charge income tracking
    - Used for block expense allocations
    - Excluded from property counts in statistics
```

**Data Integrity**:
- Virtual block properties are **excluded** from property counts in portfolio statistics
- Filtering pattern: `WHERE property_type != 'BLOCK'`
- Applied consistently across UI, API endpoints, and analytics calculations

### Block Financial Management

#### Service Charge Distribution

**Table**: `block_service_charge_distributions`

Tracks how block-level service charges are allocated across individual properties.

```sql
Table: block_service_charge_distributions
- id (PK)
- block_id (FK)
- property_id (FK)
- annual_charge (Property's annual service charge)
- percentage (% of total block charges)
- allocation_method (EQUAL, BY_SQFT, BY_BEDROOMS, CUSTOM)
- effective_from, effective_to (time-based allocations)
- created_at, created_by, notes
```

**Allocation Methods**:
1. **EQUAL**: Service charges split equally across all properties
   ```
   Example: £12,000 annual charge ÷ 10 properties = £1,200 per property
   ```

2. **BY_SQFT**: Allocated proportionally by square footage
   ```
   Example:
   Property A: 500 sqft / 5,000 total = 10% = £1,200
   Property B: 750 sqft / 5,000 total = 15% = £1,800
   ```

3. **BY_BEDROOMS**: Distributed based on bedroom count
   ```
   Example:
   1-bed flat: 1/30 bedrooms = 3.33% = £400
   2-bed flat: 2/30 bedrooms = 6.67% = £800
   3-bed flat: 3/30 bedrooms = 10% = £1,200
   ```

4. **CUSTOM**: Manually set percentages or amounts

#### Block Expense Tracking

**Table**: `block_expenses`

Records all expenses at the block level for service charge recovery.

```sql
Table: block_expenses
- id (PK)
- block_id (FK)
- description (e.g., "Annual Building Insurance")
- amount
- expense_category (MAINTENANCE, CLEANING, INSURANCE, MANAGEMENT, UTILITIES, REPAIRS, SECURITY, LANDSCAPING, OTHER)
- expense_date
- payment_source_id (FK to payment_sources)
- is_recoverable (Can be recharged to tenants)
- invoice_reference
- paid, paid_date
- created_at, created_by, notes
```

**Use Cases**:
- Track building insurance costs
- Record cleaning/maintenance contracts
- Monitor utility bills for common areas
- Calculate service charge recovery amounts
- Generate block-level expense reports

**Example**:
```
Block: Riverside Apartments
├─ Expense 1: Building Insurance    → £5,000  (Annual, Recoverable)
├─ Expense 2: Cleaning Contract      → £3,600  (Annual, Recoverable)
├─ Expense 3: Lift Maintenance       → £1,200  (Annual, Recoverable)
├─ Expense 4: Landscaping            → £800    (Annual, Recoverable)
└─ Total Annual Costs: £10,600
   ÷ 15 properties = £706.67 per property annual service charge
```

#### Block-Level Transaction Tracking

The `historical_transactions` table includes block-level tracking:

```sql
ALTER TABLE historical_transactions
ADD COLUMN block_id BIGINT (FK to blocks);
```

**Use Cases**:
- Link service charge payments to specific blocks
- Track block-level income/expense aggregates
- Generate block financial statements
- Reconcile block bank accounts

### PayProp Integration for Blocks

Blocks support PayProp tag-based synchronization:

**Block Fields**:
- `payprop_tags`: CSV list of PayProp tag IDs (e.g., "12345,12346")
- `payprop_tag_names`: Human-readable names (e.g., "Owner-2-BodenHouse")
- `sync_status`: Track synchronization state (PENDING, SYNCED, FAILED)
- `last_sync_at`: Last successful sync timestamp

**Tag Naming Convention**:
```
Format: Owner-{portfolio_id}-{sanitized_block_name}
Example: "Owner-2-BodenHouseNG10"
```

**Synchronization Flow**:
1. Create block in CRM system
2. Assign block to portfolio
3. Generate PayProp-compatible tag name
4. Create tag in PayProp via API
5. Store tag ID in block record
6. Assign properties to block
7. Sync property tags with PayProp

**Repository Methods**:
```java
// Find blocks needing PayProp sync
List<Block> blocksNeedingSync = blockRepository.findBlocksNeedingSync();

// Find blocks with missing PayProp external IDs
List<Block> blocksWithMissingTags = blockRepository.findBlocksWithMissingPayPropTags();

// Validate PayProp tag names
Optional<String> tagName = blockRepository.generateBlockTagName(portfolioId, blockName);
```

### Block Operations & Queries

#### Key Repository Methods

```java
// Find active blocks for portfolio (via junction table)
@Query("SELECT b FROM Block b " +
       "JOIN BlockPortfolioAssignment bpa ON bpa.block.id = b.id " +
       "WHERE bpa.portfolio.id = :portfolioId AND bpa.isActive = true " +
       "ORDER BY b.displayOrder, b.name")
List<Block> findByPortfolioIdOrderByDisplayOrder(@Param("portfolioId") Long portfolioId);

// Get blocks with property counts
@Query("SELECT b, COUNT(DISTINCT ppa.property.id) as propertyCount FROM Block b " +
       "JOIN BlockPortfolioAssignment bpa ON bpa.block.id = b.id " +
       "LEFT JOIN PropertyPortfolioAssignment ppa ON ppa.block.id = b.id " +
       "WHERE bpa.portfolio.id = :portfolioId AND bpa.isActive = true " +
       "GROUP BY b.id ORDER BY b.displayOrder")
List<Object[]> findBlocksWithPropertyCountsByPortfolio(@Param("portfolioId") Long portfolioId);

// Count properties via assignment table (accurate for many-to-many)
@Query("SELECT COUNT(pba) FROM PropertyBlockAssignment pba " +
       "WHERE pba.block.id = :blockId AND pba.isActive = true")
long countPropertiesInBlockViaAssignment(@Param("blockId") Long blockId);

// Find empty blocks (no property assignments)
@Query("SELECT b FROM Block b " +
       "WHERE b.isActive = 'Y' AND b.id NOT IN (" +
       "    SELECT DISTINCT ppa.block.id FROM PropertyPortfolioAssignment ppa " +
       "    WHERE ppa.block.id IS NOT NULL AND ppa.isActive = true" +
       ") ORDER BY b.portfolio.displayOrder, b.displayOrder")
List<Block> findEmptyBlocks();

// Check block name uniqueness within portfolio
@Query("SELECT COUNT(b) > 0 FROM Block b " +
       "WHERE b.portfolio.id = :portfolioId " +
       "AND UPPER(b.name) = UPPER(:name) " +
       "AND b.isActive = 'Y' " +
       "AND (:excludeId IS NULL OR b.id <> :excludeId)")
boolean existsByPortfolioAndNameIgnoreCase(@Param("portfolioId") Long portfolioId,
                                          @Param("name") String name,
                                          @Param("excludeId") Long excludeId);

// Get next display order for new blocks
@Query("SELECT COALESCE(MAX(b.displayOrder), 0) + 1 FROM Block b " +
       "WHERE b.portfolio.id = :portfolioId")
Integer getNextDisplayOrderForPortfolio(@Param("portfolioId") Long portfolioId);
```

#### Property Count Filtering

**Critical Pattern**: Always exclude virtual block properties from property counts

```java
// Controller Layer - Portfolio Details View
List<Property> allProperties = portfolioService.getPropertiesForPortfolio(id);

// Filter out block properties (virtual entities)
List<Property> properties = allProperties.stream()
    .filter(p -> !"BLOCK".equals(p.getPropertyType()))
    .collect(Collectors.toList());

// Calculate analytics (only for actual leasable properties)
PortfolioAnalytics analytics = portfolioService.calculatePortfolioAnalytics(id, LocalDate.now());
```

**Applied Locations**:
1. `PortfolioController.java:1550-1560` - Portfolio details view
2. `PortfolioController.java:3271-3282` - Hierarchical properties API endpoint
3. `PortfolioServiceImpl.java:596-606` - Analytics calculation method

### UI Features (Employee/Manager Portal)

#### Portfolio Details Page with Block Organization

**Route**: `/portfolio/{id}`
**Template**: `portfolio/portfolio-details.html`

**Features**:
- **Hierarchical Block Display**: Properties organized under their assigned blocks
- **Statistics Dashboard**: Real-time property counts, occupancy rates, financial metrics
- **Block Creation**: Modal interface for creating new blocks within portfolio
- **Block Editing**: Update block details, capacity, financial settings
- **Property Assignment**: Drag-and-drop properties between blocks
- **Unassigned Properties Section**: Properties not yet assigned to blocks
- **PayProp Synchronization**: Sync block assignments with PayProp tags

**Key JavaScript Functions**:
```javascript
// Load blocks for portfolio
fetch(`/portfolio/internal/blocks/portfolio/${portfolioId}`)
    .then(response => response.json())
    .then(data => displayBlocks(data.blocks));

// Display blocks with property counts
function displayBlocks(blocks) {
    blocks.forEach(block => {
        // Render block card
        // Show property count
        // Enable drag-and-drop zones
    });
}

// Create new block
function createBlock(portfolioId, blockData) {
    fetch(`/portfolio/internal/blocks`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(blockData)
    });
}

// Assign property to block
function assignPropertyToBlock(propertyId, blockId, portfolioId) {
    fetch(`/portfolio/internal/properties/${propertyId}/assign-to-block`, {
        method: 'POST',
        body: JSON.stringify({ portfolioId, blockId })
    });
}
```

**Statistics Display** (Excludes Virtual Block Properties):
```html
<div class="stats-card">
    <h3>44 Total Properties</h3> <!-- BEFORE: Included virtual block property -->
    <h3>43 Total Properties</h3> <!-- AFTER: Excludes virtual block properties -->
</div>
```

#### Block Creation Interface

**Modal Form Fields**:
- Block Name (required)
- Description (optional)
- Block Type (dropdown: Residential, Commercial, etc.)
- Maximum Properties (capacity limit, optional)
- Address Details (Line 1, Line 2, City, County, Postcode)
- Financial Settings:
  - Annual Service Charge
  - Service Charge Frequency (Monthly, Quarterly, Annual)
  - Ground Rent Annual
  - Insurance Annual
  - Reserve Fund Contribution
  - Allocation Method (Equal, By Sq Ft, By Bedrooms, Custom)
  - Service Charge Bank Account

**Validation**:
- Block name uniqueness within portfolio
- Capacity validation (cannot exceed max if set)
- Required field validation

#### Property Assignment Interface

**Drag-and-Drop Functionality**:
```javascript
// Drag start
propertyElement.addEventListener('dragstart', (e) => {
    e.dataTransfer.setData('propertyId', property.id);
});

// Drop on block
blockElement.addEventListener('drop', (e) => {
    const propertyId = e.dataTransfer.getData('propertyId');
    const blockId = blockElement.dataset.blockId;
    assignPropertyToBlock(propertyId, blockId, portfolioId);
});
```

**Assignment Feedback**:
- Visual indicators during drag operations
- Success/failure notifications
- Real-time property count updates
- Automatic UI refresh after assignment

### Property Owner Portal - Limited Functionality

**Current Status**: Property owners have a simplified, **read-only** portfolio view

**Route**: `/property-owner/portfolio`
**Template**: `property-owner/portfolio.html`

**Available Features**:
- View list of portfolios (basic info only)
- See total property count per portfolio
- View flat list of properties
- See which portfolio each property belongs to

**Missing Features** (Employee/Manager Only):
- ❌ No block visibility
- ❌ No hierarchical organization view
- ❌ No property assignment functionality
- ❌ No block creation/editing
- ❌ No detailed portfolio statistics
- ❌ No drag-and-drop interface

**Future Enhancement**: Potential to add read-only block view for property owners to see how their properties are organized.

### Architecture Benefits

1. **Flexible Organization**: Properties can be managed individually or grouped into blocks
2. **Multi-Portfolio Support**: Blocks can be shared across portfolios (e.g., mixed-ownership buildings)
3. **Service Charge Management**: Block-level expense tracking and distribution
4. **PayProp Integration**: Seamless synchronization with PayProp tag system
5. **Financial Granularity**: Track income/expenses at property, block, or portfolio levels
6. **Scalability**: Handles large property portfolios with hundreds of units
7. **Data Integrity**: Junction tables prevent orphaned relationships
8. **Accurate Statistics**: Virtual block properties excluded from tenant-facing counts

### Migration Path

The system supports gradual migration from old direct FK relationships:

1. **Phase 1**: Legacy `blocks.portfolio_id` coexists with `block_portfolio_assignments`
2. **Phase 2**: All code uses junction table queries (CURRENT STATE)
3. **Phase 3**: Deprecate direct FK columns after validation period
4. **Phase 4**: Remove legacy FK columns in future migration

**Backward Compatibility**: Both old and new relationship patterns are validated during the transition period.

## Transaction Data Architecture

### HistoricalTransaction Entity

**Purpose**: Stores all financial transactions (both historical CSV imports and PayProp synced data)

```sql
Table: historical_transactions
Core Fields:
- id (PK)
- transaction_date, amount, description
- transaction_type (INCOME, EXPENSE, etc.)
- category, subcategory
- financial_year

Relationships:
- property_id (FK to properties)
- customer_id (FK to customers.customer_id) - NULL for property-level aggregates
- payment_source_id (FK to payment_sources)
- block_id (FK to blocks)

PayProp Integration:
- payprop_transaction_id (unique)
- payprop_tenant_id

Banking:
- bank_reference, payment_method
- account_number, sort_code

Business Fields:
- vat_amount, commission_amount
- commission_percentage
```

### Dual-Granularity Transaction Model

The system supports **both property-level and tenant-level** transaction tracking:

#### Property-Level Transactions (Historical CSV)
- **Pattern**: One transaction per property per month
- **customer_id**: NULL (aggregate transaction)
- **Example**: "Rental Income - July 2025: £2,786.40" for Apartment 40
- **Use Case**: Historical data imports, summary reporting

#### Tenant-Level Transactions (PayProp Integration)
- **Pattern**: Individual transactions per tenant
- **customer_id**: Populated (links to specific tenant via Customer entity)
- **Example**:
  ```
  Transaction 1: Jason Barclay - £810.00
  Transaction 2: Neha Minocha - £702.00
  Transaction 3: Michel & Sandra - £1,274.40
  ```
- **Use Case**: Detailed tracking, tenant statements, PayProp sync

#### Validation Pattern
```java
// Pseudo-code for validating tenant-property relationships
if (transaction.customerId != null && transaction.propertyId != null) {
    boolean isValid = customerPropertyAssignmentRepository
        .existsByCustomerAndPropertyAndType(
            transaction.customerId,
            transaction.propertyId,
            AssignmentType.TENANT
        );

    if (!isValid) {
        log.warn("Transaction references customer not assigned to property");
    }
}
```

### Payment Source Tracking

**Table**: `payment_sources`
- Tracks transaction origins: PayProp_CSV, Old Account System, Manual Entry, Bank Import
- Enables source-specific processing and reporting
- 100% of historical transactions have payment_source linkage

### Transaction Lifecycle

1. **Import**: CSV upload or PayProp API sync
2. **Validation**: Property/customer relationship checks via assignment tables
3. **Classification**: Category assignment, commission calculation
4. **Linkage**: Relationship establishment (property, customer, portfolio, block)
5. **Reporting**: Statement generation, financial summaries

## Key Architecture Strengths

1. **Assignment Table Flexibility**:
   - Supports multi-tenant properties
   - Handles co-ownership scenarios
   - Manages property-portfolio-block hierarchies
   - Tracks assignment history with start/end dates

2. **Dual-Granularity Compatibility**:
   - Historical property-level data coexists with tenant-level PayProp data
   - No data structure conflicts between sources
   - Flexible reporting at both levels

3. **PayProp Integration Ready**:
   - All entities have `payprop_*` fields for synchronization
   - Assignment tables include sync status tracking
   - Supports bidirectional sync (local ↔ PayProp)

4. **Customer-Centric Design**:
   - Single Customer entity handles all customer types
   - Reduces duplication and simplifies relationship management
   - Enables customers to have multiple roles (e.g., tenant and contractor)

---

# PayProp Integration Data Import Process

## Overview
The system integrates with PayProp API for comprehensive financial data synchronization.

## Transaction Granularity Compatibility

### PayProp vs Historical Data Integration

The system successfully integrates two different transaction granularity models:

#### PayProp Model: Tenant-Level Granularity
PayProp tracks rent payments at the **individual tenant level**, creating separate transactions for each tenant in a multi-tenant property.

**Example - Apartment 40 (July 2025)**:
```
PayProp Data (3 transactions):
├─ Transaction 1: Neha Minocha    → £702.00    (tenant_id: xxx)
├─ Transaction 2: Michel & Sandra → £1,274.40  (tenant_id: yyy)
└─ Transaction 3: Jason Barclay   → £810.00    (tenant_id: zzz)
Total: £2,786.40
```

Each transaction includes:
- `incoming_tenant_payprop_id` - Individual tenant identifier
- `incoming_tenant_name` - Tenant name
- `beneficiary_type` = 'beneficiary' for rent payments
- Separate `amount` per tenant

#### Historical CSV Model: Property-Level Aggregation
Historical CSV imports use **property-level aggregation**, combining all tenant payments into a single monthly transaction.

**Example - Apartment 40 (July 2025)**:
```
Historical CSV Data (1 transaction):
└─ Rental Income → £2,786.40 (property-level aggregate)
Total: £2,786.40
```

Transaction characteristics:
- `customer_id` = NULL (no individual tenant linkage)
- `property_id` = populated (property-level tracking)
- Single monthly aggregate per property

### Compatibility Architecture

The system handles both models through:

1. **Flexible customer_id Usage**:
   - `NULL` = Property-level aggregate (historical CSV pattern)
   - `NOT NULL` = Tenant-level transaction (PayProp pattern)

2. **Assignment Table Validation**:
   - Validates tenant-property relationships via `customer_property_assignments`
   - Supports multiple tenants per property
   - Tracks tenancy periods with `start_date` and `end_date`

3. **Payment Source Tracking**:
   - `payment_source` field distinguishes data origins
   - Enables source-specific processing logic
   - Current sources: PayProp_CSV, Old Account System, Manual Entry, Bank Import

4. **No Double-Counting**:
   - PayProp transactions: Use `payprop_transaction_id` for deduplication
   - Historical transactions: Date-based reconciliation
   - Monthly totals validated across both sources

### Real-World Example: Apartment 40

**Database State**:
```sql
-- customer_property_assignments table
Property: Apartment 40 - 31 Watkin Road (ID: 40)
├─ Assignment 1: OWNER  → Customer 69 (ramakrishnasai.talluri@gmail.com)
├─ Assignment 2: TENANT → Customer 27 (jaybarclay22@gmail.com)
├─ Assignment 3: TENANT → Customer 19 (nehaminocha18@gmail.com)
└─ Assignment 4: TENANT → Customer 5 (micheemabondo@gmail.com)

-- historical_transactions table (PayProp source)
Transaction 1: £810.00    | customer_id=27 | property_id=40 | Jason Barclay
Transaction 2: £702.00    | customer_id=19 | property_id=40 | Neha Minocha
Transaction 3: £1,274.40  | customer_id=5  | property_id=40 | Michel & Sandra

-- historical_transactions table (CSV source)
Transaction 4: £2,786.40  | customer_id=NULL | property_id=40 | Rental Income
```

**Validation Query**:
```sql
-- Verify all PayProp transactions have valid assignments
SELECT ht.*, cpa.assignment_type
FROM historical_transactions ht
LEFT JOIN customer_property_assignments cpa
  ON ht.customer_id = cpa.customer_id
  AND ht.property_id = cpa.property_id
  AND cpa.assignment_type = 'TENANT'
WHERE ht.customer_id IS NOT NULL
  AND ht.property_id = 40;

-- Result: All 3 tenant transactions have matching TENANT assignments ✓
```

### Benefits of Dual-Granularity Support

1. **Historical Data Preservation**: Legacy CSV imports maintain original property-level structure
2. **Detailed Tracking**: PayProp integration provides tenant-level granularity for current operations
3. **Flexible Reporting**: Generate reports at either property or tenant level
4. **Gradual Migration**: Can convert historical aggregates to tenant-level if needed
5. **Data Integrity**: Assignment table validates all tenant-property relationships

## Import Architecture

### Core Service: `PayPropFinancialSyncService`
- **Method**: `performComprehensiveFinancialSync()`
- **Pattern**: Paginated sync with transaction rollback capability
- **Dependencies**: PayPropOAuth2Service, PayPropApiClient, multiple repositories

### Data Sync Flow
1. **Properties** → Commission data mapping
2. **Beneficiaries** → Owner/contractor identification  
3. **Payment Categories** → Transaction classification
4. **Invoice Categories** → Rental income categorization
5. **Invoice Instructions** → Payment directives
6. **Financial Transactions** → ICDN actual transactions
7. **Batch Payments** → Payment batch reconciliation
8. **Commission Calculations** → Fee computations
9. **Commission Linking** → Instruction vs completion validation

### API Patterns
- **Pagination**: `apiClient.fetchAllPages(endpoint, processor)`
- **Error Handling**: Per-item try/catch with continue-on-error
- **Rate Limiting**: Built into PayPropApiClient
- **Chunked Processing**: Date-based chunks for large datasets

## Potential Import Friction Points

### 1. API Rate Limits
- **Issue**: PayProp API has undocumented rate limits
- **Mitigation**: Exponential backoff in PayPropApiClient
- **Risk**: Large property portfolios may hit limits during bulk sync

### 2. Data Consistency 
- **Issue**: PayProp data can be inconsistent between endpoints
- **Example**: Property ID in transactions may not match properties endpoint
- **Mitigation**: ID mapping validation (`validateInstructionCompletionIntegrity()`)

### 3. Transaction Classification
- **Issue**: PayProp transaction types don't map 1:1 to business categories
- **Solution**: `mapTransactionType()` method with beneficiary type analysis
- **Risk**: New PayProp categories may be uncategorized

### 4. Date Range Processing
- **Issue**: Large date ranges cause memory issues and timeouts
- **Solution**: Chunked date processing for batch payments
- **Risk**: Cross-chunk transaction relationships may be missed

### 5. Duplicate Detection
- **Issue**: API failures can cause duplicate imports
- **Solution**: PayProp transaction ID uniqueness constraints
- **Risk**: Partial failures leave inconsistent state

### 6. OAuth Token Management
- **Issue**: PayProp tokens expire, causing mid-sync failures
- **Solution**: `PayPropOAuth2Service` handles refresh
- **Risk**: Refresh failures require manual re-authentication

### 7. Property-Transaction Linking
- **Issue**: PayProp uses multiple property ID formats
- **Fields**: `payPropId`, `propertyId`, internal IDs
- **Risk**: Transactions may not link to correct properties

## Statement Generation Impact

### Required Data Dependencies
- **Unit Numbers**: Extracted from property names/addresses
- **Actual Rent Received**: From `invoice` transaction types
- **Payment Dates**: From `transactionDate` fields
- **Payment Batches**: From `paymentBatchId` linking
- **Expenses**: From `payment_to_contractor`, `payment_to_beneficiary` types
- **Commission Rates**: From property commission percentages

### Data Quality Requirements
- Property names must contain unit identifiers
- Transaction dates must be accurate for period filtering  
- PayProp IDs must be consistent across all endpoints
- Expense categorization must be complete for accurate statements

## Operational Recommendations

### Monitoring
- Track sync success rates per endpoint
- Monitor processing times for performance degradation
- Alert on data validation failures

### Error Recovery
- Implement partial sync restart capability
- Store sync checkpoints for large operations
- Provide manual reconciliation tools for edge cases

### Performance Optimization
- Consider caching frequently accessed data
- Implement incremental sync for daily operations
- Use database partitioning for large transaction tables

---

# Data Quality & Validation

## Current Data Status

### Historical Transactions Analysis (April 22, 2025 onwards)

**Total Transactions**: 621

#### Field Population Statistics

**100% Populated (Core Fields)**:
- `transaction_date`, `amount`, `description`
- `transaction_type`, `category`, `financial_year`
- `payment_source_id` (100% linkage to payment sources)

**98.5% Populated**:
- `property_id` (3 transactions with NULL - fees/adjustments)

**45% Populated**:
- `customer_id` (279 populated, 342 NULL)
  - NULL is **intentional** for property-level aggregates (fees, adjustments)
  - Expected pattern: Rent = tenant-level, Fees = property-level

**0% Populated (Extended Fields)**:
- `subcategory`, `bank_reference`, `payment_method`
- `vat_amount`, `commission_amount`
- `payprop_transaction_id`, `payprop_tenant_id` (historical imports only)

#### Assignment Table Linkage

**Relationship Validation** (transactions with customer_id):
- **279 transactions** have customer_id populated
- **279 transactions** have matching `customer_property_assignments` records (100% validation success)
- **0 orphaned references** - all customer-property links are valid

**Transaction Categories**:
```
Category         | Count | With Customer | With Assignment | Assignment %
-----------------|-------|---------------|-----------------|-------------
rent             | 285   | 34            | 32              | 94%
commission       | 146   | 0             | 0               | N/A (fees)
owner_liability  | 146   | 0             | 0               | N/A (accruals)
parking          | 12    | 2             | 2               | 100%
owner_payment    | 10    | 1             | 1               | 100%
```

### Data Quality Issues

#### Issue 1: Rental Income Without Tenant Linkage
**Status**: Expected behavior for historical CSV imports

- **251 rent transactions** (88%) have NULL customer_id
- **Root Cause**: Historical CSV uses property-level aggregation
- **Impact**: Cannot generate individual tenant statements for pre-PayProp periods
- **Resolution Options**:
  1. Keep as-is for historical accuracy
  2. Split aggregates using current tenant assignments (retroactive)
  3. Flag as "property-level" in reporting

#### Issue 2: Expense Customer Linkage
**Status**: Data quality gap requiring attention

- **13 expense transactions** from "Old Account System" have NULL customer_id
- **Categories**: furnishing, general, safety, white_goods
- **Expected**: Should link to property owners or contractors
- **Action Required**: Manual review and linkage

#### Issue 3: Missing Extended Fields
**Status**: Acceptable - fields not captured in CSV imports

- Banking details (bank_reference, payment_method, account_number)
- VAT tracking (vat_amount)
- Commission details (commission_amount, commission_percentage)
- **Note**: PayProp API sync will populate these fields for future transactions

### Data Validation Patterns

#### Pattern 1: Assignment Validation
```java
/**
 * Validates that a transaction's customer-property relationship
 * is backed by a valid assignment record
 */
public boolean validateTransactionAssignment(HistoricalTransaction transaction) {
    if (transaction.getCustomerId() == null || transaction.getPropertyId() == null) {
        return true; // Property-level aggregate - no validation needed
    }

    return customerPropertyAssignmentRepository.existsByCustomerAndProperty(
        transaction.getCustomerId(),
        transaction.getPropertyId(),
        AssignmentType.TENANT  // or OWNER for owner payments
    );
}
```

#### Pattern 2: Tenant Rent Reconciliation
```java
/**
 * Reconciles property-level rent aggregate with tenant-level payments
 */
public BigDecimal reconcilePropertyRent(Long propertyId, YearMonth month) {
    // Get property-level aggregate (historical CSV)
    BigDecimal propertyAggregate = transactionRepository
        .findByPropertyAndCategoryAndMonth(propertyId, "rent", month)
        .stream()
        .filter(t -> t.getCustomerId() == null)  // Property-level only
        .map(HistoricalTransaction::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    // Get tenant-level payments (PayProp)
    BigDecimal tenantTotal = transactionRepository
        .findByPropertyAndCategoryAndMonth(propertyId, "rent", month)
        .stream()
        .filter(t -> t.getCustomerId() != null)  // Tenant-level only
        .map(HistoricalTransaction::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    // Validate totals match (within tolerance)
    BigDecimal difference = propertyAggregate.subtract(tenantTotal).abs();
    if (difference.compareTo(new BigDecimal("0.01")) > 0) {
        log.warn("Reconciliation mismatch for property {}: Property={}, Tenants={}",
            propertyId, propertyAggregate, tenantTotal);
    }

    return difference;
}
```

#### Pattern 3: Multi-Tenant Property Validation
```sql
-- Find properties with multiple active tenant assignments but no tenant-level transactions
SELECT p.id, p.property_name, COUNT(DISTINCT cpa.customer_id) as tenant_count
FROM properties p
JOIN customer_property_assignments cpa ON p.id = cpa.property_id
LEFT JOIN historical_transactions ht ON p.id = ht.property_id
    AND ht.customer_id IS NOT NULL
    AND ht.category = 'rent'
WHERE cpa.assignment_type = 'TENANT'
    AND (cpa.end_date IS NULL OR cpa.end_date > CURRENT_DATE)
GROUP BY p.id, p.property_name
HAVING tenant_count > 1 AND COUNT(ht.id) = 0;
```

### Data Quality Metrics

**Current System Health** (as of October 2025):

| Metric | Status | Score |
|--------|--------|-------|
| Property Linkage | ✅ Excellent | 98.5% |
| Assignment Validation | ✅ Excellent | 100% |
| Payment Source Tracking | ✅ Excellent | 100% |
| Tenant-Property Assignments | ✅ Good | 70 active assignments |
| Rental Income Customer Linkage | ⚠️ Historical Gap | 12% (by design for CSV imports) |
| Expense Customer Linkage | ⚠️ Needs Attention | 13 unlinked expenses |
| Extended Field Population | ℹ️ Expected | 0% (CSV limitation) |

### Recommended Actions

1. **High Priority**:
   - Review and link 13 expense transactions to appropriate customers
   - Document property-level vs tenant-level transaction policies

2. **Medium Priority**:
   - Implement assignment validation warnings in transaction import UI
   - Add reconciliation reports for multi-tenant properties
   - Create data quality dashboard showing linkage statistics

3. **Low Priority**:
   - Consider retroactive splitting of historical rent aggregates
   - Enhance CSV import to capture banking details if available
   - Archive or remove unused Tenant entity (0 records)

4. **Monitoring**:
   - Weekly data quality report on orphaned transactions
   - Monthly reconciliation of property vs tenant totals
   - Quarterly assignment table cleanup (expired tenancies)

## Contributing

Contributions to the CRM Web Application are welcome! If you spot any bugs or would like to propose new features, please open an issue or submit a pull request.

## License

This project is licensed under the MIT License.

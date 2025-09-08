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



# PayProp Integration Data Import Process

## Overview
The system integrates with PayProp API for comprehensive financial data synchronization.

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

## Contributing

Contributions to the CRM Web Application are welcome! If you spot any bugs or would like to propose new features, please open an issue or submit a pull request.

## License

This project is licensed under the MIT License.

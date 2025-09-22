package site.easy.to.build.crm.service.sheets;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.FinancialTransaction;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.repository.FinancialTransactionRepository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GoogleSheetsServiceAccountService {

    private final CustomerService customerService;
    private final PropertyService propertyService;
    private final FinancialTransactionRepository financialTransactionRepository;

    @Value("${GOOGLE_SERVICE_ACCOUNT_KEY}")
    private String serviceAccountKey;

    // Shared Drive ID for CRM statements - service account has Manager access
    private static final String SHARED_DRIVE_ID = "0ADaFlidiFrFDUk9PVA";

    @Autowired
    public GoogleSheetsServiceAccountService(CustomerService customerService,
                                           PropertyService propertyService,
                                           FinancialTransactionRepository financialTransactionRepository) {
        this.customerService = customerService;
        this.propertyService = propertyService;
        this.financialTransactionRepository = financialTransactionRepository;
    }

    /**
     * Get properly formatted service account key (handle escaped newlines)
     */
    private String getFormattedServiceAccountKey() {
        if (serviceAccountKey == null) {
            return null;
        }

        // If the key contains escaped newlines, replace them with actual newlines
        if (serviceAccountKey.contains("\\n")) {
            System.out.println("🔧 ServiceAccount: Converting escaped newlines in service account key");
            return serviceAccountKey.replace("\\n", "\n");
        }

        return serviceAccountKey;
    }

    /**
     * Create a Sheets service using service account credentials
     */
    private Sheets createSheetsService() throws IOException, GeneralSecurityException {
        System.out.println("🔧 ServiceAccount: Creating Google Sheets service...");

        try {
            String formattedKey = getFormattedServiceAccountKey();
            if (formattedKey == null || formattedKey.trim().isEmpty()) {
                throw new IOException("Service account key is not configured (GOOGLE_SERVICE_ACCOUNT_KEY environment variable)");
            }

            System.out.println("🔧 ServiceAccount: Key length: " + formattedKey.length() + " characters");

            // Parse and log key details for debugging
            try {
                if (formattedKey.contains("client_email")) {
                    String email = extractServiceAccountEmail(formattedKey);
                    System.out.println("🔧 ServiceAccount: Extracted email from key: " + email);
                }
                if (formattedKey.contains("project_id")) {
                    String projectId = extractProjectId(formattedKey);
                    System.out.println("🔧 ServiceAccount: Project ID: " + projectId);
                }
            } catch (Exception e) {
                System.out.println("⚠️ ServiceAccount: Could not extract key details: " + e.getMessage());
            }

            GoogleCredential credential = GoogleCredential
                .fromStream(new ByteArrayInputStream(formattedKey.getBytes()))
                .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS));

            System.out.println("🔧 ServiceAccount: Credential created successfully");
            System.out.println("🔧 ServiceAccount: Service account email: " + credential.getServiceAccountId());
            System.out.println("🔧 ServiceAccount: Scopes: " + credential.getServiceAccountScopesAsString());

            // Test token creation before building service
            try {
                boolean refreshed = credential.refreshToken();
                System.out.println("🔧 ServiceAccount: Token refresh test: " + refreshed);
                System.out.println("🔧 ServiceAccount: Access token available: " + (credential.getAccessToken() != null));
                if (credential.getAccessToken() != null) {
                    System.out.println("🔧 ServiceAccount: Access token length: " + credential.getAccessToken().length());
                }
            } catch (Exception e) {
                System.err.println("❌ ServiceAccount: Token refresh failed: " + e.getMessage());
            }

            Sheets service = new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                credential)
                .setApplicationName("CRM Property Management")
                .build();

            System.out.println("✅ ServiceAccount: Google Sheets service created successfully");
            return service;

        } catch (Exception e) {
            System.err.println("❌ ServiceAccount: Failed to create Sheets service: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Creates a simple test spreadsheet to verify service account access
     */
    public String createTestSpreadsheet() throws IOException, GeneralSecurityException {
        System.out.println("🧪 ServiceAccount: Creating test spreadsheet...");

        // WORKAROUND: Based on API metrics, CreateSpreadsheet has 71% error rate
        // but other Sheets operations work. Use Drive API to create, then Sheets API to populate.

        String title = "Google Sheets API Test - " + new java.util.Date();
        System.out.println("🧪 ServiceAccount: Creating test spreadsheet with title: " + title);

        try {
            System.out.println("🧪 ServiceAccount: Using Drive API workaround for spreadsheet creation...");

            // Step 1: Create spreadsheet using Drive API (which works)
            // Note: We need to create a Drive service like we do for Sheets
            GoogleCredential credential = GoogleCredential
                .fromStream(new ByteArrayInputStream(serviceAccountKey.getBytes()))
                .createScoped(Collections.singleton(DriveScopes.DRIVE));

            Drive driveService = new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                credential)
                .setApplicationName("CRM Property Management")
                .build();

            com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
            fileMetadata.setName(title);
            fileMetadata.setMimeType("application/vnd.google-apps.spreadsheet");

            com.google.api.services.drive.model.File file = driveService.files().create(fileMetadata).execute();
            String spreadsheetId = file.getId();

            System.out.println("✅ ServiceAccount: Spreadsheet created via Drive API: " + spreadsheetId);

            // Step 2: Add test data using Sheets API (which works according to metrics)
            System.out.println("🧪 ServiceAccount: Adding test data using Sheets API...");

            Sheets sheetsService = createSheetsService();

            List<List<Object>> testData = List.of(
                List.of("Google Sheets API Test", "Status", "Working"),
                List.of("Service Account", "property-statement-generator@crecrm.iam.gserviceaccount.com"),
                List.of("Created", new java.util.Date().toString()),
                List.of("Method", "Drive API + Sheets API workaround")
            );

            ValueRange body = new ValueRange().setValues(testData);

            sheetsService.spreadsheets().values()
                .update(spreadsheetId, "A1", body)
                .setValueInputOption("RAW")
                .execute();

            System.out.println("✅ ServiceAccount: Test data added successfully - workaround complete!");
            return spreadsheetId;

        } catch (Exception e) {
            System.err.println("❌ ServiceAccount: Drive API workaround failed: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Test basic Google API access with service account (simpler than Sheets)
     */
    public String testBasicGoogleApiAccess() throws IOException, GeneralSecurityException {
        System.out.println("🧪 ServiceAccount: Testing basic Google API access...");

        try {
            // Test 1: Try to get information about the service account itself
            System.out.println("🧪 ServiceAccount: Testing service account token validation...");

            // Create a minimal credential test
            com.google.api.client.googleapis.auth.oauth2.GoogleCredential credential =
                com.google.api.client.googleapis.auth.oauth2.GoogleCredential
                    .fromStream(new java.io.ByteArrayInputStream(serviceAccountKey.getBytes()))
                    .createScoped(java.util.Collections.singleton(SheetsScopes.SPREADSHEETS));

            System.out.println("🧪 ServiceAccount: Credential created with Sheets write scope");
            System.out.println("🧪 ServiceAccount: Service account ID: " + credential.getServiceAccountId());

            // Try to refresh the token
            boolean refreshed = credential.refreshToken();
            System.out.println("🧪 ServiceAccount: Token refresh successful: " + refreshed);
            System.out.println("🧪 ServiceAccount: Access token exists: " + (credential.getAccessToken() != null));

            return "Basic API access test successful - service account can authenticate";

        } catch (Exception e) {
            System.err.println("❌ ServiceAccount: Basic API access test failed: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Creates a property owner statement in Google Sheets
     */
    public String createPropertyOwnerStatement(Customer propertyOwner, LocalDate fromDate, LocalDate toDate)
            throws IOException, GeneralSecurityException {

        System.out.println("📊 ServiceAccount: Creating property owner statement for: " + getCustomerName(propertyOwner));

        // WORKAROUND: Use Drive API to create spreadsheet (works), then Sheets API to populate (0% error rate)
        String title = String.format("Property Owner Statement - %s - %s to %s",
            getCustomerName(propertyOwner),
            fromDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
            toDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        System.out.println("📊 ServiceAccount: Creating spreadsheet with title: " + title);

        String spreadsheetId;

        System.out.println("📊 ServiceAccount: Using SHARED DRIVE approach (Google recommended)!");

        try {
            // Create spreadsheet in shared drive - service account has Manager access
            String formattedKey = getFormattedServiceAccountKey();
            GoogleCredential driveCredential = GoogleCredential
                .fromStream(new ByteArrayInputStream(formattedKey.getBytes()))
                .createScoped(Collections.singleton(DriveScopes.DRIVE));

            Drive driveService = new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                driveCredential)
                .setApplicationName("CRM Property Management")
                .build();

            System.out.println("📊 ServiceAccount: Creating spreadsheet in shared drive: " + SHARED_DRIVE_ID);

            // Create spreadsheet file in shared drive
            com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
            fileMetadata.setName(title);
            fileMetadata.setMimeType("application/vnd.google-apps.spreadsheet");
            fileMetadata.setParents(Collections.singletonList(SHARED_DRIVE_ID));

            com.google.api.services.drive.model.File file = driveService.files()
                .create(fileMetadata)
                .setSupportsAllDrives(true)  // Required for shared drives
                .execute();

            spreadsheetId = file.getId();
            System.out.println("✅ ServiceAccount: Spreadsheet created in shared drive! ID: " + spreadsheetId);

            // Give the property owner access to their statement (optional)
            String customerEmail = propertyOwner.getEmail();
            if (customerEmail != null && !customerEmail.isEmpty()) {
                try {
                    com.google.api.services.drive.model.Permission customerPermission = new com.google.api.services.drive.model.Permission();
                    customerPermission.setRole("reader"); // Can view but not edit
                    customerPermission.setType("user");
                    customerPermission.setEmailAddress(customerEmail);

                    driveService.permissions().create(spreadsheetId, customerPermission)
                        .setSupportsAllDrives(true)  // Required for shared drives
                        .execute();

                    System.out.println("✅ ServiceAccount: Property owner (" + customerEmail + ") granted access to their statement");
                } catch (Exception customerAccessError) {
                    System.err.println("⚠️ ServiceAccount: Could not grant access to customer: " + customerAccessError.getMessage());
                }
            } else {
                System.err.println("⚠️ ServiceAccount: Customer email not found - cannot grant access");
            }

        } catch (IOException e) {
            throw e; // Re-throw IOException as-is
        } catch (Exception e) {
            System.err.println("❌ ServiceAccount: Unexpected error in statement creation: " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Failed to create Google Sheets statement: " + e.getMessage(), e);
        }

        // Step 2: Create Sheets service for populating data (UpdateValues has 0% error rate)
        Sheets service = createSheetsService();

        // Get properties for this owner
        List<Property> properties = propertyService.getPropertiesByOwner(propertyOwner.getCustomerId());

        // Prepare data
        List<List<Object>> values = new ArrayList<>();

        // Header
        values.add(Arrays.asList(
            "PROPERTY OWNER STATEMENT",
            "", "", "", "", "", ""
        ));
        values.add(Arrays.asList(""));

        // Owner info
        values.add(Arrays.asList("Owner:", getCustomerName(propertyOwner)));
        values.add(Arrays.asList("Period:", fromDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) +
                                          " to " + toDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
        values.add(Arrays.asList("Generated:", LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
        values.add(Arrays.asList(""));

        // Summary section
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;

        for (Property property : properties) {
            List<FinancialTransaction> transactions = financialTransactionRepository
                .findByPropertyIdAndTransactionDateBetween(property.getPayPropId(), fromDate, toDate);

            BigDecimal propertyIncome = transactions.stream()
                .filter(t -> t.getAmount() != null && t.getAmount().compareTo(BigDecimal.ZERO) > 0)
                .map(FinancialTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal propertyExpenses = transactions.stream()
                .filter(t -> t.getAmount() != null && t.getAmount().compareTo(BigDecimal.ZERO) < 0)
                .map(t -> t.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalIncome = totalIncome.add(propertyIncome);
            totalExpenses = totalExpenses.add(propertyExpenses);
        }

        BigDecimal netAmount = totalIncome.subtract(totalExpenses);

        values.add(Arrays.asList("SUMMARY"));
        values.add(Arrays.asList("Total Properties:", properties.size()));
        values.add(Arrays.asList("Total Income:", "£" + totalIncome.setScale(2, BigDecimal.ROUND_HALF_UP)));
        values.add(Arrays.asList("Total Expenses:", "£" + totalExpenses.setScale(2, BigDecimal.ROUND_HALF_UP)));
        values.add(Arrays.asList("Net Amount:", "£" + netAmount.setScale(2, BigDecimal.ROUND_HALF_UP)));
        values.add(Arrays.asList(""));

        // Property details
        values.add(Arrays.asList("PROPERTY DETAILS"));
        values.add(Arrays.asList("Property", "Address", "Income", "Expenses", "Net"));

        for (Property property : properties) {
            List<FinancialTransaction> transactions = financialTransactionRepository
                .findByPropertyIdAndTransactionDateBetween(property.getPayPropId(), fromDate, toDate);

            BigDecimal propertyIncome = transactions.stream()
                .filter(t -> t.getAmount() != null && t.getAmount().compareTo(BigDecimal.ZERO) > 0)
                .map(FinancialTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal propertyExpenses = transactions.stream()
                .filter(t -> t.getAmount() != null && t.getAmount().compareTo(BigDecimal.ZERO) < 0)
                .map(t -> t.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal propertyNet = propertyIncome.subtract(propertyExpenses);

            values.add(Arrays.asList(
                property.getPropertyName() != null ? property.getPropertyName() : "Property " + property.getPayPropId(),
                property.getFullAddress() != null ? property.getFullAddress() : "No address",
                "£" + propertyIncome.setScale(2, BigDecimal.ROUND_HALF_UP),
                "£" + propertyExpenses.setScale(2, BigDecimal.ROUND_HALF_UP),
                "£" + propertyNet.setScale(2, BigDecimal.ROUND_HALF_UP)
            ));
        }

        // Update the sheet with data
        ValueRange body = new ValueRange().setValues(values);
        UpdateValuesResponse result = service.spreadsheets().values()
            .update(spreadsheetId, "A1", body)
            .setValueInputOption("RAW")
            .execute();

        // Make the spreadsheet public (viewable by anyone with link)
        BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
            .setRequests(Arrays.asList(
                new Request().setAddSheet(new AddSheetRequest()
                    .setProperties(new SheetProperties().setTitle("Statement")))
            ));

        System.out.println("✅ Created Google Sheets statement: " + spreadsheetId);
        return spreadsheetId;
    }

    /**
     * Creates a tenant statement in Google Sheets
     */
    public String createTenantStatement(Customer tenant, LocalDate fromDate, LocalDate toDate)
            throws IOException, GeneralSecurityException {

        // WORKAROUND: Use Drive API to create spreadsheet (works), then Sheets API to populate (0% error rate)
        String title = String.format("Tenant Statement - %s - %s to %s",
            getCustomerName(tenant),
            fromDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
            toDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        System.out.println("📊 ServiceAccount: Creating tenant statement with title: " + title);

        String spreadsheetId;
        try {
            System.out.println("📊 ServiceAccount: Using Drive API workaround for tenant statement creation...");

            // Step 1: Create spreadsheet using Drive API (which works according to your metrics)
            GoogleCredential credential = GoogleCredential
                .fromStream(new ByteArrayInputStream(serviceAccountKey.getBytes()))
                .createScoped(Collections.singleton(DriveScopes.DRIVE));

            Drive driveService = new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                credential)
                .setApplicationName("CRM Property Management")
                .build();

            com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
            fileMetadata.setName(title);
            fileMetadata.setMimeType("application/vnd.google-apps.spreadsheet");

            com.google.api.services.drive.model.File file = driveService.files().create(fileMetadata).execute();
            spreadsheetId = file.getId();

            System.out.println("✅ ServiceAccount: Tenant statement created via Drive API: " + spreadsheetId);

        } catch (Exception e) {
            System.err.println("❌ ServiceAccount: Drive API workaround failed for tenant statement: " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Failed to create tenant statement via Drive API: " + e.getMessage(), e);
        }

        // Step 2: Create Sheets service for populating data (UpdateValues has 0% error rate)
        Sheets service = createSheetsService();

        // Get tenant's property
        Property property = propertyService.getPropertyByTenant(tenant.getCustomerId());

        // Prepare data
        List<List<Object>> values = new ArrayList<>();

        // Header
        values.add(Arrays.asList("TENANT STATEMENT"));
        values.add(Arrays.asList(""));

        // Tenant info
        values.add(Arrays.asList("Tenant:", getCustomerName(tenant)));
        if (property != null) {
            values.add(Arrays.asList("Property:", property.getPropertyName() != null ? property.getPropertyName() : "Property " + property.getPayPropId()));
            values.add(Arrays.asList("Address:", property.getFullAddress() != null ? property.getFullAddress() : "No address"));
        }
        values.add(Arrays.asList("Period:", fromDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) +
                                          " to " + toDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
        values.add(Arrays.asList("Generated:", LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
        values.add(Arrays.asList(""));

        // Transaction details
        if (property != null) {
            List<FinancialTransaction> transactions = financialTransactionRepository
                .findByPropertyIdAndTransactionDateBetween(property.getPayPropId(), fromDate, toDate);

            values.add(Arrays.asList("TRANSACTIONS"));
            values.add(Arrays.asList("Date", "Description", "Amount", "Type"));

            for (FinancialTransaction transaction : transactions) {
                values.add(Arrays.asList(
                    transaction.getTransactionDate() != null ?
                        transaction.getTransactionDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "",
                    transaction.getDescription() != null ? transaction.getDescription() : "No description",
                    transaction.getAmount() != null ?
                        "£" + transaction.getAmount().setScale(2, BigDecimal.ROUND_HALF_UP) : "£0.00",
                    transaction.getTransactionType() != null ? transaction.getTransactionType() : "Unknown"
                ));
            }
        } else {
            values.add(Arrays.asList("No property found for this tenant."));
        }

        // Update the sheet with data
        ValueRange body = new ValueRange().setValues(values);
        UpdateValuesResponse result = service.spreadsheets().values()
            .update(spreadsheetId, "A1", body)
            .setValueInputOption("RAW")
            .execute();

        System.out.println("✅ Created Google Sheets tenant statement: " + spreadsheetId);
        return spreadsheetId;
    }

    /**
     * Helper method to get customer name safely
     */
    private String getCustomerName(Customer customer) {
        return customer.getName() != null ? customer.getName() : "Customer " + customer.getCustomerId();
    }

    /**
     * Helper method to extract service account email from JSON key
     */
    private String extractServiceAccountEmail(String key) {
        try {
            if (key != null && key.contains("client_email")) {
                String[] parts = key.split("\"client_email\":");
                if (parts.length > 1) {
                    String emailPart = parts[1].trim();
                    if (emailPart.startsWith("\"")) {
                        int endIndex = emailPart.indexOf("\"", 1);
                        if (endIndex > 0) {
                            return emailPart.substring(1, endIndex);
                        }
                    }
                }
            }
            return "Unable to extract email from service account key";
        } catch (Exception e) {
            return "Error extracting email: " + e.getMessage();
        }
    }

    /**
     * Helper method to extract project ID from JSON key
     */
    private String extractProjectId(String key) {
        try {
            if (key != null && key.contains("project_id")) {
                String[] parts = key.split("\"project_id\":");
                if (parts.length > 1) {
                    String projectPart = parts[1].trim();
                    if (projectPart.startsWith("\"")) {
                        int endIndex = projectPart.indexOf("\"", 1);
                        if (endIndex > 0) {
                            return projectPart.substring(1, endIndex);
                        }
                    }
                }
            }
            return "Unable to extract project ID from service account key";
        } catch (Exception e) {
            return "Error extracting project ID: " + e.getMessage();
        }
    }
}
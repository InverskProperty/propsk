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

        // Build enhanced statement data with full structure
        PropertyOwnerStatementData data = buildPropertyOwnerStatementData(propertyOwner, fromDate, toDate);
        List<List<Object>> values = buildEnhancedPropertyOwnerStatementValues(data);

        // Update the sheet with data using USER_ENTERED to enable formulas
        ValueRange body = new ValueRange().setValues(values);
        UpdateValuesResponse result = service.spreadsheets().values()
            .update(spreadsheetId, "A1", body)
            .setValueInputOption("USER_ENTERED") // This enables formulas!
            .execute();

        // Apply enhanced formatting with currency, colors, and borders
        applyEnhancedPropertyOwnerStatementFormatting(service, spreadsheetId, data);

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

    // Enhanced statement data building methods (copied from GoogleSheetsStatementService)

    private PropertyOwnerStatementData buildPropertyOwnerStatementData(Customer propertyOwner,
                                                                      LocalDate fromDate, LocalDate toDate) {
        PropertyOwnerStatementData data = new PropertyOwnerStatementData();
        data.setPropertyOwner(propertyOwner);
        data.setFromDate(fromDate);
        data.setToDate(toDate);
        data.setPortfolioName("PROPERTY PORTFOLIO");

        // Get properties for this owner
        List<Property> properties = propertyService.getPropertiesByOwner(propertyOwner.getCustomerId());
        data.setProperties(properties);

        // Build rental data for each property
        List<PropertyRentalData> rentalDataList = new ArrayList<>();
        for (Property property : properties) {
            PropertyRentalData rentalData = buildPropertyRentalData(property, fromDate, toDate);
            rentalDataList.add(rentalData);
        }
        data.setRentalData(rentalDataList);

        // Calculate totals
        data.setTotalRentReceived(calculateTotalRent(properties, fromDate, toDate));
        data.setTotalExpenses(calculateTotalExpenses(properties, fromDate, toDate));
        data.setNetIncome(data.getTotalRentReceived().subtract(data.getTotalExpenses()));

        return data;
    }

    private PropertyRentalData buildPropertyRentalData(Property property, LocalDate fromDate, LocalDate toDate) {
        PropertyRentalData rentalData = new PropertyRentalData();

        // Set property details including unit number
        rentalData.setPropertyAddress(buildPropertyAddress(property));
        rentalData.setUnitNumber(extractUnitNumber(property));
        rentalData.setProperty(property);

        // Get tenant info
        Customer tenant = getTenantForProperty(property.getId());
        rentalData.setTenantName(tenant != null ? tenant.getName() : "Vacant");
        rentalData.setStartDate(tenant != null ? tenant.getMoveInDate() : null);

        // Set rent amounts
        BigDecimal monthlyRent = property.getMonthlyPayment() != null ? property.getMonthlyPayment() : BigDecimal.ZERO;
        rentalData.setRentAmount(monthlyRent);
        rentalData.setRentDue(monthlyRent);

        // Set fee percentages
        BigDecimal managementPercentage = property.getCommissionPercentage() != null ?
            property.getCommissionPercentage() : new BigDecimal("10.0");
        BigDecimal servicePercentage = new BigDecimal("5.0");

        rentalData.setManagementFeePercentage(managementPercentage);
        rentalData.setServiceFeePercentage(servicePercentage);

        return rentalData;
    }

    private List<List<Object>> buildEnhancedPropertyOwnerStatementValues(PropertyOwnerStatementData data) {
        List<List<Object>> values = new ArrayList<>();

        // Header section - match CSV format exactly (12 columns)
        values.add(Arrays.asList("", "", "", "", "PROPSK LTD", "", "", "", "", "%", "", ""));
        values.add(Arrays.asList("", "", "", "", "1 Poplar Court, Greensward Lane, Hockley, England, SS5 5JB", "", "", "", "", "Management Fee", "10", ""));
        values.add(Arrays.asList("", "", "", "", "Company number 15933011", "", "", "", "", "Service Fee", "5", ""));
        values.add(Arrays.asList("", "", "", "", "", "", "", "", "", "", "", ""));
        values.add(Arrays.asList("", "", "", "", "STATEMENT", "", "", "", "", "", "", ""));
        values.add(Arrays.asList("", "", "", "", "", "", "", "", "", "", "", ""));

        // Client, Property, Period information
        values.add(Arrays.asList("CLIENT:", data.getPropertyOwner().getName(), "", "", "", "", "", "", "", "", "", ""));

        // Get property name
        String propertyName = data.getProperties().isEmpty() ? "PROPERTY PORTFOLIO" : data.getProperties().get(0).getPropertyName();
        values.add(Arrays.asList("PROPERTY:", propertyName, "", "", "", "", "", "", "", "", "", ""));

        // Format period dates
        String fromDateFormatted = data.getFromDate().format(DateTimeFormatter.ofPattern("d")) +
                                 getOrdinalSuffix(data.getFromDate().getDayOfMonth()) + " " +
                                 data.getFromDate().format(DateTimeFormatter.ofPattern("MMM yyyy"));
        String toDateFormatted = data.getToDate().format(DateTimeFormatter.ofPattern("d")) +
                               getOrdinalSuffix(data.getToDate().getDayOfMonth()) + " " +
                               data.getToDate().format(DateTimeFormatter.ofPattern("MMM yyyy"));
        values.add(Arrays.asList("PERIOD:", fromDateFormatted + " to " + toDateFormatted, "", "", "", "", "", "", "", "", "", ""));

        values.add(Arrays.asList("", "", "", "", "", "", "", "", "", "", "", ""));
        values.add(Arrays.asList("", "", "", "", "", "", "", "", "", "", "", ""));

        // Income Statement Header
        values.add(Arrays.asList("Income Statement", "", "", "", "", "", "", "", "", "", "", ""));

        // Column headers with line breaks
        values.add(Arrays.asList("Unit No.", "Tenant", "Tenancy Dates",
            "Rent\\n Due\\n Amount", "Rent\\n Received\\n Date", "Rent\\n Received\\n Amount",
            "Management\\n Fee\\n 10%", "Service\\n Fee\\n 5%",
            "Net\\n Due to\\n " + getFirstNameSafe(data.getPropertyOwner()),
            "Date\\n Paid", "Rent\\n Due less\\n Received", "Comments", "Payment Batch"));

        // Income Statement Data Rows
        int dataStartRow = values.size() + 1;

        for (PropertyRentalData rental : data.getRentalData()) {
            List<FinancialTransaction> transactions = financialTransactionRepository
                .findPropertyTransactionsForStatement(rental.getProperty().getPayPropId(), data.getFromDate(), data.getToDate());

            BigDecimal rentDue = rental.getRentAmount();
            BigDecimal rentReceived = calculateActualRentReceived(transactions);

            int currentRow = values.size() + 1;
            String managementFeeFormula = "=-F" + currentRow + "*0.1";
            String serviceFeeFormula = "=-F" + currentRow + "*0.05";
            String netDueFormula = "=F" + currentRow + "+G" + currentRow + "+H" + currentRow;
            String outstandingFormula = "=D" + currentRow + "-F" + currentRow;

            String rentReceivedDate = getPaymentDate(transactions);
            String datePaid = getDistributionDate(transactions);
            String paymentBatch = getPaymentBatchInfo(transactions);
            String comments = "";

            values.add(Arrays.asList(
                rental.getUnitNumber(),
                rental.getTenantName(),
                formatTenancyDate(rental.getStartDate()),
                rentDue,
                rentReceivedDate,
                rentReceived,
                managementFeeFormula,
                serviceFeeFormula,
                netDueFormula,
                datePaid,
                outstandingFormula,
                comments,
                paymentBatch
            ));
        }

        values.add(Arrays.asList(""));
        values.add(Arrays.asList("", "", "", "", "", "", "", "", "", "", "", "", ""));
        values.add(Arrays.asList("OFFICE", "", "", "", "", "", "", "", "0", "", "0", "Vacant", ""));

        // TOTAL row with SUM formulas
        int dataEndRow = values.size() - 2;
        values.add(Arrays.asList("TOTAL", "", "",
                                "=SUM(D" + (dataStartRow + 1) + ":D" + dataEndRow + ")", "",
                                "=SUM(F" + (dataStartRow + 1) + ":F" + dataEndRow + ")",
                                "=SUM(G" + (dataStartRow + 1) + ":G" + dataEndRow + ")",
                                "=SUM(H" + (dataStartRow + 1) + ":H" + dataEndRow + ")",
                                "=SUM(I" + (dataStartRow + 1) + ":I" + dataEndRow + ")",
                                "",
                                "=SUM(K" + (dataStartRow + 1) + ":K" + dataEndRow + ")",
                                "", ""));

        // Expenses Section
        values.add(Arrays.asList("", "", "", "", "", "", "", "", "", "", "", "", ""));
        values.add(Arrays.asList("Expenses", "", "", "", "", "", "", "", "", "", "", "", ""));
        values.add(Arrays.asList("", "", "", "", "", "", "", "", "", "", "", "", ""));
        values.add(Arrays.asList("Unit No.", "Expense Label", "", "", "",
                                "Expense \\n Amount", "Management\\n Contribution", "",
                                "Net Expense \\n Amount", "", "", "Comments", ""));

        // Get expense data
        BigDecimal totalExpenses = BigDecimal.ZERO;
        List<FinancialTransaction> expenseTransactions = getExpenseTransactions(data.getProperties(), data.getFromDate(), data.getToDate());

        for (FinancialTransaction expense : expenseTransactions) {
            String unitNo = getUnitNumberFromProperty(expense.getPropertyId());
            String expenseLabel = expense.getCategoryName() != null ? expense.getCategoryName() : expense.getDescription();
            BigDecimal expenseAmount = expense.getAmount();
            BigDecimal netExpenseAmount = expenseAmount.negate();

            values.add(Arrays.asList(unitNo, expenseLabel, "", "", "",
                                   formatCurrency(expenseAmount), "", "",
                                   formatCurrency(netExpenseAmount), "", "", "", ""));
            totalExpenses = totalExpenses.add(expenseAmount);
        }

        values.add(Arrays.asList("", "", "", "", "", "", "", "", "", "", "", "", ""));
        values.add(Arrays.asList("TOTAL", "", "", "", "", formatCurrency(totalExpenses), "", "",
                                formatCurrency(totalExpenses.negate()), "", "", "Deducted from rent", ""));

        values.add(Arrays.asList("", "", "", "", "", "", "", "", "", "", "", "", ""));

        // SUMMARY Section
        values.add(Arrays.asList("SUMMARY", "", "", "", "", "", "", "", "", "", "", "", ""));
        values.add(Arrays.asList("", "", "", "", "", "", "", "", "", "", "", "", ""));

        int totalRowNumber = dataStartRow + data.getRentalData().size() + 4;
        int expenseRowNumber = totalRowNumber + 15;

        values.add(Arrays.asList("Total Rent Due for the Period", "", "", "=D" + totalRowNumber, "", "", "", "", "", "", "", "", ""));
        values.add(Arrays.asList("Total Received by Propsk", "", "", "=F" + totalRowNumber, "", "", "", "", "", "", "", "", ""));
        values.add(Arrays.asList("", "", "", "", "", "", "", "", "", "", "", "", ""));
        values.add(Arrays.asList("Management Fee", "", "", "=G" + totalRowNumber, "", "", "", "", "", "", "", "", ""));
        values.add(Arrays.asList("Service Charge", "", "", "=H" + totalRowNumber, "", "", "", "", "", "", "", "", ""));
        values.add(Arrays.asList("Expenses Paid by Agent", "", "", "=I" + expenseRowNumber, "", "", "", "", "", "", "", "", ""));
        values.add(Arrays.asList("", "", "", "", "", "", "", "", "", "", "", "", ""));
        values.add(Arrays.asList("Net Due to " + getFirstNameSafe(data.getPropertyOwner()), "", "",
                                "=I" + totalRowNumber + "+I" + expenseRowNumber, "", "", "", "", "", "", "", "", ""));

        return values;
    }

    private void applyEnhancedPropertyOwnerStatementFormatting(Sheets sheetsService, String spreadsheetId, PropertyOwnerStatementData data)
            throws IOException {
        List<Request> requests = new ArrayList<>();

        // Header formatting
        requests.add(new Request().setRepeatCell(new RepeatCellRequest()
            .setRange(new GridRange().setSheetId(0).setStartRowIndex(0).setEndRowIndex(6))
            .setCell(new CellData().setUserEnteredFormat(new CellFormat()
                .setTextFormat(new TextFormat().setBold(true).setFontSize(12))
                .setBackgroundColor(new Color().setRed(0.9f).setGreen(0.95f).setBlue(1.0f))))
            .setFields("userEnteredFormat.textFormat.bold,userEnteredFormat.textFormat.fontSize,userEnteredFormat.backgroundColor")));

        // Currency formatting for amount columns
        int[] currencyColumns = {3, 5, 6, 7, 8, 10};
        for (int col : currencyColumns) {
            requests.add(new Request().setRepeatCell(new RepeatCellRequest()
                .setRange(new GridRange().setSheetId(0)
                    .setStartRowIndex(13).setEndRowIndex(50)
                    .setStartColumnIndex(col).setEndColumnIndex(col + 1))
                .setCell(new CellData().setUserEnteredFormat(new CellFormat()
                    .setNumberFormat(new NumberFormat()
                        .setType("CURRENCY")
                        .setPattern("£#,##0.00"))))
                .setFields("userEnteredFormat.numberFormat")));
        }

        // Execute formatting
        BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
            .setRequests(requests);
        sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute();
    }

    // Helper methods
    private String getOrdinalSuffix(int day) {
        if (day >= 11 && day <= 13) return "th";
        switch (day % 10) {
            case 1: return "st";
            case 2: return "nd";
            case 3: return "rd";
            default: return "th";
        }
    }

    private BigDecimal calculateActualRentReceived(List<FinancialTransaction> transactions) {
        return transactions.stream()
            .filter(t -> "invoice".equals(t.getTransactionType()) || "rent".equalsIgnoreCase(t.getCategoryName()))
            .map(FinancialTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String getPaymentDate(List<FinancialTransaction> transactions) {
        return transactions.stream()
            .filter(t -> "invoice".equals(t.getTransactionType()))
            .findFirst()
            .map(t -> t.getTransactionDate().format(DateTimeFormatter.ofPattern("d/M/yyyy")))
            .orElse("");
    }

    private String getDistributionDate(List<FinancialTransaction> transactions) {
        return transactions.stream()
            .filter(t -> t.getReconciliationDate() != null)
            .findFirst()
            .map(t -> t.getReconciliationDate().format(DateTimeFormatter.ofPattern("d/M/yyyy")))
            .orElse("");
    }

    private String getPaymentBatchInfo(List<FinancialTransaction> transactions) {
        return transactions.stream()
            .filter(t -> t.getPayPropBatchId() != null)
            .findFirst()
            .map(FinancialTransaction::getPayPropBatchId)
            .orElse("");
    }

    private String formatTenancyDate(LocalDate startDate) {
        if (startDate == null) return "";
        return startDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0";
        return amount.toString();
    }

    private List<FinancialTransaction> getExpenseTransactions(List<Property> properties, LocalDate fromDate, LocalDate toDate) {
        List<FinancialTransaction> allExpenses = new ArrayList<>();

        for (Property property : properties) {
            if (property.getPayPropId() != null) {
                List<FinancialTransaction> propertyExpenses = financialTransactionRepository
                    .findByPropertyAndDateRange(property.getPayPropId(), fromDate, toDate)
                    .stream()
                    .filter(this::isExpenseTransaction)
                    .collect(Collectors.toList());
                allExpenses.addAll(propertyExpenses);
            }
        }

        return allExpenses;
    }

    private boolean isExpenseTransaction(FinancialTransaction transaction) {
        String type = transaction.getTransactionType();
        String category = transaction.getCategoryName();

        if (type != null) {
            return type.equals("payment_to_contractor") ||
                   type.equals("payment_to_beneficiary") ||
                   type.equals("payment_to_agency") ||
                   type.equals("payment_property_account") ||
                   type.equals("payment_deposit_account") ||
                   type.equals("debit_note") ||
                   type.equals("adjustment") ||
                   type.equals("refund");
        }

        if (category != null) {
            String catLower = category.toLowerCase();
            return catLower.contains("maintenance") ||
                   catLower.contains("repair") ||
                   catLower.contains("clean") ||
                   catLower.contains("contractor") ||
                   catLower.contains("service") ||
                   catLower.contains("utilities") ||
                   catLower.contains("insurance") ||
                   catLower.contains("management");
        }

        return false;
    }

    private String getUnitNumberFromProperty(String propertyId) {
        try {
            List<Property> properties = propertyService.findAll();
            Property property = properties.stream()
                .filter(p -> propertyId.equals(p.getPayPropId()))
                .findFirst()
                .orElse(null);

            if (property != null) {
                return extractUnitNumber(property);
            }

            return "Unit " + propertyId;
        } catch (Exception e) {
            return "";
        }
    }

    private String extractUnitNumber(Property property) {
        String name = property.getPropertyName();
        if (name != null) {
            if (name.toLowerCase().contains("flat")) {
                return name;
            }
            if (name.toLowerCase().contains("unit")) {
                return name;
            }
        }

        String address = property.getFullAddress();
        if (address != null) {
            if (address.toLowerCase().contains("flat")) {
                String[] parts = address.split(",");
                for (String part : parts) {
                    if (part.trim().toLowerCase().contains("flat")) {
                        return part.trim();
                    }
                }
            }
        }

        return property.getPropertyName() != null ? property.getPropertyName() : "Property " + property.getId();
    }

    private String buildPropertyAddress(Property property) {
        StringBuilder address = new StringBuilder();
        if (property.getAddressLine1() != null && !property.getAddressLine1().trim().isEmpty()) {
            address.append(property.getAddressLine1());
        }
        if (property.getAddressLine2() != null && !property.getAddressLine2().trim().isEmpty()) {
            if (address.length() > 0) address.append(", ");
            address.append(property.getAddressLine2());
        }
        if (property.getCity() != null && !property.getCity().trim().isEmpty()) {
            if (address.length() > 0) address.append(", ");
            address.append(property.getCity());
        }

        if (address.length() == 0 && property.getPropertyName() != null) {
            return property.getPropertyName();
        }

        return address.toString();
    }

    private Customer getTenantForProperty(Long propertyId) {
        try {
            List<Customer> tenants = customerService.findByAssignedPropertyId(propertyId);
            if (!tenants.isEmpty()) {
                return tenants.stream()
                    .filter(customer -> customer.getIsTenant() != null && customer.getIsTenant())
                    .filter(customer -> customer.getMoveOutDate() == null || customer.getMoveOutDate().isAfter(LocalDate.now()))
                    .findFirst()
                    .orElse(tenants.get(0));
            }

            List<Customer> entityTenants = customerService.findByEntityTypeAndEntityId("Property", propertyId);
            return entityTenants.stream()
                .filter(customer -> customer.getIsTenant() != null && customer.getIsTenant())
                .findFirst()
                .orElse(null);

        } catch (Exception e) {
            System.err.println("Error finding tenant for property " + propertyId + ": " + e.getMessage());
            return null;
        }
    }

    private BigDecimal calculateTotalRent(List<Property> properties, LocalDate fromDate, LocalDate toDate) {
        return properties.stream()
            .filter(property -> property.getMonthlyPayment() != null)
            .map(Property::getMonthlyPayment)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateTotalExpenses(List<Property> properties, LocalDate fromDate, LocalDate toDate) {
        BigDecimal totalExpenses = BigDecimal.ZERO;

        for (Property property : properties) {
            totalExpenses = totalExpenses.add(calculatePropertyExpenses(property, fromDate, toDate));
        }

        return totalExpenses;
    }

    private BigDecimal calculatePropertyExpenses(Property property, LocalDate fromDate, LocalDate toDate) {
        try {
            String propertyPayPropId = property.getPayPropId();
            if (propertyPayPropId != null) {
                return financialTransactionRepository
                    .sumExpensesForProperty(propertyPayPropId, fromDate, toDate);
            }

            return BigDecimal.ZERO;
        } catch (Exception e) {
            System.err.println("Error calculating expenses for property " + property.getId() + ": " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private String getFirstNameSafe(Customer customer) {
        if (customer == null) {
            return "Owner";
        }

        String name = customer.getName();
        if (name == null || name.trim().isEmpty()) {
            return "Owner";
        }

        String[] nameParts = name.trim().split("\\s+");
        return nameParts.length > 0 ? nameParts[0] : "Owner";
    }

    // Data classes for enhanced statements
    public static class PropertyOwnerStatementData {
        private Customer propertyOwner;
        private LocalDate fromDate;
        private LocalDate toDate;
        private String portfolioName;
        private List<Property> properties;
        private List<PropertyRentalData> rentalData;
        private BigDecimal totalRentReceived;
        private BigDecimal totalExpenses;
        private BigDecimal netIncome;

        // Getters and setters
        public Customer getPropertyOwner() { return propertyOwner; }
        public void setPropertyOwner(Customer propertyOwner) { this.propertyOwner = propertyOwner; }

        public LocalDate getFromDate() { return fromDate; }
        public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }

        public LocalDate getToDate() { return toDate; }
        public void setToDate(LocalDate toDate) { this.toDate = toDate; }

        public String getPortfolioName() { return portfolioName; }
        public void setPortfolioName(String portfolioName) { this.portfolioName = portfolioName; }

        public List<Property> getProperties() { return properties; }
        public void setProperties(List<Property> properties) { this.properties = properties; }

        public List<PropertyRentalData> getRentalData() { return rentalData; }
        public void setRentalData(List<PropertyRentalData> rentalData) { this.rentalData = rentalData; }

        public BigDecimal getTotalRentReceived() { return totalRentReceived; }
        public void setTotalRentReceived(BigDecimal totalRentReceived) { this.totalRentReceived = totalRentReceived; }

        public BigDecimal getTotalExpenses() { return totalExpenses; }
        public void setTotalExpenses(BigDecimal totalExpenses) { this.totalExpenses = totalExpenses; }

        public BigDecimal getNetIncome() { return netIncome; }
        public void setNetIncome(BigDecimal netIncome) { this.netIncome = netIncome; }
    }

    public static class PropertyRentalData {
        private Property property;
        private String unitNumber;
        private String propertyAddress;
        private String tenantName;
        private LocalDate startDate;
        private BigDecimal rentAmount;
        private BigDecimal rentDue;
        private BigDecimal managementFeePercentage;
        private BigDecimal serviceFeePercentage;

        // Getters and setters
        public Property getProperty() { return property; }
        public void setProperty(Property property) { this.property = property; }

        public String getUnitNumber() { return unitNumber; }
        public void setUnitNumber(String unitNumber) { this.unitNumber = unitNumber; }

        public String getPropertyAddress() { return propertyAddress; }
        public void setPropertyAddress(String propertyAddress) { this.propertyAddress = propertyAddress; }

        public String getTenantName() { return tenantName; }
        public void setTenantName(String tenantName) { this.tenantName = tenantName; }

        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

        public BigDecimal getRentAmount() { return rentAmount; }
        public void setRentAmount(BigDecimal rentAmount) { this.rentAmount = rentAmount; }

        public BigDecimal getRentDue() { return rentDue; }
        public void setRentDue(BigDecimal rentDue) { this.rentDue = rentDue; }

        public BigDecimal getManagementFeePercentage() { return managementFeePercentage; }
        public void setManagementFeePercentage(BigDecimal managementFeePercentage) { this.managementFeePercentage = managementFeePercentage; }

        public BigDecimal getServiceFeePercentage() { return serviceFeePercentage; }
        public void setServiceFeePercentage(BigDecimal serviceFeePercentage) { this.serviceFeePercentage = serviceFeePercentage; }
    }
}
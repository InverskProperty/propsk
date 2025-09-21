package site.easy.to.build.crm.service.sheets;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;
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

    @Autowired
    public GoogleSheetsServiceAccountService(CustomerService customerService,
                                           PropertyService propertyService,
                                           FinancialTransactionRepository financialTransactionRepository) {
        this.customerService = customerService;
        this.propertyService = propertyService;
        this.financialTransactionRepository = financialTransactionRepository;
    }

    /**
     * Create a Sheets service using service account credentials
     */
    private Sheets createSheetsService() throws IOException, GeneralSecurityException {
        System.out.println("🔧 ServiceAccount: Creating Google Sheets service...");

        try {
            // Check if service account key is available
            if (serviceAccountKey == null || serviceAccountKey.trim().isEmpty()) {
                throw new IOException("Service account key is not configured (GOOGLE_SERVICE_ACCOUNT_KEY environment variable)");
            }

            System.out.println("🔧 ServiceAccount: Key length: " + serviceAccountKey.length() + " characters");

            GoogleCredential credential = GoogleCredential
                .fromStream(new ByteArrayInputStream(serviceAccountKey.getBytes()))
                .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS));

            System.out.println("🔧 ServiceAccount: Credential created successfully");
            System.out.println("🔧 ServiceAccount: Service account email: " + credential.getServiceAccountId());

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

        Sheets service = createSheetsService();

        // Create simple test spreadsheet
        String title = "Google Sheets API Test - " + new java.util.Date();
        System.out.println("🧪 ServiceAccount: Creating test spreadsheet with title: " + title);

        Spreadsheet spreadsheet = new Spreadsheet()
            .setProperties(new SpreadsheetProperties().setTitle(title));

        try {
            System.out.println("🧪 ServiceAccount: Calling Google Sheets API to create test spreadsheet...");
            spreadsheet = service.spreadsheets().create(spreadsheet).execute();
            String spreadsheetId = spreadsheet.getSpreadsheetId();
            System.out.println("✅ ServiceAccount: Test spreadsheet created successfully: " + spreadsheetId);
            return spreadsheetId;
        } catch (Exception e) {
            System.err.println("❌ ServiceAccount: Failed to create test spreadsheet: " + e.getMessage());
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
                    .createScoped(java.util.Collections.singleton(
                        "https://www.googleapis.com/auth/cloud-platform.read-only"));

            System.out.println("🧪 ServiceAccount: Credential created with read-only scope");
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

        Sheets service = createSheetsService();

        // Create new spreadsheet
        String title = String.format("Property Owner Statement - %s - %s to %s",
            getCustomerName(propertyOwner),
            fromDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
            toDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        System.out.println("📊 ServiceAccount: Creating spreadsheet with title: " + title);

        Spreadsheet spreadsheet = new Spreadsheet()
            .setProperties(new SpreadsheetProperties().setTitle(title));

        try {
            System.out.println("📊 ServiceAccount: Calling Google Sheets API to create spreadsheet...");
            spreadsheet = service.spreadsheets().create(spreadsheet).execute();
            System.out.println("✅ ServiceAccount: Spreadsheet created successfully: " + spreadsheet.getSpreadsheetId());
        } catch (Exception e) {
            System.err.println("❌ ServiceAccount: Failed to create spreadsheet: " + e.getMessage());
            if (e.getMessage().contains("403") || e.getMessage().contains("forbidden")) {
                System.err.println("💡 ServiceAccount: This is likely a permissions issue. Check:");
                System.err.println("   1. Google Sheets API is enabled in Google Cloud Console");
                System.err.println("   2. Service account has proper permissions");
                System.err.println("   3. Service account key is valid and not expired");
            }
            throw e;
        }
        String spreadsheetId = spreadsheet.getSpreadsheetId();

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

        Sheets service = createSheetsService();

        // Create new spreadsheet
        Spreadsheet spreadsheet = new Spreadsheet()
            .setProperties(new SpreadsheetProperties()
                .setTitle(String.format("Tenant Statement - %s - %s to %s",
                    getCustomerName(tenant),
                    fromDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    toDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))));

        spreadsheet = service.spreadsheets().create(spreadsheet).execute();
        String spreadsheetId = spreadsheet.getSpreadsheetId();

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
}
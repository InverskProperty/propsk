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
        GoogleCredential credential = GoogleCredential
            .fromStream(new ByteArrayInputStream(serviceAccountKey.getBytes()))
            .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS));

        return new Sheets.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            credential)
            .setApplicationName("CRM Property Management")
            .build();
    }

    /**
     * Creates a property owner statement in Google Sheets
     */
    public String createPropertyOwnerStatement(Customer propertyOwner, LocalDate fromDate, LocalDate toDate)
            throws IOException, GeneralSecurityException {

        Sheets service = createSheetsService();

        // Create new spreadsheet
        Spreadsheet spreadsheet = new Spreadsheet()
            .setProperties(new SpreadsheetProperties()
                .setTitle(String.format("Property Owner Statement - %s - %s to %s",
                    getCustomerName(propertyOwner),
                    fromDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    toDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))));

        spreadsheet = service.spreadsheets().create(spreadsheet).execute();
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
                .findByPropertyIdAndDateBetween(property.getPropertyId(), fromDate, toDate);

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
                .findByPropertyIdAndDateBetween(property.getPropertyId(), fromDate, toDate);

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
                property.getPropertyName() != null ? property.getPropertyName() : "Property " + property.getPropertyId(),
                property.getAddress() != null ? property.getAddress() : "No address",
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
            values.add(Arrays.asList("Property:", property.getPropertyName() != null ? property.getPropertyName() : "Property " + property.getPropertyId()));
            values.add(Arrays.asList("Address:", property.getAddress() != null ? property.getAddress() : "No address"));
        }
        values.add(Arrays.asList("Period:", fromDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) +
                                          " to " + toDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
        values.add(Arrays.asList("Generated:", LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
        values.add(Arrays.asList(""));

        // Transaction details
        if (property != null) {
            List<FinancialTransaction> transactions = financialTransactionRepository
                .findByPropertyIdAndDateBetween(property.getPropertyId(), fromDate, toDate);

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
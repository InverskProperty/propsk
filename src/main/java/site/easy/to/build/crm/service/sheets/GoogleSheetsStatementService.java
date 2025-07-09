package site.easy.to.build.crm.service.sheets;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerType;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.property.PropertyService;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class GoogleSheetsStatementService {

    private final CustomerService customerService;
    private final PropertyService propertyService;

    @Autowired
    public GoogleSheetsStatementService(CustomerService customerService, PropertyService propertyService) {
        this.customerService = customerService;
        this.propertyService = propertyService;
    }

    /**
     * Creates a property owner statement in Google Sheets
     */
    public String createPropertyOwnerStatement(OAuthUser oAuthUser, Customer propertyOwner, 
                                             LocalDate fromDate, LocalDate toDate) 
            throws IOException, GeneralSecurityException {
        
        Sheets sheetsService = getSheetsService(oAuthUser);
        
        // Create new spreadsheet
        Spreadsheet spreadsheet = new Spreadsheet()
            .setProperties(new SpreadsheetProperties()
                .setTitle(generateStatementTitle(propertyOwner, fromDate, toDate)));
        
        Spreadsheet createdSheet = sheetsService.spreadsheets().create(spreadsheet).execute();
        String spreadsheetId = createdSheet.getSpreadsheetId();
        
        // Build statement data
        PropertyOwnerStatementData data = buildPropertyOwnerStatementData(propertyOwner, fromDate, toDate);
        
        // Create headers and data rows
        List<List<Object>> values = buildPropertyOwnerStatementValues(data);
        
        // Write data to sheet
        ValueRange body = new ValueRange().setValues(values);
        sheetsService.spreadsheets().values()
            .update(spreadsheetId, "A1", body)
            .setValueInputOption("RAW")
            .execute();
        
        // Apply formatting
        applyPropertyOwnerStatementFormatting(sheetsService, spreadsheetId);
        
        return spreadsheetId;
    }

    /**
     * Creates a tenant statement in Google Sheets
     */
    public String createTenantStatement(OAuthUser oAuthUser, Customer tenant, 
                                       LocalDate fromDate, LocalDate toDate) 
            throws IOException, GeneralSecurityException {
        
        Sheets sheetsService = getSheetsService(oAuthUser);
        
        // Create new spreadsheet
        Spreadsheet spreadsheet = new Spreadsheet()
            .setProperties(new SpreadsheetProperties()
                .setTitle(generateStatementTitle(tenant, fromDate, toDate)));
        
        Spreadsheet createdSheet = sheetsService.spreadsheets().create(spreadsheet).execute();
        String spreadsheetId = createdSheet.getSpreadsheetId();
        
        // Build statement data
        TenantStatementData data = buildTenantStatementData(tenant, fromDate, toDate);
        
        // Create headers and data rows
        List<List<Object>> values = buildTenantStatementValues(data);
        
        // Write data to sheet
        ValueRange body = new ValueRange().setValues(values);
        sheetsService.spreadsheets().values()
            .update(spreadsheetId, "A1", body)
            .setValueInputOption("RAW")
            .execute();
        
        // Apply formatting
        applyTenantStatementFormatting(sheetsService, spreadsheetId);
        
        return spreadsheetId;
    }

    /**
     * Creates a portfolio summary statement
     */
    public String createPortfolioStatement(OAuthUser oAuthUser, Customer propertyOwner, 
                                         LocalDate fromDate, LocalDate toDate) 
            throws IOException, GeneralSecurityException {
        
        Sheets sheetsService = getSheetsService(oAuthUser);
        
        // Create new spreadsheet
        Spreadsheet spreadsheet = new Spreadsheet()
            .setProperties(new SpreadsheetProperties()
                .setTitle("Portfolio_Statement_" + propertyOwner.getName() + "_" + fromDate.format(DateTimeFormatter.ofPattern("yyyy-MM"))));
        
        Spreadsheet createdSheet = sheetsService.spreadsheets().create(spreadsheet).execute();
        String spreadsheetId = createdSheet.getSpreadsheetId();
        
        // Build portfolio data
        PortfolioStatementData data = buildPortfolioStatementData(propertyOwner, fromDate, toDate);
        
        // Create headers and data rows
        List<List<Object>> values = buildPortfolioStatementValues(data);
        
        // Write data to sheet
        ValueRange body = new ValueRange().setValues(values);
        sheetsService.spreadsheets().values()
            .update(spreadsheetId, "A1", body)
            .setValueInputOption("RAW")
            .execute();
        
        // Apply formatting
        applyPortfolioStatementFormatting(sheetsService, spreadsheetId);
        
        return spreadsheetId;
    }

    // Private helper methods for building statement data
    private PropertyOwnerStatementData buildPropertyOwnerStatementData(Customer propertyOwner, 
                                                                      LocalDate fromDate, LocalDate toDate) {
        PropertyOwnerStatementData data = new PropertyOwnerStatementData();
        data.setPropertyOwner(propertyOwner);
        data.setFromDate(fromDate);
        data.setToDate(toDate);
        
        // Get properties for this owner
        List<Property> properties = propertyService.getPropertiesByOwner(propertyOwner.getCustomerId());
        data.setProperties(properties);
        
        // Calculate totals (you'll need to implement these based on your business logic)
        data.setTotalRentReceived(calculateTotalRent(properties, fromDate, toDate));
        data.setTotalExpenses(calculateTotalExpenses(properties, fromDate, toDate));
        data.setNetIncome(data.getTotalRentReceived().subtract(data.getTotalExpenses()));
        
        return data;
    }

    private TenantStatementData buildTenantStatementData(Customer tenant, LocalDate fromDate, LocalDate toDate) {
        TenantStatementData data = new TenantStatementData();
        data.setTenant(tenant);
        data.setFromDate(fromDate);
        data.setToDate(toDate);
        
        // Get tenant's property
        Property property = propertyService.getPropertyByTenant(tenant.getCustomerId());
        data.setProperty(property);
        
        // Calculate rent and payments
        data.setRentDue(calculateRentDue(tenant, fromDate, toDate));
        data.setPaymentsMade(calculatePaymentsMade(tenant, fromDate, toDate));
        data.setBalance(data.getRentDue().subtract(data.getPaymentsMade()));
        
        return data;
    }

    private PortfolioStatementData buildPortfolioStatementData(Customer propertyOwner, 
                                                               LocalDate fromDate, LocalDate toDate) {
        PortfolioStatementData data = new PortfolioStatementData();
        data.setPropertyOwner(propertyOwner);
        data.setFromDate(fromDate);
        data.setToDate(toDate);
        
        // Get all properties for portfolio view
        List<Property> properties = propertyService.getPropertiesByOwner(propertyOwner.getCustomerId());
        
        // Build summary data for each property
        List<PropertySummary> summaries = new ArrayList<>();
        for (Property property : properties) {
            PropertySummary summary = new PropertySummary();
            summary.setProperty(property);
            summary.setRentReceived(calculatePropertyRent(property, fromDate, toDate));
            summary.setExpenses(calculatePropertyExpenses(property, fromDate, toDate));
            summary.setNetIncome(summary.getRentReceived().subtract(summary.getExpenses()));
            summaries.add(summary);
        }
        
        data.setPropertySummaries(summaries);
        
        return data;
    }

    private List<List<Object>> buildPropertyOwnerStatementValues(PropertyOwnerStatementData data) {
        List<List<Object>> values = new ArrayList<>();
        
        // Header
        values.add(Arrays.asList("PROPERTY OWNER STATEMENT"));
        values.add(Arrays.asList(""));
        values.add(Arrays.asList("Owner:", data.getPropertyOwner().getName()));
        values.add(Arrays.asList("Email:", data.getPropertyOwner().getEmail()));
        values.add(Arrays.asList("Period:", data.getFromDate().toString() + " to " + data.getToDate().toString()));
        values.add(Arrays.asList("Generated:", LocalDate.now().toString()));
        values.add(Arrays.asList(""));
        
        // Property details
        values.add(Arrays.asList("PROPERTY DETAILS"));
        values.add(Arrays.asList("Property", "Address", "Rent", "Expenses", "Net Income"));
        
        for (Property property : data.getProperties()) {
            BigDecimal rent = calculatePropertyRent(property, data.getFromDate(), data.getToDate());
            BigDecimal expenses = calculatePropertyExpenses(property, data.getFromDate(), data.getToDate());
            BigDecimal net = rent.subtract(expenses);
            
            values.add(Arrays.asList(
                property.getPropertyName(),
                property.getAddressLine1(),
                "£" + rent.toString(),
                "£" + expenses.toString(),
                "£" + net.toString()
            ));
        }
        
        values.add(Arrays.asList(""));
        
        // Summary
        values.add(Arrays.asList("SUMMARY"));
        values.add(Arrays.asList("Total Rent Received:", "£" + data.getTotalRentReceived().toString()));
        values.add(Arrays.asList("Total Expenses:", "£" + data.getTotalExpenses().toString()));
        values.add(Arrays.asList("Net Income:", "£" + data.getNetIncome().toString()));
        
        return values;
    }

    private List<List<Object>> buildTenantStatementValues(TenantStatementData data) {
        List<List<Object>> values = new ArrayList<>();
        
        // Header
        values.add(Arrays.asList("TENANT STATEMENT"));
        values.add(Arrays.asList(""));
        values.add(Arrays.asList("Tenant:", data.getTenant().getName()));
        values.add(Arrays.asList("Email:", data.getTenant().getEmail()));
        values.add(Arrays.asList("Property:", data.getProperty().getPropertyName()));
        values.add(Arrays.asList("Period:", data.getFromDate().toString() + " to " + data.getToDate().toString()));
        values.add(Arrays.asList("Generated:", LocalDate.now().toString()));
        values.add(Arrays.asList(""));
        
        // Payment details
        values.add(Arrays.asList("PAYMENT DETAILS"));
        values.add(Arrays.asList("Rent Due:", "£" + data.getRentDue().toString()));
        values.add(Arrays.asList("Payments Made:", "£" + data.getPaymentsMade().toString()));
        values.add(Arrays.asList("Balance:", "£" + data.getBalance().toString()));
        
        return values;
    }

    private List<List<Object>> buildPortfolioStatementValues(PortfolioStatementData data) {
        List<List<Object>> values = new ArrayList<>();
        
        // Header
        values.add(Arrays.asList("PORTFOLIO STATEMENT"));
        values.add(Arrays.asList(""));
        values.add(Arrays.asList("Property Owner:", data.getPropertyOwner().getName()));
        values.add(Arrays.asList("Period:", data.getFromDate().toString() + " to " + data.getToDate().toString()));
        values.add(Arrays.asList("Generated:", LocalDate.now().toString()));
        values.add(Arrays.asList(""));
        
        // Portfolio summary
        values.add(Arrays.asList("PORTFOLIO SUMMARY"));
        values.add(Arrays.asList("Property", "Address", "Rent", "Expenses", "Net Income", "Occupancy"));
        
        BigDecimal totalRent = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;
        
        for (PropertySummary summary : data.getPropertySummaries()) {
            totalRent = totalRent.add(summary.getRentReceived());
            totalExpenses = totalExpenses.add(summary.getExpenses());
            
            values.add(Arrays.asList(
                summary.getProperty().getPropertyName(),
                summary.getProperty().getAddressLine1(),
                "£" + summary.getRentReceived().toString(),
                "£" + summary.getExpenses().toString(),
                "£" + summary.getNetIncome().toString(),
                "Occupied" // You can enhance this with actual occupancy data
            ));
        }
        
        values.add(Arrays.asList(""));
        values.add(Arrays.asList("TOTALS"));
        values.add(Arrays.asList("Total Rent:", "£" + totalRent.toString()));
        values.add(Arrays.asList("Total Expenses:", "£" + totalExpenses.toString()));
        values.add(Arrays.asList("Net Income:", "£" + totalRent.subtract(totalExpenses).toString()));
        
        return values;
    }

    // Formatting methods
    private void applyPropertyOwnerStatementFormatting(Sheets sheetsService, String spreadsheetId) 
            throws IOException {
        // Apply bold formatting to headers
        List<Request> requests = new ArrayList<>();
        
        // Bold header row
        requests.add(new Request().setRepeatCell(new RepeatCellRequest()
            .setRange(new GridRange().setSheetId(0).setStartRowIndex(0).setEndRowIndex(1))
            .setCell(new CellData().setUserEnteredFormat(new CellFormat()
                .setTextFormat(new TextFormat().setBold(true))))
            .setFields("userEnteredFormat.textFormat.bold")));
        
        // Execute formatting
        BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
            .setRequests(requests);
        sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute();
    }

    private void applyTenantStatementFormatting(Sheets sheetsService, String spreadsheetId) 
            throws IOException {
        // Similar formatting as property owner statement
        applyPropertyOwnerStatementFormatting(sheetsService, spreadsheetId);
    }

    private void applyPortfolioStatementFormatting(Sheets sheetsService, String spreadsheetId) 
            throws IOException {
        // Similar formatting as property owner statement
        applyPropertyOwnerStatementFormatting(sheetsService, spreadsheetId);
    }

    // Helper methods (you'll need to implement these based on your business logic)
    private BigDecimal calculateTotalRent(List<Property> properties, LocalDate fromDate, LocalDate toDate) {
        // Implement rent calculation logic
        return BigDecimal.ZERO;
    }

    private BigDecimal calculateTotalExpenses(List<Property> properties, LocalDate fromDate, LocalDate toDate) {
        // Implement expense calculation logic
        return BigDecimal.ZERO;
    }

    private BigDecimal calculatePropertyRent(Property property, LocalDate fromDate, LocalDate toDate) {
        // Implement property-specific rent calculation
        return BigDecimal.ZERO;
    }

    private BigDecimal calculatePropertyExpenses(Property property, LocalDate fromDate, LocalDate toDate) {
        // Implement property-specific expense calculation
        return BigDecimal.ZERO;
    }

    private BigDecimal calculateRentDue(Customer tenant, LocalDate fromDate, LocalDate toDate) {
        // Implement rent due calculation for tenant
        return BigDecimal.ZERO;
    }

    private BigDecimal calculatePaymentsMade(Customer tenant, LocalDate fromDate, LocalDate toDate) {
        // Implement payments made calculation
        return BigDecimal.ZERO;
    }

    private Sheets getSheetsService(OAuthUser oAuthUser) throws IOException, GeneralSecurityException {
        GoogleCredential credential = new GoogleCredential().setAccessToken(oAuthUser.getAccessToken());
        
        return new Sheets.Builder(GoogleNetHttpTransport.newTrustedTransport(), 
                                 GsonFactory.getDefaultInstance(), 
                                 credential)
            .setApplicationName("CRM Property Management")
            .build();
    }

    private String generateStatementTitle(Customer customer, LocalDate fromDate, LocalDate toDate) {
        String type = customer.getCustomerType() == CustomerType.TENANT ? "Tenant" : "Owner";
        return String.format("%s_Statement_%s_%s", type, customer.getName().replaceAll("[^a-zA-Z0-9]", "_"), 
                           fromDate.format(DateTimeFormatter.ofPattern("yyyy-MM")));
    }

    // Data classes
    private static class PropertyOwnerStatementData {
        private Customer propertyOwner;
        private LocalDate fromDate;
        private LocalDate toDate;
        private List<Property> properties;
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
        
        public List<Property> getProperties() { return properties; }
        public void setProperties(List<Property> properties) { this.properties = properties; }
        
        public BigDecimal getTotalRentReceived() { return totalRentReceived; }
        public void setTotalRentReceived(BigDecimal totalRentReceived) { this.totalRentReceived = totalRentReceived; }
        
        public BigDecimal getTotalExpenses() { return totalExpenses; }
        public void setTotalExpenses(BigDecimal totalExpenses) { this.totalExpenses = totalExpenses; }
        
        public BigDecimal getNetIncome() { return netIncome; }
        public void setNetIncome(BigDecimal netIncome) { this.netIncome = netIncome; }
    }

    private static class TenantStatementData {
        private Customer tenant;
        private Property property;
        private LocalDate fromDate;
        private LocalDate toDate;
        private BigDecimal rentDue;
        private BigDecimal paymentsMade;
        private BigDecimal balance;

        // Getters and setters
        public Customer getTenant() { return tenant; }
        public void setTenant(Customer tenant) { this.tenant = tenant; }
        
        public Property getProperty() { return property; }
        public void setProperty(Property property) { this.property = property; }
        
        public LocalDate getFromDate() { return fromDate; }
        public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }
        
        public LocalDate getToDate() { return toDate; }
        public void setToDate(LocalDate toDate) { this.toDate = toDate; }
        
        public BigDecimal getRentDue() { return rentDue; }
        public void setRentDue(BigDecimal rentDue) { this.rentDue = rentDue; }
        
        public BigDecimal getPaymentsMade() { return paymentsMade; }
        public void setPaymentsMade(BigDecimal paymentsMade) { this.paymentsMade = paymentsMade; }
        
        public BigDecimal getBalance() { return balance; }
        public void setBalance(BigDecimal balance) { this.balance = balance; }
    }

    private static class PortfolioStatementData {
        private Customer propertyOwner;
        private LocalDate fromDate;
        private LocalDate toDate;
        private List<PropertySummary> propertySummaries;

        // Getters and setters
        public Customer getPropertyOwner() { return propertyOwner; }
        public void setPropertyOwner(Customer propertyOwner) { this.propertyOwner = propertyOwner; }
        
        public LocalDate getFromDate() { return fromDate; }
        public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }
        
        public LocalDate getToDate() { return toDate; }
        public void setToDate(LocalDate toDate) { this.toDate = toDate; }
        
        public List<PropertySummary> getPropertySummaries() { return propertySummaries; }
        public void setPropertySummaries(List<PropertySummary> propertySummaries) { this.propertySummaries = propertySummaries; }
    }

    private static class PropertySummary {
        private Property property;
        private BigDecimal rentReceived;
        private BigDecimal expenses;
        private BigDecimal netIncome;

        // Getters and setters
        public Property getProperty() { return property; }
        public void setProperty(Property property) { this.property = property; }
        
        public BigDecimal getRentReceived() { return rentReceived; }
        public void setRentReceived(BigDecimal rentReceived) { this.rentReceived = rentReceived; }
        
        public BigDecimal getExpenses() { return expenses; }
        public void setExpenses(BigDecimal expenses) { this.expenses = expenses; }
        
        public BigDecimal getNetIncome() { return netIncome; }
        public void setNetIncome(BigDecimal netIncome) { this.netIncome = netIncome; }
    }
}
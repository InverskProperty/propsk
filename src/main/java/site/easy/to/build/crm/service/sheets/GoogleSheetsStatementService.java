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
import site.easy.to.build.crm.repository.FinancialTransactionRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class GoogleSheetsStatementService {

    private final CustomerService customerService;
    private final PropertyService propertyService;
    private final FinancialTransactionRepository financialTransactionRepository;

    @Autowired
    public GoogleSheetsStatementService(CustomerService customerService, 
                                      PropertyService propertyService,
                                      FinancialTransactionRepository financialTransactionRepository) {
        this.customerService = customerService;
        this.propertyService = propertyService;
        this.financialTransactionRepository = financialTransactionRepository;
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
        data.setPortfolioName("PROPERTY PORTFOLIO"); // Set a default portfolio name
        
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
        
        // Set property address
        rentalData.setPropertyAddress(buildPropertyAddress(property));
        
        // Get tenant info from customer table
        Customer tenant = getTenantForProperty(property.getId());
        rentalData.setTenantName(tenant != null ? tenant.getName() : "Vacant");
        rentalData.setStartDate(tenant != null ? tenant.getMoveInDate() : null);
        
        // Set rent amounts
        BigDecimal monthlyRent = property.getMonthlyPayment() != null ? property.getMonthlyPayment() : BigDecimal.ZERO;
        rentalData.setRentAmount(monthlyRent);
        rentalData.setRentDue(monthlyRent); // Assume full month due
        
        // Set fee percentages
        BigDecimal managementPercentage = property.getCommissionPercentage() != null ? property.getCommissionPercentage() : BigDecimal.ZERO;
        BigDecimal servicePercentage = getServiceFeePercentage(property); // You'll need to implement this
        
        rentalData.setManagementFeePercentage(managementPercentage);
        rentalData.setServiceFeePercentage(servicePercentage);
        
        // Calculate fee amounts
        rentalData.calculateManagementFeeAmount();
        rentalData.calculateServiceFeeAmount();
        rentalData.setNetAmount(rentalData.calculateNetAmount());
        
        // Set dates and outstanding
        rentalData.setNextDueDate(getNextDueDate(property, fromDate, toDate));
        rentalData.setPaymentDate(getPaymentDate(property, fromDate, toDate));
        rentalData.setOutstanding(calculateOutstanding(property, fromDate, toDate));
        rentalData.setNotes(getPropertyNotes(property));
        
        return rentalData;
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
        
        // Header section
        values.add(Arrays.asList("PROPSK LTD"));
        values.add(Arrays.asList("1 Poplar Court, Greensward Lane, Hockley, England, SS5 5JB"));
        values.add(Arrays.asList("Company number 15933011"));
        values.add(Arrays.asList(""));
        values.add(Arrays.asList("STATEMENT"));
        values.add(Arrays.asList(""));
        values.add(Arrays.asList("CLIENT:", data.getPropertyOwner().getName()));
        values.add(Arrays.asList("PROPERTY:", data.getPortfolioName()));
        values.add(Arrays.asList("PERIOD:", formatPeriod(data.getFromDate(), data.getToDate())));
        values.add(Arrays.asList(""));
        
        // Note about varying rates
        values.add(Arrays.asList("Note: Management and Service fees vary by property"));
        values.add(Arrays.asList(""));
        
        // Property rental table headers
        values.add(Arrays.asList("Property Address", "Tenant Name", "Start Date", "Monthly Rent", 
                           "Next Due", "Rent Due", "Mgmt Fee", "Mgmt %", "Service Fee", "Service %",
                           "Net Amount", "Payment Date", "Outstanding", "Notes"));
        
        // Add rental data for each property
        BigDecimal totalRent = BigDecimal.ZERO;
        BigDecimal totalManagementFees = BigDecimal.ZERO;
        BigDecimal totalServiceFees = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        
        for (PropertyRentalData rental : data.getRentalData()) {
            values.add(Arrays.asList(
                rental.getPropertyAddress(),
                rental.getTenantName(),
                rental.getStartDate() != null ? rental.getStartDate().toString() : "",
                "£" + rental.getRentAmount(),
                rental.getNextDueDate() != null ? rental.getNextDueDate().toString() : "",
                "£" + rental.getRentDue(),
                "£" + rental.getManagementFeeAmount(),
                rental.getManagementFeePercentage() + "%",
                "£" + rental.getServiceFeeAmount(),
                rental.getServiceFeePercentage() + "%",
                "£" + rental.getNetAmount(),
                rental.getPaymentDate() != null ? rental.getPaymentDate().toString() : "",
                "£" + rental.getOutstanding(),
                rental.getNotes() != null ? rental.getNotes() : ""
            ));
            
            // Accumulate totals
            totalRent = totalRent.add(rental.getRentAmount());
            totalManagementFees = totalManagementFees.add(rental.getManagementFeeAmount());
            totalServiceFees = totalServiceFees.add(rental.getServiceFeeAmount());
            totalNet = totalNet.add(rental.getNetAmount());
        }
        
        // Totals section
        values.add(Arrays.asList("TOTAL", "", "", 
                           "£" + totalRent, 
                           "", 
                           "£" + totalRent,
                           "£" + totalManagementFees,
                           "", // No single percentage for mixed portfolio
                           "£" + totalServiceFees,
                           "", // No single percentage for mixed portfolio
                           "£" + totalNet,
                           "", "", ""));
        
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

    // Helper methods - Implemented with real database queries
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
        
        // Fallback to property name if no address components
        if (address.length() == 0 && property.getPropertyName() != null) {
            return property.getPropertyName();
        }
        
        return address.toString();
    }

    private Customer getTenantForProperty(Long propertyId) {
        // Find tenant assigned to this property via assigned_property_id
        try {
            List<Customer> tenants = customerService.findByAssignedPropertyId(propertyId);
            if (!tenants.isEmpty()) {
                // Return the first active tenant
                return tenants.stream()
                    .filter(customer -> customer.getIsTenant() != null && customer.getIsTenant())
                    .filter(customer -> customer.getMoveOutDate() == null || customer.getMoveOutDate().isAfter(LocalDate.now()))
                    .findFirst()
                    .orElse(tenants.get(0));
            }
            
            // Alternative: Check by entity_type and entity_id
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

    private BigDecimal getServiceFeePercentage(Property property) {
        // For now, return 0 since you're using commission_percentage for management only
        // TODO: When you add service_fee_percentage column, return property.getServiceFeePercentage()
        return BigDecimal.ZERO;
    }

    private LocalDate getNextDueDate(Property property, LocalDate fromDate, LocalDate toDate) {
        // Calculate next rent due date (typically first of next month)
        LocalDate nextMonth = toDate.plusDays(1).withDayOfMonth(1);
        return nextMonth;
    }

    private LocalDate getPaymentDate(Property property, LocalDate fromDate, LocalDate toDate) {
        // Get most recent payment date from financial_transactions for this property
        try {
            String propertyPayPropId = property.getPayPropId();
            if (propertyPayPropId != null) {
                LocalDate latestPayment = financialTransactionRepository
                    .findLatestPaymentDateForProperty(propertyPayPropId, fromDate, toDate);
                if (latestPayment != null) {
                    return latestPayment;
                }
            }
            
            // Default to 25th of the end month if no payment data found
            return toDate.withDayOfMonth(Math.min(25, toDate.lengthOfMonth()));
        } catch (Exception e) {
            return toDate.withDayOfMonth(Math.min(25, toDate.lengthOfMonth()));
        }
    }

    private BigDecimal calculateOutstanding(Property property, LocalDate fromDate, LocalDate toDate) {
        // Calculate outstanding balance from financial_transactions
        try {
            String propertyPayPropId = property.getPayPropId();
            if (propertyPayPropId != null) {
                return financialTransactionRepository
                    .sumOutstandingForProperty(propertyPayPropId, fromDate, toDate);
            }
            
            return BigDecimal.ZERO;
        } catch (Exception e) {
            System.err.println("Error calculating outstanding for property " + property.getId() + ": " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private String getPropertyNotes(Property property) {
        StringBuilder notes = new StringBuilder();
        
        // Add property comment if exists
        if (property.getComment() != null && !property.getComment().trim().isEmpty()) {
            notes.append(property.getComment());
        }
        
        // Add payment method note if tenant exists
        Customer tenant = getTenantForProperty(property.getId());
        if (tenant != null && tenant.getPaymentMethod() != null) {
            if (notes.length() > 0) notes.append("; ");
            
            switch (tenant.getPaymentMethod().toString().toLowerCase()) {
                case "local":
                    if (tenant.getBankAccountNumber() != null) {
                        notes.append("Bank transfer configured");
                    }
                    break;
                case "international":
                    notes.append("International payment");
                    break;
                case "cheque":
                    notes.append("Cheque payment");
                    break;
            }
        }
        
        // Add PayProp sync note if applicable
        if (property.getPayPropId() != null) {
            if (notes.length() > 0) notes.append("; ");
            notes.append("Will be paid via the payprop system");
        }
        
        return notes.toString();
    }

    private String formatPeriod(LocalDate fromDate, LocalDate toDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy");
        return fromDate.format(formatter) + " to " + toDate.format(formatter);
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

    private BigDecimal calculatePropertyRent(Property property, LocalDate fromDate, LocalDate toDate) {
        // Calculate rent for the period (could be partial month)
        if (property.getMonthlyPayment() == null) {
            return BigDecimal.ZERO;
        }
        
        // For simplicity, return full monthly payment
        // TODO: Calculate pro-rated amount based on actual period
        return property.getMonthlyPayment();
    }

    private BigDecimal calculatePropertyExpenses(Property property, LocalDate fromDate, LocalDate toDate) {
        // Calculate expenses from financial_transactions for this property
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

    private BigDecimal calculateRentDue(Customer tenant, LocalDate fromDate, LocalDate toDate) {
        // Calculate total rent due for tenant in the period
        if (tenant.getMonthlyRent() != null) {
            return tenant.getMonthlyRent();
        }
        
        // Alternative: Get from assigned property
        if (tenant.getAssignedPropertyId() != null) {
            try {
                Property property = propertyService.getPropertyById(tenant.getAssignedPropertyId());
                if (property != null && property.getMonthlyPayment() != null) {
                    return property.getMonthlyPayment();
                }
            } catch (Exception e) {
                System.err.println("Error getting property for tenant " + tenant.getCustomerId() + ": " + e.getMessage());
            }
        }
        
        return BigDecimal.ZERO;
    }

    private BigDecimal calculatePaymentsMade(Customer tenant, LocalDate fromDate, LocalDate toDate) {
        // Calculate total payments made by tenant in the period
        try {
            String tenantPayPropId = tenant.getPayPropCustomerId();
            if (tenantPayPropId != null) {
                return financialTransactionRepository
                    .sumPaymentsByTenant(tenantPayPropId, fromDate, toDate);
            }
            
            return BigDecimal.ZERO;
        } catch (Exception e) {
            System.err.println("Error calculating payments for tenant " + tenant.getCustomerId() + ": " + e.getMessage());
            return BigDecimal.ZERO;
        }
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

    // Data classes with all necessary fields and methods
    private static class PropertyOwnerStatementData {
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

    private static class PropertyRentalData {
        private String propertyAddress;
        private String tenantName;
        private LocalDate startDate;
        private BigDecimal rentAmount;
        private LocalDate nextDueDate;
        private BigDecimal rentDue;
        private BigDecimal managementFeePercentage;
        private BigDecimal managementFeeAmount;
        private BigDecimal serviceFeePercentage;
        private BigDecimal serviceFeeAmount;
        private BigDecimal netAmount;
        private LocalDate paymentDate;
        private BigDecimal outstanding;
        private String notes;

        // Calculate net amount
        public BigDecimal calculateNetAmount() {
            BigDecimal mgmtFee = managementFeeAmount != null ? managementFeeAmount : BigDecimal.ZERO;
            BigDecimal serviceFee = serviceFeeAmount != null ? serviceFeeAmount : BigDecimal.ZERO;
            BigDecimal rent = rentAmount != null ? rentAmount : BigDecimal.ZERO;
            return rent.subtract(mgmtFee).subtract(serviceFee);
        }

        // Calculate management fee amount from percentage
        public void calculateManagementFeeAmount() {
            if (managementFeePercentage != null && rentAmount != null) {
                this.managementFeeAmount = rentAmount.multiply(managementFeePercentage)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            } else {
                this.managementFeeAmount = BigDecimal.ZERO;
            }
        }

        // Calculate service fee amount from percentage
        public void calculateServiceFeeAmount() {
            if (serviceFeePercentage != null && rentAmount != null) {
                this.serviceFeeAmount = rentAmount.multiply(serviceFeePercentage)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            } else {
                this.serviceFeeAmount = BigDecimal.ZERO;
            }
        }

        // Getters and setters
        public String getPropertyAddress() { return propertyAddress; }
        public void setPropertyAddress(String propertyAddress) { this.propertyAddress = propertyAddress; }

        public String getTenantName() { return tenantName; }
        public void setTenantName(String tenantName) { this.tenantName = tenantName; }

        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

        public BigDecimal getRentAmount() { return rentAmount; }
        public void setRentAmount(BigDecimal rentAmount) { this.rentAmount = rentAmount; }

        public LocalDate getNextDueDate() { return nextDueDate; }
        public void setNextDueDate(LocalDate nextDueDate) { this.nextDueDate = nextDueDate; }

        public BigDecimal getRentDue() { return rentDue; }
        public void setRentDue(BigDecimal rentDue) { this.rentDue = rentDue; }

        public BigDecimal getManagementFeePercentage() { return managementFeePercentage; }
        public void setManagementFeePercentage(BigDecimal managementFeePercentage) { this.managementFeePercentage = managementFeePercentage; }

        public BigDecimal getManagementFeeAmount() { return managementFeeAmount; }
        public void setManagementFeeAmount(BigDecimal managementFeeAmount) { this.managementFeeAmount = managementFeeAmount; }

        public BigDecimal getServiceFeePercentage() { return serviceFeePercentage; }
        public void setServiceFeePercentage(BigDecimal serviceFeePercentage) { this.serviceFeePercentage = serviceFeePercentage; }

        public BigDecimal getServiceFeeAmount() { return serviceFeeAmount; }
        public void setServiceFeeAmount(BigDecimal serviceFeeAmount) { this.serviceFeeAmount = serviceFeeAmount; }

        public BigDecimal getNetAmount() { return netAmount; }
        public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }

        public LocalDate getPaymentDate() { return paymentDate; }
        public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }

        public BigDecimal getOutstanding() { return outstanding; }
        public void setOutstanding(BigDecimal outstanding) { this.outstanding = outstanding; }

        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
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
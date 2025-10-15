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
import site.easy.to.build.crm.entity.CustomerType;
import site.easy.to.build.crm.entity.CustomerPropertyAssignment;
import site.easy.to.build.crm.entity.AssignmentType;
import site.easy.to.build.crm.entity.FinancialTransaction;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.repository.FinancialTransactionRepository;
import site.easy.to.build.crm.repository.CustomerPropertyAssignmentRepository;
import site.easy.to.build.crm.service.statements.BodenHouseStatementTemplateService;
import site.easy.to.build.crm.util.RentCyclePeriodCalculator;
import site.easy.to.build.crm.util.RentCyclePeriodCalculator.RentCyclePeriod;
import site.easy.to.build.crm.enums.StatementDataSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GoogleSheetsStatementService {

    private final CustomerService customerService;
    private final PropertyService propertyService;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final CustomerPropertyAssignmentRepository assignmentRepository;
    private final BodenHouseStatementTemplateService bodenHouseTemplateService;

    @Value("${GOOGLE_SERVICE_ACCOUNT_KEY:}")
    private String serviceAccountKey;

    // Shared Drive ID for CRM statements - service account has Manager access
    private static final String SHARED_DRIVE_ID = "0ADaFlidiFrFDUk9PVA";

    @Autowired
    public GoogleSheetsStatementService(CustomerService customerService,
                                      PropertyService propertyService,
                                      FinancialTransactionRepository financialTransactionRepository,
                                      CustomerPropertyAssignmentRepository assignmentRepository,
                                      BodenHouseStatementTemplateService bodenHouseTemplateService) {
        this.customerService = customerService;
        this.propertyService = propertyService;
        this.financialTransactionRepository = financialTransactionRepository;
        this.assignmentRepository = assignmentRepository;
        this.bodenHouseTemplateService = bodenHouseTemplateService;
    }

    /**
     * Creates a property owner statement in Google Sheets with enhanced formatting and Apps Script integration
     * Now supports both OAuth2 and service account (shared drive) approaches
     */
    public String createPropertyOwnerStatement(OAuthUser oAuthUser, Customer propertyOwner,
                                             LocalDate fromDate, LocalDate toDate)
            throws IOException, GeneralSecurityException {

        System.out.println("📊 Creating property owner statement for: " + propertyOwner.getName());
        System.out.println("📊 OAuth user available: " + (oAuthUser != null));
        System.out.println("📊 Service account available: " + (serviceAccountKey != null && !serviceAccountKey.trim().isEmpty()));

        String spreadsheetId;
        Sheets sheetsService;

        // Try shared drive approach first (preferred), fallback to OAuth2
        if (serviceAccountKey != null && !serviceAccountKey.trim().isEmpty()) {
            System.out.println("📊 Using shared drive approach with service account");
            spreadsheetId = createSpreadsheetInSharedDrive(propertyOwner, fromDate, toDate);
            sheetsService = createServiceAccountSheetsService();
        } else if (oAuthUser != null) {
            System.out.println("📊 Using OAuth2 approach (fallback)");
            sheetsService = getSheetsService(oAuthUser);

            // Create new spreadsheet with enhanced properties
            Spreadsheet spreadsheet = new Spreadsheet()
                .setProperties(new SpreadsheetProperties()
                    .setTitle(generateStatementTitle(propertyOwner, fromDate, toDate))
                    .setLocale("en_GB")  // UK locale for proper currency formatting
                    .setTimeZone("Europe/London"));

            Spreadsheet createdSheet = sheetsService.spreadsheets().create(spreadsheet).execute();
            spreadsheetId = createdSheet.getSpreadsheetId();
        } else {
            throw new IllegalStateException("Neither service account nor OAuth2 user available for statement creation");
        }
        
        // Build statement data using new Boden House template
        List<List<Object>> values;

        // Determine if this is a property owner or portfolio statement
        if (isPortfolioStatement(propertyOwner)) {
            System.out.println("📊 Using Boden House template for portfolio statement");
            values = bodenHouseTemplateService.generatePortfolioStatement(propertyOwner, fromDate, toDate);
        } else {
            System.out.println("🏢 Using Boden House template for property owner statement");
            values = bodenHouseTemplateService.generatePropertyOwnerStatement(propertyOwner, fromDate, toDate);
        }
        
        // Write data to sheet with USER_ENTERED to enable formulas
        ValueRange body = new ValueRange().setValues(values);
        sheetsService.spreadsheets().values()
            .update(spreadsheetId, "A1", body)
            .setValueInputOption("USER_ENTERED") // This enables formulas!
            .execute();
        
        // Apply enhanced formatting with currency, colors, and borders
        applyBodenHouseGoogleSheetsFormatting(sheetsService, spreadsheetId);
        
        // Add Apps Script for dynamic calculations and interactions
        addAppsScriptEnhancements(sheetsService, spreadsheetId);

        return spreadsheetId;
    }

    /**
     * Creates a property owner statement with monthly period breakdown.
     * Each rent cycle period gets its own sheet, plus a summary sheet.
     *
     * @param oAuthUser OAuth user for authentication
     * @param propertyOwner Property owner
     * @param fromDate Start date of overall range
     * @param toDate End date of overall range
     * @param includedDataSources Optional set of data sources to filter by
     * @return Spreadsheet ID
     */
    public String createMonthlyPropertyOwnerStatement(OAuthUser oAuthUser, Customer propertyOwner,
                                                     LocalDate fromDate, LocalDate toDate,
                                                     Set<StatementDataSource> includedDataSources)
            throws IOException, GeneralSecurityException {

        System.out.println("📊 Creating monthly breakdown statement for: " + propertyOwner.getName());
        System.out.println("📊 Period: " + fromDate + " to " + toDate);

        // Calculate rent cycle periods
        List<RentCyclePeriod> periods = RentCyclePeriodCalculator.calculateMonthlyPeriods(fromDate, toDate);
        System.out.println("📊 Periods calculated: " + periods.size());

        String spreadsheetId;
        Sheets sheetsService;

        // Create spreadsheet using service account or OAuth2
        if (serviceAccountKey != null && !serviceAccountKey.trim().isEmpty()) {
            System.out.println("📊 Using shared drive approach with service account");
            spreadsheetId = createMultiPeriodSpreadsheetInSharedDrive(propertyOwner, fromDate, toDate, periods.size());
            sheetsService = createServiceAccountSheetsService();
        } else if (oAuthUser != null) {
            System.out.println("📊 Using OAuth2 approach (fallback)");
            sheetsService = getSheetsService(oAuthUser);

            // Create new spreadsheet
            Spreadsheet spreadsheet = new Spreadsheet()
                .setProperties(new SpreadsheetProperties()
                    .setTitle(generateMonthlyStatementTitle(propertyOwner, fromDate, toDate))
                    .setLocale("en_GB")
                    .setTimeZone("Europe/London"));

            Spreadsheet createdSheet = sheetsService.spreadsheets().create(spreadsheet).execute();
            spreadsheetId = createdSheet.getSpreadsheetId();
        } else {
            throw new IllegalStateException("Neither service account nor OAuth2 user available");
        }

        // Get the first sheet to rename it
        int sheetId = 0;

        // Generate statement for each period
        for (int i = 0; i < periods.size(); i++) {
            RentCyclePeriod period = periods.get(i);
            String sheetName = period.getSheetName();

            System.out.println("📊 Generating sheet: " + sheetName);

            // Generate statement data for this period
            List<List<Object>> values;
            if (includedDataSources != null && !includedDataSources.isEmpty()) {
                values = bodenHouseTemplateService.generatePropertyOwnerStatement(
                    propertyOwner, period.getStartDate(), period.getEndDate(), includedDataSources);
            } else {
                values = bodenHouseTemplateService.generatePropertyOwnerStatement(
                    propertyOwner, period.getStartDate(), period.getEndDate());
            }

            if (i == 0) {
                // Rename the first (default) sheet
                renameSheet(sheetsService, spreadsheetId, sheetId, sheetName);
            } else {
                // Add a new sheet for subsequent periods
                AddSheetRequest addSheetRequest = new AddSheetRequest()
                    .setProperties(new SheetProperties().setTitle(sheetName));

                Request request = new Request().setAddSheet(addSheetRequest);
                BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
                    .setRequests(List.of(request));

                sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute();
            }

            // Write data to this sheet
            ValueRange body = new ValueRange().setValues(values);
            sheetsService.spreadsheets().values()
                .update(spreadsheetId, sheetName + "!A1", body)
                .setValueInputOption("USER_ENTERED") // Enable formulas
                .execute();

            // Apply formatting
            applyBodenHouseGoogleSheetsFormattingToSheet(sheetsService, spreadsheetId, sheetName);
        }

        // Add Transaction Ledger sheet (lease-centric view)
        createTransactionLedgerSheet(sheetsService, spreadsheetId, propertyOwner, fromDate, toDate, includedDataSources);

        // Add summary sheet
        createPeriodSummarySheet(sheetsService, spreadsheetId, propertyOwner, periods);

        // Grant access to property owner
        grantAccessToPropertyOwner(spreadsheetId, propertyOwner);

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

        // Set property details including unit number
        rentalData.setPropertyAddress(buildPropertyAddress(property));
        rentalData.setUnitNumber(extractUnitNumber(property)); // Extract unit/flat number
        rentalData.setProperty(property); // Add property reference for later use

        // Get active tenant assignment for this property
        List<CustomerPropertyAssignment> activeTenants = assignmentRepository
            .findActiveAssignmentsByPropertyAndType(property.getId(), AssignmentType.TENANT, LocalDate.now());

        if (!activeTenants.isEmpty()) {
            CustomerPropertyAssignment assignment = activeTenants.get(0); // Most recent
            Customer tenant = assignment.getCustomer();

            rentalData.setTenantName(tenant != null ? tenant.getName() : "Vacant");
            // Use assignment start_date instead of deprecated customer.moveInDate
            rentalData.setStartDate(assignment.getStartDate());
        } else {
            rentalData.setTenantName("Vacant");
            rentalData.setStartDate(null);
        }

        // Set rent amounts - use actual PayProp data
        BigDecimal monthlyRent = property.getMonthlyPayment() != null ? property.getMonthlyPayment() : BigDecimal.ZERO;
        rentalData.setRentAmount(monthlyRent);
        rentalData.setRentDue(monthlyRent);

        // Set fee percentages (default to 10% management, 5% service)
        BigDecimal managementPercentage = property.getCommissionPercentage() != null ?
            property.getCommissionPercentage() : new BigDecimal("10.0");
        BigDecimal servicePercentage = new BigDecimal("5.0"); // Standard service fee

        rentalData.setManagementFeePercentage(managementPercentage);
        rentalData.setServiceFeePercentage(servicePercentage);

        return rentalData;
    }
    
    private String extractUnitNumber(Property property) {
        // Extract unit/flat number from property name or address
        String name = property.getPropertyName();
        if (name != null) {
            // Look for patterns like "Flat 1", "Unit 12", etc.
            if (name.toLowerCase().contains("flat")) {
                return name; // Return full flat descriptor
            }
            if (name.toLowerCase().contains("unit")) {
                return name;
            }
        }
        
        // If no unit found in name, try address
        String address = property.getFullAddress();
        if (address != null) {
            if (address.toLowerCase().contains("flat")) {
                // Extract flat part
                String[] parts = address.split(",");
                for (String part : parts) {
                    if (part.trim().toLowerCase().contains("flat")) {
                        return part.trim();
                    }
                }
            }
        }
        
        // Default fallback
        return property.getPropertyName() != null ? property.getPropertyName() : "Property " + property.getId();
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

    
    // Helper methods for the new statement format
    
    private String getOrdinalSuffix(int day) {
        if (day >= 11 && day <= 13) {
            return "th";
        }
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
        // Look for reconciliation date or batch payment date
        return transactions.stream()
            .filter(t -> t.getReconciliationDate() != null)
            .findFirst()
            .map(t -> t.getReconciliationDate().format(DateTimeFormatter.ofPattern("d/M/yyyy")))
            .orElse("");
    }
    
    private String getPaymentBatchInfo(List<FinancialTransaction> transactions) {
        // Return payment batch ID or reference
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
        // For simple amounts without commas (like individual fees)
        return amount.toString();
    }
    
    private String formatCurrencyWithCommas(BigDecimal amount) {
        if (amount == null) return "0";
        // For larger amounts that need comma formatting (like totals)
        java.text.NumberFormat formatter = java.text.NumberFormat.getNumberInstance();
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);
        return formatter.format(amount);
    }
    
    private List<FinancialTransaction> getExpenseTransactions(List<Property> properties, LocalDate fromDate, LocalDate toDate) {
        List<FinancialTransaction> allExpenses = new ArrayList<>();
        
        for (Property property : properties) {
            if (property.getPayPropId() != null) {
                List<FinancialTransaction> propertyExpenses = financialTransactionRepository
                    .findByPropertyAndDateRange(property.getPayPropId(), fromDate, toDate)
                    .stream()
                    .filter(t -> isExpenseTransaction(t))
                    .collect(Collectors.toList());
                allExpenses.addAll(propertyExpenses);
            }
        }
        
        return allExpenses;
    }
    
    private boolean isExpenseTransaction(FinancialTransaction transaction) {
        String type = transaction.getTransactionType();
        String category = transaction.getCategoryName();
        
        // Use the existing PayProp transaction types that represent expenses
        if (type != null) {
            // These are the exact types from PayPropFinancialSyncService
            return type.equals("payment_to_contractor") ||
                   type.equals("payment_to_beneficiary") ||
                   type.equals("payment_to_agency") ||
                   type.equals("payment_property_account") ||
                   type.equals("payment_deposit_account") ||
                   type.equals("debit_note") ||
                   type.equals("adjustment") ||
                   type.equals("refund");
        }
        
        // Also check category names for maintenance/repair expenses
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
        // Try to find the property by PayProp ID and extract unit number
        try {
            // Look up property in database by PayProp ID
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

    // Data classes for the statement
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
        private BigDecimal managementFeeAmount;
        private BigDecimal serviceFeeAmount;
        private BigDecimal netAmount;
        private LocalDate nextDueDate;
        private LocalDate paymentDate;
        private BigDecimal outstanding;
        private String notes;
        
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
        
        public BigDecimal getManagementFeeAmount() { return managementFeeAmount; }
        public void setManagementFeeAmount(BigDecimal managementFeeAmount) { this.managementFeeAmount = managementFeeAmount; }
        
        public BigDecimal getServiceFeeAmount() { return serviceFeeAmount; }
        public void setServiceFeeAmount(BigDecimal serviceFeeAmount) { this.serviceFeeAmount = serviceFeeAmount; }
        
        public BigDecimal getNetAmount() { return netAmount; }
        public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }
        
        public LocalDate getNextDueDate() { return nextDueDate; }
        public void setNextDueDate(LocalDate nextDueDate) { this.nextDueDate = nextDueDate; }
        
        public LocalDate getPaymentDate() { return paymentDate; }
        public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }
        
        public BigDecimal getOutstanding() { return outstanding; }
        public void setOutstanding(BigDecimal outstanding) { this.outstanding = outstanding; }
        
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
        
        // Helper methods
        public void calculateManagementFeeAmount() {
            if (rentAmount != null && managementFeePercentage != null) {
                this.managementFeeAmount = rentAmount.multiply(managementFeePercentage.divide(new BigDecimal("100")));
            }
        }
        
        public void calculateServiceFeeAmount() {
            if (rentAmount != null && serviceFeePercentage != null) {
                this.serviceFeeAmount = rentAmount.multiply(serviceFeePercentage.divide(new BigDecimal("100")));
            }
        }
        
        public BigDecimal calculateNetAmount() {
            if (rentAmount != null && managementFeeAmount != null && serviceFeeAmount != null) {
                return rentAmount.subtract(managementFeeAmount).subtract(serviceFeeAmount);
            }
            return BigDecimal.ZERO;
        }
    }
    
    // Helper data classes for other statement types
    public static class TenantStatementData {
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
    
    public static class PortfolioStatementData {
        private Customer propertyOwner;
        private LocalDate fromDate;
        private LocalDate toDate;
        private List<Property> properties;
        private List<PropertySummary> propertySummaries;
        
        // Getters and setters
        public Customer getPropertyOwner() { return propertyOwner; }
        public void setPropertyOwner(Customer propertyOwner) { this.propertyOwner = propertyOwner; }
        
        public LocalDate getFromDate() { return fromDate; }
        public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }
        
        public LocalDate getToDate() { return toDate; }
        public void setToDate(LocalDate toDate) { this.toDate = toDate; }
        
        public List<Property> getProperties() { return properties; }
        public void setProperties(List<Property> properties) { this.properties = properties; }
        
        public List<PropertySummary> getPropertySummaries() { return propertySummaries; }
        public void setPropertySummaries(List<PropertySummary> propertySummaries) { this.propertySummaries = propertySummaries; }
    }
    
    public static class PropertySummary {
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

    
    private void applyPropertyOwnerStatementFormatting(Sheets sheetsService, String spreadsheetId) 
            throws IOException {
        // Fallback to basic formatting if enhanced method fails
        List<Request> requests = new ArrayList<>();
        
        requests.add(new Request().setRepeatCell(new RepeatCellRequest()
            .setRange(new GridRange().setSheetId(0).setStartRowIndex(0).setEndRowIndex(1))
            .setCell(new CellData().setUserEnteredFormat(new CellFormat()
                .setTextFormat(new TextFormat().setBold(true))))
            .setFields("userEnteredFormat.textFormat.bold")));
        
        BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
            .setRequests(requests);
        sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute();
    }

    private void applyTenantStatementFormatting(Sheets sheetsService, String spreadsheetId) 
            throws IOException {
        // Similar formatting as property owner statement
        applyBodenHouseGoogleSheetsFormatting(sheetsService, spreadsheetId);
    }

    private void applyPortfolioStatementFormatting(Sheets sheetsService, String spreadsheetId) 
            throws IOException {
        // Similar formatting as property owner statement
        applyBodenHouseGoogleSheetsFormatting(sheetsService, spreadsheetId);
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
        // Find active tenant using CustomerPropertyAssignment (NEW APPROACH)
        try {
            List<CustomerPropertyAssignment> activeTenants = assignmentRepository
                .findActiveAssignmentsByPropertyAndType(propertyId, AssignmentType.TENANT, LocalDate.now());

            if (!activeTenants.isEmpty()) {
                // Return the most recent active tenant
                return activeTenants.get(0).getCustomer();
            }

            return null;

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

    /**
     * Generate title for monthly breakdown statements
     */
    private String generateMonthlyStatementTitle(Customer customer, LocalDate fromDate, LocalDate toDate) {
        String customerName = customer.getName().replaceAll("[^a-zA-Z0-9]", "_");
        return String.format("Owner_Statement_Customer___%s_%s_%s",
            customerName.toLowerCase(),
            fromDate.format(DateTimeFormatter.ofPattern("yyyy-MM")),
            toDate.format(DateTimeFormatter.ofPattern("yyyy-MM")));
    }

    /**
     * Rename a sheet in the spreadsheet
     */
    private void renameSheet(Sheets sheetsService, String spreadsheetId, int sheetId, String newName) throws IOException {
        UpdateSheetPropertiesRequest updateRequest = new UpdateSheetPropertiesRequest()
            .setProperties(new SheetProperties().setSheetId(sheetId).setTitle(newName))
            .setFields("title");

        Request request = new Request().setUpdateSheetProperties(updateRequest);
        BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
            .setRequests(List.of(request));

        sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute();
    }

    /**
     * Apply formatting to a specific sheet
     */
    private void applyBodenHouseGoogleSheetsFormattingToSheet(Sheets sheetsService, String spreadsheetId, String sheetName)
            throws IOException {
        applyBodenHouseGoogleSheetsFormatting(sheetsService, spreadsheetId);
    }

    /**
     * Create a spreadsheet in shared drive for multi-period statements
     */
    private String createMultiPeriodSpreadsheetInSharedDrive(Customer propertyOwner, LocalDate fromDate, LocalDate toDate, int periodCount)
            throws IOException, GeneralSecurityException {
        return createSpreadsheetInSharedDrive(propertyOwner, fromDate, toDate);
    }

    /**
     * Grant access to property owner
     */
    private void grantAccessToPropertyOwner(String spreadsheetId, Customer propertyOwner) throws IOException {
        String customerEmail = propertyOwner.getEmail();
        if (customerEmail == null || customerEmail.isEmpty()) {
            System.err.println("⚠️ Property owner has no email");
            return;
        }

        try {
            String formattedKey = getFormattedServiceAccountKey();
            GoogleCredential driveCredential = GoogleCredential
                .fromStream(new java.io.ByteArrayInputStream(formattedKey.getBytes()))
                .createScoped(Arrays.asList(DriveScopes.DRIVE_FILE));

            Drive driveService = new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                driveCredential)
                .setApplicationName("CRM Property Management")
                .build();

            com.google.api.services.drive.model.Permission customerPermission =
                new com.google.api.services.drive.model.Permission();
            customerPermission.setRole("reader");
            customerPermission.setType("user");
            customerPermission.setEmailAddress(customerEmail);

            driveService.permissions().create(spreadsheetId, customerPermission)
                .setSupportsAllDrives(true)
                .setSendNotificationEmail(false)
                .execute();

            System.out.println("✅ Access granted to " + customerEmail);
        } catch (Exception e) {
            System.err.println("⚠️ Could not grant access: " + e.getMessage());
        }
    }

    /**
     * Create Transaction Ledger sheet - lease-centric view of all transactions
     */
    private void createTransactionLedgerSheet(Sheets sheetsService, String spreadsheetId, Customer propertyOwner,
                                             LocalDate fromDate, LocalDate toDate, Set<StatementDataSource> includedDataSources)
            throws IOException {

        System.out.println("📊 Creating Transaction Ledger sheet");

        // Create the sheet
        AddSheetRequest addSheetRequest = new AddSheetRequest()
            .setProperties(new SheetProperties()
                .setTitle("Transaction Ledger")
                .setIndex(0)); // Put at the beginning

        Request request = new Request().setAddSheet(addSheetRequest);
        BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
            .setRequests(List.of(request));

        sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute();

        // Generate transaction ledger data
        List<List<Object>> ledgerValues;
        if (includedDataSources != null && !includedDataSources.isEmpty()) {
            ledgerValues = bodenHouseTemplateService.generateTransactionLedger(
                propertyOwner, fromDate, toDate, includedDataSources);
        } else {
            ledgerValues = bodenHouseTemplateService.generateTransactionLedger(
                propertyOwner, fromDate, toDate);
        }

        // Write data to the sheet
        ValueRange body = new ValueRange().setValues(ledgerValues);
        sheetsService.spreadsheets().values()
            .update(spreadsheetId, "Transaction Ledger!A1", body)
            .setValueInputOption("USER_ENTERED")
            .execute();

        // Apply formatting to the Transaction Ledger sheet
        applyTransactionLedgerFormatting(sheetsService, spreadsheetId);

        System.out.println("✅ Transaction Ledger created with " + (ledgerValues.size() - 1) + " transactions");
    }

    /**
     * Apply formatting to Transaction Ledger sheet
     */
    private void applyTransactionLedgerFormatting(Sheets sheetsService, String spreadsheetId) throws IOException {
        List<Request> requests = new ArrayList<>();

        // Get sheet ID for Transaction Ledger
        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
        Integer sheetId = null;
        for (Sheet sheet : spreadsheet.getSheets()) {
            if ("Transaction Ledger".equals(sheet.getProperties().getTitle())) {
                sheetId = sheet.getProperties().getSheetId();
                break;
            }
        }

        if (sheetId == null) return;

        // Format header row
        requests.add(new Request()
            .setRepeatCell(new RepeatCellRequest()
                .setRange(new GridRange()
                    .setSheetId(sheetId)
                    .setStartRowIndex(0)
                    .setEndRowIndex(1)
                    .setStartColumnIndex(0)
                    .setEndColumnIndex(17))
                .setCell(new CellData()
                    .setUserEnteredFormat(new CellFormat()
                        .setTextFormat(new TextFormat()
                            .setBold(true)
                            .setFontSize(10)
                            .setFontFamily("Calibri"))
                        .setHorizontalAlignment("CENTER")
                        .setVerticalAlignment("MIDDLE")
                        .setWrapStrategy("WRAP")
                        .setBackgroundColor(new Color()
                            .setRed(0.2f)
                            .setGreen(0.4f)
                            .setBlue(0.6f)
                            .setAlpha(1.0f))
                        .setTextFormat(new TextFormat()
                            .setBold(true)
                            .setForegroundColor(new Color()
                                .setRed(1.0f)
                                .setGreen(1.0f)
                                .setBlue(1.0f)))))
                .setFields("userEnteredFormat")));

        // Format currency columns (Amount, Running Balance, Commission Amount, Service Fee Amount, Net to Owner)
        int[] currencyColumns = {7, 10, 12, 14, 15};
        for (int col : currencyColumns) {
            requests.add(new Request()
                .setRepeatCell(new RepeatCellRequest()
                    .setRange(new GridRange()
                        .setSheetId(sheetId)
                        .setStartRowIndex(1)
                        .setEndRowIndex(1000)
                        .setStartColumnIndex(col)
                        .setEndColumnIndex(col + 1))
                    .setCell(new CellData()
                        .setUserEnteredFormat(new CellFormat()
                            .setNumberFormat(new com.google.api.services.sheets.v4.model.NumberFormat()
                                .setType("CURRENCY")
                                .setPattern("£#,##0.00;(£#,##0.00)"))
                            .setHorizontalAlignment("RIGHT")))
                    .setFields("userEnteredFormat(numberFormat,horizontalAlignment)")));
        }

        // Format percentage columns (Commission Rate, Service Fee Rate)
        int[] percentageColumns = {11, 13};
        for (int col : percentageColumns) {
            requests.add(new Request()
                .setRepeatCell(new RepeatCellRequest()
                    .setRange(new GridRange()
                        .setSheetId(sheetId)
                        .setStartRowIndex(1)
                        .setEndRowIndex(1000)
                        .setStartColumnIndex(col)
                        .setEndColumnIndex(col + 1))
                    .setCell(new CellData()
                        .setUserEnteredFormat(new CellFormat()
                            .setNumberFormat(new com.google.api.services.sheets.v4.model.NumberFormat()
                                .setType("PERCENT")
                                .setPattern("0.00%"))
                            .setHorizontalAlignment("CENTER")))
                    .setFields("userEnteredFormat")));
        }

        // Freeze header row
        requests.add(new Request()
            .setUpdateSheetProperties(new UpdateSheetPropertiesRequest()
                .setProperties(new SheetProperties()
                    .setSheetId(sheetId)
                    .setGridProperties(new GridProperties()
                        .setFrozenRowCount(1)))
                .setFields("gridProperties.frozenRowCount")));

        // Set column widths
        Map<Integer, Integer> columnWidths = Map.of(
            0, 100,  // Date
            1, 150,  // Lease Reference
            2, 150,  // Property/Block
            3, 120,  // Unit Number
            4, 150,  // Tenant Name
            5, 120,  // Transaction Type
            6, 100,  // Category
            7, 100,  // Amount
            8, 120,  // Account Source
            9, 250   // Description
        );

        for (Map.Entry<Integer, Integer> entry : columnWidths.entrySet()) {
            requests.add(new Request()
                .setUpdateDimensionProperties(new UpdateDimensionPropertiesRequest()
                    .setRange(new DimensionRange()
                        .setSheetId(sheetId)
                        .setDimension("COLUMNS")
                        .setStartIndex(entry.getKey())
                        .setEndIndex(entry.getKey() + 1))
                    .setProperties(new DimensionProperties()
                        .setPixelSize(entry.getValue()))
                    .setFields("pixelSize")));
        }

        // Execute all formatting requests
        if (!requests.isEmpty()) {
            BatchUpdateSpreadsheetRequest batchUpdateRequest = new BatchUpdateSpreadsheetRequest()
                .setRequests(requests);

            sheetsService.spreadsheets()
                .batchUpdate(spreadsheetId, batchUpdateRequest)
                .execute();

            System.out.println("✅ Applied Transaction Ledger formatting");
        }
    }

    /**
     * Create a summary sheet
     */
    private void createPeriodSummarySheet(Sheets sheetsService, String spreadsheetId, Customer propertyOwner, List<RentCyclePeriod> periods)
            throws IOException {

        System.out.println("📊 Creating Period Summary sheet");

        AddSheetRequest addSheetRequest = new AddSheetRequest()
            .setProperties(new SheetProperties()
                .setTitle("Period Summary")
                .setIndex(0));

        Request request = new Request().setAddSheet(addSheetRequest);
        BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
            .setRequests(List.of(request));

        sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute();

        List<List<Object>> summaryValues = new ArrayList<>();
        summaryValues.add(Arrays.asList("PROPSK LTD"));
        summaryValues.add(Arrays.asList("PERIOD SUMMARY"));
        summaryValues.add(Arrays.asList(""));
        summaryValues.add(Arrays.asList("CLIENT:", propertyOwner.getName()));
        summaryValues.add(Arrays.asList("PERIODS:", periods.size()));
        summaryValues.add(Arrays.asList(""));
        summaryValues.add(Arrays.asList("Period", "Start Date", "End Date", "Total Rent", "Total Fees", "Net Due"));

        for (RentCyclePeriod period : periods) {
            summaryValues.add(Arrays.asList(
                period.getDisplayName(),
                period.getStartDate().toString(),
                period.getEndDate().toString(),
                "", "", "" // Formulas would go here
            ));
        }

        ValueRange body = new ValueRange().setValues(summaryValues);
        sheetsService.spreadsheets().values()
            .update(spreadsheetId, "Period Summary!A1", body)
            .setValueInputOption("USER_ENTERED")
            .execute();

        System.out.println("✅ Period Summary created");
    }

    /**
     * Adds Google Apps Script enhancements for dynamic calculations and interactions
     */
    private void addAppsScriptEnhancements(Sheets sheetsService, String spreadsheetId) {
        try {
            // Apps Script code for enhanced functionality
            String appsScriptCode = buildAppsScriptCode();
            
            // Note: To add Apps Script, we would need to use the Apps Script API
            // For now, we'll add comments and notes that suggest using Apps Script
            
            // Add a note to the spreadsheet about Apps Script enhancements
            List<List<Object>> notesValues = new ArrayList<>();
            notesValues.add(Arrays.asList("", "", "", "", "", "", "", "", "", "", "", 
                "📝 ENHANCEMENTS AVAILABLE:", ""));
            notesValues.add(Arrays.asList("", "", "", "", "", "", "", "", "", "", "", 
                "• Automatic calculations", ""));
            notesValues.add(Arrays.asList("", "", "", "", "", "", "", "", "", "", "", 
                "• Data validation", ""));
            notesValues.add(Arrays.asList("", "", "", "", "", "", "", "", "", "", "", 
                "• Interactive features", ""));
            notesValues.add(Arrays.asList("", "", "", "", "", "", "", "", "", "", "", 
                "• Email alerts", ""));
            
            // Add these notes at the bottom of the sheet
            ValueRange notesBody = new ValueRange().setValues(notesValues);
            sheetsService.spreadsheets().values()
                .append(spreadsheetId, "A100", notesBody)
                .setValueInputOption("RAW")
                .execute();
                
        } catch (Exception e) {
            System.err.println("Warning: Could not add Apps Script enhancements: " + e.getMessage());
        }
    }
    
    /**
     * Builds the Google Apps Script code for enhanced functionality
     */
    private String buildAppsScriptCode() {
        return """
            // Google Apps Script enhancements for Property Management Statement
            
            function onEdit(e) {
              // Automatically recalculate when data changes
              if (e.range.getColumn() >= 4 && e.range.getColumn() <= 11) {
                recalculateFormulas();
              }
            }
            
            function recalculateFormulas() {
              var sheet = SpreadsheetApp.getActiveSheet();
              // Force recalculation of all formulas
              SpreadsheetApp.flush();
            }
            
            function validateData() {
              var sheet = SpreadsheetApp.getActiveSheet();
              var dataRange = sheet.getDataRange();
              var values = dataRange.getValues();
              
              // Check for negative outstanding amounts
              for (var i = 13; i < values.length; i++) {
                if (values[i][10] < 0) { // Outstanding column
                  sheet.getRange(i+1, 11).setBackground('#ffcccc'); // Highlight in red
                }
              }
            }
            
            function sendEmailAlert() {
              // Send email alerts for overdue payments
              var sheet = SpreadsheetApp.getActiveSheet();
              // Implementation for email notifications
            }
            
            function formatCurrency() {
              var sheet = SpreadsheetApp.getActiveSheet();
              var currencyColumns = [4, 6, 7, 8, 9, 11]; // D, F, G, H, I, K
              
              currencyColumns.forEach(function(col) {
                sheet.getRange(14, col, sheet.getLastRow()-13, 1)
                     .setNumberFormat('£#,##0.00');
              });
            }
            """;
    }

    /**
     * Safely get the first name from a customer, handling null names gracefully
     */
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

    /**
     * Creates a spreadsheet in the shared drive using service account
     */
    private String createSpreadsheetInSharedDrive(Customer propertyOwner, LocalDate fromDate, LocalDate toDate)
            throws IOException, GeneralSecurityException {

        String title = generateStatementTitle(propertyOwner, fromDate, toDate);
        System.out.println("📊 Creating spreadsheet in shared drive: " + title);

        // Create Drive service for shared drive operations
        String formattedKey = getFormattedServiceAccountKey();
        GoogleCredential driveCredential = GoogleCredential
            .fromStream(new java.io.ByteArrayInputStream(formattedKey.getBytes()))
            .createScoped(Collections.singleton(DriveScopes.DRIVE));

        Drive driveService = new Drive.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            driveCredential)
            .setApplicationName("CRM Property Management")
            .build();

        // Create spreadsheet file in shared drive
        com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
        fileMetadata.setName(title);
        fileMetadata.setMimeType("application/vnd.google-apps.spreadsheet");
        fileMetadata.setParents(Collections.singletonList(SHARED_DRIVE_ID));

        com.google.api.services.drive.model.File file = driveService.files()
            .create(fileMetadata)
            .setSupportsAllDrives(true)  // Required for shared drives
            .execute();

        String spreadsheetId = file.getId();
        System.out.println("✅ Spreadsheet created in shared drive! ID: " + spreadsheetId);

        // Give the property owner access to their statement
        String customerEmail = propertyOwner.getEmail();
        if (customerEmail != null && !customerEmail.isEmpty()) {
            try {
                com.google.api.services.drive.model.Permission customerPermission = new com.google.api.services.drive.model.Permission();
                customerPermission.setRole("reader"); // Can view but not edit
                customerPermission.setType("user");
                customerPermission.setEmailAddress(customerEmail);

                driveService.permissions().create(spreadsheetId, customerPermission)
                    .setSupportsAllDrives(true)  // Required for shared drives
                    .setSendNotificationEmail(false)  // Don't notify customer by email
                    .execute();

                System.out.println("✅ Property owner (" + customerEmail + ") granted access to their statement");
            } catch (Exception customerAccessError) {
                System.err.println("⚠️ Could not grant access to customer: " + customerAccessError.getMessage());
            }
        }

        return spreadsheetId;
    }

    /**
     * Creates a Sheets service using service account credentials
     */
    private Sheets createServiceAccountSheetsService() throws IOException, GeneralSecurityException {
        String formattedKey = getFormattedServiceAccountKey();
        GoogleCredential credential = GoogleCredential
            .fromStream(new java.io.ByteArrayInputStream(formattedKey.getBytes()))
            .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS));

        return new Sheets.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            credential)
            .setApplicationName("CRM Property Management")
            .build();
    }

    /**
     * Get properly formatted service account key (handle escaped newlines)
     */
    private String getFormattedServiceAccountKey() {
        if (serviceAccountKey == null || serviceAccountKey.trim().isEmpty()) {
            throw new IllegalStateException("Service account key is not configured");
        }

        // If the key contains escaped newlines, replace them with actual newlines
        if (serviceAccountKey.contains("\\n")) {
            System.out.println("🔧 Converting escaped newlines in service account key");
            return serviceAccountKey.replace("\\n", "\n");
        }

        return serviceAccountKey;
    }

    /**
     * Determine if this should be treated as a portfolio statement
     */
    private boolean isPortfolioStatement(Customer propertyOwner) {
        // Get properties for this owner
        List<Property> properties = propertyService.getPropertiesByOwner(propertyOwner.getCustomerId());

        // If owner has multiple properties or properties in different buildings, treat as portfolio
        if (properties.size() > 5) {
            return true;
        }

        // Check if properties are in different buildings
        Set<String> buildings = properties.stream()
            .map(this::extractBuildingName)
            .collect(java.util.stream.Collectors.toSet());

        return buildings.size() > 1;
    }

    private String extractBuildingName(Property property) {
        if (property.getPropertyName() == null) return "UNKNOWN";

        String name = property.getPropertyName().toUpperCase();
        if (name.contains("BODEN HOUSE") || name.contains("WEST GATE")) {
            return "BODEN HOUSE";
        } else if (name.contains("KNIGHTON HAYES")) {
            return "KNIGHTON HAYES";
        }
        return property.getPropertyName().toUpperCase();
    }

    /**
     * Apply Boden House specific formatting to Google Sheets
     */
    private void applyBodenHouseGoogleSheetsFormatting(Sheets sheetsService, String spreadsheetId)
            throws IOException {

        List<Request> requests = new ArrayList<>();

        // Format company header (PROPSK LTD section)
        requests.add(new Request()
            .setRepeatCell(new RepeatCellRequest()
                .setRange(new GridRange()
                    .setSheetId(0)
                    .setStartRowIndex(30)
                    .setEndRowIndex(36)
                    .setStartColumnIndex(37)
                    .setEndColumnIndex(38))
                .setCell(new CellData()
                    .setUserEnteredFormat(new CellFormat()
                        .setTextFormat(new TextFormat()
                            .setBold(true)
                            .setFontSize(12)
                            .setFontFamily("Calibri"))
                        .setHorizontalAlignment("RIGHT")))
                .setFields("userEnteredFormat(textFormat,horizontalAlignment)")));

        // Format column headers
        requests.add(new Request()
            .setRepeatCell(new RepeatCellRequest()
                .setRange(new GridRange()
                    .setSheetId(0)
                    .setStartRowIndex(39)
                    .setEndRowIndex(40)
                    .setStartColumnIndex(0)
                    .setEndColumnIndex(38))
                .setCell(new CellData()
                    .setUserEnteredFormat(new CellFormat()
                        .setTextFormat(new TextFormat()
                            .setBold(true)
                            .setFontSize(10)
                            .setFontFamily("Calibri"))
                        .setHorizontalAlignment("CENTER")
                        .setVerticalAlignment("MIDDLE")
                        .setWrapStrategy("WRAP")
                        .setBackgroundColor(new Color()
                            .setRed(0.9f)
                            .setGreen(0.9f)
                            .setBlue(0.9f))
                        .setBorders(new Borders()
                            .setTop(new Border().setStyle("SOLID").setWidth(1))
                            .setBottom(new Border().setStyle("SOLID").setWidth(1))
                            .setLeft(new Border().setStyle("SOLID").setWidth(1))
                            .setRight(new Border().setStyle("SOLID").setWidth(1)))))
                .setFields("userEnteredFormat")));

        // Format currency columns
        int[] currencyColumns = {5, 10, 11, 12, 13, 15, 17, 18, 20, 22, 24, 26, 28, 30, 31, 32, 33, 34, 36};
        for (int col : currencyColumns) {
            requests.add(new Request()
                .setRepeatCell(new RepeatCellRequest()
                    .setRange(new GridRange()
                        .setSheetId(0)
                        .setStartRowIndex(40)
                        .setEndRowIndex(1000) // Large range to cover all data
                        .setStartColumnIndex(col)
                        .setEndColumnIndex(col + 1))
                    .setCell(new CellData()
                        .setUserEnteredFormat(new CellFormat()
                            .setNumberFormat(new com.google.api.services.sheets.v4.model.NumberFormat()
                                .setType("CURRENCY")
                                .setPattern("£#,##0.00;(£#,##0.00)"))
                            .setHorizontalAlignment("RIGHT")))
                    .setFields("userEnteredFormat(numberFormat,horizontalAlignment)")));
        }

        // Format percentage columns
        int[] percentageColumns = {14, 16};
        for (int col : percentageColumns) {
            requests.add(new Request()
                .setRepeatCell(new RepeatCellRequest()
                    .setRange(new GridRange()
                        .setSheetId(0)
                        .setStartRowIndex(40)
                        .setEndRowIndex(1000)
                        .setStartColumnIndex(col)
                        .setEndColumnIndex(col + 1))
                    .setCell(new CellData()
                        .setUserEnteredFormat(new CellFormat()
                            .setNumberFormat(new com.google.api.services.sheets.v4.model.NumberFormat()
                                .setType("PERCENT")
                                .setPattern("0.00%"))
                            .setHorizontalAlignment("CENTER")))
                    .setFields("userEnteredFormat")));
        }

        // Set column widths to match your spreadsheet
        requests.add(new Request()
            .setUpdateDimensionProperties(new UpdateDimensionPropertiesRequest()
                .setRange(new DimensionRange()
                    .setSheetId(0)
                    .setDimension("COLUMNS")
                    .setStartIndex(0)
                    .setEndIndex(38))
                .setProperties(new DimensionProperties()
                    .setPixelSize(100)) // Default width
                .setFields("pixelSize")));

        // Execute all formatting requests
        if (!requests.isEmpty()) {
            BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
                .setRequests(requests);

            sheetsService.spreadsheets()
                .batchUpdate(spreadsheetId, batchRequest)
                .execute();

            System.out.println("✅ Applied Boden House formatting to Google Sheets");
        }
    }

}
package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.dto.StatementGenerationRequest;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerType;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.enums.StatementDataSource;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.statements.XLSXStatementService;
import site.easy.to.build.crm.service.sheets.GoogleSheetsStatementService;
import site.easy.to.build.crm.service.user.OAuthUserService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enhanced Statement Controller with Data Source Selection
 * Allows users to select which data sources to include in generated statements
 */
@Controller
@RequestMapping("/admin/enhanced-statements")
public class EnhancedStatementController {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private XLSXStatementService xlsxStatementService;

    @Autowired
    private GoogleSheetsStatementService googleSheetsStatementService;

    @Autowired
    private OAuthUserService oAuthUserService;

    /**
     * Show statement generation form with data source selection
     */
    @GetMapping
    public String showStatementForm(Model model) {
        // Get all property owners for dropdown
        List<Customer> propertyOwners = customerService.findByCustomerType(CustomerType.PROPERTY_OWNER);
        model.addAttribute("propertyOwners", propertyOwners);

        // Add data sources for checkboxes
        model.addAttribute("dataSources", Arrays.asList(StatementDataSource.values()));

        // Add default date range (current month)
        LocalDate now = LocalDate.now();
        LocalDate fromDate = now.withDayOfMonth(1);
        LocalDate toDate = now.withDayOfMonth(now.lengthOfMonth());

        model.addAttribute("defaultFromDate", fromDate);
        model.addAttribute("defaultToDate", toDate);

        return "admin/enhanced-statements";
    }

    /**
     * Generate property owner statement as XLSX with data source selection
     */
    @PostMapping("/property-owner/xlsx")
    @ResponseBody
    public ResponseEntity<byte[]> generatePropertyOwnerStatementXLSX(
            @RequestParam Long propertyOwnerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) List<String> dataSources,
            @RequestParam(defaultValue = "true") boolean includeExpenses,
            @RequestParam(defaultValue = "true") boolean includeFormulas,
            Authentication authentication) throws IOException {

        try {
            // Parse selected data sources
            Set<StatementDataSource> selectedDataSources = parseDataSources(dataSources);

            // Create request object
            StatementGenerationRequest request = new StatementGenerationRequest(
                propertyOwnerId, fromDate, toDate, selectedDataSources);
            request.setStatementType("PROPERTY_OWNER");
            request.setOutputFormat("XLSX");
            request.setIncludeExpenses(includeExpenses);
            request.setIncludeFormulas(includeFormulas);

            // Generate statement
            Customer propertyOwner = customerService.findByCustomerId(propertyOwnerId);
            byte[] xlsxData = xlsxStatementService.generatePropertyOwnerStatementXLSX(propertyOwner, fromDate, toDate);

            // Generate filename with data source info
            String filename = generateFilename(propertyOwner, fromDate, toDate, selectedDataSources, "xlsx");

            return ResponseEntity.ok()
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(xlsxData);

        } catch (Exception e) {
            String errorMessage = "Error generating statement: " + e.getMessage();
            return ResponseEntity.badRequest()
                .header("Content-Type", "text/plain")
                .body(errorMessage.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Generate property owner statement as Google Sheets with data source selection
     */
    @PostMapping("/property-owner/google-sheets")
    @ResponseBody
    public ResponseEntity<String> generatePropertyOwnerStatementGoogleSheets(
            @RequestParam Long propertyOwnerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) List<String> dataSources,
            @RequestParam(defaultValue = "true") boolean includeExpenses,
            Authentication authentication) throws IOException, GeneralSecurityException {

        try {
            // Parse selected data sources
            Set<StatementDataSource> selectedDataSources = parseDataSources(dataSources);

            // Get OAuth user for Google Sheets access
            OAuthUser oAuthUser = oAuthUserService.findBtEmail(authentication.getName());
            if (oAuthUser == null) {
                return ResponseEntity.badRequest()
                    .body("{\"error\": \"Google OAuth not configured. Please sign in with Google first.\"}");
            }

            // Generate statement
            Customer propertyOwner = customerService.findByCustomerId(propertyOwnerId);
            String spreadsheetId = googleSheetsStatementService.createPropertyOwnerStatement(
                oAuthUser, propertyOwner, fromDate, toDate);

            String spreadsheetUrl = "https://docs.google.com/spreadsheets/d/" + spreadsheetId;

            return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .body("{\"spreadsheetId\":\"" + spreadsheetId + "\",\"url\":\"" + spreadsheetUrl +
                      "\",\"dataSources\":\"" + getDataSourceSummary(selectedDataSources) + "\"}");

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .header("Content-Type", "application/json")
                .body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    /**
     * Generate portfolio statement with data source selection
     */
    @PostMapping("/portfolio/xlsx")
    @ResponseBody
    public ResponseEntity<byte[]> generatePortfolioStatementXLSX(
            @RequestParam Long propertyOwnerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) List<String> dataSources,
            Authentication authentication) throws IOException {

        try {
            // Parse selected data sources
            Set<StatementDataSource> selectedDataSources = parseDataSources(dataSources);

            // Create request object
            StatementGenerationRequest request = new StatementGenerationRequest(
                propertyOwnerId, fromDate, toDate, selectedDataSources);
            request.setStatementType("PORTFOLIO");
            request.setOutputFormat("XLSX");

            // Generate statement
            Customer propertyOwner = customerService.findByCustomerId(propertyOwnerId);
            byte[] xlsxData = xlsxStatementService.generatePortfolioStatementXLSX(propertyOwner, fromDate, toDate);

            // Generate filename
            String filename = generateFilename(propertyOwner, fromDate, toDate, selectedDataSources, "xlsx")
                .replace("Statement", "Portfolio");

            return ResponseEntity.ok()
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(xlsxData);

        } catch (Exception e) {
            String errorMessage = "Error generating portfolio statement: " + e.getMessage();
            return ResponseEntity.badRequest()
                .header("Content-Type", "text/plain")
                .body(errorMessage.getBytes(StandardCharsets.UTF_8));
        }
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    /**
     * Parse data source strings to enum set
     */
    private Set<StatementDataSource> parseDataSources(List<String> dataSources) {
        if (dataSources == null || dataSources.isEmpty()) {
            // Default to all data sources if none selected
            return Arrays.stream(StatementDataSource.values()).collect(Collectors.toSet());
        }

        return dataSources.stream()
            .map(ds -> {
                try {
                    return StatementDataSource.valueOf(ds);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            })
            .filter(ds -> ds != null)
            .collect(Collectors.toSet());
    }

    /**
     * Generate filename with data source information
     */
    private String generateFilename(Customer propertyOwner, LocalDate fromDate, LocalDate toDate,
                                  Set<StatementDataSource> dataSources, String extension) {
        String ownerName = propertyOwner.getName().replaceAll("[^a-zA-Z0-9]", "_");
        String period = fromDate.toString() + "_to_" + toDate.toString();
        String dataSourceSuffix = dataSources.size() == StatementDataSource.values().length ?
            "All_Sources" : "Selected_Sources";

        return String.format("Statement_%s_%s_%s.%s", ownerName, period, dataSourceSuffix, extension);
    }

    /**
     * Get summary of selected data sources
     */
    private String getDataSourceSummary(Set<StatementDataSource> dataSources) {
        if (dataSources == null || dataSources.isEmpty()) {
            return "All data sources";
        }
        return dataSources.stream()
            .map(StatementDataSource::getDisplayName)
            .collect(Collectors.joining(", "));
    }
}
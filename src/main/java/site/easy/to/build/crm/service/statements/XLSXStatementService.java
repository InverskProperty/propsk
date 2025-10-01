package site.easy.to.build.crm.service.statements;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerType;
import site.easy.to.build.crm.entity.FinancialTransaction;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.enums.StatementDataSource;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.repository.FinancialTransactionRepository;
import site.easy.to.build.crm.util.RentCyclePeriodCalculator;
import site.easy.to.build.crm.util.RentCyclePeriodCalculator.RentCyclePeriod;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class XLSXStatementService {

    private final CustomerService customerService;
    private final PropertyService propertyService;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final BodenHouseStatementTemplateService bodenHouseTemplateService;

    @Autowired
    public XLSXStatementService(CustomerService customerService,
                               PropertyService propertyService,
                               FinancialTransactionRepository financialTransactionRepository,
                               BodenHouseStatementTemplateService bodenHouseTemplateService) {
        this.customerService = customerService;
        this.propertyService = propertyService;
        this.financialTransactionRepository = financialTransactionRepository;
        this.bodenHouseTemplateService = bodenHouseTemplateService;
    }

    /**
     * Generate Property Owner Statement as XLSX using Boden House template
     */
    public byte[] generatePropertyOwnerStatementXLSX(Customer propertyOwner,
                                                   LocalDate fromDate, LocalDate toDate)
            throws IOException {

        System.out.println("🏢 Generating XLSX Property Owner statement using Boden House template for: " + propertyOwner.getName());

        // Use new Boden House template
        List<List<Object>> values = bodenHouseTemplateService.generatePropertyOwnerStatement(propertyOwner, fromDate, toDate);

        // Create Excel workbook with the new template
        return createBodenHouseExcelStatement(values, "Property Owner Statement");
    }

    /**
     * Generate Property Owner Statement with monthly breakdown as XLSX
     */
    public byte[] generateMonthlyPropertyOwnerStatementXLSX(Customer propertyOwner,
                                                           LocalDate fromDate, LocalDate toDate,
                                                           Set<StatementDataSource> includedDataSources)
            throws IOException {

        System.out.println("🏢 Generating XLSX MONTHLY Property Owner statement for: " + propertyOwner.getName());
        System.out.println("📊 Date range: " + fromDate + " to " + toDate);

        // Calculate rent cycle periods
        List<RentCyclePeriod> periods = RentCyclePeriodCalculator.calculateMonthlyPeriods(fromDate, toDate);
        System.out.println("📊 Splitting into " + periods.size() + " monthly periods");

        // Create workbook with multiple sheets
        XSSFWorkbook workbook = new XSSFWorkbook();

        // Generate a sheet for each period
        for (int i = 0; i < periods.size(); i++) {
            RentCyclePeriod period = periods.get(i);
            String sheetName = sanitizeSheetName(period.getSheetName());

            System.out.println("📊 Generating sheet " + (i + 1) + "/" + periods.size() + ": " + sheetName);

            // Generate statement data for this period
            List<List<Object>> values = bodenHouseTemplateService.generatePropertyOwnerStatement(
                propertyOwner, period.getStartDate(), period.getEndDate());

            // Create sheet and populate data
            XSSFSheet sheet = workbook.createSheet(sheetName);
            populateSheetWithData(sheet, values);
            applyBodenHouseFormatting(workbook, sheet);

            System.out.println("✅ Completed sheet: " + sheetName);
        }

        // Create summary sheet
        createPeriodSummarySheetXLSX(workbook, propertyOwner, periods);

        // Force formula evaluation
        workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();

        // Convert to byte array
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();

        System.out.println("✅ Monthly XLSX statement generated successfully - " + outputStream.size() + " bytes");

        return outputStream.toByteArray();
    }

    /**
     * Sanitize sheet name for Excel (max 31 chars, no invalid chars)
     */
    private String sanitizeSheetName(String name) {
        // Remove invalid characters: \ / * ? [ ]
        String sanitized = name.replaceAll("[\\\\/*?\\[\\]]", "-");

        // Excel sheet names must be 31 characters or less
        if (sanitized.length() > 31) {
            sanitized = sanitized.substring(0, 31);
        }

        return sanitized;
    }

    /**
     * Populate sheet with data
     */
    private void populateSheetWithData(XSSFSheet sheet, List<List<Object>> values) {
        for (int rowIndex = 0; rowIndex < values.size(); rowIndex++) {
            Row row = sheet.createRow(rowIndex);
            List<Object> rowData = values.get(rowIndex);

            for (int colIndex = 0; colIndex < rowData.size(); colIndex++) {
                Cell cell = row.createCell(colIndex);
                Object value = rowData.get(colIndex);

                if (value instanceof String && ((String) value).startsWith("=")) {
                    // Excel formula - remove leading "=" and set as formula
                    try {
                        cell.setCellFormula(((String) value).substring(1));
                    } catch (Exception e) {
                        // Fallback to text if formula is invalid
                        cell.setCellValue(value.toString());
                    }
                } else if (value instanceof BigDecimal) {
                    cell.setCellValue(((BigDecimal) value).doubleValue());
                } else if (value instanceof Number) {
                    cell.setCellValue(((Number) value).doubleValue());
                } else if (value instanceof Boolean) {
                    cell.setCellValue(((Boolean) value) ? "TRUE" : "FALSE");
                } else if (value != null) {
                    cell.setCellValue(value.toString());
                }
            }
        }

        // Auto-size columns for better readability (38 columns)
        for (int i = 0; i < 38; i++) {
            try {
                sheet.autoSizeColumn(i);
                // Set minimum width to prevent too narrow columns
                if (sheet.getColumnWidth(i) < 1500) {
                    sheet.setColumnWidth(i, 1500);
                }
                // Set maximum width to prevent too wide columns
                if (sheet.getColumnWidth(i) > 6000) {
                    sheet.setColumnWidth(i, 6000);
                }
            } catch (Exception e) {
                // Continue if auto-sizing fails for any column
            }
        }
    }

    /**
     * Create Period Summary sheet in XLSX workbook
     */
    private void createPeriodSummarySheetXLSX(XSSFWorkbook workbook, Customer propertyOwner,
                                             List<RentCyclePeriod> periods) {
        System.out.println("📊 Creating Period Summary sheet");

        XSSFSheet summarySheet = workbook.createSheet("Period Summary");

        // Build summary data
        List<List<Object>> summaryData = new ArrayList<>();

        // Header
        summaryData.add(Arrays.asList("PERIOD SUMMARY"));
        summaryData.add(Arrays.asList(""));
        summaryData.add(Arrays.asList("Property Owner:", propertyOwner.getName()));
        summaryData.add(Arrays.asList(""));

        // Column headers
        summaryData.add(Arrays.asList("Period", "Total Rent Due", "Total Received", "Management Fee",
                                      "Service Fee", "Total Expenses", "Net Due to Owner"));

        // Period rows with formulas referencing other sheets
        for (RentCyclePeriod period : periods) {
            String sheetName = sanitizeSheetName(period.getSheetName());

            summaryData.add(Arrays.asList(
                period.getDisplayName(),
                "='" + sheetName + "'!D" + (13 + 5),
                "='" + sheetName + "'!F" + (13 + 5),
                "='" + sheetName + "'!G" + (13 + 5),
                "='" + sheetName + "'!H" + (13 + 5),
                "='" + sheetName + "'!I" + (13 + 10),
                "='" + sheetName + "'!I" + (13 + 5)
            ));
        }

        // Grand totals row
        int firstDataRow = 6;
        int lastDataRow = firstDataRow + periods.size() - 1;
        summaryData.add(Arrays.asList(""));
        summaryData.add(Arrays.asList(
            "TOTAL",
            "=SUM(B" + firstDataRow + ":B" + lastDataRow + ")",
            "=SUM(C" + firstDataRow + ":C" + lastDataRow + ")",
            "=SUM(D" + firstDataRow + ":D" + lastDataRow + ")",
            "=SUM(E" + firstDataRow + ":E" + lastDataRow + ")",
            "=SUM(F" + firstDataRow + ":F" + lastDataRow + ")",
            "=SUM(G" + firstDataRow + ":G" + lastDataRow + ")"
        ));

        // Populate summary sheet
        populateSheetWithData(summarySheet, summaryData);

        // Apply summary-specific formatting
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 16);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font whiteFont = workbook.createFont();
        whiteFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(whiteFont);

        Row headerRow = summarySheet.getRow(0);
        if (headerRow != null && headerRow.getCell(0) != null) {
            headerRow.getCell(0).setCellStyle(headerStyle);
        }

        // Column headers formatting
        CellStyle columnHeaderStyle = workbook.createCellStyle();
        Font columnHeaderFont = workbook.createFont();
        columnHeaderFont.setBold(true);
        columnHeaderStyle.setFont(columnHeaderFont);
        columnHeaderStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        columnHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Row columnHeaderRow = summarySheet.getRow(4);
        if (columnHeaderRow != null) {
            for (int i = 0; i < 7; i++) {
                Cell cell = columnHeaderRow.getCell(i);
                if (cell != null) {
                    cell.setCellStyle(columnHeaderStyle);
                }
            }
        }

        // Currency formatting for amount columns (B through G)
        CellStyle currencyStyle = workbook.createCellStyle();
        currencyStyle.setDataFormat(workbook.createDataFormat().getFormat("£#,##0.00"));

        for (int rowIdx = 5; rowIdx < summaryData.size(); rowIdx++) {
            Row row = summarySheet.getRow(rowIdx);
            if (row != null) {
                for (int colIdx = 1; colIdx <= 6; colIdx++) {
                    Cell cell = row.getCell(colIdx);
                    if (cell != null) {
                        cell.setCellStyle(currencyStyle);
                    }
                }
            }
        }

        System.out.println("✅ Period Summary sheet created");
    }

    /**
     * Generate Tenant Statement as XLSX
     */
    public byte[] generateTenantStatementXLSX(Customer tenant,
                                            LocalDate fromDate, LocalDate toDate)
            throws IOException {

        System.out.println("🏠 Generating tenant XLSX statement for: " + tenant.getName());

        TenantStatementData data = buildTenantStatementData(tenant, fromDate, toDate);
        return createTenantExcelStatement(data);
    }

    /**
     * Generate Portfolio Statement as XLSX using Boden House template
     */
    public byte[] generatePortfolioStatementXLSX(Customer propertyOwner,
                                               LocalDate fromDate, LocalDate toDate)
            throws IOException {

        System.out.println("📊 Generating portfolio XLSX statement using Boden House template for: " + propertyOwner.getName());

        // Use new Boden House template for portfolio
        List<List<Object>> values = bodenHouseTemplateService.generatePortfolioStatement(propertyOwner, fromDate, toDate);

        // Create Excel workbook with the new template
        return createBodenHouseExcelStatement(values, "Portfolio Statement");
    }

    // ===== EXCEL CREATION METHODS =====

    /**
     * Create Excel statement using Boden House template (NEW METHOD)
     */
    private byte[] createBodenHouseExcelStatement(List<List<Object>> values, String sheetName) throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet(sheetName);

        // Create rows and cells following your exact template structure
        for (int rowIndex = 0; rowIndex < values.size(); rowIndex++) {
            Row row = sheet.createRow(rowIndex);
            List<Object> rowData = values.get(rowIndex);

            for (int colIndex = 0; colIndex < rowData.size(); colIndex++) {
                Cell cell = row.createCell(colIndex);
                Object value = rowData.get(colIndex);

                if (value instanceof String && ((String) value).startsWith("=")) {
                    // Excel formula - remove leading "=" and set as formula
                    try {
                        cell.setCellFormula(((String) value).substring(1));
                    } catch (Exception e) {
                        // Fallback to text if formula is invalid
                        cell.setCellValue(value.toString());
                    }
                } else if (value instanceof BigDecimal) {
                    cell.setCellValue(((BigDecimal) value).doubleValue());
                } else if (value instanceof Number) {
                    cell.setCellValue(((Number) value).doubleValue());
                } else if (value instanceof Boolean) {
                    cell.setCellValue(((Boolean) value) ? "TRUE" : "FALSE");
                } else if (value != null) {
                    cell.setCellValue(value.toString());
                }
            }
        }

        // Apply Boden House specific formatting
        applyBodenHouseFormatting(workbook, sheet);

        // Auto-size columns for better readability (38 columns like your spreadsheet)
        for (int i = 0; i < 38; i++) {
            try {
                sheet.autoSizeColumn(i);
                // Set minimum width to prevent too narrow columns
                if (sheet.getColumnWidth(i) < 1500) {
                    sheet.setColumnWidth(i, 1500);
                }
                // Set maximum width to prevent too wide columns
                if (sheet.getColumnWidth(i) > 6000) {
                    sheet.setColumnWidth(i, 6000);
                }
            } catch (Exception e) {
                // Continue if auto-sizing fails for any column
            }
        }

        // Force formula evaluation
        workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();

        // Convert to byte array
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();

        System.out.println("✅ Boden House XLSX statement generated successfully - " + outputStream.size() + " bytes");

        return outputStream.toByteArray();
    }

    private byte[] createExcelStatement(PropertyOwnerStatementData data) throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Property Statement");

        // Get statement structure with formulas (reuse existing Google Sheets logic)
        List<List<Object>> values = buildEnhancedPropertyOwnerStatementValues(data);

        // Create rows and cells with formula support
        for (int rowIndex = 0; rowIndex < values.size(); rowIndex++) {
            Row row = sheet.createRow(rowIndex);
            List<Object> rowData = values.get(rowIndex);

            for (int colIndex = 0; colIndex < rowData.size(); colIndex++) {
                Cell cell = row.createCell(colIndex);
                Object value = rowData.get(colIndex);

                if (value instanceof String && ((String) value).startsWith("=")) {
                    // Excel formula - remove leading "=" and set as formula
                    try {
                        cell.setCellFormula(((String) value).substring(1));
                    } catch (Exception e) {
                        // Fallback to text if formula is invalid
                        cell.setCellValue(value.toString());
                    }
                } else if (value instanceof BigDecimal) {
                    cell.setCellValue(((BigDecimal) value).doubleValue());
                } else if (value instanceof Number) {
                    cell.setCellValue(((Number) value).doubleValue());
                } else if (value != null) {
                    cell.setCellValue(value.toString());
                }
            }
        }

        // Apply professional formatting
        applyPropertyOwnerStatementFormatting(workbook, sheet, data);

        // Auto-size columns for better readability (38 columns)
        for (int i = 0; i < 38; i++) {
            try {
                sheet.autoSizeColumn(i);
                // Set minimum width to prevent too narrow columns
                if (sheet.getColumnWidth(i) < 1500) {
                    sheet.setColumnWidth(i, 1500);
                }
                // Set maximum width to prevent too wide columns
                if (sheet.getColumnWidth(i) > 6000) {
                    sheet.setColumnWidth(i, 6000);
                }
            } catch (Exception e) {
                // Continue if auto-sizing fails for any column
            }
        }

        // Force formula evaluation
        workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();

        // Convert to byte array
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();

        System.out.println("✅ XLSX statement generated successfully - " + outputStream.size() + " bytes");

        return outputStream.toByteArray();
    }

    private byte[] createTenantExcelStatement(TenantStatementData data) throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Tenant Statement");

        List<List<Object>> values = buildTenantStatementValues(data);

        // Create rows and cells
        for (int rowIndex = 0; rowIndex < values.size(); rowIndex++) {
            Row row = sheet.createRow(rowIndex);
            List<Object> rowData = values.get(rowIndex);

            for (int colIndex = 0; colIndex < rowData.size(); colIndex++) {
                Cell cell = row.createCell(colIndex);
                Object value = rowData.get(colIndex);

                if (value instanceof BigDecimal) {
                    cell.setCellValue(((BigDecimal) value).doubleValue());
                } else if (value != null) {
                    cell.setCellValue(value.toString());
                }
            }
        }

        applyBasicFormatting(workbook, sheet);

        // Auto-size columns
        for (int i = 0; i < 6; i++) {
            sheet.autoSizeColumn(i);
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();

        return outputStream.toByteArray();
    }

    private byte[] createPortfolioExcelStatement(PortfolioStatementData data) throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Portfolio Statement");

        List<List<Object>> values = buildPortfolioStatementValues(data);

        // Create rows and cells
        for (int rowIndex = 0; rowIndex < values.size(); rowIndex++) {
            Row row = sheet.createRow(rowIndex);
            List<Object> rowData = values.get(rowIndex);

            for (int colIndex = 0; colIndex < rowData.size(); colIndex++) {
                Cell cell = row.createCell(colIndex);
                Object value = rowData.get(colIndex);

                if (value instanceof BigDecimal) {
                    cell.setCellValue(((BigDecimal) value).doubleValue());
                } else if (value != null) {
                    cell.setCellValue(value.toString());
                }
            }
        }

        applyBasicFormatting(workbook, sheet);

        // Auto-size columns
        for (int i = 0; i < 6; i++) {
            sheet.autoSizeColumn(i);
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();

        return outputStream.toByteArray();
    }

    // ===== FORMATTING METHODS =====

    private void applyPropertyOwnerStatementFormatting(XSSFWorkbook workbook, XSSFSheet sheet, PropertyOwnerStatementData data) {
        // Create styles
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);
        CellStyle totalStyle = createTotalStyle(workbook);
        CellStyle columnHeaderStyle = createColumnHeaderStyle(workbook);
        CellStyle booleanStyle = createBooleanStyle(workbook);
        CellStyle percentageStyle = createPercentageStyle(workbook);

        // Apply header formatting (rows 0-11 - company info and statement header)
        for (int rowIndex = 0; rowIndex <= 11; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null) {
                for (Cell cell : row) {
                    if (cell != null && cell.getStringCellValue() != null && !cell.getStringCellValue().trim().isEmpty()) {
                        cell.setCellStyle(headerStyle);
                    }
                }
            }
        }

        // Apply column header formatting (row 12 - table headers with 38 columns)
        Row columnHeaderRow = sheet.getRow(12);
        if (columnHeaderRow != null) {
            for (int i = 0; i < 38; i++) {
                Cell cell = columnHeaderRow.getCell(i);
                if (cell != null) {
                    cell.setCellStyle(columnHeaderStyle);
                }
            }
        }

        // Enhanced column formatting for 38-column layout
        int dataStartRow = 13;
        int dataEndRow = dataStartRow + data.getRentalData().size() + 15; // Include additional rows

        // Currency columns: E, J, K, L, M, O, Q, R, T, W, Z, AC, AE, AF, AG, AH, AI, AK
        int[] currencyColumns = {4, 9, 10, 11, 12, 14, 16, 17, 19, 22, 25, 28, 30, 31, 32, 33, 34, 36};

        // Boolean columns: G, H, I
        int[] booleanColumns = {6, 7, 8};

        // Percentage columns: N, P
        int[] percentageColumns = {13, 15};

        // Apply currency formatting
        for (int col : currencyColumns) {
            for (int rowIndex = dataStartRow; rowIndex <= dataEndRow; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row != null) {
                    Cell cell = row.getCell(col);
                    if (cell != null) {
                        // Check if this is a TOTAL row
                        Row checkRow = sheet.getRow(rowIndex);
                        if (checkRow != null && checkRow.getCell(0) != null &&
                            "TOTAL".equals(checkRow.getCell(0).getStringCellValue())) {
                            cell.setCellStyle(totalStyle);
                        } else {
                            cell.setCellStyle(currencyStyle);
                        }
                    }
                }
            }
        }

        // Apply boolean formatting
        for (int col : booleanColumns) {
            for (int rowIndex = dataStartRow; rowIndex <= dataEndRow; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row != null) {
                    Cell cell = row.getCell(col);
                    if (cell != null) {
                        cell.setCellStyle(booleanStyle);
                    }
                }
            }
        }

        // Apply percentage formatting
        for (int col : percentageColumns) {
            for (int rowIndex = dataStartRow; rowIndex <= dataEndRow; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row != null) {
                    Cell cell = row.getCell(col);
                    if (cell != null) {
                        cell.setCellStyle(percentageStyle);
                    }
                }
            }
        }

        // Add borders around data table (38 columns)
        addTableBorders(workbook, sheet, 12, dataEndRow, 0, 37);
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        return style;
    }

    private CellStyle createCurrencyStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("£#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private CellStyle createTotalStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setDataFormat(workbook.createDataFormat().getFormat("£#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createColumnHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Add borders
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        return style;
    }

    private CellStyle createBooleanStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        // Add borders
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        return style;
    }

    private CellStyle createPercentageStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("0%"));
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        // Add borders
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        return style;
    }

    private void applyBasicFormatting(XSSFWorkbook workbook, XSSFSheet sheet) {
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle currencyStyle = createCurrencyStyle(workbook);

        // Apply header formatting to first 8 rows
        for (int rowIndex = 0; rowIndex <= 7; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null) {
                Cell firstCell = row.getCell(0);
                if (firstCell != null) {
                    firstCell.setCellStyle(headerStyle);
                }
            }
        }
    }

    private void addTableBorders(XSSFWorkbook workbook, XSSFSheet sheet, int startRow, int endRow, int startCol, int endCol) {
        // This would add borders using POI's border utilities
        // For now, we'll skip complex border implementation
    }

    /**
     * Apply Boden House specific formatting (matching your spreadsheet style)
     */
    private void applyBodenHouseFormatting(XSSFWorkbook workbook, XSSFSheet sheet) {
        CellStyle headerStyle = createBodenHouseHeaderStyle(workbook);
        CellStyle currencyStyle = createBodenHouseCurrencyStyle(workbook);
        CellStyle percentageStyle = createBodenHousePercentageStyle(workbook);
        CellStyle companyHeaderStyle = createBodenHouseCompanyHeaderStyle(workbook);

        // Apply company header formatting (PROPSK LTD section)
        for (int rowIndex = 30; rowIndex <= 35; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null) {
                Cell companyCell = row.getCell(37); // Company info in column 37
                if (companyCell != null) {
                    companyCell.setCellStyle(companyHeaderStyle);
                }
            }
        }

        // Apply header formatting to column headers row
        Row headerRow = sheet.getRow(39); // Assuming headers are around row 39
        if (headerRow != null) {
            for (int colIndex = 0; colIndex < 38; colIndex++) {
                Cell cell = headerRow.getCell(colIndex);
                if (cell != null) {
                    cell.setCellStyle(headerStyle);
                }
            }
        }

        // Apply currency formatting to amount columns
        int[] currencyColumns = {5, 10, 11, 12, 13, 15, 17, 18, 20, 22, 24, 26, 28, 30, 31, 32, 33, 34, 36};
        for (int rowIndex = 40; rowIndex < sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null) {
                for (int colIndex : currencyColumns) {
                    Cell cell = row.getCell(colIndex);
                    if (cell != null && cell.getCellType() == CellType.NUMERIC) {
                        cell.setCellStyle(currencyStyle);
                    }
                }
            }
        }

        // Apply percentage formatting to percentage columns
        int[] percentageColumns = {14, 16};
        for (int rowIndex = 40; rowIndex < sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null) {
                for (int colIndex : percentageColumns) {
                    Cell cell = row.getCell(colIndex);
                    if (cell != null) {
                        cell.setCellStyle(percentageStyle);
                    }
                }
            }
        }

        // Set column widths to match your spreadsheet
        setBodenHouseColumnWidths(sheet);
    }

    private CellStyle createBodenHouseHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        font.setFontName("Calibri");
        style.setFont(font);

        // Background color (light grey)
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Borders
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        // Center alignment
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);

        return style;
    }

    private CellStyle createBodenHouseCurrencyStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setFontName("Calibri");
        style.setFont(font);

        // Currency format (£)
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("£#,##0.00;(£#,##0.00)"));

        // Alignment
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        // Borders
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        return style;
    }

    private CellStyle createBodenHousePercentageStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setFontName("Calibri");
        style.setFont(font);

        // Percentage format
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("0.00%"));

        // Alignment
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        // Borders
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        return style;
    }

    private CellStyle createBodenHouseCompanyHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        font.setFontName("Calibri");
        style.setFont(font);

        // Alignment
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        return style;
    }

    private void setBodenHouseColumnWidths(XSSFSheet sheet) {
        // Set column widths to match your spreadsheet (in Excel units)
        int[] columnWidths = {
            800,   // cde
            2500,  // Unit No.
            4000,  // Tenant
            2000,  // Tenancy Dates
            1500,  // Rent Due Date
            2000,  // Rent Due Amount
            2000,  // Rent Received Date
            2000,  // Paid to Robert Ellis
            2500,  // Paid to Propsk Old Account
            2500,  // Paid to Propsk PayProp
            2000,  // Rent Received Amount
            2500,  // Amount received in Payprop
            2500,  // Amount Received Old Account
            2500,  // Total Rent Received By Propsk
            1500,  // Management Fee (%)
            2000,  // Management Fee (£)
            1500,  // Service Fee (%)
            2000,  // Service Fee (£)
            2500,  // Total Fees Charged by Propsk
            2000,  // Expense 1 Label
            2000,  // Expense 1 Amount
            3000,  // Expense 1 Comment
            2000,  // Expense 2 Label
            2000,  // Expense 2 Amount
            3000,  // Expense 2 Comment
            2000,  // Expense 3 Label
            2000,  // Expense 3 Amount
            3000,  // Expense 3 Comment
            2000,  // Expense 4 Label
            2000,  // Expense 4 Amount
            3000,  // Expense 4 Comment
            2000,  // Total Expenses
            2500,  // Total Expenses and Commission
            2500,  // Net Due to Prestvale
            3000,  // Net Due from Propsk After Expenses and Commissions
            1500,  // Date Paid
            2000,  // Rent Due less Received
            2000   // Comments
        };

        for (int i = 0; i < columnWidths.length && i < 38; i++) {
            sheet.setColumnWidth(i, columnWidths[i]);
        }
    }

    // ===== DATA BUILDING METHODS (Copied from GoogleSheetsStatementService) =====

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

        // Set rent due day
        rentalData.setRentDueDay(extractRentDueDay(property));

        // Set fee percentages
        BigDecimal managementPercentage = property.getCommissionPercentage() != null ?
            property.getCommissionPercentage() : new BigDecimal("10.0");
        BigDecimal servicePercentage = new BigDecimal("5.0");

        rentalData.setManagementFeePercentage(managementPercentage);
        rentalData.setServiceFeePercentage(servicePercentage);

        // Get financial transaction data
        List<FinancialTransaction> transactions = Collections.emptyList();
        try {
            if (property.getPayPropId() != null) {
                transactions = financialTransactionRepository
                    .findPropertyTransactionsForStatement(property.getPayPropId(), fromDate, toDate);
            }
        } catch (Exception e) {
            System.err.println("Error getting transactions for property " + property.getId() + ": " + e.getMessage());
        }

        // Calculate rent received
        BigDecimal rentReceived = calculateActualRentReceived(transactions);
        rentalData.setRentReceived(rentReceived);

        // Set payment routing flags
        rentalData.setPaidToRobertEllis(isPaidToRobertEllis(property, transactions));
        rentalData.setPaidToOldAccount(isPaidToOldAccount(property, transactions));
        rentalData.setPaidToPayProp(isPaidToPayProp(property, transactions));

        // Calculate payment amounts by account
        if (rentalData.getPaidToPayProp()) {
            rentalData.setAmountReceivedPayProp(rentReceived);
            rentalData.setAmountReceivedOldAccount(BigDecimal.ZERO);
        } else if (rentalData.getPaidToOldAccount()) {
            rentalData.setAmountReceivedOldAccount(rentReceived);
            rentalData.setAmountReceivedPayProp(BigDecimal.ZERO);
        } else {
            rentalData.setAmountReceivedPayProp(BigDecimal.ZERO);
            rentalData.setAmountReceivedOldAccount(BigDecimal.ZERO);
        }

        // Get expenses for this property
        List<ExpenseItem> expenses = getExpensesForProperty(property, fromDate, toDate);
        rentalData.setExpenses(expenses);

        return rentalData;
    }

    private List<List<Object>> buildEnhancedPropertyOwnerStatementValues(PropertyOwnerStatementData data) {
        List<List<Object>> values = new ArrayList<>();

        // Enhanced header section - 38 columns
        String[] emptyRow = new String[38];
        Arrays.fill(emptyRow, "");

        // Company header
        String[] companyRow1 = emptyRow.clone();
        companyRow1[4] = "PROPSK LTD";
        values.add(Arrays.asList(companyRow1));

        String[] companyRow2 = emptyRow.clone();
        companyRow2[4] = "1 Poplar Court, Greensward Lane, Hockley, England, SS5 5JB";
        values.add(Arrays.asList(companyRow2));

        String[] companyRow3 = emptyRow.clone();
        companyRow3[4] = "Company number 15933011";
        values.add(Arrays.asList(companyRow3));

        values.add(Arrays.asList(emptyRow)); // Empty row

        String[] statementRow = emptyRow.clone();
        statementRow[4] = "STATEMENT";
        values.add(Arrays.asList(statementRow));

        values.add(Arrays.asList(emptyRow)); // Empty row

        // Client, Property, Period information
        String[] clientRow = emptyRow.clone();
        clientRow[0] = "CLIENT:";
        clientRow[1] = data.getPropertyOwner().getName();
        values.add(Arrays.asList(clientRow));

        String[] propertyRow = emptyRow.clone();
        propertyRow[0] = "PROPERTY:";
        propertyRow[1] = data.getProperties().isEmpty() ? "PROPERTY PORTFOLIO" : data.getProperties().get(0).getPropertyName();
        values.add(Arrays.asList(propertyRow));

        // Format period dates
        String fromDateFormatted = data.getFromDate().format(DateTimeFormatter.ofPattern("d")) +
                                 getOrdinalSuffix(data.getFromDate().getDayOfMonth()) + " " +
                                 data.getFromDate().format(DateTimeFormatter.ofPattern("MMM yyyy"));
        String toDateFormatted = data.getToDate().format(DateTimeFormatter.ofPattern("d")) +
                               getOrdinalSuffix(data.getToDate().getDayOfMonth()) + " " +
                               data.getToDate().format(DateTimeFormatter.ofPattern("MMM yyyy"));

        String[] periodRow = emptyRow.clone();
        periodRow[0] = "PERIOD:";
        periodRow[1] = fromDateFormatted + " to " + toDateFormatted;
        values.add(Arrays.asList(periodRow));

        values.add(Arrays.asList(emptyRow)); // Empty row
        values.add(Arrays.asList(emptyRow)); // Empty row

        // Income Statement Header
        String[] incomeHeaderRow = emptyRow.clone();
        incomeHeaderRow[0] = "Income Statement";
        values.add(Arrays.asList(incomeHeaderRow));

        // Enhanced column headers (38 columns total)
        values.add(Arrays.asList(
            "Unit No.", "Tenant", "Tenancy Dates", "Rent Due Date",
            "Rent\nDue\nAmount", "Rent\nReceived\nDate",
            "Paid to Robert Ellis", "Paid to Propsk Old Account", "Paid to Propsk PayProp",
            "Rent\nReceived\nAmount", "Amount received in Payprop", "Amount Received Old Account",
            "Total Rent Received By Propsk", "Management\nFee\n(%)", "Management\nFee\n(£)",
            "Service Fee (%)", "Service\nFee\n(£)", "Total Fees Charged by Propsk",
            "Expense 1 Label", "Expense 1 Amount", "Expense 1 Comment",
            "Expense 2 Label", "Expense 2 Amount", "Expense 2 Comment",
            "Expense 3 Label", "Expense 3 Amount", "Expense 3 Comment",
            "Expense 4 Label", "Expense 4 Amount", "Expense 4 Comment",
            "Total Expenses", "Net\nDue to\n" + getFirstNameSafe(data.getPropertyOwner()),
            "Net Due from Propsk Old Account", "Net Due from Propsk PayProp Account",
            "Net Due from Propsk Total", "Date\nPaid", "Rent\nDue less\nReceived", "Comments"
        ));

        // Enhanced data rows with all formulas
        int dataStartRow = values.size() + 1;

        for (PropertyRentalData rental : data.getRentalData()) {
            int currentRow = values.size() + 1; // Current Excel row (1-based)

            // Get financial data
            List<FinancialTransaction> transactions = financialTransactionRepository
                .findPropertyTransactionsForStatement(rental.getProperty().getPayPropId(), data.getFromDate(), data.getToDate());

            // Calculate rent received
            BigDecimal rentReceived = calculateActualRentReceived(transactions);

            // Get expenses for this property (up to 4)
            List<ExpenseItem> propertyExpenses = getExpensesForProperty(rental.getProperty(), data.getFromDate(), data.getToDate());

            // Payment routing detection
            Boolean paidToRobertEllis = isPaidToRobertEllis(rental.getProperty(), transactions);
            Boolean paidToOldAccount = isPaidToOldAccount(rental.getProperty(), transactions);
            Boolean paidToPayProp = isPaidToPayProp(rental.getProperty(), transactions);

            values.add(Arrays.asList(
                // A-C: Basic info
                rental.getUnitNumber(),                                    // A
                rental.getTenantName(),                                    // B
                formatTenancyDate(rental.getStartDate()),                  // C

                // D-I: Payment routing
                extractRentDueDay(rental.getProperty()),                   // D - Rent Due Date
                rental.getRentDue(),                                       // E - Rent Due Amount
                getPaymentDate(transactions),                              // F - Rent Received Date
                paidToRobertEllis,                                         // G - Paid to Robert Ellis
                paidToOldAccount,                                          // H - Paid to Old Account
                paidToPayProp,                                             // I - Paid to PayProp

                // J-M: Rent calculations
                rentReceived,                                              // J - Rent Received Amount
                "=J" + currentRow + "*I" + currentRow,                    // K - Amount via PayProp
                "=H" + currentRow + "*J" + currentRow,                    // L - Amount via Old Account
                "=(J" + currentRow + "*H" + currentRow + ")+(J" + currentRow + "*I" + currentRow + ")", // M - Total Received

                // N-R: Fee calculations
                "10%",                                                     // N - Management Fee %
                "=J" + currentRow + "*-0.1",                             // O - Management Fee £
                "5%",                                                      // P - Service Fee %
                "=J" + currentRow + "*-0.05",                            // Q - Service Fee £
                "=O" + currentRow + "+Q" + currentRow,                   // R - Total Fees

                // S-AD: Expenses (4 slots)
                getExpenseLabel(propertyExpenses, 0),                      // S - Expense 1 Label
                getExpenseAmount(propertyExpenses, 0),                     // T - Expense 1 Amount
                getExpenseComment(propertyExpenses, 0),                    // U - Expense 1 Comment
                getExpenseLabel(propertyExpenses, 1),                      // V - Expense 2 Label
                getExpenseAmount(propertyExpenses, 1),                     // W - Expense 2 Amount
                getExpenseComment(propertyExpenses, 1),                    // X - Expense 2 Comment
                getExpenseLabel(propertyExpenses, 2),                      // Y - Expense 3 Label
                getExpenseAmount(propertyExpenses, 2),                     // Z - Expense 3 Amount
                getExpenseComment(propertyExpenses, 2),                    // AA - Expense 3 Comment
                getExpenseLabel(propertyExpenses, 3),                      // AB - Expense 4 Label
                getExpenseAmount(propertyExpenses, 3),                     // AC - Expense 4 Amount
                getExpenseComment(propertyExpenses, 3),                    // AD - Expense 4 Comment

                // AE-AL: Net calculations and final data
                "=-T" + currentRow + "+-W" + currentRow + "+-Z" + currentRow + "+-AC" + currentRow, // AE - Total Expenses
                "=J" + currentRow + "+R" + currentRow + "+AE" + currentRow,  // AF - Net Due to Owner
                "=AF" + currentRow + "*H" + currentRow,                      // AG - Net from Old Account
                "=AF" + currentRow + "*I" + currentRow,                      // AH - Net from PayProp
                "=AG" + currentRow + "+AH" + currentRow,                     // AI - Net Total
                getDistributionDate(transactions),                           // AJ - Date Paid
                "=E" + currentRow + "-J" + currentRow,                      // AK - Outstanding
                getPropertyComments(rental.getProperty(), transactions)      // AL - Comments
            ));
        }

        // Add additional property types (OFFICE, BUILDING, Parking spaces)
        values.add(addOtherIncomeRow("OFFICE", values.size() + 1));
        values.add(addOtherIncomeRow("BUILDING ADDITIONAL INCOME", values.size() + 1));

        // Add parking spaces
        for (int i = 1; i <= 10; i++) {
            values.add(addOtherIncomeRow("Parking Space " + i, values.size() + 1));
        }

        // TOTAL row with comprehensive SUM formulas
        int dataEndRow = values.size();
        values.add(createTotalRow(dataStartRow, dataEndRow));

        return values;
    }

    // ===== ENHANCED HELPER METHODS FOR 38-COLUMN LAYOUT =====

    // Payment routing detection methods
    private Boolean isPaidToRobertEllis(Property property, List<FinancialTransaction> transactions) {
        // Check if payments go to Robert Ellis account
        // This would be based on your business logic - for now, default to FALSE
        return false;
    }

    private Boolean isPaidToOldAccount(Property property, List<FinancialTransaction> transactions) {
        // Check if property uses old Propsk account
        // Look for transactions that indicate old account usage based on data source or description
        return transactions.stream()
            .anyMatch(t -> (t.getDataSource() != null && t.getDataSource().contains("old")) ||
                          (t.getDescription() != null && t.getDescription().toLowerCase().contains("old account"))) ||
               property.getComment() != null && property.getComment().toLowerCase().contains("old account");
    }

    private Boolean isPaidToPayProp(Property property, List<FinancialTransaction> transactions) {
        // Check if property uses PayProp system
        return property.getPayPropId() != null && !property.getPayPropId().trim().isEmpty();
    }

    private Integer extractRentDueDay(Property property) {
        // Extract rent due day from property data or tenant data
        Customer tenant = getTenantForProperty(property.getId());
        if (tenant != null) {
            // Since getRentDueDay() doesn't exist, use a default logic or extract from other fields
            // You could add this field to Customer entity later, for now use a default
        }

        // Default common rent due days
        if (property.getPropertyName() != null) {
            String name = property.getPropertyName().toLowerCase();
            if (name.contains("1")) return 1;
            if (name.contains("15")) return 15;
            if (name.contains("27")) return 27;
        }

        // Default to 1st of month
        return 1;
    }

    private List<ExpenseItem> getExpensesForProperty(Property property, LocalDate fromDate, LocalDate toDate) {
        List<ExpenseItem> expenses = new ArrayList<>();

        try {
            if (property.getPayPropId() != null) {
                List<FinancialTransaction> expenseTransactions = financialTransactionRepository
                    .findByPropertyAndDateRange(property.getPayPropId(), fromDate, toDate)
                    .stream()
                    .filter(this::isExpenseTransaction)
                    .limit(4) // Maximum 4 expenses per property
                    .collect(Collectors.toList());

                for (FinancialTransaction expense : expenseTransactions) {
                    String label = expense.getCategoryName() != null ? expense.getCategoryName() :
                                  expense.getDescription() != null ? expense.getDescription() : "Expense";
                    BigDecimal amount = expense.getAmount() != null ? expense.getAmount() : BigDecimal.ZERO;
                    String comment = expense.getDescription() != null ? expense.getDescription() : "";

                    expenses.add(new ExpenseItem(label, amount, comment));
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting expenses for property " + property.getId() + ": " + e.getMessage());
        }

        return expenses;
    }

    private String getExpenseLabel(List<ExpenseItem> expenses, int index) {
        return expenses.size() > index && expenses.get(index) != null ?
               expenses.get(index).getLabel() : "";
    }

    private BigDecimal getExpenseAmount(List<ExpenseItem> expenses, int index) {
        return expenses.size() > index && expenses.get(index) != null ?
               expenses.get(index).getAmount() : BigDecimal.ZERO;
    }

    private String getExpenseComment(List<ExpenseItem> expenses, int index) {
        return expenses.size() > index && expenses.get(index) != null ?
               expenses.get(index).getComment() : "";
    }

    private String getPropertyComments(Property property, List<FinancialTransaction> transactions) {
        StringBuilder comments = new StringBuilder();

        // Add property-specific comments
        if (property.getComment() != null && !property.getComment().trim().isEmpty()) {
            comments.append(property.getComment());
        }

        // Add vacancy status
        Customer tenant = getTenantForProperty(property.getId());
        if (tenant == null) {
            if (comments.length() > 0) comments.append("; ");
            comments.append("Vacant");
        }

        // Add payment method notes
        if (tenant != null && tenant.getPaymentMethod() != null) {
            if (comments.length() > 0) comments.append("; ");
            comments.append("Payment: ").append(tenant.getPaymentMethod());
        }

        // Add transaction-specific notes
        if (transactions.stream().anyMatch(t -> t.getDescription() != null && t.getDescription().contains("error"))) {
            if (comments.length() > 0) comments.append("; ");
            comments.append("Payment error - check transactions");
        }

        return comments.toString();
    }

    private List<Object> addOtherIncomeRow(String unitName, int currentRow) {
        // Create empty row for additional income sources (OFFICE, BUILDING, Parking)
        List<Object> row = new ArrayList<>();

        row.add(unitName);                                              // A - Unit No
        row.add("");                                                    // B - Tenant
        row.add("");                                                    // C - Tenancy Dates
        row.add(unitName.contains("Parking") ? "" : 30);              // D - Rent Due Date
        row.add("");                                                    // E - Rent Due Amount
        row.add("");                                                    // F - Rent Received Date
        row.add(false);                                                 // G - Paid to Robert Ellis
        row.add(false);                                                 // H - Paid to Old Account
        row.add(false);                                                 // I - Paid to PayProp
        row.add("");                                                    // J - Rent Received Amount
        row.add("=J" + currentRow + "*I" + currentRow);                // K - Amount via PayProp
        row.add("=H" + currentRow + "*J" + currentRow);                // L - Amount via Old Account
        row.add("=(J" + currentRow + "*H" + currentRow + ")+(J" + currentRow + "*I" + currentRow + ")"); // M - Total
        row.add("10%");                                                 // N - Management Fee %
        row.add("=J" + currentRow + "*-0.1");                          // O - Management Fee £
        row.add("5%");                                                  // P - Service Fee %
        row.add("=J" + currentRow + "*-0.05");                         // Q - Service Fee £
        row.add("=O" + currentRow + "+Q" + currentRow);                // R - Total Fees

        // Expenses (S-AD) - empty for other income
        for (int i = 0; i < 12; i++) {
            row.add("");
        }

        row.add("=-T" + currentRow + "+-W" + currentRow + "+-Z" + currentRow + "+-AC" + currentRow); // AE - Total Expenses
        row.add("=J" + currentRow + "+R" + currentRow + "+AE" + currentRow);   // AF - Net Due
        row.add("=AF" + currentRow + "*H" + currentRow);                       // AG - Net from Old Account
        row.add("=AF" + currentRow + "*I" + currentRow);                       // AH - Net from PayProp
        row.add("=AG" + currentRow + "+AH" + currentRow);                      // AI - Net Total
        row.add("");                                                           // AJ - Date Paid
        row.add("=E" + currentRow + "-J" + currentRow);                       // AK - Outstanding
        row.add(unitName.equals("OFFICE") ? "Vacant" : "");                   // AL - Comments

        return row;
    }

    private List<Object> createTotalRow(int dataStartRow, int dataEndRow) {
        List<Object> totalRow = new ArrayList<>();

        totalRow.add("TOTAL");                                          // A
        totalRow.add("");                                               // B
        totalRow.add("");                                               // C
        totalRow.add("=SUM(E" + dataStartRow + ":E" + dataEndRow + ")"); // D - Total Rent Due
        totalRow.add("");                                               // E
        totalRow.add("");                                               // F
        totalRow.add("");                                               // G
        totalRow.add("");                                               // H
        totalRow.add("");                                               // I
        totalRow.add("=SUM(J" + dataStartRow + ":J" + dataEndRow + ")"); // J - Total Rent Received
        totalRow.add("=SUM(K" + dataStartRow + ":K" + dataEndRow + ")"); // K - Total PayProp
        totalRow.add("=SUM(L" + dataStartRow + ":L" + dataEndRow + ")"); // L - Total Old Account
        totalRow.add("=SUM(M" + dataStartRow + ":M" + dataEndRow + ")"); // M - Total Received
        totalRow.add("");                                               // N
        totalRow.add("=SUM(O" + dataStartRow + ":O" + dataEndRow + ")"); // O - Total Management Fees
        totalRow.add("");                                               // P
        totalRow.add("=SUM(Q" + dataStartRow + ":Q" + dataEndRow + ")"); // Q - Total Service Fees
        totalRow.add("=SUM(R" + dataStartRow + ":R" + dataEndRow + ")"); // R - Total All Fees

        // Expense totals (S-AD)
        totalRow.add("");                                               // S
        totalRow.add("=SUM(T" + dataStartRow + ":T" + dataEndRow + ")"); // T - Total Expense 1
        totalRow.add("");                                               // U
        totalRow.add("");                                               // V
        totalRow.add("=SUM(W" + dataStartRow + ":W" + dataEndRow + ")"); // W - Total Expense 2
        totalRow.add("");                                               // X
        totalRow.add("");                                               // Y
        totalRow.add("=SUM(Z" + dataStartRow + ":Z" + dataEndRow + ")"); // Z - Total Expense 3
        totalRow.add("");                                               // AA
        totalRow.add("");                                               // AB
        totalRow.add("=SUM(AC" + dataStartRow + ":AC" + dataEndRow + ")"); // AC - Total Expense 4
        totalRow.add("");                                               // AD

        totalRow.add("=SUM(AE" + dataStartRow + ":AE" + dataEndRow + ")"); // AE - Total All Expenses
        totalRow.add("=SUM(AF" + dataStartRow + ":AF" + dataEndRow + ")"); // AF - Total Net Due
        totalRow.add("=SUM(AG" + dataStartRow + ":AG" + dataEndRow + ")"); // AG - Total Net Old Account
        totalRow.add("=SUM(AH" + dataStartRow + ":AH" + dataEndRow + ")"); // AH - Total Net PayProp
        totalRow.add("=SUM(AI" + dataStartRow + ":AI" + dataEndRow + ")"); // AI - Total Net All
        totalRow.add("");                                               // AJ
        totalRow.add("=SUM(AK" + dataStartRow + ":AK" + dataEndRow + ")"); // AK - Total Outstanding
        totalRow.add("");                                               // AL

        return totalRow;
    }

    // Helper methods for statement building
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

    // Tenant and Portfolio statement building methods
    private TenantStatementData buildTenantStatementData(Customer tenant, LocalDate fromDate, LocalDate toDate) {
        TenantStatementData data = new TenantStatementData();
        data.setTenant(tenant);
        data.setFromDate(fromDate);
        data.setToDate(toDate);

        Property property = propertyService.getPropertyByTenant(tenant.getCustomerId());
        data.setProperty(property);

        data.setRentDue(calculateRentDue(tenant, fromDate, toDate));
        data.setPaymentsMade(calculatePaymentsMade(tenant, fromDate, toDate));
        data.setBalance(data.getRentDue().subtract(data.getPaymentsMade()));

        return data;
    }

    private PortfolioStatementData buildPortfolioStatementData(Customer propertyOwner, LocalDate fromDate, LocalDate toDate) {
        PortfolioStatementData data = new PortfolioStatementData();
        data.setPropertyOwner(propertyOwner);
        data.setFromDate(fromDate);
        data.setToDate(toDate);

        List<Property> properties = propertyService.getPropertiesByOwner(propertyOwner.getCustomerId());

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

    private List<List<Object>> buildTenantStatementValues(TenantStatementData data) {
        List<List<Object>> values = new ArrayList<>();

        values.add(Arrays.asList("TENANT STATEMENT"));
        values.add(Arrays.asList(""));
        values.add(Arrays.asList("Tenant:", data.getTenant().getName()));
        values.add(Arrays.asList("Email:", data.getTenant().getEmail()));
        values.add(Arrays.asList("Property:", data.getProperty().getPropertyName()));
        values.add(Arrays.asList("Period:", data.getFromDate().toString() + " to " + data.getToDate().toString()));
        values.add(Arrays.asList("Generated:", LocalDate.now().toString()));
        values.add(Arrays.asList(""));

        values.add(Arrays.asList("PAYMENT DETAILS"));
        values.add(Arrays.asList("Rent Due:", "£" + data.getRentDue().toString()));
        values.add(Arrays.asList("Payments Made:", "£" + data.getPaymentsMade().toString()));
        values.add(Arrays.asList("Balance:", "£" + data.getBalance().toString()));

        return values;
    }

    private List<List<Object>> buildPortfolioStatementValues(PortfolioStatementData data) {
        List<List<Object>> values = new ArrayList<>();

        values.add(Arrays.asList("PORTFOLIO STATEMENT"));
        values.add(Arrays.asList(""));
        values.add(Arrays.asList("Property Owner:", data.getPropertyOwner().getName()));
        values.add(Arrays.asList("Period:", data.getFromDate().toString() + " to " + data.getToDate().toString()));
        values.add(Arrays.asList("Generated:", LocalDate.now().toString()));
        values.add(Arrays.asList(""));

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
                "Occupied"
            ));
        }

        values.add(Arrays.asList(""));
        values.add(Arrays.asList("TOTALS"));
        values.add(Arrays.asList("Total Rent:", "£" + totalRent.toString()));
        values.add(Arrays.asList("Total Expenses:", "£" + totalExpenses.toString()));
        values.add(Arrays.asList("Net Income:", "£" + totalRent.subtract(totalExpenses).toString()));

        return values;
    }

    private BigDecimal calculateRentDue(Customer tenant, LocalDate fromDate, LocalDate toDate) {
        if (tenant.getMonthlyRent() != null) {
            return tenant.getMonthlyRent();
        }

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

    private BigDecimal calculatePropertyRent(Property property, LocalDate fromDate, LocalDate toDate) {
        if (property.getMonthlyPayment() == null) {
            return BigDecimal.ZERO;
        }

        return property.getMonthlyPayment();
    }

    // Data classes
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
        private Integer rentDueDay;
        private BigDecimal managementFeePercentage;
        private BigDecimal serviceFeePercentage;
        private Boolean paidToRobertEllis;
        private Boolean paidToOldAccount;
        private Boolean paidToPayProp;
        private BigDecimal rentReceived;
        private BigDecimal amountReceivedPayProp;
        private BigDecimal amountReceivedOldAccount;
        private List<ExpenseItem> expenses;

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

        public Integer getRentDueDay() { return rentDueDay; }
        public void setRentDueDay(Integer rentDueDay) { this.rentDueDay = rentDueDay; }

        public Boolean getPaidToRobertEllis() { return paidToRobertEllis; }
        public void setPaidToRobertEllis(Boolean paidToRobertEllis) { this.paidToRobertEllis = paidToRobertEllis; }

        public Boolean getPaidToOldAccount() { return paidToOldAccount; }
        public void setPaidToOldAccount(Boolean paidToOldAccount) { this.paidToOldAccount = paidToOldAccount; }

        public Boolean getPaidToPayProp() { return paidToPayProp; }
        public void setPaidToPayProp(Boolean paidToPayProp) { this.paidToPayProp = paidToPayProp; }

        public BigDecimal getRentReceived() { return rentReceived; }
        public void setRentReceived(BigDecimal rentReceived) { this.rentReceived = rentReceived; }

        public BigDecimal getAmountReceivedPayProp() { return amountReceivedPayProp; }
        public void setAmountReceivedPayProp(BigDecimal amountReceivedPayProp) { this.amountReceivedPayProp = amountReceivedPayProp; }

        public BigDecimal getAmountReceivedOldAccount() { return amountReceivedOldAccount; }
        public void setAmountReceivedOldAccount(BigDecimal amountReceivedOldAccount) { this.amountReceivedOldAccount = amountReceivedOldAccount; }

        public List<ExpenseItem> getExpenses() { return expenses; }
        public void setExpenses(List<ExpenseItem> expenses) { this.expenses = expenses; }
    }

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

    // ExpenseItem class for tracking individual expenses
    public static class ExpenseItem {
        private String label;
        private BigDecimal amount;
        private String comment;

        public ExpenseItem() {}

        public ExpenseItem(String label, BigDecimal amount, String comment) {
            this.label = label;
            this.amount = amount;
            this.comment = comment;
        }

        // Getters and setters
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }

        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
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
}
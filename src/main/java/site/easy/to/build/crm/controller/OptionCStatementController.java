package site.easy.to.build.crm.controller;

import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.service.statement.ExcelStatementGeneratorService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * REST API Controller for Option C statement generation
 *
 * OPTION C APPROACH:
 * - Extracts raw data from database
 * - Generates Excel files with FORMULAS (not calculated values)
 * - All calculations done in Excel (transparent, auditable)
 * - Users can see and modify formulas
 *
 * SEPARATE from existing StatementController to:
 * - Avoid conflicts with existing Google Sheets/XLSX services
 * - Provide clean Option C implementation
 * - Allow gradual migration from existing system
 *
 * Endpoints:
 * - GET /api/statements/option-c/owner/{customerId}/excel - Statement for specific customer
 * - GET /api/statements/option-c/all/excel - Statement for all customers
 * - GET /api/statements/option-c/health - Health check
 */
@RestController
@RequestMapping("/api/statements/option-c")
public class OptionCStatementController {

    private static final Logger log = LoggerFactory.getLogger(OptionCStatementController.class);

    @Autowired
    private ExcelStatementGeneratorService excelGenerator;

    /**
     * Log current memory usage for debugging
     */
    private void logMemoryUsage(String phase) {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        log.info("📊 MEMORY [{}]: Used={}MB, Max={}MB", phase, usedMemory, maxMemory);
    }

    /**
     * Generate owner statement for specific customer (Excel with formulas)
     *
     * Example: GET /api/statements/option-c/owner/123/excel?startDate=2025-01-01&endDate=2025-12-31&periodStartDay=22
     *
     * @param customerId Customer ID
     * @param startDate Statement period start (format: yyyy-MM-dd)
     * @param endDate Statement period end (format: yyyy-MM-dd)
     * @param periodStartDay Day of month when period starts (1-31). Default=1 (calendar months). Use 22 for 22nd-21st periods.
     * @return Excel file download with formulas
     */
    @GetMapping("/owner/{customerId}/excel")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'PROPERTY_OWNER', 'ADMIN', 'MANAGER')")
    public ResponseEntity<byte[]> generateOwnerStatement(
            @PathVariable Long customerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") Integer periodStartDay) {

        long requestStart = System.currentTimeMillis();
        log.info("🚀 OPTION C REQUEST START: Customer {} from {} to {} (periodStartDay: {})",
                 customerId, startDate, endDate, periodStartDay);
        logMemoryUsage("REQUEST_START");

        Workbook workbook = null;
        ByteArrayOutputStream out = null;

        try {
            // Generate workbook with formulas (Option C)
            log.info("📄 Generating workbook...");
            long genStart = System.currentTimeMillis();

            if (periodStartDay == 1) {
                // Use calendar months (existing behavior)
                workbook = excelGenerator.generateStatementForCustomer(customerId, startDate, endDate);
            } else {
                // Use custom periods (e.g., 22nd-21st)
                workbook = excelGenerator.generateStatementForCustomerWithCustomPeriods(
                    customerId, startDate, endDate, periodStartDay);
            }

            log.info("✅ Workbook generated in {}ms with {} sheets",
                System.currentTimeMillis() - genStart, workbook.getNumberOfSheets());
            logMemoryUsage("WORKBOOK_GENERATED");

            // Convert to byte array
            log.info("📤 Converting workbook to byte array...");
            long writeStart = System.currentTimeMillis();
            out = new ByteArrayOutputStream();
            workbook.write(out);
            byte[] excelBytes = out.toByteArray();
            log.info("✅ Converted to {} bytes in {}ms", excelBytes.length, System.currentTimeMillis() - writeStart);
            logMemoryUsage("BYTES_WRITTEN");

            // Prepare HTTP response
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

            String filename = String.format("statement_optionc_customer_%d_%s_%s.xlsx",
                    customerId,
                    startDate.format(DateTimeFormatter.ISO_DATE),
                    endDate.format(DateTimeFormatter.ISO_DATE));

            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(excelBytes.length);

            long totalTime = System.currentTimeMillis() - requestStart;
            log.info("🏁 OPTION C REQUEST COMPLETE: Customer {}, {} sheets, {} bytes in {}ms ({}s)",
                    customerId, workbook.getNumberOfSheets(), excelBytes.length, totalTime, totalTime / 1000);
            logMemoryUsage("REQUEST_COMPLETE");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(excelBytes);

        } catch (IOException e) {
            log.error("❌ Option C IO ERROR for customer {}: {} at {}",
                    customerId, e.getMessage(), e.getClass().getName());
            log.error("Stack trace:", e);
            logMemoryUsage("IO_ERROR");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();

        } catch (OutOfMemoryError e) {
            log.error("❌ Option C OUT OF MEMORY for customer {}: {}", customerId, e.getMessage());
            logMemoryUsage("OOM_ERROR");
            // Try to free up memory
            if (workbook != null) {
                try { workbook.close(); } catch (Exception ignored) {}
            }
            System.gc();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();

        } catch (Exception e) {
            log.error("❌ Option C UNEXPECTED ERROR for customer {}: {} ({})",
                    customerId, e.getMessage(), e.getClass().getName());
            log.error("Stack trace:", e);
            logMemoryUsage("UNEXPECTED_ERROR");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();

        } finally {
            // Ensure resources are closed
            if (out != null) {
                try { out.close(); } catch (Exception ignored) {}
            }
            if (workbook != null) {
                try { workbook.close(); } catch (Exception ignored) {}
            }
            log.info("🧹 Resources cleaned up for customer {}", customerId);
        }
    }

    /**
     * Generate statement for all customers (Excel with formulas)
     *
     * Example: GET /api/statements/option-c/all/excel?startDate=2025-01-01&endDate=2025-12-31&periodStartDay=22
     *
     * @param startDate Statement period start (format: yyyy-MM-dd)
     * @param endDate Statement period end (format: yyyy-MM-dd)
     * @param periodStartDay Day of month when period starts (1-31). Default=1 (calendar months). Use 22 for 22nd-21st periods.
     * @return Excel file download with formulas
     */
    @GetMapping("/all/excel")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN', 'MANAGER')")
    public ResponseEntity<byte[]> generateAllStatements(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") Integer periodStartDay) {

        log.info("📊 Option C: Generating Excel statement for all customers from {} to {} (period start day: {})",
                 startDate, endDate, periodStartDay);

        try {
            // Generate workbook with formulas (all customers)
            Workbook workbook;
            if (periodStartDay == 1) {
                // Use calendar months (existing behavior)
                workbook = excelGenerator.generateStatement(startDate, endDate);
            } else {
                // Use custom periods (e.g., 22nd-21st)
                workbook = excelGenerator.generateStatementWithCustomPeriods(startDate, endDate, periodStartDay);
            }

            // Convert to byte array
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            workbook.close();

            byte[] excelBytes = out.toByteArray();
            out.close();

            // Prepare HTTP response
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

            String filename = String.format("statement_optionc_all_customers_%s_%s.xlsx",
                    startDate.format(DateTimeFormatter.ISO_DATE),
                    endDate.format(DateTimeFormatter.ISO_DATE));

            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(excelBytes.length);

            log.info("✅ Option C: Successfully generated all-customer statement - {} bytes", excelBytes.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(excelBytes);

        } catch (IOException e) {
            log.error("❌ Option C: Error generating Excel statement for all customers: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Generate statement for current month (convenience endpoint)
     *
     * Example: GET /api/statements/option-c/owner/123/excel/current-month?periodStartDay=22
     *
     * @param customerId Customer ID
     * @param periodStartDay Optional period start day (default=1)
     * @return Excel file download for current month
     */
    @GetMapping("/owner/{customerId}/excel/current-month")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'PROPERTY_OWNER', 'ADMIN', 'MANAGER')")
    public ResponseEntity<byte[]> generateOwnerStatementCurrentMonth(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "1") Integer periodStartDay) {

        LocalDate now = LocalDate.now();
        LocalDate startDate = now.withDayOfMonth(1);
        LocalDate endDate = now.withDayOfMonth(now.lengthOfMonth());

        log.info("📊 Option C: Generating current month statement for customer {}: {} to {}",
                customerId, startDate, endDate);

        return generateOwnerStatement(customerId, startDate, endDate, periodStartDay);
    }

    /**
     * Generate statement for current year (convenience endpoint)
     *
     * Example: GET /api/statements/option-c/owner/123/excel/current-year?periodStartDay=22
     *
     * @param customerId Customer ID
     * @param periodStartDay Optional period start day (default=1)
     * @return Excel file download for current year
     */
    @GetMapping("/owner/{customerId}/excel/current-year")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'PROPERTY_OWNER', 'ADMIN', 'MANAGER')")
    public ResponseEntity<byte[]> generateOwnerStatementCurrentYear(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "1") Integer periodStartDay) {

        LocalDate now = LocalDate.now();
        LocalDate startDate = LocalDate.of(now.getYear(), 1, 1);
        LocalDate endDate = LocalDate.of(now.getYear(), 12, 31);

        log.info("📊 Option C: Generating current year statement for customer {}: {} to {}",
                customerId, startDate, endDate);

        return generateOwnerStatement(customerId, startDate, endDate, periodStartDay);
    }

    /**
     * Generate statement for last month (convenience endpoint)
     *
     * Example: GET /api/statements/option-c/owner/123/excel/last-month?periodStartDay=22
     *
     * @param customerId Customer ID
     * @param periodStartDay Optional period start day (default=1)
     * @return Excel file download for last month
     */
    @GetMapping("/owner/{customerId}/excel/last-month")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'PROPERTY_OWNER', 'ADMIN', 'MANAGER')")
    public ResponseEntity<byte[]> generateOwnerStatementLastMonth(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "1") Integer periodStartDay) {

        LocalDate now = LocalDate.now();
        LocalDate startDate = now.minusMonths(1).withDayOfMonth(1);
        LocalDate endDate = now.minusMonths(1).withDayOfMonth(startDate.lengthOfMonth());

        log.info("📊 Option C: Generating last month statement for customer {}: {} to {}",
                customerId, startDate, endDate);

        return generateOwnerStatement(customerId, startDate, endDate, periodStartDay);
    }

    /**
     * Health check endpoint for Option C statement generation
     *
     * Example: GET /api/statements/option-c/health
     *
     * @return Health status with approach description
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok(
            "✅ Option C Statement Generation Service is running\n\n" +
            "Approach: Data Extract + Excel Formulas\n" +
            "- Extracts raw data from database\n" +
            "- Generates Excel with FORMULAS (not calculated values)\n" +
            "- All calculations done in Excel (transparent, auditable)\n" +
            "- Users can see, verify, and modify formulas\n\n" +
            "Features:\n" +
            "- 100% invoice linking (all rent payments linked to leases)\n" +
            "- Pro-rating formulas with accurate calendar-day calculations\n" +
            "- Custom reporting periods (e.g., 22nd-21st) with periodStartDay parameter\n" +
            "- Commission calculations (10% management + 5% service = 15%)\n" +
            "- Arrears tracking with running totals\n" +
            "- Multiple sheets: LEASE_MASTER, TRANSACTIONS, RENT_DUE, RENT_RECEIVED, MONTHLY_STATEMENT\n\n" +
            "Endpoints:\n" +
            "- GET /api/statements/option-c/owner/{customerId}/excel?startDate={date}&endDate={date}&periodStartDay={day}\n" +
            "- GET /api/statements/option-c/all/excel?startDate={date}&endDate={date}&periodStartDay={day}\n" +
            "- GET /api/statements/option-c/owner/{customerId}/excel/current-month\n" +
            "- GET /api/statements/option-c/owner/{customerId}/excel/current-year\n" +
            "- GET /api/statements/option-c/owner/{customerId}/excel/last-month\n\n" +
            "Custom Period Examples:\n" +
            "- periodStartDay=1 (default): Calendar months (1st-31st/30th/28th)\n" +
            "- periodStartDay=22: Custom periods (22nd-21st)\n" +
            "- Pro-rating uses actual calendar days (not fixed 30-day months)"
        );
    }
}

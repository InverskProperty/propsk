package site.easy.to.build.crm.controller;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import site.easy.to.build.crm.service.statement.FormulaAuditStatementService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * REST API Controller for Formula Audit statement generation.
 *
 * This generates a formula-rich Excel workbook designed for auditing:
 * - Fixed lease rows (same position across all monthly sheets)
 * - Excel formulas instead of hardcoded values
 * - TOTALS sheet with cross-sheet references
 * - Fewer, cleaner sheets
 */
@RestController
@RequestMapping("/api/statements/formula-audit")
public class FormulaAuditStatementController {

    private static final Logger log = LoggerFactory.getLogger(FormulaAuditStatementController.class);

    @Autowired
    private FormulaAuditStatementService formulaAuditService;

    @GetMapping("/owner/{customerId}/excel")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'PROPERTY_OWNER', 'ADMIN', 'MANAGER')")
    public ResponseEntity<byte[]> generateFormulaAuditStatement(
            @PathVariable Long customerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") Integer periodStartDay,
            @RequestParam(defaultValue = "QUARTERLY") String statementFrequency) {

        long requestStart = System.currentTimeMillis();
        log.info("FORMULA AUDIT REQUEST: Customer {} from {} to {} (periodStartDay: {}, frequency: {})",
                customerId, startDate, endDate, periodStartDay, statementFrequency);

        Workbook workbook = null;
        ByteArrayOutputStream out = null;

        try {
            workbook = formulaAuditService.generateFormulaAuditStatement(
                    customerId, startDate, endDate, periodStartDay, statementFrequency);

            out = new ByteArrayOutputStream();
            workbook.write(out);
            byte[] excelBytes = out.toByteArray();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

            String filename = String.format("formula_audit_customer_%d_%s_%s.xlsx",
                    customerId,
                    startDate.format(DateTimeFormatter.ISO_DATE),
                    endDate.format(DateTimeFormatter.ISO_DATE));

            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(excelBytes.length);

            long totalTime = System.currentTimeMillis() - requestStart;
            log.info("FORMULA AUDIT COMPLETE: Customer {}, {} sheets, {} bytes in {}ms",
                    customerId, workbook.getNumberOfSheets(), excelBytes.length, totalTime);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(excelBytes);

        } catch (IOException e) {
            log.error("Formula Audit IO ERROR for customer {}: {}", customerId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();

        } catch (OutOfMemoryError e) {
            log.error("Formula Audit OOM for customer {}: {}", customerId, e.getMessage());
            if (workbook != null) {
                try { workbook.close(); } catch (Exception ignored) {}
            }
            System.gc();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();

        } catch (Exception e) {
            log.error("Formula Audit ERROR for customer {}: {} ({})",
                    customerId, e.getMessage(), e.getClass().getName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();

        } finally {
            if (out != null) {
                try { out.close(); } catch (Exception ignored) {}
            }
            if (workbook != null) {
                try {
                    if (workbook instanceof SXSSFWorkbook) {
                        ((SXSSFWorkbook) workbook).dispose();
                        log.info("SXSSF temp files disposed");
                    }
                    workbook.close();
                } catch (Exception ignored) {}
            }
        }
    }
}

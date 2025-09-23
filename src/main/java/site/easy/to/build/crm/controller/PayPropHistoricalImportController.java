package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.service.payprop.PayPropHistoricalDataImportService;
import site.easy.to.build.crm.service.payprop.SpreadsheetToPayPropFormatService;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for PayProp historical data import functionality
 *
 * Provides endpoints for:
 * - Uploading and importing property management spreadsheets
 * - Converting spreadsheets to PayProp CSV format
 * - Importing PayProp-compatible CSV files
 * - Viewing import results and validation
 */
@Controller
@RequestMapping("/admin/payprop-import")
public class PayPropHistoricalImportController {

    private static final Logger logger = LoggerFactory.getLogger(PayPropHistoricalImportController.class);

    @Autowired
    private PayPropHistoricalDataImportService importService;

    @Autowired
    private SpreadsheetToPayPropFormatService transformService;

    /**
     * Show import interface
     */
    @GetMapping
    public String showImportPage(Model model) {
        model.addAttribute("pageTitle", "PayProp Historical Data Import");
        return "admin/payprop-import";
    }

    /**
     * Import data from property management spreadsheet
     */
    @PostMapping("/upload-spreadsheet")
    public String uploadSpreadsheet(
            @RequestParam("file") MultipartFile file,
            @RequestParam("period") String period,
            @AuthenticationPrincipal User currentUser,
            RedirectAttributes redirectAttributes) {

        try {
            logger.info("Processing spreadsheet upload: {} for period: {}", file.getOriginalFilename(), period);

            // Validate file
            if (file.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Please select a file to upload");
                return "redirect:/admin/payprop-import";
            }

            if (!isValidSpreadsheetFile(file)) {
                redirectAttributes.addFlashAttribute("error",
                    "Invalid file type. Please upload a CSV or Excel file (.csv, .xlsx)");
                return "redirect:/admin/payprop-import";
            }

            // Validate period
            if (period == null || period.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Period information is required");
                return "redirect:/admin/payprop-import";
            }

            // Process import
            PayPropHistoricalDataImportService.ImportResult result =
                importService.importFromSpreadsheet(file, period, currentUser);

            if (result.success) {
                redirectAttributes.addFlashAttribute("success",
                    String.format("Spreadsheet imported successfully! %s", result.getSummary()));

                if (!result.warnings.isEmpty()) {
                    redirectAttributes.addFlashAttribute("warnings", result.warnings);
                }
            } else {
                redirectAttributes.addFlashAttribute("error",
                    String.format("Import failed: %s", result.getSummary()));
                redirectAttributes.addFlashAttribute("errors", result.errors);
            }

        } catch (Exception e) {
            logger.error("Spreadsheet upload failed: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                "Upload failed: " + e.getMessage());
        }

        return "redirect:/admin/payprop-import";
    }

    /**
     * Import PayProp-compatible CSV file
     */
    @PostMapping("/upload-csv")
    public String uploadCsv(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser,
            RedirectAttributes redirectAttributes) {

        try {
            logger.info("Processing CSV upload: {}", file.getOriginalFilename());

            // Validate file
            if (file.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Please select a CSV file to upload");
                return "redirect:/admin/payprop-import";
            }

            if (!isValidCsvFile(file)) {
                redirectAttributes.addFlashAttribute("error",
                    "Invalid file type. Please upload a CSV file (.csv)");
                return "redirect:/admin/payprop-import";
            }

            // Process import
            PayPropHistoricalDataImportService.ImportResult result =
                importService.importFromPayPropCsv(file, currentUser);

            if (result.success) {
                redirectAttributes.addFlashAttribute("success",
                    String.format("CSV imported successfully! %s", result.getSummary()));

                if (!result.warnings.isEmpty()) {
                    redirectAttributes.addFlashAttribute("warnings", result.warnings);
                }
            } else {
                redirectAttributes.addFlashAttribute("error",
                    String.format("Import failed: %s", result.getSummary()));
                redirectAttributes.addFlashAttribute("errors", result.errors);
            }

        } catch (Exception e) {
            logger.error("CSV upload failed: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                "Upload failed: " + e.getMessage());
        }

        return "redirect:/admin/payprop-import";
    }

    /**
     * Convert spreadsheet to PayProp CSV format for preview/download
     */
    @PostMapping("/convert-to-csv")
    @ResponseBody
    public ResponseEntity<?> convertToCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam("period") String period) {

        try {
            logger.info("Converting spreadsheet to CSV: {} for period: {}", file.getOriginalFilename(), period);

            // Validate inputs
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(
                    Map.of("error", "Please select a file to convert"));
            }

            if (period == null || period.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                    Map.of("error", "Period information is required"));
            }

            // Transform to PayProp CSV
            String csvData = transformService.transformSpreadsheetToPayPropCsv(file, period);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("csvData", csvData);
            response.put("filename", generateCsvFilename(file.getOriginalFilename(), period));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Conversion failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(
                Map.of("error", "Conversion failed: " + e.getMessage()));
        }
    }

    /**
     * Download CSV template for manual data entry
     */
    @GetMapping("/download-template")
    @ResponseBody
    public ResponseEntity<byte[]> downloadTemplate() {
        String template = generateCsvTemplate();

        return ResponseEntity.ok()
            .header("Content-Type", "text/csv")
            .header("Content-Disposition", "attachment; filename=\"payprop_import_template.csv\"")
            .body(template.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Download converted CSV file (following your existing download patterns)
     */
    @PostMapping("/download-converted-csv")
    @ResponseBody
    public ResponseEntity<byte[]> downloadConvertedCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam("period") String period) {

        try {
            logger.info("Generating CSV download for: {} (period: {})", file.getOriginalFilename(), period);

            // Transform to PayProp CSV
            String csvData = transformService.transformSpreadsheetToPayPropCsv(file, period);
            String filename = generateCsvFilename(file.getOriginalFilename(), period);

            return ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(csvData.getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            logger.error("CSV download failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                .header("Content-Type", "text/plain")
                .body(("Download failed: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Validate import data before processing (AJAX endpoint)
     */
    @PostMapping("/validate")
    @ResponseBody
    public ResponseEntity<?> validateImportData(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "period", required = false) String period) {

        try {
            Map<String, Object> validation = new HashMap<>();

            // File validation
            if (file.isEmpty()) {
                validation.put("fileValid", false);
                validation.put("fileError", "No file selected");
            } else {
                validation.put("fileValid", true);
                validation.put("fileName", file.getOriginalFilename());
                validation.put("fileSize", file.getSize());
            }

            // Period validation for spreadsheets
            if (period != null && !period.trim().isEmpty()) {
                validation.put("periodValid", true);
                validation.put("period", period);
            } else if (isSpreadsheetFile(file)) {
                validation.put("periodValid", false);
                validation.put("periodError", "Period is required for spreadsheet imports");
            }

            // Quick data preview (first few lines)
            if (!file.isEmpty()) {
                try {
                    String preview = getFilePreview(file);
                    validation.put("preview", preview);
                } catch (Exception e) {
                    validation.put("previewError", "Could not preview file: " + e.getMessage());
                }
            }

            return ResponseEntity.ok(validation);

        } catch (Exception e) {
            logger.error("Validation failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(
                Map.of("error", "Validation failed: " + e.getMessage()));
        }
    }

    // ===== UTILITY METHODS =====

    private boolean isValidSpreadsheetFile(MultipartFile file) {
        if (file == null || file.isEmpty()) return false;

        String filename = file.getOriginalFilename();
        if (filename == null) return false;

        String lowercaseFilename = filename.toLowerCase();
        return lowercaseFilename.endsWith(".csv") ||
               lowercaseFilename.endsWith(".xlsx") ||
               lowercaseFilename.endsWith(".xls");
    }

    private boolean isValidCsvFile(MultipartFile file) {
        if (file == null || file.isEmpty()) return false;

        String filename = file.getOriginalFilename();
        if (filename == null) return false;

        return filename.toLowerCase().endsWith(".csv");
    }

    private boolean isSpreadsheetFile(MultipartFile file) {
        if (file == null || file.isEmpty()) return false;

        String filename = file.getOriginalFilename();
        if (filename == null) return false;

        String lowercaseFilename = filename.toLowerCase();
        return lowercaseFilename.endsWith(".xlsx") || lowercaseFilename.endsWith(".xls");
    }

    private String generateCsvFilename(String originalFilename, String period) {
        String baseName = originalFilename.replaceAll("\\.[^.]+$", ""); // Remove extension
        String cleanPeriod = period.replaceAll("[^a-zA-Z0-9_-]", "_");
        return String.format("%s_%s_payprop.csv", baseName, cleanPeriod);
    }

    private String generateCsvTemplate() {
        StringBuilder template = new StringBuilder();
        template.append("transaction_date,amount,description,transaction_type,category,property_reference,customer_reference,bank_reference,payment_method,notes\n");
        template.append("2025-01-27,795.00,\"Rent payment - January\",deposit,rent,\"Flat 1 - 3 West Gate\",\"Ms O Smoliarenko\",\"RE-JAN-01\",\"Robert Ellis\",\"Monthly collection\"\n");
        template.append("2025-01-27,-119.25,\"Commission - January\",fee,commission,\"Flat 1 - 3 West Gate\",\"Ms O Smoliarenko\",\"COMM-JAN-01\",\"Robert Ellis\",\"15% management fee\"\n");
        template.append("2025-02-15,-450.00,\"Emergency plumber\",payment,maintenance,\"Flat 2 - 3 West Gate\",\"\",\"MAINT-001\",\"Direct Payment\",\"Burst pipe repair\"\n");
        return template.toString();
    }

    private String getFilePreview(MultipartFile file) throws Exception {
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(file.getInputStream()))) {

            StringBuilder preview = new StringBuilder();
            String line;
            int lineCount = 0;
            int maxLines = 5;

            while ((line = reader.readLine()) != null && lineCount < maxLines) {
                preview.append(line).append("\n");
                lineCount++;
            }

            return preview.toString();
        }
    }
}
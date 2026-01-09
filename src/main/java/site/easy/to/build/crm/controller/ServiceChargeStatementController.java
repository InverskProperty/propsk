package site.easy.to.build.crm.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.dto.servicecharge.ServiceChargeStatementDTO;
import site.easy.to.build.crm.entity.Block;
import site.easy.to.build.crm.service.servicecharge.ServiceChargeStatementService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Controller for Service Charge Statements.
 * Allows viewing and exporting service charge statements for block properties.
 */
@Controller
@RequestMapping("/statements/service-charge")
public class ServiceChargeStatementController {

    private static final Logger log = LoggerFactory.getLogger(ServiceChargeStatementController.class);

    @Autowired
    private ServiceChargeStatementService serviceChargeStatementService;

    /**
     * Show the statement selection page.
     * Lists available blocks and allows period selection.
     */
    @GetMapping("")
    public String showSelectionPage(Model model) {
        log.info("Showing service charge statement selection page");

        List<Block> blocks = serviceChargeStatementService.getBlocksWithBlockProperty();
        model.addAttribute("blocks", blocks);

        // Default period: current month
        LocalDate now = LocalDate.now();
        LocalDate defaultStart = now.withDayOfMonth(1);
        LocalDate defaultEnd = now;

        model.addAttribute("defaultStart", defaultStart);
        model.addAttribute("defaultEnd", defaultEnd);

        return "statements/service-charge/select";
    }

    /**
     * View a service charge statement.
     */
    @GetMapping("/view")
    public String viewStatement(
            @RequestParam Long blockId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
            Model model) {

        log.info("Viewing service charge statement for block {} from {} to {}",
                blockId, periodStart, periodEnd);

        try {
            ServiceChargeStatementDTO statement = serviceChargeStatementService.generateStatement(
                    blockId, periodStart, periodEnd);

            if (statement.getBlockId() == null) {
                model.addAttribute("error", "Block not found or has no block property");
                return "redirect:/statements/service-charge";
            }

            model.addAttribute("statement", statement);
            model.addAttribute("blockId", blockId);
            model.addAttribute("periodStart", periodStart);
            model.addAttribute("periodEnd", periodEnd);

            return "statements/service-charge/view";
        } catch (Exception e) {
            log.error("Error generating statement: {}", e.getMessage(), e);
            model.addAttribute("error", "Could not generate statement: " + e.getMessage());
            return "redirect:/statements/service-charge";
        }
    }

    /**
     * Export statement as PDF.
     */
    @GetMapping("/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam Long blockId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {

        log.info("Exporting PDF for block {} from {} to {}", blockId, periodStart, periodEnd);

        try {
            byte[] pdfBytes = serviceChargeStatementService.exportToPdf(blockId, periodStart, periodEnd);

            // Generate filename
            String filename = String.format("ServiceChargeStatement_%s_%s.pdf",
                    periodStart.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                    periodEnd.format(DateTimeFormatter.ofPattern("yyyyMMdd")));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(pdfBytes.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);
        } catch (Exception e) {
            log.error("Error exporting PDF: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "text/plain")
                    .body(("Error generating PDF: " + e.getMessage()).getBytes());
        }
    }

    /**
     * API endpoint to get statement data as JSON.
     */
    @GetMapping("/api/statement")
    @ResponseBody
    public ResponseEntity<ServiceChargeStatementDTO> getStatementJson(
            @RequestParam Long blockId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {

        log.info("API: Getting statement for block {} from {} to {}", blockId, periodStart, periodEnd);

        try {
            ServiceChargeStatementDTO statement = serviceChargeStatementService.generateStatement(
                    blockId, periodStart, periodEnd);
            return ResponseEntity.ok(statement);
        } catch (Exception e) {
            log.error("Error getting statement: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

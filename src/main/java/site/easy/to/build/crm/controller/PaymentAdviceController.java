package site.easy.to.build.crm.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.dto.paymentadvice.PaymentAdviceDTO;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerType;
import site.easy.to.build.crm.entity.PaymentBatch;
import site.easy.to.build.crm.repository.CustomerRepository;
import site.easy.to.build.crm.service.payment.PaymentAdviceService;

import java.util.List;

/**
 * Controller for Payment Advice generation and viewing.
 * Allows owners to view detailed breakdowns of their payments.
 */
@Controller
@RequestMapping("/owner/payment-advice")
public class PaymentAdviceController {

    private static final Logger log = LoggerFactory.getLogger(PaymentAdviceController.class);

    @Autowired
    private PaymentAdviceService paymentAdviceService;

    @Autowired
    private CustomerRepository customerRepository;

    /**
     * List available payment batches for selection.
     * Can filter by owner.
     */
    @GetMapping("")
    public String listPaymentBatches(
            @RequestParam(required = false) Long ownerId,
            Model model) {

        log.info("Listing payment batches, ownerId: {}", ownerId);

        // Get owners for dropdown filter
        List<Customer> owners = customerRepository.findByCustomerType(CustomerType.PROPERTY_OWNER);
        model.addAttribute("owners", owners);
        model.addAttribute("selectedOwnerId", ownerId);

        // Get batches for selected owner (or all if none selected)
        List<PaymentBatch> batches;
        if (ownerId != null) {
            batches = paymentAdviceService.getBatchesForOwner(ownerId);
            Customer selectedOwner = customerRepository.findById(ownerId).orElse(null);
            model.addAttribute("selectedOwner", selectedOwner);
        } else {
            // If no owner selected, show message to select one
            batches = List.of();
        }
        model.addAttribute("batches", batches);

        return "owner/payment-advice/list";
    }

    /**
     * View a single payment advice for a batch.
     */
    @GetMapping("/{batchId}")
    public String viewPaymentAdvice(
            @PathVariable String batchId,
            Model model) {

        log.info("Viewing payment advice for batch: {}", batchId);

        try {
            PaymentAdviceDTO advice = paymentAdviceService.generatePaymentAdvice(batchId);
            model.addAttribute("advice", advice);
            return "owner/payment-advice/view";
        } catch (Exception e) {
            log.error("Error generating payment advice for batch {}: {}", batchId, e.getMessage());
            model.addAttribute("error", "Could not generate payment advice: " + e.getMessage());
            return "redirect:/owner/payment-advice";
        }
    }

    /**
     * Export payment advice as PDF.
     * For now, redirects to the print view which can be printed to PDF.
     */
    @GetMapping("/{batchId}/pdf")
    public ResponseEntity<byte[]> exportPdf(@PathVariable String batchId) {
        log.info("Exporting PDF for batch: {}", batchId);

        try {
            // For now, return the Excel file as PDF is more complex to generate
            // Users can use browser print to PDF for the view page
            byte[] excelBytes = paymentAdviceService.exportToExcel(batchId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment",
                "PaymentAdvice_" + batchId + ".pdf");

            // Note: For a proper PDF, we'd need to use iText html2pdf or similar
            // For now, return a message suggesting print to PDF
            String message = "PDF export coming soon. Please use the Print button and select 'Save as PDF' in your browser.";
            return ResponseEntity.ok()
                .header("Content-Type", "text/plain")
                .body(message.getBytes());
        } catch (Exception e) {
            log.error("Error exporting PDF for batch {}: {}", batchId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(("Error: " + e.getMessage()).getBytes());
        }
    }

    /**
     * Export payment advice as Excel.
     */
    @GetMapping("/{batchId}/excel")
    public ResponseEntity<byte[]> exportExcel(@PathVariable String batchId) {
        log.info("Exporting Excel for batch: {}", batchId);

        try {
            byte[] excelBytes = paymentAdviceService.exportToExcel(batchId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment",
                "PaymentAdvice_" + batchId + ".xlsx");
            headers.setContentLength(excelBytes.length);

            return ResponseEntity.ok()
                .headers(headers)
                .body(excelBytes);
        } catch (Exception e) {
            log.error("Error exporting Excel for batch {}: {}", batchId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(("Error: " + e.getMessage()).getBytes());
        }
    }
}

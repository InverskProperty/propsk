package site.easy.to.build.crm.controller.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.entity.PaymentCorrection;
import site.easy.to.build.crm.service.transaction.PaymentCorrectionService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payment-corrections")
public class PaymentCorrectionController {

    @Autowired
    private PaymentCorrectionService paymentCorrectionService;

    @GetMapping
    public ResponseEntity<List<PaymentCorrection>> listCorrections() {
        return ResponseEntity.ok(paymentCorrectionService.findAll());
    }

    @PostMapping
    public ResponseEntity<PaymentCorrection> createCorrection(@RequestBody PaymentCorrection correction) {
        PaymentCorrection saved = paymentCorrectionService.save(correction);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deactivateCorrection(@PathVariable Long id) {
        paymentCorrectionService.deactivate(id);
        return ResponseEntity.ok(Map.of("status", "deactivated", "id", id.toString()));
    }

    @PostMapping("/apply")
    public ResponseEntity<Map<String, Object>> applyNow() {
        int applied = paymentCorrectionService.applyCorrections();
        return ResponseEntity.ok(Map.of("correctionsApplied", applied));
    }
}

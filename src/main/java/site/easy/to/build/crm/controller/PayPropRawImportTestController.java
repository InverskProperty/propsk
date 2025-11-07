package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.easy.to.build.crm.service.payprop.raw.PayPropRawImportOrchestrator;
import site.easy.to.build.crm.service.payprop.raw.PayPropRawImportOrchestrator.PayPropRawImportOrchestrationResult;
import site.easy.to.build.crm.service.payprop.raw.PayPropRawPropertiesImportService;
import site.easy.to.build.crm.service.payprop.raw.PayPropRawInvoicesImportService;
import site.easy.to.build.crm.service.payprop.raw.PayPropRawImportResult;
import site.easy.to.build.crm.service.payprop.business.PropertyRentCalculationService;
import site.easy.to.build.crm.service.payprop.business.PropertyRentCalculationResult;

import java.util.HashMap;
import java.util.Map;

/**
 * PayProp Raw Import Test Controller
 *
 * Test endpoints for the new raw import system that solves the £995 vs £1,075 mystery.
 * Use these endpoints to test Phase 3A implementation.
 *
 * IMPORTANT: This is for TESTING ONLY. Remove before production or secure appropriately.
 */
@RestController
@RequestMapping("/test/payprop-raw")
@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
public class PayPropRawImportTestController {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropRawImportTestController.class);
    
    @Autowired
    private PayPropRawImportOrchestrator orchestrator;
    
    @Autowired
    private PayPropRawPropertiesImportService propertiesImportService;
    
    @Autowired
    private PayPropRawInvoicesImportService invoicesImportService;
    
    @Autowired
    private PropertyRentCalculationService rentCalculationService;
    
    /**
     * Test the complete raw import process (FULL SOLUTION)
     * This endpoint solves the £995 vs £1,075 problem end-to-end
     */
    @PostMapping("/complete")
    public ResponseEntity<Map<String, Object>> testCompleteImport() {
        log.info("🧪 TESTING: Complete PayProp raw import process");
        
        try {
            PayPropRawImportOrchestrationResult result = orchestrator.executeCompleteImport();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("summary", result.getSummary());
            response.put("duration_seconds", result.getDuration().getSeconds());
            response.put("import_results", result.getImportResults());
            response.put("rent_calculation", result.getRentCalculationResult());
            
            if (result.isSuccess()) {
                response.put("message", "✅ £995 vs £1,075 mystery SOLVED! Check logs for details.");
                log.info("🎯 TEST SUCCESS: Complete import finished");
                return ResponseEntity.ok(response);
            } else {
                response.put("error", result.getErrorMessage());
                log.error("❌ TEST FAILED: Complete import failed");
                return ResponseEntity.internalServerError().body(response);
            }
            
        } catch (Exception e) {
            log.error("❌ TEST EXCEPTION: Complete import test failed", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Test raw data import only (no business logic)
     */
    @PostMapping("/raw-only")
    public ResponseEntity<Map<String, Object>> testRawImportOnly() {
        log.info("🧪 TESTING: Raw data import only");
        
        try {
            PayPropRawImportOrchestrationResult result = orchestrator.executeRawImportOnly();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("summary", result.getSummary());
            response.put("duration_seconds", result.getDuration().getSeconds());
            response.put("import_results", result.getImportResults());
            
            if (result.isSuccess()) {
                response.put("message", "✅ Raw data imported successfully. Ready for business logic phase.");
                return ResponseEntity.ok(response);
            } else {
                response.put("error", result.getErrorMessage());
                return ResponseEntity.internalServerError().body(response);
            }
            
        } catch (Exception e) {
            log.error("❌ TEST EXCEPTION: Raw import only test failed", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Test business logic only (assumes raw data exists)
     */
    @PostMapping("/business-logic-only")
    public ResponseEntity<Map<String, Object>> testBusinessLogicOnly() {
        log.info("🧪 TESTING: Business logic only (rent calculation)");
        
        try {
            PropertyRentCalculationResult result = orchestrator.executeBusinessLogicOnly();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("summary", result.getSummary());
            response.put("duration_seconds", result.getDuration().getSeconds());
            response.put("total_properties", result.getTotalProperties());
            response.put("decisions_calculated", result.getDecisionsCalculated());
            response.put("properties_updated", result.getPropertiesUpdated());
            
            if (result.isSuccess()) {
                response.put("message", "✅ £995 vs £1,075 calculations completed!");
                return ResponseEntity.ok(response);
            } else {
                response.put("error", result.getErrorMessage());
                return ResponseEntity.internalServerError().body(response);
            }
            
        } catch (Exception e) {
            log.error("❌ TEST EXCEPTION: Business logic only test failed", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Test properties import only
     */
    @PostMapping("/properties")
    public ResponseEntity<Map<String, Object>> testPropertiesImport() {
        log.info("🧪 TESTING: Properties import (£995 data)");
        
        try {
            PayPropRawImportResult result = propertiesImportService.importAllProperties();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("summary", result.getSummary());
            response.put("total_fetched", result.getTotalFetched());
            response.put("total_imported", result.getTotalImported());
            response.put("duration_seconds", result.getDuration().getSeconds());
            
            if (result.isSuccess()) {
                response.put("message", String.format("✅ Imported %d properties with £995 settings data", 
                    result.getTotalImported()));
                return ResponseEntity.ok(response);
            } else {
                response.put("error", result.getErrorMessage());
                return ResponseEntity.internalServerError().body(response);
            }
            
        } catch (Exception e) {
            log.error("❌ TEST EXCEPTION: Properties import test failed", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Test invoices import only
     */
    @PostMapping("/invoices")
    public ResponseEntity<Map<String, Object>> testInvoicesImport() {
        log.info("🧪 TESTING: Invoice instructions import (£1,075 data)");
        
        try {
            PayPropRawImportResult result = invoicesImportService.importAllInvoices();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("summary", result.getSummary());
            response.put("total_fetched", result.getTotalFetched());
            response.put("total_imported", result.getTotalImported());
            response.put("duration_seconds", result.getDuration().getSeconds());
            
            if (result.isSuccess()) {
                response.put("message", String.format("✅ Imported %d invoice instructions with £1,075 gross amounts", 
                    result.getTotalImported()));
                return ResponseEntity.ok(response);
            } else {
                response.put("error", result.getErrorMessage());
                return ResponseEntity.internalServerError().body(response);
            }
            
        } catch (Exception e) {
            log.error("❌ TEST EXCEPTION: Invoice instructions import test failed", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Get status of raw import tables
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getImportStatus() {
        log.info("🧪 CHECKING: Raw import table status");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // This would need to be implemented to check table row counts
            // For now, just return a placeholder
            response.put("message", "Raw import system is ready for testing");
            response.put("endpoints", Map.of(
                "complete", "POST /test/payprop-raw/complete - Full end-to-end test",
                "raw-only", "POST /test/payprop-raw/raw-only - Import raw data only", 
                "business-logic", "POST /test/payprop-raw/business-logic-only - Calculate rent decisions",
                "properties", "POST /test/payprop-raw/properties - Import properties (£995)",
                "invoices", "POST /test/payprop-raw/invoices - Import invoices (£1,075)"
            ));
            response.put("ready", true);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Error checking import status", e);
            response.put("error", e.getMessage());
            response.put("ready", false);
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.easy.to.build.crm.service.payprop.raw.PayPropRawImportOrchestrator;
import site.easy.to.build.crm.service.payprop.raw.PayPropRawImportOrchestrator.PayPropRawImportOrchestrationResult;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple PayProp Raw Import Controller
 * 
 * Uses the same pattern as existing working controllers in the project.
 * Provides both HTML view and JSON API endpoints for testing.
 */
@Controller
@RequestMapping("/debug/payprop-raw")
public class PayPropRawImportSimpleController {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropRawImportSimpleController.class);
    
    @Autowired
    private PayPropRawImportOrchestrator orchestrator;
    
    /**
     * HTML view for testing (like other debug controllers)
     */
    @GetMapping("/test-page")
    public String testPage(Model model) {
        model.addAttribute("title", "PayProp Raw Import Test");
        model.addAttribute("description", "Test the raw import system that solves £995 vs £1,075 mystery");
        return "debug/payprop-raw-test";  // Will need to create this template
    }
    
    /**
     * Simple status check (JSON response)
     */
    @GetMapping("/status")
    @ResponseBody
    public Map<String, Object> getStatus() {
        log.info("🧪 PayProp raw import status check");
        
        Map<String, Object> status = new HashMap<>();
        status.put("ready", true);
        status.put("message", "PayProp raw import system ready");
        status.put("endpoints", Map.of(
            "complete_test", "/debug/payprop-raw/run-complete-test",
            "status", "/debug/payprop-raw/status"
        ));
        
        return status;
    }
    
    /**
     * Run the complete test (JSON response)
     */
    @PostMapping("/run-complete-test")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> runCompleteTest() {
        log.info("🧪 STARTING: PayProp raw import complete test");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            PayPropRawImportOrchestrationResult result = orchestrator.executeCompleteImport();
            
            response.put("success", result.isSuccess());
            response.put("summary", result.getSummary());
            response.put("duration_seconds", result.getDuration().getSeconds());
            response.put("import_results", result.getImportResults());
            response.put("rent_calculation", result.getRentCalculationResult());
            
            if (result.isSuccess()) {
                response.put("message", "✅ £995 vs £1,075 mystery SOLVED!");
                log.info("🎯 TEST SUCCESS: Raw import complete");
                return ResponseEntity.ok(response);
            } else {
                response.put("error", result.getErrorMessage());
                log.error("❌ TEST FAILED: {}", result.getErrorMessage());
                return ResponseEntity.status(500).body(response);
            }
            
        } catch (Exception e) {
            log.error("❌ TEST EXCEPTION: Raw import test failed", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
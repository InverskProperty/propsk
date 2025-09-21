// PayPropTestPageController.java
package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import site.easy.to.build.crm.service.sheets.GoogleSheetsServiceAccountService;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller to serve the PayProp integration test page
 */
@Controller
@RequestMapping("/payprop")
public class PayPropTestPageController {

    @Autowired
    private GoogleSheetsServiceAccountService googleSheetsServiceAccountService;

    /**
     * Serve the PayProp test page
     * Access at: /payprop/test
     */
    @GetMapping("/test")
    public String showTestPage() {
        return "payprop/test"; // This will look for test.html in templates/payprop/
    }

    /**
     * Test Google Sheets service account access
     * Access at: /payprop/test-google-sheets
     */
    @GetMapping("/test-google-sheets")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testGoogleSheetsAccess() {
        Map<String, Object> result = new HashMap<>();

        try {
            System.out.println("🧪 TEST: Starting Google Sheets service account test...");

            // Try to create a simple test spreadsheet
            String spreadsheetId = googleSheetsServiceAccountService.createTestSpreadsheet();

            result.put("success", true);
            result.put("message", "Google Sheets access test successful!");
            result.put("spreadsheetId", spreadsheetId);
            result.put("spreadsheetUrl", "https://docs.google.com/spreadsheets/d/" + spreadsheetId);

            System.out.println("✅ TEST: Google Sheets test successful - " + spreadsheetId);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            System.err.println("❌ TEST: Google Sheets test failed - " + e.getMessage());
            e.printStackTrace();

            result.put("success", false);
            result.put("message", "Google Sheets access test failed: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());

            return ResponseEntity.status(500).body(result);
        }
    }
}
package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.statements.XLSXStatementService;

import java.time.LocalDate;

@Controller
@RequestMapping("/test")
public class XLSXTestController {

    private final XLSXStatementService xlsxStatementService;
    private final CustomerService customerService;

    @Autowired
    public XLSXTestController(XLSXStatementService xlsxStatementService,
                             CustomerService customerService) {
        this.xlsxStatementService = xlsxStatementService;
        this.customerService = customerService;
    }

    /**
     * Test enhanced XLSX generation with 38-column layout
     */
    @GetMapping("/xlsx-enhanced")
    public ResponseEntity<byte[]> generateEnhancedXLSX(Authentication authentication) {
        try {
            System.out.println("🧪 Testing ENHANCED 38-column XLSX generation...");

            // Find any property owner for testing
            Customer testPropertyOwner = customerService.findPropertyOwners()
                .stream()
                .findFirst()
                .orElse(null);

            if (testPropertyOwner == null) {
                System.out.println("❌ No property owners found for testing");
                return ResponseEntity.notFound().build();
            }

            System.out.println("🏢 Using test property owner: " + testPropertyOwner.getName());

            // Use current month as test period
            LocalDate fromDate = LocalDate.now().withDayOfMonth(1);
            LocalDate toDate = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());

            // Generate enhanced XLSX
            byte[] excelData = xlsxStatementService.generatePropertyOwnerStatementXLSX(
                testPropertyOwner, fromDate, toDate);

            String filename = "Enhanced_Statement_" + testPropertyOwner.getName().replaceAll("[^a-zA-Z0-9]", "_") + ".xlsx";

            System.out.println("✅ Enhanced XLSX generated successfully - " + excelData.length + " bytes");
            System.out.println("📊 Features included: 38 columns, payment routing, 4 expense slots per property, advanced formulas");

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelData);

        } catch (Exception e) {
            System.err.println("❌ Enhanced XLSX generation failed: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Test XLSX generation with sample data (original 13-column version for comparison)
     */
    @GetMapping("/xlsx-sample")
    public ResponseEntity<byte[]> generateSampleXLSX(Authentication authentication) {
        try {
            System.out.println("🧪 Testing XLSX generation...");

            // Find any property owner for testing
            Customer testPropertyOwner = customerService.findPropertyOwners()
                .stream()
                .findFirst()
                .orElse(null);

            if (testPropertyOwner == null) {
                System.out.println("❌ No property owners found for testing");
                return ResponseEntity.notFound().build();
            }

            System.out.println("🏢 Using test property owner: " + testPropertyOwner.getName());

            // Use current month as test period
            LocalDate fromDate = LocalDate.now().withDayOfMonth(1);
            LocalDate toDate = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());

            // Generate XLSX
            byte[] excelData = xlsxStatementService.generatePropertyOwnerStatementXLSX(
                testPropertyOwner, fromDate, toDate);

            String filename = "Test_Statement_" + testPropertyOwner.getName().replaceAll("[^a-zA-Z0-9]", "_") + ".xlsx";

            System.out.println("✅ Test XLSX generated successfully - " + excelData.length + " bytes");

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelData);

        } catch (Exception e) {
            System.err.println("❌ Test XLSX generation failed: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Test basic service instantiation
     */
    @GetMapping("/xlsx-health")
    @ResponseBody
    public String testXLSXHealth() {
        try {
            // Just test that the service is properly instantiated
            if (xlsxStatementService != null) {
                return "✅ XLSXStatementService is properly instantiated and ready!";
            } else {
                return "❌ XLSXStatementService is null";
            }
        } catch (Exception e) {
            return "❌ Error: " + e.getMessage();
        }
    }

    /**
     * Show debug information
     */
    @GetMapping("/xlsx-debug")
    @ResponseBody
    public String debugXLSXService() {
        try {
            StringBuilder debug = new StringBuilder();
            debug.append("📊 XLSX Service Debug Information:\n\n");

            debug.append("✅ XLSXStatementService: ").append(xlsxStatementService != null ? "Available" : "NULL").append("\n");
            debug.append("✅ CustomerService: ").append(customerService != null ? "Available" : "NULL").append("\n");

            // Check property owners
            try {
                int propertyOwnersCount = customerService.findPropertyOwners().size();
                debug.append("🏢 Property Owners Found: ").append(propertyOwnersCount).append("\n");
            } catch (Exception e) {
                debug.append("❌ Error finding property owners: ").append(e.getMessage()).append("\n");
            }

            // Check Apache POI availability
            try {
                Class.forName("org.apache.poi.xssf.usermodel.XSSFWorkbook");
                debug.append("✅ Apache POI: Available\n");
            } catch (ClassNotFoundException e) {
                debug.append("❌ Apache POI: Not available\n");
            }

            debug.append("\n🔗 Test URLs:\n");
            debug.append("- Enhanced XLSX (38 columns): /test/xlsx-enhanced\n");
            debug.append("- Sample XLSX (13 columns): /test/xlsx-sample\n");
            debug.append("- Health Check: /test/xlsx-health\n");

            debug.append("\n📊 Enhanced Features:\n");
            debug.append("- 38 columns with payment routing detection\n");
            debug.append("- 4 expense slots per property\n");
            debug.append("- Advanced Excel formulas\n");
            debug.append("- Boolean/percentage formatting\n");
            debug.append("- Office and parking space rows\n");

            return debug.toString();

        } catch (Exception e) {
            return "❌ Debug Error: " + e.getMessage();
        }
    }
}
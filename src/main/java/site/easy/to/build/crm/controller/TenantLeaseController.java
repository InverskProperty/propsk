package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.CustomerPropertyAssignment;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.assignment.TenantAssignmentWithLeaseService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for creating Tenant Assignments WITH Leases
 *
 * This ensures whenever you assign a tenant to a property,
 * you also create the lease (invoice) with financial terms.
 *
 * Endpoints:
 * - GET /employee/tenant-lease/create - Show form
 * - POST /employee/tenant-lease/create - Create tenant + lease
 * - GET /api/tenant-lease/assignments-without-leases - Find gaps
 * - POST /api/tenant-lease/add-lease/{assignmentId} - Add missing lease
 */
@Controller
@RequestMapping("/employee/tenant-lease")
public class TenantLeaseController {

    @Autowired
    private TenantAssignmentWithLeaseService tenantLeaseService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private PropertyService propertyService;

    /**
     * Show create tenant + lease form
     */
    @GetMapping("/create")
    public String showCreateTenantLease(Model model, Authentication authentication) {
        try {
            List<Customer> tenants = customerService.findTenants();
            List<Property> properties = propertyService.findAll();

            model.addAttribute("tenants", tenants);
            model.addAttribute("properties", properties);

            return "tenant-lease/create-tenant-lease";

        } catch (Exception e) {
            model.addAttribute("error", "Error loading page: " + e.getMessage());
            return "error/500";
        }
    }

    /**
     * Create tenant assignment AND lease together
     */
    @PostMapping("/create")
    public String createTenantWithLease(
            @RequestParam("customerId") Long customerId,
            @RequestParam("propertyId") Long propertyId,
            @RequestParam("rentAmount") BigDecimal rentAmount,
            @RequestParam("startDate") String startDateStr,
            @RequestParam(value = "endDate", required = false) String endDateStr,
            @RequestParam(value = "paymentDay", required = false, defaultValue = "1") Integer paymentDay,
            @RequestParam(value = "syncToPayProp", defaultValue = "false") Boolean syncToPayProp,
            RedirectAttributes redirectAttributes,
            Authentication authentication) {

        try {
            System.out.println("🏠 Creating tenant + lease: Customer " + customerId +
                    " → Property " + propertyId + " @ £" + rentAmount);

            // Get entities
            Customer customer = customerService.findByCustomerId(customerId);
            Property property = propertyService.findById(propertyId);

            if (customer == null || property == null) {
                redirectAttributes.addFlashAttribute("error", "Customer or Property not found");
                return "redirect:/employee/tenant-lease/create";
            }

            // Parse dates
            LocalDate startDate = LocalDate.parse(startDateStr);
            LocalDate endDate = null;
            if (endDateStr != null && !endDateStr.trim().isEmpty()) {
                endDate = LocalDate.parse(endDateStr);
            }

            // Create tenant assignment + lease
            TenantAssignmentWithLeaseService.TenantLeaseResult result =
                    tenantLeaseService.createTenantWithLease(
                            customer, property, rentAmount, startDate, endDate, paymentDay, syncToPayProp);

            if (result.success) {
                String message = String.format(
                        "✅ Created tenant assignment (ID: %d) and lease (ID: %d) for %s at %s",
                        result.assignment.getId(),
                        result.lease.getId(),
                        customer.getName(),
                        property.getPropertyName());

                redirectAttributes.addFlashAttribute("success", message);
                System.out.println(message);
            } else {
                redirectAttributes.addFlashAttribute("error", result.message);
            }

        } catch (Exception e) {
            System.out.println("❌ Failed to create tenant + lease: " + e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
        }

        return "redirect:/employee/tenant-lease/create";
    }

    /**
     * API: Find tenant assignments that don't have leases
     * GET /api/tenant-lease/assignments-without-leases
     */
    @GetMapping("/api/assignments-without-leases")
    @ResponseBody
    public ResponseEntity<?> findAssignmentsWithoutLeases() {
        try {
            List<CustomerPropertyAssignment> assignments =
                    tenantLeaseService.findTenantAssignmentsWithoutLeases();

            Map<String, Object> response = new HashMap<>();
            response.put("count", assignments.size());
            response.put("assignments", assignments.stream()
                    .map(a -> Map.of(
                            "id", a.getId(),
                            "tenantName", a.getCustomer().getName(),
                            "propertyName", a.getProperty().getPropertyName(),
                            "startDate", a.getStartDate() != null ? a.getStartDate().toString() : "N/A",
                            "endDate", a.getEndDate() != null ? a.getEndDate().toString() : "Ongoing"
                    ))
                    .toList());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * API: Add lease to existing tenant assignment
     * POST /api/tenant-lease/add-lease/{assignmentId}
     * Body: { "rentAmount": 900.00, "paymentDay": 1 }
     */
    @PostMapping("/api/add-lease/{assignmentId}")
    @ResponseBody
    public ResponseEntity<?> addLeaseToAssignment(
            @PathVariable Long assignmentId,
            @RequestBody Map<String, Object> request) {

        try {
            BigDecimal rentAmount = new BigDecimal(request.get("rentAmount").toString());
            Integer paymentDay = request.containsKey("paymentDay")
                    ? Integer.parseInt(request.get("paymentDay").toString())
                    : 1;

            TenantAssignmentWithLeaseService.TenantLeaseResult result =
                    tenantLeaseService.addLeaseToExistingAssignment(assignmentId, rentAmount, paymentDay);

            Map<String, Object> response = new HashMap<>();
            response.put("success", result.success);
            response.put("message", result.message);

            if (result.success) {
                response.put("leaseId", result.lease.getId());
                response.put("assignmentId", result.assignment.getId());
            }

            return result.success
                    ? ResponseEntity.ok(response)
                    : ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}

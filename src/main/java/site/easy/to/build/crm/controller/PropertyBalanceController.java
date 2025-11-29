package site.easy.to.build.crm.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.PropertyBalanceLedger;
import site.easy.to.build.crm.repository.PropertyBalanceLedgerRepository;
import site.easy.to.build.crm.repository.PropertyRepository;
import site.easy.to.build.crm.service.payment.PropertyBalanceService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for Property Balance management
 *
 * Provides UI for:
 * - Viewing property balances
 * - Viewing ledger history
 * - Manual adjustments
 * - Transfers between properties
 * - Setting opening balances
 */
@Controller
@RequestMapping("/property-balances")
public class PropertyBalanceController {

    private static final Logger log = LoggerFactory.getLogger(PropertyBalanceController.class);

    @Autowired
    private PropertyBalanceService propertyBalanceService;

    @Autowired
    private PropertyBalanceLedgerRepository ledgerRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    // ===== DASHBOARD =====

    /**
     * Property balance dashboard - list all properties with balances
     */
    @GetMapping
    public String dashboard(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String filter,
            Model model) {

        log.info("Property balance dashboard requested, filter: {}", filter);

        // Get all properties with balance info
        List<Property> properties;
        if ("with-balance".equals(filter)) {
            // Only properties with non-zero balance
            List<Long> propertyIdsWithBalance = ledgerRepository.findPropertiesWithBalance();
            properties = propertyRepository.findAllById(propertyIdsWithBalance);
        } else if ("block".equals(filter)) {
            // Only block properties
            properties = propertyRepository.findByIsBlockProperty(true);
        } else {
            // All properties
            properties = propertyRepository.findAll();
        }

        // Build balance info for each property
        List<Map<String, Object>> propertyBalances = properties.stream().map(p -> {
            Map<String, Object> info = new HashMap<>();
            info.put("property", p);
            info.put("currentBalance", propertyBalanceService.getCurrentBalance(p.getId()));
            info.put("availableBalance", propertyBalanceService.getAvailableBalance(p.getId()));
            info.put("minimumBalance", p.getPropertyAccountMinimumBalance() != null
                    ? p.getPropertyAccountMinimumBalance() : BigDecimal.ZERO);
            info.put("isBlockProperty", Boolean.TRUE.equals(p.getIsBlockProperty()));
            return info;
        }).toList();

        model.addAttribute("propertyBalances", propertyBalances);
        model.addAttribute("filter", filter);

        return "property-balances/dashboard";
    }

    // ===== PROPERTY DETAIL =====

    /**
     * Property balance detail view with ledger history
     */
    @GetMapping("/{propertyId}")
    public String propertyDetail(
            @PathVariable Long propertyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Model model) {

        log.info("Property balance detail requested for property {}", propertyId);

        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found: " + propertyId));

        // Default date range: last 3 months
        if (fromDate == null) {
            fromDate = LocalDate.now().minusMonths(3);
        }
        if (toDate == null) {
            toDate = LocalDate.now();
        }

        // Get ledger history
        List<PropertyBalanceLedger> ledgerEntries = propertyBalanceService.getLedgerHistory(
                propertyId, fromDate, toDate);

        // Get balance info
        BigDecimal currentBalance = propertyBalanceService.getCurrentBalance(propertyId);
        BigDecimal availableBalance = propertyBalanceService.getAvailableBalance(propertyId);

        model.addAttribute("property", property);
        model.addAttribute("ledgerEntries", ledgerEntries);
        model.addAttribute("currentBalance", currentBalance);
        model.addAttribute("availableBalance", availableBalance);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("isBlockProperty", Boolean.TRUE.equals(property.getIsBlockProperty()));

        return "property-balances/detail";
    }

    // ===== MANUAL ADJUSTMENT =====

    /**
     * Show adjustment form
     */
    @GetMapping("/{propertyId}/adjust")
    public String adjustForm(@PathVariable Long propertyId, Model model) {

        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found: " + propertyId));

        model.addAttribute("property", property);
        model.addAttribute("currentBalance", propertyBalanceService.getCurrentBalance(propertyId));

        return "property-balances/adjust";
    }

    /**
     * Process manual adjustment
     */
    @PostMapping("/{propertyId}/adjust")
    public String processAdjustment(
            @PathVariable Long propertyId,
            @RequestParam BigDecimal amount,
            @RequestParam String adjustmentType,
            @RequestParam String description,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttributes) {

        log.info("Processing adjustment for property {}: {} {}", propertyId, adjustmentType, amount);

        try {
            // Convert to signed amount based on type
            BigDecimal signedAmount = "debit".equals(adjustmentType)
                    ? amount.negate() : amount;

            propertyBalanceService.adjust(propertyId, signedAmount, description, notes, null);

            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("Adjustment of %s processed successfully", amount));

        } catch (Exception e) {
            log.error("Failed to process adjustment", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Failed to process adjustment: " + e.getMessage());
        }

        return "redirect:/property-balances/" + propertyId;
    }

    // ===== OPENING BALANCE =====

    /**
     * Show opening balance form
     */
    @GetMapping("/{propertyId}/opening-balance")
    public String openingBalanceForm(@PathVariable Long propertyId, Model model) {

        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found: " + propertyId));

        // Check if property already has ledger entries
        boolean hasEntries = ledgerRepository.hasAnyEntries(propertyId);

        model.addAttribute("property", property);
        model.addAttribute("hasEntries", hasEntries);
        model.addAttribute("currentBalance", propertyBalanceService.getCurrentBalance(propertyId));

        return "property-balances/opening-balance";
    }

    /**
     * Set opening balance
     */
    @PostMapping("/{propertyId}/opening-balance")
    public String setOpeningBalance(
            @PathVariable Long propertyId,
            @RequestParam BigDecimal amount,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttributes) {

        log.info("Setting opening balance for property {}: {} as of {}", propertyId, amount, asOfDate);

        try {
            propertyBalanceService.setOpeningBalance(propertyId, amount, asOfDate, notes, null);

            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("Opening balance of %s set successfully", amount));

        } catch (Exception e) {
            log.error("Failed to set opening balance", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Failed to set opening balance: " + e.getMessage());
        }

        return "redirect:/property-balances/" + propertyId;
    }

    // ===== TRANSFERS =====

    /**
     * Show transfer form
     */
    @GetMapping("/{propertyId}/transfer")
    public String transferForm(@PathVariable Long propertyId, Model model) {

        Property fromProperty = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found: " + propertyId));

        // Get block properties as potential transfer destinations
        List<Property> blockProperties = propertyRepository.findByIsBlockProperty(true);

        model.addAttribute("fromProperty", fromProperty);
        model.addAttribute("blockProperties", blockProperties);
        model.addAttribute("availableBalance", propertyBalanceService.getAvailableBalance(propertyId));

        return "property-balances/transfer";
    }

    /**
     * Process transfer
     */
    @PostMapping("/{propertyId}/transfer")
    public String processTransfer(
            @PathVariable Long propertyId,
            @RequestParam Long toPropertyId,
            @RequestParam BigDecimal amount,
            @RequestParam String description,
            RedirectAttributes redirectAttributes) {

        log.info("Processing transfer from property {} to {}: {}", propertyId, toPropertyId, amount);

        try {
            propertyBalanceService.transfer(propertyId, toPropertyId, amount, description, null);

            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("Transfer of %s processed successfully", amount));

        } catch (Exception e) {
            log.error("Failed to process transfer", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Failed to process transfer: " + e.getMessage());
        }

        return "redirect:/property-balances/" + propertyId;
    }

    // ===== API ENDPOINTS =====

    /**
     * Get balance for a property (AJAX)
     */
    @GetMapping("/api/{propertyId}/balance")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getBalance(@PathVariable Long propertyId) {
        Map<String, Object> response = new HashMap<>();
        response.put("propertyId", propertyId);
        response.put("currentBalance", propertyBalanceService.getCurrentBalance(propertyId));
        response.put("availableBalance", propertyBalanceService.getAvailableBalance(propertyId));
        return ResponseEntity.ok(response);
    }

    /**
     * Get ledger entries for a property (AJAX)
     */
    @GetMapping("/api/{propertyId}/ledger")
    @ResponseBody
    public ResponseEntity<List<PropertyBalanceLedger>> getLedger(
            @PathVariable Long propertyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<PropertyBalanceLedger> entries = ledgerRepository
                .findByPropertyIdOrderByEntryDateDescCreatedAtDesc(
                        propertyId, PageRequest.of(page, size));

        return ResponseEntity.ok(entries.getContent());
    }
}

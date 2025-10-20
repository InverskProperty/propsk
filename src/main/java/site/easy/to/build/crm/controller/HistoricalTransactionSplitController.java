package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;

import site.easy.to.build.crm.service.transaction.HistoricalTransactionSplitService;
import site.easy.to.build.crm.util.AuthenticationUtils;
import site.easy.to.build.crm.util.AuthorizationUtil;

import java.util.Map;

/**
 * Controller for retroactively splitting historical rent payments
 */
@Controller
@RequestMapping("/employee/historical-transaction-split")
public class HistoricalTransactionSplitController {

    @Autowired
    private HistoricalTransactionSplitService splitService;

    @Autowired
    private AuthenticationUtils authenticationUtils;

    /**
     * Show split management page
     */
    @GetMapping
    public String showSplitPage(Model model, Authentication authentication) {
        // Only allow managers
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return "error/forbidden";
        }

        model.addAttribute("pageTitle", "Historical Transaction Split");
        return "transaction/split-management";
    }

    /**
     * Preview split (dry run)
     */
    @GetMapping("/preview")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> previewSplit(Authentication authentication) {
        // Only allow managers
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).build();
        }

        Map<String, Object> results = splitService.splitExistingRentPayments(true);
        return ResponseEntity.ok(results);
    }

    /**
     * Execute split (actual operation)
     */
    @PostMapping("/execute")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> executeSplit(Authentication authentication) {
        // Only allow managers
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return ResponseEntity.status(403).build();
        }

        Map<String, Object> results = splitService.splitExistingRentPayments(false);
        return ResponseEntity.ok(results);
    }
}

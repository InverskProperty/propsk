package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import site.easy.to.build.crm.service.payprop.PayPropOAuth2Service;
import site.easy.to.build.crm.service.payprop.PayPropPropertiesImportToMainTableService;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/payprop")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER')")
public class PayPropImportPageController {

    @Autowired
    private PayPropOAuth2Service oAuth2Service;

    @Autowired
    private PayPropPropertiesImportToMainTableService propertiesImportService;

    @GetMapping("/import")
    public String showImportPage(Model model) {
        // IMMEDIATE ACCESS: Skip OAuth check entirely on page load
        // Connection status will be checked asynchronously via JavaScript
        model.addAttribute("paypropConnected", null); // null = unknown status
        model.addAttribute("checkConnectionAsync", true); // Signal to JS to check
        model.addAttribute("home", "/");
        return "payprop/import";
    }

    @PostMapping("/import/status")
    @ResponseBody
    public Map<String, Object> getImportStatus() {
        Map<String, Object> response = new HashMap<>();
        try {
            boolean isConnected = oAuth2Service.hasValidTokens();
            response.put("connected", isConnected);
            response.put("status", isConnected ? "ready" : "not_connected");
        } catch (Exception e) {
            response.put("connected", false);
            response.put("status", "error");
            response.put("error", "Connection check failed: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/import/clear-and-reimport-properties")
    @ResponseBody
    public Map<String, Object> clearAndReimportProperties() {
        boolean isConnected = oAuth2Service.hasValidTokens();
        if (!isConnected) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "PayProp not connected - please authenticate first");
            return response;
        }

        return propertiesImportService.clearAndReimportProperties();
    }
}
package site.easy.to.build.crm.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import site.easy.to.build.crm.service.payprop.PayPropOAuth2Service;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/payprop")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER')")
public class PayPropImportPageController {

    @Autowired
    private PayPropOAuth2Service oAuth2Service;

    @GetMapping("/import")
    public String showImportPage(Model model, Authentication authentication) {
        logger.info("🔍 PayProp Import Page Access Attempt");
        logger.info("   URL: /payprop/import");
        logger.info("   Authentication type: " + (authentication != null ? authentication.getClass().getSimpleName() : "null"));
        logger.info("   Is authenticated: " + (authentication != null ? authentication.isAuthenticated() : false));
        
        if (authentication != null) {
            logger.info("   Principal: " + authentication.getName());
            logger.info("   Authorities: " + authentication.getAuthorities());
        }
        
        try {
            boolean isConnected = oAuth2Service.hasValidTokens();
            logger.info("   PayProp connected: " + isConnected);
            
            model.addAttribute("paypropConnected", isConnected);
            model.addAttribute("home", "/");
            
            logger.info("✅ Returning payprop/import template");
            return "payprop/import";
        } catch (Exception e) {
            logger.error("❌ Error in showImportPage: " + e.getMessage(), e);
            throw e;
        }
    }

    @PostMapping("/import/status")
    @ResponseBody
    public Map<String, Object> getImportStatus() {
        Map<String, Object> response = new HashMap<>();
        boolean isConnected = oAuth2Service.hasValidTokens();
        response.put("connected", isConnected);
        response.put("status", isConnected ? "ready" : "not_connected");
        return response;
    }
}
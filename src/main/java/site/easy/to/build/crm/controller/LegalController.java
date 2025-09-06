package site.easy.to.build.crm.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for legal pages (Privacy Policy, Terms of Service, etc.)
 */
@Controller
public class LegalController {

    /**
     * Privacy Policy page - Required for Google OAuth verification
     */
    @GetMapping("/privacy-policy")
    public String privacyPolicy() {
        return "privacy-policy";
    }

    /**
     * Terms of Service page
     */
    @GetMapping("/terms-of-service")
    public String termsOfService() {
        // Could add a terms template later if needed
        return "redirect:/privacy-policy";
    }
}
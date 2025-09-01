package site.easy.to.build.crm.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * PayProp Fallback Controller
 * Shown when PayProp is not enabled (payprop.enabled=false)
 */
@Controller
@RequestMapping("/payprop")
@ConditionalOnProperty(name = "payprop.enabled", havingValue = "false", matchIfMissing = true)
public class PayPropFallbackController {

    @GetMapping("/import")
    public String showPayPropDisabled(Model model) {
        model.addAttribute("pageTitle", "PayProp Import - Not Available");
        model.addAttribute("error", "PayProp integration is currently disabled.");
        model.addAttribute("message", "To enable PayProp features, set PAYPROP_ENABLED=true in your environment variables and restart the application.");
        model.addAttribute("home", "/");
        
        return "payprop/payprop-disabled";
    }
    
    @GetMapping("/**")
    public String catchAllPayPropRoutes(Model model) {
        return showPayPropDisabled(model);
    }
}
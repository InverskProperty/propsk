package site.easy.to.build.crm.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

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
    
    @GetMapping("/debug-auth")
    @ResponseBody
    public Map<String, Object> debugAuthentication(Authentication authentication) {
        Map<String, Object> debug = new HashMap<>();
        if (authentication != null) {
            debug.put("authenticated", authentication.isAuthenticated());
            debug.put("principal", authentication.getPrincipal().toString());
            debug.put("authorities", authentication.getAuthorities().toString());
            debug.put("authType", authentication.getClass().getSimpleName());
        } else {
            debug.put("error", "No authentication found");
        }
        debug.put("paypropEnabled", "FALSE - PayProp is disabled");
        return debug;
    }
    
    @GetMapping("/**")
    public String catchAllPayPropRoutes(Model model) {
        return showPayPropDisabled(model);
    }
}
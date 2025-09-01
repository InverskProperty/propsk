package site.easy.to.build.crm.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Debug controller to check authentication status
 */
@RestController
@RequestMapping("/debug")
public class AuthDebugController {

    @GetMapping("/auth")
    @ResponseBody
    public Map<String, Object> debugAuthentication(Authentication authentication) {
        Map<String, Object> debug = new HashMap<>();
        if (authentication != null) {
            debug.put("authenticated", authentication.isAuthenticated());
            debug.put("principal", authentication.getPrincipal().toString());
            debug.put("authorities", authentication.getAuthorities().toString());
            debug.put("authType", authentication.getClass().getSimpleName());
            debug.put("name", authentication.getName());
        } else {
            debug.put("error", "No authentication found");
        }
        return debug;
    }
}
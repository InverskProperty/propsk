package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import site.easy.to.build.crm.service.payprop.PayPropOAuth2TokenService;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/payprop")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER')")
public class PayPropImportPageController {

    @Autowired
    private PayPropOAuth2TokenService tokenService;

    @GetMapping("/import")
    public String showImportPage(Model model) {
        boolean isConnected = tokenService.hasValidToken();
        model.addAttribute("paypropConnected", isConnected);
        model.addAttribute("home", "/");
        return "payprop/import";
    }

    @PostMapping("/import/status")
    @ResponseBody
    public Map<String, Object> getImportStatus() {
        Map<String, Object> response = new HashMap<>();
        boolean isConnected = tokenService.hasValidToken();
        response.put("connected", isConnected);
        response.put("status", isConnected ? "ready" : "not_connected");
        return response;
    }
}
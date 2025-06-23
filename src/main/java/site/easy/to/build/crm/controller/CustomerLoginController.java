package site.easy.to.build.crm.controller;

import jakarta.annotation.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import site.easy.to.build.crm.entity.CustomerLoginInfo;
import site.easy.to.build.crm.service.customer.CustomerLoginInfoService;

@Controller
public class CustomerLoginController {

    private final CustomerLoginInfoService customerLoginInfoService;
    private final PasswordEncoder passwordEncoder;

    public CustomerLoginController(CustomerLoginInfoService customerLoginInfoService, 
                                 PasswordEncoder passwordEncoder) {
        this.customerLoginInfoService = customerLoginInfoService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/set-password")
    public String showPasswordForm(Model model, @RequestParam("token") @Nullable String token) {
        if (token == null) {
            return "redirect:/customer-login?error=invalid_token";
        }
        
        CustomerLoginInfo customerLoginInfo = customerLoginInfoService.findByToken(token);
        if (customerLoginInfo == null || customerLoginInfo.isTokenExpired()) {
            return "redirect:/customer-login?error=invalid_token";
        }
        
        model.addAttribute("customerLoginInfo", customerLoginInfo);
        if (customerLoginInfo.getCustomer() != null) {
            model.addAttribute("customerType", customerLoginInfo.getCustomer().getTypeDisplayName());
        }
        return "set-password";
    }

    @PostMapping("/set-password")
    public String setPassword(@ModelAttribute("customerLoginInfo") CustomerLoginInfo customerLoginInfo, 
                             @RequestParam("token") @Nullable String token,
                             RedirectAttributes redirectAttributes) {
        if (token == null) {
            redirectAttributes.addFlashAttribute("error", "Invalid token");
            return "redirect:/customer-login";
        }
        
        CustomerLoginInfo existingLoginInfo = customerLoginInfoService.findByToken(token);
        if (existingLoginInfo == null || existingLoginInfo.isTokenExpired()) {
            redirectAttributes.addFlashAttribute("error", "Invalid or expired token");
            return "redirect:/customer-login";
        }
        
        if (!existingLoginInfo.isPasswordSet()) {
            String hashPassword = passwordEncoder.encode(customerLoginInfo.getPassword());
            existingLoginInfo.setPassword(hashPassword);
            existingLoginInfo.setPasswordSet(true);
            existingLoginInfo.setToken(null); // Clear the token
            existingLoginInfo.setTokenExpiresAt(null);
            customerLoginInfoService.save(existingLoginInfo);
            
            redirectAttributes.addFlashAttribute("success", "Password set successfully. Please login.");
        } else {
            redirectAttributes.addFlashAttribute("error", "Password has already been set");
        }
        
        return "redirect:/customer-login";
    }

    @RequestMapping("/customer-login")
    public String showCustomerLoginForm(@RequestParam(value = "error", required = false) String error,
                                       @RequestParam(value = "logout", required = false) String logout,
                                       Model model) {
        if (error != null) {
            switch (error) {
                case "invalid_token":
                    model.addAttribute("error", "Invalid or expired token");
                    break;
                case "user_not_found":
                    model.addAttribute("error", "User account not found");
                    break;
                case "system_error":
                    model.addAttribute("error", "System error occurred during login");
                    break;
                case "true":
                default:
                    model.addAttribute("error", "Invalid username or password");
                    break;
            }
        }
        
        if (logout != null) {
            model.addAttribute("message", "You have been logged out successfully");
        }
        
        return "customer-login";
    }

    // NOTE: No @PostMapping("/customer-login") - Spring Security handles this
}
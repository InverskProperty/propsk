package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.CustomerLoginInfo;
import site.easy.to.build.crm.service.customer.CustomerLoginInfoService;
import site.easy.to.build.crm.service.customer.CustomerService;

import java.time.LocalDateTime;

@Controller
public class CustomerRegistrationController {

    private final CustomerService customerService;
    private final CustomerLoginInfoService customerLoginInfoService;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public CustomerRegistrationController(CustomerService customerService, 
                                        CustomerLoginInfoService customerLoginInfoService,
                                        PasswordEncoder passwordEncoder) {
        this.customerService = customerService;
        this.customerLoginInfoService = customerLoginInfoService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/customer-register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("registrationForm", new CustomerRegistrationForm());
        return "customer-register";
    }

    @PostMapping("/customer-register")
    public String processRegistration(@ModelAttribute("registrationForm") CustomerRegistrationForm form,
                                    RedirectAttributes redirectAttributes) {
        try {
            // Validate input
            if (form.getEmail() == null || form.getEmail().trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Email is required");
                return "redirect:/customer-register";
            }

            // Email format validation
            if (!form.getEmail().matches("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$")) {
                redirectAttributes.addFlashAttribute("errorMessage", "Please enter a valid email address");
                return "redirect:/customer-register";
            }

            if (form.getPassword() == null || form.getPassword().trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Password is required");
                return "redirect:/customer-register";
            }

            if (form.getPassword().length() < 6) {
                redirectAttributes.addFlashAttribute("errorMessage", "Password must be at least 6 characters");
                return "redirect:/customer-register";
            }

            // Password confirmation validation
            if (form.getConfirmPassword() == null || !form.getPassword().equals(form.getConfirmPassword())) {
                redirectAttributes.addFlashAttribute("errorMessage", "Passwords do not match");
                return "redirect:/customer-register";
            }

            // Check if email exists in customer table
            Customer customer = customerService.findByEmail(form.getEmail().trim());
            if (customer == null) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "Email not found in our system. Please contact support if you believe this is an error.");
                return "redirect:/customer-register";
            }

            // Check if customer already has login credentials
            CustomerLoginInfo existingLogin = customerLoginInfoService.findByEmail(form.getEmail().trim());
            if (existingLogin != null && existingLogin.isPasswordSet()) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "An account with this email already exists. Please use the login page.");
                return "redirect:/customer-register";
            }

            // Create or update login credentials
            CustomerLoginInfo loginInfo;
            if (existingLogin != null) {
                // Update existing record
                loginInfo = existingLogin;
            } else {
                // Create new login record
                loginInfo = new CustomerLoginInfo();
                loginInfo.setId(customer.getCustomerId().intValue()); // Use customer ID as login ID
            }

            // Set login credentials
            loginInfo.setUsername(form.getEmail().trim());
            loginInfo.setPassword(passwordEncoder.encode(form.getPassword()));
            loginInfo.setPasswordSet(true);
            loginInfo.setAccountLocked(false);
            loginInfo.setLoginAttempts(0);
            loginInfo.setCreatedAt(LocalDateTime.now());
            loginInfo.setUpdatedAt(LocalDateTime.now());
            
            // Clear any existing tokens
            loginInfo.setToken(null);
            loginInfo.setTokenExpiresAt(null);

            // Save login info
            customerLoginInfoService.save(loginInfo);

            // Update customer's profile_id to link to login info
            customer.setCustomerLoginInfo(loginInfo);
            customerService.save(customer);

            redirectAttributes.addFlashAttribute("successMessage", 
                "Account created successfully! You can now log in with your email and password.");
            
            return "redirect:/customer-login";

        } catch (Exception e) {
            System.err.println("Error during customer registration: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("errorMessage", 
                "An error occurred during registration. Please try again or contact support.");
            return "redirect:/customer-register";
        }
    }

    /**
     * Simple form class for customer registration
     */
    public static class CustomerRegistrationForm {
        private String email;
        private String password;
        private String confirmPassword;

        public CustomerRegistrationForm() {}

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getConfirmPassword() {
            return confirmPassword;
        }

        public void setConfirmPassword(String confirmPassword) {
            this.confirmPassword = confirmPassword;
        }
    }
}
package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import site.easy.to.build.crm.entity.CustomerLoginInfo;
import site.easy.to.build.crm.service.customer.CustomerLoginInfoService;

@Controller
public class PasswordTestController {

    @Autowired
    private CustomerLoginInfoService customerLoginInfoService;
    
    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/test-password")
    @ResponseBody
    public String testPassword() {
        try {
            String email = "ramakrishnasai.talluri@gmail.com";
            String newPassword = "test123"; // Use this password for testing
            
            CustomerLoginInfo loginInfo = customerLoginInfoService.findByEmail(email);
            if (loginInfo == null) {
                return "User not found";
            }
            
            // Hash the new password and save it
            String hashedPassword = passwordEncoder.encode(newPassword);
            loginInfo.setPassword(hashedPassword);
            customerLoginInfoService.save(loginInfo);
            
            return "Password updated for " + email + " to: " + newPassword + 
                   "<br>Hashed: " + hashedPassword +
                   "<br>Now try logging in with password: " + newPassword;
            
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
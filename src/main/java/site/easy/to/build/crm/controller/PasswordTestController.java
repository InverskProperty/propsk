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
            String email = "piyush@sunflaguk.com";

            CustomerLoginInfo loginInfo = customerLoginInfoService.findByEmail(email);
            if (loginInfo == null) {
                return "User not found for email: " + email;
            }

            String storedHash = loginInfo.getPassword();
            String testPassword = "123456"; // Try common passwords

            boolean matches1 = passwordEncoder.matches(testPassword, storedHash);
            boolean matches2 = passwordEncoder.matches("password", storedHash);
            boolean matches3 = passwordEncoder.matches("test123", storedHash);
            boolean matches4 = passwordEncoder.matches("admin", storedHash);

            return "Password verification for " + email + ":" +
                   "<br>Stored hash: " + storedHash +
                   "<br>'" + testPassword + "' matches: " + matches1 +
                   "<br>'password' matches: " + matches2 +
                   "<br>'test123' matches: " + matches3 +
                   "<br>'admin' matches: " + matches4 +
                   "<br><br>If none match, try setting a new password using /set-test-password";

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @GetMapping("/set-test-password")
    @ResponseBody
    public String setTestPassword() {
        try {
            String email = "piyush@sunflaguk.com";
            String newPassword = "test123";

            CustomerLoginInfo loginInfo = customerLoginInfoService.findByEmail(email);
            if (loginInfo == null) {
                return "User not found for email: " + email;
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
package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import site.easy.to.build.crm.util.PasswordFixUtil;

@Controller
public class PasswordFixController {

    @Autowired
    private PasswordFixUtil passwordFixUtil;

    @GetMapping("/fix-piyush-password")
    @ResponseBody
    public String fixPiyushPassword() {
        String email = "piyush@sunflaguk.com";
        String password = "123";

        // Check current password format
        boolean currentlyBCrypt = passwordFixUtil.checkPasswordFormat(email);

        if (currentlyBCrypt) {
            return "Password for " + email + " is already properly BCrypt encoded.";
        }

        // Fix the password
        boolean success = passwordFixUtil.fixUserPassword(email, password);

        if (success) {
            return "✅ Password successfully fixed for " + email +
                   "<br/>Password is now properly BCrypt encoded." +
                   "<br/>You can now login with password: " + password;
        } else {
            return "❌ Failed to fix password for " + email;
        }
    }

    @GetMapping("/check-piyush-password")
    @ResponseBody
    public String checkPiyushPassword() {
        String email = "piyush@sunflaguk.com";
        boolean isBCrypt = passwordFixUtil.checkPasswordFormat(email);

        return "Password format check for " + email + ": " +
               (isBCrypt ? "✅ Properly BCrypt encoded" : "❌ Not BCrypt encoded - needs fixing");
    }
}
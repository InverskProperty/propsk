package site.easy.to.build.crm.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.repository.UserRepository;

import java.util.List;

@Component
public class PasswordFixUtil {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public boolean fixUserPassword(String email, String plainPassword) {
        try {
            User user = userRepository.findByEmail(email);
            if (user == null) {
                System.err.println("User not found: " + email);
                return false;
            }

            // Encode the password with BCrypt
            String encodedPassword = passwordEncoder.encode(plainPassword);
            user.setPassword(encodedPassword);
            userRepository.save(user);

            System.out.println("✅ Password updated for user: " + email);
            System.out.println("   New BCrypt hash: " + encodedPassword);
            return true;

        } catch (Exception e) {
            System.err.println("❌ Error fixing password for " + email + ": " + e.getMessage());
            return false;
        }
    }

    public boolean checkPasswordFormat(String email) {
        try {
            User user = userRepository.findByEmail(email);
            if (user == null) {
                System.err.println("User not found: " + email);
                return false;
            }

            String password = user.getPassword();
            boolean isBCrypt = password != null && password.startsWith("$2");

            System.out.println("Password check for " + email + ":");
            System.out.println("  Password length: " + (password != null ? password.length() : "NULL"));
            System.out.println("  Is BCrypt format: " + isBCrypt);
            System.out.println("  Password preview: " + (password != null ? password.substring(0, Math.min(20, password.length())) + "..." : "NULL"));

            return isBCrypt;

        } catch (Exception e) {
            System.err.println("❌ Error checking password for " + email + ": " + e.getMessage());
            return false;
        }
    }
}
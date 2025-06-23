package site.easy.to.build.crm.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.google.service.gmail.GoogleGmailApiService;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * FIXED EmailTokenUtils.java - Enhanced Version with Missing Methods
 * Used for customer login management and email verification
 */
public class EmailTokenUtils {
    
    private static final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private static final SecureRandom random = new SecureRandom();
    
    // Character sets for password generation
    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL_CHARS = "!@#$%^&*";
    private static final String ALL_CHARS = UPPERCASE + LOWERCASE + DIGITS + SPECIAL_CHARS;
    
    /**
     * Generate a random password for customer login
     * @return Random password string
     */
    public static String generateRandomPassword() {
        return generateRandomPassword(12); // Default 12 character password
    }
    
    /**
     * Generate a random password with specified length
     * @param length Password length
     * @return Random password string
     */
    public static String generateRandomPassword(int length) {
        if (length < 8) {
            length = 8; // Minimum security requirement
        }
        
        StringBuilder password = new StringBuilder();
        
        // Ensure at least one character from each set
        password.append(UPPERCASE.charAt(random.nextInt(UPPERCASE.length())));
        password.append(LOWERCASE.charAt(random.nextInt(LOWERCASE.length())));
        password.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        password.append(SPECIAL_CHARS.charAt(random.nextInt(SPECIAL_CHARS.length())));
        
        // Fill the rest with random characters
        for (int i = 4; i < length; i++) {
            password.append(ALL_CHARS.charAt(random.nextInt(ALL_CHARS.length())));
        }
        
        // Shuffle the password to randomize character positions
        return shuffleString(password.toString());
    }
    
    /**
     * Encode a password using BCrypt
     * @param rawPassword Raw password to encode
     * @return Encoded password
     */
    public static String encodePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        return passwordEncoder.encode(rawPassword);
    }
    
    /**
     * Verify a raw password against an encoded password
     * @param rawPassword Raw password to check
     * @param encodedPassword Encoded password to verify against
     * @return true if password matches
     */
    public static boolean verifyPassword(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
    
    /**
     * Generate a unique email verification/reset token
     * @return Random token string
     */
    public static String generateEmailToken() {
        return UUID.randomUUID().toString().replace("-", "") + System.currentTimeMillis();
    }
    
    /**
     * ✅ ADDED: Generate a token (alias for generateEmailToken for backward compatibility)
     * FIXES: ManagerController.java line 134 error
     * @return Random token string
     */
    public static String generateToken() {
        return generateEmailToken();
    }
    
    /**
     * Generate a shorter verification code (6 digits)
     * @return 6-digit verification code
     */
    public static String generateVerificationCode() {
        return String.format("%06d", random.nextInt(999999));
    }
    
    /**
     * Check if a token has expired
     * @param tokenCreatedAt When the token was created
     * @param expirationHours Hours until expiration
     * @return true if token is expired
     */
    public static boolean isTokenExpired(LocalDateTime tokenCreatedAt, int expirationHours) {
        if (tokenCreatedAt == null) {
            return true;
        }
        LocalDateTime expirationTime = tokenCreatedAt.plusHours(expirationHours);
        return LocalDateTime.now().isAfter(expirationTime);
    }
    
    /**
     * Check if a token has expired (default 24 hours)
     * @param tokenCreatedAt When the token was created
     * @return true if token is expired
     */
    public static boolean isTokenExpired(LocalDateTime tokenCreatedAt) {
        return isTokenExpired(tokenCreatedAt, 24);
    }
    
    /**
     * ✅ ADDED: Create expiration time for tokens
     * Used by CustomerController password reset functionality
     * @param hours Hours from now for expiration
     * @return LocalDateTime representing expiration time
     */
    public static LocalDateTime createExpirationTime(int hours) {
        return LocalDateTime.now().plusHours(hours);
    }
    
    /**
     * Generate a secure random string for various uses
     * @param length Length of the random string
     * @return Random alphanumeric string
     */
    public static String generateRandomString(int length) {
        String chars = UPPERCASE + LOWERCASE + DIGITS;
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < length; i++) {
            result.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        return result.toString();
    }
    
    /**
     * Generate a password reset token that's shorter and user-friendly
     * @return 8-character reset code
     */
    public static String generatePasswordResetCode() {
        return generateRandomString(8).toUpperCase();
    }
    
    /**
     * Validate password strength
     * @param password Password to validate
     * @return true if password meets minimum requirements
     */
    public static boolean isPasswordStrong(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(c -> SPECIAL_CHARS.indexOf(c) >= 0);
        
        return hasUpper && hasLower && hasDigit && hasSpecial;
    }
    
    /**
     * ✅ ADDED: Send registration email (backward compatibility method)
     * FIXES: ManagerController.java line 140 error
     * @param email User email
     * @param tempPassword Temporary password (actually the name in ManagerController)
     * @param baseUrl The base URL (actually the name in ManagerController) 
     * @param oAuthUser OAuth user for authentication
     * @param gmailService Gmail service instance
     * @return true if email was sent successfully
     */
    
    public static boolean sendRegistrationEmail(String email, String name, String baseUrl, 
                                            OAuthUser oAuthUser, GoogleGmailApiService gmailService) {
        try {
            // Create email content
            String subject = "Welcome - Your Account Details";
            String body = buildEmployeeRegistrationEmailBody(email, name, baseUrl);
            
            // ✅ FIXED: Use correct method signature - OAuthUser first, then email, subject, body
            gmailService.sendEmail(oAuthUser, email, subject, body);
            return true;
            
        } catch (Exception e) {
            // Log error if needed
            return false;
        }
    }
    
    /**
     * Helper method to shuffle a string
     * @param input String to shuffle
     * @return Shuffled string
     */
    private static String shuffleString(String input) {
        char[] characters = input.toCharArray();
        for (int i = characters.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = characters[i];
            characters[i] = characters[j];
            characters[j] = temp;
        }
        return new String(characters);
    }
    
    /**
     * Helper method to build employee registration email body
     * @param email User email
     * @param name User name
     * @param baseUrl Base URL for setting password
     * @return Email body content
     */
    private static String buildEmployeeRegistrationEmailBody(String email, String name, String baseUrl) {
        return String.format(
            "Welcome %s!\n\n" +
            "Your employee account has been created successfully.\n\n" +
            "Email: %s\n" +
            "Please click the following link to set your password:\n" +
            "%s\n\n" +
            "Best regards,\n" +
            "The CRM Team",
            name, email, baseUrl
        );
    }
}
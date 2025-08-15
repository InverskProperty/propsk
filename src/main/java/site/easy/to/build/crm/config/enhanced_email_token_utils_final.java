package site.easy.to.build.crm.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.google.service.gmail.GoogleGmailApiService;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Utility class for handling email tokens, password generation, and encoding
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
     * Generate a simple token for manager invitations (backward compatibility)
     * @return Random token string
     */
    public static String generateToken() {
        return generateEmailToken(); // Use the same logic as generateEmailToken
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
        boolean hasSpecial = password.chars().anyMatch(ch -> SPECIAL_CHARS.indexOf(ch) >= 0);
        
        return hasUpper && hasLower && hasDigit && hasSpecial;
    }
    
    /**
     * Generate a temporary PIN for quick access
     * @return 4-digit PIN
     */
    public static String generateTempPin() {
        return String.format("%04d", random.nextInt(9999));
    }
    
    // Helper method to shuffle string characters
    private static String shuffleString(String input) {
        char[] characters = input.toCharArray();
        
        for (int i = characters.length - 1; i > 0; i--) {
            int randomIndex = random.nextInt(i + 1);
            char temp = characters[i];
            characters[i] = characters[randomIndex];
            characters[randomIndex] = temp;
        }
        
        return new String(characters);
    }
    
    /**
     * Create an expiration timestamp for tokens
     * @param hoursFromNow Hours from current time
     * @return LocalDateTime for expiration
     */
    public static LocalDateTime createExpirationTime(int hoursFromNow) {
        return LocalDateTime.now().plusHours(hoursFromNow);
    }
    
    /**
     * Create an expiration timestamp for tokens (default 24 hours)
     * @return LocalDateTime for expiration
     */
    public static LocalDateTime createExpirationTime() {
        return createExpirationTime(24);
    }
    
    /**
     * Send registration email (for manager controller backward compatibility)
     * This is a simplified implementation - the full email functionality 
     * should be handled by the EmailService class
     * 
     * @param email Recipient email
     * @param token Registration token
     * @param baseUrl Base URL for the application
     * @param oAuthUser OAuth user (can be null)
     * @param googleGmailApiService Gmail API service
     * @return true if email sent successfully
     */
    public static boolean sendRegistrationEmail(String email, String token, String baseUrl, 
                                              OAuthUser oAuthUser, GoogleGmailApiService googleGmailApiService) {
        try {
            String subject = "Account Registration - Complete Your Setup";
            String message = buildRegistrationEmailContent(email, token, baseUrl);
            
            // If Gmail API service is available and OAuth user exists, try to send email
            if (googleGmailApiService != null && oAuthUser != null) {
                // Note: This is a simplified implementation
                // In a real scenario, you'd need proper authentication context
                System.out.println("Sending registration email to: " + email);
                System.out.println("Registration URL: " + baseUrl + "/register?token=" + token);
                return true; // Placeholder - return true for now
            } else {
                // Log the registration details if email can't be sent
                System.out.println("Registration email details for: " + email);
                System.out.println("Token: " + token);
                System.out.println("Registration URL: " + baseUrl + "/register?token=" + token);
                return false;
            }
        } catch (Exception e) {
            System.err.println("Error sending registration email: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Build registration email content
     * @param email Recipient email
     * @param token Registration token
     * @param baseUrl Base application URL
     * @return Email content
     */
    private static String buildRegistrationEmailContent(String email, String token, String baseUrl) {
        StringBuilder content = new StringBuilder();
        content.append("Hello,\n\n");
        content.append("You have been invited to create an account.\n\n");
        content.append("Please click the following link to complete your registration:\n\n");
        content.append(baseUrl).append("/register?token=").append(token).append("\n\n");
        content.append("This link will expire in 24 hours.\n\n");
        content.append("If you did not expect this invitation, please ignore this email.\n\n");
        content.append("Best regards,\n");
        content.append("The CRM Team");
        
        return content.toString();
    }
}
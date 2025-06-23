
package site.easy.to.build.crm.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Custom failure handler for customer login
 * Handles authentication failures and provides debug information
 */
@Component
public class CustomerLoginFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, 
                                       HttpServletResponse response, 
                                       AuthenticationException exception) throws IOException, ServletException {
        
        System.out.println("=== DEBUG: CustomerLoginFailureHandler.onAuthenticationFailure ===");
        System.out.println("DEBUG: Authentication failed: " + exception.getMessage());
        System.out.println("DEBUG: Exception type: " + exception.getClass().getSimpleName());
        System.out.println("DEBUG: Request URL: " + request.getRequestURL());
        System.out.println("DEBUG: Request URI: " + request.getRequestURI());
        
        // Log the exception stack trace for debugging
        exception.printStackTrace();
        
        response.sendRedirect("/customer-login?error=true");
    }
}
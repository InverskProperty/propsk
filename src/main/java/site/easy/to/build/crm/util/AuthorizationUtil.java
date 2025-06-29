package site.easy.to.build.crm.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import site.easy.to.build.crm.entity.User;

import java.util.Objects;

public class AuthorizationUtil {

    public static Boolean checkIfUserAuthorized(User employee, User loggedinUser) {
        return Objects.equals(loggedinUser.getId(), employee.getId());
    }

    public static Boolean hasRole(Authentication authentication, String role) {
        GrantedAuthority authorityToCheck = new SimpleGrantedAuthority(role);
        return authentication.getAuthorities().contains(authorityToCheck);
    }

    /**
     * Check if the authenticated user has any of the specified roles
     * @param authentication The Spring Security authentication object
     * @param roles Variable number of role strings to check
     * @return true if user has at least one of the specified roles, false otherwise
     */
    public static Boolean hasAnyRole(Authentication authentication, String... roles) {
        if (authentication == null || roles == null || roles.length == 0) {
            return false;
        }
        
        for (String role : roles) {
            if (hasRole(authentication, role)) {
                return true;
            }
        }
        return false;
    }
}
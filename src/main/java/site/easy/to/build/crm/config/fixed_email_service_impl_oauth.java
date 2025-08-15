// Fix for EmailServiceImpl.java line 71
// The error is: incompatible types: java.lang.String cannot be converted to site.easy.to.build.crm.entity.OAuthUser

@Override
public boolean isGmailApiAvailable(Authentication authentication) {
    try {
        if (authentication instanceof UsernamePasswordAuthenticationToken) {
            // Regular user login - Gmail API not available
            return false;
        }
        
        // Fixed: Use the correct method to get OAuthUser from AuthenticationUtils
        OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
        if (oAuthUser == null) {
            return false;
        }
        
        return oAuthUser.getGrantedScopes().contains(GoogleAccessService.SCOPE_GMAIL);
        
    } catch (Exception e) {
        logger.error("Error checking Gmail API availability", e);
        return false;
    }
}

// The issue was that the method was trying to cast a String to OAuthUser
// The fix is to use the proper AuthenticationUtils method to extract the OAuthUser
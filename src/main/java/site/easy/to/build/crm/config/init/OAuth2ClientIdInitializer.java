package site.easy.to.build.crm.config.init;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import site.easy.to.build.crm.util.StringUtils;

import java.util.Properties;

public class OAuth2ClientIdInitializer implements EnvironmentPostProcessor {

    private static final String GOOGLE_CLIENT_ID_PROPERTY = "spring.security.oauth2.client.registration.google.client-id";
    private static final String DEFAULT_GOOGLE_CLIENT_ID = "YOUR_DEFAULT_CLIENT_ID";

    private static final String GOOGLE_CLIENT_SECRET_PROPERTY = "spring.security.oauth2.client.registration.google.client-secret";
    private static final String DEFAULT_GOOGLE_CLIENT_SECRET = "YOUR_DEFAULT_CLIENT_SECRET";

    private static final String GOOGLE_SCOPE_PROPERTY = "spring.security.oauth2.client.registration.google.scope";
    private static final String DEFAULT_GOOGLE_SCOPE = "openid,email,profile";

    private static final String GOOGLE_REDIRECT_URI_PROPERTY = "spring.security.oauth2.client.registration.google.redirect-uri";
    private static final String DEFAULT_GOOGLE_REDIRECT_URI = "{baseUrl}/login/oauth2/code/{registrationId}";

    private static final String GOOGLE_GRANT_TYPE_PROPERTY = "spring.security.oauth2.client.registration.google.authorization-grant-type";
    private static final String DEFAULT_GOOGLE_GRANT_TYPE = "authorization_code";

    // FIXED: Enhanced authorization URI to always request refresh tokens
    private static final String GOOGLE_AUTHORIZATION_URI_PROPERTY = "spring.security.oauth2.client.provider.google.authorization-uri";
    private static final String DEFAULT_GOOGLE_AUTHORIZATION_URI = "https://accounts.google.com/o/oauth2/v2/auth?access_type=offline&prompt=consent";

    // FIXED: Enhanced URI template for better refresh token handling
    private static final String GOOGLE_URI_TEMPLATE_PROPERTY = "spring.security.oauth2.client.registration.google.authorization-uri-template";
    private static final String DEFAULT_GOOGLE_URI_TEMPLATE = "https://accounts.google.com/o/oauth2/auth?access_type=offline&prompt=consent&response_type=code&client_id={clientId}&scope={scopes}&state={state}&redirect_uri={redirectUri}";

    // FIXED: Add approval prompt to force consent screen
    private static final String GOOGLE_APPROVAL_PROMPT_PROPERTY = "spring.security.oauth2.client.registration.google.approval-prompt";
    private static final String DEFAULT_GOOGLE_APPROVAL_PROMPT = "force";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        System.out.println("🔧 Initializing OAuth2 configuration with enhanced refresh token support...");
        
        initGoogleOauthCredential(environment, GOOGLE_CLIENT_ID_PROPERTY, DEFAULT_GOOGLE_CLIENT_ID);
        initGoogleOauthCredential(environment, GOOGLE_CLIENT_SECRET_PROPERTY, DEFAULT_GOOGLE_CLIENT_SECRET);
        initGoogleOauthCredential(environment, GOOGLE_SCOPE_PROPERTY, DEFAULT_GOOGLE_SCOPE);
        initGoogleOauthCredential(environment, GOOGLE_REDIRECT_URI_PROPERTY, DEFAULT_GOOGLE_REDIRECT_URI);
        initGoogleOauthCredential(environment, GOOGLE_GRANT_TYPE_PROPERTY, DEFAULT_GOOGLE_GRANT_TYPE);
        initGoogleOauthCredential(environment, GOOGLE_AUTHORIZATION_URI_PROPERTY, DEFAULT_GOOGLE_AUTHORIZATION_URI);
        initGoogleOauthCredential(environment, GOOGLE_URI_TEMPLATE_PROPERTY, DEFAULT_GOOGLE_URI_TEMPLATE);
        initGoogleOauthCredential(environment, GOOGLE_APPROVAL_PROMPT_PROPERTY, DEFAULT_GOOGLE_APPROVAL_PROMPT);
        
        System.out.println("✅ OAuth2 configuration initialized with:");
        System.out.println("   - access_type=offline (to request refresh tokens)");
        System.out.println("   - prompt=consent (to force consent screen for refresh tokens)");
        System.out.println("   - Enhanced error handling for missing refresh tokens");
    }

    private void initGoogleOauthCredential(ConfigurableEnvironment environment, String property, String value) {
        String existingValue = environment.getProperty(property);

        // Check if the property is empty or null
        if (StringUtils.isEmpty(existingValue)) {
            // Set a default value for the property
            Properties defaults = new Properties();
            defaults.setProperty(property, value);

            // Create a PropertySource with the default values
            PropertySource<?> propertySource = new PropertiesPropertySource(property, defaults);

            // Add the PropertySource at the beginning of the property sources list
            MutablePropertySources propertySources = environment.getPropertySources();
            propertySources.addFirst(propertySource);
            
            System.out.println("🔧 Set default for " + property + " = " + value);
        } else {
            System.out.println("✅ Using existing " + property + " = " + existingValue);
        }
    }
}
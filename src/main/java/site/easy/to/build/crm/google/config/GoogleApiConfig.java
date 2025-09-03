package site.easy.to.build.crm.google.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import site.easy.to.build.crm.util.MemoryDiagnostics;

@Configuration
public class GoogleApiConfig {

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    @Bean
    public GoogleAuthorizationCodeFlowWrapper googleAuthorizationCodeFlowWrapper() {
        MemoryDiagnostics.logMemoryUsage("GoogleAuthorizationCodeFlowWrapper Bean Creation Start");
        
        GoogleAuthorizationCodeFlowWrapper wrapper = new GoogleAuthorizationCodeFlowWrapper(clientId, clientSecret);
        
        MemoryDiagnostics.logMemoryUsage("GoogleAuthorizationCodeFlowWrapper Bean Creation Complete");
        return wrapper;
    }
}
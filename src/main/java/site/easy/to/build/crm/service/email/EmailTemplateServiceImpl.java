package site.easy.to.build.crm.service.email;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.repository.EmailTemplateRepository;
import site.easy.to.build.crm.entity.EmailTemplate;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.util.List;

@Service
public class EmailTemplateServiceImpl implements EmailTemplateService {

    private final EmailTemplateRepository emailTemplateRepository;
    private final AuthenticationUtils authenticationUtils;
    private final UserService userService;

    public EmailTemplateServiceImpl(EmailTemplateRepository emailTemplateRepository, AuthenticationUtils authenticationUtils, UserService userService) {
        this.emailTemplateRepository = emailTemplateRepository;
        this.authenticationUtils = authenticationUtils;
        this.userService = userService;
    }


    @Override
    public EmailTemplate findByTemplateId(int id) {
        return emailTemplateRepository.findByTemplateId(id);
    }

    @Override
    public EmailTemplate findByName(String name) {
        return emailTemplateRepository.findByName(name);
    }

    @Override
    public List<EmailTemplate> getAllTemplates() {
        return emailTemplateRepository.findAll();
    }

    @Override
    public void save(EmailTemplate emailTemplate, Authentication authentication) {
        System.out.println("📧 EmailTemplateService.save() called");

        try {
            // Get user ID from authentication
            System.out.println("🔐 Getting user ID from authentication...");
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            System.out.println("   User ID result: " + userId);

            if (userId == -1) {
                System.err.println("❌ ERROR: User ID is -1 (user not found or not authenticated)");
                throw new UsernameNotFoundException("User not found");
            }
            System.out.println("✅ Valid user ID: " + userId);

            // Find user
            System.out.println("👤 Looking up User entity by ID: " + userId);
            User user = userService.findById(Long.valueOf(userId));

            if (user == null) {
                System.err.println("❌ ERROR: User not found in database for ID: " + userId);
                throw new UsernameNotFoundException("User not found with ID: " + userId);
            }
            System.out.println("✅ User found: " + user.getName() + " (" + user.getEmail() + ")");

            // Associate user with template
            System.out.println("🔗 Associating user with template...");
            emailTemplate.setUser(user);

            // Save to database
            System.out.println("💾 Saving EmailTemplate to repository...");
            EmailTemplate savedTemplate = emailTemplateRepository.save(emailTemplate);
            System.out.println("✅ Template saved! Generated ID: " + savedTemplate.getTemplateId());

        } catch (UsernameNotFoundException e) {
            System.err.println("❌ UsernameNotFoundException in save: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            System.err.println("❌ EXCEPTION in EmailTemplateService.save:");
            System.err.println("   Exception type: " + e.getClass().getName());
            System.err.println("   Message: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to save email template: " + e.getMessage(), e);
        }
    }

    @Override
    public List<EmailTemplate> findByUserId(int userId) {
        return emailTemplateRepository.findByUserId(userId);
    }

    @Override
    public void delete(int id) {
        EmailTemplate emailTemplate = emailTemplateRepository.findByTemplateId(id);
        emailTemplateRepository.delete(emailTemplate);
    }
}

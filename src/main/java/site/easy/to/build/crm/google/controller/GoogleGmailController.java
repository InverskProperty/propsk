package site.easy.to.build.crm.google.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.HttpResponseException;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.google.model.gmail.Attachment;
import site.easy.to.build.crm.google.model.gmail.*;
import site.easy.to.build.crm.google.model.gmail.EmailPage;
import site.easy.to.build.crm.google.service.gmail.GmailEmailService;
import site.easy.to.build.crm.google.service.gmail.GoogleGmailApiService;
import site.easy.to.build.crm.google.service.gmail.GoogleGmailLabelService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.google.GoogleServiceAccountService;
import site.easy.to.build.crm.util.AuthenticationUtils;
import site.easy.to.build.crm.google.util.PageTokenManager;
import site.easy.to.build.crm.util.SessionUtils;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;
import java.util.*;

@Controller
@RequestMapping("/employee/gmail")
public class GoogleGmailController {
    private final AuthenticationUtils authenticationUtils;
    private final GmailEmailService gmailEmailService;
    private final GoogleGmailApiService googleGmailApiService;
    private final GoogleGmailLabelService googleGmailLabelService;
    private final GoogleServiceAccountService googleServiceAccountService;
    private final CustomerService customerService;

    @Autowired
    public GoogleGmailController(AuthenticationUtils authenticationUtils, GmailEmailService gmailEmailService, GoogleGmailApiService googleGmailApiService, GoogleGmailLabelService googleGmailLabelService, GoogleServiceAccountService googleServiceAccountService, CustomerService customerService) {
        this.authenticationUtils = authenticationUtils;
        this.gmailEmailService = gmailEmailService;
        this.googleGmailApiService = googleGmailApiService;
        this.googleGmailLabelService = googleGmailLabelService;
        this.googleServiceAccountService = googleServiceAccountService;
        this.customerService = customerService;
    }

    @GetMapping("/send")
    public String showEmailForm(Model model, Authentication authentication) {

        boolean isOAuthUser = !(authentication instanceof UsernamePasswordAuthenticationToken);

        if (!isOAuthUser) {
            // For service account users, show limited email form
            model.addAttribute("isServiceAccount", true);
            model.addAttribute("message", "Service account mode - email sending uses shared service account");
            model.addAttribute("emailForm", new GmailEmailInfo());
            return "gmail/email-form";
        }

        OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
        // Check for either gmail.send OR gmail.modify (which includes send capabilities)
        boolean hasGmailAccess = oAuthUser.getGrantedScopes().contains("https://www.googleapis.com/auth/gmail.send") ||
                                 oAuthUser.getGrantedScopes().contains("https://www.googleapis.com/auth/gmail.modify");

        System.out.println("🔍 DEBUG: Checking Gmail access for user: " + oAuthUser.getEmail());
        System.out.println("   Granted scopes: " + oAuthUser.getGrantedScopes());
        System.out.println("   Has gmail.send: " + oAuthUser.getGrantedScopes().contains("https://www.googleapis.com/auth/gmail.send"));
        System.out.println("   Has gmail.modify: " + oAuthUser.getGrantedScopes().contains("https://www.googleapis.com/auth/gmail.modify"));
        System.out.println("   Has Gmail access: " + hasGmailAccess);

        if(!hasGmailAccess){
            String link = "employee/settings/google-services";
            String code = "403";
            String buttonText = "Grant Access";
            String message = "Please grant the app access to Gmail  in order to use this service";
            model.addAttribute("link",link);
            model.addAttribute("message",message);
            model.addAttribute("buttonText",buttonText);
            model.addAttribute("code",code);
            return "gmail/error";
        }

        // Load all customers for selection
        try {
            List<Customer> allCustomers = customerService.findAll();
            model.addAttribute("customers", allCustomers);
            model.addAttribute("customerType", "All Customers");
            model.addAttribute("pageTitle", "Compose Email");
            model.addAttribute("backUrl", "/employee/gmail/emails");
        } catch (Exception e) {
            System.err.println("⚠️ Error loading customers: " + e.getMessage());
            // Continue without customer list - allow manual recipient entry
            model.addAttribute("customers", new ArrayList<Customer>());
        }

        model.addAttribute("emailForm", new GmailEmailInfo());
        return "gmail/email-form";
    }

    @GetMapping("/send-draft/{draftId}")
    public String showEmailFormOfDraft(Model model, @PathVariable("draftId") String draftId, Authentication authentication) {
        OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
        GmailEmailInfo emailForm;
        try {
            emailForm = googleGmailApiService.getDraft(oAuthUser,draftId);
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
        model.addAttribute("emailForm", emailForm);
        return "gmail/email-form";
    }
    @PostMapping("/upload")
    public ResponseEntity<Void> uploadAttachment() {
        // Simulate a successful file upload by returning a 200 OK response
        return ResponseEntity.ok().build();
    }

    @PostMapping("/send")
    public String sendEmail(Authentication authentication,
                            @RequestParam(value = "recipient", required = false) String recipient,
                            @RequestParam(value = "selectedIds", required = false) List<Integer> selectedIds,
                            @RequestParam("subject") String subject,
                            @RequestParam("message") String message,
                            RedirectAttributes redirectAttributes) {

        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);

            // Collect all recipient emails
            List<String> recipientEmails = new ArrayList<>();

            // Add manually entered emails
            if (recipient != null && !recipient.trim().isEmpty()) {
                String[] emails = recipient.split(",");
                for (String email : emails) {
                    email = email.trim();
                    if (!email.isEmpty() && email.contains("@")) {
                        recipientEmails.add(email);
                    }
                }
            }

            // Add selected customer emails
            if (selectedIds != null && !selectedIds.isEmpty()) {
                for (Integer customerId : selectedIds) {
                    Customer customer = customerService.findByCustomerId(customerId.longValue());
                    if (customer != null && customer.getEmail() != null && !customer.getEmail().trim().isEmpty()) {
                        recipientEmails.add(customer.getEmail());
                    }
                }
            }

            // Validate we have at least one recipient
            if (recipientEmails.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                    "Please enter at least one email address or select customers.");
                return "redirect:/employee/gmail/send";
            }

            // Send email to each recipient
            int successCount = 0;
            for (String email : recipientEmails) {
                try {
                    googleGmailApiService.sendEmail(oAuthUser, email, subject, message, new ArrayList<>(), new ArrayList<>());
                    successCount++;
                } catch (Exception e) {
                    System.err.println("Failed to send email to " + email + ": " + e.getMessage());
                }
            }

            if (successCount > 0) {
                redirectAttributes.addFlashAttribute("successMessage",
                    String.format("Successfully sent %d out of %d emails.", successCount, recipientEmails.size()));
                return "redirect:/employee/gmail/emails/sent?success=true";
            } else {
                redirectAttributes.addFlashAttribute("errorMessage",
                    "Failed to send emails. Please check your Gmail API access and try again.");
                return "redirect:/employee/gmail/send";
            }

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                "Error sending email: " + e.getMessage());
            return "redirect:/employee/gmail/send";
        }
    }

    @PostMapping("/draft/ajax")
    @ResponseBody
    public ResponseEntity<String> saveDraftAjax(Authentication authentication, @ModelAttribute("emailForm") GmailEmailInfo emailForm,
                                                BindingResult bindingResult, HttpSession session,
                                                @RequestParam("files") String files) throws JsonProcessingException {
        OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
        ObjectMapper objectMapper = new ObjectMapper();
        List<Attachment> allFiles = objectMapper.readValue(files, new TypeReference<List<Attachment>>(){});

        try {
            if (emailForm.getDraftId() != null && !emailForm.getDraftId().isEmpty()) {
                googleGmailApiService.updateDraft(oAuthUser, emailForm.getDraftId(), emailForm.getRecipient(), emailForm.getSubject(), emailForm.getBody(), new ArrayList<>(), allFiles);
            } else {
                String draftId = googleGmailApiService.createDraft(oAuthUser, emailForm.getRecipient(), emailForm.getSubject(), emailForm.getBody(), new ArrayList<>(),allFiles);
                emailForm.setDraftId(draftId);
            }
            return ResponseEntity.ok(emailForm.getDraftId());
        } catch (IOException | GeneralSecurityException | MessagingException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error saving draft: " + e.getMessage());
        }
    }
    @GetMapping("/emails")
    public String showEmails(HttpSession session, Authentication authentication, Model model,
                             @RequestParam(value = "page", defaultValue = "1") int page) throws IOException {

        boolean isOAuthUser = !(authentication instanceof UsernamePasswordAuthenticationToken);

        if (!isOAuthUser) {
            // For service account users, show limited email view
            model.addAttribute("isServiceAccount", true);
            model.addAttribute("message", "Service account mode - email reading requires OAuth");
            model.addAttribute("emails", new java.util.ArrayList<>());
            return "gmail/emails";
        }

        EmailPage emailsPerPage;
        List<String> labels = null;
        int count;
        int draft;
        try {
            emailsPerPage = getEmailsByLabel(session, authentication, page, "inbox");
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
//            labels = googleGmailLabelService.fetchAllLabels(oAuthUser);
            count = googleGmailApiService.getEmailsCount(oAuthUser,"in:inbox category:primary is:unread");
            draft = googleGmailApiService.getEmailsCount(oAuthUser,"is:draft");
            googleGmailApiService.getEmailsCount(oAuthUser, "in:inbox category:primary is:unread");
        }catch (GeneralSecurityException | IOException e) {
            String link = "";
            String code = "400";
            String buttonText = "Go Home";
            String message = "There was a problem retrieving the emails now, Please try again later!";
            int prevPage = page;
            if (e instanceof HttpResponseException httpResponseException) {
                int statusCode = httpResponseException.getStatusCode();
                if(statusCode == 403){
                    code = "403";
                    link = "employee/settings/google-services";
                    buttonText = "Grant Access";
                    message = "Please grant the app access to Gmail  in order to use this service";
                }
            }else if(page>1){
                prevPage--;
                link = "employee/gmail/emails?page="+prevPage;
                buttonText = "GO Back";
                message = "There was a problem retrieving the emails at this page, Please try again later!";
            }

            model.addAttribute("link",link);
            model.addAttribute("message",message);
            model.addAttribute("buttonText",buttonText);
            model.addAttribute("code",code);
            return "gmail/error";
        }

        model.addAttribute("emails", emailsPerPage.getEmails());
        model.addAttribute("nextToken", emailsPerPage.getNextPageToken());
        model.addAttribute("labels", "inbox");
        model.addAttribute("count", count);
        model.addAttribute("draft",draft);

        addPaginationAttributes(model, page);
        return "gmail/emails";
    }

    @GetMapping("/emails-json")
    public @ResponseBody
    EmailPage getEmailsJson(HttpSession session, Authentication authentication,
                            @RequestParam(value = "page", defaultValue = "1") int page)
            throws GeneralSecurityException, IOException {
        if(page<1){
            page = 1;
        }
        return getEmailsByLabel(session, authentication, page, "inbox");
    }

    @GetMapping("/emails/{label}")
    public String showSentEmails(HttpServletRequest request, HttpSession session, Authentication authentication, Model model,
                                 @PathVariable("label") String label,
                                 @RequestParam(value = "page", defaultValue = "1") int page, @RequestParam(value = "success", required = false) boolean success) {

        boolean isOAuthUser = !(authentication instanceof UsernamePasswordAuthenticationToken);

        if (!isOAuthUser) {
            // For service account users, show limited email view
            model.addAttribute("isServiceAccount", true);
            model.addAttribute("message", "Service account mode - email reading requires OAuth");
            model.addAttribute("emails", new java.util.ArrayList<>());
            return "gmail/emails";
        }
        if (success) {
            model.addAttribute("successMessage", "Email sent successfully!");
        }
        EmailPage emailsPerPage;
        int count;
        int draft;
        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            emailsPerPage = getEmailsByLabel(session, authentication, page, label);
            count = googleGmailApiService.getEmailsCount(oAuthUser,"in:inbox category:primary is:unread");
            draft = googleGmailApiService.getEmailsCount(oAuthUser,"is:draft");
        }catch (GeneralSecurityException | IOException e) {
            int prevPage = page;
            String link = "";
            String buttonText = "Go Home";
            String message = "There was a problem retrieving the emails now, Please try again later!";
            String code = "400";
            if (e instanceof HttpResponseException httpResponseException) {
                int statusCode = httpResponseException.getStatusCode();
                if(statusCode == 403){
                    code = "403";
                    link = "employee/settings/google-services";
                    buttonText = "Grant Access";
                    message = "Please grant the app access to Gmail  in order to use this service";
                }
            }else if(page>1){
                prevPage--;
                link = "employee/gmail/emails/" + label + "?page=" + prevPage;
                buttonText = "GO Back";
                message = "There was a problem retrieving the emails at this page, Please try again later!";
            }

            model.addAttribute("link",link);
            model.addAttribute("message",message);
            model.addAttribute("buttonText",buttonText);
            model.addAttribute("code",code);
            return "gmail/error";
        }
        model.addAttribute("emails", emailsPerPage.getEmails());
        model.addAttribute("count", count);
        model.addAttribute("draft", draft);
        model.addAttribute("label", label);
        addPaginationAttributes(model, page);
        return "gmail/emails-label";
    }
    @GetMapping("/emails-json/{label}")
    public @ResponseBody
    EmailPage getSentEmailsJson(HttpSession session, Authentication authentication,
                                @PathVariable("label") String label,
                                @RequestParam(value = "page", defaultValue = "1") int page){
        try {
            return getEmailsByLabel(session, authentication, page, label);
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private EmailPage getEmailsByLabel(HttpSession session, Authentication authentication, int page, String label)
            throws GeneralSecurityException, IOException {
        int maxResult = 10;
        OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);

        // Retrieve or create a new PageTokenManager
        PageTokenManager pageTokenManager = Optional.ofNullable(SessionUtils.getSessionAttribute(session, "pageTokenManager", PageTokenManager.class))
                .orElseGet(PageTokenManager::new);

        String pageToken = null;
        if (page != 1) {
            pageToken = gmailEmailService.getPageTokenForPage(pageTokenManager, page, oAuthUser, maxResult, label);
        }
        EmailPage emailsPerPage = gmailEmailService.getEmailsPerPage(oAuthUser, maxResult, pageToken, label);

        // Update the PageTokenManager with the nextPageToken
        Optional.ofNullable(emailsPerPage.getNextPageToken())
                .ifPresent(nextPageToken -> {
                    pageTokenManager.setPageToken(page + 1, nextPageToken);
                    session.setAttribute("pageTokenManager", pageTokenManager);
                });
        emailsPerPage.setPage(page);
        return emailsPerPage;
    }

    private void addPaginationAttributes(Model model, int currentPage) {
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("nextPage", currentPage + 1);
        model.addAttribute("prevPage", currentPage - 1);
    }
    @GetMapping("/email-details/{id}")
    public String showEmailDetails(@PathVariable("id") String emailId, Authentication authentication, Model model, HttpSession session) {

        boolean isOAuthUser = !(authentication instanceof UsernamePasswordAuthenticationToken);

        if (!isOAuthUser) {
            // For service account users, show limited email details
            model.addAttribute("isServiceAccount", true);
            model.addAttribute("message", "Service account mode - email details require OAuth");
            return "gmail/email-details";
        }

        OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
        GmailEmailInfo emailInfo;
        int count;
        int draft;
        try {
            count = googleGmailApiService.getEmailsCount(oAuthUser,"in:inbox category:primary is:unread");
            draft = googleGmailApiService.getEmailsCount(oAuthUser,"is:draft");
            emailInfo = googleGmailApiService.getEmailDetails(oAuthUser,emailId);
            googleGmailApiService.updateEmail(oAuthUser,emailId);

        } catch (GeneralSecurityException | IOException e) {
            String link = "";
            String buttonText = "Go Home";
            String message = "There was a problem retrieving the email now, Please try again later!";
            String code = "400";
            if (e instanceof HttpResponseException httpResponseException) {
                int statusCode = httpResponseException.getStatusCode();
                if(statusCode == 403){
                    code = "403";
                    link = "employee/settings/google-services";
                    buttonText = "Grant Access";
                    message = "Please grant the app access to Gmail  in order to use this service";
                }
            }

            model.addAttribute("link",link);
            model.addAttribute("message",message);
            model.addAttribute("buttonText",buttonText);
            model.addAttribute("code",code);
            return "gmail/error";
        }
        model.addAttribute("emailInfo",emailInfo);
        model.addAttribute("count", count);
        model.addAttribute("draft", draft);
        return "gmail/email-details";
    }
    @PostMapping("/deleteEmail")
    public String deleteEmail(Authentication authentication,
                              @RequestParam("emailId") String emailId,
                              @RequestParam(value = "page", defaultValue = "1") int page,
                              RedirectAttributes redirectAttributes) {
        OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);

        gmailEmailService.deleteEmail(oAuthUser, emailId, redirectAttributes);

        return "redirect:/employee/gmail/emails?page=" + page;
    }
}

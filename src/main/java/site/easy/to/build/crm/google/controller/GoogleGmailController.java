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
import site.easy.to.build.crm.entity.Block;
import site.easy.to.build.crm.entity.DocumentTemplate;
import site.easy.to.build.crm.entity.EmailTemplate;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.entity.enums.CustomerType;
import site.easy.to.build.crm.google.service.docs.GoogleDocsApiService;
import site.easy.to.build.crm.repository.BlockRepository;
import site.easy.to.build.crm.repository.EmailTemplateRepository;
import site.easy.to.build.crm.service.correspondence.CorrespondenceService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.document.DocumentTemplateService;
import site.easy.to.build.crm.service.google.GoogleServiceAccountService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.util.AuthenticationUtils;
import site.easy.to.build.crm.google.util.PageTokenManager;
import site.easy.to.build.crm.util.SessionUtils;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/employee/gmail")
public class GoogleGmailController {
    private final AuthenticationUtils authenticationUtils;
    private final GmailEmailService gmailEmailService;
    private final GoogleGmailApiService googleGmailApiService;
    private final GoogleGmailLabelService googleGmailLabelService;
    private final GoogleServiceAccountService googleServiceAccountService;
    private final CustomerService customerService;
    private final DocumentTemplateService documentTemplateService;
    private final EmailTemplateRepository emailTemplateRepository;
    private final GoogleDocsApiService googleDocsApiService;
    private final CorrespondenceService correspondenceService;
    private final UserService userService;
    private final BlockRepository blockRepository;

    @Autowired
    public GoogleGmailController(AuthenticationUtils authenticationUtils, GmailEmailService gmailEmailService,
                                GoogleGmailApiService googleGmailApiService, GoogleGmailLabelService googleGmailLabelService,
                                GoogleServiceAccountService googleServiceAccountService, CustomerService customerService,
                                DocumentTemplateService documentTemplateService, EmailTemplateRepository emailTemplateRepository,
                                GoogleDocsApiService googleDocsApiService, CorrespondenceService correspondenceService,
                                UserService userService, BlockRepository blockRepository) {
        this.authenticationUtils = authenticationUtils;
        this.gmailEmailService = gmailEmailService;
        this.googleGmailApiService = googleGmailApiService;
        this.googleGmailLabelService = googleGmailLabelService;
        this.googleServiceAccountService = googleServiceAccountService;
        this.customerService = customerService;
        this.documentTemplateService = documentTemplateService;
        this.emailTemplateRepository = emailTemplateRepository;
        this.googleDocsApiService = googleDocsApiService;
        this.correspondenceService = correspondenceService;
        this.userService = userService;
        this.blockRepository = blockRepository;
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

        if(!hasGmailAccess){
            String link = "employee/settings/google-services";
            String code = "403";
            String buttonText = "Grant Access";
            String message = "Please grant the app access to Gmail in order to use this service";
            model.addAttribute("link",link);
            model.addAttribute("message",message);
            model.addAttribute("buttonText",buttonText);
            model.addAttribute("code",code);
            return "gmail/error";
        }

        // Load all data for enhanced compose page
        try {
            // Load all customers for filtering
            List<Customer> allCustomers = customerService.findAll();
            model.addAttribute("customers", allCustomers);

            // Load email templates
            List<EmailTemplate> emailTemplates = emailTemplateRepository.findAll();
            model.addAttribute("emailTemplates", emailTemplates);

            // Load active document templates
            List<DocumentTemplate> documentTemplates = documentTemplateService.findAllActive();
            model.addAttribute("documentTemplates", documentTemplates);

            // Load blocks for tenant filtering
            List<Block> blocks = blockRepository.findAll();
            model.addAttribute("blocks", blocks);

            // Add customer types for filtering
            model.addAttribute("customerTypes", CustomerType.values());

            // Add available merge fields
            model.addAttribute("mergeFields", documentTemplateService.getAvailableMergeFields());

            model.addAttribute("pageTitle", "Compose Email");
            model.addAttribute("backUrl", "/employee/gmail/emails");

        } catch (Exception e) {
            System.err.println("⚠️ Error loading compose page data: " + e.getMessage());
            e.printStackTrace();
            // Continue with empty lists
            model.addAttribute("customers", new ArrayList<Customer>());
            model.addAttribute("emailTemplates", new ArrayList<EmailTemplate>());
            model.addAttribute("documentTemplates", new ArrayList<DocumentTemplate>());
            model.addAttribute("blocks", new ArrayList<Block>());
            model.addAttribute("customerTypes", CustomerType.values());
            model.addAttribute("mergeFields", new ArrayList<String>());
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
                            @RequestParam(value = "selectedIds", required = false) List<Long> selectedIds,
                            @RequestParam("subject") String subject,
                            @RequestParam("message") String message,
                            @RequestParam(value = "emailTemplateId", required = false) Long emailTemplateId,
                            @RequestParam(value = "documentTemplateIds", required = false) List<Long> documentTemplateIds,
                            RedirectAttributes redirectAttributes) {

        System.out.println("📧 Enhanced Gmail compose send request");
        System.out.println("   Manual recipients: " + recipient);
        System.out.println("   Selected customer IDs: " + selectedIds);
        System.out.println("   Email template ID: " + emailTemplateId);
        System.out.println("   Document template IDs: " + documentTemplateIds);

        try {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            Long userId = Long.valueOf(authenticationUtils.getLoggedInUserId(authentication));
            User currentUser = userService.findById(userId);

            if (oAuthUser == null) {
                redirectAttributes.addFlashAttribute("errorMessage",
                    "Could not retrieve OAuth user. Please try logging in again.");
                return "redirect:/employee/gmail/send";
            }

            // Collect recipient customers
            List<Customer> recipientCustomers = new ArrayList<>();
            List<String> manualEmails = new ArrayList<>();

            // Add manually entered emails
            if (recipient != null && !recipient.trim().isEmpty()) {
                for (String email : recipient.split(",")) {
                    email = email.trim();
                    if (!email.isEmpty() && email.contains("@")) {
                        manualEmails.add(email);
                    }
                }
            }

            // Add selected customers
            if (selectedIds != null && !selectedIds.isEmpty()) {
                for (Long customerId : selectedIds) {
                    Customer customer = customerService.findByCustomerId(customerId);
                    if (customer != null && customer.getEmail() != null && !customer.getEmail().trim().isEmpty()) {
                        recipientCustomers.add(customer);
                    }
                }
            }

            // Validate we have recipients
            if (recipientCustomers.isEmpty() && manualEmails.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                    "Please enter at least one email address or select customers.");
                return "redirect:/employee/gmail/send";
            }

            // Generate bulk send batch ID for tracking
            String bulkSendBatchId = correspondenceService.generateBulkSendBatchId();
            int totalRecipients = recipientCustomers.size() + manualEmails.size();
            System.out.println("📤 Processing " + totalRecipients + " recipient(s), batch ID: " + bulkSendBatchId);

            // Generate documents if document templates are selected
            Map<Long, List<String>> customerPdfFiles = new HashMap<>();
            if (documentTemplateIds != null && !documentTemplateIds.isEmpty()) {
                System.out.println("📄 Generating documents for " + documentTemplateIds.size() + " template(s)");

                for (Customer customer : recipientCustomers) {
                    List<String> pdfIds = new ArrayList<>();
                    for (Long templateId : documentTemplateIds) {
                        try {
                            Optional<DocumentTemplate> templateOpt = documentTemplateService.findById(templateId);
                            if (templateOpt.isPresent()) {
                                DocumentTemplate template = templateOpt.get();
                                String documentName = template.getName() + "_" + customer.getName().replaceAll("[^a-zA-Z0-9]", "_");

                                // TODO: Get customer's Drive folder ID (for now use null = root)
                                String targetFolderId = null;

                                String pdfId = googleDocsApiService.generatePersonalizedDocument(
                                    oAuthUser,
                                    template.getGoogleDocsTemplateId(),
                                    customer,
                                    null, // Additional merge data
                                    targetFolderId,
                                    documentName
                                );
                                pdfIds.add(pdfId);
                                System.out.println("   ✅ Generated " + template.getName() + " for " + customer.getName());
                            }
                        } catch (Exception e) {
                            System.err.println("   ❌ Failed to generate document for " + customer.getName() + ": " + e.getMessage());
                        }
                    }
                    if (!pdfIds.isEmpty()) {
                        customerPdfFiles.put(customer.getCustomerId(), pdfIds);
                    }
                }
            }

            // Send emails to customers
            int successCount = 0;
            for (Customer customer : recipientCustomers) {
                try {
                    // Personalize subject and message
                    String personalizedSubject = replaceCustomerMergeFields(subject, customer);
                    String personalizedMessage = replaceCustomerMergeFields(message, customer);

                    // Prepare attachments
                    List<Attachment> attachments = new ArrayList<>();
                    List<String> pdfIds = customerPdfFiles.get(customer.getCustomerId());
                    if (pdfIds != null) {
                        for (String pdfId : pdfIds) {
                            byte[] pdfBytes = googleDocsApiService.downloadPdfAsBytes(oAuthUser, pdfId);
                            attachments.add(new Attachment(
                                "document_" + System.currentTimeMillis() + ".pdf",
                                java.util.Base64.getEncoder().encodeToString(pdfBytes),
                                "application/pdf",
                                pdfBytes.length
                            ));
                        }
                    }

                    // Send email
                    googleGmailApiService.sendEmail(oAuthUser, customer.getEmail(), personalizedSubject,
                                                    personalizedMessage, new ArrayList<>(), attachments);

                    // Log correspondence
                    correspondenceService.logSentEmail(customer, currentUser, personalizedSubject, personalizedMessage,
                                                      List.of(customer.getEmail()), pdfIds, emailTemplateId,
                                                      documentTemplateIds, null, bulkSendBatchId);

                    successCount++;
                    System.out.println("   ✅ Sent to " + customer.getEmail());

                    // Rate limiting
                    Thread.sleep(100);

                } catch (Exception e) {
                    System.err.println("   ❌ Failed for " + customer.getEmail() + ": " + e.getMessage());
                    correspondenceService.logFailedEmail(customer, currentUser, subject, message,
                                                        e.getMessage(), bulkSendBatchId);
                }
            }

            // Send to manual emails (no personalization, no correspondence tracking)
            for (String email : manualEmails) {
                try {
                    googleGmailApiService.sendEmail(oAuthUser, email, subject, message, new ArrayList<>(), new ArrayList<>());
                    successCount++;
                    System.out.println("   ✅ Sent to manual recipient " + email);
                    Thread.sleep(100);
                } catch (Exception e) {
                    System.err.println("   ❌ Failed for manual recipient " + email + ": " + e.getMessage());
                }
            }

            System.out.println("📊 Send complete: " + successCount + "/" + totalRecipients + " successful");

            if (successCount > 0) {
                redirectAttributes.addFlashAttribute("successMessage",
                    String.format("Successfully sent %d out of %d emails.", successCount, totalRecipients));
                return "redirect:/employee/gmail/emails/sent?success=true";
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Failed to send emails. Please check logs.");
                return "redirect:/employee/gmail/send";
            }

        } catch (Exception e) {
            System.err.println("❌ Error in enhanced email send: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
            return "redirect:/employee/gmail/send";
        }
    }

    /**
     * Replace merge fields in text with customer data
     */
    private String replaceCustomerMergeFields(String text, Customer customer) {
        if (text == null) return "";

        Map<String, String> mergeData = googleDocsApiService.buildCustomerMergeData(customer);
        String result = text;

        for (Map.Entry<String, String> entry : mergeData.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace(placeholder, value);
        }

        return result;
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

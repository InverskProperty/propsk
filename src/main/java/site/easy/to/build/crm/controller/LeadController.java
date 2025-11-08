package site.easy.to.build.crm.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.entity.settings.LeadEmailSettings;
import site.easy.to.build.crm.service.property.PropertyViewingService;
import site.easy.to.build.crm.service.LeadConversionService;
import site.easy.to.build.crm.google.model.calendar.EventDisplay;
import site.easy.to.build.crm.google.model.drive.GoogleDriveFolder;
import site.easy.to.build.crm.google.model.gmail.Attachment;
import site.easy.to.build.crm.google.service.acess.GoogleAccessService;
import site.easy.to.build.crm.google.service.calendar.GoogleCalendarApiService;
import site.easy.to.build.crm.google.service.drive.GoogleDriveApiService;
import site.easy.to.build.crm.google.service.gmail.GoogleGmailApiService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.drive.GoogleDriveFileService;
import site.easy.to.build.crm.service.file.FileService;
import site.easy.to.build.crm.service.lead.LeadActionService;
import site.easy.to.build.crm.service.lead.LeadService;
import site.easy.to.build.crm.service.settings.LeadEmailSettingsService;
import site.easy.to.build.crm.service.user.UserService;
import site.easy.to.build.crm.util.*;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
@RequestMapping("/employee/lead")
public class LeadController {

    private final LeadService leadService;
    private final AuthenticationUtils authenticationUtils;
    private final UserService userService;
    private final CustomerService customerService;
    private final LeadActionService leadActionService;
    private final GoogleCalendarApiService googleCalendarApiService;
    private final FileService fileService;
    private final GoogleDriveApiService googleDriveApiService;
    private final GoogleDriveFileService googleDriveFileService;
    private final FileUtil fileUtil;
    private final LeadEmailSettingsService leadEmailSettingsService;
    private final GoogleGmailApiService googleGmailApiService;
    private final EntityManager entityManager;
    private final LeadConversionService leadConversionService;
    private PropertyViewingService propertyViewingService;

    @Autowired
    public LeadController(LeadService leadService, AuthenticationUtils authenticationUtils, UserService userService, CustomerService customerService,
                          LeadActionService leadActionService, GoogleCalendarApiService googleCalendarApiService, FileService fileService,
                          GoogleDriveApiService googleDriveApiService, GoogleDriveFileService googleDriveFileService, FileUtil fileUtil,
                          LeadEmailSettingsService leadEmailSettingsService, GoogleGmailApiService googleGmailApiService, EntityManager entityManager,
                          LeadConversionService leadConversionService) {
        this.leadService = leadService;
        this.authenticationUtils = authenticationUtils;
        this.userService = userService;
        this.customerService = customerService;
        this.leadActionService = leadActionService;
        this.googleCalendarApiService = googleCalendarApiService;
        this.fileService = fileService;
        this.googleDriveApiService = googleDriveApiService;
        this.googleDriveFileService = googleDriveFileService;
        this.fileUtil = fileUtil;
        this.leadEmailSettingsService = leadEmailSettingsService;
        this.googleGmailApiService = googleGmailApiService;
        this.entityManager = entityManager;
        this.leadConversionService = leadConversionService;
    }

    @Autowired(required = false)
    public void setPropertyViewingService(PropertyViewingService propertyViewingService) {
        this.propertyViewingService = propertyViewingService;
    }

    @GetMapping("/show/{id}")
    public String showDetails(@PathVariable("id") int id, Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(Long.valueOf(userId));
        if(loggedInUser.isInactiveUser()) {
            return "error/account-inactive";
        }

        Lead lead = leadService.findByLeadId(id);

        if(lead == null) {
            return  "error/not-found";
        }
        User employee = lead.getEmployee();
        if(!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") && !AuthorizationUtil.checkIfUserAuthorized(employee,loggedInUser)) {
            return "error/access-denied";
        }

        EventDisplay eventDisplay = null;
        String eventId = lead.getMeetingId();
        List<File> files = fileService.findByLeadId(id);
        List<Attachment> attachments = new ArrayList<>();
        for (File file : files) {
            String base64Data = Base64.getEncoder().encodeToString(file.getFileData());
            Attachment attachment = new Attachment(file.getFileName(), base64Data, file.getFileType());
            attachments.add(attachment);
        }
        if (!(authentication instanceof UsernamePasswordAuthenticationToken) && eventId != null && !eventId.isEmpty() && googleCalendarApiService != null) {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            try {
                eventDisplay = googleCalendarApiService.getEvent("primary", oAuthUser, eventId);
            } catch (IOException | GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        }

        // Get viewings for this lead if service is available
        List<PropertyViewing> viewings = new ArrayList<>();
        if (propertyViewingService != null) {
            viewings = propertyViewingService.getViewingsForLead(lead);
        }

        model.addAttribute("lead", lead);
        model.addAttribute("event", eventDisplay);
        model.addAttribute("attachments", attachments);
        model.addAttribute("viewings", viewings);
        return "lead/show-details";
    }

    @GetMapping("/assigned-leads")
    public String showAssignedEmployeeLeads(Authentication authentication, Model model) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        List<Lead> leads = leadService.findAssignedLeads(userId);
        model.addAttribute("leads", leads);
        return "lead/show-my-leads";
    }

    @GetMapping("/created-leads")
    public String showCreatedEmployeeLeads(Authentication authentication, Model model) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        List<Lead> leads = leadService.findCreatedLeads(userId);
        model.addAttribute("leads", leads);
        return "lead/show-my-leads";
    }

    @GetMapping("/manager/all-leads")
    public String showAllLeads(Model model) {
        List<Lead> leads = leadService.findAll();
        model.addAttribute("leads", leads);
        return "lead/show-my-leads";
    }

    @PostMapping("/upload")
    public ResponseEntity<Void> uploadAttachment() {
        // Simulate a successful file upload by returning a 200 OK response
        return ResponseEntity.ok().build();
    }

    @GetMapping("/create")
    public String showCreatingForm(Model model, Authentication authentication) {
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User user = userService.findById(Long.valueOf(userId));
        if(user.isInactiveUser()) {
            return "error/account-inactive";
        }
        populateModelAttributes(model, authentication, user);
        model.addAttribute("lead", new Lead());
        return "lead/create-lead";
    }

    @PostMapping("/create")
    public String createLead(@ModelAttribute("lead") @Validated Lead lead, BindingResult bindingResult,
                             @RequestParam(value = "customerId", required = false) Long customerId, @RequestParam("employeeId") int employeeId,
                             Authentication authentication, @RequestParam("allFiles")@Nullable String files,
                             @RequestParam("folderId") @Nullable String folderId, Model model) throws JsonProcessingException {

        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User manager = userService.findById(Long.valueOf(userId));
        if(manager.isInactiveUser()) {
            return "error/account-inactive";
        }

        if(bindingResult.hasErrors()) {
            User user = userService.findById(Long.valueOf(userId));
            populateModelAttributes(model, authentication, user);
            return "lead/create-lead";
        }

        User employee = userService.findById(Long.valueOf(employeeId));
        Customer customer = null;
        if (customerId != null) {
            customer = customerService.findByCustomerId(customerId);
        }
        if(AuthorizationUtil.hasRole(authentication, "ROLE_EMPLOYEE") && (employee.getId() != userId)) {
            return "error/500";
        }
        lead.setCustomer(customer);
        lead.setEmployee(employee);
        lead.setManager(manager);
        lead.setGoogleDriveFolderId(folderId);
        lead.setCreatedAt(LocalDateTime.now());

        ObjectMapper objectMapper = new ObjectMapper();
        List<Attachment> allFiles = objectMapper.readValue(files, new TypeReference<List<Attachment>>() {
        });

        if (!(authentication instanceof UsernamePasswordAuthenticationToken) && googleDriveApiService != null) {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            try {
                if (folderId != null && !folderId.isEmpty()) {
                    googleDriveApiService.checkFolderExists(oAuthUser, folderId);
                }
            } catch (IOException | GeneralSecurityException e) {
                return "error/500";
            }
        }

        Lead createdLead = leadService.save(lead);
        fileUtil.saveFiles(allFiles, createdLead);

        if (lead.getGoogleDrive() != null) {
            fileUtil.saveGoogleDriveFiles(authentication, allFiles, folderId, createdLead);
        }

        if (lead.getStatus().equals("meeting-to-schedule")) {
            return "redirect:/employee/calendar/create-event?leadId=" + lead.getLeadId();
        }
        if(AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return "redirect:/employee/lead/created-leads";
        }
        return "redirect:/employee/lead/assigned-leads";
    }

    @GetMapping("/update/{id}")
    public String showUpdatingForm(Model model, @PathVariable("id") int id, Authentication authentication) {

        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(Long.valueOf(userId));
        if(loggedInUser.isInactiveUser()) {
            return "error/account-inactive";
        }

        Lead lead = leadService.findByLeadId(id);

        if(lead == null) {
            return "error/not-found";
        }

        User employee = lead.getEmployee();
        if(!AuthorizationUtil.checkIfUserAuthorized(employee,loggedInUser) && !AuthorizationUtil.hasRole(authentication,"ROLE_MANAGER")) {
            return "error/access-denied";
        }

        List<User> employees = new ArrayList<>();
        List<Customer> customers = new ArrayList<>();

        if(AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            employees = userService.findAll();
            customers = customerService.findAll();
        } else {
            employees.add(loggedInUser);
            //In case Employee's manager assign lead for the employee with a customer that's not created by this employee
            //As a result of that the employee mustn't change the customer
            if(!Objects.equals(employee.getId(), lead.getManager().getId())) {
                customers.add(lead.getCustomer());
            } else {
                customers = customerService.findByUserId(loggedInUser.getId().longValue());
            }
        }


        List<File> files = lead.getFiles();

        List<Attachment> attachments = new ArrayList<>();
        for (File file : files) {
            String base64Data = Base64.getEncoder().encodeToString(file.getFileData());
            Attachment attachment = new Attachment(file.getFileName(), base64Data, file.getFileType());
            attachments.add(attachment);
        }

        List<GoogleDriveFolder> folders = null;

        boolean hasGoogleDriveAccess = false;
        if (!(authentication instanceof UsernamePasswordAuthenticationToken) && googleDriveApiService != null) {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            List<GoogleDriveFile> googleDriveFiles = lead.getGoogleDriveFiles();
            try {
                hasGoogleDriveAccess = authenticationUtils.checkIfAppHasAccess(GoogleAccessService.SCOPE_DRIVE, oAuthUser);
                if (hasGoogleDriveAccess) {
                    folders = googleDriveApiService.listFolders(oAuthUser);
                }

                // Check if the file got deleted using his Google Drive
                fileUtil.updateFilesBasedOnGoogleDriveFiles(oAuthUser,googleDriveFiles,lead);

            } catch (IOException | GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        }

        model.addAttribute("lead", lead);
        model.addAttribute("employees", employees);
        model.addAttribute("customers", customers);
        model.addAttribute("attachments", attachments);
        model.addAttribute("folders", folders);
        model.addAttribute("hasGoogleDriveAccess",hasGoogleDriveAccess);
        return "lead/update-lead";
    }

    @PostMapping("/update")
    public String updateLead(@ModelAttribute("lead") @Validated Lead lead, BindingResult bindingResult, @RequestParam(value = "customerId", required = false) Long customerId,
                             @RequestParam("employeeId") int employeeId, Authentication authentication, Model model,
                             @RequestParam("allFiles") @Nullable String files, @RequestParam("folderId") @Nullable String folderId) throws JsonProcessingException {

        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(Long.valueOf(userId));
        if(loggedInUser.isInactiveUser()) {
            return "error/account-inactive";
        }
        Lead currLead = leadService.findByLeadId(lead.getLeadId());
        if(currLead == null) {
            return "error/500";
        }

        User manager = currLead.getManager();
        User employee = userService.findById(Long.valueOf(employeeId));
        Customer customer = null;
        if (customerId != null) {
            customer = customerService.findByCustomerId(customerId);
        }
        if(employee == null || manager == null) {
            return "error/500";
        }

        //check in case the employee created a lead for him/her self,
        // they won't be able to assign lead for customer that isn't created themselves
        if(manager.getId() == employeeId) {
            if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") && customer != null && customer.getUser().getId() != userId) {
                return "error/500";
            }
        } else {
            if(!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") &&
               currLead.getCustomer() != null &&
               (customerId == null || !currLead.getCustomer().getCustomerId().equals(customerId))) {
                return "error/500";
            }
        }

        if(AuthorizationUtil.hasRole(authentication, "ROLE_EMPLOYEE") && employee.getId() != userId) {
            return "error/500";
        }

        if(bindingResult.hasErrors()) {
            List<User> employees = new ArrayList<>();
            List<Customer> customers = new ArrayList<>();

            if(AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                employees = userService.findAll();
                customers = customerService.findAll();
            } else {
                employees.add(loggedInUser);
                //In case Employee's manager assign lead for the employee with a customer that's not created by this employee
                //As a result of that the employee mustn't change the customer
                if(!Objects.equals(employee.getId(), lead.getManager().getId())) {
                    customers.add(lead.getCustomer());
                } else {
                    customers = customerService.findByUserId(loggedInUser.getId().longValue());
                }
            }
            Lead tempLead = leadService.findByLeadId(lead.getLeadId());
            lead.setEmployee(tempLead.getEmployee());
            lead.setManager(tempLead.getManager());
            lead.setLeadActions(tempLead.getLeadActions());
            lead.setCustomer(tempLead.getCustomer());
            List<File> filesArray = tempLead.getFiles();

            List<Attachment> attachments = new ArrayList<>();
            for (File file : filesArray) {
                String base64Data = Base64.getEncoder().encodeToString(file.getFileData());
                Attachment attachment = new Attachment(file.getFileName(), base64Data, file.getFileType());
                attachments.add(attachment);
            }

            List<GoogleDriveFolder> folders = null;

            boolean hasGoogleDriveAccess = false;
            if (!(authentication instanceof UsernamePasswordAuthenticationToken) && googleDriveApiService != null) {
                OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
                List<GoogleDriveFile> googleDriveFiles = tempLead.getGoogleDriveFiles();
                try {
                    hasGoogleDriveAccess = authenticationUtils.checkIfAppHasAccess(GoogleAccessService.SCOPE_DRIVE, oAuthUser);
                    if (hasGoogleDriveAccess) {
                        folders = googleDriveApiService.listFolders(oAuthUser);
                    }

                    // Check if the file got deleted using his Google Drive
                    fileUtil.updateFilesBasedOnGoogleDriveFiles(oAuthUser,googleDriveFiles,tempLead);

                } catch (IOException | GeneralSecurityException e) {
                    throw new RuntimeException(e);
                }
            }
            model.addAttribute("employees", employees);
            model.addAttribute("customers", customers);
            model.addAttribute("attachments", attachments);
            model.addAttribute("folders", folders);
            model.addAttribute("hasGoogleDriveAccess",hasGoogleDriveAccess);
            return "lead/update-lead";
        }

        Lead prevLead = leadService.findByLeadId(lead.getLeadId());
        Lead originalLead = new Lead();
        BeanUtils.copyProperties(prevLead,originalLead);
        List<File> oldFiles = fileService.findByLeadId(lead.getLeadId());
        List<GoogleDriveFile> oldGoogleDriveFiles = new ArrayList<>();
        if(googleDriveFileService != null) {
            oldGoogleDriveFiles = googleDriveFileService.getAllDriveFileByLeadId(lead.getLeadId());
        }

        ObjectMapper objectMapper = new ObjectMapper();
        List<Attachment> allFiles = objectMapper.readValue(files, new TypeReference<List<Attachment>>() {
        });

        lead.setCustomer(customer);
        lead.setEmployee(employee);
        lead.setManager(manager);
        lead.setGoogleDriveFolderId(folderId);
        lead.setCreatedAt(originalLead.getCreatedAt());
        fileUtil.deleteOldFiles(oldFiles, lead);
        if (!(authentication instanceof UsernamePasswordAuthenticationToken) && googleDriveFileService != null) {
            fileUtil.deleteGoogleDriveFiles(oldGoogleDriveFiles, authentication);
        }
        fileUtil.saveFiles(allFiles, lead);
        if(!(authentication instanceof UsernamePasswordAuthenticationToken) && lead.getGoogleDrive() && googleDriveApiService != null) {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            try {
                if (folderId != null && !folderId.isEmpty()) {
                    googleDriveApiService.checkFolderExists(oAuthUser, folderId);
                }
            } catch (IOException | GeneralSecurityException e) {
                return "error/500";
            }
            fileUtil.saveGoogleDriveFiles(authentication,allFiles,folderId,lead);
        }
        Lead CurrentLead = leadService.save(lead);
        saveLeadActions(lead, prevLead);
        List<String> properties = DatabaseUtil.getColumnNames(entityManager, Lead.class);
        Map<String, Pair<String ,String>> changes = LogEntityChanges.trackChanges(originalLead,CurrentLead, properties);

        boolean isGoogleUser = !(authentication instanceof UsernamePasswordAuthenticationToken);

        if(isGoogleUser && googleGmailApiService != null && customer != null) {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            if(oAuthUser.getGrantedScopes().contains(GoogleAccessService.SCOPE_GMAIL)) {
                try {
                    processEmailSettingsChanges(changes, userId, oAuthUser, customer);
                } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return "redirect:/employee/lead/assigned-leads";
    }

    @PostMapping("/delete/{id}")
    public String deleteLead(@PathVariable("id") int id, Authentication authentication) {
        Lead lead = leadService.findByLeadId(id);

        User employee = lead.getEmployee();
        int userId = authenticationUtils.getLoggedInUserId(authentication);
        User loggedInUser = userService.findById(Long.valueOf(userId));
        if(loggedInUser.isInactiveUser()) {
            return "error/account-inactive";
        }
        if(!AuthorizationUtil.checkIfUserAuthorized(employee,loggedInUser)) {
            return "error/access-denied";
        }

        leadService.delete(lead);
        return "redirect:/employee/lead/created-leads";
    }

    @PatchMapping("/{id}/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateLeadStatus(@PathVariable("id") int id,
                                                                 @RequestBody Map<String, String> request,
                                                                 Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if(loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(403).body(Map.of("success", false, "message", "Account is inactive"));
            }

            Lead lead = leadService.findByLeadId(id);
            if(lead == null) {
                return ResponseEntity.status(404).body(Map.of("success", false, "message", "Lead not found"));
            }

            User employee = lead.getEmployee();
            if(!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") && !AuthorizationUtil.checkIfUserAuthorized(employee, loggedInUser)) {
                return ResponseEntity.status(403).body(Map.of("success", false, "message", "Access denied"));
            }

            String newStatus = request.get("status");
            if(newStatus == null || newStatus.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Status is required"));
            }

            LeadStatus previousStatus = lead.getStatus();
            lead.setStatusValue(newStatus); // Use setStatusValue for string conversion
            leadService.save(lead);

            // Create a lead action to track the status change
            LeadAction leadAction = new LeadAction();
            leadAction.setLead(lead);
            leadAction.setTimestamp(LocalDateTime.now());
            String notes = request.get("notes");
            String action = "Status changed from " + previousStatus + " to " + newStatus;
            if (notes != null && !notes.trim().isEmpty()) {
                action += ". Notes: " + notes;
            }
            leadAction.setAction(action);
            leadActionService.save(leadAction);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Lead status updated successfully",
                "leadId", lead.getLeadId(),
                "newStatus", newStatus
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Error updating status: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/schedule-viewing")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> scheduleViewing(@PathVariable("id") int id,
                                                                @RequestBody Map<String, Object> request,
                                                                Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if(loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(403).body(Map.of("success", false, "message", "Account is inactive"));
            }

            Lead lead = leadService.findByLeadId(id);
            if(lead == null) {
                return ResponseEntity.status(404).body(Map.of("success", false, "message", "Lead not found"));
            }

            // Get the property from the lead or its letting instruction
            Property property = null;
            if (lead.getProperty() != null) {
                property = lead.getProperty();
            } else if (lead.getLettingInstruction() != null && lead.getLettingInstruction().getProperty() != null) {
                property = lead.getLettingInstruction().getProperty();
            }

            if (property == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No property associated with this lead"));
            }

            String datetime = (String) request.get("scheduledDatetime");
            Integer durationMinutes = (Integer) request.get("durationMinutes");
            String viewingType = (String) request.get("viewingType");
            String notes = (String) request.get("notes");

            LocalDateTime scheduledDatetime = LocalDateTime.parse(datetime);

            // Create PropertyViewing entity if service is available
            PropertyViewing viewing = null;
            if (propertyViewingService != null) {
                viewing = propertyViewingService.scheduleViewing(
                    lead,
                    property,
                    scheduledDatetime,
                    durationMinutes != null ? durationMinutes : 30,
                    viewingType != null ? viewingType : "IN_PERSON",
                    loggedInUser
                );

                if (notes != null && !notes.trim().isEmpty()) {
                    viewing.setNotes(notes);
                    propertyViewingService.save(viewing);
                }

                // Link to letting instruction if available
                if (lead.getLettingInstruction() != null) {
                    viewing.setLettingInstruction(lead.getLettingInstruction());
                    propertyViewingService.save(viewing);
                }
            }

            // Update lead status
            LeadStatus previousStatus = lead.getStatus();
            lead.setStatus(LeadStatus.VIEWING_SCHEDULED);
            leadService.save(lead);

            // Create a lead action to track the viewing scheduling
            LeadAction leadAction = new LeadAction();
            leadAction.setLead(lead);
            leadAction.setTimestamp(LocalDateTime.now());

            StringBuilder action = new StringBuilder("Viewing scheduled for " + datetime);
            if (durationMinutes != null) {
                action.append(" (" + durationMinutes + " minutes)");
            }
            if (viewingType != null) {
                action.append(" - Type: " + viewingType);
            }
            if (notes != null && !notes.trim().isEmpty()) {
                action.append(". Notes: " + notes);
            }

            leadAction.setAction(action.toString());
            leadActionService.save(leadAction);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Viewing scheduled successfully",
                "leadId", lead.getLeadId(),
                "viewingId", viewing != null ? viewing.getId() : null
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Error scheduling viewing: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/convert")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> convertLead(@PathVariable("id") int id,
                                                            @RequestBody Map<String, Object> request,
                                                            Authentication authentication) {
        try {
            int userId = authenticationUtils.getLoggedInUserId(authentication);
            User loggedInUser = userService.findById(Long.valueOf(userId));

            if(loggedInUser.isInactiveUser()) {
                return ResponseEntity.status(403).body(Map.of("success", false, "message", "Account is inactive"));
            }

            Lead lead = leadService.findByLeadId(id);
            if(lead == null) {
                return ResponseEntity.status(404).body(Map.of("success", false, "message", "Lead not found"));
            }

            // Extract conversion parameters from request
            String leaseStartDateStr = (String) request.get("leaseStartDate");
            String leaseEndDateStr = (String) request.get("leaseEndDate");
            Double monthlyRent = ((Number) request.get("monthlyRent")).doubleValue();
            Double deposit = ((Number) request.get("deposit")).doubleValue();

            // Parse dates
            LocalDate leaseStartDate = leaseStartDateStr != null ? LocalDate.parse(leaseStartDateStr) : LocalDate.now();
            LocalDate leaseEndDate = leaseEndDateStr != null ? LocalDate.parse(leaseEndDateStr) : leaseStartDate.plusYears(1);

            // Create conversion request
            LeadConversionService.ConversionRequest conversionRequest = new LeadConversionService.ConversionRequest(
                leaseStartDate,
                leaseEndDate,
                BigDecimal.valueOf(monthlyRent),
                BigDecimal.valueOf(deposit)
            );

            // Use the unified conversion service (handles Lead, Customer, Instruction, Invoice atomically)
            LeadConversionService.ConversionResult result = leadConversionService.convertLeadToTenant(
                Long.valueOf(id),
                conversionRequest,
                userId
            );

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Lead converted successfully to tenant",
                "leadId", result.getLead().getLeadId(),
                "customerId", result.getCustomer().getCustomerId(),
                "instructionId", result.getInstruction() != null ? result.getInstruction().getId() : null,
                "instructionReference", result.getInstruction() != null ? result.getInstruction().getInstructionReference() : null
            ));
        } catch (IllegalStateException | IllegalArgumentException e) {
            // Business logic errors (validation failures)
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            // Unexpected errors
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Error converting lead: " + e.getMessage()));
        }
    }

    @PostMapping("/save-attachment/ajax")
    @ResponseBody
    public ResponseEntity<String> saveAttachmentAjax(Authentication authentication) {
        return ResponseEntity.ok("success");
    }

    @PostMapping("/drive/ajax-create")
    @ResponseBody
    public ResponseEntity<Map<String, String>> createGoogleDriveFolder(Authentication authentication, @RequestParam("folderName") String folderName) {
        OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
        String folderId = null;
        if(googleDriveApiService != null) {
            try {
                folderId = googleDriveApiService.createFolder(oAuthUser, folderName);
            } catch (IOException | GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        }
        Map<String, String> response = new HashMap<>();
        response.put("folderId", folderId);
        response.put("folderName", folderName);
        return ResponseEntity.ok(response);
    }

    private StringBuilder getChanges(Lead prevLead, Lead lead) {
        StringBuilder changes = new StringBuilder();
        if (!prevLead.getName().equals(lead.getName())) {
            changes.append("The lead's name changes from ").append(prevLead.getName()).append(" To ").append(lead.getName()).append('.');
        }
        if (!prevLead.getPhone().equals(lead.getPhone())) {
            changes.append("The lead's phone changes from ").append(prevLead.getPhone()).append(" To ").append(lead.getPhone()).append('.');
        }
        if (!prevLead.getEmployee().equals(lead.getEmployee())) {
            changes.append("The lead's employee changes from ").append(prevLead.getEmployee().getUsername()).append(" To ").append(lead.getEmployee().getUsername()).append('.');
        }
        // Handle customer changes (customer can be null)
        Customer prevCustomer = prevLead.getCustomer();
        Customer newCustomer = lead.getCustomer();
        if (prevCustomer != null && newCustomer != null && !prevCustomer.equals(newCustomer)) {
            changes.append("The lead's customer changes from ").append(prevCustomer.getName()).append(" To ").append(newCustomer.getName()).append('.');
        } else if (prevCustomer == null && newCustomer != null) {
            changes.append("The lead's customer is set to ").append(newCustomer.getName()).append('.');
        } else if (prevCustomer != null && newCustomer == null) {
            changes.append("The lead's customer ").append(prevCustomer.getName()).append(" has been removed.");
        }
        return changes;
    }

    private void saveLeadActions(Lead lead, Lead prevLead) {
        StringBuilder changes = getChanges(prevLead, lead);
        List<LeadAction> leadActions = new ArrayList<>();
        if (!changes.isEmpty()) {
            LeadAction leadAction = new LeadAction();
            leadAction.setLead(lead);
            leadAction.setTimestamp(LocalDateTime.now());
            leadAction.setAction(changes.toString());
            leadActions.add(leadAction);
            leadActionService.save(leadAction);
        }
        lead.setLeadActions(leadActions);
    }

    public void processEmailSettingsChanges(Map<String, Pair<String, String>> changes, int userId, OAuthUser oAuthUser,
                                            Customer customer) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        for (Map.Entry<String, Pair<String, String>> entry : changes.entrySet()) {
            String property = entry.getKey();
            String propertyName = StringUtils.replaceCharToCamelCase(property, '_');
            propertyName = StringUtils.replaceCharToCamelCase(propertyName, ' ');

            String prevState = entry.getValue().getFirst();
            String nextState = entry.getValue().getSecond();

            LeadEmailSettings leadEmailSettings = leadEmailSettingsService.findByUserId(userId);

            CustomerLoginInfo customerLoginInfo = customer.getCustomerLoginInfo();
            LeadEmailSettings customerLeadEmailSettings = leadEmailSettingsService.findByCustomerId(customerLoginInfo.getId());

            if (leadEmailSettings != null) {
                String getterMethodName = "get" + StringUtils.capitalizeFirstLetter(propertyName);
                Method getterMethod = LeadEmailSettings.class.getMethod(getterMethodName);
                Boolean propertyValue = (Boolean) getterMethod.invoke(leadEmailSettings);

                Boolean isCustomerLikeToGetNotified = true;
                if(customerLeadEmailSettings != null) {
                    isCustomerLikeToGetNotified = (Boolean) getterMethod.invoke(customerLeadEmailSettings);
                }

                if (isCustomerLikeToGetNotified != null && propertyValue != null && propertyValue && isCustomerLikeToGetNotified) {
                    String emailTemplateGetterMethodName = "get" + StringUtils.capitalizeFirstLetter(propertyName) + "EmailTemplate";
                    Method emailTemplateGetterMethod = LeadEmailSettings.class.getMethod(emailTemplateGetterMethodName);
                    EmailTemplate emailTemplate = (EmailTemplate) emailTemplateGetterMethod.invoke(leadEmailSettings);
                    String body = emailTemplate.getContent();

                    property = property.replace(' ', '_');
                    String regex = "\\{\\{(.*?)\\}\\}";
                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(body);

                    while (matcher.find()) {
                        String placeholder = matcher.group(1);
                        if (placeholder.contains("previous") && placeholder.contains(property)) {
                            body = body.replace("{{" + placeholder + "}}", prevState);
                        } else if (placeholder.contains("next") && placeholder.contains(property)) {
                            body = body.replace("{{" + placeholder + "}}", nextState);
                        }
                    }

                    try {
                        googleGmailApiService.sendEmail(oAuthUser, customer.getEmail(), emailTemplate.getName(), body);
                    } catch (IOException | GeneralSecurityException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    private void populateModelAttributes(Model model, Authentication authentication, User loggedInUser) {
        List<User> employees = new ArrayList<>();
        List<Customer> customers;

        List<Attachment> attachments = new ArrayList<>();

        if(AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            employees = userService.findAll();
            customers = customerService.findAll();
        } else {
            employees.add(loggedInUser);
            customers = customerService.findByUserId(loggedInUser.getId().longValue());
        }

        List<GoogleDriveFolder> folders = null;
        boolean hasGoogleDriveAccess = false;
        if (!(authentication instanceof UsernamePasswordAuthenticationToken) && googleDriveApiService != null) {
            OAuthUser oAuthUser = authenticationUtils.getOAuthUserFromAuthentication(authentication);
            try {
                hasGoogleDriveAccess = authenticationUtils.checkIfAppHasAccess(GoogleAccessService.SCOPE_DRIVE, oAuthUser);
                if (hasGoogleDriveAccess) {
                    folders = googleDriveApiService.listFolders(oAuthUser);
                }
            } catch (IOException | GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        }

        model.addAttribute("employees", employees);
        model.addAttribute("customers", customers);
        model.addAttribute("attachments", attachments);
        model.addAttribute("folders", folders);
        model.addAttribute("hasGoogleDriveAccess", hasGoogleDriveAccess);
    }
}

// PayPropMaintenanceSyncService.java - Fixed version
package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.*;
import site.easy.to.build.crm.service.ticket.TicketService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.tag.TagNamespaceService;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * PayProp Maintenance Ticket Synchronization Service
 * Handles bidirectional sync of maintenance tickets, messages, and payments
 * between PayProp and CRM systems.
 */
@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Service
public class PayPropMaintenanceSyncService {
    
    private static final Logger log = LoggerFactory.getLogger(PayPropMaintenanceSyncService.class);
    
    @Autowired
    private PayPropApiClient apiClient;
    
    @Autowired
    private TicketService ticketService;
    
    @Autowired
    private CustomerService customerService;
    
    @Autowired
    private PropertyService propertyService;
    
    @Autowired
    private TicketRepository ticketRepository;
    
    @Autowired
    private CustomerRepository customerRepository;
    
    @Autowired
    private PaymentCategoryRepository paymentCategoryRepository;
    
    @Autowired
    private TagNamespaceService tagNamespaceService;
    
    // ===== IMPORT FROM PAYPROP =====
    
    /**
     * Sync maintenance categories from PayProp
     */
    public SyncResult syncMaintenanceCategories() {
        log.info("🏷️ Syncing maintenance categories from PayProp...");
        
        try {
            Map<String, Object> response = apiClient.get("/maintenance/categories");
            
            List<Map<String, Object>> categories = extractCategoriesFromResponse(response);
            
            int created = 0, updated = 0;
            
            for (Map<String, Object> categoryData : categories) {
                String externalId = (String) categoryData.get("id");
                String name = (String) categoryData.get("name");
                String description = (String) categoryData.get("description");
                
                if (externalId != null && name != null) {
                    // For now, we'll store categories as PaymentCategory entities
                    // Later, you might want a dedicated MaintenanceCategory entity
                    PaymentCategory category = paymentCategoryRepository.findByPayPropCategoryId(externalId);
                    
                    if (category == null) {
                        category = new PaymentCategory();
                        category.setPayPropCategoryId(externalId);
                        category.setCategoryType("MAINTENANCE");
                        category.setCreatedAt(LocalDateTime.now());
                        created++;
                    } else {
                        updated++;
                    }
                    
                    category.setCategoryName(name);
                    category.setDescription(description);
                    category.setIsActive("Y");
                    category.setUpdatedAt(LocalDateTime.now());
                    
                    paymentCategoryRepository.save(category);
                }
            }
            
            return SyncResult.success("Maintenance categories synced", 
                Map.of("created", created, "updated", updated, "total", categories.size()));
                
        } catch (Exception e) {
            log.error("❌ Failed to sync maintenance categories: {}", e.getMessage(), e);
            return SyncResult.failure("Failed to sync maintenance categories: " + e.getMessage());
        }
    }
    
    /**
     * Import all maintenance tickets from PayProp
     */
    public SyncResult importMaintenanceTickets() {
        log.info("🎫 Starting maintenance tickets import from PayProp...");
        
        try {
            // First ensure categories are synced
            syncMaintenanceCategories();
            
            List<Map<String, Object>> allTickets = apiClient.fetchAllPages(
                "/maintenance/tickets", 
                Function.identity()
            );
            
            int created = 0, updated = 0, skipped = 0, errors = 0;
            
            for (Map<String, Object> ticketData : allTickets) {
                try {
                    MaintenanceTicketSyncResult result = syncMaintenanceTicketFromPayProp(ticketData);
                    switch (result) {
                        case CREATED: created++; break;
                        case UPDATED: updated++; break;
                        case SKIPPED: skipped++; break;
                        case ERROR: errors++; break;
                    }
                } catch (Exception e) {
                    errors++;
                    log.error("❌ Failed to sync ticket {}: {}", ticketData.get("id"), e.getMessage());
                }
            }
            
            log.info("🎫 Maintenance tickets import completed: {} created, {} updated, {} skipped, {} errors", 
                created, updated, skipped, errors);
            
            Map<String, Object> details = Map.of(
                "total_processed", allTickets.size(),
                "created", created,
                "updated", updated,
                "skipped", skipped,
                "errors", errors
            );
            
            return errors == 0 ? 
                SyncResult.success("Maintenance tickets imported successfully", details) :
                SyncResult.partial("Maintenance tickets imported with some errors", details);
                
        } catch (Exception e) {
            log.error("❌ Maintenance tickets import failed: {}", e.getMessage(), e);
            return SyncResult.failure("Maintenance tickets import failed: " + e.getMessage());
        }
    }
    
    /**
     * Sync maintenance ticket from PayProp - CRM-PRIMARY VERSION
     * Only creates new tickets, never updates existing ones
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MaintenanceTicketSyncResult syncMaintenanceTicketFromPayProp(Map<String, Object> ticketData) {
        try {
            String payPropTicketId = (String) ticketData.get("id");
            
            if (payPropTicketId == null || payPropTicketId.trim().isEmpty()) {
                log.warn("⚠️ Skipping ticket with missing PayProp ID");
                return MaintenanceTicketSyncResult.SKIPPED;
            }
            
            // Check if ticket already exists in CRM
            Ticket existingTicket = ticketService.findByPayPropTicketId(payPropTicketId);
            
            if (existingTicket != null) {
                // CRM IS PRIMARY: Skip updates from PayProp
                log.info("🏆 CRM Primary: Skipping PayProp update for ticket {} - CRM is source of truth", 
                    payPropTicketId);
                return MaintenanceTicketSyncResult.SKIPPED;
            }
            
            // Only create NEW tickets from PayProp
            Ticket ticket = createTicketFromPayPropData(ticketData);
            if (ticket == null) {
                return MaintenanceTicketSyncResult.SKIPPED;
            }
            
            // Save new ticket
            ticketService.save(ticket);
            
            log.info("✅ Created NEW maintenance ticket from PayProp: {} ({})", 
                ticket.getSubject(), payPropTicketId);
            
            return MaintenanceTicketSyncResult.CREATED;
            
        } catch (DataIntegrityViolationException e) {
            log.warn("⚠️ Duplicate ticket detected, treating as skipped: {}", e.getMessage());
            return MaintenanceTicketSyncResult.SKIPPED;
        } catch (Exception e) {
            log.error("❌ Failed to sync maintenance ticket: {}", e.getMessage());
            return MaintenanceTicketSyncResult.ERROR;
        }
    }
    
    /**
     * Create ticket from PayProp data
     */
    private Ticket createTicketFromPayPropData(Map<String, Object> ticketData) {
        try {
            String payPropTicketId = (String) ticketData.get("id");
            String payPropPropertyId = (String) ticketData.get("property_id");
            String payPropTenantId = (String) ticketData.get("tenant_id");
            String payPropCategoryId = (String) ticketData.get("category_id");
            
            // Find or create customer for this ticket
            Customer customer = findOrCreateCustomerForTicket(payPropPropertyId, payPropTenantId);
            if (customer == null) {
                log.warn("⚠️ Could not resolve customer for ticket {}", payPropTicketId);
                return null;
            }
            
            Ticket ticket = new Ticket();
            
            // Basic ticket information - FIXED: use actual field names
            ticket.setPayPropTicketId(payPropTicketId);
            ticket.setSubject((String) ticketData.getOrDefault("subject", "Maintenance Request"));
            ticket.setDescription((String) ticketData.get("description"));
            ticket.setType("maintenance"); // FIXED: use setType not setTicketType
            
            // Map PayProp status to CRM status
            String payPropStatus = (String) ticketData.get("status");
            ticket.setStatus(mapPayPropStatusToCrmStatus(payPropStatus));
            
            // Priority and emergency handling
            Boolean isEmergency = (Boolean) ticketData.get("is_emergency");
            if (Boolean.TRUE.equals(isEmergency)) {
                ticket.setPriority("high");
                ticket.setUrgencyLevel("emergency");
            } else {
                ticket.setPriority("medium");
                ticket.setUrgencyLevel("routine");
            }
            
            // PayProp integration fields - FIXED: use actual field names
            ticket.setPayPropCategoryId(payPropCategoryId);
            ticket.setPayPropPropertyId(payPropPropertyId);
            ticket.setPayPropTenantId(payPropTenantId);
            ticket.setPayPropLastSync(LocalDateTime.now());
            ticket.setPayPropSynced(true);
            
            // Map category with namespace
            if (payPropCategoryId != null) {
                PaymentCategory category = paymentCategoryRepository.findByPayPropCategoryId(payPropCategoryId);
                if (category != null) {
                    // Create namespaced maintenance tag
                    String namespacedCategory = tagNamespaceService.createMaintenanceTag(category.getCategoryName());
                    ticket.setMaintenanceCategory(namespacedCategory);
                }
            }
            
            // Set customer relationship
            ticket.setCustomer(customer);
            
            // Dates
            ticket.setCreatedAt(LocalDateTime.now());
            
            String createdDate = (String) ticketData.get("created_at");
            if (createdDate != null) {
                try {
                    ticket.setCreatedAt(LocalDateTime.parse(createdDate));
                } catch (Exception e) {
                    log.debug("Could not parse created_at date: {}", createdDate);
                }
            }
            
            // Auto-assign based on customer or property
            autoAssignTicket(ticket, customer);
            
            return ticket;
            
        } catch (Exception e) {
            log.error("❌ Error creating ticket from PayProp data: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Update existing ticket from PayProp data
     */
    private void updateTicketFromPayPropData(Ticket ticket, Map<String, Object> ticketData) {
        // Update description if changed
        String newDescription = (String) ticketData.get("description");
        if (newDescription != null && !newDescription.equals(ticket.getDescription())) {
            ticket.setDescription(newDescription);
        }
        
        // Update status
        String payPropStatus = (String) ticketData.get("status");
        String mappedStatus = mapPayPropStatusToCrmStatus(payPropStatus);
        if (!mappedStatus.equals(ticket.getStatus())) {
            ticket.setStatus(mappedStatus);
        }
        
        // Update emergency status
        Boolean isEmergency = (Boolean) ticketData.get("is_emergency");
        if (Boolean.TRUE.equals(isEmergency)) {
            ticket.setPriority("high");
            ticket.setUrgencyLevel("emergency");
        }
        
        // Update sync timestamp
        ticket.setPayPropLastSync(LocalDateTime.now());
        ticket.setPayPropSynced(true);
    }
    
    /**
     * Find or create customer for ticket based on property/tenant info
     */
    private Customer findOrCreateCustomerForTicket(String payPropPropertyId, String payPropTenantId) {
        // Try to find tenant first (they're the ones reporting issues)
        if (payPropTenantId != null) {
            Customer tenant = customerRepository.findByPayPropEntityId(payPropTenantId);
            if (tenant != null) {
                return tenant;
            }
        }
        
        // Fall back to property owner
        if (payPropPropertyId != null) {
            // Find property and then its owner - FIXED: handle Optional properly
            Optional<Property> propertyOpt = propertyService.findByPayPropId(payPropPropertyId);
            if (propertyOpt.isPresent()) {
                Property property = propertyOpt.get();
                // Find owner through assignments
                List<Customer> owners = customerService.findByEntityTypeAndEntityId("Property", property.getId());
                Optional<Customer> propertyOwner = owners.stream()
                    .filter(customer -> Boolean.TRUE.equals(customer.getIsPropertyOwner()))
                    .findFirst();
                
                if (propertyOwner.isPresent()) {
                    return propertyOwner.get();
                }
            }
            
            // Create placeholder customer for unknown property
            return createPlaceholderCustomerForProperty(payPropPropertyId);
        }
        
        return null;
    }
    
    /**
     * Create placeholder customer for unknown property
     */
    private Customer createPlaceholderCustomerForProperty(String payPropPropertyId) {
        try {
            Customer customer = new Customer();
            customer.setName("PayProp Property " + payPropPropertyId);
            customer.setEmail("noreply+" + payPropPropertyId + "@yourcompany.com");
            customer.setCustomerType(CustomerType.PROPERTY_OWNER);
            customer.setIsPropertyOwner(true);
            customer.setPayPropEntityId(payPropPropertyId);
            customer.setDescription("Auto-created from PayProp maintenance ticket");
            customer.setCreatedAt(LocalDateTime.now());
            
            // Find a default user to assign
            // You might want to make this configurable
            User defaultUser = findDefaultUser();
            if (defaultUser != null) {
                customer.setUser(defaultUser);
                customer = customerService.save(customer);
                log.info("✅ Created placeholder customer for property {}", payPropPropertyId);
                return customer;
            }
        } catch (Exception e) {
            log.error("❌ Failed to create placeholder customer: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Auto-assign ticket to appropriate employee
     */
    private void autoAssignTicket(Ticket ticket, Customer customer) {
        try {
            // Assign to customer's user if available
            if (customer.getUser() != null) {
                ticket.setEmployee(customer.getUser());
                ticket.setManager(customer.getUser());
                return;
            }
            
            // Emergency tickets go to managers
            if ("emergency".equals(ticket.getUrgencyLevel())) {
                User manager = findManagerUser();
                if (manager != null) {
                    ticket.setEmployee(manager);
                    ticket.setManager(manager);
                    return;
                }
            }
            
            // Assign to default user
            User defaultUser = findDefaultUser();
            if (defaultUser != null) {
                ticket.setEmployee(defaultUser);
                ticket.setManager(defaultUser);
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to auto-assign ticket: {}", e.getMessage());
        }
    }
    
    /**
     * Sync ticket messages from PayProp
     */
    private void syncTicketMessages(Ticket ticket, String payPropTicketId) {
        try {
            String endpoint = "/maintenance/tickets/" + payPropTicketId + "/messages";
            List<Map<String, Object>> messages = apiClient.fetchAllPages(endpoint, Function.identity());
            
            for (Map<String, Object> messageData : messages) {
                // For now, append messages to ticket description
                // Later, implement proper TicketMessage entity
                String messageId = (String) messageData.get("id");
                String message = (String) messageData.get("message");
                String authorType = (String) messageData.get("author_type");
                
                if (message != null) {
                    String messagePrefix = "tenant".equals(authorType) ? "[Tenant]" : "[PayProp]";
                    String formattedMessage = String.format("\n\n%s %s: %s", 
                        messagePrefix, LocalDateTime.now(), message);
                    
                    ticket.setDescription(ticket.getDescription() + formattedMessage);
                }
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to sync messages for ticket {}: {}", payPropTicketId, e.getMessage());
        }
    }
    
    // ===== EXPORT TO PAYPROP =====
    
    /**
     * Export new/updated CRM tickets to PayProp
     */
    public SyncResult exportMaintenanceTicketsToPayProp() {
        log.info("📤 Exporting maintenance tickets to PayProp...");
        
        try {
            // Find tickets that need to be synced to PayProp - FIXED: use actual repository methods
            List<Ticket> ticketsToSync = ticketRepository.findByTypeAndPayPropSynced("maintenance", false);
            
            int created = 0, updated = 0, errors = 0;
            
            for (Ticket ticket : ticketsToSync) {
                try {
                    if (ticket.getPayPropTicketId() == null) {
                        // Create new ticket in PayProp
                        createTicketInPayProp(ticket);
                        created++;
                    } else {
                        // Update existing ticket in PayProp
                        updateTicketInPayProp(ticket);
                        updated++;
                    }
                } catch (Exception e) {
                    errors++;
                    log.error("❌ Failed to export ticket {} to PayProp: {}", 
                        ticket.getTicketId(), e.getMessage());
                    
                    // Mark as failed
                    ticket.setPayPropSynced(false);
                    ticketService.save(ticket);
                }
            }
            
            Map<String, Object> details = Map.of(
                "processed", ticketsToSync.size(),
                "created", created,
                "updated", updated,
                "errors", errors
            );
            
            return errors == 0 ? 
                SyncResult.success("Tickets exported successfully", details) :
                SyncResult.partial("Tickets exported with some errors", details);
                
        } catch (Exception e) {
            log.error("❌ Export to PayProp failed: {}", e.getMessage(), e);
            return SyncResult.failure("Export to PayProp failed: " + e.getMessage());
        }
    }
    
    /**
     * Create new maintenance ticket in PayProp
     */
    private void createTicketInPayProp(Ticket ticket) throws Exception {
        Map<String, Object> ticketPayload = buildPayPropTicketPayload(ticket);
        
        Map<String, Object> response = apiClient.post("/maintenance/tickets", ticketPayload);
        
        String payPropTicketId = (String) response.get("id");
        if (payPropTicketId != null) {
            ticket.setPayPropTicketId(payPropTicketId);
            ticket.setPayPropSynced(true);
            ticket.setPayPropLastSync(LocalDateTime.now());
            ticketService.save(ticket);
            
            log.info("✅ Created maintenance ticket in PayProp: {} -> {}", 
                ticket.getTicketId(), payPropTicketId);
        } else {
            throw new RuntimeException("PayProp did not return ticket ID");
        }
    }
    
    /**
     * Update existing maintenance ticket in PayProp
     */
    private void updateTicketInPayProp(Ticket ticket) throws Exception {
        Map<String, Object> ticketPayload = buildPayPropTicketPayload(ticket);
        
        String endpoint = "/maintenance/tickets/" + ticket.getPayPropTicketId();
        apiClient.put(endpoint, ticketPayload);
        
        ticket.setPayPropSynced(true);
        ticket.setPayPropLastSync(LocalDateTime.now());
        ticketService.save(ticket);
        
        log.info("✅ Updated maintenance ticket in PayProp: {} ({})", 
            ticket.getTicketId(), ticket.getPayPropTicketId());
    }
    
    /**
     * Build PayProp ticket payload from CRM ticket
     */
    private Map<String, Object> buildPayPropTicketPayload(Ticket ticket) {
        Map<String, Object> payload = new HashMap<>();
        
        // Find PayProp property ID
        String payPropPropertyId = findPayPropPropertyId(ticket);
        if (payPropPropertyId != null) {
            payload.put("property_id", payPropPropertyId);
        }
        
        // Find PayProp tenant ID
        String payPropTenantId = findPayPropTenantId(ticket);
        if (payPropTenantId != null) {
            payload.put("tenant_id", payPropTenantId);
        }
        
        // Basic ticket data
        payload.put("subject", ticket.getSubject());
        payload.put("description", ticket.getDescription());
        payload.put("status", mapCrmStatusToPayPropStatus(ticket.getStatus()));
        payload.put("is_emergency", "emergency".equals(ticket.getUrgencyLevel()));
        
        // Category
        if (ticket.getPayPropCategoryId() != null) {
            payload.put("category_id", ticket.getPayPropCategoryId());
        } else if (ticket.getMaintenanceCategory() != null) {
            // Try to find category by name
            PaymentCategory category = paymentCategoryRepository.findByCategoryName(ticket.getMaintenanceCategory());
            if (category != null && category.getPayPropCategoryId() != null) {
                payload.put("category_id", category.getPayPropCategoryId());
            }
        }
        
        return payload;
    }
    
    // ===== UTILITY METHODS =====
    
    /**
     * Map PayProp status to CRM status
     */
    private String mapPayPropStatusToCrmStatus(String payPropStatus) {
        if (payPropStatus == null) return "open";
        
        switch (payPropStatus.toLowerCase()) {
            case "new": return "open";
            case "in_progress": return "in-progress";
            case "on_hold": return "on-hold";
            case "resolved": return "resolved";
            case "rejected": return "closed";
            default: return "open";
        }
    }
    
    /**
     * Map CRM status to PayProp status
     */
    private String mapCrmStatusToPayPropStatus(String crmStatus) {
        if (crmStatus == null) return "new";
        
        switch (crmStatus.toLowerCase()) {
            case "open": return "new";
            case "in-progress": return "in_progress";
            case "on-hold": return "on_hold";
            case "resolved": return "resolved";
            case "closed": return "rejected";
            default: return "new";
        }
    }
    
    /**
     * Find PayProp property ID for ticket
     */
    private String findPayPropPropertyId(Ticket ticket) {
        try {
            if (ticket.getCustomer() != null) {
                // Check if customer has assigned properties
                List<Customer> owners = customerService.findByEntityTypeAndEntityId(
                    "Property", ticket.getCustomer().getCustomerId().longValue());
                
                if (!owners.isEmpty()) {
                    // Get first property (you might want to be more specific)
                    Customer owner = owners.get(0);
                    if (owner.getAssignedPropertyId() != null) {
                        Property property = propertyService.findById(owner.getAssignedPropertyId());
                        if (property != null) {
                            return property.getPayPropId();
                        }
                    }
                }
                
                // Check if customer itself has a PayProp property reference
                if (ticket.getCustomer().getPayPropEntityId() != null) {
                    // This might be a property ID if customer was created from property
                    Optional<Property> propertyOpt = propertyService.findByPayPropId(
                        ticket.getCustomer().getPayPropEntityId());
                    if (propertyOpt.isPresent()) {
                        return ticket.getCustomer().getPayPropEntityId();
                    }
                }
            }
        } catch (Exception e) {
            log.error("❌ Error finding PayProp property ID: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Find PayProp tenant ID for ticket
     */
    private String findPayPropTenantId(Ticket ticket) {
        try {
            if (ticket.getCustomer() != null && 
                Boolean.TRUE.equals(ticket.getCustomer().getIsTenant()) &&
                ticket.getCustomer().getPayPropEntityId() != null) {
                return ticket.getCustomer().getPayPropEntityId();
            }
        } catch (Exception e) {
            log.error("❌ Error finding PayProp tenant ID: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Extract categories from PayProp response
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractCategoriesFromResponse(Map<String, Object> response) {
        // Try different possible response structures
        if (response.containsKey("categories")) {
            Object categories = response.get("categories");
            if (categories instanceof List) {
                return (List<Map<String, Object>>) categories;
            }
        }
        
        if (response.containsKey("data")) {
            Object data = response.get("data");
            if (data instanceof List) {
                return (List<Map<String, Object>>) data;
            }
        }
        
        if (response.containsKey("items")) {
            Object items = response.get("items");
            if (items instanceof List) {
                return (List<Map<String, Object>>) items;
            }
        }
        
        return new ArrayList<>();
    }
    
    /**
     * Find default user for assignment
     */
    private User findDefaultUser() {
        try {
            // You might want to make this configurable
            return customerRepository.findAll().stream()
                .map(Customer::getUser)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        } catch (Exception e) {
            log.error("❌ Error finding default user: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Find manager user for emergency assignments
     */
    private User findManagerUser() {
        try {
            // Find user with MANAGER role
            return customerRepository.findAll().stream()
                .map(Customer::getUser)
                .filter(Objects::nonNull)
                .filter(user -> user.getRoles() != null && 
                              user.getRoles().stream().anyMatch(role -> 
                                  "ROLE_MANAGER".equals(role.getName())))
                .findFirst()
                .orElse(findDefaultUser());
        } catch (Exception e) {
            log.error("❌ Error finding manager user: {}", e.getMessage());
            return findDefaultUser();
        }
    }

    /**
     * Fetch maintenance tickets with proper field handling
     */
    public List<Map<String, Object>> fetchMaintenanceTickets() {
        return apiClient.fetchAllPages("/maintenance/tickets", Function.identity());
    }

    /**
     * Fetch maintenance categories with proper field handling  
     */
    public List<Map<String, Object>> fetchMaintenanceCategories() {
        return apiClient.fetchAllPages("/maintenance/categories", Function.identity());
    }
    
    // ===== ENUMS =====
    
    public enum MaintenanceTicketSyncResult {
        CREATED,
        UPDATED,
        SKIPPED,
        ERROR
    }
}
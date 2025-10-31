// PayPropMaintenanceSyncService.java - Fixed version
package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
    @Lazy
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
    private DataSource dataSource;
    
    @Autowired
    private TagNamespaceService tagNamespaceService;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    // ===== IMPORT FROM PAYPROP =====
    
    /**
     * Sync maintenance categories from PayProp - CORRECTED VERSION
     * Uses the proper payprop_maintenance_categories table
     * Runs in separate transaction to prevent rollback of parent transaction on 403 errors
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncResult syncMaintenanceCategories() {
        log.info("🏷️ Syncing maintenance categories from PayProp...");

        try {
            // Use the correct endpoint: /maintenance/categories (not /payments/categories)
            List<Map<String, Object>> categories;
            try {
                categories = apiClient.fetchAllPages("/maintenance/categories",
                    item -> {
                        log.debug("Maintenance Category - ID: {} Name: {} Description: {}",
                            item.get("id"), item.get("name"), item.get("description"));
                        return item;
                    });
            } catch (Exception apiError) {
                // Handle 403 Forbidden gracefully - maintenance permissions may not be available
                String errorMsg = apiError.getMessage();
                if (errorMsg != null && (errorMsg.contains("403") || errorMsg.contains("FORBIDDEN") ||
                                        errorMsg.contains("permission"))) {
                    log.warn("⚠️ Maintenance categories API access denied (403 FORBIDDEN) - skipping maintenance sync");
                    log.warn("   This is not a critical error - maintenance features require special PayProp permissions");
                    return SyncResult.partial("Maintenance categories skipped - insufficient API permissions",
                        Map.of("total_fetched", 0, "imported", 0,
                               "warning", "403 FORBIDDEN - PayProp account lacks maintenance permissions"));
                }
                // Re-throw other errors
                throw apiError;
            }

            log.info("📦 PayProp API returned: {} maintenance categories", categories.size());

            // If no categories were returned, check if it's a permissions issue
            if (categories.isEmpty()) {
                log.warn("⚠️ No maintenance categories fetched - may indicate insufficient permissions or no data");
                return SyncResult.partial("No maintenance categories available",
                    Map.of("total_fetched", 0, "imported", 0, "warning", "No categories available or insufficient permissions"));
            }

            // Clear existing data for fresh import
            int deletedCount = jdbcTemplate.update("DELETE FROM payprop_maintenance_categories");
            log.info("Cleared {} existing maintenance categories for fresh import", deletedCount);

            int created = 0;
            String insertSql = """
                INSERT IGNORE INTO payprop_maintenance_categories (
                    payprop_external_id, name, description, category_type, is_active
                ) VALUES (?, ?, ?, ?, ?)
            """;

            for (Map<String, Object> categoryData : categories) {
                String externalId = getStringValue(categoryData, "id");
                String name = getStringValue(categoryData, "name");
                String description = getStringValue(categoryData, "description");

                // PayProp puts category names in description field when name is null
                String categoryName = (name != null && !name.trim().isEmpty()) ? name : description;

                if (externalId != null && categoryName != null && !categoryName.trim().isEmpty()) {
                    int result = jdbcTemplate.update(insertSql,
                        externalId, categoryName, description, "maintenance", true);

                    if (result > 0) {
                        created++;
                    }
                }
            }

            log.info("✅ Maintenance categories sync completed: {} imported", created);

            return SyncResult.success("Maintenance categories synced",
                Map.of("total_fetched", categories.size(), "imported", created, "deleted", deletedCount));

        } catch (Exception e) {
            // Check if this is a permissions error
            if (e.getMessage() != null &&
                (e.getMessage().contains("403 FORBIDDEN") ||
                 e.getMessage().contains("You do not have the necessary permission"))) {
                log.warn("⚠️ Insufficient permissions to sync maintenance categories - skipping");
                return SyncResult.partial("Insufficient permissions for maintenance categories",
                    Map.of("total_fetched", 0, "imported", 0, "error", "403 Forbidden"));
            }

            log.error("❌ Failed to sync maintenance categories: {}", e.getMessage(), e);
            return SyncResult.failure("Failed to sync maintenance categories: " + e.getMessage());
        }
    }
    
    // Helper method
    private String getStringValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        return map.get(key).toString();
    }
    
    /**
     * Import all maintenance tickets from PayProp
     * Runs in separate transaction to prevent rollback of parent transaction on 403 errors
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncResult importMaintenanceTickets() {
        log.info("🎫 Starting maintenance tickets import from PayProp...");

        try {
            // First ensure categories are synced
            syncMaintenanceCategories();

            List<Map<String, Object>> allTickets;
            try {
                allTickets = apiClient.fetchAllPages(
                    "/maintenance/tickets",
                    Function.identity()
                );
            } catch (Exception apiError) {
                // Handle 403 Forbidden gracefully - maintenance permissions may not be available
                String errorMsg = apiError.getMessage();
                if (errorMsg != null && (errorMsg.contains("403") || errorMsg.contains("FORBIDDEN") ||
                                        errorMsg.contains("permission"))) {
                    log.warn("⚠️ Maintenance tickets API access denied (403 FORBIDDEN) - skipping tickets import");
                    log.warn("   This is not a critical error - maintenance features require special PayProp permissions");
                    return SyncResult.partial("Maintenance tickets skipped - insufficient API permissions",
                        Map.of("created", 0, "updated", 0, "skipped", 0, "errors", 0,
                               "warning", "403 FORBIDDEN - PayProp account lacks maintenance permissions"));
                }
                // Re-throw other errors
                throw apiError;
            }

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
            
            // PRIORITY: Set internal property relationship for consistent ticket visibility
            if (payPropPropertyId != null) {
                try {
                    Optional<Property> propertyOpt = propertyService.findByPayPropId(payPropPropertyId);
                    if (propertyOpt.isPresent()) {
                        ticket.setProperty(propertyOpt.get());
                        System.out.println("✅ Set internal property relationship for synced ticket: " + propertyOpt.get().getPropertyName());
                    } else {
                        System.out.println("⚠️ Could not find internal property for PayProp ID: " + payPropPropertyId);
                    }
                } catch (Exception e) {
                    System.err.println("Error setting internal property relationship: " + e.getMessage());
                }
            }
            
            // Map category from payprop_maintenance_categories table
            if (payPropCategoryId != null) {
                try {
                    String categoryQuery = "SELECT name FROM payprop_maintenance_categories WHERE payprop_external_id = ? LIMIT 1";
                    List<String> categoryNames = jdbcTemplate.queryForList(categoryQuery, String.class, payPropCategoryId);
                    
                    if (!categoryNames.isEmpty()) {
                        String categoryName = categoryNames.get(0);
                        // Create namespaced maintenance tag
                        String namespacedCategory = tagNamespaceService.createMaintenanceTag(categoryName);
                        ticket.setMaintenanceCategory(namespacedCategory);
                        log.debug("✅ Mapped PayProp category {} to CRM category {}", payPropCategoryId, namespacedCategory);
                    } else {
                        log.warn("⚠️ PayProp category {} not found in payprop_maintenance_categories table", payPropCategoryId);
                    }
                } catch (Exception e) {
                    log.error("❌ Error mapping PayProp category {}: {}", payPropCategoryId, e.getMessage());
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
            // Find category ID from maintenance categories table
            String categoryId = findMaintenanceCategoryId(ticket.getMaintenanceCategory());
            if (categoryId != null) {
                payload.put("category_id", categoryId);
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
            if (ticket.getCustomer() == null) {
                log.debug("🔍 Ticket {} has no customer", ticket.getTicketId());
                return null;
            }
            
            Customer customer = ticket.getCustomer();
            log.debug("🔍 Finding PayProp property ID for customer {}", customer.getCustomerId());
            
            // Method 1: Check customer_property_assignments table for properties linked to this customer
            String query = "SELECT p.payprop_id FROM customer_property_assignments cpa " +
                          "JOIN properties p ON cpa.property_id = p.id " +
                          "WHERE cpa.customer_id = ? AND p.payprop_id IS NOT NULL " +
                          "ORDER BY cpa.is_primary DESC, cpa.created_at DESC LIMIT 1";
                          
            List<String> propertyIds = jdbcTemplate.queryForList(query, String.class, customer.getCustomerId());
            if (!propertyIds.isEmpty()) {
                String payPropId = propertyIds.get(0);
                log.debug("✅ Found PayProp property ID via assignments: {}", payPropId);
                return payPropId;
            }
            
            // Method 2: If customer has assigned_property_id (legacy field), use that
            if (customer.getAssignedPropertyId() != null) {
                Property property = propertyService.findById(customer.getAssignedPropertyId());
                if (property != null && property.getPayPropId() != null) {
                    log.debug("✅ Found PayProp property ID via assigned_property_id: {}", property.getPayPropId());
                    return property.getPayPropId();
                }
            }
            
            log.warn("⚠️ Could not find PayProp property ID for customer {}", customer.getCustomerId());
            return null;
            
        } catch (Exception e) {
            log.error("❌ Error finding PayProp property ID: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Find PayProp tenant ID for ticket
     */
    private String findPayPropTenantId(Ticket ticket) {
        try {
            if (ticket.getCustomer() == null) {
                log.debug("🔍 Ticket {} has no customer", ticket.getTicketId());
                return null;
            }
            
            Customer customer = ticket.getCustomer();
            log.debug("🔍 Finding PayProp tenant ID for customer {}", customer.getCustomerId());
            
            // Method 1: If customer is marked as tenant and has PayProp entity ID, use it directly
            if (Boolean.TRUE.equals(customer.getIsTenant()) && customer.getPayPropEntityId() != null) {
                log.debug("✅ Found PayProp tenant ID directly from customer: {}", customer.getPayPropEntityId());
                return customer.getPayPropEntityId();
            }
            
            // Method 2: Check if customer has TENANT assignment in customer_property_assignments 
            String query = "SELECT c.payprop_entity_id FROM customer_property_assignments cpa " +
                          "JOIN customers c ON cpa.customer_id = c.customer_id " +
                          "WHERE cpa.customer_id = ? AND cpa.assignment_type = 'TENANT' " +
                          "AND c.payprop_entity_id IS NOT NULL LIMIT 1";
                          
            List<String> tenantIds = jdbcTemplate.queryForList(query, String.class, customer.getCustomerId());
            if (!tenantIds.isEmpty()) {
                String payPropTenantId = tenantIds.get(0);
                log.debug("✅ Found PayProp tenant ID via TENANT assignment: {}", payPropTenantId);
                return payPropTenantId;
            }
            
            log.warn("⚠️ Could not find PayProp tenant ID for customer {}", customer.getCustomerId());
            return null;
            
        } catch (Exception e) {
            log.error("❌ Error finding PayProp tenant ID: {}", e.getMessage());
            return null;
        }
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
     * Find PayProp maintenance category ID by category name
     */
    private String findMaintenanceCategoryId(String categoryName) {
        if (categoryName == null) return null;
        
        String sql = "SELECT payprop_external_id FROM payprop_maintenance_categories WHERE LOWER(description) = LOWER(?) AND is_active = true";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, categoryName);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String categoryId = rs.getString("payprop_external_id");
                    log.debug("✅ Found PayProp category ID '{}' for maintenance category: {}", categoryId, categoryName);
                    return categoryId;
                }
            }
        } catch (Exception e) {
            log.error("❌ Error querying maintenance category for '{}': {}", categoryName, e.getMessage());
        }
        
        log.warn("⚠️ No PayProp category ID found for maintenance category: {}", categoryName);
        return null;
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
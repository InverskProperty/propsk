package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import site.easy.to.build.crm.entity.PaymentCategory;
import site.easy.to.build.crm.repository.PaymentCategoryRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Service for mapping historical data categories to PayProp-compatible categories
 *
 * This service ensures that historical imports align with your existing PayProp
 * category structure by validating against the actual payprop_*_categories tables.
 */
@Service
public class PayPropCategoryMappingService {

    private static final Logger logger = LoggerFactory.getLogger(PayPropCategoryMappingService.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PaymentCategoryRepository paymentCategoryRepository;

    // Cache for performance
    private Map<String, String> invoiceCategoryCache = new HashMap<>();
    private Map<String, String> paymentCategoryCache = new HashMap<>();
    private Map<String, String> maintenanceCategoryCache = new HashMap<>();
    private boolean cacheInitialized = false;

    /**
     * Map a historical transaction category to PayProp-compatible format
     */
    public String mapHistoricalCategory(String transactionType, String category) {
        if (!cacheInitialized) {
            initializeCache();
        }

        if (category == null || category.trim().isEmpty()) {
            return getDefaultCategoryForType(transactionType);
        }

        String normalizedCategory = category.toLowerCase().trim();

        // Map based on transaction type
        return switch (transactionType.toLowerCase()) {
            case "deposit", "invoice" -> mapInvoiceCategory(normalizedCategory);
            case "fee", "commission" -> "commission";
            case "payment", "expense" -> mapPaymentCategory(normalizedCategory);
            default -> normalizedCategory;
        };
    }

    /**
     * Map maintenance/expense categories to PayProp maintenance categories
     */
    public String mapMaintenanceCategory(String expenseDescription) {
        if (!cacheInitialized) {
            initializeCache();
        }

        if (expenseDescription == null || expenseDescription.trim().isEmpty()) {
            return "general_maintenance";
        }

        String lower = expenseDescription.toLowerCase();

        // Check if we have exact matches from PayProp maintenance categories
        for (Map.Entry<String, String> entry : maintenanceCategoryCache.entrySet()) {
            if (lower.contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }

        // Fallback to semantic mapping
        if (lower.contains("maintenance") || lower.contains("repair") ||
            lower.contains("plumb") || lower.contains("paint") ||
            lower.contains("filling") || lower.contains("bathroom")) {
            return "maintenance";
        } else if (lower.contains("fire") || lower.contains("safety") ||
                   lower.contains("extinguisher") || lower.contains("alarm")) {
            return "fire_safety";
        } else if (lower.contains("washing") || lower.contains("fridge") ||
                   lower.contains("appliance") || lower.contains("white goods")) {
            return "white_goods";
        } else if (lower.contains("clear") || lower.contains("clean") ||
                   lower.contains("removal")) {
            return "clearance";
        } else if (lower.contains("bed") || lower.contains("furniture") ||
                   lower.contains("furnish") || lower.contains("mattress")) {
            return "furnishing";
        } else if (lower.contains("electric") || lower.contains("wiring") ||
                   lower.contains("socket")) {
            return "electrical";
        } else if (lower.contains("gas") || lower.contains("boiler") ||
                   lower.contains("heating")) {
            return "heating";
        }

        return "general_maintenance";
    }

    /**
     * Validate if a category exists in PayProp system
     */
    public boolean isValidPayPropCategory(String transactionType, String category) {
        if (!cacheInitialized) {
            initializeCache();
        }

        return switch (transactionType.toLowerCase()) {
            case "deposit", "invoice" -> invoiceCategoryCache.containsValue(category) ||
                                        category.equals("rent") || category.equals("deposit");
            case "fee" -> category.equals("commission");
            case "payment", "expense" -> paymentCategoryCache.containsValue(category) ||
                                        maintenanceCategoryCache.containsValue(category);
            default -> false;
        };
    }

    /**
     * Get all available categories for a transaction type
     */
    public Set<String> getAvailableCategories(String transactionType) {
        if (!cacheInitialized) {
            initializeCache();
        }

        return switch (transactionType.toLowerCase()) {
            case "deposit", "invoice" -> new HashSet<>(invoiceCategoryCache.values());
            case "fee" -> Set.of("commission");
            case "payment", "expense" -> {
                Set<String> categories = new HashSet<>(paymentCategoryCache.values());
                categories.addAll(maintenanceCategoryCache.values());
                yield categories;
            }
            default -> Collections.emptySet();
        };
    }

    /**
     * Get suggested categories for fuzzy matching
     */
    public List<String> suggestCategories(String transactionType, String partialCategory) {
        Set<String> availableCategories = getAvailableCategories(transactionType);
        String lower = partialCategory.toLowerCase();

        return availableCategories.stream()
            .filter(cat -> cat.toLowerCase().contains(lower) || lower.contains(cat.toLowerCase()))
            .sorted()
            .toList();
    }

    // ===== PRIVATE METHODS =====

    private void initializeCache() {
        logger.info("Initializing PayProp category cache from database");

        try {
            loadInvoiceCategories();
            loadPaymentCategories();
            loadMaintenanceCategories();
            cacheInitialized = true;
            logger.info("Category cache initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize category cache: {}", e.getMessage(), e);
        }
    }

    private void loadInvoiceCategories() throws SQLException {
        String sql = "SELECT payprop_external_id, name FROM payprop_invoice_categories WHERE is_active = 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String id = rs.getString("payprop_external_id");
                String name = rs.getString("name");
                if (id != null && name != null) {
                    invoiceCategoryCache.put(id, name.toLowerCase());
                }
            }
        }

        // Add standard categories
        invoiceCategoryCache.put("rent", "rent");
        invoiceCategoryCache.put("deposit", "deposit");
        invoiceCategoryCache.put("parking", "parking");

        logger.debug("Loaded {} invoice categories", invoiceCategoryCache.size());
    }

    private void loadPaymentCategories() throws SQLException {
        String sql = "SELECT payprop_external_id, name FROM payprop_payments_categories";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String id = rs.getString("payprop_external_id");
                String name = rs.getString("name");
                if (id != null && name != null) {
                    paymentCategoryCache.put(id, name.toLowerCase());
                }
            }
        }

        // Add standard categories
        paymentCategoryCache.put("commission", "commission");
        paymentCategoryCache.put("owner", "owner");
        paymentCategoryCache.put("contractor", "contractor");

        logger.debug("Loaded {} payment categories", paymentCategoryCache.size());
    }

    private void loadMaintenanceCategories() throws SQLException {
        String sql = "SELECT payprop_external_id, name FROM payprop_maintenance_categories WHERE is_active = 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String id = rs.getString("payprop_external_id");
                String name = rs.getString("name");
                if (id != null && name != null) {
                    maintenanceCategoryCache.put(id, name.toLowerCase());
                }
            }
        }

        // Add standard maintenance categories that align with your spreadsheet
        maintenanceCategoryCache.put("maintenance", "maintenance");
        maintenanceCategoryCache.put("fire_safety", "fire_safety");
        maintenanceCategoryCache.put("white_goods", "white_goods");
        maintenanceCategoryCache.put("clearance", "clearance");
        maintenanceCategoryCache.put("furnishing", "furnishing");
        maintenanceCategoryCache.put("electrical", "electrical");
        maintenanceCategoryCache.put("heating", "heating");
        maintenanceCategoryCache.put("plumbing", "plumbing");
        maintenanceCategoryCache.put("decorating", "decorating");

        logger.debug("Loaded {} maintenance categories", maintenanceCategoryCache.size());
    }

    private String mapInvoiceCategory(String category) {
        // Direct mapping for invoice categories
        if (category.contains("rent")) return "rent";
        if (category.contains("deposit") || category.contains("security")) return "deposit";
        if (category.contains("parking")) return "parking";
        if (category.contains("service")) return "service_charge";
        if (category.contains("utility") || category.contains("utilities")) return "utilities";

        // Check against actual PayProp invoice categories
        for (Map.Entry<String, String> entry : invoiceCategoryCache.entrySet()) {
            if (category.contains(entry.getValue()) || entry.getValue().contains(category)) {
                return entry.getValue();
            }
        }

        return "rent"; // Default for deposit transactions
    }

    private String mapPaymentCategory(String category) {
        // Commission handling
        if (category.contains("commission") || category.contains("fee") ||
            category.contains("management") || category.contains("service")) {
            return "commission";
        }

        // Maintenance/contractor payments
        if (category.contains("maintenance") || category.contains("repair") ||
            category.contains("contractor") || category.contains("expense")) {
            return mapMaintenanceCategory(category);
        }

        // Owner payments
        if (category.contains("owner") || category.contains("beneficiary")) {
            return "owner";
        }

        // Check against actual PayProp payment categories
        for (Map.Entry<String, String> entry : paymentCategoryCache.entrySet()) {
            if (category.contains(entry.getValue()) || entry.getValue().contains(category)) {
                return entry.getValue();
            }
        }

        return "general_expense";
    }

    private String getDefaultCategoryForType(String transactionType) {
        return switch (transactionType.toLowerCase()) {
            case "deposit", "invoice" -> "rent";
            case "fee" -> "commission";
            case "payment", "expense" -> "general_expense";
            default -> "general";
        };
    }

    /**
     * Refresh category cache from database
     */
    public void refreshCache() {
        logger.info("Refreshing PayProp category cache");
        cacheInitialized = false;
        invoiceCategoryCache.clear();
        paymentCategoryCache.clear();
        maintenanceCategoryCache.clear();
        initializeCache();
    }

    /**
     * Get cache statistics for monitoring
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cache_initialized", cacheInitialized);
        stats.put("invoice_categories", invoiceCategoryCache.size());
        stats.put("payment_categories", paymentCategoryCache.size());
        stats.put("maintenance_categories", maintenanceCategoryCache.size());
        return stats;
    }
}
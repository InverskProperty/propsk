package site.easy.to.build.crm.entity;

/**
 * Enumeration defining tag namespaces to prevent conflicts between different systems
 * Each namespace has a specific prefix and purpose
 */
public enum TagNamespace {
    
    /**
     * Portfolio tags - for organizing properties into portfolios
     * Format: PF-{portfolio-name} or PF-{portfolio-id}
     * Examples: PF-RESIDENTIAL-LONDON, PF-COMMERCIAL-MANCHESTER, PF-123
     */
    PORTFOLIO("PF-", "Portfolio tags for property organization"),
    
    /**
     * Block tags - for organizing properties within portfolios into blocks
     * Format: BL-{block-name} or BL-{portfolio-id}-{block-name}
     * Examples: BL-BUILDING-A, BL-123-EAST-WING, BL-TOWER-1
     */
    BLOCK("BL-", "Block tags for property grouping within portfolios"),
    
    /**
     * Maintenance tags - for maintenance ticket categorization and workflow
     * Format: MT-{category}-{subcategory} or MT-{ticket-type}
     * Examples: MT-PLUMBING-EMERGENCY, MT-ELECTRICAL-ROUTINE, MT-APPLIANCE
     */
    MAINTENANCE("MT-", "Maintenance and ticketing system tags"),
    
    /**
     * Tenant tags - for tenant-specific categorization
     * Format: TN-{category} or TN-{tenant-type}
     * Examples: TN-PRIORITY, TN-COMMERCIAL, TN-RESIDENTIAL
     */
    TENANT("TN-", "Tenant categorization tags"),
    
    /**
     * System tags - for internal system operations and automation
     * Format: SYS-{operation} or SYS-{automation-type}
     * Examples: SYS-AUTO-SYNC, SYS-MIGRATED, SYS-BACKUP
     */
    SYSTEM("SYS-", "Internal system operation tags"),
    
    /**
     * Custom tags - for user-defined tags that don't fit other categories
     * Format: CUSTOM-{user-defined}
     * Examples: CUSTOM-VIP, CUSTOM-SEASONAL, CUSTOM-SPECIAL-HANDLING
     */
    CUSTOM("CUSTOM-", "User-defined custom tags");
    
    private final String prefix;
    private final String description;
    
    TagNamespace(String prefix, String description) {
        this.prefix = prefix;
        this.description = description;
    }
    
    public String getPrefix() {
        return prefix;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Create a namespaced tag with the given suffix
     * @param suffix The tag content without namespace prefix
     * @return Fully qualified namespaced tag
     */
    public String createTag(String suffix) {
        if (suffix == null || suffix.trim().isEmpty()) {
            throw new IllegalArgumentException("Tag suffix cannot be null or empty");
        }
        
        // Remove any existing prefix to avoid double-prefixing
        String cleanSuffix = suffix.trim();
        for (TagNamespace namespace : TagNamespace.values()) {
            if (cleanSuffix.startsWith(namespace.getPrefix())) {
                cleanSuffix = cleanSuffix.substring(namespace.getPrefix().length());
                break;
            }
        }
        
        // Ensure suffix is uppercase and replace spaces with hyphens
        cleanSuffix = cleanSuffix.toUpperCase()
                                .replace(" ", "-")
                                .replace("_", "-")
                                .replaceAll("[^A-Z0-9\\-]", "")
                                .replaceAll("-+", "-")  // Replace multiple hyphens with single
                                .replaceAll("^-|-$", ""); // Remove leading/trailing hyphens
        
        return this.prefix + cleanSuffix;
    }
    
    /**
     * Extract the suffix from a namespaced tag
     * @param namespacedTag The full namespaced tag
     * @return The suffix without namespace prefix, or original string if no namespace found
     */
    public static String extractSuffix(String namespacedTag) {
        if (namespacedTag == null || namespacedTag.trim().isEmpty()) {
            return namespacedTag;
        }
        
        for (TagNamespace namespace : TagNamespace.values()) {
            if (namespacedTag.startsWith(namespace.getPrefix())) {
                return namespacedTag.substring(namespace.getPrefix().length());
            }
        }
        
        return namespacedTag; // No namespace found, return as-is
    }
    
    /**
     * Determine the namespace of a given tag
     * @param tag The tag to analyze
     * @return The namespace enum, or null if no namespace found
     */
    public static TagNamespace getNamespace(String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            return null;
        }
        
        for (TagNamespace namespace : TagNamespace.values()) {
            if (tag.startsWith(namespace.getPrefix())) {
                return namespace;
            }
        }
        
        return null; // No namespace found
    }
    
    /**
     * Check if a tag belongs to a specific namespace
     * @param tag The tag to check
     * @param namespace The namespace to check against
     * @return true if tag belongs to the namespace
     */
    public static boolean isInNamespace(String tag, TagNamespace namespace) {
        return namespace != null && tag != null && tag.startsWith(namespace.getPrefix());
    }
    
    /**
     * Validate that a tag follows proper namespace conventions
     * @param tag The tag to validate
     * @return true if tag is properly formatted
     */
    public static boolean isValidNamespacedTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            return false;
        }
        
        TagNamespace namespace = getNamespace(tag);
        if (namespace == null) {
            return false; // No namespace found
        }
        
        String suffix = extractSuffix(tag);
        return suffix != null && !suffix.trim().isEmpty() && 
               suffix.matches("^[A-Z0-9\\-]+$"); // Only uppercase letters, numbers, and hyphens
    }
    
    /**
     * Convert a legacy tag to a namespaced tag
     * @param legacyTag The existing tag without namespace
     * @param targetNamespace The namespace to apply
     * @return Properly namespaced tag
     */
    public static String convertLegacyTag(String legacyTag, TagNamespace targetNamespace) {
        if (legacyTag == null || targetNamespace == null) {
            return legacyTag;
        }
        
        // If already namespaced, return as-is
        if (getNamespace(legacyTag) != null) {
            return legacyTag;
        }
        
        return targetNamespace.createTag(legacyTag);
    }
}
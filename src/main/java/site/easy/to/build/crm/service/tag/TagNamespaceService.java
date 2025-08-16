package site.easy.to.build.crm.service.tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.TagNamespace;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing tag namespaces and preventing tag conflicts
 * Provides validation, conversion, and conflict detection for namespaced tags
 */
@Service
public class TagNamespaceService {
    
    private static final Logger log = LoggerFactory.getLogger(TagNamespaceService.class);
    
    /**
     * Create a portfolio tag with proper namespace
     * @param portfolioName The name or identifier for the portfolio
     * @return Namespaced portfolio tag
     */
    public String createPortfolioTag(String portfolioName) {
        return TagNamespace.PORTFOLIO.createTag(portfolioName);
    }
    
    /**
     * Create a portfolio tag with portfolio ID
     * @param portfolioId The portfolio ID
     * @return Namespaced portfolio tag
     */
    public String createPortfolioTag(Long portfolioId) {
        return TagNamespace.PORTFOLIO.createTag(portfolioId.toString());
    }
    
    /**
     * Create a block tag with proper namespace
     * @param blockName The name of the block
     * @return Namespaced block tag
     */
    public String createBlockTag(String blockName) {
        return TagNamespace.BLOCK.createTag(blockName);
    }
    
    /**
     * Create a block tag within a specific portfolio
     * @param portfolioId The portfolio ID
     * @param blockName The block name
     * @return Namespaced block tag with portfolio context
     */
    public String createBlockTag(Long portfolioId, String blockName) {
        String suffix = portfolioId + "-" + blockName;
        return TagNamespace.BLOCK.createTag(suffix);
    }
    
    /**
     * Create a maintenance tag with proper namespace
     * @param category The maintenance category
     * @return Namespaced maintenance tag
     */
    public String createMaintenanceTag(String category) {
        return TagNamespace.MAINTENANCE.createTag(category);
    }
    
    /**
     * Create a maintenance tag with category and subcategory
     * @param category The main category (e.g., "PLUMBING")
     * @param subcategory The subcategory (e.g., "EMERGENCY")
     * @return Namespaced maintenance tag
     */
    public String createMaintenanceTag(String category, String subcategory) {
        String suffix = category + "-" + subcategory;
        return TagNamespace.MAINTENANCE.createTag(suffix);
    }
    
    /**
     * Create a tenant tag with proper namespace
     * @param tenantCategory The tenant category
     * @return Namespaced tenant tag
     */
    public String createTenantTag(String tenantCategory) {
        return TagNamespace.TENANT.createTag(tenantCategory);
    }
    
    /**
     * Create a system tag with proper namespace
     * @param systemOperation The system operation or automation type
     * @return Namespaced system tag
     */
    public String createSystemTag(String systemOperation) {
        return TagNamespace.SYSTEM.createTag(systemOperation);
    }
    
    /**
     * Create a custom tag with proper namespace
     * @param customTag The custom tag content
     * @return Namespaced custom tag
     */
    public String createCustomTag(String customTag) {
        return TagNamespace.CUSTOM.createTag(customTag);
    }
    
    /**
     * Validate a collection of tags for namespace compliance
     * @param tags Collection of tags to validate
     * @return ValidationResult with details about validation
     */
    public TagValidationResult validateTags(Collection<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return new TagValidationResult(true, new ArrayList<>(), new ArrayList<>());
        }
        
        List<String> validTags = new ArrayList<>();
        List<String> invalidTags = new ArrayList<>();
        
        for (String tag : tags) {
            if (TagNamespace.isValidNamespacedTag(tag)) {
                validTags.add(tag);
            } else {
                invalidTags.add(tag);
                log.warn("Invalid namespaced tag found: {}", tag);
            }
        }
        
        boolean isValid = invalidTags.isEmpty();
        return new TagValidationResult(isValid, validTags, invalidTags);
    }
    
    /**
     * Detect potential conflicts between tags from different namespaces
     * @param tags Collection of tags to analyze
     * @return ConflictDetectionResult with conflict details
     */
    public TagConflictResult detectConflicts(Collection<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return new TagConflictResult(false, new ArrayList<>());
        }
        
        Map<TagNamespace, List<String>> tagsByNamespace = tags.stream()
            .filter(tag -> TagNamespace.getNamespace(tag) != null)
            .collect(Collectors.groupingBy(TagNamespace::getNamespace));
        
        List<TagConflict> conflicts = new ArrayList<>();
        
        // Check for similar suffixes across different namespaces
        for (TagNamespace namespace1 : tagsByNamespace.keySet()) {
            for (TagNamespace namespace2 : tagsByNamespace.keySet()) {
                if (namespace1 != namespace2) {
                    List<String> tags1 = tagsByNamespace.get(namespace1);
                    List<String> tags2 = tagsByNamespace.get(namespace2);
                    
                    for (String tag1 : tags1) {
                        String suffix1 = TagNamespace.extractSuffix(tag1);
                        for (String tag2 : tags2) {
                            String suffix2 = TagNamespace.extractSuffix(tag2);
                            
                            // Check for exact suffix matches or similar suffixes
                            if (suffix1.equals(suffix2) || isSimilarSuffix(suffix1, suffix2)) {
                                conflicts.add(new TagConflict(tag1, tag2, 
                                    "Similar suffixes in different namespaces: " + suffix1 + " vs " + suffix2));
                            }
                        }
                    }
                }
            }
        }
        
        return new TagConflictResult(!conflicts.isEmpty(), conflicts);
    }
    
    /**
     * Convert legacy tags to namespaced tags
     * @param legacyTags Collection of legacy tags
     * @param targetNamespace The namespace to apply to legacy tags
     * @return Collection of properly namespaced tags
     */
    public List<String> convertLegacyTags(Collection<String> legacyTags, TagNamespace targetNamespace) {
        if (legacyTags == null || targetNamespace == null) {
            return new ArrayList<>();
        }
        
        return legacyTags.stream()
            .map(tag -> TagNamespace.convertLegacyTag(tag, targetNamespace))
            .collect(Collectors.toList());
    }
    
    /**
     * Filter tags by namespace
     * @param tags Collection of tags to filter
     * @param namespace The namespace to filter by
     * @return List of tags in the specified namespace
     */
    public List<String> filterTagsByNamespace(Collection<String> tags, TagNamespace namespace) {
        if (tags == null || namespace == null) {
            return new ArrayList<>();
        }
        
        return tags.stream()
            .filter(tag -> TagNamespace.isInNamespace(tag, namespace))
            .collect(Collectors.toList());
    }
    
    /**
     * Get all portfolio tags from a collection
     * @param tags Collection of tags
     * @return List of portfolio tags only
     */
    public List<String> getPortfolioTags(Collection<String> tags) {
        return filterTagsByNamespace(tags, TagNamespace.PORTFOLIO);
    }
    
    /**
     * Get all maintenance tags from a collection
     * @param tags Collection of tags
     * @return List of maintenance tags only
     */
    public List<String> getMaintenanceTags(Collection<String> tags) {
        return filterTagsByNamespace(tags, TagNamespace.MAINTENANCE);
    }
    
    /**
     * Get all block tags from a collection
     * @param tags Collection of tags
     * @return List of block tags only
     */
    public List<String> getBlockTags(Collection<String> tags) {
        return filterTagsByNamespace(tags, TagNamespace.BLOCK);
    }
    
    /**
     * Convert legacy tag to namespaced format if needed
     * Used for backward compatibility during migration
     * @param tag The tag to convert
     * @param namespace The target namespace
     * @return Namespaced tag
     */
    public String ensureNamespaced(String tag, TagNamespace namespace) {
        if (tag == null || namespace == null) {
            return tag;
        }
        
        // If already namespaced, return as-is
        if (TagNamespace.isValidNamespacedTag(tag)) {
            return tag;
        }
        
        // Convert legacy tag to namespaced format
        return namespace.createTag(tag);
    }
    
    /**
     * Get all possible tag variations for a given tag (legacy + namespaced)
     * Useful for searching existing data during migration
     */
    public List<String> getTagVariations(String originalTag, TagNamespace namespace) {
        if (originalTag == null || namespace == null || originalTag.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String> variations = new ArrayList<>();
        
        // Add original tag
        variations.add(originalTag);
        
        // Add namespaced version if not already namespaced and not empty
        if (!TagNamespace.isValidNamespacedTag(originalTag) && !originalTag.trim().isEmpty()) {
            try {
                variations.add(namespace.createTag(originalTag));
            } catch (IllegalArgumentException e) {
                // Skip if tag creation fails (e.g., empty suffix after cleaning)
            }
        }
        
        // If it's namespaced, also add the suffix (legacy format)
        if (TagNamespace.isValidNamespacedTag(originalTag)) {
            String suffix = TagNamespace.extractSuffix(originalTag);
            if (!suffix.equals(originalTag) && !suffix.trim().isEmpty()) {
                variations.add(suffix);
            }
        }
        
        return variations;
    }
    
    /**
     * Check if two suffixes are similar (potential conflict)
     */
    private boolean isSimilarSuffix(String suffix1, String suffix2) {
        if (suffix1 == null || suffix2 == null) {
            return false;
        }
        
        // Simple similarity check - can be enhanced with more sophisticated algorithms
        return suffix1.toLowerCase().contains(suffix2.toLowerCase()) || 
               suffix2.toLowerCase().contains(suffix1.toLowerCase()) ||
               levenshteinDistance(suffix1, suffix2) <= 2;
    }
    
    /**
     * Calculate Levenshtein distance for similarity checking
     */
    private int levenshteinDistance(String s1, String s2) {
        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    costs[j] = j;
                } else {
                    if (j > 0) {
                        int newValue = costs[j - 1];
                        if (s1.charAt(i - 1) != s2.charAt(j - 1)) {
                            newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                        }
                        costs[j - 1] = lastValue;
                        lastValue = newValue;
                    }
                }
            }
            if (i > 0) {
                costs[s2.length()] = lastValue;
            }
        }
        return costs[s2.length()];
    }
    
    // ===== RESULT CLASSES =====
    
    public static class TagValidationResult {
        private final boolean isValid;
        private final List<String> validTags;
        private final List<String> invalidTags;
        
        public TagValidationResult(boolean isValid, List<String> validTags, List<String> invalidTags) {
            this.isValid = isValid;
            this.validTags = validTags;
            this.invalidTags = invalidTags;
        }
        
        public boolean isValid() { return isValid; }
        public List<String> getValidTags() { return validTags; }
        public List<String> getInvalidTags() { return invalidTags; }
    }
    
    public static class TagConflictResult {
        private final boolean hasConflicts;
        private final List<TagConflict> conflicts;
        
        public TagConflictResult(boolean hasConflicts, List<TagConflict> conflicts) {
            this.hasConflicts = hasConflicts;
            this.conflicts = conflicts;
        }
        
        public boolean hasConflicts() { return hasConflicts; }
        public List<TagConflict> getConflicts() { return conflicts; }
    }
    
    public static class TagConflict {
        private final String tag1;
        private final String tag2;
        private final String description;
        
        public TagConflict(String tag1, String tag2, String description) {
            this.tag1 = tag1;
            this.tag2 = tag2;
            this.description = description;
        }
        
        public String getTag1() { return tag1; }
        public String getTag2() { return tag2; }
        public String getDescription() { return description; }
    }
}
// PayPropTagGenerator.java - Utility for generating hierarchical PayProp tag names
package site.easy.to.build.crm.util;

import site.easy.to.build.crm.entity.Portfolio;
import site.easy.to.build.crm.entity.Block;

import java.util.regex.Pattern;

/**
 * Utility class for generating consistent PayProp tag names following the hierarchical naming convention:
 * 
 * Portfolio tags: PF-{PORTFOLIO_NAME}
 * Block tags: PF-{PORTFOLIO_NAME}-BL-{BLOCK_NAME}
 * 
 * All names are normalized to uppercase, alphanumeric characters with hyphens only.
 */
public class PayPropTagGenerator {
    
    // Constants for tag prefixes
    public static final String PORTFOLIO_PREFIX = "PF-";
    public static final String BLOCK_SEPARATOR = "-BL-";
    
    // Maximum lengths for PayProp API compatibility
    public static final int MAX_TAG_LENGTH = 100; // PayProp API limit
    public static final int MAX_PORTFOLIO_NAME_LENGTH = 40; // Leaves room for block names
    public static final int MAX_BLOCK_NAME_LENGTH = 30; // Reasonable limit for block names
    
    // Pattern for valid tag characters (alphanumeric and hyphens only)
    private static final Pattern INVALID_CHARS = Pattern.compile("[^A-Z0-9\\-]");
    private static final Pattern MULTIPLE_HYPHENS = Pattern.compile("-+");
    private static final Pattern LEADING_TRAILING_HYPHENS = Pattern.compile("^-|-$");
    
    /**
     * Generate a portfolio tag name
     * Format: PF-{PORTFOLIO_NAME}
     * 
     * @param portfolioName Portfolio name to convert
     * @return Generated tag name
     * @throws IllegalArgumentException if name is null, empty, or would result in invalid tag
     */
    public static String generatePortfolioTag(String portfolioName) {
        if (portfolioName == null || portfolioName.trim().isEmpty()) {
            throw new IllegalArgumentException("Portfolio name cannot be null or empty");
        }
        
        String normalizedName = normalizeNameForTag(portfolioName, MAX_PORTFOLIO_NAME_LENGTH);
        String tag = PORTFOLIO_PREFIX + normalizedName;
        
        validateTagLength(tag, "Portfolio");
        return tag;
    }
    
    /**
     * Generate a portfolio tag name from Portfolio entity
     * 
     * @param portfolio Portfolio entity
     * @return Generated tag name
     */
    public static String generatePortfolioTag(Portfolio portfolio) {
        if (portfolio == null || portfolio.getName() == null) {
            throw new IllegalArgumentException("Portfolio and portfolio name cannot be null");
        }
        
        return generatePortfolioTag(portfolio.getName());
    }
    
    /**
     * Generate a block tag name
     * Format: PF-{PORTFOLIO_NAME}-BL-{BLOCK_NAME}
     * 
     * @param portfolioName Portfolio name
     * @param blockName Block name
     * @return Generated tag name
     * @throws IllegalArgumentException if names are null/empty or would result in invalid tag
     */
    public static String generateBlockTag(String portfolioName, String blockName) {
        if (portfolioName == null || portfolioName.trim().isEmpty()) {
            throw new IllegalArgumentException("Portfolio name cannot be null or empty");
        }
        if (blockName == null || blockName.trim().isEmpty()) {
            throw new IllegalArgumentException("Block name cannot be null or empty");
        }
        
        String normalizedPortfolio = normalizeNameForTag(portfolioName, MAX_PORTFOLIO_NAME_LENGTH);
        String normalizedBlock = normalizeNameForTag(blockName, MAX_BLOCK_NAME_LENGTH);
        
        String tag = PORTFOLIO_PREFIX + normalizedPortfolio + BLOCK_SEPARATOR + normalizedBlock;
        
        validateTagLength(tag, "Block");
        return tag;
    }
    
    /**
     * Generate a block tag name from entities
     * 
     * @param portfolio Portfolio entity
     * @param block Block entity
     * @return Generated tag name
     */
    public static String generateBlockTag(Portfolio portfolio, Block block) {
        if (portfolio == null || portfolio.getName() == null) {
            throw new IllegalArgumentException("Portfolio and portfolio name cannot be null");
        }
        if (block == null || block.getName() == null) {
            throw new IllegalArgumentException("Block and block name cannot be null");
        }
        
        return generateBlockTag(portfolio.getName(), block.getName());
    }
    
    /**
     * Generate a block tag name when block belongs to a portfolio
     * 
     * @param block Block entity (must have portfolio relationship loaded)
     * @return Generated tag name
     */
    public static String generateBlockTag(Block block) {
        if (block == null || block.getName() == null) {
            throw new IllegalArgumentException("Block and block name cannot be null");
        }
        if (block.getPortfolio() == null || block.getPortfolio().getName() == null) {
            throw new IllegalArgumentException("Block must have portfolio with name");
        }
        
        return generateBlockTag(block.getPortfolio().getName(), block.getName());
    }
    
    /**
     * Normalize a name for use in tags
     * - Convert to uppercase
     * - Replace invalid characters with hyphens
     * - Collapse multiple hyphens to single hyphen
     * - Remove leading/trailing hyphens
     * - Truncate to maximum length
     * 
     * @param name Name to normalize
     * @param maxLength Maximum length after normalization
     * @return Normalized name
     */
    public static String normalizeNameForTag(String name, int maxLength) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        
        // Start with trimmed, uppercase name
        String normalized = name.trim().toUpperCase();
        
        // Replace invalid characters with hyphens
        normalized = INVALID_CHARS.matcher(normalized).replaceAll("-");
        
        // Collapse multiple hyphens
        normalized = MULTIPLE_HYPHENS.matcher(normalized).replaceAll("-");
        
        // Remove leading and trailing hyphens
        normalized = LEADING_TRAILING_HYPHENS.matcher(normalized).replaceAll("");
        
        // Truncate if too long, but avoid ending with hyphen
        if (normalized.length() > maxLength) {
            normalized = normalized.substring(0, maxLength);
            normalized = LEADING_TRAILING_HYPHENS.matcher(normalized).replaceAll("");
        }
        
        // Ensure we still have a valid name after normalization
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Name '" + name + "' cannot be normalized to valid tag format");
        }
        
        return normalized;
    }
    
    /**
     * Validate that a tag meets length requirements
     * 
     * @param tag Tag to validate
     * @param type Type of tag (for error messages)
     * @throws IllegalArgumentException if tag is too long
     */
    private static void validateTagLength(String tag, String type) {
        if (tag.length() > MAX_TAG_LENGTH) {
            throw new IllegalArgumentException(
                String.format("%s tag '%s' is too long (%d characters, max %d)", 
                             type, tag, tag.length(), MAX_TAG_LENGTH)
            );
        }
    }
    
    /**
     * Check if a string is a valid portfolio tag
     * 
     * @param tag Tag to validate
     * @return true if tag follows portfolio tag format
     */
    public static boolean isValidPortfolioTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = tag.trim();
        return trimmed.startsWith(PORTFOLIO_PREFIX) && 
               !trimmed.contains(BLOCK_SEPARATOR) &&
               trimmed.length() <= MAX_TAG_LENGTH;
    }
    
    /**
     * Check if a string is a valid block tag
     * 
     * @param tag Tag to validate
     * @return true if tag follows block tag format
     */
    public static boolean isValidBlockTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = tag.trim();
        return trimmed.startsWith(PORTFOLIO_PREFIX) && 
               trimmed.contains(BLOCK_SEPARATOR) &&
               trimmed.length() <= MAX_TAG_LENGTH;
    }
    
    /**
     * Extract portfolio name from a portfolio tag
     * 
     * @param portfolioTag Portfolio tag (PF-{NAME})
     * @return Portfolio name portion, or null if invalid format
     */
    public static String extractPortfolioNameFromTag(String portfolioTag) {
        if (!isValidPortfolioTag(portfolioTag)) {
            return null;
        }
        
        return portfolioTag.substring(PORTFOLIO_PREFIX.length());
    }
    
    /**
     * Extract portfolio name from a block tag
     * 
     * @param blockTag Block tag (PF-{PORTFOLIO}-BL-{BLOCK})
     * @return Portfolio name portion, or null if invalid format
     */
    public static String extractPortfolioNameFromBlockTag(String blockTag) {
        if (!isValidBlockTag(blockTag)) {
            return null;
        }
        
        int blockIndex = blockTag.indexOf(BLOCK_SEPARATOR);
        if (blockIndex == -1) {
            return null;
        }
        
        return blockTag.substring(PORTFOLIO_PREFIX.length(), blockIndex);
    }
    
    /**
     * Extract block name from a block tag
     * 
     * @param blockTag Block tag (PF-{PORTFOLIO}-BL-{BLOCK})
     * @return Block name portion, or null if invalid format
     */
    public static String extractBlockNameFromTag(String blockTag) {
        if (!isValidBlockTag(blockTag)) {
            return null;
        }
        
        int blockIndex = blockTag.indexOf(BLOCK_SEPARATOR);
        if (blockIndex == -1) {
            return null;
        }
        
        return blockTag.substring(blockIndex + BLOCK_SEPARATOR.length());
    }
    
    /**
     * Generate a unique tag name by appending a number if the base tag already exists
     * 
     * @param baseTag Base tag name
     * @param existingTags Set of existing tag names to check against
     * @param maxAttempts Maximum number of attempts to find unique name
     * @return Unique tag name
     * @throws IllegalStateException if unable to generate unique name within maxAttempts
     */
    public static String generateUniqueTag(String baseTag, java.util.Set<String> existingTags, int maxAttempts) {
        if (baseTag == null || baseTag.trim().isEmpty()) {
            throw new IllegalArgumentException("Base tag cannot be null or empty");
        }
        
        String candidate = baseTag.trim();
        
        // If base tag is unique, return it
        if (!existingTags.contains(candidate.toUpperCase())) {
            return candidate;
        }
        
        // Try appending numbers
        for (int i = 2; i <= maxAttempts; i++) {
            candidate = baseTag + "-" + i;
            
            if (candidate.length() <= MAX_TAG_LENGTH && 
                !existingTags.contains(candidate.toUpperCase())) {
                return candidate;
            }
        }
        
        throw new IllegalStateException(
            String.format("Unable to generate unique tag for '%s' after %d attempts", baseTag, maxAttempts)
        );
    }
    
    /**
     * Generate a unique tag name with default parameters
     * 
     * @param baseTag Base tag name
     * @param existingTags Set of existing tag names to check against
     * @return Unique tag name
     */
    public static String generateUniqueTag(String baseTag, java.util.Set<String> existingTags) {
        return generateUniqueTag(baseTag, existingTags, 99);
    }
    
    /**
     * Validate that a portfolio and block name combination would produce a valid tag
     * 
     * @param portfolioName Portfolio name
     * @param blockName Block name (can be null for portfolio-only validation)
     * @return Validation result with details
     */
    public static TagValidationResult validateNames(String portfolioName, String blockName) {
        try {
            if (blockName == null || blockName.trim().isEmpty()) {
                // Validate portfolio-only tag
                String tag = generatePortfolioTag(portfolioName);
                return TagValidationResult.success(tag);
            } else {
                // Validate block tag
                String tag = generateBlockTag(portfolioName, blockName);
                return TagValidationResult.success(tag);
            }
        } catch (IllegalArgumentException e) {
            return TagValidationResult.failure(e.getMessage());
        }
    }
    
    /**
     * Result of tag validation
     */
    public static class TagValidationResult {
        private final boolean valid;
        private final String generatedTag;
        private final String errorMessage;
        
        private TagValidationResult(boolean valid, String generatedTag, String errorMessage) {
            this.valid = valid;
            this.generatedTag = generatedTag;
            this.errorMessage = errorMessage;
        }
        
        public static TagValidationResult success(String generatedTag) {
            return new TagValidationResult(true, generatedTag, null);
        }
        
        public static TagValidationResult failure(String errorMessage) {
            return new TagValidationResult(false, null, errorMessage);
        }
        
        // Getters
        public boolean isValid() { return valid; }
        public String getGeneratedTag() { return generatedTag; }
        public String getErrorMessage() { return errorMessage; }
    }
}
package site.easy.to.build.crm.service.migration;

import org.junit.jupiter.api.Test;
import site.easy.to.build.crm.entity.TagNamespace;
import site.easy.to.build.crm.service.tag.TagNamespaceService;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for TagNamespaceMigrationService functionality
 */
public class TagNamespaceMigrationServiceTest {
    
    private final TagNamespaceService tagNamespaceService = new TagNamespaceService();
    
    @Test
    public void testEnsureNamespaced() {
        // Test legacy tag conversion
        String legacyTag = "London Portfolio";
        String namespacedTag = tagNamespaceService.ensureNamespaced(legacyTag, TagNamespace.PORTFOLIO);
        assertEquals("PF-LONDON-PORTFOLIO", namespacedTag);
        
        // Test already namespaced tag (should remain unchanged)
        String alreadyNamespaced = "PF-EXISTING-TAG";
        String result = tagNamespaceService.ensureNamespaced(alreadyNamespaced, TagNamespace.PORTFOLIO);
        assertEquals("PF-EXISTING-TAG", result);
    }
    
    @Test
    public void testGetTagVariations() {
        // Test legacy tag variations
        List<String> variations = tagNamespaceService.getTagVariations("London Portfolio", TagNamespace.PORTFOLIO);
        assertTrue(variations.contains("London Portfolio"));
        assertTrue(variations.contains("PF-LONDON-PORTFOLIO"));
        assertEquals(2, variations.size());
        
        // Test namespaced tag variations
        List<String> namespacedVariations = tagNamespaceService.getTagVariations("PF-LONDON-PORTFOLIO", TagNamespace.PORTFOLIO);
        assertTrue(namespacedVariations.contains("PF-LONDON-PORTFOLIO"));
        assertTrue(namespacedVariations.contains("LONDON-PORTFOLIO"));
        assertEquals(2, namespacedVariations.size());
    }
    
    @Test
    public void testMigrationScenarios() {
        // Test various portfolio name scenarios that might exist
        String[] testNames = {
            "Residential London",
            "Commercial Manchester", 
            "Student Housing Bristol",
            "Mixed Use Birmingham",
            "London_Portfolio_1",
            "Test Portfolio @#$"
        };
        
        for (String name : testNames) {
            String namespaced = tagNamespaceService.createPortfolioTag(name);
            assertTrue(TagNamespace.isValidNamespacedTag(namespaced));
            assertTrue(namespaced.startsWith("PF-"));
            
            // Ensure we can extract the original essence back
            String suffix = TagNamespace.extractSuffix(namespaced);
            assertNotNull(suffix);
            assertFalse(suffix.isEmpty());
        }
    }
    
    @Test
    public void testBlockTagMigration() {
        // Test block tag creation with portfolio context
        String blockTag = tagNamespaceService.createBlockTag(123L, "Building A");
        assertEquals("BL-123-BUILDING-A", blockTag);
        
        String simpleBlockTag = tagNamespaceService.createBlockTag("East Wing");
        assertEquals("BL-EAST-WING", simpleBlockTag);
    }
    
    @Test
    public void testNullAndEmptyHandling() {
        // Test null handling
        String nullResult = tagNamespaceService.ensureNamespaced(null, TagNamespace.PORTFOLIO);
        assertNull(nullResult);
        
        // Test empty string handling - should return empty list for empty strings
        List<String> emptyVariations = tagNamespaceService.getTagVariations("", TagNamespace.PORTFOLIO);
        assertTrue(emptyVariations.isEmpty());
        
        // Test whitespace-only string handling
        List<String> whitespaceVariations = tagNamespaceService.getTagVariations("   ", TagNamespace.PORTFOLIO);
        assertTrue(whitespaceVariations.isEmpty());
    }
}
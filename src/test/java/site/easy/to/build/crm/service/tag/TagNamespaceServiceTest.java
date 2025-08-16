package site.easy.to.build.crm.service.tag;

import org.junit.jupiter.api.Test;
import site.easy.to.build.crm.entity.TagNamespace;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for TagNamespace functionality (standalone tests without Spring context)
 */
public class TagNamespaceServiceTest {
    
    private final TagNamespaceService tagNamespaceService = new TagNamespaceService();
    
    @Test
    public void testCreatePortfolioTag() {
        String portfolioTag = tagNamespaceService.createPortfolioTag("Residential London");
        assertEquals("PF-RESIDENTIAL-LONDON", portfolioTag);
        
        String portfolioIdTag = tagNamespaceService.createPortfolioTag(123L);
        assertEquals("PF-123", portfolioIdTag);
    }
    
    @Test
    public void testCreateBlockTag() {
        String blockTag = tagNamespaceService.createBlockTag("Building A");
        assertEquals("BL-BUILDING-A", blockTag);
        
        String portfolioBlockTag = tagNamespaceService.createBlockTag(456L, "East Wing");
        assertEquals("BL-456-EAST-WING", portfolioBlockTag);
    }
    
    @Test
    public void testCreateMaintenanceTag() {
        String maintenanceTag = tagNamespaceService.createMaintenanceTag("Plumbing");
        assertEquals("MT-PLUMBING", maintenanceTag);
        
        String categoryTag = tagNamespaceService.createMaintenanceTag("Electrical", "Emergency");
        assertEquals("MT-ELECTRICAL-EMERGENCY", categoryTag);
    }
    
    @Test
    public void testTagNamespaceValidation() {
        assertTrue(TagNamespace.isValidNamespacedTag("PF-RESIDENTIAL-LONDON"));
        assertTrue(TagNamespace.isValidNamespacedTag("BL-BUILDING-A"));
        assertTrue(TagNamespace.isValidNamespacedTag("MT-PLUMBING-EMERGENCY"));
        
        assertFalse(TagNamespace.isValidNamespacedTag("INVALID-TAG"));
        assertFalse(TagNamespace.isValidNamespacedTag("PF-"));
        assertFalse(TagNamespace.isValidNamespacedTag(""));
        assertFalse(TagNamespace.isValidNamespacedTag(null));
    }
    
    @Test
    public void testExtractSuffix() {
        assertEquals("RESIDENTIAL-LONDON", TagNamespace.extractSuffix("PF-RESIDENTIAL-LONDON"));
        assertEquals("BUILDING-A", TagNamespace.extractSuffix("BL-BUILDING-A"));
        assertEquals("PLUMBING-EMERGENCY", TagNamespace.extractSuffix("MT-PLUMBING-EMERGENCY"));
        assertEquals("NO-PREFIX", TagNamespace.extractSuffix("NO-PREFIX"));
    }
    
    @Test
    public void testGetNamespace() {
        assertEquals(TagNamespace.PORTFOLIO, TagNamespace.getNamespace("PF-RESIDENTIAL-LONDON"));
        assertEquals(TagNamespace.BLOCK, TagNamespace.getNamespace("BL-BUILDING-A"));
        assertEquals(TagNamespace.MAINTENANCE, TagNamespace.getNamespace("MT-PLUMBING-EMERGENCY"));
        assertNull(TagNamespace.getNamespace("INVALID-TAG"));
    }
    
    @Test
    public void testConvertLegacyTag() {
        String converted = TagNamespace.convertLegacyTag("London Portfolio", TagNamespace.PORTFOLIO);
        assertEquals("PF-LONDON-PORTFOLIO", converted);
        
        String alreadyNamespaced = TagNamespace.convertLegacyTag("PF-EXISTING", TagNamespace.PORTFOLIO);
        assertEquals("PF-EXISTING", alreadyNamespaced);
    }
    
    @Test
    public void testFilterTagsByNamespace() {
        List<String> mixedTags = Arrays.asList(
            "PF-RESIDENTIAL-LONDON",
            "BL-BUILDING-A", 
            "MT-PLUMBING-EMERGENCY",
            "PF-COMMERCIAL-MANCHESTER",
            "BL-TOWER-1",
            "INVALID-TAG"
        );
        
        List<String> portfolioTags = tagNamespaceService.getPortfolioTags(mixedTags);
        assertEquals(2, portfolioTags.size());
        assertTrue(portfolioTags.contains("PF-RESIDENTIAL-LONDON"));
        assertTrue(portfolioTags.contains("PF-COMMERCIAL-MANCHESTER"));
        
        List<String> blockTags = tagNamespaceService.getBlockTags(mixedTags);
        assertEquals(2, blockTags.size());
        assertTrue(blockTags.contains("BL-BUILDING-A"));
        assertTrue(blockTags.contains("BL-TOWER-1"));
        
        List<String> maintenanceTags = tagNamespaceService.getMaintenanceTags(mixedTags);
        assertEquals(1, maintenanceTags.size());
        assertTrue(maintenanceTags.contains("MT-PLUMBING-EMERGENCY"));
    }
    
    @Test
    public void testTagValidation() {
        List<String> validTags = Arrays.asList(
            "PF-RESIDENTIAL-LONDON",
            "BL-BUILDING-A",
            "MT-PLUMBING-EMERGENCY"
        );
        
        TagNamespaceService.TagValidationResult result = tagNamespaceService.validateTags(validTags);
        assertTrue(result.isValid());
        assertEquals(3, result.getValidTags().size());
        assertEquals(0, result.getInvalidTags().size());
    }
    
    @Test
    public void testTagValidationWithInvalidTags() {
        List<String> mixedTags = Arrays.asList(
            "PF-RESIDENTIAL-LONDON",
            "INVALID-TAG",
            "PF-",
            "BL-BUILDING-A"
        );
        
        TagNamespaceService.TagValidationResult result = tagNamespaceService.validateTags(mixedTags);
        assertFalse(result.isValid());
        assertEquals(2, result.getValidTags().size());
        assertEquals(2, result.getInvalidTags().size());
    }
    
    @Test
    public void testConflictDetection() {
        List<String> conflictingTags = Arrays.asList(
            "PF-LONDON",
            "BL-LONDON",  // Similar suffix
            "MT-BUILDING",
            "PF-BUILDING" // Similar suffix
        );
        
        TagNamespaceService.TagConflictResult result = tagNamespaceService.detectConflicts(conflictingTags);
        assertTrue(result.hasConflicts());
        assertTrue(result.getConflicts().size() > 0);
    }
    
    @Test
    public void testTagCleaning() {
        // Test special character removal and formatting
        String cleanedTag = TagNamespace.PORTFOLIO.createTag("My Special Portfolio! @#$");
        assertEquals("PF-MY-SPECIAL-PORTFOLIO", cleanedTag);
        
        String underscoreTag = TagNamespace.BLOCK.createTag("building_with_underscores");
        assertEquals("BL-BUILDING-WITH-UNDERSCORES", underscoreTag);
    }
}
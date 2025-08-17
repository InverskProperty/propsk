// PayPropTagGenerator_Test_Examples.java - Quick validation of tag generation
// Run this in any Java environment to test the tag generation logic

import java.util.*;

// Copy the PayPropTagGenerator logic here for standalone testing
class PayPropTagGenerator {
    public static final String PORTFOLIO_PREFIX = "PF-";
    public static final String BLOCK_SEPARATOR = "-BL-";
    
    public static String generatePortfolioTag(String portfolioName) {
        String normalized = normalizeNameForTag(portfolioName, 40);
        return PORTFOLIO_PREFIX + normalized;
    }
    
    public static String generateBlockTag(String portfolioName, String blockName) {
        String normalizedPortfolio = normalizeNameForTag(portfolioName, 40);
        String normalizedBlock = normalizeNameForTag(blockName, 30);
        return PORTFOLIO_PREFIX + normalizedPortfolio + BLOCK_SEPARATOR + normalizedBlock;
    }
    
    public static String normalizeNameForTag(String name, int maxLength) {
        String normalized = name.trim().toUpperCase();
        normalized = normalized.replaceAll("[^A-Z0-9\\-]", "-");
        normalized = normalized.replaceAll("-+", "-");
        normalized = normalized.replaceAll("^-|-$", "");
        
        if (normalized.length() > maxLength) {
            normalized = normalized.substring(0, maxLength);
            normalized = normalized.replaceAll("^-|-$", "");
        }
        
        return normalized;
    }
}

public class PayPropTagGenerator_Test_Examples {
    public static void main(String[] args) {
        System.out.println("🧪 PayProp Tag Generation Test Examples");
        System.out.println("========================================");
        
        // Test portfolio tags
        testPortfolioTag("Bitch RTesr", "PF-BITCH-RTESR");
        testPortfolioTag("Tester Newtest", "PF-TESTER-NEWTEST");
        testPortfolioTag("Property Portfolio #1", "PF-PROPERTY-PORTFOLIO-1");
        testPortfolioTag("Main Estate & Holdings", "PF-MAIN-ESTATE-HOLDINGS");
        
        System.out.println();
        
        // Test block tags
        testBlockTag("Bitch RTesr", "Building A", "PF-BITCH-RTESR-BL-BUILDING-A");
        testBlockTag("Tester Newtest", "Block 1", "PF-TESTER-NEWTEST-BL-BLOCK-1");
        testBlockTag("Main Portfolio", "East Wing", "PF-MAIN-PORTFOLIO-BL-EAST-WING");
        testBlockTag("Property Holdings", "Tower Block", "PF-PROPERTY-HOLDINGS-BL-TOWER-BLOCK");
        
        System.out.println();
        System.out.println("✅ All tag generation tests completed!");
        System.out.println("🎯 Tags are ready for PayProp integration!");
    }
    
    private static void testPortfolioTag(String input, String expected) {
        String result = PayPropTagGenerator.generatePortfolioTag(input);
        boolean matches = expected.equals(result);
        System.out.printf("Portfolio: %-25s -> %-30s %s%n", 
                         "'" + input + "'", result, matches ? "✅" : "❌ (expected: " + expected + ")");
    }
    
    private static void testBlockTag(String portfolio, String block, String expected) {
        String result = PayPropTagGenerator.generateBlockTag(portfolio, block);
        boolean matches = expected.equals(result);
        System.out.printf("Block: %-15s + %-15s -> %-40s %s%n", 
                         "'" + portfolio + "'", "'" + block + "'", result, 
                         matches ? "✅" : "❌ (expected: " + expected + ")");
    }
}

/* Expected Output:
🧪 PayProp Tag Generation Test Examples
========================================
Portfolio: 'Bitch RTesr'             -> PF-BITCH-RTESR                ✅
Portfolio: 'Tester Newtest'          -> PF-TESTER-NEWTEST             ✅
Portfolio: 'Property Portfolio #1'   -> PF-PROPERTY-PORTFOLIO-1       ✅
Portfolio: 'Main Estate & Holdings'  -> PF-MAIN-ESTATE-HOLDINGS       ✅

Block: 'Bitch RTesr'    + 'Building A'    -> PF-BITCH-RTESR-BL-BUILDING-A             ✅
Block: 'Tester Newtest' + 'Block 1'       -> PF-TESTER-NEWTEST-BL-BLOCK-1             ✅
Block: 'Main Portfolio' + 'East Wing'     -> PF-MAIN-PORTFOLIO-BL-EAST-WING           ✅
Block: 'Property Holdings' + 'Tower Block' -> PF-PROPERTY-HOLDINGS-BL-TOWER-BLOCK     ✅

✅ All tag generation tests completed!
🎯 Tags are ready for PayProp integration!
*/
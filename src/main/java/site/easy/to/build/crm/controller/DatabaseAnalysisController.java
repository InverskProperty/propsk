package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/database-analysis")
public class DatabaseAnalysisController {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @GetMapping("/property-tables-analysis")
    public Map<String, Object> analyzePropertyTables() {
        Map<String, Object> analysis = new HashMap<>();
        
        try {
            // 1. Properties table analysis
            String propertiesQuery = """
                SELECT 
                    COUNT(*) as total_count,
                    COUNT(CASE WHEN is_archived = 'N' THEN 1 END) as active_count,
                    COUNT(CASE WHEN is_archived = 'Y' THEN 1 END) as archived_count,
                    COUNT(payprop_id) as with_payprop_id,
                    COUNT(*) - COUNT(payprop_id) as without_payprop_id,
                    MIN(created_at) as oldest_created,
                    MAX(created_at) as newest_created
                FROM properties
                """;
                
            Map<String, Object> propertiesStats = jdbcTemplate.queryForMap(propertiesQuery);
            analysis.put("properties_table", propertiesStats);
            
            // 2. PayProp export properties analysis  
            String payPropQuery = """
                SELECT 
                    COUNT(*) as total_count,
                    COUNT(CASE WHEN is_archived = 0 THEN 1 END) as active_count,
                    COUNT(CASE WHEN is_archived = 1 THEN 1 END) as archived_count,
                    MIN(created_at) as oldest_created,
                    MAX(created_at) as newest_created
                FROM payprop_export_properties
                """;
                
            Map<String, Object> payPropStats = jdbcTemplate.queryForMap(payPropQuery);
            analysis.put("payprop_export_properties_table", payPropStats);
            
            // 3. Overlap analysis
            String overlapQuery = """
                SELECT 
                    COUNT(DISTINCT p.id) as properties_with_payprop_match,
                    COUNT(DISTINCT pep.payprop_id) as payprop_with_properties_match
                FROM properties p 
                INNER JOIN payprop_export_properties pep ON p.payprop_id = pep.payprop_id
                """;
                
            Map<String, Object> overlapStats = jdbcTemplate.queryForMap(overlapQuery);
            analysis.put("table_overlap", overlapStats);
            
            // 4. Properties only in properties table (legacy)
            String legacyOnlyQuery = "SELECT COUNT(*) as legacy_only_count FROM properties WHERE payprop_id IS NULL";
            Integer legacyOnly = jdbcTemplate.queryForObject(legacyOnlyQuery, Integer.class);
            analysis.put("legacy_only_properties", legacyOnly);
            
            // 5. PayProp properties not in properties table
            String payPropOnlyQuery = """
                SELECT COUNT(*) as payprop_only_count 
                FROM payprop_export_properties pep
                WHERE NOT EXISTS (
                    SELECT 1 FROM properties p WHERE p.payprop_id = pep.payprop_id
                )
                """;
            Integer payPropOnly = jdbcTemplate.queryForObject(payPropOnlyQuery, Integer.class);
            analysis.put("payprop_only_properties", payPropOnly);
            
            // 6. Sample data from both tables
            String propertiesSampleQuery = """
                SELECT id, property_name, payprop_id, is_archived, created_at 
                FROM properties 
                ORDER BY created_at DESC 
                LIMIT 5
                """;
            List<Map<String, Object>> propertiesSample = jdbcTemplate.queryForList(propertiesSampleQuery);
            analysis.put("properties_sample", propertiesSample);
            
            String payPropSampleQuery = """
                SELECT payprop_id, name, address_first_line, address_city, is_archived, created_at
                FROM payprop_export_properties 
                ORDER BY created_at DESC 
                LIMIT 5
                """;
            List<Map<String, Object>> payPropSample = jdbcTemplate.queryForList(payPropSampleQuery);
            analysis.put("payprop_sample", payPropSample);
            
            return analysis;
            
        } catch (Exception e) {
            analysis.put("error", "Database analysis failed: " + e.getMessage());
            return analysis;
        }
    }
}
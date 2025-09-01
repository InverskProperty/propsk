package site.easy.to.build.crm.service.payprop;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PayPropMaintenanceCategoryService {

    public List<Map<String, Object>> getAllMaintenanceCategories() {
        return getFallbackCategories();
    }

    public Map<String, Object> findByPayPropId(Long payPropId) {
        List<Map<String, Object>> categories = getAllMaintenanceCategories();
        return categories.stream()
            .filter(cat -> payPropId.equals(cat.get("id")))
            .findFirst()
            .orElse(null);
    }

    private List<Map<String, Object>> getFallbackCategories() {
        List<Map<String, Object>> fallback = new ArrayList<>();
        
        String[] categories = {
            "General Maintenance", "Plumbing", "Electrical", "Heating/Cooling", 
            "Roofing", "Flooring", "Painting", "Appliance Repair", "Pest Control", 
            "Landscaping", "Security", "Emergency Repair"
        };
        
        for (int i = 0; i < categories.length; i++) {
            Map<String, Object> category = new HashMap<>();
            category.put("id", (long) (i + 1));
            category.put("name", categories[i]);
            category.put("description", "Default " + categories[i] + " category");
            fallback.add(category);
        }
        
        return fallback;
    }
}
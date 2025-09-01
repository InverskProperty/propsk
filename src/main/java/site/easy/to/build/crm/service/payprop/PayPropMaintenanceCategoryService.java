package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.raw.PayPropRawMaintenanceCategory;
import site.easy.to.build.crm.repository.raw.PayPropRawMaintenanceCategoryRepository;

import java.util.ArrayList;
import java.util.List;

@Service
public class PayPropMaintenanceCategoryService {

    @Autowired(required = false)
    private PayPropRawMaintenanceCategoryRepository maintenanceCategoryRepository;

    public List<PayPropRawMaintenanceCategory> getAllMaintenanceCategories() {
        if (maintenanceCategoryRepository == null) {
            return getFallbackCategories();
        }
        
        try {
            List<PayPropRawMaintenanceCategory> categories = maintenanceCategoryRepository.findAll();
            return categories.isEmpty() ? getFallbackCategories() : categories;
        } catch (Exception e) {
            return getFallbackCategories();
        }
    }

    public PayPropRawMaintenanceCategory findByPayPropId(Long payPropId) {
        if (maintenanceCategoryRepository == null) {
            return null;
        }
        
        try {
            return maintenanceCategoryRepository.findByPayPropMaintenanceCategoryId(payPropId);
        } catch (Exception e) {
            return null;
        }
    }

    private List<PayPropRawMaintenanceCategory> getFallbackCategories() {
        List<PayPropRawMaintenanceCategory> fallback = new ArrayList<>();
        
        String[] categories = {
            "General Maintenance", "Plumbing", "Electrical", "Heating/Cooling", 
            "Roofing", "Flooring", "Painting", "Appliance Repair", "Pest Control", 
            "Landscaping", "Security", "Emergency Repair"
        };
        
        for (int i = 0; i < categories.length; i++) {
            PayPropRawMaintenanceCategory category = new PayPropRawMaintenanceCategory();
            category.setPayPropMaintenanceCategoryId((long) (i + 1));
            category.setName(categories[i]);
            category.setDescription("Default " + categories[i] + " category");
            fallback.add(category);
        }
        
        return fallback;
    }
}
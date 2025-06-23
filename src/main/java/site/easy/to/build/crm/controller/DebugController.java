package site.easy.to.build.crm.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.util.ArrayList;

@Controller
@RequestMapping("/debug")
public class DebugController {

    @GetMapping("/portfolio-test")
    public String testPortfolio(Model model) {
        // Add all required attributes with safe default values
        model.addAttribute("totalProperties", 5);
        model.addAttribute("occupiedCount", 3);
        model.addAttribute("vacantCount", 2);
        model.addAttribute("syncedCount", 1);
        model.addAttribute("syncedProperties", 1);
        model.addAttribute("readyForSync", 4);
        model.addAttribute("totalRentPotential", new BigDecimal("2500.00"));
        model.addAttribute("occupiedProperties", 3);
        model.addAttribute("vacantProperties", 2);
        model.addAttribute("flatCount", 3);
        model.addAttribute("houseCount", 2);
        model.addAttribute("averageRent", new BigDecimal("500.00"));
        model.addAttribute("properties", new ArrayList<>());
        
        return "property/portfolio-overview";
    }

    @GetMapping("/sync-test")
    public String testSync(Model model) {
        // Add all required attributes with safe default values
        model.addAttribute("synced", new ArrayList<>());
        model.addAttribute("needsSync", new ArrayList<>());
        model.addAttribute("missingFields", new ArrayList<>());
        model.addAttribute("totalProperties", 5);
        model.addAttribute("occupiedCount", 3);
        model.addAttribute("vacantCount", 2);
        model.addAttribute("syncedCount", 1);
        model.addAttribute("readyForSync", 4);
        model.addAttribute("totalRentPotential", new BigDecimal("2500.00"));
        
        return "property/sync-status";
    }

    @GetMapping("/update-test")
    public String testUpdate(Model model) {
        // Create a simple test property object
        TestProperty property = new TestProperty();
        property.setId(1L);
        property.setPropertyName("Test Property");
        property.setCity("London");
        property.setPostcode("SW1A 1AA");
        
        model.addAttribute("property", property);
        
        return "property/update-property";
    }

    // Simple test class to avoid dependency issues
    public static class TestProperty {
        private Long id;
        private String propertyName;
        private String propertyType;
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String county;
        private String postcode;
        private String countryCode = "UK";
        private Integer bedrooms;
        private Double bathrooms;
        private String furnished;
        private BigDecimal monthlyPayment;
        private String comment;

        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        
        public String getPropertyName() { return propertyName; }
        public void setPropertyName(String propertyName) { this.propertyName = propertyName; }
        
        public String getPropertyType() { return propertyType; }
        public void setPropertyType(String propertyType) { this.propertyType = propertyType; }
        
        public String getAddressLine1() { return addressLine1; }
        public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }
        
        public String getAddressLine2() { return addressLine2; }
        public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }
        
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        
        public String getCounty() { return county; }
        public void setCounty(String county) { this.county = county; }
        
        public String getPostcode() { return postcode; }
        public void setPostcode(String postcode) { this.postcode = postcode; }
        
        public String getCountryCode() { return countryCode; }
        public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
        
        public Integer getBedrooms() { return bedrooms; }
        public void setBedrooms(Integer bedrooms) { this.bedrooms = bedrooms; }
        
        public Double getBathrooms() { return bathrooms; }
        public void setBathrooms(Double bathrooms) { this.bathrooms = bathrooms; }
        
        public String getFurnished() { return furnished; }
        public void setFurnished(String furnished) { this.furnished = furnished; }
        
        public BigDecimal getMonthlyPayment() { return monthlyPayment; }
        public void setMonthlyPayment(BigDecimal monthlyPayment) { this.monthlyPayment = monthlyPayment; }
        
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }
}
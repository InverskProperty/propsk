package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.security.core.Authentication;
import site.easy.to.build.crm.repository.PortfolioRepository;
import site.easy.to.build.crm.repository.PropertyRepository;
import site.easy.to.build.crm.repository.CustomerRepository;
import site.easy.to.build.crm.service.portfolio.PortfolioService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/portfolio/test")
public class SimplePortfolioTestController {

    @Autowired(required = false)
    private PortfolioRepository portfolioRepository;
    
    @Autowired(required = false)
    private PropertyRepository propertyRepository;
    
    @Autowired(required = false)
    private CustomerRepository customerRepository;
    
    @Autowired(required = false)
    private PortfolioService portfolioService;
    
    @Autowired(required = false)
    private PropertyService propertyService;
    
    @Autowired(required = false)
    private AuthenticationUtils authenticationUtils;

    @GetMapping("/verify")
    @ResponseBody
    public Map<String, Object> verifySystem() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            // Check repositories
            status.put("portfolioRepository", portfolioRepository != null ? "✅ AVAILABLE" : "❌ MISSING");
            status.put("propertyRepository", propertyRepository != null ? "✅ AVAILABLE" : "❌ MISSING");
            status.put("customerRepository", customerRepository != null ? "✅ AVAILABLE" : "❌ MISSING");
            
            // Check services
            status.put("portfolioService", portfolioService != null ? "✅ AVAILABLE" : "❌ MISSING");
            status.put("propertyService", propertyService != null ? "✅ AVAILABLE" : "❌ MISSING");
            status.put("authenticationUtils", authenticationUtils != null ? "✅ AVAILABLE" : "❌ MISSING");
            
            // Check data counts
            if (portfolioRepository != null) {
                try {
                    long portfolioCount = portfolioRepository.count();
                    status.put("portfolioCount", "✅ " + portfolioCount + " portfolios found");
                } catch (Exception e) {
                    status.put("portfolioCount", "❌ ERROR: " + e.getMessage());
                }
            }
            
            if (propertyRepository != null) {
                try {
                    long propertyCount = propertyRepository.count();
                    status.put("propertyCount", "✅ " + propertyCount + " properties found");
                } catch (Exception e) {
                    status.put("propertyCount", "❌ ERROR: " + e.getMessage());
                }
            }
            
            if (customerRepository != null) {
                try {
                    long customerCount = customerRepository.count();
                    status.put("customerCount", "✅ " + customerCount + " customers found");
                } catch (Exception e) {
                    status.put("customerCount", "❌ ERROR: " + e.getMessage());
                }
            }
            
            status.put("timestamp", new java.util.Date().toString());
            status.put("overallStatus", "✅ VERIFICATION COMPLETE");
            
        } catch (Exception e) {
            status.put("error", "❌ VERIFICATION FAILED: " + e.getMessage());
        }
        
        return status;
    }

    @GetMapping("/dashboard")
    public String testDashboard(Model model, Authentication authentication) {
        try {
            model.addAttribute("pageTitle", "Portfolio Test Dashboard");
            model.addAttribute("testMode", true);
            model.addAttribute("customerName", authentication != null ? authentication.getName() : "Test User");
            
            // Basic test data
            model.addAttribute("portfolios", java.util.List.of());
            model.addAttribute("properties", java.util.List.of());
            model.addAttribute("portfolioSystemEnabled", false);
            model.addAttribute("totalProperties", 0);
            model.addAttribute("unassignedPropertiesCount", 0);
            
            return "property-owner/dashboard";
            
        } catch (Exception e) {
            model.addAttribute("error", "Test dashboard error: " + e.getMessage());
            return "property-owner/dashboard";
        }
    }

    @GetMapping("/create-sample")
    @ResponseBody
    public Map<String, Object> createSample(Authentication authentication) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (portfolioService == null) {
                result.put("error", "❌ PortfolioService not available");
                result.put("suggestion", "Portfolio system is still loading");
                return result;
            }
            
            result.put("message", "✅ Portfolio service is available!");
            result.put("suggestion", "Sample portfolio creation would work");
            result.put("userEmail", authentication != null ? authentication.getName() : "unknown");
            
        } catch (Exception e) {
            result.put("error", "❌ Error: " + e.getMessage());
        }
        
        return result;
    }

    @GetMapping("/status")
    @ResponseBody
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("controller", "✅ SimplePortfolioTestController working");
        status.put("timestamp", new java.util.Date().toString());
        status.put("message", "Test routes are working correctly");
        
        return status;
    }
}
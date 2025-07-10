package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/properties")
@CrossOrigin(origins = "*")
public class PropertyApiController {
    
    @Autowired
    private PropertyService propertyService;
    
    @Autowired
    private AuthenticationUtils authenticationUtils;
    
    /**
     * Get unassigned properties for the workflow interface
     */
    @GetMapping("/unassigned")
    public ResponseEntity<Map<String, Object>> getUnassignedProperties(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String propertyType,
            @RequestParam(required = false) String syncStatus,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Get all unassigned properties
            List<Property> unassignedProperties = propertyService.findUnassignedProperties();
            
            // Apply filters if provided
            if (city != null && !city.isEmpty()) {
                unassignedProperties = unassignedProperties.stream()
                    .filter(p -> city.equalsIgnoreCase(p.getCity()))
                    .collect(Collectors.toList());
            }
            
            if (propertyType != null && !propertyType.isEmpty()) {
                unassignedProperties = unassignedProperties.stream()
                    .filter(p -> propertyType.equalsIgnoreCase(p.getPropertyType()))
                    .collect(Collectors.toList());
            }
            
            if (syncStatus != null && !syncStatus.isEmpty()) {
                unassignedProperties = unassignedProperties.stream()
                    .filter(p -> {
                        switch (syncStatus) {
                            case "synced": return p.getPayPropId() != null;
                            case "pending": return p.getPayPropId() == null;
                            default: return true;
                        }
                    })
                    .collect(Collectors.toList());
            }
            
            // Convert to DTOs
            List<Map<String, Object>> propertyDTOs = unassignedProperties.stream()
                .map(this::convertPropertyToDTO)
                .collect(Collectors.toList());
            
            // Get unique cities and property types for filters
            List<String> cities = unassignedProperties.stream()
                .map(Property::getCity)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
                
            List<String> types = unassignedProperties.stream()
                .map(Property::getPropertyType)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
            
            response.put("success", true);
            response.put("properties", propertyDTOs);
            response.put("count", propertyDTOs.size());
            response.put("cities", cities);
            response.put("propertyTypes", types);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("❌ Error in /api/properties/unassigned: " + e.getMessage());
            e.printStackTrace();
            
            response.put("success", false);
            response.put("message", e.getMessage());
            response.put("properties", new ArrayList<>());
            response.put("count", 0);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get all properties (fallback endpoint)
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllProperties(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<Property> allProperties = propertyService.findAll();
            
            List<Map<String, Object>> propertyDTOs = allProperties.stream()
                .map(this::convertPropertyToDTO)
                .collect(Collectors.toList());
            
            response.put("success", true);
            response.put("properties", propertyDTOs);
            response.put("count", propertyDTOs.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Convert Property entity to DTO for API response
     */
    private Map<String, Object> convertPropertyToDTO(Property property) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", property.getId());
        dto.put("propertyName", property.getPropertyName() != null ? property.getPropertyName() : "Unnamed Property");
        dto.put("fullAddress", property.getFullAddress());
        dto.put("city", property.getCity());
        dto.put("postcode", property.getPostcode());
        dto.put("propertyType", property.getPropertyType());
        dto.put("monthlyPayment", property.getMonthlyPayment());
        dto.put("payPropId", property.getPayPropId());
        dto.put("isPayPropSynced", property.getPayPropId() != null);
        
        // Portfolio info
        if (property.getPortfolio() != null) {
            dto.put("portfolio", Map.of(
                "id", property.getPortfolio().getId(),
                "name", property.getPortfolio().getName()
            ));
        }
        
        // Block info
        if (property.getBlock() != null) {
            dto.put("block", Map.of(
                "id", property.getBlock().getId(),
                "name", property.getBlock().getName()
            ));
        }
        
        return dto;
    }
}
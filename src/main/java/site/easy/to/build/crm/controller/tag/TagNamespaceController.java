package site.easy.to.build.crm.controller.tag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.service.tag.TagNamespaceService;
import site.easy.to.build.crm.service.block.BlockTagService;
import site.easy.to.build.crm.service.portfolio.PortfolioService;
import site.easy.to.build.crm.util.AuthorizationUtil;

import java.util.*;

/**
 * Controller for tag namespace management and validation
 * Provides endpoints for tag validation, conflict detection, and migration
 */
@Controller
@RequestMapping("/api/tags/namespace")
public class TagNamespaceController {
    
    private static final Logger log = LoggerFactory.getLogger(TagNamespaceController.class);
    
    @Autowired
    private TagNamespaceService tagNamespaceService;
    
    @Autowired
    private BlockTagService blockTagService;
    
    @Autowired
    private PortfolioService portfolioService;
    
    /**
     * Validate a collection of tags for namespace compliance
     */
    @PostMapping("/validate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> validateTags(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") && 
                !AuthorizationUtil.hasRole(authentication, "ROLE_EMPLOYEE")) {
                response.put("success", false);
                response.put("message", "Access denied");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            
            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) request.get("tags");
            
            if (tags == null || tags.isEmpty()) {
                response.put("success", false);
                response.put("message", "No tags provided for validation");
                return ResponseEntity.badRequest().body(response);
            }
            
            TagNamespaceService.TagValidationResult validationResult = 
                tagNamespaceService.validateTags(tags);
            
            TagNamespaceService.TagConflictResult conflictResult = 
                tagNamespaceService.detectConflicts(tags);
            
            response.put("success", validationResult.isValid() && !conflictResult.hasConflicts());
            response.put("validation", Map.of(
                "isValid", validationResult.isValid(),
                "validTags", validationResult.getValidTags(),
                "invalidTags", validationResult.getInvalidTags()
            ));
            response.put("conflicts", Map.of(
                "hasConflicts", conflictResult.hasConflicts(),
                "conflictDetails", conflictResult.getConflicts().stream()
                    .map(conflict -> Map.of(
                        "tag1", conflict.getTag1(),
                        "tag2", conflict.getTag2(),
                        "description", conflict.getDescription()
                    ))
                    .toList()
            ));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error validating tags: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Validation failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Convert legacy tags to namespaced format
     */
    @PostMapping("/migrate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> migrateLegacyTags(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
                response.put("success", false);
                response.put("message", "Admin access required");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            
            @SuppressWarnings("unchecked")
            List<String> legacyTags = (List<String>) request.get("legacyTags");
            String targetNamespaceStr = (String) request.get("targetNamespace");
            
            if (legacyTags == null || targetNamespaceStr == null) {
                response.put("success", false);
                response.put("message", "Legacy tags and target namespace are required");
                return ResponseEntity.badRequest().body(response);
            }
            
            TagNamespace targetNamespace;
            try {
                targetNamespace = TagNamespace.valueOf(targetNamespaceStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                response.put("success", false);
                response.put("message", "Invalid namespace: " + targetNamespaceStr);
                return ResponseEntity.badRequest().body(response);
            }
            
            List<String> convertedTags = tagNamespaceService.convertLegacyTags(legacyTags, targetNamespace);
            
            response.put("success", true);
            response.put("originalTags", legacyTags);
            response.put("convertedTags", convertedTags);
            response.put("targetNamespace", targetNamespace.name());
            response.put("message", "Legacy tags converted successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error migrating legacy tags: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Migration failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get all available namespaces and their descriptions
     */
    @GetMapping("/namespaces")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAvailableNamespaces(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<Map<String, Object>> namespaces = new ArrayList<>();
            
            for (TagNamespace namespace : TagNamespace.values()) {
                namespaces.add(Map.of(
                    "name", namespace.name(),
                    "prefix", namespace.getPrefix(),
                    "description", namespace.getDescription()
                ));
            }
            
            response.put("success", true);
            response.put("namespaces", namespaces);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting namespaces: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Failed to get namespaces: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Create a new namespaced tag
     */
    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createNamespacedTag(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") && 
                !AuthorizationUtil.hasRole(authentication, "ROLE_EMPLOYEE")) {
                response.put("success", false);
                response.put("message", "Access denied");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            
            String namespaceStr = (String) request.get("namespace");
            String suffix = (String) request.get("suffix");
            
            if (namespaceStr == null || suffix == null) {
                response.put("success", false);
                response.put("message", "Namespace and suffix are required");
                return ResponseEntity.badRequest().body(response);
            }
            
            TagNamespace namespace;
            try {
                namespace = TagNamespace.valueOf(namespaceStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                response.put("success", false);
                response.put("message", "Invalid namespace: " + namespaceStr);
                return ResponseEntity.badRequest().body(response);
            }
            
            String namespacedTag = namespace.createTag(suffix);
            
            response.put("success", true);
            response.put("namespacedTag", namespacedTag);
            response.put("namespace", namespace.name());
            response.put("prefix", namespace.getPrefix());
            response.put("suffix", TagNamespace.extractSuffix(namespacedTag));
            response.put("message", "Namespaced tag created successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error creating namespaced tag: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Tag creation failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Filter tags by namespace
     */
    @PostMapping("/filter")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> filterTagsByNamespace(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) request.get("tags");
            String namespaceStr = (String) request.get("namespace");
            
            if (tags == null || namespaceStr == null) {
                response.put("success", false);
                response.put("message", "Tags and namespace are required");
                return ResponseEntity.badRequest().body(response);
            }
            
            TagNamespace namespace;
            try {
                namespace = TagNamespace.valueOf(namespaceStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                response.put("success", false);
                response.put("message", "Invalid namespace: " + namespaceStr);
                return ResponseEntity.badRequest().body(response);
            }
            
            List<String> filteredTags = tagNamespaceService.filterTagsByNamespace(tags, namespace);
            
            response.put("success", true);
            response.put("originalTags", tags);
            response.put("filteredTags", filteredTags);
            response.put("namespace", namespace.name());
            response.put("message", "Tags filtered successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error filtering tags: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Filtering failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Validate block tags for a specific portfolio
     */
    @GetMapping("/portfolio/{id}/validate-blocks")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> validatePortfolioBlockTags(
            @PathVariable("id") Long portfolioId,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER") && 
                !AuthorizationUtil.hasRole(authentication, "ROLE_EMPLOYEE")) {
                response.put("success", false);
                response.put("message", "Access denied");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            
            Portfolio portfolio = portfolioService.findById(portfolioId);
            if (portfolio == null) {
                response.put("success", false);
                response.put("message", "Portfolio not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            BlockTagService.BlockTagValidationResult validationResult = 
                blockTagService.validateBlockTags(portfolio);
            
            response.put("success", validationResult.isValid());
            response.put("portfolioId", portfolioId);
            response.put("portfolioName", portfolio.getName());
            response.put("isValid", validationResult.isValid());
            response.put("conflicts", validationResult.getConflicts());
            response.put("message", validationResult.isValid() ? 
                "All block tags are valid" : "Block tag conflicts found");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error validating portfolio block tags: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Validation failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
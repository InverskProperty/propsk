package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.BlockRepository;
import site.easy.to.build.crm.repository.PropertyBlockAssignmentRepository;
import site.easy.to.build.crm.repository.PropertyRepository;
import site.easy.to.build.crm.service.portfolio.PortfolioBlockService;
import site.easy.to.build.crm.service.portfolio.PortfolioService;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.util.AuthenticationUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * PropertyOwnerBlockController - Customer-facing block management for property owners
 * Provides full block functionality with authorization checks to ensure owners only access their blocks
 */
@Controller
@RequestMapping("/customer-login/blocks")
public class PropertyOwnerBlockController {

    private static final Logger log = LoggerFactory.getLogger(PropertyOwnerBlockController.class);

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private PortfolioBlockService blockService;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private AuthenticationUtils authenticationUtils;

    @Autowired
    private PropertyBlockAssignmentRepository propertyBlockAssignmentRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    // ===== AUTHORIZATION HELPERS =====

    /**
     * Get logged-in customer (owner or delegated user)
     * FIXED: Use email-based authentication like PropertyOwnerController
     */
    private Customer getLoggedInCustomer(Authentication auth) {
        log.info("🔍 getLoggedInCustomer - Starting authentication lookup");

        try {
            if (auth == null) {
                log.warn("❌ Authentication is null");
                return null;
            }

            // CRITICAL FIX: Get email directly from authentication instead of relying on user ID mapping
            String email = auth.getName();
            log.info("🔍 Email from authentication: {}", email);

            if (email == null || email.trim().isEmpty()) {
                log.warn("❌ No email found in authentication");
                return null;
            }

            // Direct email lookup using CustomerService (same approach as PropertyOwnerController)
            Customer customer = customerService.findByEmail(email);
            if (customer != null) {
                log.info("✅ Found customer by email: {} (customer_id: {}, type: {})",
                    email, customer.getCustomerId(), customer.getCustomerType());

                // Verify this is a property owner OR delegated user
                boolean isPropertyOwner = customer.getCustomerType() == CustomerType.PROPERTY_OWNER;
                boolean isDelegatedUser = customer.getCustomerType() == CustomerType.DELEGATED_USER;

                if (isPropertyOwner || isDelegatedUser) {
                    log.info("✅ Customer validation passed");
                    return customer;
                } else {
                    log.warn("❌ Customer is neither PROPERTY_OWNER nor DELEGATED_USER: {}", customer.getCustomerType());
                    return null;
                }
            }

            log.warn("❌ Customer not found by email: {}", email);
            return null;
        } catch (Exception e) {
            log.error("Failed to get logged-in customer: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Check if customer has access to a block
     */
    private boolean hasAccessToBlock(Customer customer, Long blockId) {
        if (customer == null || blockId == null) return false;

        try {
            Block block = blockRepository.findById(blockId).orElse(null);
            if (block == null) return false;

            // Check if block belongs to customer's portfolio
            Portfolio portfolio = block.getPortfolio();
            if (portfolio == null) return false;

            // PROPERTY_OWNER: Check if they own the portfolio
            if (customer.getCustomerType() == CustomerType.PROPERTY_OWNER) {
                return portfolio.getPropertyOwnerId() != null &&
                       portfolio.getPropertyOwnerId().equals(customer.getCustomerId().longValue());
            }

            // DELEGATED_USER: Check if they have access to any property in the block
            if (customer.getCustomerType() == CustomerType.DELEGATED_USER) {
                List<Property> blockProperties = propertyBlockAssignmentRepository.findPropertiesByBlockIdOrdered(blockId);
                List<Property> customerProperties = propertyService.findPropertiesByCustomerAssignments(customer.getCustomerId());

                // Check if any block properties match customer's assigned properties
                for (Property blockProp : blockProperties) {
                    for (Property custProp : customerProperties) {
                        if (blockProp.getId().equals(custProp.getId())) {
                            return true;
                        }
                    }
                }
            }

            return false;
        } catch (Exception e) {
            log.error("Error checking block access for customer {}: {}", customer.getCustomerId(), e.getMessage());
            return false;
        }
    }

    /**
     * Get all blocks accessible to customer
     */
    private List<Block> getAccessibleBlocks(Customer customer) {
        if (customer == null) return new ArrayList<>();

        try {
            if (customer.getCustomerType() == CustomerType.PROPERTY_OWNER) {
                // Get all blocks from owner's portfolios
                List<Portfolio> portfolios = portfolioService.findPortfoliosForPropertyOwnerWithBlocks(customer.getCustomerId());
                List<Block> allBlocks = new ArrayList<>();
                for (Portfolio portfolio : portfolios) {
                    if (portfolio.getBlocks() != null) {
                        allBlocks.addAll(portfolio.getBlocks().stream()
                            .filter(b -> "Y".equals(b.getIsActive()))
                            .collect(Collectors.toList()));
                    }
                }
                return allBlocks;

            } else if (customer.getCustomerType() == CustomerType.DELEGATED_USER) {
                // Get blocks containing properties the delegated user has access to
                List<Property> userProperties = propertyService.findPropertiesByCustomerAssignments(customer.getCustomerId());
                Set<Long> accessibleBlockIds = new HashSet<>();

                for (Property property : userProperties) {
                    Optional<Block> block = propertyBlockAssignmentRepository.findBlockByPropertyId(property.getId());
                    block.ifPresent(b -> accessibleBlockIds.add(b.getId()));
                }

                return blockRepository.findAllById(accessibleBlockIds).stream()
                    .filter(b -> "Y".equals(b.getIsActive()))
                    .collect(Collectors.toList());
            }

            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Error getting accessible blocks for customer {}: {}", customer.getCustomerId(), e.getMessage());
            return new ArrayList<>();
        }
    }

    // ===== VIEW ROUTES =====

    /**
     * Display all blocks accessible to owner
     * GET /customer-login/blocks
     */
    @GetMapping
    public String viewAllBlocks(Model model, Authentication auth) {
        log.info("📋 Property owner viewing all blocks");

        Customer customer = getLoggedInCustomer(auth);
        if (customer == null) {
            return "redirect:/customer-login?error=Authentication+required";
        }

        model.addAttribute("customer", customer);
        model.addAttribute("isOwner", customer.getCustomerType() == CustomerType.PROPERTY_OWNER);

        return "customer-login/blocks/all-blocks";
    }

    /**
     * Display single block details
     * GET /customer-login/blocks/{id}
     */
    @GetMapping("/{id}")
    public String viewBlockDetails(@PathVariable Long id, Model model, Authentication auth) {
        log.info("📖 Property owner viewing block {}", id);

        Customer customer = getLoggedInCustomer(auth);
        if (customer == null) {
            return "redirect:/customer-login?error=Authentication+required";
        }

        // Check authorization
        if (!hasAccessToBlock(customer, id)) {
            log.warn("Customer {} denied access to block {}", customer.getCustomerId(), id);
            return "redirect:/customer-login/blocks?error=Access+denied";
        }

        Optional<Block> blockOpt = blockRepository.findById(id);
        if (!blockOpt.isPresent()) {
            return "redirect:/customer-login/blocks?error=Block+not+found";
        }

        Block block = blockOpt.get();
        model.addAttribute("block", block);
        model.addAttribute("customer", customer);
        model.addAttribute("isOwner", customer.getCustomerType() == CustomerType.PROPERTY_OWNER);

        // Get property count
        long propertyCount = blockService.getPropertyCount(id);
        model.addAttribute("propertyCount", propertyCount);

        // Get available capacity
        Integer availableCapacity = blockService.getAvailableCapacity(id);
        model.addAttribute("availableCapacity", availableCapacity);

        return "customer-login/blocks/block-details";
    }

    /**
     * Display block edit page (owners only)
     * GET /customer-login/blocks/{id}/edit
     */
    @GetMapping("/{id}/edit")
    public String editBlockPage(@PathVariable Long id, Model model, Authentication auth) {
        log.info("✏️ Property owner editing block {}", id);

        Customer customer = getLoggedInCustomer(auth);
        if (customer == null) {
            return "redirect:/customer-login?error=Authentication+required";
        }

        // Only property owners can edit blocks, not delegated users
        if (customer.getCustomerType() != CustomerType.PROPERTY_OWNER) {
            log.warn("Delegated user {} attempted to edit block {}", customer.getCustomerId(), id);
            return "redirect:/customer-login/blocks/" + id + "?error=Editing+not+permitted";
        }

        // Check authorization
        if (!hasAccessToBlock(customer, id)) {
            log.warn("Customer {} denied access to block {}", customer.getCustomerId(), id);
            return "redirect:/customer-login/blocks?error=Access+denied";
        }

        Optional<Block> blockOpt = blockRepository.findById(id);
        if (!blockOpt.isPresent()) {
            return "redirect:/customer-login/blocks?error=Block+not+found";
        }

        model.addAttribute("block", blockOpt.get());
        model.addAttribute("customer", customer);

        return "customer-login/blocks/edit-block";
    }

    /**
     * Display Assignment Centre
     * GET /customer-login/blocks/assignment-centre
     */
    @GetMapping("/assignment-centre")
    public String assignmentCentre(Model model, Authentication auth) {
        log.info("📊 Property owner viewing Assignment Centre");

        Customer customer = getLoggedInCustomer(auth);
        if (customer == null) {
            return "redirect:/customer-login?error=Authentication+required";
        }

        model.addAttribute("customer", customer);
        model.addAttribute("isOwner", customer.getCustomerType() == CustomerType.PROPERTY_OWNER);

        return "customer-login/blocks/assignment-centre";
    }

    // ===== API ENDPOINTS =====

    /**
     * Get all accessible blocks (API endpoint)
     * GET /customer-login/blocks/api/all
     */
    @GetMapping("/api/all")
    @ResponseBody
    public ResponseEntity<?> getAllBlocks(Authentication auth) {
        log.info("📋 Fetching all accessible blocks via API");

        try {
            Customer customer = getLoggedInCustomer(auth);
            if (customer == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
            }

            List<Block> blocks = getAccessibleBlocks(customer);

            // Convert to response DTOs
            List<Map<String, Object>> blockDTOs = blocks.stream()
                .map(this::convertBlockToDTO)
                .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "blocks", blockDTOs,
                "total", blockDTOs.size()
            ));

        } catch (Exception e) {
            log.error("❌ Failed to fetch blocks: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fetch blocks: " + e.getMessage()));
        }
    }

    /**
     * Get active blocks only
     * GET /customer-login/blocks/api/active
     */
    @GetMapping("/api/active")
    @ResponseBody
    public ResponseEntity<?> getActiveBlocks(Authentication auth) {
        log.info("📋 Fetching active accessible blocks via API");

        try {
            Customer customer = getLoggedInCustomer(auth);
            if (customer == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
            }

            List<Block> blocks = getAccessibleBlocks(customer).stream()
                .filter(b -> "Y".equals(b.getIsActive()))
                .collect(Collectors.toList());

            List<Map<String, Object>> blockDTOs = blocks.stream()
                .map(this::convertBlockToDTO)
                .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "blocks", blockDTOs,
                "total", blockDTOs.size()
            ));

        } catch (Exception e) {
            log.error("❌ Failed to fetch active blocks: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fetch active blocks: " + e.getMessage()));
        }
    }

    /**
     * Get block by ID (API endpoint with authorization check)
     * GET /customer-login/blocks/api/{id}
     */
    @GetMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<?> getBlock(@PathVariable Long id, Authentication auth) {
        log.info("📖 Getting block {} via API", id);

        try {
            Customer customer = getLoggedInCustomer(auth);
            if (customer == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
            }

            // Check authorization
            if (!hasAccessToBlock(customer, id)) {
                log.warn("Customer {} denied API access to block {}", customer.getCustomerId(), id);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied"));
            }

            Optional<Block> blockOpt = blockRepository.findById(id);
            if (!blockOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            Block block = blockOpt.get();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "block", convertBlockToDTO(block)
            ));

        } catch (Exception e) {
            log.error("❌ Failed to get block {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get block: " + e.getMessage()));
        }
    }

    /**
     * Create a new block (owners only)
     * POST /customer-login/blocks/api/create
     */
    @PostMapping("/api/create")
    @ResponseBody
    public ResponseEntity<?> createBlock(@RequestBody CreateBlockRequest request, Authentication auth) {
        log.info("🏗️ Property owner creating block '{}'", request.getName());

        try {
            Customer customer = getLoggedInCustomer(auth);
            if (customer == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
            }

            // Only property owners can create blocks
            if (customer.getCustomerType() != CustomerType.PROPERTY_OWNER) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Only property owners can create blocks"));
            }

            // Validate request
            if (request.getPortfolioId() == null || request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Portfolio ID and block name are required"));
            }

            // Verify owner has access to the portfolio
            Portfolio portfolio = portfolioService.findById(request.getPortfolioId());
            if (portfolio == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Portfolio not found"));
            }

            if (!portfolio.getPropertyOwnerId().equals(customer.getCustomerId().longValue())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You can only create blocks in your own portfolios"));
            }

            // Create block
            Block block = blockService.createBlock(
                request.getPortfolioId(),
                request.getName().trim(),
                request.getDescription(),
                request.getBlockType(),
                customer.getCustomerId().longValue()
            );

            log.info("✅ Created block {} successfully for customer {}", block.getId(), customer.getCustomerId());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Block created successfully",
                "block", convertBlockToDTO(block)
            ));

        } catch (IllegalArgumentException e) {
            log.warn("❌ Invalid block creation request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("❌ Failed to create block: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to create block: " + e.getMessage()));
        }
    }

    /**
     * Update block (owners only)
     * PUT /customer-login/blocks/api/{id}
     */
    @PutMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<?> updateBlock(@PathVariable Long id, @RequestBody UpdateBlockRequest request, Authentication auth) {
        log.info("🔄 Property owner updating block {}", id);

        try {
            Customer customer = getLoggedInCustomer(auth);
            if (customer == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
            }

            // Only property owners can update blocks
            if (customer.getCustomerType() != CustomerType.PROPERTY_OWNER) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Only property owners can update blocks"));
            }

            // Check authorization
            if (!hasAccessToBlock(customer, id)) {
                log.warn("Customer {} denied access to update block {}", customer.getCustomerId(), id);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied"));
            }

            // Validate request
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Block name is required"));
            }

            // Update block
            Block block = blockService.updateBlock(
                id,
                request.getName().trim(),
                request.getDescription(),
                request.getBlockType(),
                customer.getCustomerId().longValue()
            );

            log.info("✅ Updated block {} successfully", block.getId());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Block updated successfully",
                "block", convertBlockToDTO(block)
            ));

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("❌ Invalid block update request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("❌ Failed to update block {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to update block: " + e.getMessage()));
        }
    }

    /**
     * Delete block (owners only)
     * DELETE /customer-login/blocks/api/{id}
     */
    @DeleteMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteBlock(@PathVariable Long id,
                                       @RequestParam(defaultValue = "MOVE_TO_PORTFOLIO_ONLY") String reassignmentOption,
                                       Authentication auth) {
        log.info("🗑️ Property owner deleting block {}", id);

        try {
            Customer customer = getLoggedInCustomer(auth);
            if (customer == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
            }

            // Only property owners can delete blocks
            if (customer.getCustomerType() != CustomerType.PROPERTY_OWNER) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Only property owners can delete blocks"));
            }

            // Check authorization
            if (!hasAccessToBlock(customer, id)) {
                log.warn("Customer {} denied access to delete block {}", customer.getCustomerId(), id);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied"));
            }

            // Parse reassignment option
            PortfolioBlockService.PropertyReassignmentOption option;
            try {
                option = PortfolioBlockService.PropertyReassignmentOption.valueOf(reassignmentOption.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid reassignment option: " + reassignmentOption));
            }

            // Delete block
            PortfolioBlockService.BlockDeletionResult result = blockService.deleteBlock(
                id,
                customer.getCustomerId().longValue(),
                option
            );

            if (result.isSuccess()) {
                log.info("✅ Deleted block {} successfully", id);
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", result.getMessage(),
                    "propertiesReassigned", result.getPropertiesReassigned()
                ));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", result.getMessage()));
            }

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("❌ Invalid block deletion request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("❌ Failed to delete block {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to delete block: " + e.getMessage()));
        }
    }

    /**
     * Get all properties in a block
     * GET /customer-login/blocks/api/{id}/properties
     */
    @GetMapping("/api/{id}/properties")
    @ResponseBody
    public ResponseEntity<?> getBlockProperties(@PathVariable Long id, Authentication auth) {
        log.info("📋 Fetching properties for block {}", id);

        try {
            Customer customer = getLoggedInCustomer(auth);
            if (customer == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
            }

            // Check authorization
            if (!hasAccessToBlock(customer, id)) {
                log.warn("Customer {} denied access to block {} properties", customer.getCustomerId(), id);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied"));
            }

            Optional<Block> blockOpt = blockRepository.findById(id);
            if (!blockOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            Block block = blockOpt.get();
            List<Property> properties = propertyBlockAssignmentRepository.findPropertiesByBlockIdOrdered(id);

            // For delegated users, filter to only show properties they have access to
            if (customer.getCustomerType() == CustomerType.DELEGATED_USER) {
                List<Property> userProperties = propertyService.findPropertiesByCustomerAssignments(customer.getCustomerId());
                Set<Long> userPropertyIds = userProperties.stream()
                    .map(Property::getId)
                    .collect(Collectors.toSet());

                properties = properties.stream()
                    .filter(p -> userPropertyIds.contains(p.getId()))
                    .collect(Collectors.toList());
            }

            List<Map<String, Object>> propertyDTOs = properties.stream()
                .map(this::convertPropertyToDTO)
                .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "blockId", id,
                "blockName", block.getName(),
                "properties", propertyDTOs,
                "count", propertyDTOs.size()
            ));

        } catch (Exception e) {
            log.error("❌ Failed to fetch properties for block {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fetch properties: " + e.getMessage()));
        }
    }

    /**
     * Get blocks by portfolio (with authorization check)
     * GET /customer-login/blocks/api/portfolio/{portfolioId}
     */
    @GetMapping("/api/portfolio/{portfolioId}")
    @ResponseBody
    public ResponseEntity<?> getBlocksByPortfolio(@PathVariable Long portfolioId, Authentication auth) {
        log.info("📖 Getting blocks for portfolio {}", portfolioId);

        try {
            Customer customer = getLoggedInCustomer(auth);
            if (customer == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
            }

            // Verify customer has access to portfolio
            Portfolio portfolio = portfolioService.findById(portfolioId);
            if (portfolio == null) {
                return ResponseEntity.notFound().build();
            }

            boolean hasAccess = false;
            if (customer.getCustomerType() == CustomerType.PROPERTY_OWNER) {
                hasAccess = portfolio.getPropertyOwnerId().equals(customer.getCustomerId().longValue());
            } else if (customer.getCustomerType() == CustomerType.DELEGATED_USER) {
                // Check if delegated user has access to any properties in the portfolio
                List<Property> portfolioProps = portfolioService.getPropertiesForPortfolio(portfolioId);
                List<Property> userProps = propertyService.findPropertiesByCustomerAssignments(customer.getCustomerId());
                Set<Long> userPropIds = userProps.stream().map(Property::getId).collect(Collectors.toSet());

                hasAccess = portfolioProps.stream().anyMatch(p -> userPropIds.contains(p.getId()));
            }

            if (!hasAccess) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied"));
            }

            List<Block> blocks = blockService.getBlocksByPortfolio(portfolioId);

            List<Map<String, Object>> blockResponses = blocks.stream()
                .map(this::convertBlockToDTO)
                .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "blocks", blockResponses,
                "total", blockResponses.size()
            ));

        } catch (Exception e) {
            log.error("❌ Failed to get blocks for portfolio {}: {}", portfolioId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get blocks: " + e.getMessage()));
        }
    }

    /**
     * Get assignment overview for Assignment Centre
     * GET /customer-login/blocks/api/assignment-overview
     */
    @GetMapping("/api/assignment-overview")
    @ResponseBody
    public ResponseEntity<?> getAssignmentOverview(Authentication auth) {
        log.info("📊 Fetching assignment overview");

        try {
            Customer customer = getLoggedInCustomer(auth);
            if (customer == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
            }

            List<Block> blocks = getAccessibleBlocks(customer);
            List<Map<String, Object>> blockDTOs = new ArrayList<>();

            for (Block block : blocks) {
                Map<String, Object> blockDTO = new HashMap<>();
                blockDTO.put("id", block.getId());
                blockDTO.put("name", block.getName());
                blockDTO.put("blockType", block.getBlockType());
                blockDTO.put("maxProperties", block.getMaxProperties());

                // Get property count
                long propertyCount = blockService.getPropertyCount(block.getId());
                blockDTO.put("propertyCount", propertyCount);

                // Get available capacity
                Integer availableCapacity = blockService.getAvailableCapacity(block.getId());
                blockDTO.put("availableCapacity", availableCapacity);

                blockDTOs.add(blockDTO);
            }

            // Get unassigned properties that customer has access to
            List<Property> allUserProperties = customer.getCustomerType() == CustomerType.PROPERTY_OWNER
                ? propertyService.findPropertiesByCustomerAssignments(customer.getCustomerId())
                : propertyService.findPropertiesByCustomerAssignments(customer.getCustomerId());

            List<Property> unassignedProperties = new ArrayList<>();
            for (Property property : allUserProperties) {
                Optional<Block> block = propertyBlockAssignmentRepository.findBlockByPropertyId(property.getId());
                if (!block.isPresent()) {
                    unassignedProperties.add(property);
                }
            }

            List<Map<String, Object>> unassignedPropertyDTOs = unassignedProperties.stream()
                .map(this::convertPropertyToDTO)
                .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "blocks", blockDTOs,
                "unassignedProperties", unassignedPropertyDTOs,
                "totalBlocks", blockDTOs.size(),
                "totalUnassigned", unassignedPropertyDTOs.size()
            ));

        } catch (Exception e) {
            log.error("❌ Failed to fetch assignment overview: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fetch assignment overview: " + e.getMessage()));
        }
    }

    // ===== UTILITY METHODS =====

    private Map<String, Object> convertBlockToDTO(Block block) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", block.getId());
        dto.put("name", block.getName());
        dto.put("description", block.getDescription());
        dto.put("blockType", block.getBlockType());
        dto.put("addressLine1", block.getAddressLine1());
        dto.put("addressLine2", block.getAddressLine2());
        dto.put("city", block.getCity());
        dto.put("county", block.getCounty());
        dto.put("postcode", block.getPostcode());
        dto.put("countryCode", block.getCountryCode());
        dto.put("payPropTags", block.getPayPropTags());
        dto.put("payPropTagNames", block.getPayPropTagNames());
        dto.put("syncStatus", block.getSyncStatus());
        dto.put("lastSyncAt", block.getLastSyncAt());
        dto.put("maxProperties", block.getMaxProperties());
        dto.put("colorCode", block.getColorCode());
        dto.put("displayOrder", block.getDisplayOrder());
        dto.put("isActive", block.getIsActive());
        dto.put("createdAt", block.getCreatedAt());
        dto.put("updatedAt", block.getUpdatedAt());

        // Add portfolio info
        if (block.getPortfolio() != null) {
            dto.put("portfolioId", block.getPortfolio().getId());
            dto.put("portfolioName", block.getPortfolio().getName());
        }

        // Add property count
        try {
            long propertyCount = blockService.getPropertyCount(block.getId());
            dto.put("propertyCount", propertyCount);
        } catch (Exception e) {
            dto.put("propertyCount", 0);
        }

        // Add available capacity
        try {
            Integer availableCapacity = blockService.getAvailableCapacity(block.getId());
            dto.put("availableCapacity", availableCapacity);
        } catch (Exception e) {
            dto.put("availableCapacity", null);
        }

        return dto;
    }

    private Map<String, Object> convertPropertyToDTO(Property property) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", property.getId());
        dto.put("propertyName", property.getPropertyName());
        dto.put("propertyType", property.getPropertyType());
        dto.put("addressLine1", property.getAddressLine1());
        dto.put("addressLine2", property.getAddressLine2());
        dto.put("city", property.getCity());
        dto.put("county", property.getCounty());
        dto.put("postcode", property.getPostcode());
        dto.put("countryCode", property.getCountryCode());
        dto.put("status", property.getStatus());
        dto.put("isArchived", property.getIsArchived());

        // Add block info if assigned
        try {
            Optional<Block> block = propertyBlockAssignmentRepository.findBlockByPropertyId(property.getId());
            if (block.isPresent()) {
                dto.put("blockId", block.get().getId());
                dto.put("blockName", block.get().getName());
            }
        } catch (Exception e) {
            // Ignore - block info is optional
        }

        return dto;
    }

    // ===== REQUEST CLASSES =====

    public static class CreateBlockRequest {
        private Long portfolioId;
        private String name;
        private String description;
        private BlockType blockType;

        // Getters and setters
        public Long getPortfolioId() { return portfolioId; }
        public void setPortfolioId(Long portfolioId) { this.portfolioId = portfolioId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public BlockType getBlockType() { return blockType; }
        public void setBlockType(BlockType blockType) { this.blockType = blockType; }
    }

    public static class UpdateBlockRequest {
        private String name;
        private String description;
        private BlockType blockType;

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public BlockType getBlockType() { return blockType; }
        public void setBlockType(BlockType blockType) { this.blockType = blockType; }
    }
}

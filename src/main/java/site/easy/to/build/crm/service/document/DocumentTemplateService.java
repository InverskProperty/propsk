package site.easy.to.build.crm.service.document;

import site.easy.to.build.crm.entity.DocumentTemplate;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.entity.enums.CustomerType;
import site.easy.to.build.crm.entity.enums.DocumentCategory;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing document templates
 */
public interface DocumentTemplateService {

    /**
     * Save or update a document template
     */
    DocumentTemplate save(DocumentTemplate template);

    /**
     * Find template by ID
     */
    Optional<DocumentTemplate> findById(Long id);

    /**
     * Find all active templates
     */
    List<DocumentTemplate> findAllActive();

    /**
     * Find templates by category
     */
    List<DocumentTemplate> findByCategory(DocumentCategory category);

    /**
     * Find templates applicable to a customer type
     */
    List<DocumentTemplate> findApplicableToCustomerType(CustomerType customerType);

    /**
     * Find templates by category and customer type
     */
    List<DocumentTemplate> findByCategoryAndCustomerType(DocumentCategory category, CustomerType customerType);

    /**
     * Search templates by name
     */
    List<DocumentTemplate> searchByName(String searchTerm);

    /**
     * Find templates created by a user
     */
    List<DocumentTemplate> findByCreatedBy(User user);

    /**
     * Deactivate a template (soft delete)
     */
    void deactivate(Long templateId);

    /**
     * Activate a template
     */
    void activate(Long templateId);

    /**
     * Delete a template permanently
     */
    void delete(Long templateId);

    /**
     * Check if Google Docs template ID is already in use
     */
    boolean isGoogleDocsTemplateIdInUse(String googleDocsTemplateId);

    /**
     * Get available merge fields for templates
     */
    List<String> getAvailableMergeFields();
}

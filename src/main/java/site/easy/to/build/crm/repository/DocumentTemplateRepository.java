package site.easy.to.build.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.DocumentTemplate;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.entity.enums.CustomerType;
import site.easy.to.build.crm.entity.enums.DocumentCategory;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, Long> {

    /**
     * Find all active templates
     */
    List<DocumentTemplate> findByActiveTrue();

    /**
     * Find templates by category
     */
    List<DocumentTemplate> findByCategoryAndActiveTrue(DocumentCategory category);

    /**
     * Find templates created by a specific user
     */
    List<DocumentTemplate> findByCreatedByAndActiveTrue(User createdBy);

    /**
     * Find templates applicable to a specific customer type
     */
    @Query("SELECT dt FROM DocumentTemplate dt " +
           "WHERE dt.active = true " +
           "AND (SIZE(dt.applicableToCustomerTypes) = 0 " +
           "OR :customerType MEMBER OF dt.applicableToCustomerTypes)")
    List<DocumentTemplate> findApplicableToCustomerType(@Param("customerType") CustomerType customerType);

    /**
     * Find template by Google Docs template ID
     */
    Optional<DocumentTemplate> findByGoogleDocsTemplateId(String googleDocsTemplateId);

    /**
     * Search templates by name
     */
    @Query("SELECT dt FROM DocumentTemplate dt " +
           "WHERE dt.active = true " +
           "AND LOWER(dt.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<DocumentTemplate> searchByName(@Param("searchTerm") String searchTerm);

    /**
     * Find templates by category and customer type
     */
    @Query("SELECT dt FROM DocumentTemplate dt " +
           "WHERE dt.active = true " +
           "AND dt.category = :category " +
           "AND (SIZE(dt.applicableToCustomerTypes) = 0 " +
           "OR :customerType MEMBER OF dt.applicableToCustomerTypes)")
    List<DocumentTemplate> findByCategoryAndCustomerType(
        @Param("category") DocumentCategory category,
        @Param("customerType") CustomerType customerType
    );
}

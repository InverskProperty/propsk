// PaymentCategoryRepository.java - Repository for payment categories
package site.easy.to.build.crm.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.PaymentCategory;

import java.util.List;

@Repository
public interface PaymentCategoryRepository extends JpaRepository<PaymentCategory, Long> {
    
    // ===== PAYPROP INTEGRATION QUERIES =====
    
    /**
     * Find category by PayProp category ID (for sync deduplication)
     */
    PaymentCategory findByPayPropCategoryId(String payPropCategoryId);
    
    /**
     * Check if category exists by PayProp ID
     */
    boolean existsByPayPropCategoryId(String payPropCategoryId);
    
    /**
     * Find all categories with PayProp IDs (synced categories)
     */
    List<PaymentCategory> findByPayPropCategoryIdIsNotNull();

    // ===== CATEGORY SEARCH QUERIES =====
    
    /**
     * Find category by exact name
     */
    PaymentCategory findByCategoryName(String categoryName);
    
    /**
     * Find categories by name (case insensitive)
     */
    List<PaymentCategory> findByCategoryNameContainingIgnoreCase(String categoryName);
    
    /**
     * Find categories by type
     */
    List<PaymentCategory> findByCategoryType(String categoryType);
    
    /**
     * Find categories by type (case insensitive)
     */
    List<PaymentCategory> findByCategoryTypeContainingIgnoreCase(String categoryType);

    // ===== STATUS-BASED QUERIES =====
    
    /**
     * Find active categories
     */
    List<PaymentCategory> findByIsActive(String isActive);
    
    /**
     * Find active categories ordered by name
     */
    @Query("SELECT pc FROM PaymentCategory pc WHERE pc.isActive = 'Y' ORDER BY pc.categoryName")
    List<PaymentCategory> findActiveCategoriesOrderedByName();
    
    /**
     * Find inactive categories
     */
    @Query("SELECT pc FROM PaymentCategory pc WHERE pc.isActive = 'N'")
    List<PaymentCategory> findInactiveCategories();

    // ===== SPECIALIZED CATEGORY QUERIES =====
    
    /**
     * Find owner payment categories
     */
    @Query("SELECT pc FROM PaymentCategory pc WHERE LOWER(pc.categoryName) LIKE '%owner%' OR LOWER(pc.categoryType) LIKE '%owner%'")
    List<PaymentCategory> findOwnerCategories();
    
    /**
     * Find contractor payment categories
     */
    @Query("SELECT pc FROM PaymentCategory pc WHERE " +
           "LOWER(pc.categoryName) LIKE '%contractor%' OR " +
           "LOWER(pc.categoryName) LIKE '%maintenance%' OR " +
           "LOWER(pc.categoryName) LIKE '%repair%' OR " +
           "LOWER(pc.categoryType) LIKE '%contractor%'")
    List<PaymentCategory> findContractorCategories();
    
    /**
     * Find rent-related categories
     */
    @Query("SELECT pc FROM PaymentCategory pc WHERE LOWER(pc.categoryName) LIKE '%rent%' OR LOWER(pc.categoryType) LIKE '%rent%'")
    List<PaymentCategory> findRentCategories();
    
    /**
     * Find expense categories (utilities, insurance, etc.)
     */
    @Query("SELECT pc FROM PaymentCategory pc WHERE " +
           "LOWER(pc.categoryName) LIKE '%insurance%' OR " +
           "LOWER(pc.categoryName) LIKE '%utility%' OR " +
           "LOWER(pc.categoryName) LIKE '%utilities%' OR " +
           "LOWER(pc.categoryType) LIKE '%expense%'")
    List<PaymentCategory> findExpenseCategories();

    // ===== SEARCH AND FILTERING =====
    
    /**
     * Search categories by name or description
     */
    @Query("SELECT pc FROM PaymentCategory pc WHERE " +
           "LOWER(pc.categoryName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(pc.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<PaymentCategory> searchCategories(@Param("searchTerm") String searchTerm);
    
    /**
     * Advanced category search
     */
    @Query("SELECT pc FROM PaymentCategory pc WHERE " +
           "(:categoryName IS NULL OR LOWER(pc.categoryName) LIKE LOWER(CONCAT('%', :categoryName, '%'))) AND " +
           "(:categoryType IS NULL OR LOWER(pc.categoryType) LIKE LOWER(CONCAT('%', :categoryType, '%'))) AND " +
           "(:isActive IS NULL OR pc.isActive = :isActive)")
    List<PaymentCategory> searchCategoriesAdvanced(@Param("categoryName") String categoryName,
                                                  @Param("categoryType") String categoryType,
                                                  @Param("isActive") String isActive);

    // ===== REPORTING QUERIES =====
    
    /**
     * Count categories by type
     */
    @Query("SELECT pc.categoryType, COUNT(pc) FROM PaymentCategory pc GROUP BY pc.categoryType")
    List<Object[]> countCategoriesByType();
    
    /**
     * Count active vs inactive categories
     */
    @Query("SELECT pc.isActive, COUNT(pc) FROM PaymentCategory pc GROUP BY pc.isActive")
    List<Object[]> countCategoriesByStatus();

    // ===== UTILITY QUERIES =====
    
    /**
     * Check if category name exists (for validation)
     */
    boolean existsByCategoryName(String categoryName);
    
    /**
     * Check if category name exists excluding specific ID (for updates)
     */
    @Query("SELECT CASE WHEN COUNT(pc) > 0 THEN true ELSE false END FROM PaymentCategory pc WHERE pc.categoryName = :categoryName AND pc.id != :excludeId")
    boolean existsByCategoryNameExcludingId(@Param("categoryName") String categoryName, @Param("excludeId") Long excludeId);
    
    /**
     * Find recently created categories
     */
    @Query("SELECT pc FROM PaymentCategory pc ORDER BY pc.createdAt DESC")
    List<PaymentCategory> findRecentlyCreated(Pageable pageable);
    
    /**
     * Find recently updated categories
     */
    @Query("SELECT pc FROM PaymentCategory pc ORDER BY pc.updatedAt DESC")
    List<PaymentCategory> findRecentlyUpdated(Pageable pageable);
}
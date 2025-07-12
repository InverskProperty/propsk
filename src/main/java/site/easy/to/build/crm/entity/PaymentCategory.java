// PaymentCategory.java - Payment categories from PayProp
package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_categories")
public class PaymentCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== PAYPROP INTEGRATION =====
    
    @Column(name = "pay_prop_category_id", unique = true, nullable = false)
    private String payPropCategoryId;

    // ===== CATEGORY DETAILS =====
    
    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;
    
    @Column(name = "category_type", length = 50)
    private String categoryType;
    
    @Column(name = "description", length = 500)
    private String description;
    
    @Column(name = "is_active", length = 1, columnDefinition = "varchar(1) default 'Y'")
    private String isActive = "Y";

    // ===== AUDIT FIELDS =====
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ===== CONSTRUCTORS =====
    
    public PaymentCategory() {}

    public PaymentCategory(String payPropCategoryId, String categoryName) {
        this.payPropCategoryId = payPropCategoryId;
        this.categoryName = categoryName;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // ===== GETTERS AND SETTERS =====
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPayPropCategoryId() { return payPropCategoryId; }
    public void setPayPropCategoryId(String payPropCategoryId) { this.payPropCategoryId = payPropCategoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getCategoryType() { return categoryType; }
    public void setCategoryType(String categoryType) { this.categoryType = categoryType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getIsActive() { return isActive; }
    public void setIsActive(String isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    // ===== UTILITY METHODS =====
    
    /**
     * Check if category is for owner payments
     */
    public boolean isOwnerCategory() {
        return "Owner".equalsIgnoreCase(categoryName) || 
               (categoryType != null && categoryType.toLowerCase().contains("owner"));
    }
    
    /**
     * Check if category is for contractor payments
     */
    public boolean isContractorCategory() {
        return categoryName != null && (
            categoryName.toLowerCase().contains("contractor") ||
            categoryName.toLowerCase().contains("maintenance") ||
            categoryName.toLowerCase().contains("repair")
        );
    }

    @Override
    public String toString() {
        return "PaymentCategory{" +
                "id=" + id +
                ", payPropCategoryId='" + payPropCategoryId + '\'' +
                ", categoryName='" + categoryName + '\'' +
                ", categoryType='" + categoryType + '\'' +
                '}';
    }
}

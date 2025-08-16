package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

// Block.java - Entity for grouping properties into blocks
@Entity
@Table(name = "blocks")
public class Block {

    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "name", nullable = false, length = 255)
    @NotBlank
    @Size(min = 2, max = 255)
    private String name;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "block_type")
    @Enumerated(EnumType.STRING)
    private BlockType blockType;
    
    // Address Information
    @Column(name = "address_line_1", length = 50)
    private String addressLine1;
    
    @Column(name = "address_line_2", length = 50)
    private String addressLine2;
    
    @Column(name = "city", length = 50)
    private String city;
    
    @Column(name = "county", length = 50)
    private String county;
    
    @Column(name = "postcode", length = 10)
    private String postcode;
    
    @Column(name = "country_code", length = 2)
    private String countryCode = "UK";
    
    // PayProp Integration
    @Column(name = "payprop_tags", columnDefinition = "TEXT")
    private String payPropTags;
    
    @Column(name = "payprop_tag_names", columnDefinition = "TEXT")
    private String payPropTagNames;
    
    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;
    
    @Column(name = "sync_status")
    @Enumerated(EnumType.STRING)
    private SyncStatus syncStatus = SyncStatus.pending;
    
    // Management Information
    @Column(name = "max_properties")
    private Integer maxProperties; // For buildings with limited units
    
    @Column(name = "building_year")
    private Integer buildingYear;
    
    @Column(name = "facilities", columnDefinition = "TEXT")
    private String facilities; // JSON or comma-separated
    
    // Ownership and Access
    @Column(name = "property_owner_id")
    private Integer propertyOwnerId;
    
    @Column(name = "is_active")
    private String isActive = "Y";
    
    // Display Settings
    @Column(name = "color_code", length = 7)
    private String colorCode;
    
    @Column(name = "display_order")
    private Integer displayOrder = 0;
    
    // Audit fields
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "created_by")
    private Long createdBy;
    
    @Column(name = "updated_by")
    private Long updatedBy;
    
    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id")
    private Portfolio portfolio;
    
    @OneToMany(mappedBy = "block", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Property> properties;
    
    // Constructors
    public Block() {}
    
    public Block(String name, Portfolio portfolio, Long createdBy) {
        this.name = name;
        this.portfolio = portfolio;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public BlockType getBlockType() { return blockType; }
    public void setBlockType(BlockType blockType) { this.blockType = blockType; }
    
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
    
    public String getPayPropTags() { return payPropTags; }
    public void setPayPropTags(String payPropTags) { this.payPropTags = payPropTags; }
    
    public SyncStatus getSyncStatus() { return syncStatus; }
    public void setSyncStatus(SyncStatus syncStatus) { this.syncStatus = syncStatus; }
    
    public Integer getMaxProperties() { return maxProperties; }
    public void setMaxProperties(Integer maxProperties) { this.maxProperties = maxProperties; }
    
    public Integer getBuildingYear() { return buildingYear; }
    public void setBuildingYear(Integer buildingYear) { this.buildingYear = buildingYear; }
    
    public String getFacilities() { return facilities; }
    public void setFacilities(String facilities) { this.facilities = facilities; }
    
    public Integer getPropertyOwnerId() { return propertyOwnerId; }
    public void setPropertyOwnerId(Integer propertyOwnerId) { this.propertyOwnerId = propertyOwnerId; }
    
    public String getIsActive() { return isActive; }
    public void setIsActive(String isActive) { this.isActive = isActive; }
    
    public String getColorCode() { return colorCode; }
    public void setColorCode(String colorCode) { this.colorCode = colorCode; }
    
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    
    public Portfolio getPortfolio() { return portfolio; }
    public void setPortfolio(Portfolio portfolio) { this.portfolio = portfolio; }
    
    public List<Property> getProperties() { return properties; }
    public void setProperties(List<Property> properties) { this.properties = properties; }
    
    // Business Logic Methods
    public boolean isActive() {
        return "Y".equalsIgnoreCase(isActive);
    }
    
    public boolean isFull() {
        return maxProperties != null && properties != null && properties.size() >= maxProperties;
    }
    
    public int getCurrentPropertyCount() {
        return properties != null ? properties.size() : 0;
    }
    
    public String getFullAddress() {
        StringBuilder address = new StringBuilder();
        if (addressLine1 != null && !addressLine1.trim().isEmpty()) {
            address.append(addressLine1);
        }
        if (addressLine2 != null && !addressLine2.trim().isEmpty()) {
            address.append(", ").append(addressLine2);
        }
        if (city != null && !city.trim().isEmpty()) {
            address.append(", ").append(city);
        }
        if (postcode != null && !postcode.trim().isEmpty()) {
            address.append(" ").append(postcode);
        }
        return address.toString();
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (displayOrder == null) {
            displayOrder = 0;
        }
        if (syncStatus == null) {
            syncStatus = SyncStatus.pending;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // PayProp Integration Getters/Setters
    public String getPayPropTagNames() { return payPropTagNames; }
    public void setPayPropTagNames(String payPropTagNames) { this.payPropTagNames = payPropTagNames; }
    
    public LocalDateTime getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(LocalDateTime lastSyncAt) { this.lastSyncAt = lastSyncAt; }
}
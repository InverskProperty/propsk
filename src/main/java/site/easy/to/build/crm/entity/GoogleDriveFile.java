package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "google_drive_file")
public class GoogleDriveFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @Column(name = "drive_file_id")
    private String driveFileId;

    @Column(name = "drive_folder_id")
    private String googleDriveFolderId;

    // EXISTING RELATIONSHIPS (keep these)
    @ManyToOne
    @JoinColumn(name = "lead_id")
    private Lead lead;

    @ManyToOne
    @JoinColumn(name = "contract_id")
    private Contract contract;

    // CUSTOMER FILE MANAGEMENT FIELDS
    
    @Column(name = "customer_id")
    private Integer customerId;
    
    @Column(name = "property_id")
    private Long propertyId;
    
    @Column(name = "file_name")
    private String fileName;
    
    @Column(name = "file_category")
    private String fileCategory;
    
    @Column(name = "file_type")
    private String fileType;
    
    @Column(name = "file_description")
    private String fileDescription;
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // PAYPROP INTEGRATION FIELDS
    
    @Column(name = "is_payprop_file")
    private Boolean isPayPropFile = false;
    
    @Column(name = "payprop_sync_date")
    private LocalDateTime payPropSyncDate;
    
    @Column(name = "payprop_external_id")
    private String payPropExternalId;
    
    @Column(name = "entity_type")
    private String entityType; // 'customer', 'property', 'tenant', 'beneficiary', 'contractor', etc.
    
    @Column(name = "entity_payprop_id")
    private String entityPayPropId;
    
    @Column(name = "source_entity_type")
    private String sourceEntityType; // 'tenant', 'property', 'beneficiary', etc.
    
    @Column(name = "relationship_context")
    private String relationshipContext; // 'property_owner', 'tenant_assignment', etc.

    // OPTIONAL: JPA relationships (if you want them)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", insertable = false, updatable = false)
    private Customer customer;

    // EXISTING CONSTRUCTORS (keep these)
    public GoogleDriveFile() {}

    public GoogleDriveFile(String driveFileId, String googleDriveFolderId, Lead lead) {
        this.driveFileId = driveFileId;
        this.googleDriveFolderId = googleDriveFolderId;
        this.lead = lead;
        this.createdAt = LocalDateTime.now();
        this.isActive = true;
        this.isPayPropFile = false;
    }

    public GoogleDriveFile(String driveFileId, String googleDriveFolderId, Contract contract) {
        this.driveFileId = driveFileId;
        this.googleDriveFolderId = googleDriveFolderId;
        this.contract = contract;
        this.createdAt = LocalDateTime.now();
        this.isActive = true;
        this.isPayPropFile = false;
    }

    // NEW CONSTRUCTOR FOR CUSTOMER FILES
    public GoogleDriveFile(String driveFileId, String googleDriveFolderId, Integer customerId, String fileName) {
        this.driveFileId = driveFileId;
        this.googleDriveFolderId = googleDriveFolderId;
        this.customerId = customerId;
        this.fileName = fileName;
        this.createdAt = LocalDateTime.now();
        this.isActive = true;
        this.isPayPropFile = false;
    }

    // NEW CONSTRUCTOR FOR PAYPROP FILES
    public GoogleDriveFile(String driveFileId, String googleDriveFolderId, Integer customerId, 
                          String fileName, String payPropExternalId, String entityType) {
        this.driveFileId = driveFileId;
        this.googleDriveFolderId = googleDriveFolderId;
        this.customerId = customerId;
        this.fileName = fileName;
        this.payPropExternalId = payPropExternalId;
        this.entityType = entityType;
        this.createdAt = LocalDateTime.now();
        this.isActive = true;
        this.isPayPropFile = true;
    }

    // EXISTING GETTERS/SETTERS (keep these)
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getDriveFileId() { return driveFileId; }
    public void setDriveFileId(String driveFileId) { this.driveFileId = driveFileId; }

    public String getGoogleDriveFolderId() { return googleDriveFolderId; }
    public void setGoogleDriveFolderId(String googleDriveFolderId) { this.googleDriveFolderId = googleDriveFolderId; }

    public Lead getLead() { return lead; }
    public void setLead(Lead lead) { this.lead = lead; }

    public Contract getContract() { return contract; }
    public void setContract(Contract contract) { this.contract = contract; }

    // CUSTOMER FILE MANAGEMENT GETTERS/SETTERS
    
    public Integer getCustomerId() { return customerId; }
    public void setCustomerId(Integer customerId) { this.customerId = customerId; }
    
    public Long getPropertyId() { return propertyId; }
    public void setPropertyId(Long propertyId) { this.propertyId = propertyId; }
    
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    
    public String getFileCategory() { return fileCategory; }
    public void setFileCategory(String fileCategory) { this.fileCategory = fileCategory; }
    
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    
    public String getFileDescription() { return fileDescription; }
    public void setFileDescription(String fileDescription) { this.fileDescription = fileDescription; }
    
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    // PAYPROP INTEGRATION GETTERS/SETTERS
    
    public Boolean getIsPayPropFile() { return isPayPropFile; }
    public void setIsPayPropFile(Boolean isPayPropFile) { this.isPayPropFile = isPayPropFile; }
    
    public LocalDateTime getPayPropSyncDate() { return payPropSyncDate; }
    public void setPayPropSyncDate(LocalDateTime payPropSyncDate) { this.payPropSyncDate = payPropSyncDate; }
    
    public String getPayPropExternalId() { return payPropExternalId; }
    public void setPayPropExternalId(String payPropExternalId) { this.payPropExternalId = payPropExternalId; }
    
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    
    public String getEntityPayPropId() { return entityPayPropId; }
    public void setEntityPayPropId(String entityPayPropId) { this.entityPayPropId = entityPayPropId; }
    
    public String getSourceEntityType() { return sourceEntityType; }
    public void setSourceEntityType(String sourceEntityType) { this.sourceEntityType = sourceEntityType; }
    
    public String getRelationshipContext() { return relationshipContext; }
    public void setRelationshipContext(String relationshipContext) { this.relationshipContext = relationshipContext; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    // LIFECYCLE CALLBACKS
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (isPayPropFile == null) {
            isPayPropFile = false;
        }
        if (isActive == null) {
            isActive = true;
        }
    }

    @PreUpdate  
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // UTILITY METHODS
    public boolean isCustomerFile() {
        return customerId != null;
    }

    public boolean isLeadFile() {
        return lead != null;
    }

    public boolean isContractFile() {
        return contract != null;
    }

    public boolean isFromPayProp() {
        return Boolean.TRUE.equals(isPayPropFile);
    }

    public boolean isActiveFile() {
        return Boolean.TRUE.equals(isActive);
    }

    public void markAsDeleted() {
        this.isActive = false;
        this.updatedAt = LocalDateTime.now();
    }

    public void markPayPropSynced() {
        this.payPropSyncDate = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // toString for debugging
    @Override
    public String toString() {
        return "GoogleDriveFile{" +
                "id=" + id +
                ", driveFileId='" + driveFileId + '\'' +
                ", googleDriveFolderId='" + googleDriveFolderId + '\'' +
                ", fileName='" + fileName + '\'' +
                ", customerId=" + customerId +
                ", propertyId=" + propertyId +
                ", fileCategory='" + fileCategory + '\'' +
                ", isActive=" + isActive +
                ", isPayPropFile=" + isPayPropFile +
                '}';
    }
}
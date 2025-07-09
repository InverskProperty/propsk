package site.easy.to.build.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.GoogleDriveFile;

import java.util.List;

@Repository
public interface GoogleDriveFileRepository extends JpaRepository<GoogleDriveFile, Integer> {
    
    // ===== EXISTING METHODS =====
    public List<GoogleDriveFile> findByLeadLeadId(int leadId);
    public List<GoogleDriveFile> findByContractContractId(int contractId);

    // ===== NEW METHODS FOR PAYPROP FILE SYNC =====
    
    /**
     * Find files by customer ID where files are active
     */
    List<GoogleDriveFile> findByCustomerIdAndIsActiveTrue(int customerId);
    
    /**
     * Find files by customer ID and file category where files are active
     */
    List<GoogleDriveFile> findByCustomerIdAndFileCategoryAndIsActiveTrue(int customerId, String category);
    
    /**
     * Find files by property ID where files are active
     */
    List<GoogleDriveFile> findByPropertyIdAndIsActiveTrue(Long propertyId);
    
    /**
     * Find files by property ID and file category where files are active
     */
    List<GoogleDriveFile> findByPropertyIdAndFileCategoryAndIsActiveTrue(Long propertyId, String category);
    
    /**
     * Find PayProp files that haven't been synced yet
     */
    List<GoogleDriveFile> findByIsPayPropFileTrueAndPayPropSyncDateIsNull();
    
    /**
     * Find files by customer ID and PayProp external ID for duplicate checking
     */
    List<GoogleDriveFile> findByCustomerIdAndPayPropExternalId(int customerId, String payPropExternalId);
    
    /**
     * Find files by PayProp external ID
     */
    List<GoogleDriveFile> findByPayPropExternalId(String payPropExternalId);
    
    /**
     * Find files by customer ID and file type where files are active
     */
    List<GoogleDriveFile> findByCustomerIdAndFileTypeAndIsActiveTrue(int customerId, String fileType);
    
    /**
     * Find files by customer ID and entity type where files are active
     */
    List<GoogleDriveFile> findByCustomerIdAndEntityTypeAndIsActiveTrue(int customerId, String entityType);
    
    /**
     * Find files by customer ID and PayProp sync status
     */
    List<GoogleDriveFile> findByCustomerIdAndIsPayPropFileTrue(int customerId);
    
    /**
     * Find files by property ID and PayProp sync status
     */
    List<GoogleDriveFile> findByPropertyIdAndIsPayPropFileTrue(Long propertyId);
    
    /**
     * Find files by customer ID, file category, and PayProp sync status
     */
    List<GoogleDriveFile> findByCustomerIdAndFileCategoryAndIsPayPropFileTrue(int customerId, String category);
    
    /**
     * Find files by entity type and PayProp sync status
     */
    List<GoogleDriveFile> findByEntityTypeAndIsPayPropFileTrue(String entityType);
    
    /**
     * Find files by file type and PayProp sync status
     */
    List<GoogleDriveFile> findByFileTypeAndIsPayPropFileTrue(String fileType);
    
    /**
     * Find files by Google Drive folder ID
     */
    List<GoogleDriveFile> findByGoogleDriveFolderId(String folderId);
    
    /**
     * Find files by customer ID and Google Drive folder ID
     */
    List<GoogleDriveFile> findByCustomerIdAndGoogleDriveFolderId(int customerId, String folderId);
    
    /**
     * Find files by property ID and Google Drive folder ID
     */
    List<GoogleDriveFile> findByPropertyIdAndGoogleDriveFolderId(Long propertyId, String folderId);
}
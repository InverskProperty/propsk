package site.easy.to.build.crm.service.drive;

import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.GoogleDriveFile;

import java.util.List;

public interface GoogleDriveFileService {
    public List<GoogleDriveFile> getAllDriveFileByLeadId(int leadId);

    public List<GoogleDriveFile> getAllDriveFileByContactId(int contractId);

    public void save(GoogleDriveFile googleDriveFile);

    public void delete(int id);

    // ===== NEW METHODS FOR PAYPROP FILE SYNC =====
    
    /**
     * Get files by customer and category
     */
    public List<GoogleDriveFile> getFilesByCustomerAndCategory(int customerId, String category);
    
    /**
     * Get files by property and category
     */
    public List<GoogleDriveFile> getFilesByPropertyAndCategory(Long propertyId, String category);
    
    /**
     * Get all files for a customer
     */
    public List<GoogleDriveFile> getFilesByCustomer(int customerId);
    
    /**
     * Delete file by ID
     */
    public void deleteById(int id);
    
    /**
     * Find files by customer ID and PayProp external ID for duplicate checking
     */
    public List<GoogleDriveFile> findByCustomerIdAndPayPropExternalId(int customerId, String payPropExternalId);
    
    /**
     * Get all PayProp files that haven't been synced yet
     */
    public List<GoogleDriveFile> findPayPropFilesNeedingSync();
    
    /**
     * Get files by customer and file type
     */
    public List<GoogleDriveFile> getFilesByCustomerAndFileType(int customerId, String fileType);
    
    /**
     * Get files by property ID
     */
    public List<GoogleDriveFile> getFilesByProperty(Long propertyId);
    
    /**
     * Find files by PayProp external ID
     */
    public List<GoogleDriveFile> findByPayPropExternalId(String payPropExternalId);
    
    /**
     * Get files by customer and entity type (tenant/beneficiary/contractor)
     */
    public List<GoogleDriveFile> getFilesByCustomerAndEntityType(int customerId, String entityType);
}
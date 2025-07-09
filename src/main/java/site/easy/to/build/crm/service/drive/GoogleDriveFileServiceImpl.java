package site.easy.to.build.crm.service.drive;

import jakarta.validation.constraints.Null;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.GoogleDriveFile;
import site.easy.to.build.crm.repository.GoogleDriveFileRepository;

import java.util.ArrayList;
import java.util.List;

@Service
public class GoogleDriveFileServiceImpl implements GoogleDriveFileService {

    private final GoogleDriveFileRepository googleDriveFileRepository;

    public GoogleDriveFileServiceImpl(GoogleDriveFileRepository googleDriveFileRepository) {
        this.googleDriveFileRepository = googleDriveFileRepository;
    }

    @Override
    public List<GoogleDriveFile> getAllDriveFileByLeadId(int leadId) {
        return googleDriveFileRepository.findByLeadLeadId(leadId);
    }

    @Override
    public List<GoogleDriveFile> getAllDriveFileByContactId(int contractId) {
        return googleDriveFileRepository.findByContractContractId(contractId);
    }

    @Override
    public void save(GoogleDriveFile googleDriveFile) {
        googleDriveFileRepository.save(googleDriveFile);
    }

    @Override
    public void delete(int id) {
        if (googleDriveFileRepository.findById(id).isPresent()) {
            googleDriveFileRepository.deleteById(id);
        }
    }

    // ===== NEW METHODS FOR PAYPROP FILE SYNC =====

    @Override
    public List<GoogleDriveFile> getFilesByCustomerAndCategory(int customerId, String category) {
        if (category == null || category.isEmpty()) {
            return googleDriveFileRepository.findByCustomerIdAndIsActiveTrue(customerId);
        }
        return googleDriveFileRepository.findByCustomerIdAndFileCategoryAndIsActiveTrue(customerId, category);
    }

    @Override
    public List<GoogleDriveFile> getFilesByPropertyAndCategory(Long propertyId, String category) {
        if (category == null || category.isEmpty()) {
            return googleDriveFileRepository.findByPropertyIdAndIsActiveTrue(propertyId);
        }
        return googleDriveFileRepository.findByPropertyIdAndFileCategoryAndIsActiveTrue(propertyId, category);
    }

    @Override
    public List<GoogleDriveFile> getFilesByCustomer(int customerId) {
        return googleDriveFileRepository.findByCustomerIdAndIsActiveTrue(customerId);
    }

    @Override
    public void deleteById(int id) {
        googleDriveFileRepository.deleteById(id);
    }

    @Override
    public List<GoogleDriveFile> findByCustomerIdAndPayPropExternalId(int customerId, String payPropExternalId) {
        return googleDriveFileRepository.findByCustomerIdAndPayPropExternalId(customerId, payPropExternalId);
    }

    @Override
    public List<GoogleDriveFile> findPayPropFilesNeedingSync() {
        return googleDriveFileRepository.findByIsPayPropFileTrueAndPayPropSyncDateIsNull();
    }

    @Override
    public List<GoogleDriveFile> getFilesByCustomerAndFileType(int customerId, String fileType) {
        return googleDriveFileRepository.findByCustomerIdAndFileTypeAndIsActiveTrue(customerId, fileType);
    }

    @Override
    public List<GoogleDriveFile> getFilesByProperty(Long propertyId) {
        return googleDriveFileRepository.findByPropertyIdAndIsActiveTrue(propertyId);
    }

    @Override
    public List<GoogleDriveFile> findByPayPropExternalId(String payPropExternalId) {
        return googleDriveFileRepository.findByPayPropExternalId(payPropExternalId);
    }

    @Override
    public List<GoogleDriveFile> getFilesByCustomerAndEntityType(int customerId, String entityType) {
        return googleDriveFileRepository.findByCustomerIdAndEntityTypeAndIsActiveTrue(customerId, entityType);
    }
}
package site.easy.to.build.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.ExpenseDocument;
import site.easy.to.build.crm.entity.ExpenseDocument.DocumentStatus;
import site.easy.to.build.crm.entity.ExpenseDocument.DocumentType;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseDocumentRepository extends JpaRepository<ExpenseDocument, Long> {

    // ===== BY TRANSACTION ID =====

    /**
     * Find documents for a unified transaction
     */
    List<ExpenseDocument> findByUnifiedTransactionIdAndStatusNot(Long transactionId, DocumentStatus status);

    /**
     * Find documents for a unified transaction (all statuses)
     */
    List<ExpenseDocument> findByUnifiedTransactionId(Long transactionId);

    /**
     * Find documents for a financial transaction
     */
    List<ExpenseDocument> findByFinancialTransactionIdAndStatusNot(Long transactionId, DocumentStatus status);

    /**
     * Find documents for a historical transaction
     */
    List<ExpenseDocument> findByHistoricalTransactionIdAndStatusNot(Long transactionId, DocumentStatus status);

    // ===== BY PROPERTY =====

    /**
     * Find all documents for a property
     */
    List<ExpenseDocument> findByPropertyIdAndStatusNotOrderByCreatedAtDesc(Long propertyId, DocumentStatus status);

    /**
     * Find documents for a property by type
     */
    List<ExpenseDocument> findByPropertyIdAndDocumentTypeAndStatusNotOrderByCreatedAtDesc(
            Long propertyId, DocumentType documentType, DocumentStatus status);

    // ===== BY CUSTOMER =====

    /**
     * Find all documents for a customer (property owner)
     */
    List<ExpenseDocument> findByCustomerIdAndStatusNotOrderByCreatedAtDesc(Integer customerId, DocumentStatus status);

    /**
     * Find documents for a customer by type
     */
    List<ExpenseDocument> findByCustomerIdAndDocumentTypeAndStatusNotOrderByCreatedAtDesc(
            Integer customerId, DocumentType documentType, DocumentStatus status);

    // ===== BY DOCUMENT TYPE =====

    /**
     * Find all invoices for a property
     */
    default List<ExpenseDocument> findInvoicesByProperty(Long propertyId) {
        return findByPropertyIdAndDocumentTypeAndStatusNotOrderByCreatedAtDesc(
                propertyId, DocumentType.INVOICE, DocumentStatus.ARCHIVED);
    }

    /**
     * Find all receipts for a property
     */
    default List<ExpenseDocument> findReceiptsByProperty(Long propertyId) {
        return findByPropertyIdAndDocumentTypeAndStatusNotOrderByCreatedAtDesc(
                propertyId, DocumentType.RECEIPT, DocumentStatus.ARCHIVED);
    }

    // ===== BY GOOGLE DRIVE FILE =====

    /**
     * Find document by Google Drive file ID
     */
    Optional<ExpenseDocument> findByGoogleDriveFileId(Integer googleDriveFileId);

    /**
     * Check if a Google Drive file is already linked to an expense
     */
    boolean existsByGoogleDriveFileId(Integer googleDriveFileId);

    // ===== COMPLEX QUERIES =====

    /**
     * Find all expense documents for properties owned by a customer
     */
    @Query("SELECT ed FROM ExpenseDocument ed WHERE ed.propertyId IN " +
           "(SELECT cpa.property.id FROM CustomerPropertyAssignment cpa WHERE cpa.customer.customerId = :customerId) " +
           "AND ed.status != :excludeStatus ORDER BY ed.createdAt DESC")
    List<ExpenseDocument> findByOwnerCustomerId(@Param("customerId") Integer customerId,
                                                 @Param("excludeStatus") DocumentStatus excludeStatus);

    /**
     * Find expense documents for a property within a date range
     */
    @Query("SELECT ed FROM ExpenseDocument ed " +
           "JOIN UnifiedTransaction ut ON ut.id = ed.unifiedTransactionId " +
           "WHERE ed.propertyId = :propertyId " +
           "AND ut.transactionDate BETWEEN :startDate AND :endDate " +
           "AND ed.status != :excludeStatus " +
           "ORDER BY ut.transactionDate DESC")
    List<ExpenseDocument> findByPropertyAndDateRange(
            @Param("propertyId") Long propertyId,
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate,
            @Param("excludeStatus") DocumentStatus excludeStatus);

    /**
     * Count documents by property and type
     */
    @Query("SELECT COUNT(ed) FROM ExpenseDocument ed " +
           "WHERE ed.propertyId = :propertyId AND ed.documentType = :documentType AND ed.status != 'ARCHIVED'")
    long countByPropertyAndType(@Param("propertyId") Long propertyId, @Param("documentType") DocumentType documentType);

    /**
     * Find documents that have receipts attached (for admin review)
     */
    @Query("SELECT ed FROM ExpenseDocument ed WHERE ed.googleDriveFileId IS NOT NULL AND ed.status = 'AVAILABLE' ORDER BY ed.createdAt DESC")
    List<ExpenseDocument> findAllWithReceipts();

    /**
     * Find documents pending generation
     */
    List<ExpenseDocument> findByStatus(DocumentStatus status);
}

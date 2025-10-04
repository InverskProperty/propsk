package site.easy.to.build.crm.service.paymentsource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.TransactionImportStagingRepository;
import site.easy.to.build.crm.repository.HistoricalTransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing transaction import staging and batch accumulation
 */
@Service
public class TransactionStagingService {

    private static final Logger log = LoggerFactory.getLogger(TransactionStagingService.class);

    @Autowired
    private TransactionImportStagingRepository stagingRepository;

    @Autowired
    private HistoricalTransactionRepository historicalTransactionRepository;

    /**
     * Save a transaction to staging for review
     */
    @Transactional
    public TransactionImportStaging saveStagingTransaction(TransactionImportStaging staging) {
        log.debug("Saving staging transaction for batch: {}", staging.getBatchId());
        return stagingRepository.save(staging);
    }

    /**
     * Get all staging transactions for a batch
     */
    public List<TransactionImportStaging> getStagingTransactionsForBatch(String batchId) {
        return stagingRepository.findByBatchIdOrderByLineNumberAsc(batchId);
    }

    /**
     * Get staging transactions by status
     */
    public List<TransactionImportStaging> getStagingTransactionsByStatus(String batchId, String status) {
        return stagingRepository.findByBatchIdAndStatusOrderByLineNumberAsc(batchId, status);
    }

    /**
     * Get duplicate staging transactions in batch
     */
    public List<TransactionImportStaging> getDuplicateStagingTransactions(String batchId) {
        return stagingRepository.findByBatchIdAndIsDuplicateTrueOrderByLineNumberAsc(batchId);
    }

    /**
     * Count transactions by status in batch
     */
    public long countByStatus(String batchId, String status) {
        return stagingRepository.countByBatchIdAndStatus(batchId, status);
    }

    /**
     * Get batch summary statistics
     */
    public BatchSummary getBatchSummary(String batchId) {
        List<Object[]> statusCounts = stagingRepository.getBatchStatusSummary(batchId);

        BatchSummary summary = new BatchSummary();
        summary.setBatchId(batchId);
        summary.setTotalRecords(stagingRepository.countByBatchId(batchId));

        Map<String, Long> statusMap = new HashMap<>();
        for (Object[] row : statusCounts) {
            String status = (String) row[0];
            Long count = (Long) row[1];
            statusMap.put(status, count);
        }

        summary.setPendingReview(statusMap.getOrDefault("PENDING_REVIEW", 0L));
        summary.setApproved(statusMap.getOrDefault("APPROVED", 0L));
        summary.setRejected(statusMap.getOrDefault("REJECTED", 0L));
        summary.setAmbiguousProperty(statusMap.getOrDefault("AMBIGUOUS_PROPERTY", 0L));
        summary.setAmbiguousCustomer(statusMap.getOrDefault("AMBIGUOUS_CUSTOMER", 0L));
        summary.setDuplicates(stagingRepository.countByBatchIdAndStatus(batchId, "DUPLICATE"));

        return summary;
    }

    /**
     * Check for duplicates within payment source
     * Returns staging transactions that are potential duplicates
     */
    public List<TransactionImportStaging> findDuplicatesInSource(
            Long paymentSourceId,
            LocalDate transactionDate,
            BigDecimal amount,
            Long propertyId) {

        return stagingRepository.findPotentialDuplicatesInSource(
                paymentSourceId, transactionDate, amount, propertyId);
    }

    /**
     * Check if a staging transaction duplicates an existing historical transaction
     * within the same payment source
     */
    public List<HistoricalTransaction> findExistingDuplicates(
            Long paymentSourceId,
            LocalDate transactionDate,
            BigDecimal amount,
            Long propertyId) {

        return historicalTransactionRepository.findPotentialDuplicatesInSource(
                paymentSourceId, transactionDate, amount, propertyId);
    }

    /**
     * Mark staging transaction as duplicate
     */
    @Transactional
    public void markAsDuplicate(Long stagingId, Long duplicateOfTransactionId) {
        TransactionImportStaging staging = stagingRepository.findById(stagingId)
                .orElseThrow(() -> new IllegalArgumentException("Staging transaction not found: " + stagingId));

        staging.setIsDuplicate(true);
        staging.setDuplicateOfTransactionId(duplicateOfTransactionId);
        staging.setStatus("DUPLICATE");

        stagingRepository.save(staging);
        log.info("Marked staging transaction {} as duplicate of {}", stagingId, duplicateOfTransactionId);
    }

    /**
     * Update staging transaction status
     */
    @Transactional
    public void updateStatus(Long stagingId, String status) {
        TransactionImportStaging staging = stagingRepository.findById(stagingId)
                .orElseThrow(() -> new IllegalArgumentException("Staging transaction not found: " + stagingId));

        staging.setStatus(status);
        stagingRepository.save(staging);
        log.debug("Updated staging transaction {} status to {}", stagingId, status);
    }

    /**
     * Approve staging transaction for import
     */
    @Transactional
    public void approveStagingTransaction(Long stagingId) {
        updateStatus(stagingId, "APPROVED");
    }

    /**
     * Reject staging transaction
     */
    @Transactional
    public void rejectStagingTransaction(Long stagingId) {
        updateStatus(stagingId, "REJECTED");
    }

    /**
     * Delete entire batch from staging
     */
    @Transactional
    public void deleteBatch(String batchId) {
        log.info("Deleting staging batch: {}", batchId);
        stagingRepository.deleteByBatchId(batchId);
    }

    /**
     * Delete single staging transaction
     */
    @Transactional
    public void deleteStagingTransaction(Long stagingId) {
        log.debug("Deleting staging transaction: {}", stagingId);
        stagingRepository.deleteById(stagingId);
    }

    /**
     * Batch approve multiple staging transactions
     */
    @Transactional
    public void batchApprove(List<Long> stagingIds) {
        log.info("Batch approving {} staging transactions", stagingIds.size());

        for (Long stagingId : stagingIds) {
            try {
                approveStagingTransaction(stagingId);
            } catch (Exception e) {
                log.error("Failed to approve staging transaction {}: {}", stagingId, e.getMessage());
            }
        }
    }

    /**
     * Batch reject multiple staging transactions
     */
    @Transactional
    public void batchReject(List<Long> stagingIds) {
        log.info("Batch rejecting {} staging transactions", stagingIds.size());

        for (Long stagingId : stagingIds) {
            try {
                rejectStagingTransaction(stagingId);
            } catch (Exception e) {
                log.error("Failed to reject staging transaction {}: {}", stagingId, e.getMessage());
            }
        }
    }

    /**
     * Get all approved staging transactions ready for import
     */
    public List<TransactionImportStaging> getApprovedTransactions(String batchId) {
        return stagingRepository.findByBatchIdAndStatusOrderByLineNumberAsc(batchId, "APPROVED");
    }

    /**
     * DTO for batch summary statistics
     */
    public static class BatchSummary {
        private String batchId;
        private long totalRecords;
        private long pendingReview;
        private long approved;
        private long rejected;
        private long ambiguousProperty;
        private long ambiguousCustomer;
        private long duplicates;

        // Getters and setters
        public String getBatchId() {
            return batchId;
        }

        public void setBatchId(String batchId) {
            this.batchId = batchId;
        }

        public long getTotalRecords() {
            return totalRecords;
        }

        public void setTotalRecords(long totalRecords) {
            this.totalRecords = totalRecords;
        }

        public long getPendingReview() {
            return pendingReview;
        }

        public void setPendingReview(long pendingReview) {
            this.pendingReview = pendingReview;
        }

        public long getApproved() {
            return approved;
        }

        public void setApproved(long approved) {
            this.approved = approved;
        }

        public long getRejected() {
            return rejected;
        }

        public void setRejected(long rejected) {
            this.rejected = rejected;
        }

        public long getAmbiguousProperty() {
            return ambiguousProperty;
        }

        public void setAmbiguousProperty(long ambiguousProperty) {
            this.ambiguousProperty = ambiguousProperty;
        }

        public long getAmbiguousCustomer() {
            return ambiguousCustomer;
        }

        public void setAmbiguousCustomer(long ambiguousCustomer) {
            this.ambiguousCustomer = ambiguousCustomer;
        }

        public long getDuplicates() {
            return duplicates;
        }

        public void setDuplicates(long duplicates) {
            this.duplicates = duplicates;
        }
    }
}

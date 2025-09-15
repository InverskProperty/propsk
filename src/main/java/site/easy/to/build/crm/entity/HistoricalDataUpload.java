package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Entity to track historical data uploads and their processing status
 * Maintains audit trail for uploaded data and enables rollback if needed
 */
@Entity
@Table(name = "historical_data_uploads")
public class HistoricalDataUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "upload_id")
    private Long uploadId;

    @Column(name = "filename", nullable = false)
    private String filename;

    @Column(name = "file_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private UploadFileType fileType;

    @Column(name = "upload_date", nullable = false)
    private LocalDateTime uploadDate;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    @Column(name = "processing_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private ProcessingStatus processingStatus = ProcessingStatus.PENDING;

    @Column(name = "records_processed")
    private Integer recordsProcessed = 0;

    @Column(name = "records_created")
    private Integer recordsCreated = 0;

    @Column(name = "records_updated")
    private Integer recordsUpdated = 0;

    @Column(name = "records_failed")
    private Integer recordsFailed = 0;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "processing_completed_at")
    private LocalDateTime processingCompletedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "processing_summary", columnDefinition = "JSON")
    private String processingSummary; // JSON with detailed results

    @Column(name = "file_path")
    private String filePath; // Path to uploaded file for potential reprocessing

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_hash")
    private String fileHash; // To detect duplicate uploads

    // Constructors
    public HistoricalDataUpload() {}

    public HistoricalDataUpload(String filename, UploadFileType fileType, Long uploadedBy) {
        this.filename = filename;
        this.fileType = fileType;
        this.uploadedBy = uploadedBy;
        this.uploadDate = LocalDateTime.now();
    }

    // File types that can be uploaded
    public enum UploadFileType {
        PROPERTIES("Properties CSV"),
        CUSTOMERS("Customers CSV"),
        PROPERTY_OWNERS("Property Owners CSV"),
        TENANTS("Tenants CSV"),
        TRANSACTIONS("Financial Transactions CSV"),
        INVOICES("Invoices CSV"),
        PAYMENTS("Payments CSV"),
        RELATIONSHIPS("Property-Customer Relationships CSV");

        private final String description;

        UploadFileType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // Processing status
    public enum ProcessingStatus {
        PENDING("Upload pending processing"),
        PROCESSING("Currently processing"),
        COMPLETED("Processing completed successfully"),
        COMPLETED_WITH_ERRORS("Processing completed with some errors"),
        FAILED("Processing failed"),
        CANCELLED("Processing cancelled");

        private final String description;

        ProcessingStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // Helper methods
    public void startProcessing() {
        this.processingStatus = ProcessingStatus.PROCESSING;
        this.processingStartedAt = LocalDateTime.now();
    }

    public void completeProcessing() {
        this.processingStatus = (recordsFailed > 0) ?
            ProcessingStatus.COMPLETED_WITH_ERRORS :
            ProcessingStatus.COMPLETED;
        this.processingCompletedAt = LocalDateTime.now();
    }

    public void failProcessing(String errorMessage) {
        this.processingStatus = ProcessingStatus.FAILED;
        this.processingCompletedAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
    }

    public boolean isCompleted() {
        return processingStatus == ProcessingStatus.COMPLETED ||
               processingStatus == ProcessingStatus.COMPLETED_WITH_ERRORS;
    }

    public boolean hasFailed() {
        return processingStatus == ProcessingStatus.FAILED;
    }

    // Getters and Setters
    public Long getUploadId() { return uploadId; }
    public void setUploadId(Long uploadId) { this.uploadId = uploadId; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public UploadFileType getFileType() { return fileType; }
    public void setFileType(UploadFileType fileType) { this.fileType = fileType; }

    public LocalDateTime getUploadDate() { return uploadDate; }
    public void setUploadDate(LocalDateTime uploadDate) { this.uploadDate = uploadDate; }

    public Long getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(Long uploadedBy) { this.uploadedBy = uploadedBy; }

    public ProcessingStatus getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(ProcessingStatus processingStatus) { this.processingStatus = processingStatus; }

    public Integer getRecordsProcessed() { return recordsProcessed; }
    public void setRecordsProcessed(Integer recordsProcessed) { this.recordsProcessed = recordsProcessed; }

    public Integer getRecordsCreated() { return recordsCreated; }
    public void setRecordsCreated(Integer recordsCreated) { this.recordsCreated = recordsCreated; }

    public Integer getRecordsUpdated() { return recordsUpdated; }
    public void setRecordsUpdated(Integer recordsUpdated) { this.recordsUpdated = recordsUpdated; }

    public Integer getRecordsFailed() { return recordsFailed; }
    public void setRecordsFailed(Integer recordsFailed) { this.recordsFailed = recordsFailed; }

    public LocalDateTime getProcessingStartedAt() { return processingStartedAt; }
    public void setProcessingStartedAt(LocalDateTime processingStartedAt) { this.processingStartedAt = processingStartedAt; }

    public LocalDateTime getProcessingCompletedAt() { return processingCompletedAt; }
    public void setProcessingCompletedAt(LocalDateTime processingCompletedAt) { this.processingCompletedAt = processingCompletedAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getProcessingSummary() { return processingSummary; }
    public void setProcessingSummary(String processingSummary) { this.processingSummary = processingSummary; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }
}
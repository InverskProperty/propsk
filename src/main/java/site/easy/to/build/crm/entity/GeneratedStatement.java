package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ✨ PHASE 3: Entity for tracking generated financial statements
 *
 * Stores metadata about statements generated for property owners,
 * allowing them to access historical statements from the UI.
 */
@Entity
@Table(name = "generated_statements")
public class GeneratedStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "format", length = 20)
    private String format; // EXCEL, PDF, GOOGLE_SHEETS

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "generated_by", length = 100)
    private String generatedBy; // User who generated it

    @Column(name = "billing_period_start_day")
    private Integer billingPeriodStartDay; // Custom period used

    @Column(name = "total_rent")
    private java.math.BigDecimal totalRent;

    @Column(name = "total_expenses")
    private java.math.BigDecimal totalExpenses;

    @Column(name = "total_commission")
    private java.math.BigDecimal totalCommission;

    @Column(name = "net_to_owner")
    private java.math.BigDecimal netToOwner;

    @Column(name = "download_count")
    private Integer downloadCount = 0;

    @Column(name = "last_downloaded_at")
    private LocalDateTime lastDownloadedAt;

    // Constructors
    public GeneratedStatement() {
        this.generatedAt = LocalDateTime.now();
        this.downloadCount = 0;
    }

    public GeneratedStatement(Long customerId, LocalDate periodStart, LocalDate periodEnd, String fileName, String filePath) {
        this();
        this.customerId = customerId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.fileName = fileName;
        this.filePath = filePath;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getGeneratedBy() {
        return generatedBy;
    }

    public void setGeneratedBy(String generatedBy) {
        this.generatedBy = generatedBy;
    }

    public Integer getBillingPeriodStartDay() {
        return billingPeriodStartDay;
    }

    public void setBillingPeriodStartDay(Integer billingPeriodStartDay) {
        this.billingPeriodStartDay = billingPeriodStartDay;
    }

    public java.math.BigDecimal getTotalRent() {
        return totalRent;
    }

    public void setTotalRent(java.math.BigDecimal totalRent) {
        this.totalRent = totalRent;
    }

    public java.math.BigDecimal getTotalExpenses() {
        return totalExpenses;
    }

    public void setTotalExpenses(java.math.BigDecimal totalExpenses) {
        this.totalExpenses = totalExpenses;
    }

    public java.math.BigDecimal getTotalCommission() {
        return totalCommission;
    }

    public void setTotalCommission(java.math.BigDecimal totalCommission) {
        this.totalCommission = totalCommission;
    }

    public java.math.BigDecimal getNetToOwner() {
        return netToOwner;
    }

    public void setNetToOwner(java.math.BigDecimal netToOwner) {
        this.netToOwner = netToOwner;
    }

    public Integer getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(Integer downloadCount) {
        this.downloadCount = downloadCount;
    }

    public LocalDateTime getLastDownloadedAt() {
        return lastDownloadedAt;
    }

    public void setLastDownloadedAt(LocalDateTime lastDownloadedAt) {
        this.lastDownloadedAt = lastDownloadedAt;
    }

    // Helper methods
    public void incrementDownloadCount() {
        if (this.downloadCount == null) {
            this.downloadCount = 0;
        }
        this.downloadCount++;
        this.lastDownloadedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "GeneratedStatement{" +
                "id=" + id +
                ", customerId=" + customerId +
                ", periodStart=" + periodStart +
                ", periodEnd=" + periodEnd +
                ", fileName='" + fileName + '\'' +
                ", generatedAt=" + generatedAt +
                '}';
    }
}

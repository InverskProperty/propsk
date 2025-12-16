package site.easy.to.build.crm.dto.statement;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for summarizing allocation status per property/lease for a period.
 * Used to populate reconciliation columns on monthly statement rows.
 */
public class LeaseAllocationSummaryDTO {
    private Long invoiceId;
    private Long propertyId;
    private String leaseReference;
    private BigDecimal totalAllocatedAmount;  // Sum of OWNER allocations
    private String paymentStatus;             // PAID, PENDING, PARTIAL, NONE
    private String primaryBatchId;            // Main batch (or "MULTIPLE" if split)
    private LocalDate latestPaymentDate;
    private int allocationCount;

    public LeaseAllocationSummaryDTO() {
        this.totalAllocatedAmount = BigDecimal.ZERO;
        this.allocationCount = 0;
    }

    // Getters and Setters
    public Long getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(Long invoiceId) {
        this.invoiceId = invoiceId;
    }

    public Long getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(Long propertyId) {
        this.propertyId = propertyId;
    }

    public String getLeaseReference() {
        return leaseReference;
    }

    public void setLeaseReference(String leaseReference) {
        this.leaseReference = leaseReference;
    }

    public BigDecimal getTotalAllocatedAmount() {
        return totalAllocatedAmount;
    }

    public void setTotalAllocatedAmount(BigDecimal totalAllocatedAmount) {
        this.totalAllocatedAmount = totalAllocatedAmount;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getPrimaryBatchId() {
        return primaryBatchId;
    }

    public void setPrimaryBatchId(String primaryBatchId) {
        this.primaryBatchId = primaryBatchId;
    }

    public LocalDate getLatestPaymentDate() {
        return latestPaymentDate;
    }

    public void setLatestPaymentDate(LocalDate latestPaymentDate) {
        this.latestPaymentDate = latestPaymentDate;
    }

    public int getAllocationCount() {
        return allocationCount;
    }

    public void setAllocationCount(int allocationCount) {
        this.allocationCount = allocationCount;
    }
}

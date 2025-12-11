package site.easy.to.build.crm.dto.statement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO for payment batch summary in the RELATED PAYMENTS section.
 * Groups allocations by batch with calculated totals by type.
 */
public class PaymentBatchSummaryDTO {
    private String batchId;
    private LocalDate paymentDate;
    private String batchStatus;
    private BigDecimal totalOwnerAllocations;      // Income (OWNER type)
    private BigDecimal totalExpenseAllocations;    // Expenses (EXPENSE type)
    private BigDecimal totalCommissionAllocations; // Commission (COMMISSION type)
    private BigDecimal totalDisbursementAllocations; // Disbursements (DISBURSEMENT type)
    private BigDecimal netPayment;                 // Owner - Expense - Commission
    private int propertyCount;
    private List<RelatedPaymentDTO> allocations;   // Detail rows for full audit

    public PaymentBatchSummaryDTO() {
        this.totalOwnerAllocations = BigDecimal.ZERO;
        this.totalExpenseAllocations = BigDecimal.ZERO;
        this.totalCommissionAllocations = BigDecimal.ZERO;
        this.totalDisbursementAllocations = BigDecimal.ZERO;
        this.netPayment = BigDecimal.ZERO;
        this.allocations = new ArrayList<>();
    }

    // Getters and Setters
    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
    }

    public String getBatchStatus() {
        return batchStatus;
    }

    public void setBatchStatus(String batchStatus) {
        this.batchStatus = batchStatus;
    }

    public BigDecimal getTotalOwnerAllocations() {
        return totalOwnerAllocations;
    }

    public void setTotalOwnerAllocations(BigDecimal totalOwnerAllocations) {
        this.totalOwnerAllocations = totalOwnerAllocations;
    }

    public BigDecimal getTotalExpenseAllocations() {
        return totalExpenseAllocations;
    }

    public void setTotalExpenseAllocations(BigDecimal totalExpenseAllocations) {
        this.totalExpenseAllocations = totalExpenseAllocations;
    }

    public BigDecimal getTotalCommissionAllocations() {
        return totalCommissionAllocations;
    }

    public void setTotalCommissionAllocations(BigDecimal totalCommissionAllocations) {
        this.totalCommissionAllocations = totalCommissionAllocations;
    }

    public BigDecimal getTotalDisbursementAllocations() {
        return totalDisbursementAllocations;
    }

    public void setTotalDisbursementAllocations(BigDecimal totalDisbursementAllocations) {
        this.totalDisbursementAllocations = totalDisbursementAllocations;
    }

    public BigDecimal getNetPayment() {
        return netPayment;
    }

    public void setNetPayment(BigDecimal netPayment) {
        this.netPayment = netPayment;
    }

    public int getPropertyCount() {
        return propertyCount;
    }

    public void setPropertyCount(int propertyCount) {
        this.propertyCount = propertyCount;
    }

    public List<RelatedPaymentDTO> getAllocations() {
        return allocations;
    }

    public void setAllocations(List<RelatedPaymentDTO> allocations) {
        this.allocations = allocations;
    }

    /**
     * Calculate net payment from allocation totals.
     * Net = Owner (income) - Expense - Commission
     */
    public void calculateNetPayment() {
        this.netPayment = totalOwnerAllocations
            .subtract(totalExpenseAllocations)
            .subtract(totalCommissionAllocations);
    }
}

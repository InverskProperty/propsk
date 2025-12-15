package site.easy.to.build.crm.dto.statement;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for unallocated payments (payments made but not fully covered by allocated income)
 */
public class UnallocatedPaymentDTO {
    private String batchId;
    private LocalDate paymentDate;
    private BigDecimal totalPayment;
    private BigDecimal totalAllocated;
    private BigDecimal unallocatedAmount;
    private String status;
    private String paymentReference;

    // Getters and Setters
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }

    public BigDecimal getTotalPayment() { return totalPayment; }
    public void setTotalPayment(BigDecimal totalPayment) { this.totalPayment = totalPayment; }

    public BigDecimal getTotalAllocated() { return totalAllocated; }
    public void setTotalAllocated(BigDecimal totalAllocated) { this.totalAllocated = totalAllocated; }

    public BigDecimal getUnallocatedAmount() { return unallocatedAmount; }
    public void setUnallocatedAmount(BigDecimal unallocatedAmount) { this.unallocatedAmount = unallocatedAmount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }
}

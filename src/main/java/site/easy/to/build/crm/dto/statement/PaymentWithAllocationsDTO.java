package site.easy.to.build.crm.dto.statement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO for a payment batch with its allocation details
 * Shows what income/expenses each payment covered
 */
public class PaymentWithAllocationsDTO {
    private String batchId;
    private LocalDate paymentDate;
    private BigDecimal totalPayment;
    private BigDecimal totalAllocated;
    private BigDecimal unallocatedAmount;
    private String status;
    private String paymentReference;
    private List<AllocationLineDTO> allocations = new ArrayList<>();

    /**
     * Represents a single allocation line within a payment
     */
    public static class AllocationLineDTO {
        private Long transactionId;
        private LocalDate transactionDate;
        private String propertyName;
        private String category;
        private String description;
        private BigDecimal allocatedAmount;
        private boolean isPartial;  // True if transaction is split across payments
        private boolean isFromPriorPeriod;  // True if transaction date < period start

        // Getters and Setters
        public Long getTransactionId() { return transactionId; }
        public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

        public LocalDate getTransactionDate() { return transactionDate; }
        public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }

        public String getPropertyName() { return propertyName; }
        public void setPropertyName(String propertyName) { this.propertyName = propertyName; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public BigDecimal getAllocatedAmount() { return allocatedAmount; }
        public void setAllocatedAmount(BigDecimal allocatedAmount) { this.allocatedAmount = allocatedAmount; }

        public boolean isPartial() { return isPartial; }
        public void setPartial(boolean partial) { isPartial = partial; }

        public boolean isFromPriorPeriod() { return isFromPriorPeriod; }
        public void setFromPriorPeriod(boolean fromPriorPeriod) { isFromPriorPeriod = fromPriorPeriod; }
    }

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

    public List<AllocationLineDTO> getAllocations() { return allocations; }
    public void setAllocations(List<AllocationLineDTO> allocations) { this.allocations = allocations; }

    public void addAllocation(AllocationLineDTO allocation) {
        this.allocations.add(allocation);
    }
}

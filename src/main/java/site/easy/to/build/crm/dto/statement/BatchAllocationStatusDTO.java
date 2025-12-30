package site.easy.to.build.crm.dto.statement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO for batch allocation status tracking.
 * Shows how much of a batch's allocations came from transactions in the current period
 * versus prior periods, and what remains unallocated.
 */
public class BatchAllocationStatusDTO {

    // Batch identifiers
    private String batchId;
    private LocalDate paymentDate;
    private String status; // PAID, PENDING, etc.

    // Amounts
    private BigDecimal grossIncome = BigDecimal.ZERO;
    private BigDecimal commission = BigDecimal.ZERO;
    private BigDecimal expenses = BigDecimal.ZERO;
    private BigDecimal netToOwner = BigDecimal.ZERO;

    // Allocation status - amounts by period
    private BigDecimal allocatedFromPriorPeriods = BigDecimal.ZERO; // B/F - transactions before period start
    private BigDecimal allocatedFromThisPeriod = BigDecimal.ZERO;   // Transactions within period
    private BigDecimal allocatedFromFuturePeriods = BigDecimal.ZERO; // Transactions after period end (beyond this statement)
    private BigDecimal totalAllocated = BigDecimal.ZERO;

    // Counts
    private int priorPeriodAllocations = 0;
    private int thisPeriodAllocations = 0;
    private int futurePeriodAllocations = 0;

    // Individual allocations for detailed view
    private List<AllocationDetailDTO> allocations = new ArrayList<>();

    // Constructors
    public BatchAllocationStatusDTO() {
    }

    public BatchAllocationStatusDTO(String batchId, LocalDate paymentDate, String status) {
        this.batchId = batchId;
        this.paymentDate = paymentDate;
        this.status = status;
    }

    // Add an allocation and classify it (legacy method for backward compatibility)
    public void addAllocation(AllocationDetailDTO allocation, LocalDate periodStart) {
        addAllocation(allocation, periodStart, null);
    }

    // Add an allocation and classify it with three-way period classification
    public void addAllocation(AllocationDetailDTO allocation, LocalDate periodStart, LocalDate periodEnd) {
        allocations.add(allocation);

        BigDecimal amount = allocation.getAmount() != null ? allocation.getAmount().abs() : BigDecimal.ZERO;

        // Classify by type
        // For OWNER allocations with gross/commission breakdown, use those values for totals
        if ("OWNER".equals(allocation.getAllocationType())) {
            // Use grossAmount if available (new structure), otherwise use amount (legacy)
            BigDecimal gross = allocation.getGrossAmount() != null ? allocation.getGrossAmount().abs() : amount;
            grossIncome = grossIncome.add(gross);

            // Add commission from the allocation if available
            if (allocation.getCommissionAmount() != null) {
                commission = commission.add(allocation.getCommissionAmount().abs());
            }
        } else if ("COMMISSION".equals(allocation.getAllocationType())) {
            // Skip standalone COMMISSION allocations - commission is already included in OWNER.commissionAmount
            // This prevents double-counting when PayProp has both an OWNER allocation (with commission breakdown)
            // and a separate COMMISSION allocation for the same rent payment.
            // Note: Agency reimbursements (Contractor, Council) are typed as EXPENSE, not COMMISSION.
        } else if ("EXPENSE".equals(allocation.getAllocationType()) ||
                   "DISBURSEMENT".equals(allocation.getAllocationType())) {
            expenses = expenses.add(amount);
        }

        // Classify by period (based on transaction date, not payment date)
        // Three categories: PRIOR (before periodStart), THIS_PERIOD (within), FUTURE (after periodEnd)
        LocalDate txnDate = allocation.getTransactionDate();
        if (txnDate != null && txnDate.isBefore(periodStart)) {
            allocatedFromPriorPeriods = allocatedFromPriorPeriods.add(amount);
            priorPeriodAllocations++;
            allocation.setPeriodClassification("B/F");
        } else if (periodEnd != null && txnDate != null && txnDate.isAfter(periodEnd)) {
            allocatedFromFuturePeriods = allocatedFromFuturePeriods.add(amount);
            futurePeriodAllocations++;
            allocation.setPeriodClassification("FUTURE");
        } else {
            allocatedFromThisPeriod = allocatedFromThisPeriod.add(amount);
            thisPeriodAllocations++;
            allocation.setPeriodClassification("THIS_PERIOD");
        }

        totalAllocated = totalAllocated.add(amount);
    }

    // Calculate net after all allocations added
    public void calculateNet() {
        netToOwner = grossIncome.subtract(commission).subtract(expenses);
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getGrossIncome() {
        return grossIncome;
    }

    public void setGrossIncome(BigDecimal grossIncome) {
        this.grossIncome = grossIncome;
    }

    public BigDecimal getCommission() {
        return commission;
    }

    public void setCommission(BigDecimal commission) {
        this.commission = commission;
    }

    public BigDecimal getExpenses() {
        return expenses;
    }

    public void setExpenses(BigDecimal expenses) {
        this.expenses = expenses;
    }

    public BigDecimal getNetToOwner() {
        return netToOwner;
    }

    public void setNetToOwner(BigDecimal netToOwner) {
        this.netToOwner = netToOwner;
    }

    public BigDecimal getAllocatedFromPriorPeriods() {
        return allocatedFromPriorPeriods;
    }

    public void setAllocatedFromPriorPeriods(BigDecimal allocatedFromPriorPeriods) {
        this.allocatedFromPriorPeriods = allocatedFromPriorPeriods;
    }

    public BigDecimal getAllocatedFromThisPeriod() {
        return allocatedFromThisPeriod;
    }

    public void setAllocatedFromThisPeriod(BigDecimal allocatedFromThisPeriod) {
        this.allocatedFromThisPeriod = allocatedFromThisPeriod;
    }

    public BigDecimal getTotalAllocated() {
        return totalAllocated;
    }

    public void setTotalAllocated(BigDecimal totalAllocated) {
        this.totalAllocated = totalAllocated;
    }

    public int getPriorPeriodAllocations() {
        return priorPeriodAllocations;
    }

    public void setPriorPeriodAllocations(int priorPeriodAllocations) {
        this.priorPeriodAllocations = priorPeriodAllocations;
    }

    public int getThisPeriodAllocations() {
        return thisPeriodAllocations;
    }

    public void setThisPeriodAllocations(int thisPeriodAllocations) {
        this.thisPeriodAllocations = thisPeriodAllocations;
    }

    public BigDecimal getAllocatedFromFuturePeriods() {
        return allocatedFromFuturePeriods;
    }

    public void setAllocatedFromFuturePeriods(BigDecimal allocatedFromFuturePeriods) {
        this.allocatedFromFuturePeriods = allocatedFromFuturePeriods;
    }

    public int getFuturePeriodAllocations() {
        return futurePeriodAllocations;
    }

    public void setFuturePeriodAllocations(int futurePeriodAllocations) {
        this.futurePeriodAllocations = futurePeriodAllocations;
    }

    public List<AllocationDetailDTO> getAllocations() {
        return allocations;
    }

    public void setAllocations(List<AllocationDetailDTO> allocations) {
        this.allocations = allocations;
    }

    /**
     * Inner class for individual allocation details
     */
    public static class AllocationDetailDTO {
        private Long allocationId;
        private LocalDate transactionDate;
        private String propertyName;
        private String category;
        private String allocationType; // OWNER, EXPENSE, COMMISSION
        private BigDecimal amount;
        private String periodClassification; // B/F, THIS_PERIOD, FUTURE
        private boolean isPartial;

        // Gross/Commission/Net breakdown for OWNER allocations
        private BigDecimal grossAmount;
        private BigDecimal commissionRate;
        private BigDecimal commissionAmount;
        private BigDecimal netToOwnerAmount;

        public AllocationDetailDTO() {
        }

        public AllocationDetailDTO(Long allocationId, LocalDate transactionDate, String propertyName,
                                    String category, String allocationType, BigDecimal amount, boolean isPartial) {
            this.allocationId = allocationId;
            this.transactionDate = transactionDate;
            this.propertyName = propertyName;
            this.category = category;
            this.allocationType = allocationType;
            this.amount = amount;
            this.isPartial = isPartial;
        }

        // Getters and Setters
        public Long getAllocationId() {
            return allocationId;
        }

        public void setAllocationId(Long allocationId) {
            this.allocationId = allocationId;
        }

        public LocalDate getTransactionDate() {
            return transactionDate;
        }

        public void setTransactionDate(LocalDate transactionDate) {
            this.transactionDate = transactionDate;
        }

        public String getPropertyName() {
            return propertyName;
        }

        public void setPropertyName(String propertyName) {
            this.propertyName = propertyName;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getAllocationType() {
            return allocationType;
        }

        public void setAllocationType(String allocationType) {
            this.allocationType = allocationType;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public String getPeriodClassification() {
            return periodClassification;
        }

        public void setPeriodClassification(String periodClassification) {
            this.periodClassification = periodClassification;
        }

        public boolean isPartial() {
            return isPartial;
        }

        public void setPartial(boolean partial) {
            isPartial = partial;
        }

        public BigDecimal getGrossAmount() {
            return grossAmount;
        }

        public void setGrossAmount(BigDecimal grossAmount) {
            this.grossAmount = grossAmount;
        }

        public BigDecimal getCommissionRate() {
            return commissionRate;
        }

        public void setCommissionRate(BigDecimal commissionRate) {
            this.commissionRate = commissionRate;
        }

        public BigDecimal getCommissionAmount() {
            return commissionAmount;
        }

        public void setCommissionAmount(BigDecimal commissionAmount) {
            this.commissionAmount = commissionAmount;
        }

        public BigDecimal getNetToOwnerAmount() {
            return netToOwnerAmount;
        }

        public void setNetToOwnerAmount(BigDecimal netToOwnerAmount) {
            this.netToOwnerAmount = netToOwnerAmount;
        }
    }
}

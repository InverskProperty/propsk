package site.easy.to.build.crm.dto.statement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO for batch-grouped payment data.
 * One instance represents one batch for one lease, containing all payments in that batch.
 *
 * Used for the new batch-based RENT_RECEIVED, EXPENSES, and OWNER_PAYMENTS sheets.
 */
public class BatchPaymentGroupDTO {

    // Identifiers
    private Long leaseId;
    private String leaseReference;
    private String propertyName;

    // Batch info
    private String batchId;
    private LocalDate ownerPaymentDate;  // The date the owner was paid (paidDate from allocation)

    // Rent payments in this batch (up to 4)
    private List<PaymentDetailDTO> rentPayments = new ArrayList<>();

    // Expenses in this batch (up to 4)
    private List<PaymentDetailDTO> expenses = new ArrayList<>();

    // Commission rate for this lease (for calculating commission per rent payment)
    private BigDecimal commissionRate;

    // Totals (calculated)
    private BigDecimal totalRent = BigDecimal.ZERO;
    private BigDecimal totalCommission = BigDecimal.ZERO;
    private BigDecimal totalExpenses = BigDecimal.ZERO;
    private BigDecimal netToOwner = BigDecimal.ZERO;

    // Constructors
    public BatchPaymentGroupDTO() {
    }

    public BatchPaymentGroupDTO(Long leaseId, String leaseReference, String propertyName,
                                 String batchId, LocalDate ownerPaymentDate) {
        this.leaseId = leaseId;
        this.leaseReference = leaseReference;
        this.propertyName = propertyName;
        this.batchId = batchId;
        this.ownerPaymentDate = ownerPaymentDate;
    }

    // Add a rent payment to this batch
    public void addRentPayment(PaymentDetailDTO payment) {
        if (rentPayments.size() < 4) {
            rentPayments.add(payment);
            if (payment.getAmount() != null) {
                totalRent = totalRent.add(payment.getAmount());
            }
        }
    }

    // Add an expense to this batch
    public void addExpense(PaymentDetailDTO expense) {
        if (expenses.size() < 4) {
            expenses.add(expense);
            if (expense.getAmount() != null) {
                totalExpenses = totalExpenses.add(expense.getAmount());
            }
        }
    }

    // Calculate totals based on commission rate
    public void calculateTotals() {
        if (commissionRate != null && totalRent.compareTo(BigDecimal.ZERO) > 0) {
            totalCommission = totalRent.multiply(commissionRate);
        }
        netToOwner = totalRent.subtract(totalCommission).subtract(totalExpenses);
    }

    // Getters and Setters
    public Long getLeaseId() {
        return leaseId;
    }

    public void setLeaseId(Long leaseId) {
        this.leaseId = leaseId;
    }

    public String getLeaseReference() {
        return leaseReference;
    }

    public void setLeaseReference(String leaseReference) {
        this.leaseReference = leaseReference;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public LocalDate getOwnerPaymentDate() {
        return ownerPaymentDate;
    }

    public void setOwnerPaymentDate(LocalDate ownerPaymentDate) {
        this.ownerPaymentDate = ownerPaymentDate;
    }

    public List<PaymentDetailDTO> getRentPayments() {
        return rentPayments;
    }

    public void setRentPayments(List<PaymentDetailDTO> rentPayments) {
        this.rentPayments = rentPayments;
    }

    public List<PaymentDetailDTO> getExpenses() {
        return expenses;
    }

    public void setExpenses(List<PaymentDetailDTO> expenses) {
        this.expenses = expenses;
    }

    public BigDecimal getCommissionRate() {
        return commissionRate;
    }

    public void setCommissionRate(BigDecimal commissionRate) {
        this.commissionRate = commissionRate;
    }

    public BigDecimal getTotalRent() {
        return totalRent;
    }

    public void setTotalRent(BigDecimal totalRent) {
        this.totalRent = totalRent;
    }

    public BigDecimal getTotalCommission() {
        return totalCommission;
    }

    public void setTotalCommission(BigDecimal totalCommission) {
        this.totalCommission = totalCommission;
    }

    public BigDecimal getTotalExpenses() {
        return totalExpenses;
    }

    public void setTotalExpenses(BigDecimal totalExpenses) {
        this.totalExpenses = totalExpenses;
    }

    public BigDecimal getNetToOwner() {
        return netToOwner;
    }

    public void setNetToOwner(BigDecimal netToOwner) {
        this.netToOwner = netToOwner;
    }

    // Helper to get rent payment at index (1-4), returns null if not present
    public PaymentDetailDTO getRentPayment(int index) {
        if (index >= 0 && index < rentPayments.size()) {
            return rentPayments.get(index);
        }
        return null;
    }

    // Helper to get expense at index (1-4), returns null if not present
    public PaymentDetailDTO getExpense(int index) {
        if (index >= 0 && index < expenses.size()) {
            return expenses.get(index);
        }
        return null;
    }
}

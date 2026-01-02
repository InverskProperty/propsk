package site.easy.to.build.crm.dto.paymentadvice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a per-property breakdown in the Payment Advice.
 * Shows receipts (tenant payments with gross/commission/net) and deductions (expenses) for one property.
 */
public class PropertyBreakdownDTO {

    private Long propertyId;
    private String propertyName;
    private String propertyAddress;

    private List<ReceiptLineDTO> receipts = new ArrayList<>();
    private List<DeductionLineDTO> deductions = new ArrayList<>();

    // Receipt totals
    private BigDecimal totalGrossReceipts = BigDecimal.ZERO;
    private BigDecimal totalCommission = BigDecimal.ZERO;
    private BigDecimal totalNetReceipts = BigDecimal.ZERO;

    // Deduction totals (expenses/disbursements)
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    // Final balance = net receipts - deductions
    private BigDecimal balance = BigDecimal.ZERO;

    // Legacy field for backwards compatibility
    private BigDecimal totalReceipts = BigDecimal.ZERO;

    public PropertyBreakdownDTO() {
    }

    public PropertyBreakdownDTO(Long propertyId, String propertyName) {
        this.propertyId = propertyId;
        this.propertyName = propertyName;
    }

    /**
     * Add a receipt and update totals.
     */
    public void addReceipt(ReceiptLineDTO receipt) {
        this.receipts.add(receipt);
        if (receipt.getGrossAmount() != null) {
            this.totalGrossReceipts = this.totalGrossReceipts.add(receipt.getGrossAmount());
        }
        if (receipt.getCommissionAmount() != null) {
            this.totalCommission = this.totalCommission.add(receipt.getCommissionAmount());
        }
        if (receipt.getNetAmount() != null) {
            this.totalNetReceipts = this.totalNetReceipts.add(receipt.getNetAmount());
        }
        // Update legacy field
        this.totalReceipts = this.totalGrossReceipts;
        calculateBalance();
    }

    /**
     * Add a deduction and update totals.
     */
    public void addDeduction(DeductionLineDTO deduction) {
        this.deductions.add(deduction);
        if (deduction.getGrossAmount() != null) {
            this.totalDeductions = this.totalDeductions.add(deduction.getGrossAmount());
        }
        calculateBalance();
    }

    /**
     * Calculate balance as net receipts minus deductions.
     * Balance = Gross Receipts - Commission - Expenses = Net Receipts - Expenses
     */
    public void calculateBalance() {
        this.balance = this.totalNetReceipts.subtract(this.totalDeductions);
    }

    // Getters and Setters

    public Long getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(Long propertyId) {
        this.propertyId = propertyId;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyAddress() {
        return propertyAddress;
    }

    public void setPropertyAddress(String propertyAddress) {
        this.propertyAddress = propertyAddress;
    }

    public List<ReceiptLineDTO> getReceipts() {
        return receipts;
    }

    public void setReceipts(List<ReceiptLineDTO> receipts) {
        this.receipts = receipts;
        recalculateTotals();
    }

    public List<DeductionLineDTO> getDeductions() {
        return deductions;
    }

    public void setDeductions(List<DeductionLineDTO> deductions) {
        this.deductions = deductions;
        recalculateTotals();
    }

    public BigDecimal getTotalGrossReceipts() {
        return totalGrossReceipts;
    }

    public void setTotalGrossReceipts(BigDecimal totalGrossReceipts) {
        this.totalGrossReceipts = totalGrossReceipts;
    }

    public BigDecimal getTotalCommission() {
        return totalCommission;
    }

    public void setTotalCommission(BigDecimal totalCommission) {
        this.totalCommission = totalCommission;
    }

    public BigDecimal getTotalNetReceipts() {
        return totalNetReceipts;
    }

    public void setTotalNetReceipts(BigDecimal totalNetReceipts) {
        this.totalNetReceipts = totalNetReceipts;
    }

    /**
     * @deprecated Use getTotalGrossReceipts() instead
     */
    @Deprecated
    public BigDecimal getTotalReceipts() {
        return totalGrossReceipts;
    }

    /**
     * @deprecated Use setTotalGrossReceipts() instead
     */
    @Deprecated
    public void setTotalReceipts(BigDecimal totalReceipts) {
        this.totalGrossReceipts = totalReceipts;
        this.totalReceipts = totalReceipts;
    }

    public BigDecimal getTotalDeductions() {
        return totalDeductions;
    }

    public void setTotalDeductions(BigDecimal totalDeductions) {
        this.totalDeductions = totalDeductions;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    /**
     * Recalculate totals from receipts and deductions lists.
     */
    private void recalculateTotals() {
        this.totalGrossReceipts = receipts.stream()
            .map(r -> r.getGrossAmount() != null ? r.getGrossAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.totalCommission = receipts.stream()
            .map(r -> r.getCommissionAmount() != null ? r.getCommissionAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.totalNetReceipts = receipts.stream()
            .map(r -> r.getNetAmount() != null ? r.getNetAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.totalDeductions = deductions.stream()
            .map(d -> d.getGrossAmount() != null ? d.getGrossAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Update legacy field
        this.totalReceipts = this.totalGrossReceipts;

        calculateBalance();
    }

    /**
     * Check if this property has any data.
     */
    public boolean hasData() {
        return !receipts.isEmpty() || !deductions.isEmpty();
    }
}

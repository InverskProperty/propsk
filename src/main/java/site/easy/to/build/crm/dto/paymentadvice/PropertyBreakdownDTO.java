package site.easy.to.build.crm.dto.paymentadvice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a per-property breakdown in the Payment Advice.
 * Shows receipts (tenant payments) and deductions (commission, expenses, etc.) for one property.
 */
public class PropertyBreakdownDTO {

    private Long propertyId;
    private String propertyName;
    private String propertyAddress;

    private List<ReceiptLineDTO> receipts = new ArrayList<>();
    private List<DeductionLineDTO> deductions = new ArrayList<>();

    private BigDecimal totalReceipts = BigDecimal.ZERO;
    private BigDecimal totalDeductions = BigDecimal.ZERO;
    private BigDecimal balance = BigDecimal.ZERO;

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
        if (receipt.getAmount() != null) {
            this.totalReceipts = this.totalReceipts.add(receipt.getAmount());
        }
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
     * Calculate balance as receipts minus deductions.
     */
    public void calculateBalance() {
        this.balance = this.totalReceipts.subtract(this.totalDeductions);
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

    public BigDecimal getTotalReceipts() {
        return totalReceipts;
    }

    public void setTotalReceipts(BigDecimal totalReceipts) {
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
        this.totalReceipts = receipts.stream()
            .map(r -> r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.totalDeductions = deductions.stream()
            .map(d -> d.getGrossAmount() != null ? d.getGrossAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        calculateBalance();
    }

    /**
     * Check if this property has any data.
     */
    public boolean hasData() {
        return !receipts.isEmpty() || !deductions.isEmpty();
    }
}

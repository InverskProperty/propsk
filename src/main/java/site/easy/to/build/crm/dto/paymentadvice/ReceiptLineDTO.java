package site.easy.to.build.crm.dto.paymentadvice;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a single receipt line (tenant payment) in the Payment Advice.
 * Shows gross income, commission deducted, and net to owner.
 */
public class ReceiptLineDTO {

    private String tenantName;
    private BigDecimal grossAmount;      // Gross rent received from tenant
    private BigDecimal commissionAmount; // Commission deducted
    private BigDecimal netAmount;        // Net to owner (gross - commission)
    private LocalDate paymentDate;
    private String leaseReference;

    public ReceiptLineDTO() {
    }

    public ReceiptLineDTO(String tenantName, BigDecimal grossAmount, BigDecimal commissionAmount, BigDecimal netAmount) {
        this.tenantName = tenantName;
        this.grossAmount = grossAmount;
        this.commissionAmount = commissionAmount != null ? commissionAmount : BigDecimal.ZERO;
        this.netAmount = netAmount;
    }

    // Getters and Setters

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    /**
     * @deprecated Use getGrossAmount() instead
     */
    @Deprecated
    public BigDecimal getAmount() {
        return grossAmount;
    }

    /**
     * @deprecated Use setGrossAmount() instead
     */
    @Deprecated
    public void setAmount(BigDecimal amount) {
        this.grossAmount = amount;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public void setGrossAmount(BigDecimal grossAmount) {
        this.grossAmount = grossAmount;
    }

    public BigDecimal getCommissionAmount() {
        return commissionAmount;
    }

    public void setCommissionAmount(BigDecimal commissionAmount) {
        this.commissionAmount = commissionAmount;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public void setNetAmount(BigDecimal netAmount) {
        this.netAmount = netAmount;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
    }

    public String getLeaseReference() {
        return leaseReference;
    }

    public void setLeaseReference(String leaseReference) {
        this.leaseReference = leaseReference;
    }
}

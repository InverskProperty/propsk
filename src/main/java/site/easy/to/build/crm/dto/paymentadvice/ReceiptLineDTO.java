package site.easy.to.build.crm.dto.paymentadvice;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a single receipt line (tenant payment) in the Payment Advice.
 */
public class ReceiptLineDTO {

    private String tenantName;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private String leaseReference;

    public ReceiptLineDTO() {
    }

    public ReceiptLineDTO(String tenantName, BigDecimal amount) {
        this.tenantName = tenantName;
        this.amount = amount;
    }

    public ReceiptLineDTO(String tenantName, BigDecimal amount, LocalDate paymentDate, String leaseReference) {
        this.tenantName = tenantName;
        this.amount = amount;
        this.paymentDate = paymentDate;
        this.leaseReference = leaseReference;
    }

    // Getters and Setters

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
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

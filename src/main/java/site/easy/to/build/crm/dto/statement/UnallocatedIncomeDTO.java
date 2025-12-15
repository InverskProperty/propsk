package site.easy.to.build.crm.dto.statement;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for unallocated income (rent received but not yet allocated to a payment)
 */
public class UnallocatedIncomeDTO {
    private Long transactionId;
    private LocalDate transactionDate;
    private Long propertyId;
    private String propertyName;
    private String leaseReference;
    private String tenantName;
    private String category;
    private BigDecimal grossAmount;
    private BigDecimal commission;
    private BigDecimal netDue;
    private BigDecimal allocatedAmount;
    private BigDecimal remainingUnallocated;

    // Getters and Setters
    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }

    public Long getPropertyId() { return propertyId; }
    public void setPropertyId(Long propertyId) { this.propertyId = propertyId; }

    public String getPropertyName() { return propertyName; }
    public void setPropertyName(String propertyName) { this.propertyName = propertyName; }

    public String getLeaseReference() { return leaseReference; }
    public void setLeaseReference(String leaseReference) { this.leaseReference = leaseReference; }

    public String getTenantName() { return tenantName; }
    public void setTenantName(String tenantName) { this.tenantName = tenantName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public BigDecimal getGrossAmount() { return grossAmount; }
    public void setGrossAmount(BigDecimal grossAmount) { this.grossAmount = grossAmount; }

    public BigDecimal getCommission() { return commission; }
    public void setCommission(BigDecimal commission) { this.commission = commission; }

    public BigDecimal getNetDue() { return netDue; }
    public void setNetDue(BigDecimal netDue) { this.netDue = netDue; }

    public BigDecimal getAllocatedAmount() { return allocatedAmount; }
    public void setAllocatedAmount(BigDecimal allocatedAmount) { this.allocatedAmount = allocatedAmount; }

    public BigDecimal getRemainingUnallocated() { return remainingUnallocated; }
    public void setRemainingUnallocated(BigDecimal remainingUnallocated) { this.remainingUnallocated = remainingUnallocated; }
}

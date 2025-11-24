package site.easy.to.build.crm.dto.statement;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for Lease Master data in Option C statement generation
 *
 * Represents one lease with all static information needed for statement calculations
 * Maps to LEASE_MASTER sheet in Excel output
 */
public class LeaseMasterDTO {

    private Long leaseId;
    private String leaseReference;
    private Long propertyId;
    private String propertyName;
    private String propertyAddress;
    private Long customerId;
    private String customerName;
    private String tenantName;  // Tenant/occupant name
    private LocalDate startDate;
    private LocalDate endDate;  // NULL = ongoing lease
    private BigDecimal monthlyRent;
    private String frequency;  // MONTHLY, WEEKLY, etc.
    private Integer paymentDay;  // Day of month rent is due

    // Constructor
    public LeaseMasterDTO() {
    }

    public LeaseMasterDTO(Long leaseId, String leaseReference, Long propertyId, String propertyName,
                         String propertyAddress, Long customerId, String customerName,
                         LocalDate startDate, LocalDate endDate, BigDecimal monthlyRent, String frequency) {
        this.leaseId = leaseId;
        this.leaseReference = leaseReference;
        this.propertyId = propertyId;
        this.propertyName = propertyName;
        this.propertyAddress = propertyAddress;
        this.customerId = customerId;
        this.customerName = customerName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.monthlyRent = monthlyRent;
        this.frequency = frequency;
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

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public BigDecimal getMonthlyRent() {
        return monthlyRent;
    }

    public void setMonthlyRent(BigDecimal monthlyRent) {
        this.monthlyRent = monthlyRent;
    }

    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(String frequency) {
        this.frequency = frequency;
    }

    public Integer getPaymentDay() {
        return paymentDay;
    }

    public void setPaymentDay(Integer paymentDay) {
        this.paymentDay = paymentDay;
    }

    @Override
    public String toString() {
        return "LeaseMasterDTO{" +
                "leaseId=" + leaseId +
                ", leaseReference='" + leaseReference + '\'' +
                ", propertyName='" + propertyName + '\'' +
                ", customerName='" + customerName + '\'' +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", monthlyRent=" + monthlyRent +
                '}';
    }
}

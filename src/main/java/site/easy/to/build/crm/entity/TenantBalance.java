package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entity to track running tenant balances across statement periods
 * Maintains historical continuity for rent arrears and payments
 */
@Entity
@Table(name = "tenant_balances")
public class TenantBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // NEW: Link to invoice (lease) - enables tracking balance per lease
    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "tenant_id", length = 100)
    private String tenantId;

    @Column(name = "property_id", length = 100)
    private String propertyId;

    @Column(name = "statement_period", nullable = false)
    private LocalDate statementPeriod;

    @Column(name = "previous_balance", precision = 10, scale = 2)
    private BigDecimal previousBalance = BigDecimal.ZERO;

    @Column(name = "rent_due", precision = 10, scale = 2)
    private BigDecimal rentDue = BigDecimal.ZERO;

    @Column(name = "rent_received", precision = 10, scale = 2)
    private BigDecimal rentReceived = BigDecimal.ZERO;

    @Column(name = "charges", precision = 10, scale = 2)
    private BigDecimal charges = BigDecimal.ZERO;

    @Column(name = "payments", precision = 10, scale = 2)
    private BigDecimal payments = BigDecimal.ZERO;

    @Column(name = "current_balance", precision = 10, scale = 2)
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Column(name = "running_balance", precision = 10, scale = 2)
    private BigDecimal runningBalance = BigDecimal.ZERO;

    @Column(name = "created_date", nullable = false)
    private LocalDate createdDate;

    @Column(name = "updated_date")
    private LocalDate updatedDate;

    // Constructors
    public TenantBalance() {
        this.createdDate = LocalDate.now();
    }

    public TenantBalance(Long invoiceId, String tenantId, String propertyId, LocalDate statementPeriod) {
        this();
        this.invoiceId = invoiceId;
        this.tenantId = tenantId;
        this.propertyId = propertyId;
        this.statementPeriod = statementPeriod;
    }

    // Legacy constructor for backward compatibility
    @Deprecated
    public TenantBalance(String tenantId, String propertyId, LocalDate statementPeriod) {
        this();
        this.tenantId = tenantId;
        this.propertyId = propertyId;
        this.statementPeriod = statementPeriod;
    }

    // Helper method to calculate running balance
    public void calculateRunningBalance() {
        // Running Balance = Previous Balance + Rent Due - Rent Received + Charges - Payments
        this.runningBalance = previousBalance
            .add(rentDue)
            .subtract(rentReceived)
            .add(charges)
            .subtract(payments);

        this.currentBalance = runningBalance;
        this.updatedDate = LocalDate.now();
    }

    // Helper method for rent arrears calculation
    public BigDecimal getRentArrears() {
        return rentDue.subtract(rentReceived);
    }

    // Helper method to check if tenant is in arrears
    public boolean isInArrears() {
        return runningBalance.compareTo(BigDecimal.ZERO) > 0;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(Long invoiceId) {
        this.invoiceId = invoiceId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(String propertyId) {
        this.propertyId = propertyId;
    }

    public LocalDate getStatementPeriod() {
        return statementPeriod;
    }

    public void setStatementPeriod(LocalDate statementPeriod) {
        this.statementPeriod = statementPeriod;
    }

    public BigDecimal getPreviousBalance() {
        return previousBalance;
    }

    public void setPreviousBalance(BigDecimal previousBalance) {
        this.previousBalance = previousBalance;
    }

    public BigDecimal getRentDue() {
        return rentDue;
    }

    public void setRentDue(BigDecimal rentDue) {
        this.rentDue = rentDue;
    }

    public BigDecimal getRentReceived() {
        return rentReceived;
    }

    public void setRentReceived(BigDecimal rentReceived) {
        this.rentReceived = rentReceived;
    }

    public BigDecimal getCharges() {
        return charges;
    }

    public void setCharges(BigDecimal charges) {
        this.charges = charges;
    }

    public BigDecimal getPayments() {
        return payments;
    }

    public void setPayments(BigDecimal payments) {
        this.payments = payments;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public void setCurrentBalance(BigDecimal currentBalance) {
        this.currentBalance = currentBalance;
    }

    public BigDecimal getRunningBalance() {
        return runningBalance;
    }

    public void setRunningBalance(BigDecimal runningBalance) {
        this.runningBalance = runningBalance;
    }

    public LocalDate getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDate createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDate getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(LocalDate updatedDate) {
        this.updatedDate = updatedDate;
    }
}
package site.easy.to.build.crm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single row in a lease-based statement
 * Each row corresponds to one lease agreement with all associated financial data
 */
public class LeaseStatementRow {

    // Lease identification
    private Long leaseId;
    private String leaseReference;
    private String unitNumber;
    private String propertyName;

    // Tenant information
    private String tenantName;
    private Long tenantId;

    // Lease dates
    private LocalDate tenancyStartDate;
    private LocalDate tenancyEndDate;
    private Integer rentDueDay; // Day of month rent is due

    // Financial data for the statement period
    private BigDecimal rentDueAmount;
    private LocalDate rentReceivedDate;
    private BigDecimal rentReceivedAmount;

    // Commission and fees
    private BigDecimal managementFeePercentage;
    private BigDecimal managementFeeAmount;
    private BigDecimal serviceFeePercentage;
    private BigDecimal serviceFeeAmount;

    // Expenses (up to 4 as per template)
    private List<ExpenseItem> expenses;

    // Calculated totals
    private BigDecimal totalExpenses;
    private BigDecimal netDueToLandlord;
    private BigDecimal rentDueLessReceived; // For period only
    private BigDecimal tenantBalance; // Cumulative from lease start

    // Payment tracking
    private LocalDate datePaid; // When landlord was paid
    private String paymentBatch;
    private String comments;

    public LeaseStatementRow() {
        this.expenses = new ArrayList<>();
        this.rentDueAmount = BigDecimal.ZERO;
        this.rentReceivedAmount = BigDecimal.ZERO;
        this.managementFeeAmount = BigDecimal.ZERO;
        this.serviceFeeAmount = BigDecimal.ZERO;
        this.totalExpenses = BigDecimal.ZERO;
        this.netDueToLandlord = BigDecimal.ZERO;
        this.rentDueLessReceived = BigDecimal.ZERO;
        this.tenantBalance = BigDecimal.ZERO;
    }

    /**
     * Calculate derived fields based on the data
     */
    public void calculateTotals() {
        // Calculate total expenses
        this.totalExpenses = expenses.stream()
            .map(ExpenseItem::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate rent due less received (for this period)
        this.rentDueLessReceived = rentDueAmount.subtract(rentReceivedAmount);

        // Calculate net due to landlord
        // Net = Rent Received - Management Fee - Service Fee - Expenses
        BigDecimal totalFees = managementFeeAmount.add(serviceFeeAmount);
        this.netDueToLandlord = rentReceivedAmount
            .subtract(totalFees)
            .subtract(totalExpenses);
    }

    /**
     * Check if this lease is vacant (no tenant assigned or future start date)
     */
    public boolean isVacant() {
        return tenantName == null || tenantName.trim().isEmpty() || tenantName.equalsIgnoreCase("Vacant");
    }

    /**
     * Check if lease is active during the given date range
     */
    public boolean isActiveDuring(LocalDate startDate, LocalDate endDate) {
        // Lease is active if it overlaps with the date range
        if (tenancyStartDate == null) return false;

        // If lease has no end date, it's ongoing
        if (tenancyEndDate == null) {
            return !tenancyStartDate.isAfter(endDate);
        }

        // Check for overlap
        return !tenancyStartDate.isAfter(endDate) && !tenancyEndDate.isBefore(startDate);
    }

    // Nested class for expense items
    public static class ExpenseItem {
        private String label;
        private BigDecimal amount;
        private String comment;

        public ExpenseItem() {
            this.amount = BigDecimal.ZERO;
        }

        public ExpenseItem(String label, BigDecimal amount, String comment) {
            this.label = label;
            this.amount = amount != null ? amount : BigDecimal.ZERO;
            this.comment = comment;
        }

        // Getters and setters
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }

        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }

    // ===== GETTERS AND SETTERS =====

    public Long getLeaseId() { return leaseId; }
    public void setLeaseId(Long leaseId) { this.leaseId = leaseId; }

    public String getLeaseReference() { return leaseReference; }
    public void setLeaseReference(String leaseReference) { this.leaseReference = leaseReference; }

    public String getUnitNumber() { return unitNumber; }
    public void setUnitNumber(String unitNumber) { this.unitNumber = unitNumber; }

    public String getPropertyName() { return propertyName; }
    public void setPropertyName(String propertyName) { this.propertyName = propertyName; }

    public String getTenantName() { return tenantName; }
    public void setTenantName(String tenantName) { this.tenantName = tenantName; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public LocalDate getTenancyStartDate() { return tenancyStartDate; }
    public void setTenancyStartDate(LocalDate tenancyStartDate) { this.tenancyStartDate = tenancyStartDate; }

    public LocalDate getTenancyEndDate() { return tenancyEndDate; }
    public void setTenancyEndDate(LocalDate tenancyEndDate) { this.tenancyEndDate = tenancyEndDate; }

    public Integer getRentDueDay() { return rentDueDay; }
    public void setRentDueDay(Integer rentDueDay) { this.rentDueDay = rentDueDay; }

    public BigDecimal getRentDueAmount() { return rentDueAmount; }
    public void setRentDueAmount(BigDecimal rentDueAmount) { this.rentDueAmount = rentDueAmount; }

    public LocalDate getRentReceivedDate() { return rentReceivedDate; }
    public void setRentReceivedDate(LocalDate rentReceivedDate) { this.rentReceivedDate = rentReceivedDate; }

    public BigDecimal getRentReceivedAmount() { return rentReceivedAmount; }
    public void setRentReceivedAmount(BigDecimal rentReceivedAmount) { this.rentReceivedAmount = rentReceivedAmount; }

    public BigDecimal getManagementFeePercentage() { return managementFeePercentage; }
    public void setManagementFeePercentage(BigDecimal managementFeePercentage) {
        this.managementFeePercentage = managementFeePercentage;
    }

    public BigDecimal getManagementFeeAmount() { return managementFeeAmount; }
    public void setManagementFeeAmount(BigDecimal managementFeeAmount) {
        this.managementFeeAmount = managementFeeAmount;
    }

    public BigDecimal getServiceFeePercentage() { return serviceFeePercentage; }
    public void setServiceFeePercentage(BigDecimal serviceFeePercentage) {
        this.serviceFeePercentage = serviceFeePercentage;
    }

    public BigDecimal getServiceFeeAmount() { return serviceFeeAmount; }
    public void setServiceFeeAmount(BigDecimal serviceFeeAmount) {
        this.serviceFeeAmount = serviceFeeAmount;
    }

    public List<ExpenseItem> getExpenses() { return expenses; }
    public void setExpenses(List<ExpenseItem> expenses) { this.expenses = expenses; }

    public void addExpense(String label, BigDecimal amount, String comment) {
        this.expenses.add(new ExpenseItem(label, amount, comment));
    }

    public BigDecimal getTotalExpenses() { return totalExpenses; }
    public void setTotalExpenses(BigDecimal totalExpenses) { this.totalExpenses = totalExpenses; }

    public BigDecimal getNetDueToLandlord() { return netDueToLandlord; }
    public void setNetDueToLandlord(BigDecimal netDueToLandlord) { this.netDueToLandlord = netDueToLandlord; }

    public BigDecimal getRentDueLessReceived() { return rentDueLessReceived; }
    public void setRentDueLessReceived(BigDecimal rentDueLessReceived) {
        this.rentDueLessReceived = rentDueLessReceived;
    }

    public BigDecimal getTenantBalance() { return tenantBalance; }
    public void setTenantBalance(BigDecimal tenantBalance) { this.tenantBalance = tenantBalance; }

    public LocalDate getDatePaid() { return datePaid; }
    public void setDatePaid(LocalDate datePaid) { this.datePaid = datePaid; }

    public String getPaymentBatch() { return paymentBatch; }
    public void setPaymentBatch(String paymentBatch) { this.paymentBatch = paymentBatch; }

    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }

    @Override
    public String toString() {
        return "LeaseStatementRow{" +
                "leaseReference='" + leaseReference + '\'' +
                ", unitNumber='" + unitNumber + '\'' +
                ", tenantName='" + tenantName + '\'' +
                ", rentDue=" + rentDueAmount +
                ", rentReceived=" + rentReceivedAmount +
                ", balance=" + tenantBalance +
                '}';
    }
}

package site.easy.to.build.crm.dto.servicecharge;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO for Service Charge Statement.
 *
 * Service charge statements are for block properties and show:
 * - Opening balance at period start
 * - All transactions (income and expenses) in date order with running balance
 * - Closing balance at period end
 *
 * This is different from Payment Advice which is tied to a specific payment batch.
 * Service Charge Statement shows all activity for a period.
 */
public class ServiceChargeStatementDTO {

    // Block property details
    private Long blockId;
    private String blockName;
    private Long blockPropertyId;
    private String blockPropertyName;
    private Long leaseId;

    // Service charge payer (tenant on the block property lease)
    private String serviceChargePayer;

    // Agency details
    private String agencyName = "Propsk Ltd";
    private String agencyRegistrationNumber = "15933011";
    private String agencyAddress = "1 Poplar Court, Greensward Lane, Hockley, SS5 5JB";

    // Period
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private LocalDate statementDate;

    // Balance information
    private BigDecimal openingBalance = BigDecimal.ZERO;
    private BigDecimal totalIncome = BigDecimal.ZERO;
    private BigDecimal totalExpenses = BigDecimal.ZERO;
    private BigDecimal closingBalance = BigDecimal.ZERO;

    // Combined transaction list (sorted by date) with running balance
    private List<ServiceChargeTransactionLineDTO> transactions = new ArrayList<>();

    public ServiceChargeStatementDTO() {
        this.statementDate = LocalDate.now();
    }

    /**
     * Calculate closing balance from opening + income - expenses
     */
    public void calculateClosingBalance() {
        this.closingBalance = openingBalance.add(totalIncome).subtract(totalExpenses);
    }

    /**
     * Add a transaction and update running balances.
     * Transactions should be added in date order.
     */
    public void addTransaction(ServiceChargeTransactionLineDTO transaction) {
        // Calculate running balance based on previous transaction or opening balance
        BigDecimal previousBalance = transactions.isEmpty()
            ? openingBalance
            : transactions.get(transactions.size() - 1).getRunningBalance();

        BigDecimal runningBalance;
        if (transaction.isIncome()) {
            runningBalance = previousBalance.add(transaction.getAmountIn());
            totalIncome = totalIncome.add(transaction.getAmountIn());
        } else {
            runningBalance = previousBalance.subtract(transaction.getAmountOut());
            totalExpenses = totalExpenses.add(transaction.getAmountOut());
        }

        transaction.setRunningBalance(runningBalance);
        transactions.add(transaction);

        // Update closing balance
        calculateClosingBalance();
    }

    /**
     * Check if there is any activity
     */
    public boolean hasActivity() {
        return !transactions.isEmpty();
    }

    // Getters and Setters

    public Long getBlockId() {
        return blockId;
    }

    public void setBlockId(Long blockId) {
        this.blockId = blockId;
    }

    public String getBlockName() {
        return blockName;
    }

    public void setBlockName(String blockName) {
        this.blockName = blockName;
    }

    public Long getBlockPropertyId() {
        return blockPropertyId;
    }

    public void setBlockPropertyId(Long blockPropertyId) {
        this.blockPropertyId = blockPropertyId;
    }

    public String getBlockPropertyName() {
        return blockPropertyName;
    }

    public void setBlockPropertyName(String blockPropertyName) {
        this.blockPropertyName = blockPropertyName;
    }

    public Long getLeaseId() {
        return leaseId;
    }

    public void setLeaseId(Long leaseId) {
        this.leaseId = leaseId;
    }

    public String getServiceChargePayer() {
        return serviceChargePayer;
    }

    public void setServiceChargePayer(String serviceChargePayer) {
        this.serviceChargePayer = serviceChargePayer;
    }

    public String getAgencyName() {
        return agencyName;
    }

    public void setAgencyName(String agencyName) {
        this.agencyName = agencyName;
    }

    public String getAgencyRegistrationNumber() {
        return agencyRegistrationNumber;
    }

    public void setAgencyRegistrationNumber(String agencyRegistrationNumber) {
        this.agencyRegistrationNumber = agencyRegistrationNumber;
    }

    public String getAgencyAddress() {
        return agencyAddress;
    }

    public void setAgencyAddress(String agencyAddress) {
        this.agencyAddress = agencyAddress;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public LocalDate getStatementDate() {
        return statementDate;
    }

    public void setStatementDate(LocalDate statementDate) {
        this.statementDate = statementDate;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public void setOpeningBalance(BigDecimal openingBalance) {
        this.openingBalance = openingBalance != null ? openingBalance : BigDecimal.ZERO;
    }

    public BigDecimal getTotalIncome() {
        return totalIncome;
    }

    public void setTotalIncome(BigDecimal totalIncome) {
        this.totalIncome = totalIncome != null ? totalIncome : BigDecimal.ZERO;
    }

    public BigDecimal getTotalExpenses() {
        return totalExpenses;
    }

    public void setTotalExpenses(BigDecimal totalExpenses) {
        this.totalExpenses = totalExpenses != null ? totalExpenses : BigDecimal.ZERO;
    }

    public BigDecimal getClosingBalance() {
        return closingBalance;
    }

    public void setClosingBalance(BigDecimal closingBalance) {
        this.closingBalance = closingBalance != null ? closingBalance : BigDecimal.ZERO;
    }

    public List<ServiceChargeTransactionLineDTO> getTransactions() {
        return transactions;
    }

    public void setTransactions(List<ServiceChargeTransactionLineDTO> transactions) {
        this.transactions = transactions != null ? transactions : new ArrayList<>();
    }
}

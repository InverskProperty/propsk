package site.easy.to.build.crm.dto.paymentadvice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Main DTO for Payment Advice document.
 * Contains all data needed to render a payment advice for a single owner payment (batch).
 */
public class PaymentAdviceDTO {

    // Header section - Recipient (Owner)
    private Long ownerId;
    private String ownerName;
    private String ownerAddress;
    private String ownerEmail;

    // Header section - Sender (Agency)
    private String agencyName = "Propsk Ltd";
    private String agencyRegistrationNumber = "15933011";
    private String agencyAddress = "1 Poplar Court, Greensward Lane, Hockley, SS5 5JB";

    // Payment details
    private LocalDate adviceDate;
    private String batchReference;
    private String paymentReference;
    private BigDecimal totalAmount;
    private String paymentMethod;
    private String status;

    // Per-property breakdowns
    private List<PropertyBreakdownDTO> properties = new ArrayList<>();

    // Settlement summary
    private BigDecimal totalReceipts = BigDecimal.ZERO;
    private BigDecimal totalDeductions = BigDecimal.ZERO;
    private BigDecimal totalBalance = BigDecimal.ZERO;
    private BigDecimal amountSettled = BigDecimal.ZERO;

    public PaymentAdviceDTO() {
    }

    /**
     * Add a property breakdown and update totals.
     */
    public void addProperty(PropertyBreakdownDTO property) {
        this.properties.add(property);
        updateTotals();
    }

    /**
     * Update totals from all property breakdowns.
     */
    public void updateTotals() {
        this.totalReceipts = properties.stream()
            .map(PropertyBreakdownDTO::getTotalReceipts)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.totalDeductions = properties.stream()
            .map(PropertyBreakdownDTO::getTotalDeductions)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.totalBalance = this.totalReceipts.subtract(this.totalDeductions);
    }

    /**
     * Get the number of properties included in this payment.
     */
    public int getPropertyCount() {
        return properties.size();
    }

    // Getters and Setters

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getOwnerAddress() {
        return ownerAddress;
    }

    public void setOwnerAddress(String ownerAddress) {
        this.ownerAddress = ownerAddress;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public void setOwnerEmail(String ownerEmail) {
        this.ownerEmail = ownerEmail;
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

    public LocalDate getAdviceDate() {
        return adviceDate;
    }

    public void setAdviceDate(LocalDate adviceDate) {
        this.adviceDate = adviceDate;
    }

    public String getBatchReference() {
        return batchReference;
    }

    public void setBatchReference(String batchReference) {
        this.batchReference = batchReference;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<PropertyBreakdownDTO> getProperties() {
        return properties;
    }

    public void setProperties(List<PropertyBreakdownDTO> properties) {
        this.properties = properties;
        updateTotals();
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

    public BigDecimal getTotalBalance() {
        return totalBalance;
    }

    public void setTotalBalance(BigDecimal totalBalance) {
        this.totalBalance = totalBalance;
    }

    public BigDecimal getAmountSettled() {
        return amountSettled;
    }

    public void setAmountSettled(BigDecimal amountSettled) {
        this.amountSettled = amountSettled;
    }
}

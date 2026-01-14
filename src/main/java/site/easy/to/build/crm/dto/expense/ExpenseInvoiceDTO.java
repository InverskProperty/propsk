package site.easy.to.build.crm.dto.expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO for generating expense invoices.
 * Contains all data needed to render a professional expense invoice PDF.
 */
public class ExpenseInvoiceDTO {

    // ===== INVOICE HEADER =====

    private String invoiceNumber;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private String status; // PAID, PENDING, etc.

    // ===== PROPERTY DETAILS =====

    private Long propertyId;
    private String propertyName;
    private String propertyAddress;

    // ===== OWNER DETAILS (Bill To) =====

    private Long ownerId;
    private String ownerName;
    private String ownerAddress;
    private String ownerEmail;

    // ===== VENDOR/SUPPLIER DETAILS (From) =====

    private String vendorName;
    private String vendorAddress;
    private String vendorPhone;
    private String vendorEmail;
    private String vendorReference; // Contractor ID or reference

    // ===== AGENCY DETAILS =====

    private String agencyName;
    private String agencyAddress;
    private String agencyPhone;
    private String agencyEmail;
    private String agencyRegistrationNumber;

    // ===== LINE ITEMS =====

    private List<ExpenseLineItemDTO> lineItems = new ArrayList<>();

    // ===== TOTALS =====

    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal vatRate = BigDecimal.ZERO;
    private BigDecimal vatAmount = BigDecimal.ZERO;
    private BigDecimal totalAmount = BigDecimal.ZERO;

    // ===== TRANSACTION REFERENCE =====

    private Long transactionId;
    private String transactionReference;
    private LocalDate transactionDate;
    private String paymentMethod;
    private String paymentReference;
    private LocalDate paymentDate;

    // ===== EXPENSE CATEGORY =====

    private String expenseCategory;
    private String expenseType;
    private String description;

    // ===== METADATA =====

    private LocalDateTime generatedAt;
    private String generatedBy;

    // ===== NESTED DTO FOR LINE ITEMS =====

    public static class ExpenseLineItemDTO {
        private String description;
        private String category;
        private int quantity = 1;
        private BigDecimal unitPrice = BigDecimal.ZERO;
        private BigDecimal vatRate = BigDecimal.ZERO;
        private BigDecimal vatAmount = BigDecimal.ZERO;
        private BigDecimal lineTotal = BigDecimal.ZERO;

        public ExpenseLineItemDTO() {}

        public ExpenseLineItemDTO(String description, BigDecimal amount) {
            this.description = description;
            this.unitPrice = amount;
            this.lineTotal = amount;
        }

        public ExpenseLineItemDTO(String description, BigDecimal amount, BigDecimal vatRate) {
            this.description = description;
            this.unitPrice = amount;
            this.vatRate = vatRate;
            if (vatRate != null && vatRate.compareTo(BigDecimal.ZERO) > 0) {
                this.vatAmount = amount.multiply(vatRate).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                this.lineTotal = amount.add(this.vatAmount);
            } else {
                this.lineTotal = amount;
            }
        }

        // Getters and Setters
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }

        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

        public BigDecimal getVatRate() { return vatRate; }
        public void setVatRate(BigDecimal vatRate) { this.vatRate = vatRate; }

        public BigDecimal getVatAmount() { return vatAmount; }
        public void setVatAmount(BigDecimal vatAmount) { this.vatAmount = vatAmount; }

        public BigDecimal getLineTotal() { return lineTotal; }
        public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
    }

    // ===== CONSTRUCTORS =====

    public ExpenseInvoiceDTO() {
        this.generatedAt = LocalDateTime.now();
    }

    // ===== UTILITY METHODS =====

    /**
     * Add a line item and recalculate totals
     */
    public void addLineItem(ExpenseLineItemDTO item) {
        this.lineItems.add(item);
        recalculateTotals();
    }

    /**
     * Recalculate subtotal, VAT, and total from line items
     */
    public void recalculateTotals() {
        this.subtotal = lineItems.stream()
                .map(ExpenseLineItemDTO::getUnitPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.vatAmount = lineItems.stream()
                .map(ExpenseLineItemDTO::getVatAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.totalAmount = subtotal.add(vatAmount);
    }

    /**
     * Create a single-line expense invoice
     */
    public static ExpenseInvoiceDTO forSingleExpense(String description, BigDecimal amount, BigDecimal vatAmount) {
        ExpenseInvoiceDTO dto = new ExpenseInvoiceDTO();

        ExpenseLineItemDTO item = new ExpenseLineItemDTO();
        item.setDescription(description);
        item.setUnitPrice(amount);
        item.setVatAmount(vatAmount != null ? vatAmount : BigDecimal.ZERO);
        item.setLineTotal(amount.add(vatAmount != null ? vatAmount : BigDecimal.ZERO));

        dto.addLineItem(item);
        dto.setSubtotal(amount);
        dto.setVatAmount(vatAmount != null ? vatAmount : BigDecimal.ZERO);
        dto.setTotalAmount(amount.add(vatAmount != null ? vatAmount : BigDecimal.ZERO));

        return dto;
    }

    /**
     * Generate invoice number from transaction data
     */
    public void generateInvoiceNumber(Long transactionId, Long propertyId) {
        String datePrefix = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        this.invoiceNumber = String.format("EXP-%s-%d-%d", datePrefix, propertyId != null ? propertyId : 0, transactionId != null ? transactionId : 0);
    }

    // ===== GETTERS AND SETTERS =====

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public LocalDate getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(LocalDate invoiceDate) { this.invoiceDate = invoiceDate; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getPropertyId() { return propertyId; }
    public void setPropertyId(Long propertyId) { this.propertyId = propertyId; }

    public String getPropertyName() { return propertyName; }
    public void setPropertyName(String propertyName) { this.propertyName = propertyName; }

    public String getPropertyAddress() { return propertyAddress; }
    public void setPropertyAddress(String propertyAddress) { this.propertyAddress = propertyAddress; }

    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getOwnerAddress() { return ownerAddress; }
    public void setOwnerAddress(String ownerAddress) { this.ownerAddress = ownerAddress; }

    public String getOwnerEmail() { return ownerEmail; }
    public void setOwnerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; }

    public String getVendorName() { return vendorName; }
    public void setVendorName(String vendorName) { this.vendorName = vendorName; }

    public String getVendorAddress() { return vendorAddress; }
    public void setVendorAddress(String vendorAddress) { this.vendorAddress = vendorAddress; }

    public String getVendorPhone() { return vendorPhone; }
    public void setVendorPhone(String vendorPhone) { this.vendorPhone = vendorPhone; }

    public String getVendorEmail() { return vendorEmail; }
    public void setVendorEmail(String vendorEmail) { this.vendorEmail = vendorEmail; }

    public String getVendorReference() { return vendorReference; }
    public void setVendorReference(String vendorReference) { this.vendorReference = vendorReference; }

    public String getAgencyName() { return agencyName; }
    public void setAgencyName(String agencyName) { this.agencyName = agencyName; }

    public String getAgencyAddress() { return agencyAddress; }
    public void setAgencyAddress(String agencyAddress) { this.agencyAddress = agencyAddress; }

    public String getAgencyPhone() { return agencyPhone; }
    public void setAgencyPhone(String agencyPhone) { this.agencyPhone = agencyPhone; }

    public String getAgencyEmail() { return agencyEmail; }
    public void setAgencyEmail(String agencyEmail) { this.agencyEmail = agencyEmail; }

    public String getAgencyRegistrationNumber() { return agencyRegistrationNumber; }
    public void setAgencyRegistrationNumber(String agencyRegistrationNumber) { this.agencyRegistrationNumber = agencyRegistrationNumber; }

    public List<ExpenseLineItemDTO> getLineItems() { return lineItems; }
    public void setLineItems(List<ExpenseLineItemDTO> lineItems) { this.lineItems = lineItems; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getVatRate() { return vatRate; }
    public void setVatRate(BigDecimal vatRate) { this.vatRate = vatRate; }

    public BigDecimal getVatAmount() { return vatAmount; }
    public void setVatAmount(BigDecimal vatAmount) { this.vatAmount = vatAmount; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

    public String getTransactionReference() { return transactionReference; }
    public void setTransactionReference(String transactionReference) { this.transactionReference = transactionReference; }

    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }

    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }

    public String getExpenseCategory() { return expenseCategory; }
    public void setExpenseCategory(String expenseCategory) { this.expenseCategory = expenseCategory; }

    public String getExpenseType() { return expenseType; }
    public void setExpenseType(String expenseType) { this.expenseType = expenseType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }

    public String getGeneratedBy() { return generatedBy; }
    public void setGeneratedBy(String generatedBy) { this.generatedBy = generatedBy; }
}

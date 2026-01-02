package site.easy.to.build.crm.dto.paymentadvice;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a single deduction line (commission, expense, disbursement) in the Payment Advice.
 */
public class DeductionLineDTO {

    private String type;           // Commission, Council, Disbursement, Expense, Other
    private String description;
    private BigDecimal netAmount;
    private BigDecimal vatAmount;
    private BigDecimal grossAmount;
    private String category;       // Original category from allocation
    private LocalDate transactionDate;

    public DeductionLineDTO() {
        this.vatAmount = BigDecimal.ZERO;
    }

    public DeductionLineDTO(String type, String description, BigDecimal netAmount) {
        this.type = type;
        this.description = description;
        this.netAmount = netAmount;
        this.vatAmount = BigDecimal.ZERO;
        this.grossAmount = netAmount; // No VAT for now
    }

    public DeductionLineDTO(String type, String description, BigDecimal netAmount, BigDecimal vatAmount) {
        this.type = type;
        this.description = description;
        this.netAmount = netAmount;
        this.vatAmount = vatAmount != null ? vatAmount : BigDecimal.ZERO;
        this.grossAmount = netAmount.add(this.vatAmount);
    }

    // Getters and Setters

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public void setNetAmount(BigDecimal netAmount) {
        this.netAmount = netAmount;
    }

    public BigDecimal getVatAmount() {
        return vatAmount;
    }

    public void setVatAmount(BigDecimal vatAmount) {
        this.vatAmount = vatAmount;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public void setGrossAmount(BigDecimal grossAmount) {
        this.grossAmount = grossAmount;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    /**
     * Maps allocation type/category to display type.
     */
    public static String mapToDisplayType(String allocationType, String category) {
        if ("COMMISSION".equalsIgnoreCase(allocationType)) {
            return "Commission";
        }
        if ("DISBURSEMENT".equalsIgnoreCase(allocationType)) {
            return "Disbursement";
        }
        if (category != null) {
            String lowerCategory = category.toLowerCase();
            if (lowerCategory.contains("council")) {
                return "Council";
            }
            if (lowerCategory.contains("utility") || lowerCategory.contains("utilities")) {
                return "Utilities";
            }
            if (lowerCategory.contains("maintenance") || lowerCategory.contains("repair")) {
                return "Maintenance";
            }
        }
        return "Other";
    }
}

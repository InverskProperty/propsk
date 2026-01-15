package site.easy.to.build.crm.service.expense;

import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.dto.expense.ExpenseInvoiceDTO;
import site.easy.to.build.crm.dto.expense.ExpenseInvoiceDTO.ExpenseLineItemDTO;
import site.easy.to.build.crm.dto.expense.ExpenseInvoiceDTO.InvoiceSourceType;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.*;
import site.easy.to.build.crm.service.portfolio.PortfolioBlockService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Service for generating expense invoice PDFs.
 * Creates professional invoice documents for property expenses.
 */
@Service
public class ExpenseInvoiceService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseInvoiceService.class);

    @Autowired
    private UnifiedTransactionRepository unifiedTransactionRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AgencySettingsRepository agencySettingsRepository;

    @Autowired
    private CustomerPropertyAssignmentRepository customerPropertyAssignmentRepository;

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    @Autowired
    private PortfolioBlockService portfolioBlockService;

    @Autowired
    private BlockRepository blockRepository;

    /**
     * Generate an expense invoice DTO from a unified transaction.
     *
     * @param transactionId The unified transaction ID
     * @return ExpenseInvoiceDTO populated with all invoice data
     */
    public ExpenseInvoiceDTO generateExpenseInvoice(Long transactionId) {
        log.info("Generating expense invoice for transaction: {}", transactionId);

        UnifiedTransaction transaction = unifiedTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionId));

        ExpenseInvoiceDTO invoice = new ExpenseInvoiceDTO();

        // Determine invoice source type based on transaction data
        InvoiceSourceInfo sourceInfo = determineInvoiceSourceType(transaction);
        invoice.setInvoiceSourceType(sourceInfo.sourceType);
        invoice.setShouldGenerateInvoice(sourceInfo.shouldGenerate);
        invoice.setThirdPartyVendorName(sourceInfo.vendorName);

        // Set transaction details
        invoice.setTransactionId(transactionId);
        invoice.setTransactionDate(transaction.getTransactionDate());
        invoice.setDescription(transaction.getDescription());
        invoice.setExpenseCategory(transaction.getCategory());

        // Set invoice dates
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setStatus("PAID"); // Expenses from PayProp are already paid

        // Generate invoice number
        invoice.generateInvoiceNumber(transactionId, transaction.getPropertyId());

        // Set amounts
        BigDecimal amount = transaction.getAmount() != null ? transaction.getAmount().abs() : BigDecimal.ZERO;
        BigDecimal vatAmount = BigDecimal.ZERO; // TODO: Extract VAT if available

        // Create line item
        ExpenseLineItemDTO lineItem = new ExpenseLineItemDTO();
        lineItem.setDescription(transaction.getDescription() != null ? transaction.getDescription() : "Property Expense");
        lineItem.setCategory(transaction.getCategory());
        lineItem.setUnitPrice(amount);
        lineItem.setVatAmount(vatAmount);
        lineItem.setLineTotal(amount.add(vatAmount));

        invoice.addLineItem(lineItem);
        invoice.setSubtotal(amount);
        invoice.setVatAmount(vatAmount);
        invoice.setTotalAmount(amount.add(vatAmount));

        // Set property details
        if (transaction.getPropertyId() != null) {
            invoice.setPropertyId(transaction.getPropertyId());
            invoice.setPropertyName(transaction.getPropertyName());

            Property property = propertyRepository.findById(transaction.getPropertyId()).orElse(null);
            if (property != null) {
                invoice.setPropertyAddress(buildPropertyAddress(property));

                // Get property owner
                Customer owner = findPropertyOwner(transaction.getPropertyId());
                if (owner != null) {
                    invoice.setOwnerId(owner.getCustomerId().longValue());
                    invoice.setOwnerName(owner.getName());
                    invoice.setOwnerEmail(owner.getEmail());
                    invoice.setOwnerAddress(buildCustomerAddress(owner));
                }
            }
        }

        // Set agency details (for agency-generated invoices)
        AgencySettings agency = agencySettingsRepository.getSettings().orElse(null);
        if (agency != null) {
            invoice.setAgencyName(agency.getCompanyName());
            invoice.setAgencyAddress(agency.getCompanyAddress());
            invoice.setAgencyPhone(agency.getCompanyPhone());
            invoice.setAgencyEmail(agency.getCompanyEmail());
            invoice.setAgencyRegistrationNumber(agency.getCompanyRegistrationNumber());
        }

        // For third-party invoices, set vendor details
        if (sourceInfo.sourceType == InvoiceSourceType.THIRD_PARTY_VENDOR && sourceInfo.vendorName != null) {
            invoice.setVendorName(sourceInfo.vendorName);
        }

        // For block service charges, fetch the block property details
        if (sourceInfo.sourceType == InvoiceSourceType.BLOCK_SERVICE_CHARGE) {
            invoice.setBlockExpense(true);
            populateBlockPropertyDetails(invoice, transaction);
        }

        // Set description from transaction
        invoice.setDescription(transaction.getDescription());

        log.info("Generated expense invoice: {} (source: {}, shouldGenerate: {})",
                invoice.getInvoiceNumber(), sourceInfo.sourceType, sourceInfo.shouldGenerate);
        return invoice;
    }

    /**
     * Helper class to hold invoice source determination results.
     */
    private static class InvoiceSourceInfo {
        InvoiceSourceType sourceType;
        boolean shouldGenerate;
        String vendorName;

        InvoiceSourceInfo(InvoiceSourceType sourceType, boolean shouldGenerate, String vendorName) {
            this.sourceType = sourceType;
            this.shouldGenerate = shouldGenerate;
            this.vendorName = vendorName;
        }
    }

    /**
     * Determine the invoice source type based on transaction data.
     *
     * Rules:
     * - payprop_beneficiary_type = 'agency' → AGENCY_GENERATED (generate invoice)
     * - transaction_type = 'payment_to_agency' → AGENCY_GENERATED (generate invoice)
     * - Description contains block property names → BLOCK_SERVICE_CHARGE (generate invoice)
     * - payprop_beneficiary_type = 'beneficiary' with utility/council → THIRD_PARTY_VENDOR (no generate)
     * - category = 'Owner' or description like 'Landlord' → OWNER_PAYMENT (no generate, not expense)
     */
    private InvoiceSourceInfo determineInvoiceSourceType(UnifiedTransaction transaction) {
        String description = transaction.getDescription() != null ? transaction.getDescription().toLowerCase() : "";
        String category = transaction.getCategory() != null ? transaction.getCategory().toLowerCase() : "";
        String transactionType = transaction.getTransactionType() != null ? transaction.getTransactionType().toLowerCase() : "";

        // Get beneficiary type from linked financial transaction if available
        String beneficiaryType = null;
        String beneficiaryName = null;
        if ("financial_transactions".equals(transaction.getSourceTable()) && transaction.getSourceRecordId() != null) {
            FinancialTransaction ft = financialTransactionRepository.findById(transaction.getSourceRecordId()).orElse(null);
            if (ft != null) {
                beneficiaryType = ft.getPaypropBeneficiaryType();
                // Extract vendor name from description if it contains "Beneficiary:" pattern
                if (ft.getDescription() != null && ft.getDescription().contains("Beneficiary:")) {
                    beneficiaryName = extractBeneficiaryName(ft.getDescription());
                }
            }
        }

        // Rule 1: Owner payments - NOT an expense, shouldn't show invoice
        if ("owner".equals(category) || description.contains("landlord rental payment") ||
            description.contains("landlord payment") || description.contains("payment to owner")) {
            return new InvoiceSourceInfo(InvoiceSourceType.OWNER_PAYMENT, false, null);
        }

        // Rule 2: Block/service charge payments - Agency generates invoice
        if (description.contains("boden house block") || description.contains("block property") ||
            description.contains("service charge") || "disbursement".equals(category)) {
            String blockName = extractBlockName(description);
            return new InvoiceSourceInfo(InvoiceSourceType.BLOCK_SERVICE_CHARGE, true, blockName);
        }

        // Rule 3: Agency beneficiary type or payment_to_agency - Agency generates invoice
        if ("agency".equals(beneficiaryType) || transactionType.contains("payment_to_agency")) {
            return new InvoiceSourceInfo(InvoiceSourceType.AGENCY_GENERATED, true, null);
        }

        // Rule 4: Direct utility/council payments to third party - No system invoice
        if ("beneficiary".equals(beneficiaryType) || transactionType.contains("payment_to_beneficiary")) {
            // Check if it's a utility or council payment
            if (description.contains("eon") || description.contains("scottishpower") ||
                description.contains("electric") || description.contains("gas") ||
                description.contains("water") || description.contains("council")) {
                String vendorName = beneficiaryName != null ? beneficiaryName : extractVendorFromDescription(description);
                return new InvoiceSourceInfo(InvoiceSourceType.THIRD_PARTY_VENDOR, false, vendorName);
            }
        }

        // Rule 5: Contractor payments
        if ("contractor".equals(category) || transactionType.contains("payment_to_contractor")) {
            // If paid via agency, generate invoice; if direct to contractor, don't
            if ("agency".equals(beneficiaryType) || transactionType.contains("payment_to_agency")) {
                return new InvoiceSourceInfo(InvoiceSourceType.AGENCY_GENERATED, true, null);
            } else {
                String vendorName = beneficiaryName != null ? beneficiaryName : "Contractor";
                return new InvoiceSourceInfo(InvoiceSourceType.THIRD_PARTY_VENDOR, false, vendorName);
            }
        }

        // Default: Agency generated invoice
        return new InvoiceSourceInfo(InvoiceSourceType.AGENCY_GENERATED, true, null);
    }

    /**
     * Extract beneficiary name from description like "Beneficiary: SOME NAME (beneficiary)"
     */
    private String extractBeneficiaryName(String description) {
        if (description == null) return null;
        int startIdx = description.indexOf("Beneficiary:");
        if (startIdx >= 0) {
            String afterBeneficiary = description.substring(startIdx + 12).trim();
            int endIdx = afterBeneficiary.indexOf("(");
            if (endIdx > 0) {
                return afterBeneficiary.substring(0, endIdx).trim();
            }
            return afterBeneficiary;
        }
        return null;
    }

    /**
     * Extract block name from description
     */
    private String extractBlockName(String description) {
        if (description == null) return "Block Property";
        String descLower = description.toLowerCase();
        if (descLower.contains("boden house block")) {
            return "BODEN HOUSE BLOCK PROPERTY";
        }
        return "Block Property";
    }

    /**
     * Populate block property details for block service charge invoices.
     * Finds the block associated with the property and gets the block property details.
     */
    private void populateBlockPropertyDetails(ExpenseInvoiceDTO invoice, UnifiedTransaction transaction) {
        if (transaction.getPropertyId() == null) {
            return;
        }

        // Try to find the block property through the property's block assignment
        Property property = propertyRepository.findById(transaction.getPropertyId()).orElse(null);
        if (property == null) {
            return;
        }

        // Get the block this property belongs to
        Block block = property.getBlock();
        if (block == null) {
            log.debug("Property {} has no block assigned", transaction.getPropertyId());
            // Try to extract block name from description
            String blockName = extractBlockName(transaction.getDescription());
            invoice.setBlockPropertyName(blockName);
            return;
        }

        // Get the block property (the property representing the block itself)
        Property blockProperty = block.getBlockProperty();
        if (blockProperty != null) {
            invoice.setBlockPropertyId(blockProperty.getId());
            invoice.setBlockPropertyName(blockProperty.getPropertyName());
            invoice.setBlockPropertyAddress(buildPropertyAddress(blockProperty));
            log.debug("Found block property: {} for block: {}", blockProperty.getPropertyName(), block.getName());
        } else {
            // Use the block name as fallback
            invoice.setBlockPropertyName(block.getName());
            invoice.setBlockPropertyAddress(block.getFullAddress());
            log.debug("Using block name as fallback: {}", block.getName());
        }
    }

    /**
     * Extract vendor name from description for utilities/council
     */
    private String extractVendorFromDescription(String description) {
        if (description == null) return null;
        String descLower = description.toLowerCase();
        if (descLower.contains("eon")) return "EON";
        if (descLower.contains("scottishpower")) return "ScottishPower";
        if (descLower.contains("council")) return "Council";
        // Try to extract from "VENDOR NAME - Description" pattern
        int dashIdx = description.indexOf(" - ");
        if (dashIdx > 0) {
            return description.substring(0, dashIdx).trim();
        }
        return null;
    }

    /**
     * Export expense invoice to PDF format.
     *
     * @param transactionId The unified transaction ID
     * @return byte array containing the PDF file
     */
    public byte[] exportToPdf(Long transactionId) throws IOException {
        log.info("Exporting expense invoice to PDF for transaction: {}", transactionId);

        ExpenseInvoiceDTO invoice = generateExpenseInvoice(transactionId);
        return exportToPdf(invoice);
    }

    /**
     * Export expense invoice DTO to PDF format.
     *
     * @param invoice The expense invoice DTO
     * @return byte array containing the PDF file
     */
    public byte[] exportToPdf(ExpenseInvoiceDTO invoice) throws IOException {
        String htmlContent = generatePdfHtml(invoice);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ConverterProperties properties = new ConverterProperties();
            HtmlConverter.convertToPdf(htmlContent, outputStream, properties);

            log.info("PDF export complete for invoice: {}", invoice.getInvoiceNumber());
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("Error generating PDF for invoice {}: {}", invoice.getInvoiceNumber(), e.getMessage(), e);
            throw new IOException("Failed to generate PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Generate HTML content for PDF conversion.
     */
    private String generatePdfHtml(ExpenseInvoiceDTO invoice) {
        StringBuilder html = new StringBuilder();
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy");

        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/><style>");
        // CSS styles
        html.append("body{font-family:Helvetica,Arial,sans-serif;font-size:10pt;margin:20px;color:#333}");
        html.append(".header{background:#2c3e50;color:#fff;padding:20px;margin-bottom:20px}");
        html.append(".header h1{margin:0 0 5px 0;font-size:24pt}");
        html.append(".header-table{width:100%}.header-table td{vertical-align:top;padding:3px;color:#fff}");
        html.append(".invoice-number{font-size:14pt;font-weight:bold;color:#ecf0f1}");
        html.append(".info-section{display:flex;margin-bottom:20px}");
        html.append(".info-box{flex:1;padding:15px;background:#f8f9fa;margin-right:10px;border-left:4px solid #3498db}");
        html.append(".info-box:last-child{margin-right:0}");
        html.append(".info-box h3{margin:0 0 10px 0;font-size:11pt;color:#2c3e50;text-transform:uppercase}");
        html.append(".info-box p{margin:3px 0;font-size:9pt}");
        html.append(".property-box{background:#e8f4fd;border-left-color:#2980b9}");
        html.append("table{width:100%;border-collapse:collapse;margin-bottom:20px}");
        html.append(".items-table th{background:#34495e;color:#fff;padding:10px;text-align:left;font-size:9pt}");
        html.append(".items-table td{padding:10px;border-bottom:1px solid #ddd;font-size:9pt}");
        html.append(".items-table tr:nth-child(even){background:#f8f9fa}");
        html.append(".r{text-align:right}");
        html.append(".totals-section{margin-top:20px;float:right;width:300px}");
        html.append(".totals-table td{padding:8px;border:none}");
        html.append(".totals-table .label{text-align:right;font-weight:bold;color:#7f8c8d}");
        html.append(".totals-table .value{text-align:right;min-width:100px}");
        html.append(".total-row{background:#2c3e50;color:#fff}");
        html.append(".total-row td{padding:12px}");
        html.append(".status-paid{display:inline-block;background:#27ae60;color:#fff;padding:5px 15px;border-radius:3px;font-weight:bold}");
        html.append(".status-pending{display:inline-block;background:#f39c12;color:#fff;padding:5px 15px;border-radius:3px;font-weight:bold}");
        html.append(".footer{clear:both;margin-top:40px;padding-top:20px;border-top:2px solid #eee;text-align:center;font-size:8pt;color:#95a5a6}");
        html.append(".expense-badge{display:inline-block;background:#e74c3c;color:#fff;padding:3px 10px;border-radius:3px;font-size:8pt;margin-left:10px}");
        html.append("</style></head><body>");

        // Header
        html.append("<div class=\"header\">");
        html.append("<table class=\"header-table\"><tr>");
        html.append("<td width=\"60%\">");
        html.append("<h1>EXPENSE INVOICE</h1>");
        html.append("<span class=\"expense-badge\">").append(escapeHtml(invoice.getExpenseCategory())).append("</span>");
        html.append("</td>");
        html.append("<td width=\"40%\" style=\"text-align:right\">");
        html.append("<div class=\"invoice-number\">").append(escapeHtml(invoice.getInvoiceNumber())).append("</div>");
        html.append("<div style=\"margin-top:10px\">Date: ").append(invoice.getInvoiceDate() != null ? invoice.getInvoiceDate().format(dateFormat) : "").append("</div>");
        html.append("<div style=\"margin-top:5px\"><span class=\"status-").append(invoice.getStatus() != null ? invoice.getStatus().toLowerCase() : "paid").append("\">");
        html.append(escapeHtml(invoice.getStatus() != null ? invoice.getStatus() : "PAID")).append("</span></div>");
        html.append("</td></tr></table>");
        html.append("</div>");

        // Info sections (From/To/Property)
        html.append("<table style=\"width:100%;margin-bottom:20px\"><tr>");

        // From section - Use block property for block expenses, otherwise agency
        html.append("<td width=\"33%\" style=\"vertical-align:top;padding-right:10px\">");
        html.append("<div class=\"info-box\">");
        html.append("<h3>From</h3>");

        if (invoice.isBlockExpense() && invoice.getBlockPropertyName() != null) {
            // Block service charge - show block property as the "From"
            html.append("<p><strong>").append(escapeHtml(invoice.getBlockPropertyName())).append("</strong></p>");
            if (invoice.getBlockPropertyAddress() != null) {
                html.append("<p>").append(escapeHtml(invoice.getBlockPropertyAddress()).replace("\n", "<br/>")).append("</p>");
            }
            html.append("<p><em>Block Service Charge</em></p>");
        } else {
            // Regular expense - show agency
            html.append("<p><strong>").append(escapeHtml(invoice.getAgencyName())).append("</strong></p>");
            if (invoice.getAgencyAddress() != null) html.append("<p>").append(escapeHtml(invoice.getAgencyAddress()).replace("\n", "<br/>")).append("</p>");
            if (invoice.getAgencyPhone() != null) html.append("<p>Tel: ").append(escapeHtml(invoice.getAgencyPhone())).append("</p>");
            if (invoice.getAgencyEmail() != null) html.append("<p>").append(escapeHtml(invoice.getAgencyEmail())).append("</p>");
        }
        html.append("</div></td>");

        // Owner (Bill To)
        html.append("<td width=\"33%\" style=\"vertical-align:top;padding-right:10px\">");
        html.append("<div class=\"info-box\">");
        html.append("<h3>Bill To</h3>");
        html.append("<p><strong>").append(escapeHtml(invoice.getOwnerName() != null ? invoice.getOwnerName() : "Property Owner")).append("</strong></p>");
        if (invoice.getOwnerAddress() != null) html.append("<p>").append(escapeHtml(invoice.getOwnerAddress())).append("</p>");
        if (invoice.getOwnerEmail() != null) html.append("<p>").append(escapeHtml(invoice.getOwnerEmail())).append("</p>");
        html.append("</div></td>");

        // Property
        html.append("<td width=\"33%\" style=\"vertical-align:top\">");
        html.append("<div class=\"info-box property-box\">");
        html.append("<h3>Property</h3>");
        html.append("<p><strong>").append(escapeHtml(invoice.getPropertyName() != null ? invoice.getPropertyName() : "N/A")).append("</strong></p>");
        if (invoice.getPropertyAddress() != null) html.append("<p>").append(escapeHtml(invoice.getPropertyAddress())).append("</p>");
        html.append("</div></td>");

        html.append("</tr></table>");

        // Transaction details
        if (invoice.getTransactionDate() != null || invoice.getTransactionReference() != null) {
            html.append("<div style=\"background:#f8f9fa;padding:10px;margin-bottom:20px;border-radius:3px\">");
            html.append("<strong>Transaction Details:</strong> ");
            if (invoice.getTransactionDate() != null) {
                html.append("Date: ").append(invoice.getTransactionDate().format(dateFormat));
            }
            if (invoice.getTransactionReference() != null) {
                html.append(" | Ref: ").append(escapeHtml(invoice.getTransactionReference()));
            }
            html.append("</div>");
        }

        // Line items table
        html.append("<table class=\"items-table\">");
        html.append("<tr><th style=\"width:50%\">Description</th><th style=\"width:20%\">Category</th><th class=\"r\" style=\"width:15%\">Amount</th><th class=\"r\" style=\"width:15%\">Total</th></tr>");

        for (ExpenseLineItemDTO item : invoice.getLineItems()) {
            html.append("<tr>");
            html.append("<td>").append(escapeHtml(item.getDescription() != null ? item.getDescription() : "")).append("</td>");
            html.append("<td>").append(escapeHtml(item.getCategory() != null ? item.getCategory() : "")).append("</td>");
            html.append("<td class=\"r\">").append(formatCurrency(item.getUnitPrice())).append("</td>");
            html.append("<td class=\"r\">").append(formatCurrency(item.getLineTotal())).append("</td>");
            html.append("</tr>");
        }
        html.append("</table>");

        // Totals section
        html.append("<div class=\"totals-section\">");
        html.append("<table class=\"totals-table\">");
        html.append("<tr><td class=\"label\">Subtotal:</td><td class=\"value\">").append(formatCurrency(invoice.getSubtotal())).append("</td></tr>");
        if (invoice.getVatAmount() != null && invoice.getVatAmount().compareTo(BigDecimal.ZERO) > 0) {
            html.append("<tr><td class=\"label\">VAT:</td><td class=\"value\">").append(formatCurrency(invoice.getVatAmount())).append("</td></tr>");
        }
        html.append("<tr class=\"total-row\"><td class=\"label\">TOTAL:</td><td class=\"value\" style=\"font-size:14pt\">").append(formatCurrency(invoice.getTotalAmount())).append("</td></tr>");
        html.append("</table></div>");

        // Clear float
        html.append("<div style=\"clear:both\"></div>");

        // Footer
        html.append("<div class=\"footer\">");
        html.append("<p>This expense has been charged to the property account.</p>");
        if (invoice.getAgencyName() != null) {
            html.append("<p>Generated by ").append(escapeHtml(invoice.getAgencyName()));
            if (invoice.getAgencyRegistrationNumber() != null) {
                html.append(" | Reg: ").append(escapeHtml(invoice.getAgencyRegistrationNumber()));
            }
            html.append("</p>");
        }
        html.append("</div>");

        html.append("</body></html>");

        return html.toString();
    }

    /**
     * Find the property owner for a given property.
     */
    private Customer findPropertyOwner(Long propertyId) {
        return customerPropertyAssignmentRepository.findByPropertyIdAndAssignmentType(
                propertyId, AssignmentType.OWNER)
                .stream()
                .findFirst()
                .map(CustomerPropertyAssignment::getCustomer)
                .orElse(null);
    }

    /**
     * Build property address string.
     */
    private String buildPropertyAddress(Property property) {
        StringBuilder address = new StringBuilder();
        if (property.getAddressLine1() != null) address.append(property.getAddressLine1());
        if (property.getAddressLine2() != null && !property.getAddressLine2().isEmpty()) {
            if (address.length() > 0) address.append(", ");
            address.append(property.getAddressLine2());
        }
        if (property.getCity() != null && !property.getCity().isEmpty()) {
            if (address.length() > 0) address.append(", ");
            address.append(property.getCity());
        }
        if (property.getPostcode() != null && !property.getPostcode().isEmpty()) {
            if (address.length() > 0) address.append(" ");
            address.append(property.getPostcode());
        }
        return address.toString();
    }

    /**
     * Build customer address string.
     */
    private String buildCustomerAddress(Customer customer) {
        StringBuilder address = new StringBuilder();
        if (customer.getAddressLine1() != null) address.append(customer.getAddressLine1());
        if (customer.getAddressLine2() != null && !customer.getAddressLine2().isEmpty()) {
            if (address.length() > 0) address.append(", ");
            address.append(customer.getAddressLine2());
        }
        if (customer.getCity() != null && !customer.getCity().isEmpty()) {
            if (address.length() > 0) address.append(", ");
            address.append(customer.getCity());
        }
        if (customer.getPostcode() != null && !customer.getPostcode().isEmpty()) {
            if (address.length() > 0) address.append(" ");
            address.append(customer.getPostcode());
        }
        return address.toString();
    }

    /**
     * Escape HTML special characters.
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    /**
     * Format amount as GBP currency.
     */
    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "£0.00";
        return String.format("£%,.2f", amount);
    }
}

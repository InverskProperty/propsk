package site.easy.to.build.crm.service.servicecharge;

import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.dto.servicecharge.ServiceChargeStatementDTO;
import site.easy.to.build.crm.dto.servicecharge.ServiceChargeTransactionLineDTO;
import site.easy.to.build.crm.entity.Block;
import site.easy.to.build.crm.entity.Invoice;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.UnifiedTransaction;
import site.easy.to.build.crm.repository.BlockRepository;
import site.easy.to.build.crm.repository.InvoiceRepository;
import site.easy.to.build.crm.repository.PropertyRepository;
import site.easy.to.build.crm.repository.UnifiedTransactionRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for generating Service Charge Statements for block properties.
 *
 * Service charge statements show:
 * - Opening balance at period start
 * - All income (tenant payments) and expenses in date order
 * - Running balance after each transaction
 * - Closing balance at period end
 *
 * This uses unified_transactions table directly (not allocations)
 * to avoid double-counting and show actual money movements.
 */
@Service
public class ServiceChargeStatementService {

    private static final Logger log = LoggerFactory.getLogger(ServiceChargeStatementService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private UnifiedTransactionRepository unifiedTransactionRepository;

    /**
     * Get all blocks with block properties for the dropdown selector.
     *
     * @return List of blocks that have a block property
     */
    public List<Block> getBlocksWithBlockProperty() {
        return blockRepository.findAll().stream()
                .filter(b -> b.getBlockProperty() != null)
                .collect(Collectors.toList());
    }

    /**
     * Generate a Service Charge Statement for a block property.
     *
     * @param blockId     The block ID
     * @param periodStart Start date of the period
     * @param periodEnd   End date of the period
     * @return ServiceChargeStatementDTO with all statement data
     */
    public ServiceChargeStatementDTO generateStatement(Long blockId, LocalDate periodStart, LocalDate periodEnd) {
        log.info("Generating Service Charge Statement for block {} from {} to {}",
                blockId, periodStart, periodEnd);

        // Get the block and block property
        Block block = blockRepository.findById(blockId).orElse(null);
        if (block == null) {
            log.error("Block {} not found", blockId);
            return new ServiceChargeStatementDTO();
        }

        Property blockProperty = block.getBlockProperty();
        if (blockProperty == null) {
            log.error("Block {} has no block property", blockId);
            return new ServiceChargeStatementDTO();
        }

        // Get the lease for the block property
        Invoice lease = invoiceRepository.findByProperty(blockProperty).stream()
                .findFirst()
                .orElse(null);

        if (lease == null) {
            log.error("No lease found for block property {}", blockProperty.getId());
            return new ServiceChargeStatementDTO();
        }

        // Create the statement
        ServiceChargeStatementDTO statement = new ServiceChargeStatementDTO();
        statement.setBlockId(block.getId());
        statement.setBlockName(block.getName());
        statement.setBlockPropertyId(blockProperty.getId());
        statement.setBlockPropertyName(blockProperty.getPropertyName());
        statement.setLeaseId(lease.getId());
        statement.setPeriodStart(periodStart);
        statement.setPeriodEnd(periodEnd);
        statement.setStatementDate(LocalDate.now());

        // Get tenant name (service charge payer)
        String tenantName = extractTenantName(lease);
        statement.setServiceChargePayer(tenantName);

        // Calculate opening balance
        BigDecimal openingBalance = calculateOpeningBalance(lease.getId(), periodStart);
        statement.setOpeningBalance(openingBalance);

        // Get all transactions for the period and add them
        List<ServiceChargeTransactionLineDTO> transactions = getTransactionsForPeriod(
                lease.getId(), periodStart, periodEnd);

        for (ServiceChargeTransactionLineDTO txn : transactions) {
            statement.addTransaction(txn);
        }

        log.info("Statement generated: opening={}, income={}, expenses={}, closing={}",
                statement.getOpeningBalance(),
                statement.getTotalIncome(),
                statement.getTotalExpenses(),
                statement.getClosingBalance());

        return statement;
    }

    /**
     * Calculate opening balance (all transactions before period start).
     */
    private BigDecimal calculateOpeningBalance(Long leaseId, LocalDate periodStart) {
        log.info("Calculating opening balance for lease {} before {}", leaseId, periodStart);

        LocalDate beginningOfTime = LocalDate.of(2000, 1, 1);

        // Get all INCOMING before period start
        List<UnifiedTransaction> historicalIncome = unifiedTransactionRepository
                .findByInvoiceIdAndTransactionDateBetweenAndFlowDirection(
                        leaseId, beginningOfTime, periodStart.minusDays(1),
                        UnifiedTransaction.FlowDirection.INCOMING);

        BigDecimal totalIncome = historicalIncome.stream()
                .filter(this::isValidIncomeTransaction)
                .map(ut -> ut.getAmount() != null ? ut.getAmount().abs() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Get all OUTGOING before period start
        List<UnifiedTransaction> historicalExpenses = unifiedTransactionRepository
                .findByInvoiceIdAndTransactionDateBetweenAndFlowDirection(
                        leaseId, beginningOfTime, periodStart.minusDays(1),
                        UnifiedTransaction.FlowDirection.OUTGOING);

        BigDecimal totalExpenses = historicalExpenses.stream()
                .filter(this::isValidExpenseTransaction)
                .map(ut -> ut.getAmount() != null ? ut.getAmount().abs() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal openingBalance = totalIncome.subtract(totalExpenses);

        log.info("Opening balance: income={}, expenses={}, balance={}",
                totalIncome, totalExpenses, openingBalance);

        return openingBalance;
    }

    /**
     * Get all transactions for the period, sorted by date.
     */
    private List<ServiceChargeTransactionLineDTO> getTransactionsForPeriod(
            Long leaseId, LocalDate periodStart, LocalDate periodEnd) {

        List<ServiceChargeTransactionLineDTO> result = new ArrayList<>();

        // Get income transactions
        List<UnifiedTransaction> incomeTransactions = unifiedTransactionRepository
                .findByInvoiceIdAndTransactionDateBetweenAndFlowDirection(
                        leaseId, periodStart, periodEnd,
                        UnifiedTransaction.FlowDirection.INCOMING);

        for (UnifiedTransaction ut : incomeTransactions) {
            if (isValidIncomeTransaction(ut)) {
                result.add(ServiceChargeTransactionLineDTO.income(
                        ut.getId(),
                        ut.getTransactionDate(),
                        cleanDescription(ut.getDescription()),
                        formatCategory(ut.getCategory()),
                        ut.getAmount()
                ));
            }
        }

        // Get expense transactions
        List<UnifiedTransaction> expenseTransactions = unifiedTransactionRepository
                .findByInvoiceIdAndTransactionDateBetweenAndFlowDirection(
                        leaseId, periodStart, periodEnd,
                        UnifiedTransaction.FlowDirection.OUTGOING);

        for (UnifiedTransaction ut : expenseTransactions) {
            if (isValidExpenseTransaction(ut)) {
                result.add(ServiceChargeTransactionLineDTO.expense(
                        ut.getId(),
                        ut.getTransactionDate(),
                        cleanDescription(ut.getDescription()),
                        formatCategory(ut.getCategory()),
                        ut.getAmount()
                ));
            }
        }

        // Sort by date
        result.sort(Comparator.comparing(
                ServiceChargeTransactionLineDTO::getTransactionDate,
                Comparator.nullsLast(Comparator.naturalOrder())));

        return result;
    }

    /**
     * Check if a transaction is a valid income transaction.
     * Only include HISTORICAL or INCOMING_PAYMENT from PayProp.
     * Exclude property account withdrawals.
     */
    private boolean isValidIncomeTransaction(UnifiedTransaction ut) {
        // Only include HISTORICAL or INCOMING_PAYMENT
        boolean isHistorical = ut.getSourceSystem() == UnifiedTransaction.SourceSystem.HISTORICAL;
        boolean isPayPropIncomingPayment = "INCOMING_PAYMENT".equals(ut.getPaypropDataSource());

        if (!isHistorical && !isPayPropIncomingPayment) {
            return false;
        }

        // Exclude property account withdrawals
        String description = ut.getDescription();
        if (description != null && description.toLowerCase().contains("- property account -")) {
            return false;
        }

        return true;
    }

    /**
     * Check if a transaction is a valid expense transaction.
     * Exclude owner payments, commission, zero amounts, and agency payments with no category.
     */
    private boolean isValidExpenseTransaction(UnifiedTransaction ut) {
        // Skip zero amounts
        BigDecimal amount = ut.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }

        // Skip owner payments
        String category = ut.getCategory();
        if ("owner_payment".equalsIgnoreCase(category) || "Owner".equalsIgnoreCase(category)) {
            return false;
        }

        // Skip commission
        if ("Commission".equalsIgnoreCase(category)) {
            return false;
        }

        // Skip agency payments with no category
        String transactionType = ut.getTransactionType();
        if ("payment_to_agency".equalsIgnoreCase(transactionType) && category == null) {
            return false;
        }

        return true;
    }

    /**
     * Clean up transaction description for display.
     */
    private String cleanDescription(String description) {
        if (description == null) {
            return "";
        }

        // Remove tenant ID and deposit info from payment descriptions
        String cleaned = description;

        // Pattern: "Tenant Payment - X - Property (Tenant ID: xxx, Deposit: xxx)"
        // Extract just: "Tenant Payment - X"
        if (cleaned.contains("(Tenant ID:")) {
            int idx = cleaned.indexOf("(Tenant ID:");
            if (idx > 0) {
                cleaned = cleaned.substring(0, idx).trim();
            }
        }

        // Remove " - Block Property" suffix if present
        if (cleaned.endsWith(" - Block Property")) {
            cleaned = cleaned.substring(0, cleaned.length() - " - Block Property".length()).trim();
        }

        // Remove trailing " -" if present
        if (cleaned.endsWith(" -")) {
            cleaned = cleaned.substring(0, cleaned.length() - 2).trim();
        }

        return cleaned;
    }

    /**
     * Format category for display (capitalize first letter).
     */
    private String formatCategory(String category) {
        if (category == null || category.isEmpty()) {
            return "";
        }

        // Handle special cases
        if ("utilities".equalsIgnoreCase(category)) {
            return "Utilities";
        }
        if ("Rent".equalsIgnoreCase(category)) {
            return "Service Charge";
        }

        // Capitalize first letter
        return category.substring(0, 1).toUpperCase() + category.substring(1).toLowerCase();
    }

    /**
     * Extract tenant name from lease.
     * For block properties, the customer on the lease is the service charge payer.
     */
    private String extractTenantName(Invoice lease) {
        if (lease.getCustomer() != null) {
            return lease.getCustomer().getName();
        }
        if (lease.getLeaseReference() != null) {
            return lease.getLeaseReference();
        }
        return "Unknown";
    }

    /**
     * Export statement to PDF.
     */
    public byte[] exportToPdf(Long blockId, LocalDate periodStart, LocalDate periodEnd) throws IOException {
        ServiceChargeStatementDTO statement = generateStatement(blockId, periodStart, periodEnd);
        return generatePdf(statement);
    }

    /**
     * Generate PDF from statement data.
     */
    private byte[] generatePdf(ServiceChargeStatementDTO statement) throws IOException {
        String html = generatePdfHtml(statement);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        pdf.setDefaultPageSize(PageSize.A4);

        ConverterProperties props = new ConverterProperties();
        HtmlConverter.convertToPdf(html, pdf, props);

        return baos.toByteArray();
    }

    /**
     * Generate HTML for PDF export.
     */
    private String generatePdfHtml(ServiceChargeStatementDTO statement) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html><html><head>");
        html.append("<meta charset=\"UTF-8\">");
        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; font-size: 10pt; margin: 20px; }");
        html.append("h1 { font-size: 16pt; margin-bottom: 5px; }");
        html.append("h2 { font-size: 12pt; color: #333; margin-top: 20px; margin-bottom: 10px; }");
        html.append(".header { margin-bottom: 20px; }");
        html.append(".header-row { display: flex; justify-content: space-between; margin-bottom: 15px; }");
        html.append(".header-left { }");
        html.append(".header-right { text-align: right; }");
        html.append(".info-label { font-weight: bold; color: #666; }");
        html.append(".summary-box { background: #f5f5f5; padding: 15px; margin: 20px 0; border-radius: 5px; }");
        html.append(".summary-row { display: flex; justify-content: space-between; padding: 5px 0; }");
        html.append(".summary-total { border-top: 2px solid #333; font-weight: bold; margin-top: 10px; padding-top: 10px; }");
        html.append("table { width: 100%; border-collapse: collapse; margin-top: 10px; }");
        html.append("th { background: #333; color: white; padding: 8px; text-align: left; font-size: 9pt; }");
        html.append("th.amount { text-align: right; }");
        html.append("td { padding: 6px 8px; border-bottom: 1px solid #ddd; font-size: 9pt; }");
        html.append("td.amount { text-align: right; }");
        html.append("td.income { color: #28a745; }");
        html.append("td.expense { color: #dc3545; }");
        html.append("tr.opening-balance { background: #e3f2fd; font-weight: bold; }");
        html.append("tr.closing-balance { background: #e8f5e9; font-weight: bold; }");
        html.append("tr.totals { background: #fff3cd; font-weight: bold; border-top: 2px solid #333; }");
        html.append(".footer { margin-top: 30px; font-size: 8pt; color: #666; text-align: center; }");
        html.append("</style>");
        html.append("</head><body>");

        // Header
        html.append("<div class=\"header\">");
        html.append("<h1>Service Charge Statement</h1>");
        html.append("<div class=\"header-row\">");
        html.append("<div class=\"header-left\">");
        html.append("<p><span class=\"info-label\">Block:</span> ").append(escapeHtml(statement.getBlockName())).append("</p>");
        html.append("<p><span class=\"info-label\">Property:</span> ").append(escapeHtml(statement.getBlockPropertyName())).append("</p>");
        if (statement.getServiceChargePayer() != null) {
            html.append("<p><span class=\"info-label\">Service Charge Payer:</span> ").append(escapeHtml(statement.getServiceChargePayer())).append("</p>");
        }
        html.append("</div>");
        html.append("<div class=\"header-right\">");
        html.append("<p><span class=\"info-label\">Statement Date:</span> ").append(formatDate(statement.getStatementDate())).append("</p>");
        html.append("<p><span class=\"info-label\">Period:</span> ").append(formatDate(statement.getPeriodStart())).append(" - ").append(formatDate(statement.getPeriodEnd())).append("</p>");
        html.append("</div>");
        html.append("</div>");
        html.append("</div>");

        // Summary box
        html.append("<div class=\"summary-box\">");
        html.append("<h2 style=\"margin-top: 0;\">Summary</h2>");
        html.append("<div class=\"summary-row\"><span>Opening Balance</span><span>").append(formatCurrency(statement.getOpeningBalance())).append("</span></div>");
        html.append("<div class=\"summary-row\"><span>Total Income</span><span style=\"color: #28a745;\">").append(formatCurrency(statement.getTotalIncome())).append("</span></div>");
        html.append("<div class=\"summary-row\"><span>Total Expenses</span><span style=\"color: #dc3545;\">(").append(formatCurrency(statement.getTotalExpenses())).append(")</span></div>");
        html.append("<div class=\"summary-row summary-total\"><span>Closing Balance</span><span>").append(formatCurrency(statement.getClosingBalance())).append("</span></div>");
        html.append("</div>");

        // Transaction table
        html.append("<h2>Transaction Details</h2>");
        html.append("<table>");
        html.append("<thead><tr>");
        html.append("<th>Date</th>");
        html.append("<th>Description</th>");
        html.append("<th>Category</th>");
        html.append("<th class=\"amount\">In</th>");
        html.append("<th class=\"amount\">Out</th>");
        html.append("<th class=\"amount\">Balance</th>");
        html.append("</tr></thead>");
        html.append("<tbody>");

        // Opening balance row
        html.append("<tr class=\"opening-balance\">");
        html.append("<td></td>");
        html.append("<td>Opening Balance</td>");
        html.append("<td></td>");
        html.append("<td class=\"amount\"></td>");
        html.append("<td class=\"amount\"></td>");
        html.append("<td class=\"amount\">").append(formatCurrency(statement.getOpeningBalance())).append("</td>");
        html.append("</tr>");

        // Transaction rows
        for (ServiceChargeTransactionLineDTO txn : statement.getTransactions()) {
            html.append("<tr>");
            html.append("<td>").append(formatDate(txn.getTransactionDate())).append("</td>");
            html.append("<td>").append(escapeHtml(txn.getDescription())).append("</td>");
            html.append("<td>").append(escapeHtml(txn.getCategory())).append("</td>");

            if (txn.isIncome()) {
                html.append("<td class=\"amount income\">").append(formatCurrency(txn.getAmountIn())).append("</td>");
                html.append("<td class=\"amount\"></td>");
            } else {
                html.append("<td class=\"amount\"></td>");
                html.append("<td class=\"amount expense\">").append(formatCurrency(txn.getAmountOut())).append("</td>");
            }

            html.append("<td class=\"amount\">").append(formatCurrency(txn.getRunningBalance())).append("</td>");
            html.append("</tr>");
        }

        // Totals row
        html.append("<tr class=\"totals\">");
        html.append("<td></td>");
        html.append("<td>Totals</td>");
        html.append("<td></td>");
        html.append("<td class=\"amount income\">").append(formatCurrency(statement.getTotalIncome())).append("</td>");
        html.append("<td class=\"amount expense\">").append(formatCurrency(statement.getTotalExpenses())).append("</td>");
        html.append("<td class=\"amount\"></td>");
        html.append("</tr>");

        // Closing balance row
        html.append("<tr class=\"closing-balance\">");
        html.append("<td></td>");
        html.append("<td>Closing Balance</td>");
        html.append("<td></td>");
        html.append("<td class=\"amount\"></td>");
        html.append("<td class=\"amount\"></td>");
        html.append("<td class=\"amount\">").append(formatCurrency(statement.getClosingBalance())).append("</td>");
        html.append("</tr>");

        html.append("</tbody></table>");

        // Footer
        html.append("<div class=\"footer\">");
        html.append("<p>").append(statement.getAgencyName()).append(" | Reg: ").append(statement.getAgencyRegistrationNumber()).append("</p>");
        html.append("<p>").append(statement.getAgencyAddress()).append("</p>");
        html.append("</div>");

        html.append("</body></html>");

        return html.toString();
    }

    private String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FORMATTER) : "";
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) {
            return "£0.00";
        }
        return String.format("£%,.2f", amount);
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

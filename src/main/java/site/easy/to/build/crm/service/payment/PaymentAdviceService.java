package site.easy.to.build.crm.service.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.dto.paymentadvice.*;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.entity.PaymentBatch.BatchStatus;
import site.easy.to.build.crm.entity.UnifiedAllocation.AllocationType;
import site.easy.to.build.crm.repository.*;
import site.easy.to.build.crm.service.payment.TransactionBatchAllocationService;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.geom.PageSize;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating Payment Advice documents.
 * A Payment Advice shows a detailed breakdown of an owner payment (batch)
 * with per-property receipts and deductions.
 */
@Service
public class PaymentAdviceService {

    private static final Logger log = LoggerFactory.getLogger(PaymentAdviceService.class);

    @Autowired
    private PaymentBatchRepository paymentBatchRepository;

    @Autowired
    private UnifiedAllocationRepository unifiedAllocationRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private TransactionBatchAllocationRepository transactionBatchAllocationRepository;

    @Autowired
    private HistoricalTransactionRepository historicalTransactionRepository;

    /**
     * Generate Payment Advice for a specific batch.
     * Queries BOTH unified_allocations AND transaction_batch_allocations tables
     * to ensure all allocations are captured.
     *
     * @param batchId The payment batch ID
     * @return PaymentAdviceDTO with all data needed to render the advice
     */
    public PaymentAdviceDTO generatePaymentAdvice(String batchId) {
        log.info("Generating Payment Advice for batch: {}", batchId);

        // Load the payment batch
        PaymentBatch batch = paymentBatchRepository.findByBatchId(batchId)
            .orElseThrow(() -> new RuntimeException("Payment batch not found: " + batchId));

        // Create the DTO
        PaymentAdviceDTO advice = new PaymentAdviceDTO();
        advice.setBatchReference(batchId);
        advice.setAdviceDate(batch.getPaymentDate());
        advice.setTotalAmount(batch.getTotalPayment());
        advice.setPaymentMethod(batch.getPaymentMethod());
        advice.setPaymentReference(batch.getPaymentReference());
        advice.setStatus(batch.getStatus() != null ? batch.getStatus().name() : "UNKNOWN");

        // Set owner details
        if (batch.getBeneficiaryId() != null) {
            Customer owner = customerRepository.findById(batch.getBeneficiaryId()).orElse(null);
            if (owner != null) {
                advice.setOwnerId(owner.getCustomerId().longValue());
                advice.setOwnerName(batch.getBeneficiaryName() != null ? batch.getBeneficiaryName() : owner.getName());
                advice.setOwnerEmail(owner.getEmail());
                advice.setOwnerAddress(buildOwnerAddress(owner));
            } else {
                advice.setOwnerName(batch.getBeneficiaryName());
            }
        }

        // Get allocations from unified_allocations (primary source with gross/commission/net breakdown)
        List<UnifiedAllocation> unifiedAllocations = unifiedAllocationRepository.findByPaymentBatchId(batchId);

        log.info("Found {} unified allocations for batch {}", unifiedAllocations.size(), batchId);

        // Use unified_allocations as the single source of truth
        // It has gross_amount, commission_amount, and amount (net) pre-calculated
        if (!unifiedAllocations.isEmpty()) {
            log.info("Using UnifiedAllocation as data source (has gross/commission/net breakdown)");
            buildFromUnifiedAllocations(advice, unifiedAllocations);
        }

        // Sort properties by name for consistent display
        advice.getProperties().sort(Comparator.comparing(
            PropertyBreakdownDTO::getPropertyName,
            Comparator.nullsLast(Comparator.naturalOrder())
        ));

        // Update settlement summary
        advice.updateTotals();
        advice.setAmountSettled(batch.getTotalPayment());

        log.info("Generated Payment Advice: {} properties, total balance: {}",
            advice.getPropertyCount(), advice.getTotalBalance());

        return advice;
    }

    /**
     * Build property breakdowns from UnifiedAllocation data.
     */
    private void buildFromUnifiedAllocations(PaymentAdviceDTO advice, List<UnifiedAllocation> allocations) {
        // Group allocations by property
        Map<Long, List<UnifiedAllocation>> allocationsByProperty = allocations.stream()
            .filter(a -> a.getPropertyId() != null)
            .collect(Collectors.groupingBy(UnifiedAllocation::getPropertyId));

        // Build property breakdowns
        for (Map.Entry<Long, List<UnifiedAllocation>> entry : allocationsByProperty.entrySet()) {
            Long propertyId = entry.getKey();
            List<UnifiedAllocation> propertyAllocations = entry.getValue();

            PropertyBreakdownDTO propertyBreakdown = buildPropertyBreakdown(propertyId, propertyAllocations);
            if (propertyBreakdown.hasData()) {
                advice.addProperty(propertyBreakdown);
            }
        }
    }

    /**
     * Build property breakdowns from TransactionBatchAllocation data.
     * This is the same data source the UI uses.
     * Groups by property_name since many allocations have NULL property_id.
     */
    private void buildFromTransactionAllocations(PaymentAdviceDTO advice, List<TransactionBatchAllocation> allocations) {
        // Group allocations by property_name (not property_id, since many are NULL)
        Map<String, List<TransactionBatchAllocation>> allocationsByPropertyName = allocations.stream()
            .filter(a -> a.getPropertyName() != null && !a.getPropertyName().trim().isEmpty())
            .collect(Collectors.groupingBy(TransactionBatchAllocation::getPropertyName));

        // Build property breakdowns
        for (Map.Entry<String, List<TransactionBatchAllocation>> entry : allocationsByPropertyName.entrySet()) {
            String propertyName = entry.getKey();
            List<TransactionBatchAllocation> propertyAllocations = entry.getValue();

            // Get property_id from any allocation that has it (for linking purposes)
            Long propertyId = propertyAllocations.stream()
                .filter(a -> a.getPropertyId() != null)
                .map(TransactionBatchAllocation::getPropertyId)
                .findFirst()
                .orElse(null);

            PropertyBreakdownDTO propertyBreakdown = buildPropertyBreakdownFromTxnByName(propertyId, propertyName, propertyAllocations);
            if (propertyBreakdown.hasData()) {
                advice.addProperty(propertyBreakdown);
            }
        }
    }

    /**
     * Build property breakdown from TransactionBatchAllocation data.
     * Groups by property name since many allocations have NULL property_id.
     */
    private PropertyBreakdownDTO buildPropertyBreakdownFromTxnByName(Long propertyId, String propertyName, List<TransactionBatchAllocation> allocations) {
        PropertyBreakdownDTO breakdown = new PropertyBreakdownDTO(propertyId, propertyName);

        for (TransactionBatchAllocation allocation : allocations) {
            BigDecimal amount = allocation.getAllocatedAmount();

            if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                // Positive = income (receipt)
                ReceiptLineDTO receipt = buildReceiptLineFromTxn(allocation);
                breakdown.addReceipt(receipt);
            } else if (amount != null && amount.compareTo(BigDecimal.ZERO) < 0) {
                // Negative = expense (deduction)
                DeductionLineDTO deduction = buildDeductionLineFromTxn(allocation);
                breakdown.addDeduction(deduction);
            }
        }

        return breakdown;
    }

    /**
     * Build a receipt line from a TransactionBatchAllocation.
     * The allocated_amount IS the net amount (after commission).
     * We derive gross and commission from the transaction's commission rate if available.
     */
    private ReceiptLineDTO buildReceiptLineFromTxn(TransactionBatchAllocation allocation) {
        HistoricalTransaction txn = allocation.getTransaction();

        String tenantName = "Unknown Tenant";
        // The allocation's amount IS the net to owner (this is what was allocated from the batch)
        BigDecimal netAmount = allocation.getAllocatedAmount();
        BigDecimal grossAmount = netAmount; // Default to net if no commission info
        BigDecimal commissionAmount = BigDecimal.ZERO;
        java.time.LocalDate transactionDate = null;

        if (txn != null) {
            // Get tenant name
            if (txn.getTenant() != null) {
                tenantName = txn.getTenant().getName();
            }

            // Get transaction date
            transactionDate = txn.getTransactionDate();

            // Calculate gross and commission proportionally based on the transaction's breakdown
            // If the transaction has both gross and net, we can derive the commission rate
            BigDecimal txnGross = txn.getAmount();
            BigDecimal txnNet = txn.getNetToOwnerAmount();
            BigDecimal txnCommission = txn.getCommissionAmount();

            if (txnGross != null && txnNet != null && txnNet.compareTo(BigDecimal.ZERO) != 0) {
                // Calculate the ratio of commission to net
                // commission = gross - net, so gross = net + commission
                // For the allocation, we scale proportionally
                BigDecimal ratio = txnGross.divide(txnNet, 10, java.math.RoundingMode.HALF_UP);
                grossAmount = netAmount.multiply(ratio).setScale(2, java.math.RoundingMode.HALF_UP);
                commissionAmount = grossAmount.subtract(netAmount);
            } else if (txnGross != null && txnCommission != null && txnGross.compareTo(BigDecimal.ZERO) != 0) {
                // Use commission rate from transaction
                BigDecimal commissionRate = txnCommission.abs().divide(txnGross.abs(), 10, java.math.RoundingMode.HALF_UP);
                // For net amount, gross = net / (1 - commission_rate)
                BigDecimal oneMinusRate = BigDecimal.ONE.subtract(commissionRate);
                if (oneMinusRate.compareTo(BigDecimal.ZERO) > 0) {
                    grossAmount = netAmount.divide(oneMinusRate, 2, java.math.RoundingMode.HALF_UP);
                    commissionAmount = grossAmount.subtract(netAmount);
                }
            }
        }

        ReceiptLineDTO receipt = new ReceiptLineDTO();
        receipt.setTenantName(tenantName);
        receipt.setGrossAmount(grossAmount);
        receipt.setCommissionAmount(commissionAmount);
        receipt.setNetAmount(netAmount);
        receipt.setPaymentDate(transactionDate);

        return receipt;
    }

    /**
     * Build a deduction line from a TransactionBatchAllocation.
     * The allocation amount is negative in the database (for correct netting).
     * We display the absolute value but keep the negative for totalling.
     */
    private DeductionLineDTO buildDeductionLineFromTxn(TransactionBatchAllocation allocation) {
        HistoricalTransaction txn = allocation.getTransaction();

        BigDecimal amount = allocation.getAllocatedAmount(); // Will be negative
        java.time.LocalDate transactionDate = null;
        String description = allocation.getPropertyName();
        String category = null;

        if (txn != null) {
            transactionDate = txn.getTransactionDate();
            description = txn.getDescription();
            category = txn.getCategory();
        }

        String type = DeductionLineDTO.mapToDisplayType(null, category);

        DeductionLineDTO deduction = new DeductionLineDTO();
        deduction.setType(type);
        deduction.setDescription(description);
        // Display amount as positive (absolute value) but the grossAmount stays negative for correct totalling
        deduction.setNetAmount(amount != null ? amount.abs() : BigDecimal.ZERO);
        deduction.setVatAmount(BigDecimal.ZERO);
        // Keep the NEGATIVE amount for grossAmount - this is used in totalling
        // The balance calculation in PropertyBreakdownDTO does: netReceipts - deductions
        // Since deductions are negative, this becomes: netReceipts - (-expenses) = netReceipts + expenses
        // Actually we need POSITIVE for display AND correct sign for totalling
        // Let's use positive for grossAmount as well since we subtract in balance calculation
        deduction.setGrossAmount(amount != null ? amount.abs() : BigDecimal.ZERO);
        deduction.setCategory(category);
        deduction.setTransactionDate(transactionDate);

        return deduction;
    }

    /**
     * Get list of payment batches for an owner (for selection UI).
     *
     * @param ownerId The owner's customer ID
     * @return List of payment batches ordered by date descending
     */
    public List<PaymentBatch> getBatchesForOwner(Long ownerId) {
        log.info("Getting payment batches for owner: {}", ownerId);

        // Get all batches for this beneficiary, ordered by payment date desc
        List<PaymentBatch> batches = paymentBatchRepository.findByBeneficiaryIdOrderByPaymentDateDesc(ownerId);

        log.info("Found {} batches for owner {}", batches.size(), ownerId);
        return batches;
    }

    /**
     * Get PAID batches for an owner.
     */
    public List<PaymentBatch> getPaidBatchesForOwner(Long ownerId) {
        return paymentBatchRepository.findByBeneficiaryIdAndStatusOrderByPaymentDateDesc(
            ownerId, BatchStatus.PAID);
    }

    /**
     * Build property breakdown from allocations.
     */
    private PropertyBreakdownDTO buildPropertyBreakdown(Long propertyId, List<UnifiedAllocation> allocations) {
        // Get property name from first allocation or query property
        String propertyName = allocations.stream()
            .filter(a -> a.getPropertyName() != null)
            .map(UnifiedAllocation::getPropertyName)
            .findFirst()
            .orElse(null);

        if (propertyName == null) {
            Property property = propertyRepository.findById(propertyId).orElse(null);
            if (property != null) {
                propertyName = property.getPropertyName();
            }
        }

        PropertyBreakdownDTO breakdown = new PropertyBreakdownDTO(propertyId, propertyName);

        // Separate allocations by type
        for (UnifiedAllocation allocation : allocations) {
            AllocationType type = allocation.getAllocationType();

            if (type == AllocationType.OWNER) {
                // This is a receipt (rent received) - includes commission breakdown
                ReceiptLineDTO receipt = buildReceiptLine(allocation);
                breakdown.addReceipt(receipt);
            } else if (type == AllocationType.COMMISSION) {
                // SKIP commission allocations - commission is already shown in receipts table
                // via the commissionAmount field on OWNER allocations.
                // Including it here would double-count.
                continue;
            } else {
                // This is an expense or disbursement
                DeductionLineDTO deduction = buildDeductionLine(allocation);
                breakdown.addDeduction(deduction);
            }
        }

        return breakdown;
    }

    /**
     * Build a receipt line from an OWNER allocation.
     * Shows gross income, commission deducted, and net to owner - matching the Excel output.
     */
    private ReceiptLineDTO buildReceiptLine(UnifiedAllocation allocation) {
        // Get tenant name from invoice
        String tenantName = "Unknown Tenant";
        if (allocation.getInvoiceId() != null) {
            Invoice invoice = invoiceRepository.findById(allocation.getInvoiceId()).orElse(null);
            if (invoice != null) {
                tenantName = extractTenantName(invoice);
            }
        }

        // Get amounts - gross, commission, net
        BigDecimal grossAmount = allocation.getGrossAmount();
        BigDecimal commissionAmount = allocation.getCommissionAmount();
        BigDecimal netAmount;

        // If grossAmount is not set, use the allocation amount as gross (no commission breakdown)
        if (grossAmount == null) {
            grossAmount = allocation.getAmount();
            commissionAmount = BigDecimal.ZERO;
            netAmount = grossAmount;
        } else {
            // Ensure commission is not null
            if (commissionAmount == null) {
                commissionAmount = BigDecimal.ZERO;
            }
            // Calculate net as gross - commission
            // This is important because allocation.getAmount() from PayProp may already have
            // disbursements deducted, but we want net = gross - commission only.
            // Disbursements are shown separately in the deductions section.
            netAmount = grossAmount.subtract(commissionAmount);
        }

        // Get the actual transaction date (not the batch payment date)
        java.time.LocalDate transactionDate = allocation.getPaidDate(); // fallback
        if (allocation.getUnifiedTransaction() != null &&
            allocation.getUnifiedTransaction().getTransactionDate() != null) {
            transactionDate = allocation.getUnifiedTransaction().getTransactionDate();
        }

        ReceiptLineDTO receipt = new ReceiptLineDTO();
        receipt.setTenantName(tenantName);
        receipt.setGrossAmount(grossAmount);
        receipt.setCommissionAmount(commissionAmount);
        receipt.setNetAmount(netAmount);
        receipt.setPaymentDate(transactionDate);

        return receipt;
    }

    /**
     * Build a deduction line from an EXPENSE/DISBURSEMENT allocation.
     *
     * Amount sign convention:
     * - Positive amount = expense/deduction (reduces owner payment)
     * - Negative amount = reversal/credit (increases owner payment)
     *
     * The grossAmount preserves the sign so PropertyBreakdownDTO.calculateBalance()
     * correctly handles reversals (negative deductions reduce total deductions).
     */
    private DeductionLineDTO buildDeductionLine(UnifiedAllocation allocation) {
        BigDecimal amount = allocation.getAmount();

        String type = DeductionLineDTO.mapToDisplayType(
            allocation.getAllocationType() != null ? allocation.getAllocationType().name() : null,
            allocation.getCategory()
        );

        // Build description - indicate if this is a reversal
        String description = allocation.getDescription();
        if (description == null || description.trim().isEmpty()) {
            description = type + " - " + (allocation.getPropertyName() != null ? allocation.getPropertyName() : "");
        }

        // Add reversal indicator if amount is negative
        if (amount != null && amount.compareTo(BigDecimal.ZERO) < 0) {
            if (!description.toLowerCase().contains("reversal") && !description.toLowerCase().contains("credit")) {
                description = description + " (Reversal)";
            }
        }

        // Get the actual transaction date (not the batch payment date)
        java.time.LocalDate transactionDate = allocation.getPaidDate(); // fallback
        if (allocation.getUnifiedTransaction() != null &&
            allocation.getUnifiedTransaction().getTransactionDate() != null) {
            transactionDate = allocation.getUnifiedTransaction().getTransactionDate();
        }

        DeductionLineDTO deduction = new DeductionLineDTO();
        deduction.setType(type);
        deduction.setDescription(description);
        // Net and gross amounts preserve sign for correct balance calculation
        // Negative amounts = reversals/credits that reduce total deductions
        deduction.setNetAmount(amount != null ? amount : BigDecimal.ZERO);
        deduction.setVatAmount(BigDecimal.ZERO); // No VAT for now
        deduction.setGrossAmount(amount != null ? amount : BigDecimal.ZERO);
        deduction.setCategory(allocation.getCategory());
        deduction.setTransactionDate(transactionDate);

        return deduction;
    }

    /**
     * Extract tenant name from an invoice.
     */
    private String extractTenantName(Invoice invoice) {
        // PRIORITY 1: Use the customer linked directly to the invoice/lease
        Customer invoiceCustomer = invoice.getCustomer();
        if (invoiceCustomer != null && invoiceCustomer.getName() != null && !invoiceCustomer.getName().trim().isEmpty()) {
            return invoiceCustomer.getName().trim();
        }

        // FALLBACK 1: Get tenant from letting instruction
        if (invoice.getLettingInstruction() != null) {
            LettingInstruction instruction = invoice.getLettingInstruction();
            if (instruction.getTenant() != null) {
                Customer tenant = instruction.getTenant();
                String name = tenant.getName();
                if (name != null && !name.trim().isEmpty()) {
                    return name.trim();
                }
            }
        }

        // FALLBACK 2: Use lease reference
        if (invoice.getLeaseReference() != null) {
            return "Tenant (" + invoice.getLeaseReference() + ")";
        }

        return "Unknown Tenant";
    }

    /**
     * Build owner address from customer record.
     */
    private String buildOwnerAddress(Customer owner) {
        StringBuilder address = new StringBuilder();

        if (owner.getAddressLine1() != null && !owner.getAddressLine1().trim().isEmpty()) {
            address.append(owner.getAddressLine1().trim());
        }
        if (owner.getAddressLine2() != null && !owner.getAddressLine2().trim().isEmpty()) {
            if (address.length() > 0) address.append(", ");
            address.append(owner.getAddressLine2().trim());
        }
        if (owner.getCity() != null && !owner.getCity().trim().isEmpty()) {
            if (address.length() > 0) address.append(", ");
            address.append(owner.getCity().trim());
        }
        if (owner.getPostcode() != null && !owner.getPostcode().trim().isEmpty()) {
            if (address.length() > 0) address.append(", ");
            address.append(owner.getPostcode().trim());
        }
        if (owner.getCountry() != null && !owner.getCountry().trim().isEmpty()) {
            if (address.length() > 0) address.append(", ");
            address.append(owner.getCountry().trim());
        }

        return address.length() > 0 ? address.toString() : null;
    }

    /**
     * Export Payment Advice to Excel format.
     *
     * @param batchId The payment batch ID
     * @return byte array containing the Excel file
     */
    public byte[] exportToExcel(String batchId) throws IOException {
        log.info("Exporting Payment Advice to Excel for batch: {}", batchId);

        PaymentAdviceDTO advice = generatePaymentAdvice(batchId);

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Payment Advice");

            // Create styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            CellStyle boldStyle = createBoldStyle(workbook);
            CellStyle receiptStyle = createReceiptStyle(workbook);
            CellStyle deductionStyle = createDeductionStyle(workbook);

            int rowNum = 0;

            // Title
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("PAYMENT ADVICE");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));

            rowNum++; // Empty row

            // Header section
            Row toRow = sheet.createRow(rowNum++);
            toRow.createCell(0).setCellValue("To:");
            toRow.createCell(1).setCellValue(advice.getOwnerName() != null ? advice.getOwnerName() : "");

            Row fromRow = sheet.createRow(rowNum++);
            fromRow.createCell(0).setCellValue("From:");
            fromRow.createCell(1).setCellValue(advice.getAgencyName());

            Row dateRow = sheet.createRow(rowNum++);
            dateRow.createCell(0).setCellValue("Date:");
            dateRow.createCell(1).setCellValue(advice.getAdviceDate() != null ?
                advice.getAdviceDate().format(DateTimeFormatter.ISO_DATE) : "");

            Row refRow = sheet.createRow(rowNum++);
            refRow.createCell(0).setCellValue("Reference:");
            refRow.createCell(1).setCellValue(advice.getBatchReference());

            Row statusRow = sheet.createRow(rowNum++);
            statusRow.createCell(0).setCellValue("Status:");
            statusRow.createCell(1).setCellValue(advice.getStatus());

            rowNum++; // Empty row

            // Per-property breakdowns
            for (PropertyBreakdownDTO property : advice.getProperties()) {
                // Property header
                Row propertyHeaderRow = sheet.createRow(rowNum++);
                Cell propertyCell = propertyHeaderRow.createCell(0);
                propertyCell.setCellValue("Property: " + (property.getPropertyName() != null ? property.getPropertyName() : "Unknown"));
                propertyCell.setCellStyle(boldStyle);
                sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 4));

                // Receipts
                if (!property.getReceipts().isEmpty()) {
                    Row receiptsHeaderRow = sheet.createRow(rowNum++);
                    receiptsHeaderRow.createCell(0).setCellValue("RECEIPTS (Rent Received)");
                    receiptsHeaderRow.getCell(0).setCellStyle(headerStyle);

                    Row receiptsColsRow = sheet.createRow(rowNum++);
                    receiptsColsRow.createCell(0).setCellValue("Tenant");
                    receiptsColsRow.createCell(1).setCellValue("Date");
                    receiptsColsRow.createCell(2).setCellValue("Amount");

                    for (ReceiptLineDTO receipt : property.getReceipts()) {
                        Row receiptRow = sheet.createRow(rowNum++);
                        receiptRow.createCell(0).setCellValue(receipt.getTenantName() != null ? receipt.getTenantName() : "");
                        receiptRow.createCell(1).setCellValue(receipt.getPaymentDate() != null ?
                            receipt.getPaymentDate().format(DateTimeFormatter.ISO_DATE) : "");
                        Cell amountCell = receiptRow.createCell(2);
                        if (receipt.getAmount() != null) {
                            amountCell.setCellValue(receipt.getAmount().doubleValue());
                            amountCell.setCellStyle(currencyStyle);
                        }
                        receiptRow.getCell(0).setCellStyle(receiptStyle);
                    }

                    Row receiptsTotalRow = sheet.createRow(rowNum++);
                    receiptsTotalRow.createCell(1).setCellValue("Total Receipts:");
                    receiptsTotalRow.getCell(1).setCellStyle(boldStyle);
                    Cell receiptsTotalCell = receiptsTotalRow.createCell(2);
                    receiptsTotalCell.setCellValue(property.getTotalReceipts().doubleValue());
                    receiptsTotalCell.setCellStyle(currencyStyle);
                }

                // Deductions
                if (!property.getDeductions().isEmpty()) {
                    Row deductionsHeaderRow = sheet.createRow(rowNum++);
                    deductionsHeaderRow.createCell(0).setCellValue("DEDUCTIONS (Expenses)");
                    deductionsHeaderRow.getCell(0).setCellStyle(headerStyle);

                    Row deductionsColsRow = sheet.createRow(rowNum++);
                    deductionsColsRow.createCell(0).setCellValue("Type");
                    deductionsColsRow.createCell(1).setCellValue("Description");
                    deductionsColsRow.createCell(2).setCellValue("Net");
                    deductionsColsRow.createCell(3).setCellValue("VAT");
                    deductionsColsRow.createCell(4).setCellValue("Gross");

                    for (DeductionLineDTO deduction : property.getDeductions()) {
                        Row deductionRow = sheet.createRow(rowNum++);
                        deductionRow.createCell(0).setCellValue(deduction.getType() != null ? deduction.getType() : "");
                        deductionRow.createCell(1).setCellValue(deduction.getDescription() != null ? deduction.getDescription() : "");
                        Cell netCell = deductionRow.createCell(2);
                        if (deduction.getNetAmount() != null) {
                            netCell.setCellValue(deduction.getNetAmount().doubleValue());
                            netCell.setCellStyle(currencyStyle);
                        }
                        Cell vatCell = deductionRow.createCell(3);
                        if (deduction.getVatAmount() != null) {
                            vatCell.setCellValue(deduction.getVatAmount().doubleValue());
                            vatCell.setCellStyle(currencyStyle);
                        }
                        Cell grossCell = deductionRow.createCell(4);
                        if (deduction.getGrossAmount() != null) {
                            grossCell.setCellValue(deduction.getGrossAmount().doubleValue());
                            grossCell.setCellStyle(currencyStyle);
                        }
                        deductionRow.getCell(0).setCellStyle(deductionStyle);
                    }

                    Row deductionsTotalRow = sheet.createRow(rowNum++);
                    deductionsTotalRow.createCell(3).setCellValue("Total Deductions:");
                    deductionsTotalRow.getCell(3).setCellStyle(boldStyle);
                    Cell deductionsTotalCell = deductionsTotalRow.createCell(4);
                    deductionsTotalCell.setCellValue(property.getTotalDeductions().doubleValue());
                    deductionsTotalCell.setCellStyle(currencyStyle);
                }

                // Property balance
                Row balanceRow = sheet.createRow(rowNum++);
                balanceRow.createCell(3).setCellValue("Property Balance:");
                balanceRow.getCell(3).setCellStyle(boldStyle);
                Cell balanceCell = balanceRow.createCell(4);
                balanceCell.setCellValue(property.getBalance().doubleValue());
                balanceCell.setCellStyle(currencyStyle);

                rowNum++; // Empty row between properties
            }

            // Settlement summary
            rowNum++;
            Row summaryHeaderRow = sheet.createRow(rowNum++);
            summaryHeaderRow.createCell(0).setCellValue("SETTLEMENT SUMMARY");
            summaryHeaderRow.getCell(0).setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 4));

            Row totalReceiptsRow = sheet.createRow(rowNum++);
            totalReceiptsRow.createCell(3).setCellValue("Total Receipts:");
            Cell totalReceiptsCell = totalReceiptsRow.createCell(4);
            totalReceiptsCell.setCellValue(advice.getTotalReceipts().doubleValue());
            totalReceiptsCell.setCellStyle(currencyStyle);

            Row totalDeductionsRow = sheet.createRow(rowNum++);
            totalDeductionsRow.createCell(3).setCellValue("Total Deductions:");
            Cell totalDeductionsCell = totalDeductionsRow.createCell(4);
            totalDeductionsCell.setCellValue(advice.getTotalDeductions().doubleValue());
            totalDeductionsCell.setCellStyle(currencyStyle);

            Row totalBalanceRow = sheet.createRow(rowNum++);
            totalBalanceRow.createCell(3).setCellValue("Total Balance:");
            totalBalanceRow.getCell(3).setCellStyle(boldStyle);
            Cell totalBalanceCell = totalBalanceRow.createCell(4);
            totalBalanceCell.setCellValue(advice.getTotalBalance().doubleValue());
            totalBalanceCell.setCellStyle(currencyStyle);

            Row amountSettledRow = sheet.createRow(rowNum++);
            amountSettledRow.createCell(3).setCellValue("Amount Settled:");
            amountSettledRow.getCell(3).setCellStyle(boldStyle);
            Cell amountSettledCell = amountSettledRow.createCell(4);
            amountSettledCell.setCellValue(advice.getAmountSettled().doubleValue());
            amountSettledCell.setCellStyle(currencyStyle);

            // Auto-size columns
            for (int i = 0; i < 5; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            log.info("Excel export complete for batch: {}", batchId);
            return outputStream.toByteArray();
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        return style;
    }

    private CellStyle createCurrencyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("£#,##0.00"));
        return style;
    }

    private CellStyle createBoldStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle createReceiptStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createDeductionStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    /**
     * Export Payment Advice to PDF format.
     * Generates a standalone HTML document and converts it to PDF using iText html2pdf.
     *
     * @param batchId The payment batch ID
     * @return byte array containing the PDF file
     */
    public byte[] exportToPdf(String batchId) throws IOException {
        log.info("Exporting Payment Advice to PDF for batch: {}", batchId);

        PaymentAdviceDTO advice = generatePaymentAdvice(batchId);

        // Generate HTML content
        String htmlContent = generatePdfHtml(advice);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ConverterProperties properties = new ConverterProperties();
            HtmlConverter.convertToPdf(htmlContent, outputStream, properties);

            log.info("PDF export complete for batch: {}", batchId);
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("Error generating PDF for batch {}: {}", batchId, e.getMessage(), e);
            throw new IOException("Failed to generate PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Generate standalone HTML for PDF conversion.
     * This creates a self-contained HTML document with embedded CSS.
     */
    private String generatePdfHtml(PaymentAdviceDTO advice) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>\n");
        html.append("<html><head>\n");
        html.append("<meta charset=\"UTF-8\"/>\n");
        html.append("<style>\n");
        html.append("body { font-family: Arial, sans-serif; font-size: 11px; margin: 20px; color: #333; }\n");
        html.append(".header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 20px; border-radius: 8px; margin-bottom: 20px; }\n");
        html.append(".header h1 { margin: 0 0 10px 0; font-size: 24px; }\n");
        html.append(".header-row { display: flex; justify-content: space-between; }\n");
        html.append(".header-left, .header-right { width: 48%; }\n");
        html.append(".header-right { text-align: right; }\n");
        html.append(".property-section { border: 1px solid #ddd; border-left: 4px solid #667eea; margin-bottom: 15px; border-radius: 4px; }\n");
        html.append(".property-header { background: #f5f5f5; padding: 10px; font-weight: bold; font-size: 13px; }\n");
        html.append(".section-title { background: #e8f5e9; padding: 8px 10px; font-weight: bold; color: #2e7d32; font-size: 11px; }\n");
        html.append(".section-title.expenses { background: #ffebee; color: #c62828; }\n");
        html.append("table { width: 100%; border-collapse: collapse; font-size: 10px; }\n");
        html.append("th { background: #f5f5f5; padding: 6px 8px; text-align: left; border-bottom: 1px solid #ddd; }\n");
        html.append("td { padding: 6px 8px; border-bottom: 1px solid #eee; }\n");
        html.append(".text-right { text-align: right; }\n");
        html.append(".text-success { color: #2e7d32; }\n");
        html.append(".text-danger { color: #c62828; }\n");
        html.append(".receipt-row { background: #e8f5e9; }\n");
        html.append(".deduction-row { background: #ffebee; }\n");
        html.append(".total-row { background: #e3f2fd; font-weight: bold; }\n");
        html.append(".total-row-green { background: #c8e6c9; font-weight: bold; }\n");
        html.append(".total-row-red { background: #ffcdd2; font-weight: bold; }\n");
        html.append(".settlement-summary { background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%); color: white; padding: 20px; border-radius: 8px; margin-top: 20px; }\n");
        html.append(".settlement-summary h2 { margin: 0 0 15px 0; font-size: 18px; }\n");
        html.append(".settlement-table { width: 100%; }\n");
        html.append(".settlement-table td { padding: 5px 0; border: none; color: white; }\n");
        html.append(".amount-settled { font-size: 28px; font-weight: bold; text-align: right; }\n");
        html.append(".footer { text-align: center; margin-top: 20px; font-size: 10px; color: #666; }\n");
        html.append(".badge { display: inline-block; padding: 2px 6px; border-radius: 3px; font-size: 9px; }\n");
        html.append(".badge-paid { background: #c8e6c9; color: #2e7d32; }\n");
        html.append(".badge-pending { background: #fff3e0; color: #e65100; }\n");
        html.append("</style>\n");
        html.append("</head><body>\n");

        // Header
        html.append("<div class=\"header\">\n");
        html.append("<div class=\"header-row\">\n");
        html.append("<div class=\"header-left\">\n");
        html.append("<h1>PAYMENT ADVICE</h1>\n");
        html.append("<p><strong>To:</strong><br/>");
        html.append(escapeHtml(advice.getOwnerName() != null ? advice.getOwnerName() : ""));
        if (advice.getOwnerAddress() != null) {
            html.append("<br/>").append(escapeHtml(advice.getOwnerAddress()));
        }
        if (advice.getOwnerEmail() != null) {
            html.append("<br/>").append(escapeHtml(advice.getOwnerEmail()));
        }
        html.append("</p>\n");
        html.append("</div>\n");
        html.append("<div class=\"header-right\">\n");
        html.append("<p><strong>From:</strong><br/>");
        html.append(escapeHtml(advice.getAgencyName())).append("<br/>");
        html.append("<small>Company No: ").append(escapeHtml(advice.getAgencyRegistrationNumber())).append("</small></p>\n");
        html.append("<p><strong>Date:</strong> ").append(advice.getAdviceDate() != null ? advice.getAdviceDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy")) : "").append("<br/>");
        html.append("<strong>Reference:</strong> ").append(escapeHtml(advice.getBatchReference())).append("<br/>");
        html.append("<strong>Status:</strong> <span class=\"badge badge-").append("PAID".equals(advice.getStatus()) ? "paid" : "pending").append("\">");
        html.append(escapeHtml(advice.getStatus())).append("</span></p>\n");
        html.append("</div>\n");
        html.append("</div>\n");
        html.append("</div>\n");

        // Property breakdowns
        for (PropertyBreakdownDTO property : advice.getProperties()) {
            html.append("<div class=\"property-section\">\n");
            html.append("<div class=\"property-header\">&#127968; ").append(escapeHtml(property.getPropertyName() != null ? property.getPropertyName() : "Unknown Property")).append("</div>\n");

            // Receipts section
            if (!property.getReceipts().isEmpty()) {
                html.append("<div class=\"section-title\">&#8595; Receipts (Rent Received)</div>\n");
                html.append("<table>\n");
                html.append("<tr><th>Tenant</th><th>Date</th><th class=\"text-right\">Gross</th><th class=\"text-right\">Commission</th><th class=\"text-right\">Net</th></tr>\n");

                for (ReceiptLineDTO receipt : property.getReceipts()) {
                    html.append("<tr class=\"receipt-row\">");
                    html.append("<td>").append(escapeHtml(receipt.getTenantName() != null ? receipt.getTenantName() : "")).append("</td>");
                    html.append("<td>").append(receipt.getPaymentDate() != null ? receipt.getPaymentDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "").append("</td>");
                    html.append("<td class=\"text-right\">").append(formatCurrency(receipt.getGrossAmount())).append("</td>");
                    html.append("<td class=\"text-right text-danger\">").append(formatCurrency(receipt.getCommissionAmount())).append("</td>");
                    html.append("<td class=\"text-right text-success\"><strong>").append(formatCurrency(receipt.getNetAmount())).append("</strong></td>");
                    html.append("</tr>\n");
                }

                html.append("<tr class=\"total-row-green\">");
                html.append("<td colspan=\"2\"><strong>Total Receipts</strong></td>");
                html.append("<td class=\"text-right\"><strong>").append(formatCurrency(property.getTotalGrossReceipts())).append("</strong></td>");
                html.append("<td class=\"text-right\"><strong>").append(formatCurrency(property.getTotalCommission())).append("</strong></td>");
                html.append("<td class=\"text-right\"><strong>").append(formatCurrency(property.getTotalNetReceipts())).append("</strong></td>");
                html.append("</tr>\n");
                html.append("</table>\n");
            }

            // Deductions section
            if (!property.getDeductions().isEmpty()) {
                html.append("<div class=\"section-title expenses\">&#8593; Expenses &amp; Disbursements</div>\n");
                html.append("<table>\n");
                html.append("<tr><th>Type</th><th>Date</th><th>Description</th><th class=\"text-right\">Amount</th></tr>\n");

                for (DeductionLineDTO deduction : property.getDeductions()) {
                    boolean isReversal = deduction.getGrossAmount() != null && deduction.getGrossAmount().compareTo(BigDecimal.ZERO) < 0;
                    html.append("<tr class=\"").append(isReversal ? "receipt-row" : "deduction-row").append("\">");
                    html.append("<td>").append(escapeHtml(deduction.getType() != null ? deduction.getType() : "")).append("</td>");
                    html.append("<td>").append(deduction.getTransactionDate() != null ? deduction.getTransactionDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "").append("</td>");
                    html.append("<td>").append(escapeHtml(deduction.getDescription() != null ? deduction.getDescription() : "")).append("</td>");
                    html.append("<td class=\"text-right ").append(isReversal ? "text-success" : "text-danger").append("\"><strong>");
                    html.append(formatCurrency(deduction.getGrossAmount())).append("</strong></td>");
                    html.append("</tr>\n");
                }

                html.append("<tr class=\"total-row-red\">");
                html.append("<td colspan=\"3\"><strong>Total Expenses</strong></td>");
                html.append("<td class=\"text-right\"><strong>").append(formatCurrency(property.getTotalDeductions())).append("</strong></td>");
                html.append("</tr>\n");
                html.append("</table>\n");
            }

            // Property balance
            html.append("<table>\n");
            html.append("<tr class=\"total-row\">");
            html.append("<td colspan=\"3\"><strong>Property Balance (Net Receipts - Expenses)</strong></td>");
            html.append("<td class=\"text-right\"><strong class=\"").append(property.getBalance().compareTo(BigDecimal.ZERO) >= 0 ? "text-success" : "text-danger").append("\">");
            html.append(formatCurrency(property.getBalance())).append("</strong></td>");
            html.append("</tr>\n");
            html.append("</table>\n");

            html.append("</div>\n");
        }

        // Settlement summary
        html.append("<div class=\"settlement-summary\">\n");
        html.append("<h2>Settlement Summary</h2>\n");
        html.append("<table class=\"settlement-table\">\n");
        html.append("<tr><td>Gross Income:</td><td class=\"text-right\">").append(formatCurrency(advice.getTotalGrossReceipts())).append("</td></tr>\n");
        html.append("<tr><td>Less Commission:</td><td class=\"text-right\">(").append(formatCurrency(advice.getTotalCommission())).append(")</td></tr>\n");
        html.append("<tr><td>Less Expenses:</td><td class=\"text-right\">(").append(formatCurrency(advice.getTotalExpenses())).append(")</td></tr>\n");
        html.append("<tr style=\"border-top: 1px solid rgba(255,255,255,0.3);\"><td><strong>Net to Owner:</strong></td><td class=\"text-right\"><strong>").append(formatCurrency(advice.getTotalBalance())).append("</strong></td></tr>\n");
        html.append("</table>\n");
        html.append("<div class=\"amount-settled\">Amount Settled: ").append(formatCurrency(advice.getAmountSettled())).append("</div>\n");
        if (advice.getPaymentMethod() != null) {
            html.append("<p style=\"text-align: right; margin: 5px 0 0 0;\"><small>via ").append(escapeHtml(advice.getPaymentMethod())).append("</small></p>\n");
        }
        if (advice.getPaymentReference() != null) {
            html.append("<p style=\"text-align: right; margin: 0;\"><small>Ref: ").append(escapeHtml(advice.getPaymentReference())).append("</small></p>\n");
        }
        html.append("</div>\n");

        // Footer
        html.append("<div class=\"footer\">\n");
        html.append("<p>This payment advice was generated by ").append(escapeHtml(advice.getAgencyName())).append(". If you have any questions, please contact us.</p>\n");
        html.append("</div>\n");

        html.append("</body></html>");

        return html.toString();
    }

    /**
     * Escape HTML special characters for safe output.
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
     * Format a BigDecimal as GBP currency.
     */
    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "£0.00";
        return String.format("£%,.2f", amount);
    }
}

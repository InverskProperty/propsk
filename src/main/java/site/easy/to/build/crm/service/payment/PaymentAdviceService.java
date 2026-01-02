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

        // Get allocations from BOTH sources
        List<UnifiedAllocation> unifiedAllocations = unifiedAllocationRepository.findByPaymentBatchId(batchId);
        List<TransactionBatchAllocation> txnAllocations = transactionBatchAllocationRepository.findByBatchReference(batchId);

        log.info("Found {} unified allocations and {} transaction allocations for batch {}",
            unifiedAllocations.size(), txnAllocations.size(), batchId);

        // ALWAYS prefer transaction_batch_allocations when it has data
        // because it stores expenses as negative amounts (correct sign convention)
        // while unified_allocations stores all amounts as positive (incorrect for netting)
        if (!txnAllocations.isEmpty()) {
            log.info("Using TransactionBatchAllocation as primary source (correct sign convention)");
            buildFromTransactionAllocations(advice, txnAllocations);
        } else if (!unifiedAllocations.isEmpty()) {
            log.info("Using UnifiedAllocation as fallback (no transaction allocations found)");
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
     */
    private void buildFromTransactionAllocations(PaymentAdviceDTO advice, List<TransactionBatchAllocation> allocations) {
        // Group allocations by property
        Map<Long, List<TransactionBatchAllocation>> allocationsByProperty = allocations.stream()
            .filter(a -> a.getPropertyId() != null)
            .collect(Collectors.groupingBy(TransactionBatchAllocation::getPropertyId));

        // Build property breakdowns
        for (Map.Entry<Long, List<TransactionBatchAllocation>> entry : allocationsByProperty.entrySet()) {
            Long propertyId = entry.getKey();
            List<TransactionBatchAllocation> propertyAllocations = entry.getValue();

            PropertyBreakdownDTO propertyBreakdown = buildPropertyBreakdownFromTxn(propertyId, propertyAllocations);
            if (propertyBreakdown.hasData()) {
                advice.addProperty(propertyBreakdown);
            }
        }
    }

    /**
     * Build property breakdown from TransactionBatchAllocation data.
     */
    private PropertyBreakdownDTO buildPropertyBreakdownFromTxn(Long propertyId, List<TransactionBatchAllocation> allocations) {
        String propertyName = allocations.stream()
            .filter(a -> a.getPropertyName() != null)
            .map(TransactionBatchAllocation::getPropertyName)
            .findFirst()
            .orElse(null);

        if (propertyName == null) {
            Property property = propertyRepository.findById(propertyId).orElse(null);
            if (property != null) {
                propertyName = property.getPropertyName();
            }
        }

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
     */
    private ReceiptLineDTO buildReceiptLineFromTxn(TransactionBatchAllocation allocation) {
        HistoricalTransaction txn = allocation.getTransaction();

        String tenantName = "Unknown Tenant";
        BigDecimal grossAmount = allocation.getAllocatedAmount();
        BigDecimal commissionAmount = BigDecimal.ZERO;
        BigDecimal netAmount = allocation.getAllocatedAmount();
        java.time.LocalDate transactionDate = null;

        if (txn != null) {
            // Get tenant name
            if (txn.getTenant() != null) {
                tenantName = txn.getTenant().getName();
            }

            // Get transaction date
            transactionDate = txn.getTransactionDate();

            // Get gross and commission from the transaction if available
            if (txn.getAmount() != null) {
                grossAmount = txn.getAmount().abs();
            }
            if (txn.getCommissionAmount() != null) {
                commissionAmount = txn.getCommissionAmount().abs();
            }
            if (txn.getNetToOwnerAmount() != null) {
                netAmount = txn.getNetToOwnerAmount();
            } else {
                netAmount = allocation.getAllocatedAmount();
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
        deduction.setNetAmount(amount);
        deduction.setVatAmount(BigDecimal.ZERO);
        deduction.setGrossAmount(amount); // Keep sign for correct totalling
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
        BigDecimal netAmount = allocation.getAmount();

        // If grossAmount is not set, use the net amount as gross (no commission breakdown)
        if (grossAmount == null) {
            grossAmount = netAmount;
            commissionAmount = BigDecimal.ZERO;
        }

        // Ensure commission is not null
        if (commissionAmount == null) {
            commissionAmount = BigDecimal.ZERO;
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
     * Build a deduction line from an EXPENSE/COMMISSION/DISBURSEMENT allocation.
     * Handles negative amounts (reversals) correctly.
     */
    private DeductionLineDTO buildDeductionLine(UnifiedAllocation allocation) {
        BigDecimal amount = allocation.getAmount();
        boolean isReversal = amount != null && amount.compareTo(BigDecimal.ZERO) < 0;

        String type = DeductionLineDTO.mapToDisplayType(
            allocation.getAllocationType() != null ? allocation.getAllocationType().name() : null,
            allocation.getCategory()
        );

        // Mark reversals in the type
        if (isReversal) {
            type = type + " (Reversal)";
        }

        // Build description
        String description = allocation.getDescription();
        if (description == null || description.trim().isEmpty()) {
            description = type + " - " + (allocation.getPropertyName() != null ? allocation.getPropertyName() : "");
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
        deduction.setNetAmount(amount);
        deduction.setVatAmount(BigDecimal.ZERO); // No VAT for now
        deduction.setGrossAmount(amount); // Keep sign for correct totalling
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
}

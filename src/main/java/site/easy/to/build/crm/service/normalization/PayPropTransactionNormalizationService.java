package site.easy.to.build.crm.service.normalization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import site.easy.to.build.crm.entity.FinancialTransaction;
import site.easy.to.build.crm.entity.HistoricalTransaction;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.repository.HistoricalTransactionRepository;
import site.easy.to.build.crm.repository.PropertyRepository;

import java.math.BigDecimal;

/**
 * PayProp Transaction Normalization Service
 *
 * Converts FinancialTransaction entities (from PayProp API) into HistoricalTransaction
 * entities (canonical format) for storage in the unified transactions table.
 *
 * This is the adapter layer between PayProp API sync and the canonical transaction store.
 */
@Service
@Transactional
public class PayPropTransactionNormalizationService {

    private static final Logger logger = LoggerFactory.getLogger(PayPropTransactionNormalizationService.class);

    @Autowired
    private HistoricalTransactionRepository historicalTransactionRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    /**
     * Normalize and save a PayProp transaction to the canonical historical_transactions table
     *
     * @param financialTransaction The PayProp transaction from API
     * @return true if saved successfully, false if duplicate or failed
     */
    public boolean normalizeAndSave(FinancialTransaction financialTransaction) {
        try {
            // Check for duplicates using PayProp transaction ID
            if (financialTransaction.getPayPropTransactionId() != null) {
                boolean exists = historicalTransactionRepository
                    .existsByPaypropTransactionId(financialTransaction.getPayPropTransactionId());
                if (exists) {
                    logger.debug("Skipping duplicate PayProp transaction: {}",
                               financialTransaction.getPayPropTransactionId());
                    return false;
                }
            }

            // Convert to HistoricalTransaction
            HistoricalTransaction historical = convertToHistoricalTransaction(financialTransaction);

            // Save to unified table
            historicalTransactionRepository.save(historical);

            logger.debug("Normalized and saved PayProp transaction: {}",
                        financialTransaction.getPayPropTransactionId());
            return true;

        } catch (Exception e) {
            logger.error("Failed to normalize PayProp transaction {}: {}",
                        financialTransaction.getPayPropTransactionId(),
                        e.getMessage());
            return false;
        }
    }

    /**
     * Convert FinancialTransaction to HistoricalTransaction
     */
    private HistoricalTransaction convertToHistoricalTransaction(FinancialTransaction ft) {
        HistoricalTransaction ht = new HistoricalTransaction();

        // Core transaction fields
        ht.setTransactionDate(ft.getTransactionDate());
        ht.setAmount(ft.getAmount());
        ht.setDescription(ft.getDescription() != null ? ft.getDescription() : "");
        ht.setTransactionType(mapTransactionType(ft.getTransactionType()));

        // PayProp integration fields
        ht.setPaypropTransactionId(ft.getPayPropTransactionId());
        ht.setPaypropPropertyId(ft.getPropertyId());
        ht.setPaypropTenantId(ft.getTenantId());
        ht.setPaypropCategoryId(ft.getCategoryId());

        // Commission and fee tracking
        ht.setCommissionRate(ft.getCommissionRate());
        ht.setCommissionAmount(ft.getCommissionAmount());
        ht.setServiceFeeAmount(ft.getServiceFeeAmount());
        ht.setNetToOwnerAmount(ft.getNetToOwnerAmount());

        // Instruction tracking
        ht.setIsInstruction(ft.getIsInstruction());
        ht.setIsActualTransaction(ft.getIsActualTransaction());
        ht.setInstructionId(ft.getInstructionId());
        ht.setInstructionDate(ft.getInstructionDate());

        // Batch payment
        ht.setBatchPaymentId(ft.getBatchPayment() != null ? ft.getBatchPayment().getId() : null);
        ht.setPaypropBatchId(ft.getPayPropBatchId());
        ht.setBatchSequenceNumber(ft.getBatchSequenceNumber());

        // Additional fields
        ht.setDepositId(ft.getDepositId());
        ht.setReference(ft.getPayPropTransactionId()); // Use PayProp ID as reference

        // Tax fields
        ht.setTaxRelevant(ft.getHasTax() != null && ft.getHasTax());
        ht.setVatApplicable(ft.getTaxAmount() != null && ft.getTaxAmount().compareTo(BigDecimal.ZERO) > 0);
        ht.setVatAmount(ft.getTaxAmount());

        // Source tracking
        ht.setSource(HistoricalTransaction.TransactionSource.api_sync);
        ht.setAccountSource("propsk_payprop"); // All PayProp API data is PayProp account
        ht.setSourceReference(ft.getPayPropTransactionId());

        // Category
        ht.setCategory(ft.getCategoryName());

        // Map to internal property if available
        if (ft.getPropertyId() != null) {
            Property property = propertyRepository.findByPayPropId(ft.getPropertyId()).orElse(null);
            if (property != null) {
                ht.setProperty(property);
            } else {
                logger.warn("Property not found for PayProp ID: {}", ft.getPropertyId());
            }
        }

        // Payment details
        ht.setCounterpartyName(ft.getTenantName());

        // Reconciliation
        if (ft.getReconciliationDate() != null) {
            ht.setReconciled(true);
            ht.setReconciledDate(ft.getReconciliationDate());
        }

        return ht;
    }

    /**
     * Map FinancialTransaction.transactionType (String) to HistoricalTransaction.TransactionType (Enum)
     */
    private HistoricalTransaction.TransactionType mapTransactionType(String transactionType) {
        if (transactionType == null) {
            return HistoricalTransaction.TransactionType.payment;
        }

        switch (transactionType.toLowerCase()) {
            case "invoice":
                return HistoricalTransaction.TransactionType.invoice;
            case "credit_note":
                return HistoricalTransaction.TransactionType.refund;
            case "debit_note":
                return HistoricalTransaction.TransactionType.expense;
            case "payment":
                return HistoricalTransaction.TransactionType.payment;
            case "fee":
                return HistoricalTransaction.TransactionType.fee;
            case "transfer":
                return HistoricalTransaction.TransactionType.transfer;
            case "deposit":
                return HistoricalTransaction.TransactionType.deposit;
            case "withdrawal":
                return HistoricalTransaction.TransactionType.withdrawal;
            default:
                logger.warn("Unknown transaction type '{}', defaulting to payment", transactionType);
                return HistoricalTransaction.TransactionType.payment;
        }
    }
}

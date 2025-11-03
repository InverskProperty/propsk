package site.easy.to.build.crm.service.statements;

import org.springframework.stereotype.Service;
import site.easy.to.build.crm.dto.StatementTransactionDto;
import site.easy.to.build.crm.dto.StatementTransactionDto.TransactionSource;
import site.easy.to.build.crm.entity.HistoricalTransaction;
import site.easy.to.build.crm.entity.UnifiedTransaction;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Converter Service for Statement Transactions
 *
 * Converts HistoricalTransaction entities to StatementTransactionDto
 * for unified statement processing alongside PayProp transactions.
 */
@Service
public class StatementTransactionConverter {

    /**
     * Convert a single HistoricalTransaction to StatementTransactionDto
     */
    public StatementTransactionDto convertHistoricalToDto(HistoricalTransaction tx) {
        StatementTransactionDto dto = new StatementTransactionDto();

        // Core transaction fields
        dto.setTransactionDate(tx.getTransactionDate());
        dto.setAmount(tx.getAmount());
        dto.setDescription(tx.getDescription());
        dto.setTransactionType(tx.getTransactionType() != null ? tx.getTransactionType().name() : null);
        dto.setCategory(tx.getCategory());
        dto.setSubcategory(tx.getSubcategory());

        // Source identification
        dto.setSource(TransactionSource.HISTORICAL);
        dto.setSourceTransactionId(tx.getId() != null ? tx.getId().toString() : null);
        dto.setAccountSource(tx.getAccountSource());

        // Property linking
        if (tx.getProperty() != null) {
            dto.setPropertyId(tx.getProperty().getId());
            dto.setPropertyPayPropId(tx.getProperty().getPayPropId());
            dto.setPropertyName(tx.getProperty().getPropertyName());
        } else if (tx.getPaypropPropertyId() != null) {
            dto.setPropertyPayPropId(tx.getPaypropPropertyId());
        }

        // Customer linking
        if (tx.getCustomer() != null) {
            dto.setCustomerId(tx.getCustomer().getCustomerId());
            dto.setCustomerPayPropId(tx.getCustomer().getPayPropCustomerId());
            dto.setCustomerName(tx.getCustomer().getName());
        }

        // Beneficiary information
        if (tx.getBeneficiary() != null) {
            dto.setBeneficiaryId(tx.getBeneficiary().getCustomerId());
            dto.setBeneficiaryPayPropId(tx.getBeneficiary().getPayPropCustomerId());
            dto.setBeneficiaryName(tx.getBeneficiary().getName());
        } else if (tx.getBeneficiaryName() != null) {
            dto.setBeneficiaryName(tx.getBeneficiaryName());
        }
        dto.setBeneficiaryType(tx.getBeneficiaryType());

        // Tenant information
        if (tx.getTenant() != null) {
            dto.setTenantId(tx.getTenant().getCustomerId());
            dto.setTenantPayPropId(tx.getTenant().getPayPropCustomerId());
            dto.setTenantName(tx.getTenant().getName());
        } else if (tx.getPaypropTenantId() != null) {
            dto.setTenantPayPropId(tx.getPaypropTenantId());
        }

        // Owner information
        if (tx.getOwner() != null) {
            dto.setOwnerId(tx.getOwner().getCustomerId());
            dto.setOwnerPayPropId(tx.getOwner().getPayPropCustomerId());
            dto.setOwnerName(tx.getOwner().getName());
        }

        // Commission and fee tracking
        dto.setCommissionRate(tx.getCommissionRate());
        dto.setCommissionAmount(tx.getCommissionAmount());
        dto.setServiceFeeRate(tx.getServiceFeeRate());
        dto.setServiceFeeAmount(tx.getServiceFeeAmount());
        dto.setTransactionFee(tx.getTransactionFee());
        dto.setNetToOwnerAmount(tx.getNetToOwnerAmount());

        // Batch payment tracking
        if (tx.getBatchPaymentId() != null) {
            dto.setBatchPaymentId(tx.getBatchPaymentId().toString());
        }
        dto.setPaypropBatchId(tx.getPaypropBatchId());

        // Incoming transaction tracking
        dto.setIncomingTransactionId(tx.getIncomingTransactionId());
        dto.setIncomingTransactionAmount(tx.getIncomingTransactionAmount());

        // Lease/Invoice reference
        if (tx.getInvoice() != null) {
            dto.setInvoiceId(tx.getInvoice().getId());
            dto.setLeaseReference(tx.getInvoice().getLeaseReference());
            dto.setPaypropInvoiceId(tx.getInvoice().getPaypropId());
        }

        // Add lease period information if available
        if (tx.getLeaseStartDate() != null || tx.getLeaseEndDate() != null) {
            // Store in notes if needed for statement display
            if (dto.getNotes() == null) {
                dto.setNotes("");
            }
        }

        // Bank and payment details
        dto.setBankReference(tx.getBankReference());
        dto.setPaymentMethod(tx.getPaymentMethod());
        dto.setReference(tx.getReference());

        // VAT and tax
        dto.setVatApplicable(tx.getVatApplicable());
        dto.setVatAmount(tx.getVatAmount());
        dto.setTaxRelevant(tx.getTaxRelevant());

        // Status
        dto.setReconciled(tx.getReconciled());
        dto.setReconciledDate(tx.getReconciledDate());
        dto.setStatus(tx.getStatus() != null ? tx.getStatus().name() : null);

        // Metadata
        dto.setNotes(tx.getNotes());

        return dto;
    }

    /**
     * Convert a list of HistoricalTransactions to DTOs
     */
    public List<StatementTransactionDto> convertHistoricalListToDto(List<HistoricalTransaction> transactions) {
        return transactions.stream()
            .map(this::convertHistoricalToDto)
            .collect(Collectors.toList());
    }

    /**
     * Convert a single UnifiedTransaction to StatementTransactionDto
     */
    public StatementTransactionDto convertUnifiedToDto(UnifiedTransaction tx) {
        StatementTransactionDto dto = new StatementTransactionDto();

        // Core transaction fields
        dto.setTransactionDate(tx.getTransactionDate());
        dto.setAmount(tx.getAmount());
        dto.setDescription(tx.getDescription());
        dto.setTransactionType(tx.getTransactionType());
        dto.setCategory(tx.getCategory());

        // Source identification
        if (tx.getSourceSystem() != null) {
            dto.setSource(tx.getSourceSystem() == UnifiedTransaction.SourceSystem.HISTORICAL
                ? TransactionSource.HISTORICAL
                : TransactionSource.PAYPROP);
        }
        dto.setSourceTransactionId(tx.getId() != null ? tx.getId().toString() : null);

        // Property linking
        dto.setPropertyId(tx.getPropertyId());
        dto.setPropertyName(tx.getPropertyName());

        // Customer linking
        dto.setCustomerId(tx.getCustomerId());

        // Lease/Invoice reference
        dto.setInvoiceId(tx.getInvoiceId());
        dto.setLeaseReference(tx.getLeaseReference());

        return dto;
    }

    /**
     * Convert a list of UnifiedTransactions to DTOs
     */
    public List<StatementTransactionDto> convertUnifiedListToDto(List<UnifiedTransaction> transactions) {
        return transactions.stream()
            .map(this::convertUnifiedToDto)
            .collect(Collectors.toList());
    }

    /**
     * Filter transactions by account source (for filtering by data source in statements)
     */
    public List<StatementTransactionDto> filterByAccountSource(List<StatementTransactionDto> transactions,
                                                               String... allowedSources) {
        if (allowedSources == null || allowedSources.length == 0) {
            return transactions;
        }

        return transactions.stream()
            .filter(tx -> {
                if (tx.getAccountSource() == null) return false;
                for (String source : allowedSources) {
                    if (tx.getAccountSource().equalsIgnoreCase(source)) {
                        return true;
                    }
                }
                return false;
            })
            .collect(Collectors.toList());
    }
}

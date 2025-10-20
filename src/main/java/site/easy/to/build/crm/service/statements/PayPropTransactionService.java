package site.easy.to.build.crm.service.statements;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.dto.StatementTransactionDto;
import site.easy.to.build.crm.dto.StatementTransactionDto.TransactionSource;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * PayProp Transaction Service
 *
 * Queries PayProp tables and converts to StatementTransactionDto
 * for unified statement generation.
 *
 * Data Sources:
 * - payprop_report_all_payments: Actual processed payments (rent received, owner payments, expenses)
 * - payprop_export_payments: Standing payment instructions
 * - payprop_export_invoices: Invoice instructions (for rent due calculations)
 */
@Service
public class PayPropTransactionService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Get all PayProp transactions for a property within a date range
     *
     * @param propertyPayPropId PayProp property ID
     * @param fromDate Start date (inclusive)
     * @param toDate End date (inclusive)
     * @return List of transactions from PayProp
     */
    public List<StatementTransactionDto> getPayPropTransactionsForProperty(String propertyPayPropId,
                                                                           LocalDate fromDate,
                                                                           LocalDate toDate) {
        List<StatementTransactionDto> transactions = new ArrayList<>();

        // Query payprop_report_all_payments for actual transactions
        String sql = """
            SELECT
                payprop_id,
                amount,
                description,
                due_date,
                reference,
                service_fee,
                transaction_fee,
                beneficiary_payprop_id,
                beneficiary_name,
                beneficiary_type,
                category_payprop_id,
                category_name,
                incoming_transaction_id,
                incoming_transaction_amount,
                incoming_property_payprop_id,
                incoming_property_name,
                incoming_tenant_payprop_id,
                incoming_tenant_name,
                payment_batch_id,
                payment_batch_transfer_date,
                payment_instruction_id,
                reconciliation_date
            FROM payprop_report_all_payments
            WHERE (incoming_property_payprop_id = ? OR beneficiary_payprop_id IN (
                SELECT payprop_id FROM payprop_export_properties WHERE payprop_id = ?
            ))
            AND due_date BETWEEN ? AND ?
            AND sync_status != 'error'
            ORDER BY due_date ASC, payprop_id ASC
            """;

        jdbcTemplate.query(sql,
            ps -> {
                ps.setString(1, propertyPayPropId);
                ps.setString(2, propertyPayPropId);
                ps.setDate(3, java.sql.Date.valueOf(fromDate));
                ps.setDate(4, java.sql.Date.valueOf(toDate));
            },
            rs -> {
                transactions.add(mapPayPropPaymentToDto(rs));
            });

        return transactions;
    }

    /**
     * Get PayProp transactions for an owner (all their properties)
     *
     * @param ownerCustomerId Internal customer ID
     * @param fromDate Start date
     * @param toDate End date
     * @return List of transactions
     */
    public List<StatementTransactionDto> getPayPropTransactionsForOwner(Long ownerCustomerId,
                                                                        LocalDate fromDate,
                                                                        LocalDate toDate) {
        List<StatementTransactionDto> transactions = new ArrayList<>();

        // First get all property PayProp IDs for this owner
        String propertyQuery = """
            SELECT pay_prop_id
            FROM properties
            WHERE property_owner_id = ? AND pay_prop_id IS NOT NULL
            """;

        List<String> propertyPayPropIds = jdbcTemplate.queryForList(propertyQuery,
            new Object[]{ownerCustomerId}, String.class);

        // Get transactions for each property
        for (String propertyPayPropId : propertyPayPropIds) {
            transactions.addAll(getPayPropTransactionsForProperty(propertyPayPropId, fromDate, toDate));
        }

        return transactions;
    }

    /**
     * Get rent payments (incoming transactions) from PayProp
     */
    public List<StatementTransactionDto> getPayPropRentPayments(String propertyPayPropId,
                                                                LocalDate fromDate,
                                                                LocalDate toDate) {
        String sql = """
            SELECT
                payprop_id,
                amount,
                description,
                due_date,
                reference,
                service_fee,
                transaction_fee,
                beneficiary_payprop_id,
                beneficiary_name,
                beneficiary_type,
                category_payprop_id,
                category_name,
                incoming_transaction_id,
                incoming_transaction_amount,
                incoming_property_payprop_id,
                incoming_property_name,
                incoming_tenant_payprop_id,
                incoming_tenant_name,
                payment_batch_id,
                payment_batch_transfer_date,
                payment_instruction_id,
                reconciliation_date
            FROM payprop_report_all_payments
            WHERE incoming_property_payprop_id = ?
            AND incoming_transaction_id IS NOT NULL
            AND due_date BETWEEN ? AND ?
            AND sync_status = 'active'
            ORDER BY due_date ASC
            """;

        List<StatementTransactionDto> transactions = new ArrayList<>();
        jdbcTemplate.query(sql,
            ps -> {
                ps.setString(1, propertyPayPropId);
                ps.setDate(2, java.sql.Date.valueOf(fromDate));
                ps.setDate(3, java.sql.Date.valueOf(toDate));
            },
            rs -> {
                StatementTransactionDto dto = mapPayPropPaymentToDto(rs);
                dto.setTransactionType("invoice");  // Rent received
                dto.setCategory("rent");
                transactions.add(dto);
            });

        return transactions;
    }

    /**
     * Get owner payments (outgoing to beneficiaries) from PayProp
     */
    public List<StatementTransactionDto> getPayPropOwnerPayments(String propertyPayPropId,
                                                                 LocalDate fromDate,
                                                                 LocalDate toDate) {
        String sql = """
            SELECT
                payprop_id,
                amount,
                description,
                due_date,
                reference,
                service_fee,
                transaction_fee,
                beneficiary_payprop_id,
                beneficiary_name,
                beneficiary_type,
                category_payprop_id,
                category_name,
                incoming_transaction_id,
                incoming_transaction_amount,
                incoming_property_payprop_id,
                incoming_property_name,
                incoming_tenant_payprop_id,
                incoming_tenant_name,
                payment_batch_id,
                payment_batch_transfer_date,
                payment_instruction_id,
                reconciliation_date
            FROM payprop_report_all_payments
            WHERE incoming_property_payprop_id = ?
            AND beneficiary_type = 'beneficiary'
            AND due_date BETWEEN ? AND ?
            AND sync_status = 'active'
            ORDER BY due_date ASC
            """;

        List<StatementTransactionDto> transactions = new ArrayList<>();
        jdbcTemplate.query(sql,
            ps -> {
                ps.setString(1, propertyPayPropId);
                ps.setDate(2, java.sql.Date.valueOf(fromDate));
                ps.setDate(3, java.sql.Date.valueOf(toDate));
            },
            rs -> {
                StatementTransactionDto dto = mapPayPropPaymentToDto(rs);
                dto.setTransactionType("payment");  // Owner payment
                transactions.add(dto);
            });

        return transactions;
    }

    /**
     * Get expense payments (contractor, maintenance) from PayProp
     */
    public List<StatementTransactionDto> getPayPropExpenses(String propertyPayPropId,
                                                            LocalDate fromDate,
                                                            LocalDate toDate) {
        String sql = """
            SELECT
                payprop_id,
                amount,
                description,
                due_date,
                reference,
                service_fee,
                transaction_fee,
                beneficiary_payprop_id,
                beneficiary_name,
                beneficiary_type,
                category_payprop_id,
                category_name,
                incoming_transaction_id,
                incoming_transaction_amount,
                incoming_property_payprop_id,
                incoming_property_name,
                incoming_tenant_payprop_id,
                incoming_tenant_name,
                payment_batch_id,
                payment_batch_transfer_date,
                payment_instruction_id,
                reconciliation_date
            FROM payprop_report_all_payments
            WHERE incoming_property_payprop_id = ?
            AND beneficiary_type IN ('contractor', 'individual', 'company')
            AND due_date BETWEEN ? AND ?
            AND sync_status = 'active'
            ORDER BY due_date ASC
            """;

        List<StatementTransactionDto> transactions = new ArrayList<>();
        jdbcTemplate.query(sql,
            ps -> {
                ps.setString(1, propertyPayPropId);
                ps.setDate(2, java.sql.Date.valueOf(fromDate));
                ps.setDate(3, java.sql.Date.valueOf(toDate));
            },
            rs -> {
                StatementTransactionDto dto = mapPayPropPaymentToDto(rs);
                dto.setTransactionType("expense");  // Expense payment
                transactions.add(dto);
            });

        return transactions;
    }

    /**
     * Map a PayProp payment result set to StatementTransactionDto
     */
    private StatementTransactionDto mapPayPropPaymentToDto(ResultSet rs) throws SQLException {
        StatementTransactionDto dto = new StatementTransactionDto();

        // Core transaction fields
        dto.setTransactionDate(rs.getDate("due_date") != null ?
            rs.getDate("due_date").toLocalDate() : null);
        dto.setAmount(rs.getBigDecimal("amount"));
        dto.setDescription(rs.getString("description"));
        dto.setReference(rs.getString("reference"));

        // Source identification
        dto.setSource(TransactionSource.PAYPROP);
        dto.setSourceTransactionId(rs.getString("payprop_id"));
        dto.setAccountSource("PayProp");

        // Category
        dto.setCategory(rs.getString("category_name"));
        dto.setPaypropInvoiceId(rs.getString("category_payprop_id"));

        // Beneficiary information
        dto.setBeneficiaryPayPropId(rs.getString("beneficiary_payprop_id"));
        dto.setBeneficiaryName(rs.getString("beneficiary_name"));
        dto.setBeneficiaryType(rs.getString("beneficiary_type"));

        // Property information
        dto.setPropertyPayPropId(rs.getString("incoming_property_payprop_id"));
        dto.setPropertyName(rs.getString("incoming_property_name"));

        // Tenant information
        dto.setTenantPayPropId(rs.getString("incoming_tenant_payprop_id"));
        dto.setTenantName(rs.getString("incoming_tenant_name"));

        // Fee tracking
        dto.setServiceFeeAmount(rs.getBigDecimal("service_fee"));
        dto.setTransactionFee(rs.getBigDecimal("transaction_fee"));

        // Batch tracking
        dto.setPaypropBatchId(rs.getString("payment_batch_id"));
        if (rs.getDate("payment_batch_transfer_date") != null) {
            dto.setBatchTransferDate(rs.getDate("payment_batch_transfer_date").toLocalDate());
        }

        // Incoming transaction tracking
        dto.setIncomingTransactionId(rs.getString("incoming_transaction_id"));
        dto.setIncomingTransactionAmount(rs.getBigDecimal("incoming_transaction_amount"));

        // Reconciliation
        if (rs.getDate("reconciliation_date") != null) {
            dto.setReconciliationDate(rs.getDate("reconciliation_date").toLocalDate());
            dto.setReconciled(true);
        } else {
            dto.setReconciled(false);
        }

        return dto;
    }
}

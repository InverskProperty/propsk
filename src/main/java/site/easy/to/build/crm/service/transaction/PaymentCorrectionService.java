package site.easy.to.build.crm.service.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.PaymentCorrection;
import site.easy.to.build.crm.repository.PaymentCorrectionRepository;

import java.util.List;

@Service
public class PaymentCorrectionService {

    private static final Logger log = LoggerFactory.getLogger(PaymentCorrectionService.class);

    @Autowired
    private PaymentCorrectionRepository paymentCorrectionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Apply all active corrections to the unified tables.
     * Called as a final step in the rebuild process, AFTER all source data has been inserted.
     *
     * @return Number of corrections applied
     */
    public int applyCorrections() {
        List<PaymentCorrection> corrections = paymentCorrectionRepository.findByActiveTrue();

        if (corrections.isEmpty()) {
            log.info("  No active payment corrections to apply");
            return 0;
        }

        log.info("  Applying {} active payment correction(s)...", corrections.size());
        int applied = 0;

        for (PaymentCorrection correction : corrections) {
            try {
                switch (correction.getCorrectionType()) {
                    case "REASSIGN_PROPERTY" -> applied += applyReassignProperty(correction);
                    case "EXCLUDE" -> applied += applyExclude(correction);
                    default -> log.warn("  Unknown correction type: {}", correction.getCorrectionType());
                }
            } catch (Exception e) {
                log.error("  Failed to apply correction id={}: {}", correction.getId(), e.getMessage(), e);
            }
        }

        log.info("  Applied {} correction(s) successfully", applied);
        return applied;
    }

    /**
     * REASSIGN_PROPERTY: Move a transaction from one property/lease to another.
     *
     * Matching strategy: uses source_incoming_transaction_id which is the stable PayProp
     * incoming payment ID (payprop_export_incoming_payments.payprop_id). This ID survives
     * rebuilds because it's stored in the PayProp source tables, not the unified tables.
     *
     * Updates: unified_incoming_transactions, unified_transactions, unified_allocations
     */
    private int applyReassignProperty(PaymentCorrection c) {
        String incomingPaypropId = c.getSourceIncomingTransactionId();
        if (incomingPaypropId == null) {
            log.warn("  Correction id={} has no source_incoming_transaction_id, skipping", c.getId());
            return 0;
        }

        // Resolve corrected property name and lease reference
        String correctedPropertyName = jdbcTemplate.queryForObject(
                "SELECT property_name FROM properties WHERE id = ?",
                String.class, c.getCorrectedPropertyId());

        String correctedLeaseRef = c.getCorrectedLeaseReference();
        if (correctedLeaseRef == null && c.getCorrectedInvoiceId() != null) {
            correctedLeaseRef = jdbcTemplate.queryForObject(
                    "SELECT lease_reference FROM invoices WHERE id = ?",
                    String.class, c.getCorrectedInvoiceId());
        }

        int totalUpdated = 0;

        // 1. Update unified_incoming_transactions (matched by payprop_transaction_id)
        int uitUpdated = jdbcTemplate.update("""
            UPDATE unified_incoming_transactions
            SET property_id = ?,
                lease_id = ?,
                lease_reference = ?
            WHERE payprop_transaction_id = ?
            """,
                c.getCorrectedPropertyId(),
                c.getCorrectedInvoiceId(),
                correctedLeaseRef,
                incomingPaypropId);
        totalUpdated += uitUpdated;
        log.info("    Updated {} unified_incoming_transactions for correction id={}", uitUpdated, c.getId());

        // 2. Update unified_transactions (matched by payprop_transaction_id)
        int utUpdated = jdbcTemplate.update("""
            UPDATE unified_transactions
            SET property_id = ?,
                property_name = ?,
                invoice_id = ?,
                lease_reference = ?
            WHERE payprop_transaction_id = ?
            """,
                c.getCorrectedPropertyId(),
                correctedPropertyName,
                c.getCorrectedInvoiceId(),
                correctedLeaseRef,
                incomingPaypropId);
        totalUpdated += utUpdated;
        log.info("    Updated {} unified_transactions for correction id={}", utUpdated, c.getId());

        // 3. Update unified_allocations (matched via payprop_payment_id from payprop_report_all_payments)
        //    All allocation records (OWNER, COMMISSION, EXPENSE etc.) that belong to this incoming transaction
        int uaUpdated = jdbcTemplate.update("""
            UPDATE unified_allocations
            SET property_id = ?,
                property_name = ?,
                invoice_id = ?
            WHERE payprop_payment_id IN (
                SELECT prap.payprop_id
                FROM payprop_report_all_payments prap
                WHERE prap.incoming_transaction_id = ?
            )
            """,
                c.getCorrectedPropertyId(),
                correctedPropertyName,
                c.getCorrectedInvoiceId(),
                incomingPaypropId);
        totalUpdated += uaUpdated;
        log.info("    Updated {} unified_allocations for correction id={}", uaUpdated, c.getId());

        if (totalUpdated > 0) {
            log.info("  REASSIGN_PROPERTY correction id={} applied: {} total rows updated", c.getId(), totalUpdated);
        } else {
            log.warn("  REASSIGN_PROPERTY correction id={} matched 0 rows - verify source_incoming_transaction_id={}",
                    c.getId(), incomingPaypropId);
        }

        return totalUpdated > 0 ? 1 : 0;
    }

    /**
     * EXCLUDE: Remove a transaction from the unified tables entirely.
     */
    private int applyExclude(PaymentCorrection c) {
        String incomingPaypropId = c.getSourceIncomingTransactionId();
        if (incomingPaypropId == null) {
            log.warn("  Correction id={} has no source_incoming_transaction_id, skipping", c.getId());
            return 0;
        }

        int deleted = 0;

        // Delete allocations first (FK safety)
        deleted += jdbcTemplate.update("""
            DELETE FROM unified_allocations
            WHERE payprop_payment_id IN (
                SELECT prap.payprop_id
                FROM payprop_report_all_payments prap
                WHERE prap.incoming_transaction_id = ?
            )
            """, incomingPaypropId);

        // Delete unified_transactions
        deleted += jdbcTemplate.update(
                "DELETE FROM unified_transactions WHERE payprop_transaction_id = ?",
                incomingPaypropId);

        // Delete incoming transactions
        deleted += jdbcTemplate.update(
                "DELETE FROM unified_incoming_transactions WHERE payprop_transaction_id = ?",
                incomingPaypropId);

        log.info("  EXCLUDE correction id={}: deleted {} rows", c.getId(), deleted);
        return deleted > 0 ? 1 : 0;
    }

    public PaymentCorrection save(PaymentCorrection correction) {
        return paymentCorrectionRepository.save(correction);
    }

    public List<PaymentCorrection> findAll() {
        return paymentCorrectionRepository.findByActiveTrueOrderByCreatedAtDesc();
    }

    public void deactivate(Long id) {
        paymentCorrectionRepository.findById(id).ifPresent(c -> {
            c.setActive(false);
            paymentCorrectionRepository.save(c);
        });
    }
}

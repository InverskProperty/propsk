package site.easy.to.build.crm.service.payprop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.Invoice;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.repository.InvoiceRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service for linking PayProp transactions to local Invoice (lease) records
 * Resolves the gap between PayProp data and local lease tracking
 */
@Service
public class PayPropInvoiceLinkingService {

    private static final Logger log = LoggerFactory.getLogger(PayPropInvoiceLinkingService.class);

    @Autowired
    private InvoiceRepository invoiceRepository;

    /**
     * Find the most appropriate invoice (lease) for a PayProp transaction
     *
     * Matching Strategy:
     * 1. Try exact match: property + tenant + date within lease period
     * 2. Fallback to property match only if no tenant match
     * 3. Prefer more recent leases if multiple matches
     *
     * @param property The property (already resolved from PayProp property ID)
     * @param tenantPayPropId PayProp tenant ID (may be null)
     * @param transactionDate Date of the transaction
     * @return Best matching invoice, or null if no match found
     */
    public Invoice findInvoiceForTransaction(Property property,
                                            String tenantPayPropId,
                                            LocalDate transactionDate) {

        if (property == null || transactionDate == null) {
            log.warn("Cannot find invoice: property or transaction date is null");
            return null;
        }

        // Get all invoices for this property that overlap with the transaction date
        List<Invoice> candidates = invoiceRepository.findByPropertyAndDateRangeOverlap(
            property, transactionDate, transactionDate);

        if (candidates.isEmpty()) {
            log.warn("No invoices found for property {} on date {}",
                    property.getId(), transactionDate);
            return null;
        }

        // Strategy 1: Try to match by tenant PayProp ID (exact match)
        if (tenantPayPropId != null && !tenantPayPropId.trim().isEmpty()) {
            Optional<Invoice> exactMatch = candidates.stream()
                .filter(inv -> tenantPayPropId.equals(inv.getPaypropCustomerId()))
                .filter(inv -> isDateWithinLease(inv, transactionDate))
                .findFirst();

            if (exactMatch.isPresent()) {
                log.info("Found exact tenant match: invoice {} for tenant {}",
                        exactMatch.get().getId(), tenantPayPropId);
                return exactMatch.get();
            }

            log.debug("No exact tenant match for {}, trying fallback", tenantPayPropId);
        }

        // Strategy 2: Fallback to first active invoice for this property
        // Prefer more recent leases
        Optional<Invoice> fallbackMatch = candidates.stream()
            .filter(inv -> isDateWithinLease(inv, transactionDate))
            .findFirst();  // Already sorted by start_date DESC in repository query

        if (fallbackMatch.isPresent()) {
            log.info("Using fallback match: invoice {} for property {} (no tenant match)",
                    fallbackMatch.get().getId(), property.getId());
            return fallbackMatch.get();
        }

        log.warn("No suitable invoice found for property {} on date {}",
                property.getId(), transactionDate);
        return null;
    }

    /**
     * Check if a transaction date falls within a lease period
     */
    private boolean isDateWithinLease(Invoice invoice, LocalDate transactionDate) {
        LocalDate startDate = invoice.getStartDate();
        LocalDate endDate = invoice.getEndDate();

        // Transaction must be on or after lease start
        if (startDate != null && transactionDate.isBefore(startDate)) {
            return false;
        }

        // Transaction must be before lease end (if end date exists)
        if (endDate != null && transactionDate.isAfter(endDate)) {
            return false;
        }

        return true;
    }

    /**
     * Find invoice by PayProp invoice ID (direct reverse lookup)
     * Fastest method when PayProp transaction includes invoice reference
     *
     * @param payPropInvoiceId PayProp invoice ID
     * @return Invoice if found, null otherwise
     */
    public Invoice findInvoiceByPayPropId(String payPropInvoiceId) {
        if (payPropInvoiceId == null || payPropInvoiceId.trim().isEmpty()) {
            return null;
        }

        Optional<Invoice> invoice = invoiceRepository.findByPaypropId(payPropInvoiceId);

        if (invoice.isPresent()) {
            log.debug("Found invoice by PayProp ID: {}", payPropInvoiceId);
            return invoice.get();
        }

        log.debug("No invoice found for PayProp ID: {}", payPropInvoiceId);
        return null;
    }

    /**
     * Find invoice with detailed context logging for debugging
     * Use this during import to understand matching issues
     */
    public Invoice findInvoiceWithLogging(Property property,
                                         String tenantPayPropId,
                                         LocalDate transactionDate,
                                         String payPropTransactionId) {

        log.info("Finding invoice for PayProp transaction {}: property={}, tenant={}, date={}",
                payPropTransactionId,
                property != null ? property.getId() : "null",
                tenantPayPropId,
                transactionDate);

        Invoice invoice = findInvoiceForTransaction(property, tenantPayPropId, transactionDate);

        if (invoice == null) {
            log.warn("❌ No invoice found for PayProp transaction {}", payPropTransactionId);
        } else {
            log.info("✅ Linked PayProp transaction {} to invoice {} (lease: {})",
                    payPropTransactionId,
                    invoice.getId(),
                    invoice.getLeaseReference());
        }

        return invoice;
    }
}

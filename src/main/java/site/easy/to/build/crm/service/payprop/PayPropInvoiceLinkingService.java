package site.easy.to.build.crm.service.payprop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.Invoice;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.repository.InvoiceRepository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Service for linking PayProp transactions to local Invoice (lease) records
 *
 * UPDATED STRATEGY (2025-10-28):
 * - Prioritize property + customer matching (no date constraints)
 * - Date is informational only, not restrictive
 * - Handles deposits, early payments, late payments
 * - For multiple leases: picks most recent (by end_date)
 */
@Service
public class PayPropInvoiceLinkingService {

    private static final Logger log = LoggerFactory.getLogger(PayPropInvoiceLinkingService.class);

    @Autowired
    private InvoiceRepository invoiceRepository;

    /**
     * Find the most appropriate invoice (lease) for a transaction
     *
     * NEW Matching Strategy (flexible, no date constraints):
     * 1. Try PayProp invoice ID match (if available)
     * 2. Try property + customer match (strongest identifiers)
     *    - If multiple matches: pick most recent lease by end_date
     * 3. Fallback to property-only match
     * 4. Date is informational only, not a hard constraint
     *
     * @param property The property
     * @param customer The customer (tenant/beneficiary)
     * @param payPropInvoiceId PayProp invoice ID (may be null)
     * @param transactionDate Date of the transaction (informational)
     * @return Best matching invoice, or null if no match found
     */
    public Invoice findInvoiceForTransaction(Property property,
                                            Customer customer,
                                            String payPropInvoiceId,
                                            LocalDate transactionDate) {

        if (property == null) {
            log.warn("Cannot find invoice: property is null");
            return null;
        }

        // PRIORITY 1: Try PayProp invoice ID match (exact)
        if (payPropInvoiceId != null && !payPropInvoiceId.trim().isEmpty()) {
            Invoice invoice = findInvoiceByPayPropId(payPropInvoiceId);
            if (invoice != null) {
                log.info("✅ Found invoice by PayProp ID: {} → invoice {}",
                        payPropInvoiceId, invoice.getId());
                return invoice;
            }
        }

        // PRIORITY 2: Property + Customer match (strong identifiers, no date constraint)
        if (customer != null) {
            List<Invoice> customerLeases = invoiceRepository.findByCustomerAndProperty(
                customer, property);

            if (customerLeases.size() == 1) {
                Invoice invoice = customerLeases.get(0);
                log.info("✅ Found single lease for property {} + customer {}: invoice {}",
                        property.getId(), customer.getCustomerId(), invoice.getId());
                return invoice;
            }

            if (customerLeases.size() > 1) {
                // Multiple leases: Pick most recent by end_date
                Invoice invoice = customerLeases.stream()
                    .sorted(Comparator.comparing(Invoice::getEndDate,
                                               Comparator.nullsLast(Comparator.reverseOrder())))
                    .findFirst()
                    .orElse(null);

                log.info("✅ Found {} leases for property {} + customer {}, using most recent: invoice {}",
                        customerLeases.size(), property.getId(), customer.getCustomerId(),
                        invoice != null ? invoice.getId() : "null");
                return invoice;
            }

            log.debug("No leases found for property {} + customer {}",
                    property.getId(), customer.getCustomerId());
        }

        // PRIORITY 3: Property-only fallback (weakest)
        List<Invoice> propertyLeases = invoiceRepository.findByProperty(property);

        if (propertyLeases.isEmpty()) {
            log.warn("❌ No invoices found for property {}", property.getId());
            return null;
        }

        if (propertyLeases.size() == 1) {
            Invoice invoice = propertyLeases.get(0);
            log.info("✅ Found single lease for property {}: invoice {}",
                    property.getId(), invoice.getId());
            return invoice;
        }

        // Multiple leases: Pick most recent by end_date
        Invoice invoice = propertyLeases.stream()
            .sorted(Comparator.comparing(Invoice::getEndDate,
                                       Comparator.nullsLast(Comparator.reverseOrder())))
            .findFirst()
            .orElse(null);

        log.info("✅ Found {} leases for property {}, using most recent: invoice {}",
                propertyLeases.size(), property.getId(),
                invoice != null ? invoice.getId() : "null");
        return invoice;
    }

    /**
     * LEGACY METHOD - Keep for backward compatibility
     * Redirects to new method with null customer
     *
     * @deprecated Use findInvoiceForTransaction(Property, Customer, String, LocalDate)
     */
    @Deprecated
    public Invoice findInvoiceForTransaction(Property property,
                                            String tenantPayPropId,
                                            LocalDate transactionDate) {
        // Legacy method - use new logic with null customer
        return findInvoiceForTransaction(property, null, tenantPayPropId, transactionDate);
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
                                         Customer customer,
                                         String payPropInvoiceId,
                                         LocalDate transactionDate,
                                         String payPropTransactionId) {

        log.info("Finding invoice for transaction {}: property={}, customer={}, payPropInvoiceId={}, date={}",
                payPropTransactionId,
                property != null ? property.getId() : "null",
                customer != null ? customer.getCustomerId() : "null",
                payPropInvoiceId,
                transactionDate);

        Invoice invoice = findInvoiceForTransaction(property, customer, payPropInvoiceId, transactionDate);

        if (invoice == null) {
            log.warn("❌ No invoice found for transaction {}", payPropTransactionId);
        } else {
            log.info("✅ Linked transaction {} to invoice {} (lease: {})",
                    payPropTransactionId,
                    invoice.getId(),
                    invoice.getLeaseReference());
        }

        return invoice;
    }

    /**
     * LEGACY METHOD - Keep for backward compatibility
     * @deprecated Use findInvoiceWithLogging(Property, Customer, String, LocalDate, String)
     */
    @Deprecated
    public Invoice findInvoiceWithLogging(Property property,
                                         String tenantPayPropId,
                                         LocalDate transactionDate,
                                         String payPropTransactionId) {
        return findInvoiceWithLogging(property, null, tenantPayPropId, transactionDate, payPropTransactionId);
    }
}

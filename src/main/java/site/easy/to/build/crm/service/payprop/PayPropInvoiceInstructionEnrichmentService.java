package site.easy.to.build.crm.service.payprop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.Invoice;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.repository.CustomerRepository;
import site.easy.to.build.crm.repository.InvoiceRepository;
import site.easy.to.build.crm.repository.PropertyRepository;

import jakarta.persistence.EntityManager;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service to enrich local leases (invoices table) with PayProp invoice instruction IDs.
 *
 * Purpose:
 * - Read PayProp invoice instructions from payprop_export_invoices
 * - Match to local leases by property + customer (ignoring dates)
 * - Populate payprop_id and payprop_customer_id fields on local leases
 *
 * This creates bidirectional mapping:
 * - Local lease can be found via PayProp IDs
 * - PayProp transactions can map to local lease structure
 */
@ConditionalOnProperty(name = "payprop.enabled", havingValue = "true", matchIfMissing = false)
@Service
public class PayPropInvoiceInstructionEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(PayPropInvoiceInstructionEnrichmentService.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private EntityManager entityManager;

    /**
     * Enrich local leases with PayProp invoice instruction IDs
     *
     * @return Result summary with counts
     */
    @Transactional
    public EnrichmentResult enrichLocalLeasesWithPayPropIds() {
        log.info("🔗 Starting lease enrichment with PayProp invoice instruction IDs");

        EnrichmentResult result = new EnrichmentResult();

        try {
            // 1. Read PayProp invoice instructions
            List<PayPropInvoiceInstruction> instructions = readPayPropInvoiceInstructions();
            result.totalPayPropInstructions = instructions.size();
            log.info("📥 Found {} PayProp invoice instructions", instructions.size());

            // 2. Process each instruction
            int enriched = 0;
            int created = 0;
            int skipped = 0;
            int errors = 0;

            for (PayPropInvoiceInstruction instruction : instructions) {
                try {
                    // CRITICAL: Check for soft-deleted invoices with same payprop_id and hard delete them
                    // This prevents "Duplicate entry for key 'invoices.payprop_id'" errors
                    // when trying to create/update invoices with PayProp IDs
                    cleanupSoftDeletedInvoiceWithPayPropId(instruction.paypropId);

                    // Find property in our system
                    Property property = propertyRepository.findByPayPropId(instruction.propertyPaypropId)
                        .orElse(null);
                    if (property == null) {
                        log.debug("⏭️ Property not found for PayProp ID: {} - skipping instruction {}",
                            instruction.propertyPaypropId, instruction.paypropId);
                        skipped++;
                        result.addSkipped(instruction.paypropId, "Property not found: " + instruction.propertyPaypropId);
                        continue;
                    }

                    // Find customer/tenant in our system (optional)
                    Customer customer = null;
                    if (instruction.tenantPaypropId != null && !instruction.tenantPaypropId.isEmpty()) {
                        customer = customerRepository.findByPayPropEntityId(instruction.tenantPaypropId);
                    }

                    // Find existing local lease by property + customer (ignoring dates as requested)
                    Invoice existingLease = null;
                    if (customer != null) {
                        // Match by property + customer (ordered by start date desc)
                        List<Invoice> leases = invoiceRepository.findByPropertyAndCustomerOrderByStartDateDesc(property, customer);
                        if (!leases.isEmpty()) {
                            // Take the most recent active lease
                            existingLease = leases.stream()
                                .filter(Invoice::getIsActive)
                                .findFirst()
                                .or(() -> leases.stream().findFirst())
                                .orElse(null);
                        }
                    }

                    if (existingLease != null) {
                        // ENRICH existing lease with PayProp IDs
                        boolean updated = false;

                        if (existingLease.getPaypropId() == null || !existingLease.getPaypropId().equals(instruction.paypropId)) {
                            existingLease.setPaypropId(instruction.paypropId);
                            updated = true;
                        }

                        if (instruction.tenantPaypropId != null &&
                            (existingLease.getPaypropCustomerId() == null ||
                             !existingLease.getPaypropCustomerId().equals(instruction.tenantPaypropId))) {
                            existingLease.setPaypropCustomerId(instruction.tenantPaypropId);
                            updated = true;
                        }

                        if (updated) {
                            existingLease.setPaypropLastSync(LocalDateTime.now());
                            invoiceRepository.save(existingLease);
                            enriched++;
                            log.debug("✅ Enriched lease {} with PayProp IDs", existingLease.getLeaseReference());
                        } else {
                            skipped++;
                        }
                    } else {
                        // No existing lease - AUTO-CREATE from PayProp instruction
                        if (customer == null) {
                            // Can't create lease without tenant
                            log.debug("ℹ️ Cannot create lease for instruction {} - tenant not found", instruction.paypropId);
                            skipped++;
                            result.addSkipped(instruction.paypropId, "No tenant found for instruction");
                            continue;
                        }

                        try {
                            Invoice newLease = createLeaseFromPayPropInstruction(instruction, property, customer);
                            invoiceRepository.save(newLease);
                            created++;
                            log.info("✅ Created new lease from PayProp instruction: {} for {} at {}",
                                newLease.getLeaseReference(), customer.getName(), property.getAddressLine1());
                        } catch (Exception createEx) {
                            log.error("❌ Failed to auto-create lease from instruction {}: {}",
                                instruction.paypropId, createEx.getMessage());
                            errors++;
                            result.addError(instruction.paypropId, "Failed to create lease: " + createEx.getMessage());
                        }
                    }

                } catch (Exception e) {
                    log.error("❌ Failed to process instruction {}: {}", instruction.paypropId, e.getMessage());
                    errors++;
                    result.addError(instruction.paypropId, e.getMessage());

                    // CRITICAL: Clear the entity manager after an error to prevent cascade failures
                    // When a constraint violation occurs, Hibernate marks the session as rollback-only
                    // and subsequent operations fail with duplicate entry or null id errors
                    try {
                        entityManager.clear();
                    } catch (Exception clearEx) {
                        log.warn("Failed to clear entity manager after error: {}", clearEx.getMessage());
                    }
                }
            }

            result.enriched = enriched;
            result.created = created;
            result.skipped = skipped;
            result.errors = errors;
            result.success = (errors == 0 || enriched > 0);

            log.info("✅ Lease enrichment complete: {} enriched, {} created, {} skipped, {} errors",
                enriched, created, skipped, errors);

        } catch (Exception e) {
            log.error("❌ Lease enrichment failed", e);
            result.success = false;
            result.errorMessage = e.getMessage();
        }

        return result;
    }

    /**
     * Clean up soft-deleted invoices that have the same payprop_id
     *
     * Problem: When an invoice is soft-deleted (deleted_at set), it still retains its payprop_id.
     * The UNIQUE constraint on payprop_id doesn't care about soft-deletes, so when PayProp
     * sync tries to create/update an invoice with that payprop_id, it fails with:
     * "Duplicate entry 'd71ebxD9Z5' for key 'invoices.payprop_id'"
     *
     * Solution: Hard delete any soft-deleted invoices with this payprop_id before processing.
     * If an invoice is soft-deleted but PayProp still has it active, the soft-delete was likely
     * a mistake or the invoice was reactivated in PayProp.
     *
     * @param paypropId The PayProp ID to check
     */
    private void cleanupSoftDeletedInvoiceWithPayPropId(String paypropId) {
        if (paypropId == null || paypropId.isEmpty()) {
            return;
        }

        try {
            // Find invoice with this payprop_id (may be soft-deleted)
            Optional<Invoice> invoiceOpt = invoiceRepository.findByPaypropId(paypropId);

            if (invoiceOpt.isPresent()) {
                Invoice invoice = invoiceOpt.get();

                // Check if it's soft-deleted
                if (invoice.getDeletedAt() != null) {
                    log.warn("⚠️ Found soft-deleted invoice (ID: {}, lease: {}) with payprop_id '{}' that would block sync. " +
                            "Hard deleting to allow PayProp sync to recreate it.",
                            invoice.getId(), invoice.getLeaseReference(), paypropId);

                    // Hard delete the soft-deleted invoice
                    invoiceRepository.delete(invoice);
                    // Force flush the delete immediately to clear the constraint
                    entityManager.flush();

                    log.info("✅ Removed soft-deleted invoice to prevent duplicate payprop_id constraint violation");
                }
                // If invoice exists and is NOT soft-deleted, leave it alone - enrichment will update it
            }
        } catch (Exception e) {
            log.error("❌ Failed to cleanup soft-deleted invoice with payprop_id '{}': {}",
                    paypropId, e.getMessage());
            // CRITICAL: Clear the entity manager to remove any dirty entities that would cause
            // cascade failures on subsequent operations
            try {
                entityManager.clear();
            } catch (Exception clearEx) {
                log.warn("Failed to clear entity manager in cleanup: {}", clearEx.getMessage());
            }
            // Don't throw - let the sync continue and handle the duplicate error if it occurs
        }
    }

    /**
     * Create a new lease (Invoice) from PayProp invoice instruction
     */
    private Invoice createLeaseFromPayPropInstruction(
            PayPropInvoiceInstruction instruction,
            Property property,
            Customer customer) {

        Invoice lease = new Invoice();

        // Store PayProp reference as external reference (not as primary lease_reference)
        lease.setExternalReference("PAYPROP-" + instruction.paypropId);
        // Note: leaseReference will be auto-generated as "LEASE-{id}" after save

        // Link to property and customer
        lease.setProperty(property);
        lease.setCustomer(customer);

        // Financial details from instruction
        lease.setAmount(instruction.amount);
        lease.setFrequency(mapFrequencyFromPayProp(instruction.frequency));
        lease.setPaymentDay(instruction.paymentDay);

        // Date range from instruction
        lease.setStartDate(instruction.fromDate);
        lease.setEndDate(instruction.toDate); // May be null for ongoing leases

        // Set as active based on instruction
        lease.setIsActive(instruction.isActive != null ? instruction.isActive : true);

        // PayProp linkage
        lease.setPaypropId(instruction.paypropId);
        lease.setPaypropCustomerId(instruction.tenantPaypropId);
        lease.setPaypropLastSync(LocalDateTime.now());

        // Set category - categoryId is REQUIRED by validation
        if (instruction.categoryId != null && !instruction.categoryId.isEmpty()) {
            lease.setCategoryId(instruction.categoryId);
            lease.setCategoryName(instruction.categoryName);
        } else {
            // Fallback to a default - use "RENT" as category ID if PayProp doesn't provide one
            lease.setCategoryId("RENT");
            lease.setCategoryName(instruction.categoryName != null ? instruction.categoryName : "Rent");
        }

        // Set description - REQUIRED by validation
        String description = String.format("Lease for %s - Tenant: %s - £%s %s",
            property.getAddressLine1() != null ? property.getAddressLine1() : instruction.propertyName,
            customer.getName(),
            instruction.amount,
            instruction.frequency != null ? instruction.frequency : "monthly");
        lease.setDescription(description);

        // Metadata
        lease.setCreatedAt(LocalDateTime.now());

        log.debug("📝 Created lease object: {} for property {} + customer {}",
            lease.getLeaseReference(), property.getAddressLine1(), customer.getName());

        return lease;
    }

    /**
     * Map PayProp frequency string to Invoice.InvoiceFrequency enum
     */
    private Invoice.InvoiceFrequency mapFrequencyFromPayProp(String paypropFrequency) {
        if (paypropFrequency == null) {
            return Invoice.InvoiceFrequency.monthly; // Default
        }

        return switch (paypropFrequency.toLowerCase()) {
            case "monthly", "m" -> Invoice.InvoiceFrequency.monthly;
            case "weekly", "w" -> Invoice.InvoiceFrequency.weekly;
            case "quarterly", "q" -> Invoice.InvoiceFrequency.quarterly;
            case "yearly", "annual", "y", "a" -> Invoice.InvoiceFrequency.yearly;
            case "daily", "d" -> Invoice.InvoiceFrequency.daily;
            case "one_time", "once" -> Invoice.InvoiceFrequency.one_time;
            default -> {
                log.warn("⚠️ Unknown PayProp frequency '{}', defaulting to monthly", paypropFrequency);
                yield Invoice.InvoiceFrequency.monthly;
            }
        };
    }

    /**
     * Read PayProp invoice instructions from payprop_export_invoices
     */
    private List<PayPropInvoiceInstruction> readPayPropInvoiceInstructions() throws Exception {
        List<PayPropInvoiceInstruction> instructions = new ArrayList<>();

        String sql = """
            SELECT
                payprop_id,
                property_payprop_id,
                tenant_payprop_id,
                gross_amount,
                frequency,
                from_date,
                to_date,
                payment_day,
                is_active_instruction,
                property_name,
                tenant_display_name,
                category_name,
                category_payprop_id
            FROM payprop_export_invoices
            WHERE is_active_instruction = 1
            ORDER BY property_payprop_id, tenant_payprop_id
        """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                PayPropInvoiceInstruction instruction = new PayPropInvoiceInstruction();
                instruction.paypropId = rs.getString("payprop_id");
                instruction.propertyPaypropId = rs.getString("property_payprop_id");
                instruction.tenantPaypropId = rs.getString("tenant_payprop_id");
                instruction.amount = rs.getBigDecimal("gross_amount");
                instruction.frequency = rs.getString("frequency");

                java.sql.Date fromDate = rs.getDate("from_date");
                instruction.fromDate = fromDate != null ? fromDate.toLocalDate() : null;

                java.sql.Date toDate = rs.getDate("to_date");
                instruction.toDate = toDate != null ? toDate.toLocalDate() : null;

                instruction.paymentDay = (Integer) rs.getObject("payment_day");
                instruction.isActive = rs.getBoolean("is_active_instruction");
                instruction.propertyName = rs.getString("property_name");
                instruction.tenantName = rs.getString("tenant_display_name");
                instruction.categoryName = rs.getString("category_name");
                instruction.categoryId = rs.getString("category_payprop_id");

                instructions.add(instruction);
            }
        }

        return instructions;
    }

    /**
     * Result object for enrichment operation
     */
    public static class EnrichmentResult {
        public boolean success;
        public int totalPayPropInstructions;
        public int enriched;
        public int created;
        public int skipped;
        public int errors;
        public String errorMessage;
        public List<String> skippedDetails = new ArrayList<>();
        public List<String> errorDetails = new ArrayList<>();

        public void addSkipped(String instructionId, String reason) {
            skippedDetails.add(String.format("%s: %s", instructionId, reason));
        }

        public void addError(String instructionId, String error) {
            errorDetails.add(String.format("%s: %s", instructionId, error));
        }

        @Override
        public String toString() {
            return String.format("EnrichmentResult{success=%s, total=%d, enriched=%d, created=%d, skipped=%d, errors=%d}",
                success, totalPayPropInstructions, enriched, created, skipped, errors);
        }
    }

    /**
     * Internal record structure for PayProp invoice instruction data
     */
    private static class PayPropInvoiceInstruction {
        String paypropId;
        String propertyPaypropId;
        String tenantPaypropId;
        java.math.BigDecimal amount;
        String frequency;
        java.time.LocalDate fromDate;
        java.time.LocalDate toDate;
        Integer paymentDay;
        Boolean isActive;
        String propertyName;
        String tenantName;
        String categoryName;
        String categoryId;
    }
}

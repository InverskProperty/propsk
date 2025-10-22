package site.easy.to.build.crm.service.payprop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.Invoice;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.repository.CustomerRepository;
import site.easy.to.build.crm.repository.InvoiceRepository;
import site.easy.to.build.crm.repository.PropertyRepository;

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
                        // No existing lease - just track for now
                        // You can decide later if you want to auto-create leases from PayProp
                        log.debug("ℹ️ No local lease found for property {} + customer {} - instruction {}",
                            property.getAddressLine1(), customer != null ? customer.getName() : "unknown",
                            instruction.paypropId);
                        skipped++;
                        result.addSkipped(instruction.paypropId,
                            "No local lease for property: " + property.getAddressLine1());
                    }

                } catch (Exception e) {
                    log.error("❌ Failed to process instruction {}: {}", instruction.paypropId, e.getMessage());
                    errors++;
                    result.addError(instruction.paypropId, e.getMessage());
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
                category_name
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
    }
}

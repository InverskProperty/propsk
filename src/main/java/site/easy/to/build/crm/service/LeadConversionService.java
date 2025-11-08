package site.easy.to.build.crm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Unified service for converting leads to tenants
 *
 * This service coordinates the complete conversion workflow:
 * 1. Validates the lead can be converted
 * 2. Creates or updates Customer with isTenant=true
 * 3. Updates Lead status to CONVERTED
 * 4. Updates LettingInstruction to ACTIVE_LEASE
 * 5. Creates rental Invoice
 * 6. Records conversion action
 *
 * All operations happen in a single @Transactional boundary to ensure atomicity.
 */
@Service
@Transactional
public class LeadConversionService {

    private static final Logger logger = LoggerFactory.getLogger(LeadConversionService.class);

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private LettingInstructionRepository lettingInstructionRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private LeadActionRepository leadActionRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Convert a lead to a tenant with full workflow coordination
     *
     * @param leadId The lead to convert
     * @param conversionRequest Details for the conversion (lease dates, rent, deposit)
     * @param convertedByUserId User performing the conversion
     * @return ConversionResult containing the updated entities
     * @throws IllegalStateException if lead cannot be converted
     * @throws IllegalArgumentException if lead or instruction not found
     */
    public ConversionResult convertLeadToTenant(Long leadId, ConversionRequest conversionRequest, Integer convertedByUserId) {
        logger.info("Starting lead conversion for lead ID: {}", leadId);

        // 1. Validate and get lead
        Lead lead = leadRepository.findById(leadId.intValue())
                .orElseThrow(() -> new IllegalArgumentException("Lead not found: " + leadId));

        validateLeadForConversion(lead);

        // 2. Get or create customer
        Customer customer = getOrCreateCustomer(lead, convertedByUserId);
        customer.setIsTenant(true);
        customer.setMoveInDate(conversionRequest.getLeaseStartDate());
        customer.setMoveOutDate(conversionRequest.getLeaseEndDate());
        customer.setMonthlyRent(conversionRequest.getMonthlyRent());
        customer.setDepositAmount(conversionRequest.getDepositAmount());
        customer = customerRepository.save(customer);
        logger.info("Customer {} marked as tenant", customer.getCustomerId());

        // 3. Update lead
        lead.setStatus(LeadStatus.CONVERTED);
        lead.setConvertedAt(LocalDateTime.now());
        lead.setConvertedToCustomer(customer);
        if (lead.getCustomer() == null) {
            lead.setCustomer(customer);
        }
        lead = leadRepository.save(lead);
        logger.info("Lead {} marked as converted", lead.getLeadId());

        // 4. Update letting instruction (if attached)
        LettingInstruction instruction = null;
        if (lead.getLettingInstruction() != null) {
            instruction = lead.getLettingInstruction();

            // Validate instruction can be converted
            if (instruction.getStatus() != InstructionStatus.OFFER_MADE &&
                instruction.getStatus() != InstructionStatus.CONTRACTS_COMPLETE &&
                instruction.getStatus() != InstructionStatus.IN_CONTRACTS) {
                logger.warn("Converting instruction from unexpected status: {} (allowed but unusual)",
                           instruction.getStatus());
            }

            instruction.convertToActiveLease(
                customer,
                conversionRequest.getLeaseStartDate(),
                conversionRequest.getLeaseEndDate(),
                conversionRequest.getMonthlyRent(),
                conversionRequest.getDepositAmount()
            );
            instruction = lettingInstructionRepository.save(instruction);
            logger.info("Instruction {} converted to ACTIVE_LEASE", instruction.getInstructionReference());

            // 5. Create rental invoice
            Invoice invoice = createRentInvoice(instruction, customer, conversionRequest, convertedByUserId);
            invoice = invoiceRepository.save(invoice);
            logger.info("Created rental invoice {} for customer {}", invoice.getId(), customer.getCustomerId());
        } else {
            logger.warn("Lead {} has no letting instruction - skipping instruction update and invoice creation", leadId);
        }

        // 6. Record conversion action
        recordConversionAction(lead, customer, convertedByUserId);

        logger.info("Successfully converted lead {} to tenant (customer {})", leadId, customer.getCustomerId());

        return new ConversionResult(lead, customer, instruction);
    }

    /**
     * Validate that a lead can be converted
     */
    private void validateLeadForConversion(Lead lead) {
        if (lead.getStatus() == LeadStatus.CONVERTED) {
            throw new IllegalStateException("Lead is already converted");
        }

        if (lead.getStatus() == LeadStatus.LOST) {
            throw new IllegalStateException("Cannot convert a lost lead");
        }

        // Recommended to be in CONTRACTS_COMPLETE, but allow flexibility
        if (lead.getStatus() != LeadStatus.CONTRACTS_COMPLETE) {
            logger.warn("Converting lead from status {} (recommended: CONTRACTS_COMPLETE)", lead.getStatus());
        }

        if (lead.getProperty() == null && lead.getLettingInstruction() == null) {
            throw new IllegalStateException("Lead must have either a property or letting instruction");
        }

        if (lead.getEmail() == null && lead.getCustomer() == null) {
            throw new IllegalStateException("Lead must have either an email or existing customer");
        }
    }

    /**
     * Get existing customer or create new one
     */
    private Customer getOrCreateCustomer(Lead lead, Integer createdByUserId) {
        // If lead already has a customer, use it
        if (lead.getCustomer() != null) {
            logger.info("Using existing customer {} for lead {}",
                       lead.getCustomer().getCustomerId(), lead.getLeadId());
            return lead.getCustomer();
        }

        // If lead has convertedToCustomer, use it
        if (lead.getConvertedToCustomer() != null) {
            logger.info("Using previously converted customer {} for lead {}",
                       lead.getConvertedToCustomer().getCustomerId(), lead.getLeadId());
            return lead.getConvertedToCustomer();
        }

        // Otherwise create new customer
        logger.info("Creating new customer for lead {}", lead.getLeadId());
        Customer customer = new Customer();
        customer.setName(lead.getName());
        customer.setEmail(lead.getEmail());
        customer.setPhone(lead.getPhone());

        // Link to user if available
        if (createdByUserId != null) {
            userRepository.findById(createdByUserId).ifPresent(customer::setUser);
        }

        customer.setCreatedAt(LocalDateTime.now());

        return customer;
    }

    /**
     * Create a rental invoice for the tenancy
     */
    private Invoice createRentInvoice(LettingInstruction instruction, Customer customer,
                                     ConversionRequest request, Integer createdByUserId) {
        Invoice invoice = new Invoice();

        // Link to entities
        invoice.setCustomer(customer);
        invoice.setProperty(instruction.getProperty());
        invoice.setLettingInstruction(instruction);

        // Invoice details
        invoice.setCategoryId("RENT"); // Assuming this is the category ID for rent
        invoice.setAmount(request.getMonthlyRent());
        invoice.setDescription("Monthly rent for " + instruction.getProperty().getPropertyName());

        // Dates - set to lease period
        invoice.setStartDate(request.getLeaseStartDate());
        invoice.setEndDate(request.getLeaseEndDate());

        // Recurring monthly
        invoice.setFrequency(Invoice.InvoiceFrequency.monthly);

        // Link to user
        if (createdByUserId != null) {
            userRepository.findById(createdByUserId).ifPresent(invoice::setCreatedByUser);
        }

        invoice.setCreatedAt(LocalDateTime.now());

        // Generate unique reference
        invoice.setLeaseReference(generateLeaseReference(instruction));

        return invoice;
    }

    /**
     * Generate unique lease reference for invoice
     */
    private String generateLeaseReference(LettingInstruction instruction) {
        return "LEASE-" + instruction.getProperty().getId() + "-" +
               LocalDate.now().toString().replace("-", "");
    }

    /**
     * Record the conversion action in lead history
     */
    private void recordConversionAction(Lead lead, Customer customer, Integer userId) {
        LeadAction action = new LeadAction();
        action.setLead(lead);
        action.setAction("Lead converted to tenant (Customer ID: " + customer.getCustomerId() + ")");
        action.setTimestamp(LocalDateTime.now());

        leadActionRepository.save(action);
    }

    /**
     * Request object for conversion details
     */
    public static class ConversionRequest {
        private LocalDate leaseStartDate;
        private LocalDate leaseEndDate;
        private BigDecimal monthlyRent;
        private BigDecimal depositAmount;

        public ConversionRequest() {}

        public ConversionRequest(LocalDate leaseStartDate, LocalDate leaseEndDate,
                               BigDecimal monthlyRent, BigDecimal depositAmount) {
            this.leaseStartDate = leaseStartDate;
            this.leaseEndDate = leaseEndDate;
            this.monthlyRent = monthlyRent;
            this.depositAmount = depositAmount;
        }

        // Getters and setters
        public LocalDate getLeaseStartDate() { return leaseStartDate; }
        public void setLeaseStartDate(LocalDate leaseStartDate) { this.leaseStartDate = leaseStartDate; }

        public LocalDate getLeaseEndDate() { return leaseEndDate; }
        public void setLeaseEndDate(LocalDate leaseEndDate) { this.leaseEndDate = leaseEndDate; }

        public BigDecimal getMonthlyRent() { return monthlyRent; }
        public void setMonthlyRent(BigDecimal monthlyRent) { this.monthlyRent = monthlyRent; }

        public BigDecimal getDepositAmount() { return depositAmount; }
        public void setDepositAmount(BigDecimal depositAmount) { this.depositAmount = depositAmount; }
    }

    /**
     * Result object containing all updated entities
     */
    public static class ConversionResult {
        private final Lead lead;
        private final Customer customer;
        private final LettingInstruction instruction;

        public ConversionResult(Lead lead, Customer customer, LettingInstruction instruction) {
            this.lead = lead;
            this.customer = customer;
            this.instruction = instruction;
        }

        public Lead getLead() { return lead; }
        public Customer getCustomer() { return customer; }
        public LettingInstruction getInstruction() { return instruction; }
    }
}

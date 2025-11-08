package site.easy.to.build.crm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing letting instructions and their lifecycle
 * Handles instruction creation, status transitions, conversions, and metrics
 */
@Service
@Transactional
public class LettingInstructionService {

    private static final Logger logger = LoggerFactory.getLogger(LettingInstructionService.class);

    @Autowired
    private LettingInstructionRepository lettingInstructionRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private CustomerPropertyAssignmentRepository customerPropertyAssignmentRepository;

    // ===== CRUD OPERATIONS =====

    /**
     * Create a new letting instruction for a property
     */
    public LettingInstruction createInstruction(Long propertyId, BigDecimal targetRent,
                                                Integer targetLeaseLengthMonths,
                                                LocalDate expectedVacancyDate,
                                                String propertyDescription,
                                                Long createdByUserId) {
        // Validate property exists
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found: " + propertyId));

        // Note: Properties can have multiple concurrent instructions
        // (e.g., multiple units within a property, or overlapping preparation periods)

        // Get creating user
        User createdByUser = null;
        if (createdByUserId != null) {
            createdByUser = userRepository.findById(createdByUserId.intValue());
        }

        // Create instruction
        LettingInstruction instruction = new LettingInstruction();
        instruction.setProperty(property);
        instruction.setStatus(InstructionStatus.INSTRUCTION_RECEIVED);
        instruction.setInstructionReceivedDate(LocalDate.now());
        instruction.setExpectedVacancyDate(expectedVacancyDate);
        instruction.setTargetRent(targetRent);
        instruction.setTargetLeaseLengthMonths(targetLeaseLengthMonths);
        instruction.setPropertyDescription(propertyDescription);
        instruction.setCreatedByUser(createdByUser);

        // Generate unique reference
        String reference = generateInstructionReference(property);
        instruction.setInstructionReference(reference);

        LettingInstruction saved = lettingInstructionRepository.save(instruction);
        logger.info("Created letting instruction {} for property {}", saved.getInstructionReference(), property.getPropertyName());

        return saved;
    }

    /**
     * Get instruction by ID
     */
    @Transactional(readOnly = true)
    public Optional<LettingInstruction> getInstructionById(Long id) {
        return lettingInstructionRepository.findById(id);
    }

    /**
     * Get instruction with all details eagerly loaded
     */
    @Transactional(readOnly = true)
    public Optional<LettingInstruction> getInstructionWithDetails(Long id) {
        return lettingInstructionRepository.findByIdWithDetails(id);
    }

    /**
     * Get instruction by reference
     */
    @Transactional(readOnly = true)
    public Optional<LettingInstruction> getInstructionByReference(String reference) {
        return lettingInstructionRepository.findByInstructionReference(reference);
    }

    /**
     * Get all instructions for a property (letting history)
     */
    @Transactional(readOnly = true)
    public List<LettingInstruction> getPropertyLettingHistory(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found: " + propertyId));
        return lettingInstructionRepository.findByPropertyOrderByCreatedAtDesc(property);
    }

    /**
     * Get active instruction for a property
     */
    @Transactional(readOnly = true)
    public Optional<LettingInstruction> getActiveInstructionForProperty(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found: " + propertyId));
        return lettingInstructionRepository.findActiveInstructionForProperty(property);
    }

    // ===== STATUS TRANSITION OPERATIONS =====

    /**
     * Mark instruction as ready and start preparing for advertising
     */
    public LettingInstruction startPreparing(Long instructionId) {
        LettingInstruction instruction = getInstructionById(instructionId)
                .orElseThrow(() -> new IllegalArgumentException("Instruction not found: " + instructionId));

        if (instruction.getStatus() != InstructionStatus.INSTRUCTION_RECEIVED) {
            throw new IllegalStateException("Can only start preparing from INSTRUCTION_RECEIVED status");
        }

        instruction.setStatus(InstructionStatus.PREPARING);
        LettingInstruction saved = lettingInstructionRepository.save(instruction);
        logger.info("Instruction {} moved to PREPARING", instruction.getInstructionReference());

        return saved;
    }

    /**
     * Start advertising the property
     */
    public LettingInstruction startAdvertising(Long instructionId, LocalDate advertisingStartDate,
                                               String keyFeatures, String marketingNotes) {
        LettingInstruction instruction = getInstructionById(instructionId)
                .orElseThrow(() -> new IllegalArgumentException("Instruction not found: " + instructionId));

        if (!instruction.getStatus().canTransitionTo(InstructionStatus.ADVERTISING)) {
            throw new IllegalStateException("Cannot transition to ADVERTISING from " + instruction.getStatus());
        }

        instruction.startAdvertising(advertisingStartDate != null ? advertisingStartDate : LocalDate.now());
        instruction.setKeyFeatures(keyFeatures);
        instruction.setMarketingNotes(marketingNotes);

        LettingInstruction saved = lettingInstructionRepository.save(instruction);
        logger.info("Instruction {} started advertising", instruction.getInstructionReference());

        return saved;
    }

    /**
     * Move instruction to viewings in progress
     */
    public LettingInstruction startViewings(Long instructionId) {
        LettingInstruction instruction = getInstructionById(instructionId)
                .orElseThrow(() -> new IllegalArgumentException("Instruction not found: " + instructionId));

        if (instruction.getStatus() != InstructionStatus.ADVERTISING) {
            throw new IllegalStateException("Can only start viewings from ADVERTISING status");
        }

        instruction.setStatus(InstructionStatus.VIEWINGS_IN_PROGRESS);
        LettingInstruction saved = lettingInstructionRepository.save(instruction);
        logger.info("Instruction {} moved to VIEWINGS_IN_PROGRESS", instruction.getInstructionReference());

        return saved;
    }

    /**
     * Mark offer as made and accepted
     */
    public LettingInstruction markOfferMade(Long instructionId, String internalNotes) {
        LettingInstruction instruction = getInstructionById(instructionId)
                .orElseThrow(() -> new IllegalArgumentException("Instruction not found: " + instructionId));

        if (instruction.getStatus() != InstructionStatus.VIEWINGS_IN_PROGRESS) {
            throw new IllegalStateException("Can only mark offer from VIEWINGS_IN_PROGRESS status");
        }

        instruction.setStatus(InstructionStatus.OFFER_MADE);
        if (internalNotes != null) {
            String existingNotes = instruction.getInternalNotes();
            instruction.setInternalNotes(existingNotes != null ?
                    existingNotes + "\n\n" + internalNotes : internalNotes);
        }

        LettingInstruction saved = lettingInstructionRepository.save(instruction);
        logger.info("Instruction {} moved to OFFER_MADE", instruction.getInstructionReference());

        return saved;
    }

    /**
     * Convert instruction to active lease
     * Comprehensive process that handles:
     * 1. Lead to Customer conversion (if needed)
     * 2. Customer-Property junction table assignment
     * 3. Rent invoice/lease creation
     * 4. Lead status update to CONVERTED
     * 5. Instruction status update to ACTIVE_LEASE
     * 6. Property status update to OCCUPIED
     */
    @Transactional
    public LettingInstruction convertToActiveLease(Long instructionId, Long leadIdOrCustomerId,
                                                   LocalDate leaseStartDate, LocalDate leaseEndDate,
                                                   BigDecimal actualRent, BigDecimal depositAmount,
                                                   LocalDate leaseSignedDate) {
        LettingInstruction instruction = getInstructionById(instructionId)
                .orElseThrow(() -> new IllegalArgumentException("Instruction not found: " + instructionId));

        // Allow conversion from OFFER_ACCEPTED, IN_CONTRACTS, or CONTRACTS_COMPLETE
        if (instruction.getStatus() != InstructionStatus.OFFER_ACCEPTED &&
            instruction.getStatus() != InstructionStatus.IN_CONTRACTS &&
            instruction.getStatus() != InstructionStatus.CONTRACTS_COMPLETE) {
            throw new IllegalStateException("Can only convert to lease from OFFER_ACCEPTED, IN_CONTRACTS, or CONTRACTS_COMPLETE status. Current: " + instruction.getStatus());
        }

        Property property = instruction.getProperty();
        Customer tenant;
        Lead lead = null;

        // Step 1: Get or create customer from lead
        // First try to find as lead ID
        Optional<Lead> leadOpt = leadRepository.findById(leadIdOrCustomerId.intValue());
        if (leadOpt.isPresent()) {
            lead = leadOpt.get();

            // Check if lead already has a customer
            if (lead.getCustomer() != null) {
                tenant = lead.getCustomer();
                logger.info("Lead {} already has customer {}", lead.getLeadId(), tenant.getCustomerId());
            } else {
                // Create customer from lead
                tenant = new Customer();
                tenant.setName(lead.getName());
                tenant.setEmail(lead.getEmail());
                tenant.setPhone(lead.getPhone());
                tenant.setCountry("United Kingdom");
                tenant.setIsTenant(true);
                tenant.setCustomerType(CustomerType.TENANT);

                if (lead.getEmploymentStatus() != null) {
                    tenant.setDescription("Employment: " + lead.getEmploymentStatus());
                }

                tenant = customerRepository.save(tenant);
                logger.info("Created customer {} from lead {}", tenant.getCustomerId(), lead.getLeadId());

                // Link customer to lead
                lead.setCustomer(tenant);
                leadRepository.save(lead);
            }
        } else {
            // Try as customer ID
            tenant = customerRepository.findById(leadIdOrCustomerId)
                    .orElseThrow(() -> new IllegalArgumentException("Lead or Customer not found: " + leadIdOrCustomerId));
            logger.info("Using existing customer {}", tenant.getCustomerId());
        }

        // Step 2: Create property-customer assignment (TENANT type)
        CustomerPropertyAssignment assignment = new CustomerPropertyAssignment();
        assignment.setCustomer(tenant);
        assignment.setProperty(property);
        assignment.setAssignmentType(AssignmentType.TENANT);
        assignment.setIsPrimary(true);
        assignment.setStartDate(leaseStartDate);
        assignment.setEndDate(leaseEndDate);
        assignment.setSyncStatus("LOCAL_ONLY");

        customerPropertyAssignmentRepository.save(assignment);
        logger.info("Created property-customer TENANT assignment for property {} and customer {}",
                   property.getId(), tenant.getCustomerId());

        // Step 3: Create rent invoice (recurring lease)
        Invoice rentInvoice = new Invoice();
        rentInvoice.setCustomer(tenant);
        rentInvoice.setProperty(property);
        rentInvoice.setInvoiceType("Rent");
        rentInvoice.setCategoryId("RENT");
        rentInvoice.setFrequency(Invoice.InvoiceFrequency.monthly);
        rentInvoice.setAmount(actualRent);
        rentInvoice.setStartDate(leaseStartDate);
        rentInvoice.setEndDate(leaseEndDate);
        rentInvoice.setDescription("Monthly rent for " + property.getPropertyName());
        rentInvoice.setSyncStatus(Invoice.SyncStatus.pending);

        // Generate lease reference
        String leaseRef = "LEASE-" + property.getId() + "-" +
                         leaseStartDate.toString().replace("-", "");
        rentInvoice.setLeaseReference(leaseRef);

        invoiceRepository.save(rentInvoice);
        logger.info("Created rent invoice with lease reference {} for £{}/month",
                   leaseRef, actualRent);

        // Step 4: If there was a deposit, create deposit invoice
        if (depositAmount != null && depositAmount.compareTo(BigDecimal.ZERO) > 0) {
            Invoice depositInvoice = new Invoice();
            depositInvoice.setCustomer(tenant);
            depositInvoice.setProperty(property);
            depositInvoice.setInvoiceType("Deposit");
            depositInvoice.setCategoryId("DEPOSIT");
            depositInvoice.setFrequency(Invoice.InvoiceFrequency.one_time);
            depositInvoice.setAmount(depositAmount);
            depositInvoice.setStartDate(leaseStartDate);
            depositInvoice.setDescription("Security deposit for " + property.getPropertyName());
            depositInvoice.setSyncStatus(Invoice.SyncStatus.pending);
            depositInvoice.setLeaseReference(leaseRef);

            invoiceRepository.save(depositInvoice);
            logger.info("Created deposit invoice for £{}", depositAmount);
        }

        // Step 5: Update lead status to CONVERTED (if there was a lead)
        if (lead != null) {
            lead.setStatus(LeadStatus.CONVERTED);
            lead.setConvertedAt(LocalDateTime.now());
            lead.setConvertedToCustomer(tenant);
            leadRepository.save(lead);
            logger.info("Marked lead {} as CONVERTED", lead.getLeadId());
        }

        // Step 6: Update instruction to ACTIVE_LEASE
        instruction.setTenant(tenant);
        instruction.setLeaseStartDate(leaseStartDate);
        instruction.setLeaseEndDate(leaseEndDate);
        instruction.setActualRent(actualRent);
        instruction.setDepositAmount(depositAmount);
        instruction.setLeaseSignedDate(leaseSignedDate != null ? leaseSignedDate : LocalDate.now());
        instruction.setStatus(InstructionStatus.ACTIVE_LEASE);

        if (instruction.getAdvertisingEndDate() == null) {
            instruction.setAdvertisingEndDate(LocalDate.now());
        }

        // Calculate metrics
        calculateInstructionMetrics(instruction);

        LettingInstruction saved = lettingInstructionRepository.save(instruction);
        logger.info("Instruction {} converted to ACTIVE_LEASE with tenant {} (lease ref: {})",
                instruction.getInstructionReference(), tenant.getName(), leaseRef);

        // Step 7: Update property status to OCCUPIED
        property.markOccupied();
        propertyRepository.save(property);
        logger.info("Property {} marked as OCCUPIED", property.getId());

        return saved;
    }

    /**
     * Convert instruction to active lease with full tenant address details
     * Overloaded version that accepts tenant address information
     */
    @Transactional
    public LettingInstruction convertToActiveLease(Long instructionId, Long leadIdOrCustomerId,
                                                   LocalDate leaseStartDate, LocalDate leaseEndDate,
                                                   BigDecimal actualRent, BigDecimal depositAmount,
                                                   LocalDate leaseSignedDate,
                                                   String tenantAddress, String tenantCity, String tenantPostcode) {
        LettingInstruction instruction = getInstructionById(instructionId)
                .orElseThrow(() -> new IllegalArgumentException("Instruction not found: " + instructionId));

        // Allow conversion from OFFER_ACCEPTED, IN_CONTRACTS, or CONTRACTS_COMPLETE
        if (instruction.getStatus() != InstructionStatus.OFFER_ACCEPTED &&
            instruction.getStatus() != InstructionStatus.IN_CONTRACTS &&
            instruction.getStatus() != InstructionStatus.CONTRACTS_COMPLETE) {
            throw new IllegalStateException("Can only convert to lease from OFFER_ACCEPTED, IN_CONTRACTS, or CONTRACTS_COMPLETE status. Current: " + instruction.getStatus());
        }

        Property property = instruction.getProperty();
        Customer tenant;
        Lead lead = null;

        // Step 1: Get or create customer from lead
        // First try to find as lead ID
        Optional<Lead> leadOpt = leadRepository.findById(leadIdOrCustomerId.intValue());
        if (leadOpt.isPresent()) {
            lead = leadOpt.get();

            // Check if lead already has a customer
            if (lead.getCustomer() != null) {
                tenant = lead.getCustomer();
                logger.info("Lead {} already has customer {}", lead.getLeadId(), tenant.getCustomerId());
            } else {
                // Create customer from lead
                tenant = new Customer();
                tenant.setName(lead.getName());
                tenant.setEmail(lead.getEmail());
                tenant.setPhone(lead.getPhone());
                tenant.setCountry("United Kingdom");
                tenant.setIsTenant(true);
                tenant.setCustomerType(CustomerType.TENANT);

                if (lead.getEmploymentStatus() != null) {
                    tenant.setDescription("Employment: " + lead.getEmploymentStatus());
                }

                tenant = customerRepository.save(tenant);
                logger.info("Created customer {} from lead {}", tenant.getCustomerId(), lead.getLeadId());

                // Link customer to lead
                lead.setCustomer(tenant);
                leadRepository.save(lead);
            }
        } else {
            // Try as customer ID
            tenant = customerRepository.findById(leadIdOrCustomerId)
                    .orElseThrow(() -> new IllegalArgumentException("Lead or Customer not found: " + leadIdOrCustomerId));
            logger.info("Using existing customer {}", tenant.getCustomerId());
        }

        // Update tenant address if provided
        if (tenantAddress != null && !tenantAddress.trim().isEmpty()) {
            tenant.setAddress(tenantAddress);
        }
        if (tenantCity != null && !tenantCity.trim().isEmpty()) {
            tenant.setCity(tenantCity);
        }
        if (tenantPostcode != null && !tenantPostcode.trim().isEmpty()) {
            tenant.setPostcode(tenantPostcode);
        }

        // Save updated customer details
        tenant = customerRepository.save(tenant);
        logger.info("Updated customer {} address details", tenant.getCustomerId());

        // Step 2: Create property-customer assignment (TENANT type)
        CustomerPropertyAssignment assignment = new CustomerPropertyAssignment();
        assignment.setCustomer(tenant);
        assignment.setProperty(property);
        assignment.setAssignmentType(AssignmentType.TENANT);
        assignment.setIsPrimary(true);
        assignment.setStartDate(leaseStartDate);
        assignment.setEndDate(leaseEndDate);
        assignment.setSyncStatus("LOCAL_ONLY");

        customerPropertyAssignmentRepository.save(assignment);
        logger.info("Created property-customer TENANT assignment for property {} and customer {}",
                   property.getId(), tenant.getCustomerId());

        // Step 3: Create rent invoice (recurring lease)
        Invoice rentInvoice = new Invoice();
        rentInvoice.setCustomer(tenant);
        rentInvoice.setProperty(property);
        rentInvoice.setInvoiceType("Rent");
        rentInvoice.setCategoryId("RENT");
        rentInvoice.setFrequency(Invoice.InvoiceFrequency.monthly);
        rentInvoice.setAmount(actualRent);
        rentInvoice.setStartDate(leaseStartDate);
        rentInvoice.setEndDate(leaseEndDate);
        rentInvoice.setDescription("Monthly rent for " + property.getPropertyName());
        rentInvoice.setSyncStatus(Invoice.SyncStatus.pending);

        // Generate lease reference
        String leaseRef = "LEASE-" + property.getId() + "-" +
                         leaseStartDate.toString().replace("-", "");
        rentInvoice.setLeaseReference(leaseRef);

        invoiceRepository.save(rentInvoice);
        logger.info("Created rent invoice with lease reference {} for £{}/month",
                   leaseRef, actualRent);

        // Step 4: If there was a deposit, create deposit invoice
        if (depositAmount != null && depositAmount.compareTo(BigDecimal.ZERO) > 0) {
            Invoice depositInvoice = new Invoice();
            depositInvoice.setCustomer(tenant);
            depositInvoice.setProperty(property);
            depositInvoice.setInvoiceType("Deposit");
            depositInvoice.setCategoryId("DEPOSIT");
            depositInvoice.setFrequency(Invoice.InvoiceFrequency.one_time);
            depositInvoice.setAmount(depositAmount);
            depositInvoice.setStartDate(leaseStartDate);
            depositInvoice.setDescription("Security deposit for " + property.getPropertyName());
            depositInvoice.setSyncStatus(Invoice.SyncStatus.pending);
            depositInvoice.setLeaseReference(leaseRef);

            invoiceRepository.save(depositInvoice);
            logger.info("Created deposit invoice for £{}", depositAmount);
        }

        // Step 5: Update lead status to CONVERTED (if there was a lead)
        if (lead != null) {
            lead.setStatus(LeadStatus.CONVERTED);
            lead.setConvertedAt(LocalDateTime.now());
            lead.setConvertedToCustomer(tenant);
            leadRepository.save(lead);
            logger.info("Marked lead {} as CONVERTED", lead.getLeadId());
        }

        // Step 6: Update instruction to ACTIVE_LEASE
        instruction.setTenant(tenant);
        instruction.setLeaseStartDate(leaseStartDate);
        instruction.setLeaseEndDate(leaseEndDate);
        instruction.setActualRent(actualRent);
        instruction.setDepositAmount(depositAmount);
        instruction.setLeaseSignedDate(leaseSignedDate != null ? leaseSignedDate : LocalDate.now());
        instruction.setStatus(InstructionStatus.ACTIVE_LEASE);

        if (instruction.getAdvertisingEndDate() == null) {
            instruction.setAdvertisingEndDate(LocalDate.now());
        }

        // Calculate metrics
        calculateInstructionMetrics(instruction);

        LettingInstruction saved = lettingInstructionRepository.save(instruction);
        logger.info("Instruction {} converted to ACTIVE_LEASE with tenant {} (lease ref: {})",
                instruction.getInstructionReference(), tenant.getName(), leaseRef);

        // Step 7: Update property status to OCCUPIED
        property.markOccupied();
        propertyRepository.save(property);
        logger.info("Property {} marked as OCCUPIED", property.getId());

        return saved;
    }

    /**
     * Close instruction (lease ended or instruction withdrawn)
     */
    public LettingInstruction closeInstruction(Long instructionId, String closureReason) {
        LettingInstruction instruction = getInstructionById(instructionId)
                .orElseThrow(() -> new IllegalArgumentException("Instruction not found: " + instructionId));

        instruction.closeInstruction(closureReason);

        LettingInstruction saved = lettingInstructionRepository.save(instruction);
        logger.info("Instruction {} closed: {}", instruction.getInstructionReference(), closureReason);

        return saved;
    }

    /**
     * Mark offer as accepted (holding deposit paid)
     * Simplified workflow: ADVERTISING -> OFFER_ACCEPTED
     */
    @Transactional
    public LettingInstruction markOfferAccepted(Long instructionId, Long tenantId, BigDecimal agreedRent, String notes) {
        LettingInstruction instruction = getInstructionById(instructionId)
                .orElseThrow(() -> new IllegalArgumentException("Instruction not found: " + instructionId));

        if (!instruction.getStatus().canTransitionTo(InstructionStatus.OFFER_ACCEPTED)) {
            throw new IllegalStateException("Cannot accept offer from status: " + instruction.getStatus());
        }

        instruction.setStatus(InstructionStatus.OFFER_ACCEPTED);
        instruction.setActualRent(agreedRent);

        // Update the selected lead's status to IN_CONTRACTS
        // Note: tenantId parameter is actually the leadId from the frontend
        if (tenantId != null) {
            Lead selectedLead = leadRepository.findById(tenantId.intValue())
                    .orElseThrow(() -> new IllegalArgumentException("Lead not found: " + tenantId));

            selectedLead.setStatus(LeadStatus.IN_CONTRACTS);
            leadRepository.save(selectedLead);

            logger.info("Updated lead {} status to IN_CONTRACTS for instruction {}",
                       selectedLead.getLeadId(), instruction.getInstructionReference());

            // Update other leads on this instruction to LOST status
            instruction.getLeads().stream()
                    .filter(lead -> lead.getLeadId() != selectedLead.getLeadId())
                    .filter(lead -> lead.getStatus() != LeadStatus.CONVERTED && lead.getStatus() != LeadStatus.LOST)
                    .forEach(lead -> {
                        lead.setStatus(LeadStatus.LOST);
                        leadRepository.save(lead);
                        logger.info("Marked lead {} as LOST (didn't win property)", lead.getLeadId());
                    });
        }

        if (notes != null && !notes.isEmpty()) {
            String existingNotes = instruction.getInternalNotes() != null ? instruction.getInternalNotes() : "";
            instruction.setInternalNotes(existingNotes + "\n[" + LocalDate.now() + "] Offer Accepted: " + notes);
        }

        LettingInstruction saved = lettingInstructionRepository.save(instruction);
        logger.info("Offer accepted for instruction {} - rent: £{}",
                    instruction.getInstructionReference(), agreedRent);

        return saved;
    }

    /**
     * Cancel instruction
     */
    @Transactional
    public LettingInstruction cancelInstruction(Long instructionId, String reason) {
        LettingInstruction instruction = getInstructionById(instructionId)
                .orElseThrow(() -> new IllegalArgumentException("Instruction not found: " + instructionId));

        instruction.setStatus(InstructionStatus.CANCELLED);
        // Use existing closedDate field if it exists, otherwise skip
        instruction.setClosureReason(reason);

        LettingInstruction saved = lettingInstructionRepository.save(instruction);
        logger.info("Cancelled instruction {} - reason: {}", instruction.getInstructionReference(), reason);

        return saved;
    }

    // ===== LEAD MANAGEMENT =====

    /**
     * Link a lead to an instruction
     */
    public LettingInstruction addLeadToInstruction(Long instructionId, Long leadId) {
        LettingInstruction instruction = getInstructionById(instructionId)
                .orElseThrow(() -> new IllegalArgumentException("Instruction not found: " + instructionId));

        if (!instruction.getStatus().canAcceptLeads()) {
            throw new IllegalStateException("Instruction cannot accept leads in status: " + instruction.getStatus());
        }

        Lead lead = leadRepository.findById(leadId.intValue())
                .orElseThrow(() -> new IllegalArgumentException("Lead not found: " + leadId));

        lead.setLettingInstruction(instruction);
        lead.setProperty(instruction.getProperty());
        leadRepository.save(lead);

        // Update metrics
        instruction.incrementEnquiryCount();
        lettingInstructionRepository.save(instruction);

        logger.info("Added lead {} to instruction {}", lead.getLeadId(), instruction.getInstructionReference());

        return instruction;
    }

    /**
     * Remove lead from instruction
     */
    public LettingInstruction removeLeadFromInstruction(Long instructionId, Long leadId) {
        LettingInstruction instruction = getInstructionById(instructionId)
                .orElseThrow(() -> new IllegalArgumentException("Instruction not found: " + instructionId));

        Lead lead = leadRepository.findById(leadId.intValue())
                .orElseThrow(() -> new IllegalArgumentException("Lead not found: " + leadId));

        lead.setLettingInstruction(null);
        leadRepository.save(lead);

        logger.info("Removed lead {} from instruction {}", lead.getLeadId(), instruction.getInstructionReference());

        return instruction;
    }

    // ===== METRICS AND REPORTING =====

    /**
     * Calculate and update metrics for an instruction
     */
    public void calculateInstructionMetrics(LettingInstruction instruction) {
        // Days to fill - from advertising start to lease signed
        if (instruction.getAdvertisingStartDate() != null && instruction.getLeaseSignedDate() != null) {
            long daysToFill = ChronoUnit.DAYS.between(
                    instruction.getAdvertisingStartDate(),
                    instruction.getLeaseSignedDate()
            );
            instruction.setDaysToFill((int) daysToFill);
        }

        // Days vacant - from expected vacancy to lease start
        if (instruction.getExpectedVacancyDate() != null && instruction.getLeaseStartDate() != null) {
            long daysVacant = ChronoUnit.DAYS.between(
                    instruction.getExpectedVacancyDate(),
                    instruction.getLeaseStartDate()
            );
            instruction.setDaysVacant((int) daysVacant);
        }

        // Conversion rate - viewings that led to conversion
        if (instruction.getNumberOfViewings() != null && instruction.getNumberOfViewings() > 0) {
            double conversionRate = 1.0 / instruction.getNumberOfViewings();
            instruction.setConversionRate(BigDecimal.valueOf(conversionRate)
                    .setScale(4, RoundingMode.HALF_UP)
                    .doubleValue());
        } else if (instruction.getNumberOfEnquiries() != null && instruction.getNumberOfEnquiries() > 0) {
            double conversionRate = 1.0 / instruction.getNumberOfEnquiries();
            instruction.setConversionRate(BigDecimal.valueOf(conversionRate)
                    .setScale(4, RoundingMode.HALF_UP)
                    .doubleValue());
        }
    }

    /**
     * Update viewing count for instruction
     */
    public LettingInstruction incrementViewingCount(Long instructionId) {
        LettingInstruction instruction = getInstructionById(instructionId)
                .orElseThrow(() -> new IllegalArgumentException("Instruction not found: " + instructionId));

        instruction.incrementViewingCount();
        return lettingInstructionRepository.save(instruction);
    }

    /**
     * Generic status transition method for drag-and-drop Kanban
     * Validates transition rules and updates instruction status
     */
    public LettingInstruction transitionStatus(Long instructionId, InstructionStatus newStatus) {
        LettingInstruction instruction = getInstructionById(instructionId)
                .orElseThrow(() -> new IllegalArgumentException("Instruction not found: " + instructionId));

        InstructionStatus currentStatus = instruction.getStatus();

        // Validate transition
        if (!currentStatus.canTransitionTo(newStatus)) {
            throw new IllegalStateException("Invalid transition from " + currentStatus.getDisplayName() +
                    " to " + newStatus.getDisplayName());
        }

        // Update status
        instruction.setStatus(newStatus);
        instruction.setUpdatedAt(LocalDateTime.now());

        logger.info("Instruction {} transitioned from {} to {}",
                instructionId, currentStatus.name(), newStatus.name());

        return lettingInstructionRepository.save(instruction);
    }

    /**
     * Recalculate metrics for all active instructions
     */
    public void recalculateAllMetrics() {
        List<LettingInstruction> instructions = lettingInstructionRepository.findAll();
        for (LettingInstruction instruction : instructions) {
            if (instruction.getStatus() == InstructionStatus.ACTIVE_LEASE ||
                    instruction.getStatus() == InstructionStatus.CLOSED) {
                calculateInstructionMetrics(instruction);
                lettingInstructionRepository.save(instruction);
            }
        }
        logger.info("Recalculated metrics for {} instructions", instructions.size());
    }

    // ===== DASHBOARD AND REPORTING QUERIES =====

    /**
     * Get all active instructions (including new/preparing)
     */
    @Transactional(readOnly = true)
    public List<LettingInstruction> getAllActiveInstructions() {
        return lettingInstructionRepository.findAllActiveInstructions();
    }

    /**
     * Get all active marketing instructions
     */
    @Transactional(readOnly = true)
    public List<LettingInstruction> getActiveMarketingInstructions() {
        return lettingInstructionRepository.findAllActiveMarketingInstructions();
    }

    /**
     * Get all active leases
     */
    @Transactional(readOnly = true)
    public List<LettingInstruction> getActiveLeases() {
        return lettingInstructionRepository.findAllActiveLeases();
    }

    /**
     * Get instructions by status
     */
    @Transactional(readOnly = true)
    public List<LettingInstruction> getInstructionsByStatus(InstructionStatus status) {
        return lettingInstructionRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    /**
     * Get leases expiring in next N days
     */
    @Transactional(readOnly = true)
    public List<LettingInstruction> getLeasesExpiringInDays(int days) {
        LocalDate futureDate = LocalDate.now().plusDays(days);
        return lettingInstructionRepository.findLeasesExpiringBefore(futureDate);
    }

    /**
     * Get stale listings (advertising for more than N days)
     */
    @Transactional(readOnly = true)
    public List<LettingInstruction> getStaleListings(int days) {
        LocalDate thresholdDate = LocalDate.now().minusDays(days);
        return lettingInstructionRepository.findStaleListings(thresholdDate);
    }

    /**
     * Get low performing instructions
     */
    @Transactional(readOnly = true)
    public List<LettingInstruction> getLowPerformingInstructions(double conversionThreshold) {
        return lettingInstructionRepository.findLowPerformingInstructions(conversionThreshold);
    }

    /**
     * Get average days to fill
     */
    @Transactional(readOnly = true)
    public Double getAverageDaysToFill() {
        return lettingInstructionRepository.calculateAverageDaysToFill();
    }

    /**
     * Get average conversion rate
     */
    @Transactional(readOnly = true)
    public Double getAverageConversionRate() {
        return lettingInstructionRepository.calculateAverageConversionRate();
    }

    /**
     * Search instructions
     */
    @Transactional(readOnly = true)
    public List<LettingInstruction> searchInstructions(String searchTerm) {
        return lettingInstructionRepository.searchByPropertyNameOrAddress(searchTerm);
    }

    // ===== HELPER METHODS =====

    /**
     * Generate unique instruction reference
     * Format: INST-{propertyId}-{yyyyMMdd}
     */
    private String generateInstructionReference(Property property) {
        String baseReference = String.format("INST-%d-%s",
                property.getId(),
                LocalDate.now().toString().replace("-", ""));

        // Check if reference exists, add suffix if needed
        Optional<LettingInstruction> existing = lettingInstructionRepository.findByInstructionReference(baseReference);
        if (existing.isPresent()) {
            int suffix = 1;
            String newReference;
            do {
                newReference = baseReference + "-" + suffix;
                suffix++;
            } while (lettingInstructionRepository.findByInstructionReference(newReference).isPresent());
            return newReference;
        }

        return baseReference;
    }

    /**
     * Validate instruction can transition to new status
     */
    public boolean canTransitionToStatus(Long instructionId, InstructionStatus newStatus) {
        LettingInstruction instruction = getInstructionById(instructionId)
                .orElseThrow(() -> new IllegalArgumentException("Instruction not found: " + instructionId));

        return instruction.getStatus().canTransitionTo(newStatus);
    }

    /**
     * Get instruction summary for dashboard
     */
    @Transactional(readOnly = true)
    public InstructionSummary getInstructionSummary() {
        InstructionSummary summary = new InstructionSummary();

        List<LettingInstruction> instructionReceived = getInstructionsByStatus(InstructionStatus.INSTRUCTION_RECEIVED);
        List<LettingInstruction> preparing = getInstructionsByStatus(InstructionStatus.PREPARING);
        List<LettingInstruction> advertising = getInstructionsByStatus(InstructionStatus.ADVERTISING);
        List<LettingInstruction> viewings = getInstructionsByStatus(InstructionStatus.VIEWINGS_IN_PROGRESS);
        List<LettingInstruction> offersOld = getInstructionsByStatus(InstructionStatus.OFFER_MADE);
        List<LettingInstruction> offersNew = getInstructionsByStatus(InstructionStatus.OFFER_ACCEPTED);
        List<LettingInstruction> activeLeases = getActiveLeases();

        summary.setPreparingCount(instructionReceived.size() + preparing.size());
        summary.setAdvertisingCount(advertising.size() + viewings.size()); // Include viewings in advertising count
        summary.setViewingsCount(viewings.size()); // Keep for backward compatibility
        summary.setOffersCount(offersOld.size() + offersNew.size()); // Both old and new statuses
        summary.setActiveLeasesCount(activeLeases.size());
        summary.setAverageDaysToFill(getAverageDaysToFill());
        summary.setAverageConversionRate(getAverageConversionRate());

        return summary;
    }

    // ===== INNER CLASSES =====

    /**
     * DTO for dashboard summary statistics
     */
    public static class InstructionSummary {
        private int preparingCount;
        private int advertisingCount;
        private int viewingsCount;
        private int offersCount;
        private int activeLeasesCount;
        private Double averageDaysToFill;
        private Double averageConversionRate;

        // Getters and setters
        public int getPreparingCount() { return preparingCount; }
        public void setPreparingCount(int preparingCount) { this.preparingCount = preparingCount; }

        public int getAdvertisingCount() { return advertisingCount; }
        public void setAdvertisingCount(int advertisingCount) { this.advertisingCount = advertisingCount; }

        public int getViewingsCount() { return viewingsCount; }
        public void setViewingsCount(int viewingsCount) { this.viewingsCount = viewingsCount; }

        public int getOffersCount() { return offersCount; }
        public void setOffersCount(int offersCount) { this.offersCount = offersCount; }

        public int getActiveLeasesCount() { return activeLeasesCount; }
        public void setActiveLeasesCount(int activeLeasesCount) { this.activeLeasesCount = activeLeasesCount; }

        public Double getAverageDaysToFill() { return averageDaysToFill; }
        public void setAverageDaysToFill(Double averageDaysToFill) { this.averageDaysToFill = averageDaysToFill; }

        public Double getAverageConversionRate() { return averageConversionRate; }
        public void setAverageConversionRate(Double averageConversionRate) {
            this.averageConversionRate = averageConversionRate;
        }
    }
}

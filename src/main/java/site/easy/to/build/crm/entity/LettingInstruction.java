package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * LettingInstruction Entity - Represents a single letting/rental period for a property.
 *
 * This entity tracks the complete lifecycle of letting a property:
 * - Instruction received from property owner
 * - Preparation and advertising phase
 * - Lead management and viewings
 * - Conversion to active lease
 * - Lease termination and new instruction cycle
 *
 * Key Concept: A property can have MULTIPLE letting instructions over time.
 * Each instruction represents one complete letting cycle from vacant to occupied.
 *
 * Evolution: INSTRUCTION_RECEIVED → PREPARING → ADVERTISING → VIEWINGS_IN_PROGRESS → OFFER_MADE → ACTIVE_LEASE → CLOSED
 */
@Entity
@Table(name = "letting_instructions")
public class LettingInstruction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== PROPERTY RELATIONSHIP =====

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    @NotNull(message = "Property is required")
    private Property property;

    // ===== INSTRUCTION STATUS =====

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private InstructionStatus status = InstructionStatus.INSTRUCTION_RECEIVED;

    // ===== INSTRUCTION DATES =====

    @Column(name = "instruction_received_date")
    private LocalDate instructionReceivedDate;

    @Column(name = "notice_given_date")
    private LocalDate noticeGivenDate;

    @Column(name = "expected_vacancy_date")
    private LocalDate expectedVacancyDate;

    @Column(name = "available_from_date")
    private LocalDate availableFromDate;

    @Column(name = "advertising_start_date")
    private LocalDate advertisingStartDate;

    @Column(name = "advertising_end_date")
    private LocalDate advertisingEndDate;

    // ===== TARGET DETAILS (Initial Instruction) =====

    @Column(name = "target_rent", precision = 10, scale = 2)
    @DecimalMin(value = "0.00", message = "Target rent must be positive")
    private BigDecimal targetRent;

    @Column(name = "target_lease_length_months")
    @Min(value = 1, message = "Lease length must be at least 1 month")
    private Integer targetLeaseLengthMonths;

    @Column(name = "property_description", columnDefinition = "TEXT")
    private String propertyDescription;

    @Column(name = "key_features", columnDefinition = "TEXT")
    private String keyFeatures;

    // ===== ACTUAL DETAILS (When Converted to Lease) =====

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Customer tenant; // Set when status = ACTIVE_LEASE

    @Column(name = "actual_rent", precision = 10, scale = 2)
    @DecimalMin(value = "0.00", message = "Actual rent must be positive")
    private BigDecimal actualRent;

    @Column(name = "lease_start_date")
    private LocalDate leaseStartDate;

    @Column(name = "lease_end_date")
    private LocalDate leaseEndDate;

    @Column(name = "deposit_amount", precision = 10, scale = 2)
    @DecimalMin(value = "0.00", message = "Deposit must be positive")
    private BigDecimal depositAmount;

    @Column(name = "lease_signed_date")
    private LocalDate leaseSignedDate;

    // ===== CLOSURE DETAILS =====

    @Column(name = "closure_date")
    private LocalDate closureDate;

    @Column(name = "closure_reason")
    private String closureReason; // "LEASE_STARTED", "WITHDRAWN", "CANCELLED", "LEASE_ENDED"

    // ===== RELATIONSHIPS TO CHILD ENTITIES =====

    @OneToMany(mappedBy = "lettingInstruction", cascade = CascadeType.ALL)
    private List<Lead> leads = new ArrayList<>();

    @OneToMany(mappedBy = "lettingInstruction", cascade = CascadeType.ALL)
    private List<PropertyViewing> viewings = new ArrayList<>();

    @OneToMany(mappedBy = "lettingInstruction", cascade = CascadeType.ALL)
    private List<PropertyVacancyTask> tasks = new ArrayList<>();

    @OneToMany(mappedBy = "lettingInstruction", cascade = CascadeType.ALL)
    private List<Invoice> invoices = new ArrayList<>();

    // ===== METRICS (Calculated or Updated) =====

    @Column(name = "days_to_fill")
    private Integer daysToFill; // From advertising_start to lease_start

    @Column(name = "days_vacant")
    private Integer daysVacant; // From expected_vacancy to lease_start

    @Column(name = "number_of_enquiries")
    private Integer numberOfEnquiries = 0;

    @Column(name = "number_of_viewings")
    private Integer numberOfViewings = 0;

    @Column(name = "conversion_rate")
    private Double conversionRate; // Percentage: (converted leads / total leads) * 100

    // ===== REFERENCE AND TRACKING =====

    @Column(name = "instruction_reference", unique = true, length = 50)
    private String instructionReference; // e.g., "INST-2024-001"

    @Column(name = "marketing_notes", columnDefinition = "TEXT")
    private String marketingNotes;

    @Column(name = "internal_notes", columnDefinition = "TEXT")
    private String internalNotes;

    // ===== AUDIT FIELDS =====

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ===== CONSTRUCTORS =====

    public LettingInstruction() {
        this.createdAt = LocalDateTime.now();
        this.status = InstructionStatus.INSTRUCTION_RECEIVED;
    }

    public LettingInstruction(Property property, LocalDate instructionReceivedDate, BigDecimal targetRent) {
        this();
        this.property = property;
        this.instructionReceivedDate = instructionReceivedDate;
        this.targetRent = targetRent;
    }

    // ===== LIFECYCLE CALLBACKS =====

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();

        // Auto-generate instruction reference if not set
        if (this.instructionReference == null && this.property != null) {
            this.instructionReference = generateInstructionReference();
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();

        // Recalculate metrics
        calculateMetrics();
    }

    // ===== BUSINESS LOGIC METHODS =====

    /**
     * Generate instruction reference based on property and date
     */
    private String generateInstructionReference() {
        String propertyRef = property.getId().toString();
        String dateRef = instructionReceivedDate != null ?
                        instructionReceivedDate.toString().replace("-", "") :
                        LocalDate.now().toString().replace("-", "");
        return "INST-" + propertyRef + "-" + dateRef;
    }

    /**
     * Start advertising the property
     */
    public void startAdvertising(LocalDate startDate) {
        if (this.status == InstructionStatus.INSTRUCTION_RECEIVED ||
            this.status == InstructionStatus.PREPARING) {
            this.advertisingStartDate = startDate;
            this.status = InstructionStatus.ADVERTISING;
        } else {
            throw new IllegalStateException("Cannot start advertising from status: " + this.status);
        }
    }

    /**
     * Move to viewings in progress
     */
    public void moveToViewings() {
        if (this.status == InstructionStatus.ADVERTISING) {
            this.status = InstructionStatus.VIEWINGS_IN_PROGRESS;
        } else {
            throw new IllegalStateException("Cannot move to viewings from status: " + this.status);
        }
    }

    /**
     * Mark offer made
     */
    public void markOfferMade() {
        if (this.status == InstructionStatus.VIEWINGS_IN_PROGRESS ||
            this.status == InstructionStatus.ADVERTISING) {
            this.status = InstructionStatus.OFFER_MADE;
        } else {
            throw new IllegalStateException("Cannot mark offer made from status: " + this.status);
        }
    }

    /**
     * Convert to active lease
     */
    public void convertToActiveLease(Customer tenant, LocalDate leaseStart, LocalDate leaseEnd,
                                     BigDecimal rent, BigDecimal deposit) {
        if (this.status != InstructionStatus.OFFER_MADE) {
            throw new IllegalStateException("Cannot convert to lease from status: " + this.status);
        }

        this.tenant = tenant;
        this.leaseStartDate = leaseStart;
        this.leaseEndDate = leaseEnd;
        this.actualRent = rent;
        this.depositAmount = deposit;
        this.leaseSignedDate = LocalDate.now();
        this.status = InstructionStatus.ACTIVE_LEASE;

        if (this.advertisingEndDate == null) {
            this.advertisingEndDate = LocalDate.now();
        }

        // Calculate metrics
        calculateMetrics();
    }

    /**
     * Close instruction
     */
    public void closeInstruction(String reason) {
        this.status = InstructionStatus.CLOSED;
        this.closureDate = LocalDate.now();
        this.closureReason = reason;

        if (this.advertisingEndDate == null && this.advertisingStartDate != null) {
            this.advertisingEndDate = LocalDate.now();
        }

        calculateMetrics();
    }

    /**
     * Calculate performance metrics
     */
    public void calculateMetrics() {
        // Days to fill (from advertising start to lease start)
        if (this.advertisingStartDate != null && this.leaseStartDate != null) {
            this.daysToFill = (int) ChronoUnit.DAYS.between(this.advertisingStartDate, this.leaseStartDate);
        }

        // Days vacant (from expected vacancy to lease start)
        if (this.expectedVacancyDate != null && this.leaseStartDate != null) {
            this.daysVacant = (int) ChronoUnit.DAYS.between(this.expectedVacancyDate, this.leaseStartDate);
        }

        // Number of enquiries
        this.numberOfEnquiries = leads != null ? leads.size() : 0;

        // Number of viewings
        this.numberOfViewings = viewings != null ? viewings.size() : 0;

        // Conversion rate
        if (leads != null && !leads.isEmpty()) {
            long convertedLeads = leads.stream()
                .filter(lead -> "converted".equals(lead.getStatus()))
                .count();
            this.conversionRate = (convertedLeads * 100.0) / leads.size();
        }
    }

    /**
     * Add a lead to this instruction
     */
    public void addLead(Lead lead) {
        if (leads == null) {
            leads = new ArrayList<>();
        }
        leads.add(lead);
        lead.setLettingInstruction(this);
        this.numberOfEnquiries = leads.size();
    }

    /**
     * Add a viewing to this instruction
     */
    public void addViewing(PropertyViewing viewing) {
        if (viewings == null) {
            viewings = new ArrayList<>();
        }
        viewings.add(viewing);
        viewing.setLettingInstruction(this);
        this.numberOfViewings = viewings.size();
    }

    /**
     * Remove a lead from this instruction (properly unlinks without deleting)
     */
    public void removeLead(Lead lead) {
        if (leads != null) {
            leads.remove(lead);
            lead.setLettingInstruction(null); // Explicitly unlink
            this.numberOfEnquiries = leads.size();
        }
    }

    /**
     * Remove a viewing from this instruction (properly unlinks without deleting)
     */
    public void removeViewing(PropertyViewing viewing) {
        if (viewings != null) {
            viewings.remove(viewing);
            viewing.setLettingInstruction(null); // Explicitly unlink
            this.numberOfViewings = viewings.size();
        }
    }

    /**
     * Increment enquiry count
     */
    public void incrementEnquiryCount() {
        if (this.numberOfEnquiries == null) {
            this.numberOfEnquiries = 0;
        }
        this.numberOfEnquiries++;
    }

    /**
     * Increment viewing count
     */
    public void incrementViewingCount() {
        if (this.numberOfViewings == null) {
            this.numberOfViewings = 0;
        }
        this.numberOfViewings++;
    }

    /**
     * Check if instruction is currently active (can accept new leads)
     */
    public boolean isActive() {
        return this.status == InstructionStatus.ADVERTISING ||
               this.status == InstructionStatus.VIEWINGS_IN_PROGRESS ||
               this.status == InstructionStatus.OFFER_MADE;
    }

    /**
     * Check if instruction is a current lease
     */
    public boolean isActiveLease() {
        return this.status == InstructionStatus.ACTIVE_LEASE;
    }

    /**
     * Get days since advertising started
     */
    public Integer getDaysAdvertising() {
        if (this.advertisingStartDate != null) {
            LocalDate endDate = this.advertisingEndDate != null ?
                               this.advertisingEndDate : LocalDate.now();
            return (int) ChronoUnit.DAYS.between(this.advertisingStartDate, endDate);
        }
        return 0;
    }

    /**
     * Get days until expected vacancy
     */
    public Integer getDaysUntilVacancy() {
        if (this.expectedVacancyDate != null) {
            return (int) ChronoUnit.DAYS.between(LocalDate.now(), this.expectedVacancyDate);
        }
        return null;
    }

    // ===== GETTERS AND SETTERS =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Property getProperty() { return property; }
    public void setProperty(Property property) { this.property = property; }

    public InstructionStatus getStatus() { return status; }
    public void setStatus(InstructionStatus status) { this.status = status; }

    public LocalDate getInstructionReceivedDate() { return instructionReceivedDate; }
    public void setInstructionReceivedDate(LocalDate instructionReceivedDate) {
        this.instructionReceivedDate = instructionReceivedDate;
    }

    public LocalDate getNoticeGivenDate() { return noticeGivenDate; }
    public void setNoticeGivenDate(LocalDate noticeGivenDate) { this.noticeGivenDate = noticeGivenDate; }

    public LocalDate getExpectedVacancyDate() { return expectedVacancyDate; }
    public void setExpectedVacancyDate(LocalDate expectedVacancyDate) {
        this.expectedVacancyDate = expectedVacancyDate;
    }

    public LocalDate getAvailableFromDate() { return availableFromDate; }
    public void setAvailableFromDate(LocalDate availableFromDate) {
        this.availableFromDate = availableFromDate;
    }

    public LocalDate getAdvertisingStartDate() { return advertisingStartDate; }
    public void setAdvertisingStartDate(LocalDate advertisingStartDate) {
        this.advertisingStartDate = advertisingStartDate;
    }

    public LocalDate getAdvertisingEndDate() { return advertisingEndDate; }
    public void setAdvertisingEndDate(LocalDate advertisingEndDate) {
        this.advertisingEndDate = advertisingEndDate;
    }

    public BigDecimal getTargetRent() { return targetRent; }
    public void setTargetRent(BigDecimal targetRent) { this.targetRent = targetRent; }

    public Integer getTargetLeaseLengthMonths() { return targetLeaseLengthMonths; }
    public void setTargetLeaseLengthMonths(Integer targetLeaseLengthMonths) {
        this.targetLeaseLengthMonths = targetLeaseLengthMonths;
    }

    public String getPropertyDescription() { return propertyDescription; }
    public void setPropertyDescription(String propertyDescription) {
        this.propertyDescription = propertyDescription;
    }

    public String getKeyFeatures() { return keyFeatures; }
    public void setKeyFeatures(String keyFeatures) { this.keyFeatures = keyFeatures; }

    public Customer getTenant() { return tenant; }
    public void setTenant(Customer tenant) { this.tenant = tenant; }

    public BigDecimal getActualRent() { return actualRent; }
    public void setActualRent(BigDecimal actualRent) { this.actualRent = actualRent; }

    public LocalDate getLeaseStartDate() { return leaseStartDate; }
    public void setLeaseStartDate(LocalDate leaseStartDate) { this.leaseStartDate = leaseStartDate; }

    public LocalDate getLeaseEndDate() { return leaseEndDate; }
    public void setLeaseEndDate(LocalDate leaseEndDate) { this.leaseEndDate = leaseEndDate; }

    public BigDecimal getDepositAmount() { return depositAmount; }
    public void setDepositAmount(BigDecimal depositAmount) { this.depositAmount = depositAmount; }

    public LocalDate getLeaseSignedDate() { return leaseSignedDate; }
    public void setLeaseSignedDate(LocalDate leaseSignedDate) { this.leaseSignedDate = leaseSignedDate; }

    public LocalDate getClosureDate() { return closureDate; }
    public void setClosureDate(LocalDate closureDate) { this.closureDate = closureDate; }

    public String getClosureReason() { return closureReason; }
    public void setClosureReason(String closureReason) { this.closureReason = closureReason; }

    public List<Lead> getLeads() { return leads; }
    public void setLeads(List<Lead> leads) { this.leads = leads; }

    public List<PropertyViewing> getViewings() { return viewings; }
    public void setViewings(List<PropertyViewing> viewings) { this.viewings = viewings; }

    public List<PropertyVacancyTask> getTasks() { return tasks; }
    public void setTasks(List<PropertyVacancyTask> tasks) { this.tasks = tasks; }

    public List<Invoice> getInvoices() { return invoices; }
    public void setInvoices(List<Invoice> invoices) { this.invoices = invoices; }

    public Integer getDaysToFill() { return daysToFill; }
    public void setDaysToFill(Integer daysToFill) { this.daysToFill = daysToFill; }

    public Integer getDaysVacant() { return daysVacant; }
    public void setDaysVacant(Integer daysVacant) { this.daysVacant = daysVacant; }

    public Integer getNumberOfEnquiries() { return numberOfEnquiries; }
    public void setNumberOfEnquiries(Integer numberOfEnquiries) {
        this.numberOfEnquiries = numberOfEnquiries;
    }

    public Integer getNumberOfViewings() { return numberOfViewings; }
    public void setNumberOfViewings(Integer numberOfViewings) {
        this.numberOfViewings = numberOfViewings;
    }

    public Double getConversionRate() { return conversionRate; }
    public void setConversionRate(Double conversionRate) { this.conversionRate = conversionRate; }

    public String getInstructionReference() { return instructionReference; }
    public void setInstructionReference(String instructionReference) {
        this.instructionReference = instructionReference;
    }

    public String getMarketingNotes() { return marketingNotes; }
    public void setMarketingNotes(String marketingNotes) { this.marketingNotes = marketingNotes; }

    public String getInternalNotes() { return internalNotes; }
    public void setInternalNotes(String internalNotes) { this.internalNotes = internalNotes; }

    public User getCreatedByUser() { return createdByUser; }
    public void setCreatedByUser(User createdByUser) { this.createdByUser = createdByUser; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    // ===== toString =====

    @Override
    public String toString() {
        return String.format("LettingInstruction{id=%d, reference='%s', property=%s, status=%s, targetRent=%s}",
                           id, instructionReference,
                           property != null ? property.getAddressLine1() : null,
                           status, targetRent);
    }
}

package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "contractor_bids")
public class ContractorBid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    // CORRECTED: Reference to Customer entity instead of separate Contractor entity
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contractor_id", nullable = false)
    private Customer contractor; // Uses unified Customer entity

    @Column(name = "bid_amount", precision = 10, scale = 2)
    private BigDecimal bidAmount;

    @Column(name = "estimated_hours")
    private Integer estimatedHours;

    @Column(name = "bid_description", columnDefinition = "TEXT")
    private String bidDescription;

    @Column(name = "materials_cost", precision = 10, scale = 2)
    private BigDecimal materialsCost;

    @Column(name = "labour_cost", precision = 10, scale = 2)
    private BigDecimal labourCost;

    @Column(name = "estimated_start_date")
    private LocalDate estimatedStartDate;

    @Column(name = "estimated_completion_date")
    private LocalDate estimatedCompletionDate;

    @Column(name = "warranty_offered_months")
    private Integer warrantyOfferedMonths = 12;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "invited"; // invited, submitted, under-review, accepted, rejected, withdrawn, expired

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // MISSING FIELDS FROM SERVICE IMPLEMENTATION
    @Column(name = "invited_at")
    private LocalDateTime invitedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "priority_level", length = 20)
    private String priorityLevel; // standard, express, emergency

    @Column(name = "estimated_completion_hours")
    private Integer estimatedCompletionHours;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    // PayProp integration fields
    @Column(name = "payprop_synced")
    private Boolean payPropSynced = false;

    @Column(name = "payprop_beneficiary_id", length = 50)
    private String payPropBeneficiaryId;

    // Constructors
    public ContractorBid() {
    }

    public ContractorBid(Ticket ticket, Customer contractor, BigDecimal bidAmount, String bidDescription) {
        this.ticket = ticket;
        this.contractor = contractor;
        this.bidAmount = bidAmount;
        this.bidDescription = bidDescription;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Ticket getTicket() { return ticket; }
    public void setTicket(Ticket ticket) { this.ticket = ticket; }

    public Customer getContractor() { return contractor; }
    public void setContractor(Customer contractor) { 
        // Validation: ensure the customer is actually a contractor
        if (contractor != null && !contractor.getIsContractor()) {
            throw new IllegalArgumentException("Customer must be a contractor to submit bids");
        }
        this.contractor = contractor; 
    }

    public BigDecimal getBidAmount() { return bidAmount; }
    public void setBidAmount(BigDecimal bidAmount) { this.bidAmount = bidAmount; }

    public Integer getEstimatedHours() { return estimatedHours; }
    public void setEstimatedHours(Integer estimatedHours) { this.estimatedHours = estimatedHours; }

    public String getBidDescription() { return bidDescription; }
    public void setBidDescription(String bidDescription) { this.bidDescription = bidDescription; }

    public BigDecimal getMaterialsCost() { return materialsCost; }
    public void setMaterialsCost(BigDecimal materialsCost) { this.materialsCost = materialsCost; }

    public BigDecimal getLabourCost() { return labourCost; }
    public void setLabourCost(BigDecimal labourCost) { this.labourCost = labourCost; }

    public LocalDate getEstimatedStartDate() { return estimatedStartDate; }
    public void setEstimatedStartDate(LocalDate estimatedStartDate) { this.estimatedStartDate = estimatedStartDate; }

    public LocalDate getEstimatedCompletionDate() { return estimatedCompletionDate; }
    public void setEstimatedCompletionDate(LocalDate estimatedCompletionDate) { this.estimatedCompletionDate = estimatedCompletionDate; }

    public Integer getWarrantyOfferedMonths() { return warrantyOfferedMonths; }
    public void setWarrantyOfferedMonths(Integer warrantyOfferedMonths) { this.warrantyOfferedMonths = warrantyOfferedMonths; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public User getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(User reviewedBy) { this.reviewedBy = reviewedBy; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getInvitedAt() { return invitedAt; }
    public void setInvitedAt(LocalDateTime invitedAt) { this.invitedAt = invitedAt; }

    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }

    public LocalDateTime getRejectedAt() { return rejectedAt; }
    public void setRejectedAt(LocalDateTime rejectedAt) { this.rejectedAt = rejectedAt; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public String getPriorityLevel() { return priorityLevel; }
    public void setPriorityLevel(String priorityLevel) { this.priorityLevel = priorityLevel; }

    public Integer getEstimatedCompletionHours() { return estimatedCompletionHours; }
    public void setEstimatedCompletionHours(Integer estimatedCompletionHours) { 
        this.estimatedCompletionHours = estimatedCompletionHours; 
    }

    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }

    public Boolean getPayPropSynced() { return payPropSynced; }
    public void setPayPropSynced(Boolean payPropSynced) { this.payPropSynced = payPropSynced; }

    public String getPayPropBeneficiaryId() { return payPropBeneficiaryId; }
    public void setPayPropBeneficiaryId(String payPropBeneficiaryId) { this.payPropBeneficiaryId = payPropBeneficiaryId; }

    // Helper Methods
    public boolean isSubmitted() {
        return "submitted".equals(status);
    }

    public boolean isAccepted() {
        return "accepted".equals(status);
    }

    public boolean isRejected() {
        return "rejected".equals(status);
    }

    public boolean canBeEdited() {
        return "invited".equals(status) || "submitted".equals(status);
    }

    public BigDecimal getTotalCost() {
        BigDecimal total = bidAmount != null ? bidAmount : BigDecimal.ZERO;
        if (materialsCost != null) {
            total = total.add(materialsCost);
        }
        if (labourCost != null) {
            total = total.add(labourCost);
        }
        return total;
    }

    public boolean hasValidBankDetails() {
        return contractor != null && contractor.hasValidBankDetails();
    }

    public String getContractorName() {
        return contractor != null ? contractor.getFullName() : "Unknown Contractor";
    }

    public String getContractorEmail() {
        return contractor != null ? contractor.getEmail() : null;
    }

    public String getContractorPhone() {
        return contractor != null ? contractor.getPhone() : null;
    }

    // PayProp Integration Helper
    public boolean needsPayPropBeneficiarySync() {
        return contractor != null && 
               contractor.getIsContractor() && 
               (contractor.getPayPropEntityId() == null || !contractor.getPayPropSynced());
    }

    // Lifecycle callbacks
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        
        // Auto-populate PayProp beneficiary ID if contractor is already synced
        if (contractor != null && contractor.getPayPropEntityId() != null) {
            payPropBeneficiaryId = contractor.getPayPropEntityId();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "ContractorBid{" +
               "id=" + id +
               ", ticketId=" + (ticket != null ? ticket.getTicketId() : null) +
               ", contractorName='" + getContractorName() + '\'' +
               ", bidAmount=" + bidAmount +
               ", status='" + status + '\'' +
               '}';
    }
}
package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "trigger_ticket")
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_id")
    private int ticketId;

    @Column(name = "subject")
    @NotBlank(message = "Subject is required")
    private String subject;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status")
    @NotBlank(message = "Status is required")
    @Pattern(regexp = "^(open|assigned|bidding|bid-review|contractor-selected|work-in-progress|work-completed|payment-pending|payment-processed|on-hold|resolved|closed|reopened|pending-customer-response|escalated|archived)$", 
             message = "Invalid status")
    private String status;

    @Column(name = "priority")
    @NotBlank(message = "Priority is required")
    @Pattern(regexp = "^(low|medium|high|urgent|critical|emergency)$", message = "Invalid priority")
    private String priority;

    @Column(name = "type")
    @Pattern(regexp = "^(maintenance|emergency|support|billing|general|complaint|request)$", message = "Invalid ticket type")
    private String type;

    // ===== PAYPROP INTEGRATION FIELDS =====
    
    @Column(name = "pay_prop_ticket_id", length = 50)
    private String payPropTicketId;
    
    @Column(name = "pay_prop_property_id", length = 50)
    private String payPropPropertyId;
    
    @Column(name = "pay_prop_tenant_id", length = 50)  
    private String payPropTenantId;
    
    @Column(name = "pay_prop_category_id", length = 50)
    private String payPropCategoryId;
    
    @Column(name = "pay_prop_synced")
    private Boolean payPropSynced = false;
    
    @Column(name = "pay_prop_last_sync")
    private LocalDateTime payPropLastSync;

    // ===== CONTRACTOR MANAGEMENT FIELDS =====
    
    @Column(name = "selected_contractor_id")
    private Long selectedContractorId;
    
    @Column(name = "approved_amount", precision = 10, scale = 2)
    private BigDecimal approvedAmount;
    
    @Column(name = "actual_cost", precision = 10, scale = 2)
    private BigDecimal actualCost;
    
    @Column(name = "estimated_hours")
    private Integer estimatedHours;
    
    @Column(name = "actual_hours")
    private Integer actualHours;
    
    @Column(name = "payment_reference", length = 100)
    private String paymentReference;
    
    @Column(name = "pay_prop_payment_id", length = 50)
    private String payPropPaymentId;

    // ===== MAINTENANCE SPECIFIC FIELDS =====
    
    @Column(name = "maintenance_category", length = 50)
    @Pattern(regexp = "^(plumbing|electrical|heating|general|emergency|appliance|external|internal)$", 
             message = "Invalid maintenance category")
    private String maintenanceCategory;
    
    @Column(name = "urgency_level", length = 20)
    @Pattern(regexp = "^(routine|urgent|emergency|health-safety)$", message = "Invalid urgency level")
    private String urgencyLevel;
    
    @Column(name = "access_required")
    private Boolean accessRequired = false;
    
    @Column(name = "tenant_present_required")
    private Boolean tenantPresentRequired = false;
    
    @Column(name = "preferred_time_slot", length = 50)
    private String preferredTimeSlot;
    
    @Column(name = "work_started_at")
    private LocalDateTime workStartedAt;
    
    @Column(name = "work_completed_at")
    private LocalDateTime workCompletedAt;
    
    @Column(name = "warranty_period_months")
    private Integer warrantyPeriodMonths;

    // ===== EXISTING RELATIONSHIPS =====
    
    @ManyToOne
    @JoinColumn(name = "manager_id")
    private User manager;

    @ManyToOne
    @JoinColumn(name = "employee_id")
    private User employee;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    // ===== NEW RELATIONSHIPS =====
    
    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ContractorBid> contractorBids;

    // ENHANCED: Direct relationship to selected contractor
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_contractor_id", insertable = false, updatable = false)
    private Customer selectedContractor;

    // ===== CONSTRUCTORS =====
    
    public Ticket() {
    }

    public Ticket(String subject, String description, String status, String priority, String type, 
                  User manager, User employee, Customer customer, LocalDateTime createdAt) {
        this.subject = subject;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.type = type;
        this.manager = manager;
        this.employee = employee;
        this.customer = customer;
        this.createdAt = createdAt;
    }

    // ===== EXISTING GETTERS AND SETTERS =====
    
    public int getTicketId() { return ticketId; }
    public void setTicketId(int ticketId) { this.ticketId = ticketId; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public User getManager() { return manager; }
    public void setManager(User manager) { this.manager = manager; }

    public User getEmployee() { return employee; }
    public void setEmployee(User employee) { this.employee = employee; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    // ===== PAYPROP INTEGRATION GETTERS AND SETTERS =====
    
    public String getPayPropTicketId() { return payPropTicketId; }
    public void setPayPropTicketId(String payPropTicketId) { this.payPropTicketId = payPropTicketId; }

    public String getPayPropPropertyId() { return payPropPropertyId; }
    public void setPayPropPropertyId(String payPropPropertyId) { this.payPropPropertyId = payPropPropertyId; }

    public String getPayPropTenantId() { return payPropTenantId; }
    public void setPayPropTenantId(String payPropTenantId) { this.payPropTenantId = payPropTenantId; }

    public String getPayPropCategoryId() { return payPropCategoryId; }
    public void setPayPropCategoryId(String payPropCategoryId) { this.payPropCategoryId = payPropCategoryId; }

    public Boolean getPayPropSynced() { return payPropSynced; }
    public void setPayPropSynced(Boolean payPropSynced) { this.payPropSynced = payPropSynced; }

    public LocalDateTime getPayPropLastSync() { return payPropLastSync; }
    public void setPayPropLastSync(LocalDateTime payPropLastSync) { this.payPropLastSync = payPropLastSync; }

    // ===== CONTRACTOR MANAGEMENT GETTERS AND SETTERS =====
    
    public Long getSelectedContractorId() { return selectedContractorId; }
    public void setSelectedContractorId(Long selectedContractorId) { 
        this.selectedContractorId = selectedContractorId; 
    }

    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public void setApprovedAmount(BigDecimal approvedAmount) { this.approvedAmount = approvedAmount; }

    public BigDecimal getActualCost() { return actualCost; }
    public void setActualCost(BigDecimal actualCost) { this.actualCost = actualCost; }

    public Integer getEstimatedHours() { return estimatedHours; }
    public void setEstimatedHours(Integer estimatedHours) { this.estimatedHours = estimatedHours; }

    public Integer getActualHours() { return actualHours; }
    public void setActualHours(Integer actualHours) { this.actualHours = actualHours; }

    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }

    public String getPayPropPaymentId() { return payPropPaymentId; }
    public void setPayPropPaymentId(String payPropPaymentId) { this.payPropPaymentId = payPropPaymentId; }

    // ===== MAINTENANCE SPECIFIC GETTERS AND SETTERS =====
    
    public String getMaintenanceCategory() { return maintenanceCategory; }
    public void setMaintenanceCategory(String maintenanceCategory) { 
        this.maintenanceCategory = maintenanceCategory; 
    }

    public String getUrgencyLevel() { return urgencyLevel; }
    public void setUrgencyLevel(String urgencyLevel) { this.urgencyLevel = urgencyLevel; }

    public Boolean getAccessRequired() { return accessRequired; }
    public void setAccessRequired(Boolean accessRequired) { this.accessRequired = accessRequired; }

    public Boolean getTenantPresentRequired() { return tenantPresentRequired; }
    public void setTenantPresentRequired(Boolean tenantPresentRequired) { 
        this.tenantPresentRequired = tenantPresentRequired; 
    }

    public String getPreferredTimeSlot() { return preferredTimeSlot; }
    public void setPreferredTimeSlot(String preferredTimeSlot) { this.preferredTimeSlot = preferredTimeSlot; }

    public LocalDateTime getWorkStartedAt() { return workStartedAt; }
    public void setWorkStartedAt(LocalDateTime workStartedAt) { this.workStartedAt = workStartedAt; }

    public LocalDateTime getWorkCompletedAt() { return workCompletedAt; }
    public void setWorkCompletedAt(LocalDateTime workCompletedAt) { this.workCompletedAt = workCompletedAt; }

    public Integer getWarrantyPeriodMonths() { return warrantyPeriodMonths; }
    public void setWarrantyPeriodMonths(Integer warrantyPeriodMonths) { 
        this.warrantyPeriodMonths = warrantyPeriodMonths; 
    }

    public List<ContractorBid> getContractorBids() { return contractorBids; }
    public void setContractorBids(List<ContractorBid> contractorBids) { this.contractorBids = contractorBids; }

    // ===== ENHANCED CONTRACTOR RELATIONSHIP GETTERS AND SETTERS =====
    
    public Customer getSelectedContractor() { 
        return selectedContractor; 
    }

    public void setSelectedContractor(Customer selectedContractor) { 
        this.selectedContractor = selectedContractor;
        if (selectedContractor != null) {
            this.selectedContractorId = selectedContractor.getCustomerId();
        }
    }

    // ===== UTILITY METHODS =====
    
    public boolean isMaintenanceTicket() {
        return "maintenance".equals(type) || "emergency".equals(type);
    }
    
    public boolean isEmergencyTicket() {
        return "emergency".equals(type) || "emergency".equals(urgencyLevel);
    }
    
    public boolean isPayPropSynced() {
        return Boolean.TRUE.equals(payPropSynced);
    }
    
    public boolean needsPayPropSync() {
        return payPropTicketId != null && !isPayPropSynced();
    }
    
    public boolean hasBids() {
        return contractorBids != null && !contractorBids.isEmpty();
    }
    
    public boolean hasSelectedContractor() {
        return selectedContractorId != null;
    }
    
    public boolean isWorkInProgress() {
        return "work-in-progress".equals(status);
    }
    
    public boolean isWorkCompleted() {
        return "work-completed".equals(status);
    }
    
    public boolean isPaymentProcessed() {
        return "payment-processed".equals(status);
    }
    
    public boolean canInviteBids() {
        return isMaintenanceTicket() && 
               ("open".equals(status) || "assigned".equals(status)) && 
               !hasSelectedContractor();
    }
    
    public boolean canSelectContractor() {
        return "bidding".equals(status) || "bid-review".equals(status);
    }
    
    public boolean canStartWork() {
        return "contractor-selected".equals(status) && hasSelectedContractor();
    }
    
    public boolean canCompleteWork() {
        return "work-in-progress".equals(status);
    }
    
    public boolean canProcessPayment() {
        return "work-completed".equals(status) && approvedAmount != null;
    }

    // ===== ENHANCED CONTRACTOR UTILITY METHODS =====
    
    public String getSelectedContractorName() {
        return selectedContractor != null ? selectedContractor.getFullName() : "None Selected";
    }

    public String getSelectedContractorEmail() {
        return selectedContractor != null ? selectedContractor.getEmail() : null;
    }

    public String getSelectedContractorPhone() {
        return selectedContractor != null ? selectedContractor.getPhone() : null;
    }

    public boolean selectedContractorHasBankDetails() {
        return selectedContractor != null && selectedContractor.hasValidBankDetails();
    }

    public boolean canCreatePayPropPayment() {
        return hasSelectedContractor() && 
               selectedContractorHasBankDetails() && 
               approvedAmount != null &&
               "work-completed".equals(status);
    }

    public boolean selectedContractorIsPayPropSynced() {
        return selectedContractor != null && 
               selectedContractor.getPayPropSynced() != null && 
               selectedContractor.getPayPropSynced();
    }

    public String getSelectedContractorPayPropId() {
        return selectedContractor != null ? selectedContractor.getPayPropEntityId() : null;
    }

    // ===== BID MANAGEMENT UTILITY METHODS =====
    
    public int getBidCount() {
        return contractorBids != null ? contractorBids.size() : 0;
    }
    
    public int getSubmittedBidCount() {
        if (contractorBids == null) return 0;
        return (int) contractorBids.stream()
                .filter(bid -> "submitted".equals(bid.getStatus()))
                .count();
    }
    
    public ContractorBid getAcceptedBid() {
        if (contractorBids == null) return null;
        return contractorBids.stream()
                .filter(bid -> "accepted".equals(bid.getStatus()))
                .findFirst()
                .orElse(null);
    }
    
    public BigDecimal getLowestBidAmount() {
        if (contractorBids == null) return null;
        return contractorBids.stream()
                .filter(bid -> "submitted".equals(bid.getStatus()) && bid.getBidAmount() != null)
                .map(ContractorBid::getBidAmount)
                .min(BigDecimal::compareTo)
                .orElse(null);
    }
    
    public BigDecimal getHighestBidAmount() {
        if (contractorBids == null) return null;
        return contractorBids.stream()
                .filter(bid -> "submitted".equals(bid.getStatus()) && bid.getBidAmount() != null)
                .map(ContractorBid::getBidAmount)
                .max(BigDecimal::compareTo)
                .orElse(null);
    }
    
    public boolean hasExpiredBids() {
        if (contractorBids == null) return false;
        return contractorBids.stream()
                .anyMatch(bid -> "expired".equals(bid.getStatus()));
    }

    // ===== WORKFLOW STATUS UTILITY METHODS =====
    
    public String getStatusDisplayName() {
        switch (status != null ? status : "open") {
            case "open": return "Open";
            case "assigned": return "Assigned";
            case "bidding": return "Seeking Bids";
            case "bid-review": return "Reviewing Bids";
            case "contractor-selected": return "Contractor Selected";
            case "work-in-progress": return "Work in Progress";
            case "work-completed": return "Work Completed";
            case "payment-pending": return "Payment Pending";
            case "payment-processed": return "Payment Processed";
            case "on-hold": return "On Hold";
            case "resolved": return "Resolved";
            case "closed": return "Closed";
            case "reopened": return "Reopened";
            case "pending-customer-response": return "Awaiting Customer";
            case "escalated": return "Escalated";
            case "archived": return "Archived";
            default: return status;
        }
    }
    
    public String getPriorityDisplayName() {
        switch (priority != null ? priority : "medium") {
            case "low": return "Low";
            case "medium": return "Medium";
            case "high": return "High";
            case "urgent": return "Urgent";
            case "critical": return "Critical";
            case "emergency": return "Emergency";
            default: return priority;
        }
    }

    public String getPriorityCssClass() {
        switch (priority != null ? priority : "medium") {
            case "low": return "badge-success";
            case "medium": return "badge-info";
            case "high": return "badge-warning";
            case "urgent": return "badge-danger";
            case "critical": return "badge-danger";
            case "emergency": return "badge-dark";
            default: return "badge-secondary";
        }
    }

    public String getStatusCssClass() {
        switch (status != null ? status : "open") {
            case "open": return "badge-primary";
            case "assigned": return "badge-info";
            case "bidding": return "badge-warning";
            case "bid-review": return "badge-warning";
            case "contractor-selected": return "badge-success";
            case "work-in-progress": return "badge-info";
            case "work-completed": return "badge-success";
            case "payment-pending": return "badge-warning";
            case "payment-processed": return "badge-success";
            case "on-hold": return "badge-secondary";
            case "resolved": return "badge-success";
            case "closed": return "badge-dark";
            case "reopened": return "badge-warning";
            case "pending-customer-response": return "badge-warning";
            case "escalated": return "badge-danger";
            case "archived": return "badge-secondary";
            default: return "badge-secondary";
        }
    }
    
    // ===== TIME AND DURATION UTILITY METHODS =====
    
    /**
     * Calculate work duration if both start and completion times are available
     */
    public Long getWorkDurationHours() {
        if (workStartedAt != null && workCompletedAt != null) {
            return java.time.Duration.between(workStartedAt, workCompletedAt).toHours();
        }
        return null;
    }
    
    /**
     * Get work duration as a formatted string
     */
    public String getWorkDurationFormatted() {
        Long hours = getWorkDurationHours();
        if (hours == null) return "N/A";
        
        if (hours < 24) {
            return hours + " hour" + (hours == 1 ? "" : "s");
        } else {
            long days = hours / 24;
            long remainingHours = hours % 24;
            return days + " day" + (days == 1 ? "" : "s") + 
                   (remainingHours > 0 ? " " + remainingHours + " hour" + (remainingHours == 1 ? "" : "s") : "");
        }
    }
    
    /**
     * Check if work is overdue based on estimated completion
     */
    public boolean isOverdue() {
        if (workStartedAt != null && estimatedHours != null && 
            "work-in-progress".equals(status)) {
            LocalDateTime expectedCompletion = workStartedAt.plusHours(estimatedHours);
            return LocalDateTime.now().isAfter(expectedCompletion);
        }
        return false;
    }

    /**
     * Get the number of hours until work is due (negative if overdue)
     */
    public Long getHoursUntilDue() {
        if (workStartedAt != null && estimatedHours != null && 
            "work-in-progress".equals(status)) {
            LocalDateTime expectedCompletion = workStartedAt.plusHours(estimatedHours);
            return java.time.Duration.between(LocalDateTime.now(), expectedCompletion).toHours();
        }
        return null;
    }

    // ===== COST ANALYSIS UTILITY METHODS =====
    
    /**
     * Calculate cost variance (actual vs approved)
     */
    public BigDecimal getCostVariance() {
        if (actualCost != null && approvedAmount != null) {
            return actualCost.subtract(approvedAmount);
        }
        return null;
    }
    
    /**
     * Check if actual cost exceeded approved amount
     */
    public boolean isOverBudget() {
        BigDecimal variance = getCostVariance();
        return variance != null && variance.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * Get cost variance as percentage
     */
    public Double getCostVariancePercentage() {
        BigDecimal variance = getCostVariance();
        if (variance != null && approvedAmount != null && 
            approvedAmount.compareTo(BigDecimal.ZERO) > 0) {
            return variance.divide(approvedAmount, 4, BigDecimal.ROUND_HALF_UP)
                          .multiply(BigDecimal.valueOf(100))
                          .doubleValue();
        }
        return null;
    }

    // ===== PAYPROP INTEGRATION UTILITY METHODS =====
    
    public boolean isFromPayProp() {
        return payPropTicketId != null && !payPropTicketId.trim().isEmpty();
    }
    
    public boolean needsPayPropUpdate() {
        return isFromPayProp() && 
               (payPropLastSync == null || 
                (createdAt != null && payPropLastSync.isBefore(createdAt)));
    }
    
    public String getPayPropSyncStatus() {
        if (!isFromPayProp()) return "Not PayProp";
        if (Boolean.TRUE.equals(payPropSynced)) return "Synced";
        if (payPropLastSync == null) return "Never Synced";
        return "Sync Needed";
    }

    /**
     * Get the employee ID for this ticket
     * @return employee ID or null if no employee assigned
     */
    public Integer getEmployeeId() {
        return employee != null ? employee.getId() : null;
    }

    @Override
    public String toString() {
        return "Ticket{" +
                "ticketId=" + ticketId +
                ", subject='" + subject + '\'' +
                ", status='" + status + '\'' +
                ", priority='" + priority + '\'' +
                ", type='" + type + '\'' +
                ", payPropTicketId='" + payPropTicketId + '\'' +
                ", selectedContractorId=" + selectedContractorId +
                ", approvedAmount=" + approvedAmount +
                ", bidCount=" + getBidCount() +
                '}';
    }
}
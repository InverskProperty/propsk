package site.easy.to.build.crm.entity;

/**
 * InstructionStatus Enum - Lifecycle stages of a letting instruction
 *
 * Simplified real-world workflow:
 * 1. INSTRUCTION_RECEIVED - Owner instructs to let property
 * 2. ADVERTISING - Property marketed (viewings/offers happen concurrently)
 * 3. OFFER_ACCEPTED - Holding deposit paid, proceeding to referencing/contracts
 * 4. ACTIVE_LEASE - Tenant moved in
 * 5. CANCELLED - Instruction withdrawn or cancelled
 */
public enum InstructionStatus {

    /**
     * Initial state - Instruction received from property owner
     * Next: ADVERTISING or CANCELLED
     */
    INSTRUCTION_RECEIVED("Instruction Received", "Property owner has instructed us to let the property"),

    /**
     * Property is actively being marketed
     * Viewings, enquiries, and offers all happen in this stage
     * Next: OFFER_ACCEPTED or CANCELLED
     */
    ADVERTISING("Advertising", "Property is being actively marketed (viewings/offers ongoing)"),

    /**
     * Holding deposit paid, proceeding with referencing and contracts
     * Next: ACTIVE_LEASE, back to ADVERTISING (if falls through), or CANCELLED
     */
    OFFER_ACCEPTED("Offer Accepted", "Holding deposit paid, in referencing/contracting"),

    /**
     * Tenant has moved in, lease is active
     * Next: INSTRUCTION_RECEIVED (for re-letting) or CANCELLED
     */
    ACTIVE_LEASE("Active Lease", "Tenant is in property, lease is active"),

    /**
     * Instruction cancelled/withdrawn
     * Terminal state
     */
    CANCELLED("Cancelled", "Instruction has been cancelled or withdrawn"),

    // Legacy statuses for backward compatibility - these will be migrated
    @Deprecated
    PREPARING("Preparing", "Property being prepared for advertising"),

    @Deprecated
    VIEWINGS_IN_PROGRESS("Viewings in Progress", "Conducting viewings with potential tenants"),

    @Deprecated
    OFFER_MADE("Offer Made", "Offer accepted, proceeding to contracts"),

    @Deprecated
    CLOSED("Closed", "Instruction has been closed");

    private final String displayName;
    private final String description;

    InstructionStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get the Kanban column order for this status
     */
    public int getKanbanOrder() {
        return switch (this) {
            case INSTRUCTION_RECEIVED -> 0;
            case ADVERTISING -> 1;
            case OFFER_ACCEPTED -> 2;
            case ACTIVE_LEASE -> 3;
            case CANCELLED -> 4;
            // Legacy statuses
            case PREPARING -> 0;
            case VIEWINGS_IN_PROGRESS -> 1;
            case OFFER_MADE -> 2;
            case CLOSED -> 4;
        };
    }

    /**
     * Get CSS class for status badge
     */
    public String getBadgeClass() {
        return switch (this) {
            case INSTRUCTION_RECEIVED -> "badge-secondary";
            case ADVERTISING -> "badge-primary";
            case OFFER_ACCEPTED -> "badge-warning";
            case ACTIVE_LEASE -> "badge-success";
            case CANCELLED -> "badge-dark";
            // Legacy statuses
            case PREPARING -> "badge-info";
            case VIEWINGS_IN_PROGRESS -> "badge-warning";
            case OFFER_MADE -> "badge-success";
            case CLOSED -> "badge-dark";
        };
    }

    /**
     * Check if this status allows new leads to be added
     */
    public boolean canAcceptLeads() {
        return this == ADVERTISING ||
               this == OFFER_ACCEPTED ||
               // Legacy
               this == VIEWINGS_IN_PROGRESS ||
               this == OFFER_MADE;
    }

    /**
     * Check if can transition to a new status
     */
    public boolean canTransitionTo(InstructionStatus newStatus) {
        return switch (this) {
            case INSTRUCTION_RECEIVED -> newStatus == ADVERTISING || newStatus == CANCELLED;
            case ADVERTISING -> newStatus == OFFER_ACCEPTED || newStatus == CANCELLED;
            case OFFER_ACCEPTED -> newStatus == ACTIVE_LEASE || newStatus == ADVERTISING || newStatus == CANCELLED;
            case ACTIVE_LEASE -> newStatus == INSTRUCTION_RECEIVED || newStatus == CANCELLED; // Can re-let
            case CANCELLED -> false; // Terminal state
            // Legacy statuses
            case PREPARING -> newStatus == ADVERTISING || newStatus == CANCELLED;
            case VIEWINGS_IN_PROGRESS -> newStatus == OFFER_ACCEPTED || newStatus == ADVERTISING || newStatus == CANCELLED;
            case OFFER_MADE -> newStatus == ACTIVE_LEASE || newStatus == ADVERTISING || newStatus == CANCELLED;
            case CLOSED -> false;
        };
    }

    /**
     * Check if this status represents an active instruction (not leased/cancelled)
     */
    public boolean isActiveInstruction() {
        return this != ACTIVE_LEASE && this != CANCELLED && this != CLOSED;
    }
}

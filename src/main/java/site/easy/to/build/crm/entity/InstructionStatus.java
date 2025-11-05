package site.easy.to.build.crm.entity;

/**
 * InstructionStatus Enum - Lifecycle stages of a letting instruction
 *
 * Represents the evolution of a property letting from initial instruction to active lease.
 */
public enum InstructionStatus {

    /**
     * Initial state - Instruction received from property owner to let the property
     * Next: PREPARING or ADVERTISING
     */
    INSTRUCTION_RECEIVED("Instruction Received", "Property owner has instructed us to let the property"),

    /**
     * Property is being prepared for marketing (cleaning, repairs, photography, etc.)
     * Next: ADVERTISING
     */
    PREPARING("Preparing", "Property being prepared for advertising"),

    /**
     * Property is being actively advertised/marketed
     * Next: VIEWINGS_IN_PROGRESS or OFFER_MADE
     */
    ADVERTISING("Advertising", "Property is listed and being advertised"),

    /**
     * Viewings are being conducted with potential tenants
     * Next: OFFER_MADE or back to ADVERTISING
     */
    VIEWINGS_IN_PROGRESS("Viewings in Progress", "Conducting viewings with potential tenants"),

    /**
     * Offer has been made and accepted, in referencing/contract stage
     * Next: ACTIVE_LEASE or back to ADVERTISING (if falls through)
     */
    OFFER_MADE("Offer Made", "Offer accepted, proceeding to contracts"),

    /**
     * Lease is active - tenant has moved in
     * Next: CLOSED (when lease ends or tenant moves out)
     */
    ACTIVE_LEASE("Active Lease", "Tenant is in property, lease is active"),

    /**
     * Instruction is closed (lease ended, withdrawn, or cancelled)
     * Terminal state
     */
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
            case INSTRUCTION_RECEIVED, PREPARING -> 0;
            case ADVERTISING -> 1;
            case VIEWINGS_IN_PROGRESS -> 2;
            case OFFER_MADE -> 3;
            case ACTIVE_LEASE -> 4;
            case CLOSED -> 5;
        };
    }

    /**
     * Get CSS class for status badge
     */
    public String getBadgeClass() {
        return switch (this) {
            case INSTRUCTION_RECEIVED -> "badge-secondary";
            case PREPARING -> "badge-info";
            case ADVERTISING -> "badge-primary";
            case VIEWINGS_IN_PROGRESS -> "badge-warning";
            case OFFER_MADE -> "badge-success";
            case ACTIVE_LEASE -> "badge-success";
            case CLOSED -> "badge-dark";
        };
    }

    /**
     * Check if this status allows new leads to be added
     */
    public boolean canAcceptLeads() {
        return this == ADVERTISING ||
               this == VIEWINGS_IN_PROGRESS ||
               this == OFFER_MADE;
    }

    /**
     * Check if can transition to a new status
     */
    public boolean canTransitionTo(InstructionStatus newStatus) {
        return switch (this) {
            case INSTRUCTION_RECEIVED -> newStatus == PREPARING || newStatus == ADVERTISING;
            case PREPARING -> newStatus == ADVERTISING;
            case ADVERTISING -> newStatus == VIEWINGS_IN_PROGRESS || newStatus == OFFER_MADE || newStatus == CLOSED;
            case VIEWINGS_IN_PROGRESS -> newStatus == OFFER_MADE || newStatus == ADVERTISING || newStatus == CLOSED;
            case OFFER_MADE -> newStatus == ACTIVE_LEASE || newStatus == ADVERTISING || newStatus == CLOSED;
            case ACTIVE_LEASE -> newStatus == CLOSED;
            case CLOSED -> false; // Terminal state
        };
    }

    /**
     * Check if this status represents an active instruction (not closed/lease)
     */
    public boolean isActiveInstruction() {
        return this != ACTIVE_LEASE && this != CLOSED;
    }
}

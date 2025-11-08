package site.easy.to.build.crm.entity;

/**
 * LeadStatus Enum - Lifecycle stages of a lead through the tenant journey
 *
 * Lead pipeline workflow:
 * 1. ENQUIRY - Initial contact made
 * 2. VIEWING_SCHEDULED - Viewing appointment booked
 * 3. VIEWING_COMPLETED - Viewing has taken place
 * 4. INTERESTED - Lead expressed interest after viewing
 * 5. APPLICATION_SUBMITTED - Formal application received
 * 6. REFERENCING - Background/credit checks in progress
 * 7. IN_CONTRACTS - Contracts being prepared and signed
 * 8. CONTRACTS_COMPLETE - All paperwork complete
 * 9. CONVERTED - Lead successfully converted to tenant
 * 10. LOST - Lead didn't proceed (lost to competitor, changed mind, etc.)
 */
public enum LeadStatus {

    /**
     * Initial enquiry - Lead has made contact
     * Next: VIEWING_SCHEDULED, INTERESTED, or LOST
     */
    ENQUIRY("Enquiry", "Initial enquiry received"),

    /**
     * Viewing has been scheduled
     * Next: VIEWING_COMPLETED or LOST
     */
    VIEWING_SCHEDULED("Viewing Scheduled", "Property viewing has been scheduled"),

    /**
     * Viewing has been completed
     * Next: INTERESTED, APPLICATION_SUBMITTED, or LOST
     */
    VIEWING_COMPLETED("Viewing Completed", "Property viewing has taken place"),

    /**
     * Lead has expressed interest
     * Next: APPLICATION_SUBMITTED or LOST
     */
    INTERESTED("Interested", "Lead has expressed interest in the property"),

    /**
     * Formal application has been submitted
     * Next: REFERENCING or LOST
     */
    APPLICATION_SUBMITTED("Application Submitted", "Formal tenancy application received"),

    /**
     * Background and credit checks in progress
     * Next: IN_CONTRACTS or LOST
     */
    REFERENCING("Referencing", "Tenant referencing in progress"),

    /**
     * Contracts being prepared and signed
     * Next: CONTRACTS_COMPLETE or LOST
     */
    IN_CONTRACTS("In Contracts", "Tenancy agreement being prepared and signed"),

    /**
     * All contracts signed, ready for conversion
     * Next: CONVERTED
     */
    CONTRACTS_COMPLETE("Contracts Complete", "All contracts signed, ready for tenant creation"),

    /**
     * Lead successfully converted to tenant
     * Terminal state (success)
     */
    CONVERTED("Converted", "Lead converted to tenant"),

    /**
     * Lead lost - did not proceed
     * Terminal state (failure)
     */
    LOST("Lost", "Lead did not proceed");

    private final String displayName;
    private final String description;

    LeadStatus(String displayName, String description) {
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
            case ENQUIRY -> 0;
            case VIEWING_SCHEDULED -> 1;
            case VIEWING_COMPLETED -> 2;
            case INTERESTED -> 3;
            case APPLICATION_SUBMITTED -> 4;
            case REFERENCING -> 5;
            case IN_CONTRACTS -> 6;
            case CONTRACTS_COMPLETE -> 7;
            case CONVERTED -> 8;
            case LOST -> 9;
        };
    }

    /**
     * Get CSS class for status badge
     */
    public String getBadgeClass() {
        return switch (this) {
            case ENQUIRY -> "badge-secondary";
            case VIEWING_SCHEDULED -> "badge-info";
            case VIEWING_COMPLETED -> "badge-primary";
            case INTERESTED -> "badge-warning";
            case APPLICATION_SUBMITTED -> "badge-success";
            case REFERENCING -> "badge-info";
            case IN_CONTRACTS -> "badge-warning";
            case CONTRACTS_COMPLETE -> "badge-success";
            case CONVERTED -> "badge-success";
            case LOST -> "badge-dark";
        };
    }

    /**
     * Check if this is a terminal status (no further progression)
     */
    public boolean isTerminal() {
        return this == CONVERTED || this == LOST;
    }

    /**
     * Check if this status represents an active lead (not converted/lost)
     */
    public boolean isActive() {
        return !isTerminal();
    }

    /**
     * Check if can transition to a new status
     */
    public boolean canTransitionTo(LeadStatus newStatus) {
        // Terminal states cannot transition
        if (this.isTerminal()) {
            return false;
        }

        // Can always mark as lost
        if (newStatus == LOST) {
            return true;
        }

        // Specific valid transitions
        return switch (this) {
            case ENQUIRY -> newStatus == VIEWING_SCHEDULED ||
                           newStatus == INTERESTED ||
                           newStatus == APPLICATION_SUBMITTED;
            case VIEWING_SCHEDULED -> newStatus == VIEWING_COMPLETED;
            case VIEWING_COMPLETED -> newStatus == INTERESTED ||
                                     newStatus == APPLICATION_SUBMITTED;
            case INTERESTED -> newStatus == APPLICATION_SUBMITTED;
            case APPLICATION_SUBMITTED -> newStatus == REFERENCING;
            case REFERENCING -> newStatus == IN_CONTRACTS;
            case IN_CONTRACTS -> newStatus == CONTRACTS_COMPLETE;
            case CONTRACTS_COMPLETE -> newStatus == CONVERTED;
            default -> false;
        };
    }

    /**
     * Get the database/JSON value (lowercase with hyphens)
     * This matches the existing database values
     */
    public String getValue() {
        return switch (this) {
            case ENQUIRY -> "enquiry";
            case VIEWING_SCHEDULED -> "viewing-scheduled";
            case VIEWING_COMPLETED -> "viewing-completed";
            case INTERESTED -> "interested";
            case APPLICATION_SUBMITTED -> "application-submitted";
            case REFERENCING -> "referencing";
            case IN_CONTRACTS -> "in-contracts";
            case CONTRACTS_COMPLETE -> "contracts-complete";
            case CONVERTED -> "converted";
            case LOST -> "lost";
        };
    }

    /**
     * Parse from database/JSON value (lowercase with hyphens)
     */
    public static LeadStatus fromValue(String value) {
        if (value == null) {
            return ENQUIRY; // Default
        }
        return switch (value.toLowerCase()) {
            case "enquiry" -> ENQUIRY;
            case "viewing-scheduled" -> VIEWING_SCHEDULED;
            case "viewing-completed" -> VIEWING_COMPLETED;
            case "interested" -> INTERESTED;
            case "application-submitted" -> APPLICATION_SUBMITTED;
            case "referencing" -> REFERENCING;
            case "in-contracts" -> IN_CONTRACTS;
            case "contracts-complete" -> CONTRACTS_COMPLETE;
            case "converted" -> CONVERTED;
            case "lost" -> LOST;
            default -> throw new IllegalArgumentException("Unknown lead status: " + value);
        };
    }
}

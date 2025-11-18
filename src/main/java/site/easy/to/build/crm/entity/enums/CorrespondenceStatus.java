package site.easy.to.build.crm.entity.enums;

/**
 * Status of email correspondence
 */
public enum CorrespondenceStatus {
    DRAFT("Draft"),
    SENDING("Sending"),
    SENT("Sent"),
    FAILED("Failed"),
    BOUNCED("Bounced");

    private final String displayName;

    CorrespondenceStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

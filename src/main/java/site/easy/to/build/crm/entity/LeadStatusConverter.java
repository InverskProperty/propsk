package site.easy.to.build.crm.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA Converter to handle mapping between database string values and LeadStatus enum
 *
 * This converter maintains backward compatibility with existing database values
 * which use lowercase-with-hyphens format (e.g., "viewing-scheduled")
 */
@Converter(autoApply = false)
public class LeadStatusConverter implements AttributeConverter<LeadStatus, String> {

    @Override
    public String convertToDatabaseColumn(LeadStatus status) {
        if (status == null) {
            return null;
        }
        return status.getValue();
    }

    @Override
    public LeadStatus convertToEntityAttribute(String dbValue) {
        if (dbValue == null || dbValue.trim().isEmpty()) {
            return LeadStatus.ENQUIRY; // Default value
        }

        try {
            return LeadStatus.fromValue(dbValue);
        } catch (IllegalArgumentException e) {
            // Handle legacy values that might not match
            // Default to ENQUIRY for safety
            System.err.println("Warning: Unknown lead status '" + dbValue + "', defaulting to ENQUIRY");
            return LeadStatus.ENQUIRY;
        }
    }
}

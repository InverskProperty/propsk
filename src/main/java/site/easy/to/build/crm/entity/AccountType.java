package site.easy.to.build.crm.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AccountType {
    INDIVIDUAL("individual"),
    BUSINESS("business");
    
    private final String value;
    
    AccountType(String value) {
        this.value = value;
    }
    
    @JsonValue
    public String getValue() {
        return value;
    }
    
    @Override
    public String toString() {
        return value;  // Returns lowercase for database
    }
    
    // This handles database mapping
    @JsonCreator
    public static AccountType fromValue(String value) {
        if (value == null) return INDIVIDUAL;
        
        for (AccountType type : AccountType.values()) {
            if (type.value.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        return INDIVIDUAL; // Safe fallback
    }
}
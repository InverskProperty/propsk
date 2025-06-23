// AccountType.java
package site.easy.to.build.crm.entity;

public enum AccountType {
    INDIVIDUAL("individual"),
    BUSINESS("business");
    
    private final String value;
    
    AccountType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    public static AccountType fromValue(String value) {
        for (AccountType type : AccountType.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown account type: " + value);
    }
}
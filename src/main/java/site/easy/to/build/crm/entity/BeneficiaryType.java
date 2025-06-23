// BeneficiaryType.java
package site.easy.to.build.crm.entity;

public enum BeneficiaryType {
    AGENCY("agency"),
    BENEFICIARY("beneficiary"),
    GLOBAL_BENEFICIARY("global_beneficiary"),
    PROPERTY_ACCOUNT("property_account"),
    DEPOSIT_ACCOUNT("deposit_account");
    
    private final String payPropCode;
    
    BeneficiaryType(String payPropCode) {
        this.payPropCode = payPropCode;
    }
    
    public String getPayPropCode() {
        return payPropCode;
    }
    
    public static BeneficiaryType fromPayPropCode(String code) {
        for (BeneficiaryType type : BeneficiaryType.values()) {
            if (type.payPropCode.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown beneficiary type: " + code);
    }
}
// Frequency.java
package site.easy.to.build.crm.entity;

public enum Frequency {
    ONE_TIME("O"),
    WEEKLY("W"),
    BI_WEEKLY("2W"),
    FOUR_WEEKLY("4W"),
    MONTHLY("M"),
    BI_MONTHLY("2M"),
    QUARTERLY("Q"),
    SEMI_ANNUAL("6M"),
    ANNUAL("A");
    
    private final String payPropCode;
    
    Frequency(String payPropCode) {
        this.payPropCode = payPropCode;
    }
    
    public String getPayPropCode() {
        return payPropCode;
    }
    
    public static Frequency fromPayPropCode(String code) {
        for (Frequency freq : Frequency.values()) {
            if (freq.payPropCode.equals(code)) {
                return freq;
            }
        }
        throw new IllegalArgumentException("Unknown frequency code: " + code);
    }
}

// PaymentMethod.java
package site.easy.to.build.crm.entity;

public enum PaymentMethod {
    LOCAL("local"),
    INTERNATIONAL("international"),
    CHEQUE("cheque");
    
    private final String payPropCode;
    
    PaymentMethod(String payPropCode) {
        this.payPropCode = payPropCode;
    }
    
    public String getPayPropCode() {
        return payPropCode;
    }
    
    public static PaymentMethod fromPayPropCode(String code) {
        for (PaymentMethod method : PaymentMethod.values()) {
            if (method.payPropCode.equals(code)) {
                return method;
            }
        }
        throw new IllegalArgumentException("Unknown payment method: " + code);
    }
}
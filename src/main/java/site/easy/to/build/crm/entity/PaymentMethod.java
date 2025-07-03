package site.easy.to.build.crm.entity;

public enum PaymentMethod {
    local("local"),
    international("international"),
    cheque("cheque");
    
    private final String payPropCode;
    
    PaymentMethod(String payPropCode) {
        this.payPropCode = payPropCode;
    }
    
    public String getPayPropCode() {
        return payPropCode;
    }
    
    public String getValue() {
        return this.name();  // Returns "local", "international", "cheque"
    }
    
    public static PaymentMethod fromPayPropCode(String code) {
        if (code == null) {
            return local; // Safe default
        }
        
        for (PaymentMethod method : PaymentMethod.values()) {
            if (method.payPropCode.equals(code)) {
                return method;
            }
        }
        
        // Log warning but don't crash
        System.err.println("⚠️ Unknown PayProp payment method: " + code + ", defaulting to 'local'");
        return local; // Safe fallback instead of throwing exception
    }
    
    // Additional helper method for display
    public String getDisplayName() {
        switch (this) {
            case local: return "Local Bank Transfer";
            case international: return "International Transfer";
            case cheque: return "Cheque Payment";
            default: return this.name();
        }
    }
}
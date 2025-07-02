package site.easy.to.build.crm.entity;

public enum AccountType {
    INDIVIDUAL,
    BUSINESS;
    
    public String getValue() {
        return this.name().toLowerCase();  // Returns "individual" or "business" for PayProp
    }
    
    public String getDisplayName() {
        return this.name().substring(0, 1).toUpperCase() + this.name().substring(1).toLowerCase();
    }
}
package site.easy.to.build.crm.entity;

public enum AccountType {
    individual,  
    business;    
    
    public String getValue() {
        return this.name();  // Returns "individual" or "business"
    }
    
    public String getDisplayName() {
        return this.name().substring(0, 1).toUpperCase() + this.name().substring(1);
    }
}
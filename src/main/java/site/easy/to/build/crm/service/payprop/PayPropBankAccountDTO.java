package site.easy.to.build.crm.service.payprop;

public class PayPropBankAccountDTO {
    private String account_name;
    private String account_number;
    private String branch_code;
    private String bank_name;
    private String branch_name;
    private String iban;
    private String swift_code;
    private String country_code;
    
    // Getters and setters
    public String getAccount_name() { return account_name; }
    public void setAccount_name(String account_name) { this.account_name = account_name; }
    
    public String getAccount_number() { return account_number; }
    public void setAccount_number(String account_number) { this.account_number = account_number; }
    
    public String getBranch_code() { return branch_code; }
    public void setBranch_code(String branch_code) { this.branch_code = branch_code; }
    
    public String getBank_name() { return bank_name; }
    public void setBank_name(String bank_name) { this.bank_name = bank_name; }
    
    public String getBranch_name() { return branch_name; }
    public void setBranch_name(String branch_name) { this.branch_name = branch_name; }
    
    public String getIban() { return iban; }
    public void setIban(String iban) { this.iban = iban; }
    
    public String getSwift_code() { return swift_code; }
    public void setSwift_code(String swift_code) { this.swift_code = swift_code; }
    
    public String getCountry_code() { return country_code; }
    public void setCountry_code(String country_code) { this.country_code = country_code; }
}
package site.easy.to.build.crm.service.payprop;

import java.time.LocalDate;

public class PayPropTenantDTO {
    private String account_type;
    private String first_name;
    private String last_name;
    private String business_name;
    private String email_address;
    private String mobile_number;
    private String phone;
    private String fax;
    private String customer_id;
    private String customer_reference;
    private String comment;
    private LocalDate date_of_birth;
    private String id_number;
    private String vat_number;
    private Boolean notify_email;
    private Boolean notify_sms;
    private PayPropAddressDTO address;
    private PayPropBankAccountDTO bank_account;
    private Boolean has_bank_account;
    
    // Getters and setters
    public String getAccount_type() { return account_type; }
    public void setAccount_type(String account_type) { this.account_type = account_type; }
    
    public String getFirst_name() { return first_name; }
    public void setFirst_name(String first_name) { this.first_name = first_name; }
    
    public String getLast_name() { return last_name; }
    public void setLast_name(String last_name) { this.last_name = last_name; }
    
    public String getBusiness_name() { return business_name; }
    public void setBusiness_name(String business_name) { this.business_name = business_name; }
    
    public String getEmail_address() { return email_address; }
    public void setEmail_address(String email_address) { this.email_address = email_address; }
    
    public String getMobile_number() { return mobile_number; }
    public void setMobile_number(String mobile_number) { this.mobile_number = mobile_number; }
    
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    
    public String getFax() { return fax; }
    public void setFax(String fax) { this.fax = fax; }
    
    public String getCustomer_id() { return customer_id; }
    public void setCustomer_id(String customer_id) { this.customer_id = customer_id; }
    
    public String getCustomer_reference() { return customer_reference; }
    public void setCustomer_reference(String customer_reference) { this.customer_reference = customer_reference; }
    
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    
    public LocalDate getDate_of_birth() { return date_of_birth; }
    public void setDate_of_birth(LocalDate date_of_birth) { this.date_of_birth = date_of_birth; }
    
    public String getId_number() { return id_number; }
    public void setId_number(String id_number) { this.id_number = id_number; }
    
    public String getVat_number() { return vat_number; }
    public void setVat_number(String vat_number) { this.vat_number = vat_number; }
    
    public Boolean getNotify_email() { return notify_email; }
    public void setNotify_email(Boolean notify_email) { this.notify_email = notify_email; }
    
    public Boolean getNotify_sms() { return notify_sms; }
    public void setNotify_sms(Boolean notify_sms) { this.notify_sms = notify_sms; }
    
    public PayPropAddressDTO getAddress() { return address; }
    public void setAddress(PayPropAddressDTO address) { this.address = address; }
    
    public PayPropBankAccountDTO getBank_account() { return bank_account; }
    public void setBank_account(PayPropBankAccountDTO bank_account) { this.bank_account = bank_account; }
    
    public Boolean getHas_bank_account() { return has_bank_account; }
    public void setHas_bank_account(Boolean has_bank_account) { this.has_bank_account = has_bank_account; }
}

class PayPropBankAccountDTO {
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
package site.easy.to.build.crm.service.payprop;

public class PayPropBeneficiaryDTO {
    private String account_type;
    private String payment_method;
    private String first_name;
    private String last_name;
    private String business_name;
    private String email_address;
    private String mobile;
    private String phone;
    private String fax;
    private String customer_id;
    private String customer_reference;
    private String comment;
    private String id_number;
    private String vat_number;
    private PayPropAddressDTO address;
    private PayPropBankAccountDTO bank_account;
    private PayPropCommunicationDTO communication_preferences;
    
    // Getters and setters
    public String getAccount_type() { return account_type; }
    public void setAccount_type(String account_type) { this.account_type = account_type; }
    
    public String getPayment_method() { return payment_method; }
    public void setPayment_method(String payment_method) { this.payment_method = payment_method; }
    
    public String getFirst_name() { return first_name; }
    public void setFirst_name(String first_name) { this.first_name = first_name; }
    
    public String getLast_name() { return last_name; }
    public void setLast_name(String last_name) { this.last_name = last_name; }
    
    public String getBusiness_name() { return business_name; }
    public void setBusiness_name(String business_name) { this.business_name = business_name; }
    
    public String getEmail_address() { return email_address; }
    public void setEmail_address(String email_address) { this.email_address = email_address; }
    
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    
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
    
    public String getId_number() { return id_number; }
    public void setId_number(String id_number) { this.id_number = id_number; }
    
    public String getVat_number() { return vat_number; }
    public void setVat_number(String vat_number) { this.vat_number = vat_number; }
    
    public PayPropAddressDTO getAddress() { return address; }
    public void setAddress(PayPropAddressDTO address) { this.address = address; }
    
    public PayPropBankAccountDTO getBank_account() { return bank_account; }
    public void setBank_account(PayPropBankAccountDTO bank_account) { this.bank_account = bank_account; }
    
    public PayPropCommunicationDTO getCommunication_preferences() { return communication_preferences; }
    public void setCommunication_preferences(PayPropCommunicationDTO communication_preferences) { this.communication_preferences = communication_preferences; }
}

class PayPropCommunicationDTO {
    private PayPropEmailDTO email;
    
    public PayPropEmailDTO getEmail() { return email; }
    public void setEmail(PayPropEmailDTO email) { this.email = email; }
}

class PayPropEmailDTO {
    private Boolean enabled;
    private Boolean payment_advice;
    
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    
    public Boolean getPayment_advice() { return payment_advice; }
    public void setPayment_advice(Boolean payment_advice) { this.payment_advice = payment_advice; }
}
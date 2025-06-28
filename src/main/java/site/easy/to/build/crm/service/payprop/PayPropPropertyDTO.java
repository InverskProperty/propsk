package site.easy.to.build.crm.service.payprop;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PayPropPropertyDTO {
    private String name;
    private String customer_id;
    private String customer_reference;
    private String agent_name;
    private String notes;
    private PayPropAddressDTO address;
    private PayPropSettingsDTO settings;
    
    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getCustomer_id() { return customer_id; }
    public void setCustomer_id(String customer_id) { this.customer_id = customer_id; }
    
    public String getCustomer_reference() { return customer_reference; }
    public void setCustomer_reference(String customer_reference) { this.customer_reference = customer_reference; }
    
    public String getAgent_name() { return agent_name; }
    public void setAgent_name(String agent_name) { this.agent_name = agent_name; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public PayPropAddressDTO getAddress() { return address; }
    public void setAddress(PayPropAddressDTO address) { this.address = address; }
    
    public PayPropSettingsDTO getSettings() { return settings; }
    public void setSettings(PayPropSettingsDTO settings) { this.settings = settings; }
}

class PayPropSettingsDTO {
    private Boolean enable_payments;
    private Boolean hold_owner_funds;
    private BigDecimal monthly_payment;
    private BigDecimal minimum_balance;
    private LocalDate listing_from;
    private LocalDate listing_to;
    
    // Getters and setters
    public Boolean getEnable_payments() { return enable_payments; }
    public void setEnable_payments(Boolean enable_payments) { this.enable_payments = enable_payments; }
    
    public Boolean getHold_owner_funds() { return hold_owner_funds; }
    public void setHold_owner_funds(Boolean hold_owner_funds) { this.hold_owner_funds = hold_owner_funds; }
    
    public BigDecimal getMonthly_payment() { return monthly_payment; }
    public void setMonthly_payment(BigDecimal monthly_payment) { this.monthly_payment = monthly_payment; }
    
    public BigDecimal getMinimum_balance() { return minimum_balance; }
    public void setMinimum_balance(BigDecimal minimum_balance) { this.minimum_balance = minimum_balance; }
    
    public LocalDate getListing_from() { return listing_from; }
    public void setListing_from(LocalDate listing_from) { this.listing_from = listing_from; }
    
    public LocalDate getListing_to() { return listing_to; }
    public void setListing_to(LocalDate listing_to) { this.listing_to = listing_to; }
}
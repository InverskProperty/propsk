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
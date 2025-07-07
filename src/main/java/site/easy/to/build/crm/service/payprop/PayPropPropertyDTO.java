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
    
    // Getters and setters with null protection
    public String getName() { return name; }
    public void setName(String name) { 
        this.name = (name != null) ? name : ""; 
    }
    
    public String getCustomer_id() { return customer_id; }
    public void setCustomer_id(String customer_id) { 
        this.customer_id = (customer_id != null) ? customer_id : ""; 
    }
    
    public String getCustomer_reference() { return customer_reference; }
    public void setCustomer_reference(String customer_reference) { 
        this.customer_reference = (customer_reference != null) ? customer_reference : ""; 
    }
    
    public String getAgent_name() { return agent_name; }
    public void setAgent_name(String agent_name) { 
        this.agent_name = (agent_name != null) ? agent_name : ""; 
    }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { 
        this.notes = (notes != null) ? notes : ""; 
    }
    
    public PayPropAddressDTO getAddress() { return address; }
    public void setAddress(PayPropAddressDTO address) { 
        // Initialize with empty address if null
        if (address == null) {
            address = new PayPropAddressDTO();
            // Set default values
            address.setAddress_line_1("");
            address.setAddress_line_2("");
            address.setAddress_line_3("");
            address.setCity("");
            address.setState("");
            address.setPostal_code("");
            address.setCountry_code("GB");
        }
        this.address = address; 
    }
    
    public PayPropSettingsDTO getSettings() { return settings; }
    public void setSettings(PayPropSettingsDTO settings) { 
        // Initialize with default settings if null
        if (settings == null) {
            settings = new PayPropSettingsDTO();
            // Set required defaults
            settings.setListing_from(LocalDate.now().toString());  // ✅ LocalDate to String
            settings.setVerify_payments(false);
            settings.setEnable_payments(false);
            settings.setHold_owner_funds(false);
        }
        this.settings = settings; 
    }
}
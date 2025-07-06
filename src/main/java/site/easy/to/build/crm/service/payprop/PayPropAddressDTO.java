package site.easy.to.build.crm.service.payprop;

public class PayPropAddressDTO {
    private String address_line_1;
    private String address_line_2;
    private String address_line_3;
    private String city;
    private String state;
    private String postal_code;
    private String country_code;
    
    // Getters and setters with null protection
    public String getAddress_line_1() { return address_line_1; }
    public void setAddress_line_1(String address_line_1) { 
        this.address_line_1 = (address_line_1 != null) ? address_line_1 : ""; 
    }
    
    public String getAddress_line_2() { return address_line_2; }
    public void setAddress_line_2(String address_line_2) { 
        this.address_line_2 = (address_line_2 != null) ? address_line_2 : ""; 
    }
    
    public String getAddress_line_3() { return address_line_3; }
    public void setAddress_line_3(String address_line_3) { 
        // CRITICAL FIX: Ensure address_line_3 is never null
        this.address_line_3 = (address_line_3 != null) ? address_line_3 : ""; 
    }
    
    public String getCity() { return city; }
    public void setCity(String city) { 
        this.city = (city != null) ? city : ""; 
    }
    
    public String getState() { return state; }
    public void setState(String state) { 
        // CRITICAL FIX: Ensure state is never null  
        this.state = (state != null) ? state : ""; 
    }
    
    public String getPostal_code() { return postal_code; }
    public void setPostal_code(String postal_code) { 
        this.postal_code = (postal_code != null) ? postal_code : ""; 
    }
    
    public String getCountry_code() { return country_code; }
    public void setCountry_code(String country_code) { 
        this.country_code = (country_code != null) ? country_code : "GB"; // Default to UK
    }
}
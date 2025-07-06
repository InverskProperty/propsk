// PayPropSettingsDTO.java (separate the nested class)
package site.easy.to.build.crm.service.payprop;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PayPropSettingsDTO {
    private Boolean enable_payments;
    private Boolean hold_owner_funds;
    private BigDecimal monthly_payment;
    private BigDecimal minimum_balance;
    private LocalDate listing_from;
    private LocalDate listing_to;
    private Boolean verify_payments;
    
    // Getters and setters with null protection
    public Boolean getEnable_payments() { return enable_payments; }
    public void setEnable_payments(Boolean enable_payments) { 
        this.enable_payments = (enable_payments != null) ? enable_payments : false; 
    }
    
    public Boolean getHold_owner_funds() { return hold_owner_funds; }
    public void setHold_owner_funds(Boolean hold_owner_funds) { 
        this.hold_owner_funds = (hold_owner_funds != null) ? hold_owner_funds : false; 
    }
    
    public BigDecimal getMonthly_payment() { return monthly_payment; }
    public void setMonthly_payment(BigDecimal monthly_payment) { 
        this.monthly_payment = (monthly_payment != null) ? monthly_payment : BigDecimal.ZERO; 
    }
    
    public BigDecimal getMinimum_balance() { return minimum_balance; }
    public void setMinimum_balance(BigDecimal minimum_balance) { 
        this.minimum_balance = (minimum_balance != null) ? minimum_balance : BigDecimal.ZERO; 
    }
    
    public LocalDate getListing_from() { return listing_from; }
    public void setListing_from(LocalDate listing_from) { 
        // CRITICAL FIX: Ensure listing_from is never null
        this.listing_from = (listing_from != null) ? listing_from : LocalDate.now(); 
    }
    
    public LocalDate getListing_to() { return listing_to; }
    public void setListing_to(LocalDate listing_to) { 
        this.listing_to = listing_to; // Can be null for ongoing listings
    }
    
    public Boolean getVerify_payments() { return verify_payments; }
    public void setVerify_payments(Boolean verify_payments) { 
        // CRITICAL FIX: Ensure verify_payments is never null
        this.verify_payments = (verify_payments != null) ? verify_payments : false; 
    }
}
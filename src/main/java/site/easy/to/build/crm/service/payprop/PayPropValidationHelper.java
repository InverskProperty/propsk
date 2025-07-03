// PayPropValidationHelper.java
package site.easy.to.build.crm.service.payprop;

import site.easy.to.build.crm.entity.*;
import java.util.ArrayList;
import java.util.List;

public class PayPropValidationHelper {

    public static List<String> validatePropertyForSync(Property property) {
        List<String> errors = new ArrayList<>();
        
        if (property.getPropertyName() == null || property.getPropertyName().trim().isEmpty()) {
            errors.add("Property name is required");
        }
        
        if (property.getCustomerId() == null || property.getCustomerId().trim().isEmpty()) {
            errors.add("Customer ID is required");
        }
        
        if (property.getMonthlyPayment() == null || property.getMonthlyPayment().doubleValue() <= 0) {
            errors.add("Monthly payment must be greater than 0");
        }
        
        return errors;
    }

    public static List<String> validateCustomerForTenantSync(Customer customer) {
        List<String> errors = new ArrayList<>();
        
        if (customer.getAccountType() == null) {
            errors.add("Account type is required");
        }
        
        if (customer.getAccountType() == AccountType.individual) {
            if (customer.getFirstName() == null || customer.getFirstName().trim().isEmpty()) {
                errors.add("First name is required for individual accounts");
            }
            if (customer.getLastName() == null || customer.getLastName().trim().isEmpty()) {
                errors.add("Last name is required for individual accounts");
            }
        } else if (customer.getAccountType() == AccountType.business) {
            if (customer.getBusinessName() == null || customer.getBusinessName().trim().isEmpty()) {
                errors.add("Business name is required for business accounts");
            }
        }
        
        if (customer.getEmail() != null && customer.getEmail().length() > 50) {
            errors.add("Email must be 50 characters or less for tenants");
        }
        
        return errors;
    }

    public static List<String> validateCustomerForBeneficiarySync(Customer customer) {
        List<String> errors = new ArrayList<>();
        
        // Run basic tenant validation first
        errors.addAll(validateCustomerForTenantSync(customer));
        
        if (customer.getPaymentMethod() == null) {
            errors.add("Payment method is required for beneficiaries");
        }
        
        if (customer.getPaymentMethod() == PaymentMethod.international) {
            if (customer.getAddressLine1() == null || customer.getCity() == null) {
                errors.add("Address is required for international payments");
            }
            
            boolean hasIban = customer.getBankIban() != null && !customer.getBankIban().trim().isEmpty();
            boolean hasAccountAndSwift = customer.getInternationalAccountNumber() != null && 
                                        customer.getBankSwiftCode() != null;
            
            if (!hasIban && !hasAccountAndSwift) {
                errors.add("IBAN or account number + SWIFT code required for international payments");
            }
        }
        
        if (customer.getPaymentMethod() == PaymentMethod.local) {
            if (customer.getBankAccountName() == null || 
                customer.getBankAccountNumber() == null || 
                customer.getBankSortCode() == null) {
                errors.add("Bank account details required for local payments");
            }
        }
        
        return errors;
    }
}
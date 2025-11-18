package site.easy.to.build.crm.dto;

import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.enums.CustomerType;

/**
 * DTO for displaying customers with their associated block information
 * Used in email compose page for customer selection and filtering
 */
public class CustomerWithBlockDTO {

    private Long customerId;
    private String name;
    private String email;
    private String phone;
    private CustomerType customerType;
    private String blockName;
    private Long blockId;

    // Constructor
    public CustomerWithBlockDTO() {
    }

    public CustomerWithBlockDTO(Customer customer, String blockName, Long blockId) {
        this.customerId = customer.getCustomerId();
        this.name = customer.getName();
        this.email = customer.getEmail();
        this.phone = customer.getPhone();
        this.customerType = customer.getCustomerType();
        this.blockName = blockName;
        this.blockId = blockId;
    }

    // Getters and Setters
    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public CustomerType getCustomerType() {
        return customerType;
    }

    public void setCustomerType(CustomerType customerType) {
        this.customerType = customerType;
    }

    public String getBlockName() {
        return blockName;
    }

    public void setBlockName(String blockName) {
        this.blockName = blockName;
    }

    public Long getBlockId() {
        return blockId;
    }

    public void setBlockId(Long blockId) {
        this.blockId = blockId;
    }

    @Override
    public String toString() {
        return "CustomerWithBlockDTO{" +
                "customerId=" + customerId +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", customerType=" + customerType +
                ", blockName='" + blockName + '\'' +
                '}';
    }
}

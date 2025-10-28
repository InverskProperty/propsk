package site.easy.to.build.crm.dto.statement;

/**
 * DTO for Property reference data in Option C statement generation
 *
 * Minimal property information for statement reference
 */
public class PropertyDTO {

    private Long propertyId;
    private String name;
    private String address;

    // Constructor
    public PropertyDTO() {
    }

    public PropertyDTO(Long propertyId, String name, String address) {
        this.propertyId = propertyId;
        this.name = name;
        this.address = address;
    }

    // Getters and Setters
    public Long getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(Long propertyId) {
        this.propertyId = propertyId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    @Override
    public String toString() {
        return "PropertyDTO{" +
                "propertyId=" + propertyId +
                ", name='" + name + '\'' +
                ", address='" + address + '\'' +
                '}';
    }
}

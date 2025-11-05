package site.easy.to.build.crm.service.property;

import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.Invoice;
import site.easy.to.build.crm.entity.Lead;
import site.easy.to.build.crm.entity.Property;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Service interface for converting property rental leads to tenants.
 * Handles the complete conversion workflow from interested lead to active tenant.
 */
public interface LeadConversionService {

    /**
     * Convert a lead to a tenant (Customer) and create lease (Invoice)
     * This is the main conversion method that handles the complete workflow
     *
     * @param lead Lead to convert
     * @param property Property to lease
     * @param leaseStartDate Start date of the lease
     * @param leaseEndDate End date of the lease
     * @param monthlyRent Monthly rent amount
     * @return Created customer (tenant)
     */
    Customer convertLeadToTenant(Lead lead, Property property, LocalDate leaseStartDate,
                                 LocalDate leaseEndDate, BigDecimal monthlyRent);

    /**
     * Convert a lead to a tenant with deposit information
     *
     * @param lead Lead to convert
     * @param property Property to lease
     * @param leaseStartDate Start date of the lease
     * @param leaseEndDate End date of the lease
     * @param monthlyRent Monthly rent amount
     * @param depositAmount Security deposit amount
     * @return Created customer (tenant)
     */
    Customer convertLeadToTenant(Lead lead, Property property, LocalDate leaseStartDate,
                                 LocalDate leaseEndDate, BigDecimal monthlyRent, BigDecimal depositAmount);

    /**
     * Create a customer from lead data
     * Does not create lease - useful for pre-conversion customer creation
     *
     * @param lead Lead to convert
     * @return Created customer
     */
    Customer createCustomerFromLead(Lead lead);

    /**
     * Create a lease (Invoice) for a tenant
     *
     * @param customer Tenant customer
     * @param property Property to lease
     * @param leaseStartDate Start date of the lease
     * @param leaseEndDate End date of the lease
     * @param monthlyRent Monthly rent amount
     * @return Created lease invoice
     */
    Invoice createLeaseForTenant(Customer customer, Property property, LocalDate leaseStartDate,
                                  LocalDate leaseEndDate, BigDecimal monthlyRent);

    /**
     * Mark lead as converted and link to customer
     *
     * @param lead Lead to mark as converted
     * @param customer Customer created from lead
     * @return Updated lead
     */
    Lead markLeadAsConverted(Lead lead, Customer customer);

    /**
     * Check if a lead can be converted to tenant
     * Validates required data and status
     *
     * @param lead Lead to check
     * @return true if lead can be converted
     */
    boolean canConvertLead(Lead lead);

    /**
     * Get conversion readiness status for a lead
     * Returns detailed information about what's missing/required
     *
     * @param lead Lead to check
     * @return Status message describing readiness
     */
    String getConversionReadinessStatus(Lead lead);

    /**
     * Reverse a lead conversion (rollback)
     * Useful if conversion was done in error
     * Does NOT delete the customer, only unlinks and changes status
     *
     * @param lead Lead to reverse
     * @return Updated lead
     */
    Lead reverseConversion(Lead lead);

    /**
     * Find matching properties for a lead based on their requirements
     *
     * @param lead Lead with property requirements
     * @return Matching properties
     */
    java.util.List<Property> findMatchingPropertiesForLead(Lead lead);

    /**
     * Calculate lead to tenant conversion rate for a property
     *
     * @param property Property to calculate for
     * @return Conversion rate as percentage
     */
    double calculateConversionRate(Property property);

    /**
     * Get all converted leads (successful conversions)
     *
     * @return List of converted leads
     */
    java.util.List<Lead> getConvertedLeads();

    /**
     * Get leads ready for conversion (in-contracts status)
     *
     * @return List of leads ready to convert
     */
    java.util.List<Lead> getLeadsReadyForConversion();

    /**
     * Get average time from enquiry to conversion
     *
     * @return Average days to convert
     */
    double getAverageDaysToConversion();
}

package site.easy.to.build.crm.service.property;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.repository.CustomerRepository;
import site.easy.to.build.crm.repository.InvoiceRepository;
import site.easy.to.build.crm.repository.LeadRepository;
import site.easy.to.build.crm.repository.PropertyRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of LeadConversionService.
 * Handles converting property rental leads to tenants.
 */
@Service
public class LeadConversionServiceImpl implements LeadConversionService {

    private final LeadRepository leadRepository;
    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final PropertyRepository propertyRepository;

    public LeadConversionServiceImpl(LeadRepository leadRepository,
                                     CustomerRepository customerRepository,
                                     InvoiceRepository invoiceRepository,
                                     PropertyRepository propertyRepository) {
        this.leadRepository = leadRepository;
        this.customerRepository = customerRepository;
        this.invoiceRepository = invoiceRepository;
        this.propertyRepository = propertyRepository;
    }

    @Override
    @Transactional
    public Customer convertLeadToTenant(Lead lead, Property property, LocalDate leaseStartDate,
                                        LocalDate leaseEndDate, BigDecimal monthlyRent) {
        return convertLeadToTenant(lead, property, leaseStartDate, leaseEndDate, monthlyRent, null);
    }

    @Override
    @Transactional
    public Customer convertLeadToTenant(Lead lead, Property property, LocalDate leaseStartDate,
                                        LocalDate leaseEndDate, BigDecimal monthlyRent,
                                        BigDecimal depositAmount) {
        // Validate conversion eligibility
        if (!canConvertLead(lead)) {
            throw new IllegalStateException("Lead cannot be converted: " + getConversionReadinessStatus(lead));
        }

        // Create customer from lead
        Customer customer = createCustomerFromLead(lead);

        // Create lease invoice
        Invoice lease = createLeaseForTenant(customer, property, leaseStartDate, leaseEndDate, monthlyRent);

        // Mark lead as converted
        markLeadAsConverted(lead, customer);

        // Update property status to OCCUPIED
        property.markOccupied();
        propertyRepository.save(property);

        return customer;
    }

    @Override
    @Transactional
    public Customer createCustomerFromLead(Lead lead) {
        Customer customer = new Customer();

        // Basic information from lead
        customer.setName(lead.getName());
        // Note: Lead entity doesn't have email field - would need to be added
        customer.setPhone(lead.getPhone());
        customer.setCountry("United Kingdom"); // Default, can be updated

        // Mark as tenant
        customer.setIsTenant(true);
        customer.setCustomerType(CustomerType.TENANT);

        // Additional tenant-specific information
        if (lead.getEmploymentStatus() != null) {
            customer.setDescription("Employment: " + lead.getEmploymentStatus());
        }

        return customerRepository.save(customer);
    }

    @Override
    @Transactional
    public Invoice createLeaseForTenant(Customer customer, Property property, LocalDate leaseStartDate,
                                        LocalDate leaseEndDate, BigDecimal monthlyRent) {
        Invoice lease = new Invoice();

        // Link to customer and property
        lease.setCustomer(customer);
        lease.setProperty(property);

        // Lease details
        lease.setInvoiceType("Rent");
        lease.setInvoiceFrequency(Invoice.InvoiceFrequency.MONTHLY);
        lease.setAmount(monthlyRent);
        lease.setStartDate(leaseStartDate);
        lease.setEndDate(leaseEndDate);
        lease.setNextDueDate(leaseStartDate);

        // Set sync status as active
        lease.setSyncStatus(Invoice.SyncStatus.ACTIVE);

        // Generate invoice number
        lease.setInvoiceNumber("LEASE-" + property.getId() + "-" +
                              leaseStartDate.toString().replace("-", ""));

        return invoiceRepository.save(lease);
    }

    @Override
    @Transactional
    public Lead markLeadAsConverted(Lead lead, Customer customer) {
        lead.setStatus("converted");
        lead.setConvertedAt(LocalDateTime.now());
        lead.setConvertedToCustomer(customer);
        return leadRepository.save(lead);
    }

    @Override
    public boolean canConvertLead(Lead lead) {
        // Must be a property rental lead
        if (lead.getLeadType() != LeadType.PROPERTY_RENTAL) {
            return false;
        }

        // Must not already be converted
        if ("converted".equals(lead.getStatus())) {
            return false;
        }

        // Must have required contact information
        if (lead.getName() == null || lead.getName().trim().isEmpty()) {
            return false;
        }

        // Should typically be in later stage of funnel
        // But we don't enforce this strictly as manual conversions may happen

        return true;
    }

    @Override
    public String getConversionReadinessStatus(Lead lead) {
        List<String> issues = new ArrayList<>();

        if (lead.getLeadType() != LeadType.PROPERTY_RENTAL) {
            issues.add("Lead type must be PROPERTY_RENTAL");
        }

        if ("converted".equals(lead.getStatus())) {
            issues.add("Lead is already converted");
        }

        if (lead.getName() == null || lead.getName().trim().isEmpty()) {
            issues.add("Lead name is required");
        }

        if (lead.getProperty() == null) {
            issues.add("Property assignment recommended (but not required)");
        }

        if (!"in-contracts".equals(lead.getStatus()) &&
            !"interested".equals(lead.getStatus()) &&
            !"application-submitted".equals(lead.getStatus()) &&
            !"referencing".equals(lead.getStatus())) {
            issues.add("Warning: Lead is not in expected conversion stage (current: " +
                      lead.getStatus() + ")");
        }

        if (issues.isEmpty()) {
            return "Ready for conversion";
        }

        return "Issues: " + String.join("; ", issues);
    }

    @Override
    @Transactional
    public Lead reverseConversion(Lead lead) {
        if (!"converted".equals(lead.getStatus())) {
            throw new IllegalStateException("Lead is not converted, cannot reverse");
        }

        lead.setStatus("lost");
        lead.setConvertedAt(null);
        lead.setConvertedToCustomer(null);

        return leadRepository.save(lead);
    }

    @Override
    public List<Property> findMatchingPropertiesForLead(Lead lead) {
        if (lead.getBudgetMax() == null) {
            // No budget specified, return all available properties
            return propertyRepository.findPropertiesAvailableForLetting();
        }

        // Find properties matching budget and availability
        LocalDate availableFrom = lead.getDesiredMoveInDate() != null ?
                                 lead.getDesiredMoveInDate() : LocalDate.now();

        return propertyRepository.findSuitablePropertiesForLead(
            null, // bedrooms - Lead entity doesn't have this field yet
            lead.getBudgetMax(),
            availableFrom
        );
    }

    @Override
    public double calculateConversionRate(Property property) {
        long totalLeads = leadRepository.countByProperty(property);
        if (totalLeads == 0) {
            return 0.0;
        }

        long convertedLeads = leadRepository.countByPropertyAndStatus(property, "converted");
        return (convertedLeads * 100.0) / totalLeads;
    }

    @Override
    public List<Lead> getConvertedLeads() {
        return leadRepository.findByStatus("converted");
    }

    @Override
    public List<Lead> getLeadsReadyForConversion() {
        // Leads in 'in-contracts' status are typically ready for conversion
        return leadRepository.findByStatus("in-contracts");
    }

    @Override
    public double getAverageDaysToConversion() {
        List<Lead> convertedLeads = getConvertedLeads();

        if (convertedLeads.isEmpty()) {
            return 0.0;
        }

        long totalDays = 0;
        int count = 0;

        for (Lead lead : convertedLeads) {
            if (lead.getCreatedAt() != null && lead.getConvertedAt() != null) {
                long days = ChronoUnit.DAYS.between(
                    lead.getCreatedAt().toLocalDate(),
                    lead.getConvertedAt().toLocalDate()
                );
                totalDays += days;
                count++;
            }
        }

        return count > 0 ? (double) totalDays / count : 0.0;
    }
}

package site.easy.to.build.crm.service.assignment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.CustomerPropertyAssignment;
import site.easy.to.build.crm.entity.Invoice;
import site.easy.to.build.crm.entity.AssignmentType;
import site.easy.to.build.crm.entity.Invoice.InvoiceFrequency;
import site.easy.to.build.crm.entity.Invoice.SyncStatus;
import site.easy.to.build.crm.repository.CustomerPropertyAssignmentRepository;
import site.easy.to.build.crm.repository.InvoiceRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Service to create Tenant Assignment + Lease (Invoice) together
 *
 * SOLVES THE WORKFLOW GAP:
 * Previously, creating a tenant assignment didn't create a lease
 * This service ensures both are created together for consistent data
 *
 * Usage:
 *   assignmentService.createTenantWithLease(customer, property, rentAmount, startDate);
 *
 * This creates:
 *   1. CustomerPropertyAssignment (links tenant to property)
 *   2. Invoice (represents the lease with financial terms)
 */
@Service
@Transactional
public class TenantAssignmentWithLeaseService {

    private static final Logger log = LoggerFactory.getLogger(TenantAssignmentWithLeaseService.class);

    @Autowired
    private CustomerPropertyAssignmentRepository assignmentRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    /**
     * Create tenant assignment AND corresponding lease in one operation
     *
     * @param customer The tenant (Customer entity)
     * @param property The property being rented
     * @param rentAmount Monthly rent amount
     * @param startDate Lease start date
     * @param endDate Lease end date (null if ongoing)
     * @param paymentDay Day of month rent is due (default 1)
     * @param syncToPayProp Whether to sync to PayProp
     * @return The created lease (Invoice)
     */
    public TenantLeaseResult createTenantWithLease(
            Customer customer,
            Property property,
            BigDecimal rentAmount,
            LocalDate startDate,
            LocalDate endDate,
            Integer paymentDay,
            boolean syncToPayProp) {

        log.info("🔗 Creating tenant assignment WITH lease: {} → {} (£{}/month)",
                customer.getName(), property.getPropertyName(), rentAmount);

        TenantLeaseResult result = new TenantLeaseResult();

        try {
            // Step 1: Create tenant assignment
            CustomerPropertyAssignment assignment = new CustomerPropertyAssignment();
            assignment.setCustomer(customer);
            assignment.setProperty(property);
            assignment.setAssignmentType(AssignmentType.TENANT);
            assignment.setStartDate(startDate);
            assignment.setEndDate(endDate);
            assignment.setCreatedAt(LocalDateTime.now());

            CustomerPropertyAssignment savedAssignment = assignmentRepository.save(assignment);
            result.assignment = savedAssignment;

            log.info("✅ Created tenant assignment: ID {}", savedAssignment.getId());

            // Step 2: Create lease (Invoice)
            Invoice lease = new Invoice();
            lease.setCustomer(customer);
            lease.setProperty(property);
            lease.setCategoryId("rent");
            lease.setCategoryName("Rent");
            lease.setAmount(rentAmount);
            lease.setFrequency(InvoiceFrequency.M); // Monthly by default
            lease.setPaymentDay(paymentDay != null ? paymentDay : 1);
            lease.setStartDate(startDate);
            lease.setEndDate(endDate);
            lease.setDescription(String.format("Monthly rent for %s at %s",
                    customer.getName(), property.getPropertyName()));
            lease.setIsActive(true);

            // Set sync status
            if (syncToPayProp) {
                lease.setSyncStatus(SyncStatus.pending);
            } else {
                lease.setSyncStatus(SyncStatus.manual);
            }

            Invoice savedLease = invoiceRepository.save(lease);
            result.lease = savedLease;

            log.info("✅ Created lease (invoice): ID {}", savedLease.getId());

            // Link assignment to lease
            savedAssignment.setPaypropInvoiceId(savedLease.getId().toString());
            assignmentRepository.save(savedAssignment);

            result.success = true;
            result.message = String.format("Successfully created tenant assignment and lease for %s at %s (£%.2f/month)",
                    customer.getName(), property.getPropertyName(), rentAmount);

            log.info("✅ Tenant + Lease creation complete");

        } catch (Exception e) {
            log.error("❌ Failed to create tenant with lease", e);
            result.success = false;
            result.message = "Error: " + e.getMessage();
        }

        return result;
    }

    /**
     * Create tenant assignment with lease using default payment day (1st of month)
     */
    public TenantLeaseResult createTenantWithLease(
            Customer customer,
            Property property,
            BigDecimal rentAmount,
            LocalDate startDate,
            LocalDate endDate,
            boolean syncToPayProp) {
        return createTenantWithLease(customer, property, rentAmount, startDate, endDate, 1, syncToPayProp);
    }

    /**
     * Create tenant assignment with lease using default payment day and no end date
     */
    public TenantLeaseResult createTenantWithLease(
            Customer customer,
            Property property,
            BigDecimal rentAmount,
            LocalDate startDate,
            boolean syncToPayProp) {
        return createTenantWithLease(customer, property, rentAmount, startDate, null, 1, syncToPayProp);
    }

    /**
     * Update existing tenant assignment to add a lease if missing
     * Useful for migrating old tenant assignments that don't have leases
     */
    public TenantLeaseResult addLeaseToExistingAssignment(
            Long assignmentId,
            BigDecimal rentAmount,
            Integer paymentDay) {

        TenantLeaseResult result = new TenantLeaseResult();

        try {
            CustomerPropertyAssignment assignment = assignmentRepository.findById(assignmentId)
                    .orElse(null);

            if (assignment == null) {
                result.success = false;
                result.message = "Assignment not found";
                return result;
            }

            if (assignment.getAssignmentType() != AssignmentType.TENANT) {
                result.success = false;
                result.message = "Assignment is not a tenant assignment";
                return result;
            }

            // Check if lease already exists
            if (assignment.getPaypropInvoiceId() != null) {
                result.success = false;
                result.message = "Lease already exists for this assignment";
                return result;
            }

            // Create lease
            Invoice lease = new Invoice();
            lease.setCustomer(assignment.getCustomer());
            lease.setProperty(assignment.getProperty());
            lease.setCategoryId("rent");
            lease.setCategoryName("Rent");
            lease.setAmount(rentAmount);
            lease.setFrequency(InvoiceFrequency.M);
            lease.setPaymentDay(paymentDay != null ? paymentDay : 1);
            lease.setStartDate(assignment.getStartDate() != null ? assignment.getStartDate() : LocalDate.now());
            lease.setEndDate(assignment.getEndDate());
            lease.setDescription(String.format("Monthly rent for %s at %s",
                    assignment.getCustomer().getName(),
                    assignment.getProperty().getPropertyName()));
            lease.setIsActive(true);
            lease.setSyncStatus(SyncStatus.manual);

            Invoice savedLease = invoiceRepository.save(lease);

            // Link assignment to lease
            assignment.setPaypropInvoiceId(savedLease.getId().toString());
            assignmentRepository.save(assignment);

            result.assignment = assignment;
            result.lease = savedLease;
            result.success = true;
            result.message = "Successfully added lease to existing assignment";

            log.info("✅ Added lease {} to existing assignment {}", savedLease.getId(), assignmentId);

        } catch (Exception e) {
            log.error("❌ Failed to add lease to assignment {}", assignmentId, e);
            result.success = false;
            result.message = "Error: " + e.getMessage();
        }

        return result;
    }

    /**
     * Find all tenant assignments that don't have leases
     * Useful for migration/cleanup
     */
    @Transactional(readOnly = true)
    public java.util.List<CustomerPropertyAssignment> findTenantAssignmentsWithoutLeases() {
        return assignmentRepository.findAll().stream()
                .filter(a -> a.getAssignmentType() == AssignmentType.TENANT)
                .filter(a -> a.getPaypropInvoiceId() == null || a.getPaypropInvoiceId().isEmpty())
                .toList();
    }

    // ===== RESULT CLASS =====

    public static class TenantLeaseResult {
        public boolean success;
        public String message;
        public CustomerPropertyAssignment assignment;
        public Invoice lease;
    }
}

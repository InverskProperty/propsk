package site.easy.to.build.crm.service.synchronization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.Tenant;
import site.easy.to.build.crm.repository.TenantRepository;
import site.easy.to.build.crm.service.customer.CustomerService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service to maintain bidirectional synchronization between Tenants and Customers tables.
 *
 * PROBLEM:
 * - Wizard imports create tenant records with payprop_id but NULL customer_id
 * - PayProp sync creates customer records with payprop_entity_id
 * - No automatic linking causes data disconnection
 *
 * SOLUTION:
 * - Automatically links tenant.customer_id to customers.customer_id
 * - Based on matching payprop_id ↔ payprop_entity_id
 * - Runs during full sync and can be triggered manually
 * - Idempotent - safe to run multiple times
 */
@Service
public class TenantCustomerLinkService {

    private static final Logger log = LoggerFactory.getLogger(TenantCustomerLinkService.class);

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private CustomerService customerService;

    /**
     * Link all unlinked tenants to their corresponding customer records.
     * This is the main method called during sync operations.
     *
     * @return LinkResult with statistics
     */
    @Transactional
    public LinkResult linkAllTenantsToCustomers() {
        log.info("🔗 Starting tenant-customer synchronization...");

        LinkResult result = new LinkResult();
        long startTime = System.currentTimeMillis();

        try {
            // Find all tenants with payprop_id
            List<Tenant> allTenants = tenantRepository.findByPayPropIdIsNotNull();
            result.setTotalTenantsChecked(allTenants.size());

            log.info("📊 Found {} tenants with PayProp IDs to check", allTenants.size());

            for (Tenant tenant : allTenants) {
                try {
                    boolean linked = linkSingleTenant(tenant);
                    if (linked) {
                        result.incrementLinked();
                        log.debug("✅ Linked tenant {} to customer", tenant.getPayPropId());
                    } else {
                        result.incrementAlreadyLinked();
                        log.debug("ℹ️ Tenant {} already linked", tenant.getPayPropId());
                    }
                } catch (CustomerNotFoundException e) {
                    result.incrementNoMatchingCustomer();
                    log.warn("⚠️ No matching customer for tenant {} ({})",
                        getTenantDisplayName(tenant), tenant.getPayPropId());
                } catch (Exception e) {
                    result.incrementErrors();
                    log.error("❌ Failed to link tenant {}: {}",
                        tenant.getPayPropId(), e.getMessage(), e);
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            result.setDurationMs(duration);
            result.setSuccess(result.getErrors() == 0);

            log.info("✅ Tenant-customer linking completed in {}ms", duration);
            log.info("📊 Results: {} newly linked, {} already linked, {} no match, {} errors",
                result.getNewlyLinked(), result.getAlreadyLinked(),
                result.getNoMatchingCustomer(), result.getErrors());

            return result;

        } catch (Exception e) {
            log.error("❌ Tenant-customer linking failed", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setDurationMs(System.currentTimeMillis() - startTime);
            return result;
        }
    }

    /**
     * Link a specific tenant to their customer record.
     *
     * @param tenant The tenant to link
     * @return true if newly linked, false if already linked
     * @throws CustomerNotFoundException if no matching customer found
     */
    @Transactional
    public boolean linkSingleTenant(Tenant tenant) throws CustomerNotFoundException {
        // Already linked?
        if (tenant.getCustomer() != null) {
            return false;
        }

        // No PayProp ID? Can't link
        if (tenant.getPayPropId() == null || tenant.getPayPropId().trim().isEmpty()) {
            throw new IllegalArgumentException("Tenant has no PayProp ID");
        }

        // Find matching customer by payprop_entity_id
        Customer customer = customerService.findByPayPropEntityId(tenant.getPayPropId());

        if (customer == null) {
            throw new CustomerNotFoundException(
                String.format("No customer found with payprop_entity_id=%s", tenant.getPayPropId()));
        }

        // Link them!
        tenant.setCustomer(customer);
        tenantRepository.save(tenant);

        log.info("🔗 Linked tenant '{}' (payprop_id={}) to customer {} (customer_id={})",
            getTenantDisplayName(tenant), tenant.getPayPropId(),
            customer.getName(), customer.getCustomerId());

        return true;
    }

    /**
     * Link tenants by PayProp ID list (useful for batch operations).
     *
     * @param payPropIds List of PayProp tenant IDs to link
     * @return LinkResult with statistics
     */
    @Transactional
    public LinkResult linkTenantsByPayPropIds(List<String> payPropIds) {
        log.info("🔗 Linking {} specific tenants to customers", payPropIds.size());

        LinkResult result = new LinkResult();
        long startTime = System.currentTimeMillis();

        for (String payPropId : payPropIds) {
            try {
                Tenant tenant = tenantRepository.findByPayPropId(payPropId)
                    .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + payPropId));

                boolean linked = linkSingleTenant(tenant);
                if (linked) {
                    result.incrementLinked();
                } else {
                    result.incrementAlreadyLinked();
                }

            } catch (CustomerNotFoundException e) {
                result.incrementNoMatchingCustomer();
                log.warn("⚠️ No matching customer for payprop_id={}", payPropId);
            } catch (Exception e) {
                result.incrementErrors();
                log.error("❌ Failed to link tenant {}: {}", payPropId, e.getMessage());
            }
        }

        result.setTotalTenantsChecked(payPropIds.size());
        result.setDurationMs(System.currentTimeMillis() - startTime);
        result.setSuccess(result.getErrors() == 0);

        return result;
    }

    /**
     * Check linking status without making changes.
     *
     * @return Map with statistics
     */
    public Map<String, Integer> checkLinkingStatus() {
        Map<String, Integer> status = new HashMap<>();

        List<Tenant> allTenants = tenantRepository.findByPayPropIdIsNotNull();
        int totalWithPayProp = allTenants.size();
        int linked = 0;
        int unlinked = 0;
        int matchAvailable = 0;

        for (Tenant tenant : allTenants) {
            if (tenant.getCustomer() != null) {
                linked++;
            } else {
                unlinked++;
                // Check if matching customer exists
                Customer customer = customerService.findByPayPropEntityId(tenant.getPayPropId());
                if (customer != null) {
                    matchAvailable++;
                }
            }
        }

        status.put("totalTenantsWithPayPropId", totalWithPayProp);
        status.put("linked", linked);
        status.put("unlinked", unlinked);
        status.put("unlinkedWithMatchAvailable", matchAvailable);
        status.put("unlinkedWithNoMatch", unlinked - matchAvailable);

        log.info("📊 Linking Status: {} total, {} linked, {} unlinked ({} can be linked, {} no match)",
            totalWithPayProp, linked, unlinked, matchAvailable, unlinked - matchAvailable);

        return status;
    }

    // Helper methods

    private String getTenantDisplayName(Tenant tenant) {
        if (tenant.getBusinessName() != null && !tenant.getBusinessName().trim().isEmpty()) {
            return tenant.getBusinessName();
        }
        String name = (tenant.getFirstName() + " " + tenant.getLastName()).trim();
        return name.isEmpty() ? tenant.getEmailAddress() : name;
    }

    // Result class

    public static class LinkResult {
        private boolean success;
        private int totalTenantsChecked;
        private int newlyLinked;
        private int alreadyLinked;
        private int noMatchingCustomer;
        private int errors;
        private long durationMs;
        private String errorMessage;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public int getTotalTenantsChecked() { return totalTenantsChecked; }
        public void setTotalTenantsChecked(int total) { this.totalTenantsChecked = total; }

        public int getNewlyLinked() { return newlyLinked; }
        public void incrementLinked() { this.newlyLinked++; }

        public int getAlreadyLinked() { return alreadyLinked; }
        public void incrementAlreadyLinked() { this.alreadyLinked++; }

        public int getNoMatchingCustomer() { return noMatchingCustomer; }
        public void incrementNoMatchingCustomer() { this.noMatchingCustomer++; }

        public int getErrors() { return errors; }
        public void incrementErrors() { this.errors++; }

        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long duration) { this.durationMs = duration; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String message) { this.errorMessage = message; }

        public String getSummary() {
            return String.format("Checked %d tenants: %d newly linked, %d already linked, %d no match, %d errors",
                totalTenantsChecked, newlyLinked, alreadyLinked, noMatchingCustomer, errors);
        }
    }

    // Exception class

    public static class CustomerNotFoundException extends Exception {
        public CustomerNotFoundException(String message) {
            super(message);
        }
    }
}

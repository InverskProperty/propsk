package site.easy.to.build.crm.service.statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.dto.statement.CustomerDTO;
import site.easy.to.build.crm.dto.statement.LeaseMasterDTO;
import site.easy.to.build.crm.dto.statement.PropertyDTO;
import site.easy.to.build.crm.dto.statement.TransactionDTO;
import site.easy.to.build.crm.entity.AssignmentType;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.HistoricalTransaction;
import site.easy.to.build.crm.entity.Invoice;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.UnifiedTransaction;
import site.easy.to.build.crm.repository.CustomerPropertyAssignmentRepository;
import site.easy.to.build.crm.repository.CustomerRepository;
import site.easy.to.build.crm.repository.HistoricalTransactionRepository;
import site.easy.to.build.crm.repository.InvoiceRepository;
import site.easy.to.build.crm.repository.PropertyRepository;
import site.easy.to.build.crm.repository.UnifiedTransactionRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for extracting raw data for Option C statement generation
 *
 * KEY PRINCIPLE: NO CALCULATIONS
 * - This service only extracts data from database
 * - All calculations done in Excel formulas
 * - Returns DTOs with raw values only
 */
@Service
public class StatementDataExtractService {

    private static final Logger log = LoggerFactory.getLogger(StatementDataExtractService.class);

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private HistoricalTransactionRepository historicalTransactionRepository;

    @Autowired
    private UnifiedTransactionRepository unifiedTransactionRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerPropertyAssignmentRepository customerPropertyAssignmentRepository;

    /**
     * Extract lease master data (all leases)
     *
     * Returns one row per lease with:
     * - Lease details (id, reference, dates, rent amount)
     * - Property details (id, name, address)
     * - Customer details (id, name)
     *
     * NO CALCULATIONS - Raw data only
     *
     * @return List of lease master records
     */
    public List<LeaseMasterDTO> extractLeaseMaster() {
        log.info("Extracting lease master data...");

        // Get all invoices with lease references (these are the actual leases)
        List<Invoice> invoices = invoiceRepository.findAll().stream()
            .filter(i -> i.getLeaseReference() != null && !i.getLeaseReference().trim().isEmpty())
            .collect(Collectors.toList());

        log.info("Found {} leases with lease references", invoices.size());

        List<LeaseMasterDTO> leaseMaster = new ArrayList<>();

        for (Invoice invoice : invoices) {
            LeaseMasterDTO dto = new LeaseMasterDTO();

            // Lease details
            dto.setLeaseId(invoice.getId());
            dto.setLeaseReference(invoice.getLeaseReference());
            dto.setStartDate(invoice.getStartDate());
            dto.setEndDate(invoice.getEndDate());  // NULL = ongoing lease
            dto.setMonthlyRent(invoice.getAmount());
            dto.setFrequency(invoice.getFrequency() != null ? invoice.getFrequency().name() : "MONTHLY");

            // Property details
            Property property = invoice.getProperty();
            if (property != null) {
                dto.setPropertyId(property.getId());
                dto.setPropertyName(property.getPropertyName());
                dto.setPropertyAddress(property.getFullAddress());
            }

            // Customer details
            Customer customer = invoice.getCustomer();
            if (customer != null) {
                dto.setCustomerId(customer.getCustomerId());
                dto.setCustomerName(customer.getName());
            }

            leaseMaster.add(dto);
        }

        log.info("Extracted {} lease master records", leaseMaster.size());
        return leaseMaster;
    }

    /**
     * Extract lease master data for specific customer
     *
     * @param customerId Customer ID to filter by
     * @return List of lease master records for this customer
     */
    public List<LeaseMasterDTO> extractLeaseMasterForCustomer(Long customerId) {
        log.error("🔍 DEBUG: Extracting lease master data for customer {}...", customerId);

        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null) {
            log.error("🔍 DEBUG: Customer {} not found - returning empty list", customerId);
            return new ArrayList<>();
        }

        log.error("🔍 DEBUG: Customer {} found, type={}", customerId, customer.getCustomerType());

        // Get leases based on customer type
        List<Invoice> invoices;

        if (customer.getCustomerType() == site.easy.to.build.crm.entity.CustomerType.PROPERTY_OWNER ||
            customer.getCustomerType() == site.easy.to.build.crm.entity.CustomerType.DELEGATED_USER) {
            // For property owners and delegated users: Get leases for properties they OWN
            log.error("🔍 DEBUG: Customer {} is {}, getting leases for owned properties",
                customerId, customer.getCustomerType());

            // Get properties where customer is OWNER or MANAGER (same logic as PropertyService)
            List<Long> ownerPropertyIds = customerPropertyAssignmentRepository
                .findPropertyIdsByCustomerIdAndAssignmentType(customerId, AssignmentType.OWNER);
            List<Long> managerPropertyIds = customerPropertyAssignmentRepository
                .findPropertyIdsByCustomerIdAndAssignmentType(customerId, AssignmentType.MANAGER);

            // Combine and deduplicate into final variable for lambda
            final List<Long> ownedPropertyIds = new ArrayList<>();
            ownedPropertyIds.addAll(ownerPropertyIds);
            ownedPropertyIds.addAll(managerPropertyIds);
            final List<Long> deduplicatedIds = ownedPropertyIds.stream().distinct().collect(Collectors.toList());

            log.error("🔍 DEBUG: Found {} OWNER + {} MANAGER = {} total properties for customer {}, IDs: {}",
                ownerPropertyIds.size(), managerPropertyIds.size(), deduplicatedIds.size(), customerId, deduplicatedIds);

            invoices = invoiceRepository.findAll().stream()
                .filter(i -> i.getProperty() != null)
                .filter(i -> ownedPropertyIds.contains(i.getProperty().getId()))
                .filter(i -> i.getLeaseReference() != null && !i.getLeaseReference().trim().isEmpty())
                .collect(Collectors.toList());

            log.error("🔍 DEBUG: After filtering invoices for customer {}: {} leases found", customerId, invoices.size());
        } else {
            // For tenants: Get leases where they are the customer
            log.error("🔍 DEBUG: Customer {} is TENANT, getting leases where they are the customer", customerId);
            invoices = invoiceRepository.findByCustomer(customer).stream()
                .filter(i -> i.getLeaseReference() != null && !i.getLeaseReference().trim().isEmpty())
                .collect(Collectors.toList());
        }

        log.error("🔍 DEBUG: Total {} leases found for customer {}", invoices.size(), customerId);

        List<LeaseMasterDTO> leaseMaster = new ArrayList<>();

        for (Invoice invoice : invoices) {
            LeaseMasterDTO dto = new LeaseMasterDTO();

            // Lease details
            dto.setLeaseId(invoice.getId());
            dto.setLeaseReference(invoice.getLeaseReference());
            dto.setStartDate(invoice.getStartDate());
            dto.setEndDate(invoice.getEndDate());
            dto.setMonthlyRent(invoice.getAmount());
            dto.setFrequency(invoice.getFrequency() != null ? invoice.getFrequency().name() : "MONTHLY");

            // Property details
            Property property = invoice.getProperty();
            if (property != null) {
                dto.setPropertyId(property.getId());
                dto.setPropertyName(property.getPropertyName());
                dto.setPropertyAddress(property.getFullAddress());
            }

            // Customer details (always same customer in this method)
            dto.setCustomerId(customer.getCustomerId());
            dto.setCustomerName(customer.getName());

            leaseMaster.add(dto);
        }

        // Log lease_id distribution to match against transaction invoice_ids
        log.error("🔍 DEBUG: Lease_id distribution for customer {}: {}",
            customerId,
            leaseMaster.stream()
                .map(l -> "lease_id=" + l.getLeaseId() + " ref=" + l.getLeaseReference() + " property=" + l.getPropertyName())
                .collect(Collectors.toList()));

        log.info("Extracted {} lease master records for customer {}", leaseMaster.size(), customerId);
        return leaseMaster;
    }

    /**
     * Extract all transactions linked to leases
     *
     * Returns transactions where invoice_id IS NOT NULL
     * Optionally filters by date range
     *
     * NO CALCULATIONS - Raw transaction data only
     *
     * @param startDate Optional start date filter (inclusive)
     * @param endDate Optional end date filter (inclusive)
     * @return List of transactions
     */
    public List<TransactionDTO> extractTransactions(LocalDate startDate, LocalDate endDate) {
        log.info("✨ Extracting UNIFIED transactions (historical + PayProp) from {} to {}...", startDate, endDate);

        // Get all transactions with invoice_id populated from unified_transactions
        // This includes BOTH historical AND PayProp data
        List<UnifiedTransaction> transactions;

        if (startDate != null && endDate != null) {
            // Filter by date range
            transactions = unifiedTransactionRepository.findByTransactionDateBetween(startDate, endDate);
        } else {
            // All transactions with invoice_id (unified table already filters for invoice_id IS NOT NULL)
            transactions = unifiedTransactionRepository.findByInvoiceIdIsNotNull();
        }

        log.info("✨ Found {} UNIFIED transactions (HISTORICAL + PAYPROP)", transactions.size());

        List<TransactionDTO> transactionDTOs = new ArrayList<>();

        for (UnifiedTransaction ut : transactions) {
            TransactionDTO dto = new TransactionDTO();

            dto.setTransactionId(ut.getId());
            dto.setTransactionDate(ut.getTransactionDate());
            dto.setInvoiceId(ut.getInvoiceId());
            dto.setPropertyId(ut.getPropertyId());
            dto.setPropertyName(ut.getPropertyName());  // For matching INCOMING_PAYMENT to leases
            dto.setCustomerId(ut.getCustomerId());
            dto.setCategory(ut.getCategory());
            dto.setTransactionType(null); // UnifiedTransaction doesn't have transactionType
            dto.setAmount(ut.getAmount());
            dto.setDescription(ut.getDescription());
            dto.setLeaseStartDate(ut.getLeaseStartDate());
            dto.setLeaseEndDate(ut.getLeaseEndDate());
            dto.setRentAmountAtTransaction(ut.getRentAmountAtTransaction());

            transactionDTOs.add(dto);
        }

        log.info("✨ Extracted {} UNIFIED transaction records (shows historical + PayProp data)", transactionDTOs.size());
        return transactionDTOs;
    }

    /**
     * Extract transactions for specific customer
     *
     * @param customerId Customer ID to filter by
     * @param startDate Optional start date filter
     * @param endDate Optional end date filter
     * @return List of transactions for this customer
     */
    public List<TransactionDTO> extractTransactionsForCustomer(Long customerId, LocalDate startDate, LocalDate endDate) {
        log.info("✨ UNIFIED: Extracting transactions for customer {} from {} to {}...", customerId, startDate, endDate);

        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null) {
            log.error("Customer {} not found - returning empty list", customerId);
            return new ArrayList<>();
        }

        log.info("✨ UNIFIED: Customer {} found, type={}", customerId, customer.getCustomerType());

        // Use unified_transactions repository's built-in query
        // This automatically handles OWNER + MANAGER assignments and filters by date range
        List<UnifiedTransaction> transactions = unifiedTransactionRepository
            .findByCustomerOwnedPropertiesAndDateRange(customerId, startDate, endDate);

        log.info("✨ UNIFIED: Found {} transactions (HISTORICAL + PAYPROP) for customer {}",
            transactions.size(), customerId);

        List<TransactionDTO> transactionDTOs = new ArrayList<>();

        for (UnifiedTransaction ut : transactions) {
            TransactionDTO dto = new TransactionDTO();

            dto.setTransactionId(ut.getId());
            dto.setTransactionDate(ut.getTransactionDate());
            dto.setInvoiceId(ut.getInvoiceId());
            dto.setPropertyId(ut.getPropertyId());
            dto.setPropertyName(ut.getPropertyName());  // For matching INCOMING_PAYMENT to leases
            dto.setCustomerId(ut.getCustomerId());
            dto.setCategory(ut.getCategory());
            dto.setTransactionType(null); // UnifiedTransaction doesn't have transactionType
            dto.setAmount(ut.getAmount());
            dto.setDescription(ut.getDescription());
            dto.setLeaseStartDate(ut.getLeaseStartDate());
            dto.setLeaseEndDate(ut.getLeaseEndDate());
            dto.setRentAmountAtTransaction(ut.getRentAmountAtTransaction());

            transactionDTOs.add(dto);
        }

        // Log invoice_id distribution to understand why Monthly Statement shows £0.00
        log.error("🔍 DEBUG: Transaction invoice_id distribution for customer {}: {}",
            customerId,
            transactionDTOs.stream()
                .map(t -> "invoice_id=" + t.getInvoiceId() + " amount=" + t.getAmount())
                .collect(Collectors.toList()));

        log.info("Extracted {} transaction records for customer {}", transactionDTOs.size(), customerId);
        return transactionDTOs;
    }

    /**
     * Extract all properties (for reference)
     *
     * @return List of properties
     */
    public List<PropertyDTO> extractProperties() {
        log.info("Extracting property reference data...");

        List<Property> properties = propertyRepository.findAll();
        List<PropertyDTO> propertyDTOs = new ArrayList<>();

        for (Property property : properties) {
            PropertyDTO dto = new PropertyDTO();
            dto.setPropertyId(property.getId());
            dto.setName(property.getPropertyName());
            dto.setAddress(property.getFullAddress());
            propertyDTOs.add(dto);
        }

        log.info("Extracted {} property records", propertyDTOs.size());
        return propertyDTOs;
    }

    /**
     * Extract all customers (for reference)
     *
     * @return List of customers
     */
    public List<CustomerDTO> extractCustomers() {
        log.info("Extracting customer reference data...");

        List<Customer> customers = customerRepository.findAll();
        List<CustomerDTO> customerDTOs = new ArrayList<>();

        for (Customer customer : customers) {
            CustomerDTO dto = new CustomerDTO();
            dto.setCustomerId(customer.getCustomerId());
            dto.setName(customer.getName());
            dto.setEmail(customer.getEmail());
            dto.setPhone(customer.getPhone());
            customerDTOs.add(dto);
        }

        log.info("Extracted {} customer records", customerDTOs.size());
        return customerDTOs;
    }
}

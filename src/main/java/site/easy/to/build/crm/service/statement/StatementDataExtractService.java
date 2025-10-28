package site.easy.to.build.crm.service.statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.dto.statement.CustomerDTO;
import site.easy.to.build.crm.dto.statement.LeaseMasterDTO;
import site.easy.to.build.crm.dto.statement.PropertyDTO;
import site.easy.to.build.crm.dto.statement.TransactionDTO;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.HistoricalTransaction;
import site.easy.to.build.crm.entity.Invoice;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.repository.CustomerRepository;
import site.easy.to.build.crm.repository.HistoricalTransactionRepository;
import site.easy.to.build.crm.repository.InvoiceRepository;
import site.easy.to.build.crm.repository.PropertyRepository;

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
    private PropertyRepository propertyRepository;

    @Autowired
    private CustomerRepository customerRepository;

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
        log.info("Extracting lease master data for customer {}...", customerId);

        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null) {
            log.warn("Customer {} not found", customerId);
            return new ArrayList<>();
        }

        // Get all leases for this customer
        List<Invoice> invoices = invoiceRepository.findByCustomer(customer).stream()
            .filter(i -> i.getLeaseReference() != null && !i.getLeaseReference().trim().isEmpty())
            .collect(Collectors.toList());

        log.info("Found {} leases for customer {}", invoices.size(), customerId);

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
        log.info("Extracting transactions from {} to {}...", startDate, endDate);

        // Get all transactions with invoice_id populated (100% of rent payments)
        List<HistoricalTransaction> transactions;

        if (startDate != null && endDate != null) {
            // Filter by date range
            transactions = historicalTransactionRepository.findAll().stream()
                .filter(t -> t.getInvoice() != null)
                .filter(t -> !t.getTransactionDate().isBefore(startDate))
                .filter(t -> !t.getTransactionDate().isAfter(endDate))
                .collect(Collectors.toList());
        } else {
            // All transactions with invoice_id
            transactions = historicalTransactionRepository.findAll().stream()
                .filter(t -> t.getInvoice() != null)
                .collect(Collectors.toList());
        }

        log.info("Found {} transactions with invoice_id", transactions.size());

        List<TransactionDTO> transactionDTOs = new ArrayList<>();

        for (HistoricalTransaction ht : transactions) {
            TransactionDTO dto = new TransactionDTO();

            dto.setTransactionId(ht.getId());
            dto.setTransactionDate(ht.getTransactionDate());
            dto.setInvoiceId(ht.getInvoice() != null ? ht.getInvoice().getId() : null);
            dto.setPropertyId(ht.getProperty() != null ? ht.getProperty().getId() : null);
            dto.setCustomerId(ht.getCustomer() != null ? ht.getCustomer().getCustomerId() : null);
            dto.setCategory(ht.getCategory());
            dto.setTransactionType(ht.getTransactionType() != null ? ht.getTransactionType().name() : null);
            dto.setAmount(ht.getAmount());
            dto.setDescription(ht.getDescription());
            dto.setLeaseStartDate(ht.getLeaseStartDate());
            dto.setLeaseEndDate(ht.getLeaseEndDate());
            dto.setRentAmountAtTransaction(ht.getRentAmountAtTransaction());

            transactionDTOs.add(dto);
        }

        log.info("Extracted {} transaction records", transactionDTOs.size());
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
        log.info("Extracting transactions for customer {} from {} to {}...", customerId, startDate, endDate);

        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null) {
            log.warn("Customer {} not found", customerId);
            return new ArrayList<>();
        }

        // Get transactions based on customer type
        List<HistoricalTransaction> transactions;

        if (customer.getCustomerType() == site.easy.to.build.crm.entity.CustomerType.PROPERTY_OWNER) {
            // For property owners: Get transactions for properties they OWN
            log.info("Customer {} is PROPERTY_OWNER, filtering by owned properties", customerId);

            // Get all invoices (leases) for this customer's properties
            List<Invoice> ownerInvoices = invoiceRepository.findAll().stream()
                .filter(inv -> inv.getProperty() != null)
                .filter(inv -> inv.getProperty().getOwners() != null)
                .filter(inv -> inv.getProperty().getOwners().stream()
                    .anyMatch(owner -> owner.getCustomerId().equals(customerId)))
                .collect(Collectors.toList());

            log.info("Found {} invoices for properties owned by customer {}", ownerInvoices.size(), customerId);

            // Get transactions for those invoices
            transactions = historicalTransactionRepository.findAll().stream()
                .filter(t -> t.getInvoice() != null)
                .filter(t -> ownerInvoices.stream().anyMatch(inv -> inv.getId().equals(t.getInvoice().getId())))
                .filter(t -> startDate == null || !t.getTransactionDate().isBefore(startDate))
                .filter(t -> endDate == null || !t.getTransactionDate().isAfter(endDate))
                .collect(Collectors.toList());
        } else {
            // For tenants: Get transactions where they are the customer
            log.info("Customer {} is TENANT, filtering by customer_id", customerId);
            transactions = historicalTransactionRepository.findByCustomer(customer).stream()
                .filter(t -> t.getInvoice() != null)
                .filter(t -> startDate == null || !t.getTransactionDate().isBefore(startDate))
                .filter(t -> endDate == null || !t.getTransactionDate().isAfter(endDate))
                .collect(Collectors.toList());
        }

        log.info("Found {} transactions for customer {}", transactions.size(), customerId);

        List<TransactionDTO> transactionDTOs = new ArrayList<>();

        for (HistoricalTransaction ht : transactions) {
            TransactionDTO dto = new TransactionDTO();

            dto.setTransactionId(ht.getId());
            dto.setTransactionDate(ht.getTransactionDate());
            dto.setInvoiceId(ht.getInvoice() != null ? ht.getInvoice().getId() : null);
            dto.setPropertyId(ht.getProperty() != null ? ht.getProperty().getId() : null);
            dto.setCustomerId(ht.getCustomer() != null ? ht.getCustomer().getCustomerId() : null);
            dto.setCategory(ht.getCategory());
            dto.setTransactionType(ht.getTransactionType() != null ? ht.getTransactionType().name() : null);
            dto.setAmount(ht.getAmount());
            dto.setDescription(ht.getDescription());
            dto.setLeaseStartDate(ht.getLeaseStartDate());
            dto.setLeaseEndDate(ht.getLeaseEndDate());
            dto.setRentAmountAtTransaction(ht.getRentAmountAtTransaction());

            transactionDTOs.add(dto);
        }

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

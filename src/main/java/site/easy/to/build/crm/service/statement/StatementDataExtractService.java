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
import site.easy.to.build.crm.entity.LettingInstruction;
import site.easy.to.build.crm.entity.PayPropTenantComplete;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.UnifiedTransaction;
import site.easy.to.build.crm.repository.CustomerPropertyAssignmentRepository;
import site.easy.to.build.crm.repository.CustomerRepository;
import site.easy.to.build.crm.repository.HistoricalTransactionRepository;
import site.easy.to.build.crm.repository.InvoiceRepository;
import site.easy.to.build.crm.repository.PayPropTenantCompleteRepository;
import site.easy.to.build.crm.repository.PropertyRepository;
import site.easy.to.build.crm.repository.UnifiedTransactionRepository;
import site.easy.to.build.crm.service.property.PropertyService;

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

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private PayPropTenantCompleteRepository payPropTenantCompleteRepository;

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
            dto.setPaymentDay(invoice.getPaymentDay());

            // Property details
            Property property = invoice.getProperty();
            if (property != null) {
                dto.setPropertyId(property.getId());
                dto.setPropertyName(property.getPropertyName());
                dto.setPropertyAddress(property.getFullAddress());
                // Commission rates from property (not global defaults)
                dto.setCommissionPercentage(property.getCommissionPercentage());
                dto.setServiceFeePercentage(property.getServiceFeePercentage());
            }

            // Customer details (property owner)
            Customer customer = invoice.getCustomer();
            if (customer != null) {
                dto.setCustomerId(customer.getCustomerId());
                dto.setCustomerName(customer.getName());
            }

            // Tenant details (occupant)
            String tenantName = extractTenantName(invoice);
            dto.setTenantName(tenantName);

            log.info("✅ Lease {} - Tenant Name: '{}' (Customer: {})",
                invoice.getLeaseReference(), tenantName, dto.getCustomerName());

            leaseMaster.add(dto);
        }

        log.info("Extracted {} lease master records", leaseMaster.size());
        return leaseMaster;
    }

    /**
     * Extract lease master data for specific customer
     *
     * FIXED: Now uses PropertyService.findPropertiesAccessibleByCustomer() to properly handle
     * DELEGATED_USER with manages_owner_id inheritance (same pattern as dashboard, statements page, etc.)
     *
     * @param customerId Customer ID to filter by
     * @return List of lease master records for this customer
     */
    public List<LeaseMasterDTO> extractLeaseMasterForCustomer(Long customerId) {
        log.info("🔍 Extracting lease master data for customer {}...", customerId);

        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null) {
            log.error("❌ Customer {} not found - returning empty list", customerId);
            return new ArrayList<>();
        }

        log.info("✅ Customer {} found, type={}", customerId, customer.getCustomerType());

        // Get leases based on customer type
        List<Invoice> invoices;

        if (customer.getCustomerType() == site.easy.to.build.crm.entity.CustomerType.PROPERTY_OWNER ||
            customer.getCustomerType() == site.easy.to.build.crm.entity.CustomerType.DELEGATED_USER) {

            // ✅ USE THE ESTABLISHED PATTERN - Same as dashboard, property listings, etc.
            // This automatically handles:
            // - DELEGATED_USER with manages_owner_id → redirects to owner's properties
            // - PROPERTY_OWNER → returns their own properties
            List<Property> properties = propertyService.findPropertiesAccessibleByCustomer(customerId);

            log.info("✅ Found {} properties accessible by customer {} using PropertyService filter",
                properties.size(), customerId);

            // Get property IDs for filtering invoices
            final List<Long> propertyIds = properties.stream()
                .map(Property::getId)
                .collect(Collectors.toList());

            log.info("📋 Property IDs: {}", propertyIds);

            // Get leases (invoices) for these properties
            invoices = invoiceRepository.findAll().stream()
                .filter(i -> i.getProperty() != null)
                .filter(i -> propertyIds.contains(i.getProperty().getId()))
                .filter(i -> i.getLeaseReference() != null && !i.getLeaseReference().trim().isEmpty())
                .collect(Collectors.toList());

            log.info("✅ Found {} leases for customer {}'s properties", invoices.size(), customerId);
        } else {
            // For tenants: Get leases where they are the customer (tenant on the lease)
            log.info("📋 Customer {} is TENANT, getting leases where they are the customer", customerId);
            invoices = invoiceRepository.findByCustomer(customer).stream()
                .filter(i -> i.getLeaseReference() != null && !i.getLeaseReference().trim().isEmpty())
                .collect(Collectors.toList());
        }

        log.info("📊 Total {} leases found for customer {}", invoices.size(), customerId);

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
            dto.setPaymentDay(invoice.getPaymentDay());

            // Property details
            Property property = invoice.getProperty();
            if (property != null) {
                dto.setPropertyId(property.getId());
                dto.setPropertyName(property.getPropertyName());
                dto.setPropertyAddress(property.getFullAddress());
                // Commission rates from property (not global defaults)
                dto.setCommissionPercentage(property.getCommissionPercentage());
                dto.setServiceFeePercentage(property.getServiceFeePercentage());
            }

            // Customer details (always same customer in this method)
            dto.setCustomerId(customer.getCustomerId());
            dto.setCustomerName(customer.getName());

            // Tenant details (occupant)
            String tenantName = extractTenantName(invoice);
            dto.setTenantName(tenantName);

            log.info("✅ Lease {} - Tenant Name: '{}' (Customer: {})",
                invoice.getLeaseReference(), tenantName, dto.getCustomerName());

            leaseMaster.add(dto);
        }

        log.info("✅ Extracted {} lease master records for customer {}", leaseMaster.size(), customerId);

        if (!leaseMaster.isEmpty()) {
            log.info("📋 Sample leases: {}",
                leaseMaster.stream()
                    .limit(3)
                    .map(l -> "lease_id=" + l.getLeaseId() + " ref=" + l.getLeaseReference() + " property=" + l.getPropertyName())
                    .collect(Collectors.toList()));
        }

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
            dto.setTransactionType(ut.getTransactionType());
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
            dto.setTransactionType(ut.getTransactionType());
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
     * Extract INCOMING transactions only (rent received from tenants)
     *
     * This filters out OUTGOING transactions (landlord payments, fees, expenses)
     * Use this for accurate "Rent Received" calculations in statements
     *
     * @param startDate Start date filter (inclusive)
     * @param endDate End date filter (inclusive)
     * @return List of INCOMING transactions only
     */
    public List<TransactionDTO> extractRentReceived(LocalDate startDate, LocalDate endDate) {
        log.info("✨ Extracting INCOMING transactions (rent received) from {} to {}...", startDate, endDate);

        // Get only INCOMING transactions using flow_direction filter
        List<UnifiedTransaction> transactions = unifiedTransactionRepository
            .findByTransactionDateBetweenAndFlowDirection(
                startDate,
                endDate,
                UnifiedTransaction.FlowDirection.INCOMING
            );

        log.info("✨ Found {} INCOMING transactions (rent from tenants)", transactions.size());

        List<TransactionDTO> transactionDTOs = new ArrayList<>();

        for (UnifiedTransaction ut : transactions) {
            TransactionDTO dto = new TransactionDTO();

            dto.setTransactionId(ut.getId());
            dto.setTransactionDate(ut.getTransactionDate());
            dto.setInvoiceId(ut.getInvoiceId());
            dto.setPropertyId(ut.getPropertyId());
            dto.setPropertyName(ut.getPropertyName());
            dto.setCustomerId(ut.getCustomerId());
            dto.setCategory(ut.getCategory());
            dto.setTransactionType(ut.getTransactionType());
            dto.setAmount(ut.getAmount());
            dto.setDescription(ut.getDescription());
            dto.setLeaseStartDate(ut.getLeaseStartDate());
            dto.setLeaseEndDate(ut.getLeaseEndDate());
            dto.setRentAmountAtTransaction(ut.getRentAmountAtTransaction());

            transactionDTOs.add(dto);
        }

        log.info("✨ Extracted {} INCOMING transaction records", transactionDTOs.size());
        return transactionDTOs;
    }

    /**
     * Extract INCOMING transactions only (rent received) for specific customer
     *
     * @param customerId Customer ID to filter by
     * @param startDate Start date filter
     * @param endDate End date filter
     * @return List of INCOMING transactions for this customer
     */
    public List<TransactionDTO> extractRentReceivedForCustomer(Long customerId, LocalDate startDate, LocalDate endDate) {
        log.info("✨ Extracting INCOMING transactions (rent received) for customer {} from {} to {}...",
            customerId, startDate, endDate);

        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null) {
            log.error("Customer {} not found - returning empty list", customerId);
            return new ArrayList<>();
        }

        // Use the new flow_direction filtering query
        List<UnifiedTransaction> transactions = unifiedTransactionRepository
            .findByCustomerOwnedPropertiesAndDateRangeAndFlowDirection(
                customerId,
                startDate,
                endDate,
                UnifiedTransaction.FlowDirection.INCOMING
            );

        log.info("✨ Found {} INCOMING transactions (rent received) for customer {}",
            transactions.size(), customerId);

        List<TransactionDTO> transactionDTOs = new ArrayList<>();

        for (UnifiedTransaction ut : transactions) {
            TransactionDTO dto = new TransactionDTO();

            dto.setTransactionId(ut.getId());
            dto.setTransactionDate(ut.getTransactionDate());
            dto.setInvoiceId(ut.getInvoiceId());
            dto.setPropertyId(ut.getPropertyId());
            dto.setPropertyName(ut.getPropertyName());
            dto.setCustomerId(ut.getCustomerId());
            dto.setCategory(ut.getCategory());
            dto.setTransactionType(ut.getTransactionType());
            dto.setAmount(ut.getAmount());
            dto.setDescription(ut.getDescription());
            dto.setLeaseStartDate(ut.getLeaseStartDate());
            dto.setLeaseEndDate(ut.getLeaseEndDate());
            dto.setRentAmountAtTransaction(ut.getRentAmountAtTransaction());

            transactionDTOs.add(dto);
        }

        log.info("✨ Extracted {} INCOMING transaction records for customer {}", transactionDTOs.size(), customerId);
        return transactionDTOs;
    }

    /**
     * Extract OUTGOING transactions only (landlord payments, fees, expenses)
     *
     * @param customerId Customer ID to filter by
     * @param startDate Start date filter
     * @param endDate End date filter
     * @return List of OUTGOING transactions for this customer
     */
    public List<TransactionDTO> extractOutgoingForCustomer(Long customerId, LocalDate startDate, LocalDate endDate) {
        log.info("✨ Extracting OUTGOING transactions (payments, fees, expenses) for customer {} from {} to {}...",
            customerId, startDate, endDate);

        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null) {
            log.error("Customer {} not found - returning empty list", customerId);
            return new ArrayList<>();
        }

        // Use the new flow_direction filtering query
        List<UnifiedTransaction> transactions = unifiedTransactionRepository
            .findByCustomerOwnedPropertiesAndDateRangeAndFlowDirection(
                customerId,
                startDate,
                endDate,
                UnifiedTransaction.FlowDirection.OUTGOING
            );

        log.info("✨ Found {} OUTGOING transactions for customer {}", transactions.size(), customerId);

        List<TransactionDTO> transactionDTOs = new ArrayList<>();

        for (UnifiedTransaction ut : transactions) {
            TransactionDTO dto = new TransactionDTO();

            dto.setTransactionId(ut.getId());
            dto.setTransactionDate(ut.getTransactionDate());
            dto.setInvoiceId(ut.getInvoiceId());
            dto.setPropertyId(ut.getPropertyId());
            dto.setPropertyName(ut.getPropertyName());
            dto.setCustomerId(ut.getCustomerId());
            dto.setCategory(ut.getCategory());
            dto.setTransactionType(ut.getTransactionType());
            dto.setAmount(ut.getAmount());
            dto.setDescription(ut.getDescription());
            dto.setLeaseStartDate(ut.getLeaseStartDate());
            dto.setLeaseEndDate(ut.getLeaseEndDate());
            dto.setRentAmountAtTransaction(ut.getRentAmountAtTransaction());

            transactionDTOs.add(dto);
        }

        log.info("✨ Extracted {} OUTGOING transaction records for customer {}", transactionDTOs.size(), customerId);
        return transactionDTOs;
    }

    /**
     * Extract individual payment details for a specific lease and date range
     * Returns list of actual payments (date + amount) for breakdown display
     *
     * @param invoiceId Lease/invoice ID
     * @param startDate Start date filter
     * @param endDate End date filter
     * @param flowDirection INCOMING for rent received, OUTGOING for payments/expenses
     * @return List of payment details sorted by date
     */
    public List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO> extractPaymentDetails(
            Long invoiceId,
            LocalDate startDate,
            LocalDate endDate,
            UnifiedTransaction.FlowDirection flowDirection) {

        log.info("Extracting payment details for lease {} from {} to {} ({})",
            invoiceId, startDate, endDate, flowDirection);

        List<UnifiedTransaction> transactions = unifiedTransactionRepository
            .findByInvoiceIdAndTransactionDateBetweenAndFlowDirection(
                invoiceId, startDate, endDate, flowDirection);

        List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO> paymentDetails = new ArrayList<>();

        for (UnifiedTransaction txn : transactions) {
            site.easy.to.build.crm.dto.statement.PaymentDetailDTO detail =
                new site.easy.to.build.crm.dto.statement.PaymentDetailDTO();

            detail.setPaymentDate(txn.getTransactionDate());
            detail.setAmount(txn.getAmount());
            detail.setDescription(txn.getDescription());
            detail.setCategory(txn.getCategory());
            detail.setTransactionId(txn.getId());

            paymentDetails.add(detail);
        }

        // Sort by date ascending
        paymentDetails.sort((a, b) -> a.getPaymentDate().compareTo(b.getPaymentDate()));

        log.info("Extracted {} payment details for lease {}", paymentDetails.size(), invoiceId);
        return paymentDetails;
    }

    /**
     * Extract INCOMING payment details (rent received) for a lease
     */
    public List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO> extractRentReceivedDetails(
            Long invoiceId, LocalDate startDate, LocalDate endDate) {
        return extractPaymentDetails(invoiceId, startDate, endDate, UnifiedTransaction.FlowDirection.INCOMING);
    }

    /**
     * Extract OUTGOING payment details (landlord payments, fees, expenses) for a lease
     */
    public List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO> extractOutgoingDetails(
            Long invoiceId, LocalDate startDate, LocalDate endDate) {
        return extractPaymentDetails(invoiceId, startDate, endDate, UnifiedTransaction.FlowDirection.OUTGOING);
    }

    /**
     * Extract expense details for a specific lease and date range
     * Returns only OUTGOING transactions with transaction_type = 'expense'
     *
     * @param invoiceId Lease/invoice ID
     * @param startDate Start date filter
     * @param endDate End date filter
     * @return List of expense details sorted by date
     */
    public List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO> extractExpenseDetails(
            Long invoiceId, LocalDate startDate, LocalDate endDate) {

        log.info("Extracting expense details for lease {} from {} to {}",
            invoiceId, startDate, endDate);

        List<UnifiedTransaction> transactions = unifiedTransactionRepository
            .findByInvoiceIdAndTransactionDateBetweenAndFlowDirectionAndTransactionType(
                invoiceId, startDate, endDate,
                UnifiedTransaction.FlowDirection.OUTGOING,
                "expense");

        List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO> expenseDetails = new ArrayList<>();

        for (UnifiedTransaction txn : transactions) {
            site.easy.to.build.crm.dto.statement.PaymentDetailDTO detail =
                new site.easy.to.build.crm.dto.statement.PaymentDetailDTO();

            detail.setPaymentDate(txn.getTransactionDate());
            detail.setAmount(txn.getAmount());
            detail.setDescription(txn.getDescription());
            detail.setCategory(txn.getCategory());
            detail.setTransactionId(txn.getId());

            expenseDetails.add(detail);
        }

        // Sort by date ascending
        expenseDetails.sort((a, b) -> a.getPaymentDate().compareTo(b.getPaymentDate()));

        log.info("Extracted {} expense details for lease {}", expenseDetails.size(), invoiceId);
        return expenseDetails;
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

    /**
     * Extract tenant name from invoice/lease
     * Tries multiple sources:
     * 1. Letting instruction tenant
     * 2. PayProp tenant data via property's payprop_id (from payprop_export_tenants_complete)
     * 3. Most recent financial transaction tenant_name (fallback for INCOMING_PAYMENT descriptions)
     *
     * @param invoice The lease/invoice
     * @return Tenant name or empty string if not found
     */
    private String extractTenantName(Invoice invoice) {
        log.info("🔍 Extracting tenant name for invoice/lease ID: {} ({})",
            invoice.getId(), invoice.getLeaseReference());

        // Try 1: Get tenant from letting instruction
        if (invoice.getLettingInstruction() != null) {
            LettingInstruction instruction = invoice.getLettingInstruction();
            if (instruction.getTenant() != null) {
                Customer tenant = instruction.getTenant();
                String name = tenant.getName();
                if (name != null && !name.trim().isEmpty()) {
                    log.info("✓ Found tenant name from letting instruction: {}", name);
                    return name.trim();
                }
            }
        }

        // Try 2: Get tenant from PayProp tenant data via property's payprop_id
        // This searches the payprop_export_tenants_complete table where properties_json contains the property ID
        // IMPORTANT: For properties with multiple leases, we match tenant based on overlapping dates
        Property property = invoice.getProperty();
        if (property != null && property.getPayPropId() != null && !property.getPayPropId().trim().isEmpty()) {
            String propertyPayPropId = property.getPayPropId();
            LocalDate leaseStartDate = invoice.getStartDate();
            LocalDate leaseEndDate = invoice.getEndDate();

            log.info("🔍 Looking up PayProp tenant for property payprop_id: {} (lease: {} to {})",
                propertyPayPropId, leaseStartDate, leaseEndDate);

            try {
                // Find tenants linked to this property via properties_json
                List<PayPropTenantComplete> payPropTenants = payPropTenantCompleteRepository
                    .findByPropertiesJsonContainingPropertyId(propertyPayPropId);

                if (!payPropTenants.isEmpty()) {
                    log.info("📊 Found {} PayProp tenant(s) for property {}", payPropTenants.size(), propertyPayPropId);

                    // Find the tenant whose tenancy dates overlap with this lease's dates
                    PayPropTenantComplete matchedTenant = findTenantMatchingLeaseDates(
                        payPropTenants, propertyPayPropId, leaseStartDate, leaseEndDate);

                    if (matchedTenant != null) {
                        String tenantName = matchedTenant.getDisplayName();
                        if (tenantName != null && !tenantName.trim().isEmpty()) {
                            log.info("✓ Found tenant name from PayProp tenant data: {} (payprop_id: {})",
                                tenantName, matchedTenant.getPayPropId());
                            return tenantName.trim();
                        }
                    } else {
                        log.info("📊 No matching tenant found for lease dates {} to {}", leaseStartDate, leaseEndDate);
                    }
                } else {
                    log.info("📊 No PayProp tenants found for property payprop_id: {}", propertyPayPropId);
                }
            } catch (Exception e) {
                log.warn("⚠️ Error looking up PayProp tenant for property {}: {}", propertyPayPropId, e.getMessage());
            }
        }

        // Try 3: Get tenant name from most recent unified transaction (fallback)
        List<UnifiedTransaction> transactions = unifiedTransactionRepository
            .findByInvoiceId(invoice.getId());

        log.info("📊 Found {} unified transactions for invoice ID {}", transactions.size(), invoice.getId());

        if (!transactions.isEmpty()) {
            // Sort by date descending to get most recent
            transactions.sort((a, b) -> b.getTransactionDate().compareTo(a.getTransactionDate()));

            // Look for tenant name in description (INCOMING_PAYMENT format: "Tenant Payment - [Name] - ...")
            int checked = 0;
            for (UnifiedTransaction txn : transactions) {
                if (txn.getDescription() != null) {
                    if (checked < 3) { // Only log first 3 to avoid spam
                        log.info("🔍 Transaction {}: {}", txn.getId(),
                            txn.getDescription().substring(0, Math.min(100, txn.getDescription().length())));
                    }
                    checked++;

                    if (txn.getDescription().contains("Tenant Payment -")) {
                        String desc = txn.getDescription();
                        // Extract name between "Tenant Payment - " and next " - "
                        int start = desc.indexOf("Tenant Payment - ") + 17;
                        int end = desc.indexOf(" - ", start);
                        if (end > start) {
                            String tenantName = desc.substring(start, end).trim();
                            log.info("✓ Extracted tenant name from transaction: '{}'", tenantName);
                            return tenantName;
                        }
                    }
                }
            }
        }

        log.warn("⚠️ No tenant name found for invoice/lease ID: {} ({})",
            invoice.getId(), invoice.getLeaseReference());
        return ""; // No tenant name found
    }

    /**
     * Find the tenant whose tenancy dates overlap with the lease dates
     * Parses the properties_json to get tenant-specific start/end dates for the property
     *
     * @param tenants List of tenants linked to the property
     * @param propertyPayPropId The PayProp ID of the property
     * @param leaseStartDate The lease start date
     * @param leaseEndDate The lease end date (can be null for ongoing leases)
     * @return The matching tenant or null if no match found
     */
    private PayPropTenantComplete findTenantMatchingLeaseDates(
            List<PayPropTenantComplete> tenants,
            String propertyPayPropId,
            LocalDate leaseStartDate,
            LocalDate leaseEndDate) {

        if (tenants.size() == 1) {
            // Only one tenant, return it directly
            return tenants.get(0);
        }

        PayPropTenantComplete bestMatch = null;
        int bestOverlapDays = -1;

        for (PayPropTenantComplete tenant : tenants) {
            try {
                // Parse the properties_json to find tenant dates for this specific property
                String propertiesJson = tenant.getPropertiesJson();
                if (propertiesJson == null || propertiesJson.isEmpty()) {
                    continue;
                }

                // Extract tenant dates from the JSON for this property
                // Format: [{"id": "propertyId", "tenant": {"start_date": "2025-06-17", "end_date": "2025-08-28"}, ...}]
                LocalDate tenantStartDate = extractTenantStartDateFromJson(propertiesJson, propertyPayPropId);
                LocalDate tenantEndDate = extractTenantEndDateFromJson(propertiesJson, propertyPayPropId);

                if (tenantStartDate == null) {
                    // Fall back to entity-level dates if JSON parsing fails
                    tenantStartDate = tenant.getTenancyStartDate();
                    tenantEndDate = tenant.getTenancyEndDate();
                }

                log.info("🔍 Checking tenant {} ({}) - tenancy: {} to {} vs lease: {} to {}",
                    tenant.getDisplayName(), tenant.getPayPropId(),
                    tenantStartDate, tenantEndDate, leaseStartDate, leaseEndDate);

                // Check if tenant dates overlap with lease dates
                if (datesOverlap(leaseStartDate, leaseEndDate, tenantStartDate, tenantEndDate)) {
                    int overlapDays = calculateOverlapDays(leaseStartDate, leaseEndDate, tenantStartDate, tenantEndDate);
                    log.info("✓ Tenant {} overlaps with lease ({} days)", tenant.getDisplayName(), overlapDays);

                    if (overlapDays > bestOverlapDays) {
                        bestOverlapDays = overlapDays;
                        bestMatch = tenant;
                    }
                }
            } catch (Exception e) {
                log.warn("⚠️ Error parsing tenant dates for {}: {}", tenant.getPayPropId(), e.getMessage());
            }
        }

        return bestMatch;
    }

    /**
     * Check if two date ranges overlap
     */
    private boolean datesOverlap(LocalDate start1, LocalDate end1, LocalDate start2, LocalDate end2) {
        // Handle null end dates (ongoing)
        LocalDate effectiveEnd1 = end1 != null ? end1 : LocalDate.of(2099, 12, 31);
        LocalDate effectiveEnd2 = end2 != null ? end2 : LocalDate.of(2099, 12, 31);

        // Ranges overlap if start1 <= end2 AND start2 <= end1
        return !start1.isAfter(effectiveEnd2) && !start2.isAfter(effectiveEnd1);
    }

    /**
     * Calculate overlap days between two date ranges
     */
    private int calculateOverlapDays(LocalDate start1, LocalDate end1, LocalDate start2, LocalDate end2) {
        LocalDate effectiveEnd1 = end1 != null ? end1 : LocalDate.now();
        LocalDate effectiveEnd2 = end2 != null ? end2 : LocalDate.now();

        LocalDate overlapStart = start1.isAfter(start2) ? start1 : start2;
        LocalDate overlapEnd = effectiveEnd1.isBefore(effectiveEnd2) ? effectiveEnd1 : effectiveEnd2;

        if (overlapStart.isAfter(overlapEnd)) {
            return 0;
        }

        return (int) java.time.temporal.ChronoUnit.DAYS.between(overlapStart, overlapEnd);
    }

    /**
     * Extract tenant start_date from properties_json for a specific property
     * JSON format: [{"id": "propertyId", "tenant": {"start_date": "2025-06-17", ...}, ...}]
     */
    private LocalDate extractTenantStartDateFromJson(String json, String propertyId) {
        try {
            // Find the property object in the JSON array
            int propIndex = json.indexOf("\"id\": \"" + propertyId + "\"");
            if (propIndex == -1) {
                propIndex = json.indexOf("\"id\":\"" + propertyId + "\"");
            }
            if (propIndex == -1) {
                return null;
            }

            // Find start_date after this property
            int startDateIndex = json.indexOf("\"start_date\":", propIndex);
            if (startDateIndex == -1 || startDateIndex > propIndex + 500) { // Limit search scope
                return null;
            }

            // Extract the date value
            int valueStart = json.indexOf("\"", startDateIndex + 13) + 1;
            int valueEnd = json.indexOf("\"", valueStart);
            if (valueStart > 0 && valueEnd > valueStart) {
                String dateStr = json.substring(valueStart, valueEnd);
                if (dateStr != null && !dateStr.isEmpty() && !dateStr.equals("null")) {
                    return LocalDate.parse(dateStr);
                }
            }
        } catch (Exception e) {
            log.debug("Could not parse start_date from JSON: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Extract tenant end_date from properties_json for a specific property
     */
    private LocalDate extractTenantEndDateFromJson(String json, String propertyId) {
        try {
            // Find the property object in the JSON array
            int propIndex = json.indexOf("\"id\": \"" + propertyId + "\"");
            if (propIndex == -1) {
                propIndex = json.indexOf("\"id\":\"" + propertyId + "\"");
            }
            if (propIndex == -1) {
                return null;
            }

            // Find end_date after this property
            int endDateIndex = json.indexOf("\"end_date\":", propIndex);
            if (endDateIndex == -1 || endDateIndex > propIndex + 500) { // Limit search scope
                return null;
            }

            // Check for null value
            int nullCheck = json.indexOf("null", endDateIndex + 11);
            if (nullCheck == endDateIndex + 12 || nullCheck == endDateIndex + 13) {
                return null; // end_date is null (ongoing tenancy)
            }

            // Extract the date value
            int valueStart = json.indexOf("\"", endDateIndex + 11) + 1;
            int valueEnd = json.indexOf("\"", valueStart);
            if (valueStart > 0 && valueEnd > valueStart) {
                String dateStr = json.substring(valueStart, valueEnd);
                if (dateStr != null && !dateStr.isEmpty() && !dateStr.equals("null")) {
                    return LocalDate.parse(dateStr);
                }
            }
        } catch (Exception e) {
            log.debug("Could not parse end_date from JSON: {}", e.getMessage());
        }
        return null;
    }
}

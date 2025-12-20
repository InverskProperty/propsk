package site.easy.to.build.crm.service.statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.dto.statement.CustomerDTO;
import site.easy.to.build.crm.dto.statement.LeaseAllocationSummaryDTO;
import site.easy.to.build.crm.dto.statement.LeaseMasterDTO;
import site.easy.to.build.crm.dto.statement.PaymentBatchSummaryDTO;
import site.easy.to.build.crm.dto.statement.PaymentWithAllocationsDTO;
import site.easy.to.build.crm.dto.statement.PropertyDTO;
import site.easy.to.build.crm.dto.statement.RelatedPaymentDTO;
import site.easy.to.build.crm.dto.statement.TransactionDTO;
import site.easy.to.build.crm.dto.statement.UnallocatedIncomeDTO;
import site.easy.to.build.crm.dto.statement.UnallocatedPaymentDTO;
import site.easy.to.build.crm.entity.AssignmentType;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.HistoricalTransaction;
import site.easy.to.build.crm.entity.Invoice;
import site.easy.to.build.crm.entity.LettingInstruction;
import site.easy.to.build.crm.entity.PaymentBatch;
import site.easy.to.build.crm.entity.PayPropTenantComplete;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.UnifiedAllocation;
import site.easy.to.build.crm.entity.UnifiedTransaction;
import site.easy.to.build.crm.repository.CustomerPropertyAssignmentRepository;
import site.easy.to.build.crm.repository.CustomerRepository;
import site.easy.to.build.crm.repository.HistoricalTransactionRepository;
import site.easy.to.build.crm.repository.InvoiceRepository;
import site.easy.to.build.crm.repository.PaymentBatchRepository;
import site.easy.to.build.crm.repository.UnifiedAllocationRepository;
import site.easy.to.build.crm.repository.PayPropTenantCompleteRepository;
import site.easy.to.build.crm.repository.PropertyRepository;
import site.easy.to.build.crm.repository.UnifiedTransactionRepository;
import site.easy.to.build.crm.service.property.PropertyService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Log current memory usage for debugging
     * Search keyword: [STMT-DEBUG] for easy log filtering
     */
    private void logMemoryUsage(String phase) {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        int usedPercent = (int) ((usedMemory * 100) / maxMemory);
        String status = usedPercent > 90 ? "🔴 CRITICAL" : usedPercent > 70 ? "🟡 WARNING" : "🟢 OK";
        log.info("[STMT-DEBUG] {} [{}] Memory: {}MB/{}MB ({}%)",
            status, phase, usedMemory / (1024 * 1024), maxMemory / (1024 * 1024), usedPercent);
    }

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

    @Autowired
    private UnifiedAllocationRepository unifiedAllocationRepository;

    @Autowired
    private PaymentBatchRepository paymentBatchRepository;

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
            dto.setFrequencyMonths(invoice.getFrequencyMonths());  // Numeric cycle length for rent_due calculations
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

                // Block property support
                dto.setIsBlockProperty(property.getIsBlockProperty());
                dto.setPropertyAccountBalance(property.getAccountBalance());
                if (property.getBlock() != null) {
                    dto.setBlockId(property.getBlock().getId());
                    dto.setBlockName(property.getBlock().getName());
                }
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
        log.info("[STMT-DEBUG] 🔍 START extractLeaseMasterForCustomer({})", customerId);
        logMemoryUsage("EXTRACT_LEASE_START");

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
            log.info("[STMT-DEBUG] Fetching properties for customer {}...", customerId);
            List<Property> properties = propertyService.findPropertiesAccessibleByCustomer(customerId);
            logMemoryUsage("AFTER_FETCH_PROPERTIES");

            log.info("[STMT-DEBUG] ✅ Found {} properties accessible by customer {}",
                properties.size(), customerId);

            // Get property IDs for filtering invoices
            final List<Long> propertyIds = properties.stream()
                .map(Property::getId)
                .collect(Collectors.toList());

            log.info("[STMT-DEBUG] 📋 Property IDs: {}", propertyIds);

            // Get leases (invoices) for these properties
            log.info("[STMT-DEBUG] Fetching all invoices from database...");
            List<Invoice> allInvoices = invoiceRepository.findAll();
            log.info("[STMT-DEBUG] Loaded {} total invoices from database", allInvoices.size());
            logMemoryUsage("AFTER_FETCH_ALL_INVOICES");

            invoices = allInvoices.stream()
                .filter(i -> i.getProperty() != null)
                .filter(i -> propertyIds.contains(i.getProperty().getId()))
                .filter(i -> i.getLeaseReference() != null && !i.getLeaseReference().trim().isEmpty())
                .collect(Collectors.toList());

            log.info("[STMT-DEBUG] ✅ Filtered to {} leases for customer {}'s properties", invoices.size(), customerId);
            logMemoryUsage("AFTER_FILTER_INVOICES");
        } else {
            // For tenants: Get leases where they are the customer (tenant on the lease)
            log.info("📋 Customer {} is TENANT, getting leases where they are the customer", customerId);
            invoices = invoiceRepository.findByCustomer(customer).stream()
                .filter(i -> i.getLeaseReference() != null && !i.getLeaseReference().trim().isEmpty())
                .collect(Collectors.toList());
        }

        log.info("[STMT-DEBUG] 📊 Total {} leases found for customer {}", invoices.size(), customerId);
        logMemoryUsage("BEFORE_DTO_CREATION");

        List<LeaseMasterDTO> leaseMaster = new ArrayList<>();
        int dtoCount = 0;

        for (Invoice invoice : invoices) {
            dtoCount++;
            LeaseMasterDTO dto = new LeaseMasterDTO();

            // Lease details
            dto.setLeaseId(invoice.getId());
            dto.setLeaseReference(invoice.getLeaseReference());
            dto.setStartDate(invoice.getStartDate());
            dto.setEndDate(invoice.getEndDate());
            dto.setMonthlyRent(invoice.getAmount());
            dto.setFrequency(invoice.getFrequency() != null ? invoice.getFrequency().name() : "MONTHLY");
            dto.setFrequencyMonths(invoice.getFrequencyMonths());  // Numeric cycle length for rent_due calculations
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

                // Block property support
                dto.setIsBlockProperty(property.getIsBlockProperty());
                dto.setPropertyAccountBalance(property.getAccountBalance());
                if (property.getBlock() != null) {
                    dto.setBlockId(property.getBlock().getId());
                    dto.setBlockName(property.getBlock().getName());
                }
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

            // Log memory every 10 DTOs
            if (dtoCount % 10 == 0) {
                logMemoryUsage("DTO_CREATION_" + dtoCount);
            }
        }

        log.info("[STMT-DEBUG] ✅ Extracted {} lease master records for customer {}", leaseMaster.size(), customerId);
        logMemoryUsage("EXTRACT_LEASE_COMPLETE");

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
            dto.setLeaseReference(ut.getLeaseReference());  // For accurate opening balance matching
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
            dto.setLeaseReference(ut.getLeaseReference());  // For accurate opening balance matching
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

        // Log invoice_id distribution for debugging
        log.debug("🔍 Transaction invoice_id distribution for customer {}: {}",
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
        log.info("[STMT-DEBUG] ✨ START extractRentReceivedForCustomer({}) from {} to {}",
            customerId, startDate, endDate);
        logMemoryUsage("EXTRACT_RENT_RECEIVED_START");

        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null) {
            log.error("Customer {} not found - returning empty list", customerId);
            return new ArrayList<>();
        }

        // Use the new flow_direction filtering query
        log.info("[STMT-DEBUG] Querying unified transactions...");
        List<UnifiedTransaction> transactions = unifiedTransactionRepository
            .findByCustomerOwnedPropertiesAndDateRangeAndFlowDirection(
                customerId,
                startDate,
                endDate,
                UnifiedTransaction.FlowDirection.INCOMING
            );
        logMemoryUsage("AFTER_QUERY_TRANSACTIONS");

        log.info("[STMT-DEBUG] ✨ Found {} INCOMING transactions for customer {}",
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

        log.info("[STMT-DEBUG] ✨ COMPLETE extractRentReceivedForCustomer - {} records", transactionDTOs.size());
        logMemoryUsage("EXTRACT_RENT_RECEIVED_COMPLETE");
        return transactionDTOs;
    }

    /**
     * Extract ALL INCOMING transactions (rent received) for specific customer - NO DATE FILTER.
     * Used for SXSSF streaming statements where opening balance is calculated via Excel formulas.
     *
     * @param customerId Customer ID to filter by
     * @return List of ALL INCOMING transactions for this customer (from earliest to present)
     */
    public List<TransactionDTO> extractAllRentReceivedForCustomer(Long customerId) {
        log.info("[STMT-DEBUG] ✨ START extractAllRentReceivedForCustomer({}) - NO DATE FILTER", customerId);
        logMemoryUsage("EXTRACT_ALL_RENT_RECEIVED_START");

        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null) {
            log.error("Customer {} not found - returning empty list", customerId);
            return new ArrayList<>();
        }

        // Use the new query WITHOUT date filtering
        log.info("[STMT-DEBUG] Querying ALL unified transactions (no date filter)...");
        List<UnifiedTransaction> transactions = unifiedTransactionRepository
            .findByCustomerOwnedPropertiesAndFlowDirection(
                customerId,
                UnifiedTransaction.FlowDirection.INCOMING
            );
        logMemoryUsage("AFTER_QUERY_ALL_TRANSACTIONS");

        log.info("[STMT-DEBUG] ✨ Found {} TOTAL INCOMING transactions for customer {}", transactions.size(), customerId);

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

        log.info("[STMT-DEBUG] ✨ COMPLETE extractAllRentReceivedForCustomer - {} records", transactionDTOs.size());
        logMemoryUsage("EXTRACT_ALL_RENT_RECEIVED_COMPLETE");
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

        // Populate batch info from unified_allocations
        populateBatchInfo(paymentDetails);

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

        // Get ALL outgoing transactions (expenses, beneficiary payments, etc.)
        // Not filtering by transaction_type to include payment_to_beneficiary, expense, etc.
        List<UnifiedTransaction> transactions = unifiedTransactionRepository
            .findByInvoiceIdAndTransactionDateBetweenAndFlowDirection(
                invoiceId, startDate, endDate,
                UnifiedTransaction.FlowDirection.OUTGOING);

        List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO> expenseDetails = new ArrayList<>();

        for (UnifiedTransaction txn : transactions) {
            // ✅ FIX: Filter out Owner and Commission categories - these are NOT expenses
            // Only include actual expenses: Council, Disbursement, Contractor, cleaning, furnishings, etc.
            String category = txn.getCategory();
            if (category != null) {
                String lowerCategory = category.toLowerCase();
                // Skip Owner payments (landlord payments) and Commission (agency fees)
                // Note: PayProp uses both "owner" and "owner_payment" for landlord payments
                if (lowerCategory.equals("owner") || lowerCategory.equals("commission") ||
                    lowerCategory.contains("owner_payment")) {
                    log.debug("Skipping {} transaction {} (not an expense)", category, txn.getId());
                    continue;
                }
            }

            site.easy.to.build.crm.dto.statement.PaymentDetailDTO detail =
                new site.easy.to.build.crm.dto.statement.PaymentDetailDTO();

            detail.setPaymentDate(txn.getTransactionDate());
            detail.setAmount(txn.getAmount());
            detail.setDescription(txn.getDescription());
            detail.setCategory(txn.getCategory());
            detail.setTransactionId(txn.getId());

            expenseDetails.add(detail);
        }

        // Populate batch info from unified_allocations
        populateBatchInfo(expenseDetails);

        // Sort by date ascending
        expenseDetails.sort((a, b) -> a.getPaymentDate().compareTo(b.getPaymentDate()));

        log.info("Extracted {} expense details for lease {}", expenseDetails.size(), invoiceId);
        return expenseDetails;
    }

    /**
     * Populate batch info (batchId, paymentStatus, paidDate) from unified_allocations
     * for a list of payment details. Updates the DTOs in place.
     */
    private void populateBatchInfo(List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO> paymentDetails) {
        if (paymentDetails == null || paymentDetails.isEmpty()) {
            return;
        }

        // Collect transaction IDs
        List<Long> transactionIds = paymentDetails.stream()
            .map(site.easy.to.build.crm.dto.statement.PaymentDetailDTO::getTransactionId)
            .filter(id -> id != null)
            .collect(java.util.stream.Collectors.toList());

        if (transactionIds.isEmpty()) {
            return;
        }

        try {
            // Batch query for all transaction batch info
            List<Object[]> batchInfoList = unifiedAllocationRepository.getBatchInfoForTransactions(transactionIds);

            // Build maps with batch ID concatenation for split payments
            // (a single incoming payment can be split across multiple batches)
            Map<Long, String> batchIdMap = new HashMap<>();
            Map<Long, String> statusMap = new HashMap<>();
            Map<Long, LocalDate> paidDateMap = new HashMap<>();

            for (Object[] row : batchInfoList) {
                Long txnId = row[0] != null ? ((Number) row[0]).longValue() : null;
                if (txnId != null) {
                    String batchId = row[1] != null ? row[1].toString() : null;
                    String status = row[2] != null ? row[2].toString() : null;
                    LocalDate paidDate = null;
                    if (row[3] != null) {
                        if (row[3] instanceof java.sql.Date) {
                            paidDate = ((java.sql.Date) row[3]).toLocalDate();
                        } else if (row[3] instanceof LocalDate) {
                            paidDate = (LocalDate) row[3];
                        }
                    }

                    // Concatenate batch IDs for split payments (same transaction in multiple batches)
                    if (batchIdMap.containsKey(txnId)) {
                        if (batchId != null && !batchIdMap.get(txnId).contains(batchId)) {
                            batchIdMap.put(txnId, batchIdMap.get(txnId) + ", " + batchId);
                        }
                    } else {
                        batchIdMap.put(txnId, batchId);
                        statusMap.put(txnId, status);
                        paidDateMap.put(txnId, paidDate);
                    }
                }
            }

            // Populate batch info on each payment detail
            for (site.easy.to.build.crm.dto.statement.PaymentDetailDTO detail : paymentDetails) {
                Long txnId = detail.getTransactionId();
                if (txnId != null && batchIdMap.containsKey(txnId)) {
                    detail.setBatchId(batchIdMap.get(txnId));
                    detail.setPaymentStatus(statusMap.get(txnId));
                    detail.setPaidDate(paidDateMap.get(txnId));
                }
            }

            log.debug("Populated batch info for {} of {} payments", batchIdMap.size(), paymentDetails.size());
        } catch (Exception e) {
            log.warn("Error populating batch info: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }
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

        // PRIORITY 1: Use the customer linked directly to the invoice/lease
        // The invoice.customer IS the tenant for lease records (customer_type = TENANT)
        // This is the most reliable source as it's directly linked to this specific lease
        Customer invoiceCustomer = invoice.getCustomer();
        if (invoiceCustomer != null && invoiceCustomer.getName() != null && !invoiceCustomer.getName().trim().isEmpty()) {
            log.info("✓ Found tenant name from invoice customer: {} (type: {})",
                invoiceCustomer.getName(), invoiceCustomer.getCustomerType());
            return invoiceCustomer.getName().trim();
        }

        // FALLBACK 1: Get tenant from letting instruction
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

        // FALLBACK 2: Get tenant from PayProp tenant data via property's payprop_id
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

    /**
     * Calculate the tenant opening balance (arrears) for a lease as of a specific date.
     *
     * Opening Balance = (Total rent due before date) - (Total rent received before date)
     *
     * RENT IN ADVANCE MODEL:
     * - Full cycle rent is due on the lease start date
     * - Full cycle rent is due on each cycle anniversary thereafter
     * - We count how many cycle START DATES occurred BEFORE the asOfDate
     * - Each cycle start date triggers the full cycle rent being due
     *
     * Example: Lease starts 2024-01-15, 6-month cycle @ £6000, asOfDate = 2024-10-01
     * - Cycle 1 starts 2024-01-15 (before asOfDate) ✓
     * - Cycle 2 starts 2024-07-15 (before asOfDate) ✓
     * - Cycle 3 starts 2025-01-15 (after asOfDate) ✗
     * - Cycles due = 2, Rent due = £12,000
     *
     * @param leaseId The lease/invoice ID
     * @param leaseStartDate The lease start date (first cycle due date)
     * @param asOfDate Calculate balance as of this date (count cycles starting BEFORE this date)
     * @param rentAmount The rent amount per cycle (full cycle amount)
     * @param frequencyMonths The billing cycle length in months (1=monthly, 6=semi-annual, etc.)
     * @param leaseEndDate The lease end date (null for ongoing leases) - used for proration
     * @return Opening balance (positive = arrears/tenant owes, negative = credit/overpaid)
     */
    public java.math.BigDecimal calculateTenantOpeningBalance(Long leaseId, LocalDate leaseStartDate,
                                                               LocalDate asOfDate, java.math.BigDecimal rentAmount,
                                                               Integer frequencyMonths, LocalDate leaseEndDate) {
        // Default to monthly if not specified
        int cycleMonths = (frequencyMonths != null && frequencyMonths > 0) ? frequencyMonths : 1;

        log.debug("Calculating tenant opening balance for lease {} from {} to {} (rent: {}, cycle: {} months, end: {})",
            leaseId, leaseStartDate, asOfDate, rentAmount, cycleMonths, leaseEndDate);

        if (leaseStartDate == null || asOfDate == null || rentAmount == null) {
            log.warn("Cannot calculate opening balance - missing required data for lease {}", leaseId);
            return java.math.BigDecimal.ZERO;
        }

        // If asOfDate is on or before lease start, no rent is due yet
        if (!asOfDate.isAfter(leaseStartDate)) {
            return java.math.BigDecimal.ZERO;
        }

        // RENT IN ADVANCE: Count cycle start dates that occurred BEFORE asOfDate
        // Full rent is due when a cycle STARTS, so we count complete cycles that have started
        long cyclesDue;
        java.math.BigDecimal totalRentDue;

        // If lease has ended before asOfDate, cap cycles at lease end date
        if (leaseEndDate != null && leaseEndDate.isBefore(asOfDate)) {
            // Find the last cycle that started before or on the lease end date
            LocalDate lastCycleStart = leaseStartDate;
            while (lastCycleStart.plusMonths(cycleMonths).isBefore(leaseEndDate) ||
                   lastCycleStart.plusMonths(cycleMonths).isEqual(leaseEndDate)) {
                lastCycleStart = lastCycleStart.plusMonths(cycleMonths);
            }

            LocalDate cycleEndDate = lastCycleStart.plusMonths(cycleMonths).minusDays(1);

            // If lease ended before this cycle's natural end, prorate
            if (leaseEndDate.isBefore(cycleEndDate)) {
                // Full cycles before the last one + prorated final cycle
                long fullCyclesBefore = countCycleStartDatesBefore(leaseStartDate, lastCycleStart, cycleMonths);
                java.math.BigDecimal fullCyclesRent = rentAmount.multiply(java.math.BigDecimal.valueOf(fullCyclesBefore));

                // Prorate the final partial cycle
                double monthlyRate = rentAmount.doubleValue() / cycleMonths;
                long fullMonthsInPartial = java.time.temporal.ChronoUnit.MONTHS.between(lastCycleStart, leaseEndDate);
                LocalDate afterFullMonths = lastCycleStart.plusMonths(fullMonthsInPartial);
                long remainingDays = java.time.temporal.ChronoUnit.DAYS.between(afterFullMonths, leaseEndDate) + 1;
                int daysInMonth = leaseEndDate.lengthOfMonth();

                double proratedAmount = (fullMonthsInPartial * monthlyRate) +
                                       ((double) remainingDays / daysInMonth * monthlyRate);
                proratedAmount = Math.round(proratedAmount * 100.0) / 100.0;

                totalRentDue = fullCyclesRent.add(java.math.BigDecimal.valueOf(proratedAmount));
                cyclesDue = fullCyclesBefore;

                log.debug("Lease {} ended mid-cycle: {} full cycles + prorated {} months {} days = {}",
                    leaseId, fullCyclesBefore, fullMonthsInPartial, remainingDays, totalRentDue);
            } else {
                // Lease ended at cycle boundary - count cycles that started up to and including lastCycleStart
                cyclesDue = countCycleStartDatesBefore(leaseStartDate, lastCycleStart.plusDays(1), cycleMonths);
                totalRentDue = rentAmount.multiply(java.math.BigDecimal.valueOf(cyclesDue));

                log.debug("Lease {} ended at cycle boundary: {} full cycles = {}",
                    leaseId, cyclesDue, totalRentDue);
            }
        } else {
            // Ongoing lease or statement period is before lease end - count cycles before asOfDate
            cyclesDue = countCycleStartDatesBefore(leaseStartDate, asOfDate, cycleMonths);
            totalRentDue = rentAmount.multiply(java.math.BigDecimal.valueOf(cyclesDue));
        }

        log.debug("Lease {}: rent due before {} = {} (cycles started: {})",
            leaseId, asOfDate, totalRentDue, cyclesDue);

        // Get total rent received before asOfDate
        java.math.BigDecimal totalReceived = getTotalRentReceivedBefore(leaseId, asOfDate);

        log.debug("Lease {}: total received before {} = {}", leaseId, asOfDate, totalReceived);

        // Opening balance = rent due - rent received
        java.math.BigDecimal openingBalance = totalRentDue.subtract(totalReceived);

        log.info("Lease {} opening balance as of {}: {} (due: {} - received: {})",
            leaseId, asOfDate, openingBalance, totalRentDue, totalReceived);

        return openingBalance;
    }

    /**
     * Calculate total rent due with proration for partial final cycle.
     *
     * @param leaseStartDate Lease start date
     * @param endDate End date for calculation (exclusive)
     * @param cycleRent Full cycle rent amount
     * @param cycleMonths Months per cycle
     * @return Total rent due including prorated final cycle
     */
    private java.math.BigDecimal calculateRentDueWithProration(LocalDate leaseStartDate, LocalDate endDate,
                                                                java.math.BigDecimal cycleRent, int cycleMonths) {
        if (leaseStartDate == null || endDate == null || !endDate.isAfter(leaseStartDate)) {
            return java.math.BigDecimal.ZERO;
        }

        // Count full cycles
        long fullCycles = 0;
        LocalDate cycleStart = leaseStartDate;
        LocalDate lastCycleStart = leaseStartDate;

        while (true) {
            LocalDate nextCycleStart = cycleStart.plusMonths(cycleMonths);
            if (!nextCycleStart.isBefore(endDate)) {
                // This cycle extends past endDate - need to check for proration
                lastCycleStart = cycleStart;
                break;
            }
            fullCycles++;
            cycleStart = nextCycleStart;
            lastCycleStart = cycleStart;
        }

        // Calculate rent for full cycles
        java.math.BigDecimal totalRent = cycleRent.multiply(java.math.BigDecimal.valueOf(fullCycles));

        // Check if there's a partial final cycle to prorate
        LocalDate cycleEndDate = lastCycleStart.plusMonths(cycleMonths);
        if (endDate.isAfter(lastCycleStart) && endDate.isBefore(cycleEndDate)) {
            // Prorate: (full months × monthly rate) + (remaining days / days in month × monthly rate)
            double monthlyRate = cycleRent.doubleValue() / cycleMonths;

            // Count full months in the partial cycle
            long fullMonthsInPartial = java.time.temporal.ChronoUnit.MONTHS.between(lastCycleStart, endDate);
            LocalDate afterFullMonths = lastCycleStart.plusMonths(fullMonthsInPartial);

            // Count remaining days
            long remainingDays = java.time.temporal.ChronoUnit.DAYS.between(afterFullMonths, endDate);
            int daysInMonth = afterFullMonths.lengthOfMonth();

            double proratedAmount = (fullMonthsInPartial * monthlyRate) +
                                   ((double) remainingDays / daysInMonth * monthlyRate);
            proratedAmount = Math.round(proratedAmount * 100.0) / 100.0;

            totalRent = totalRent.add(java.math.BigDecimal.valueOf(proratedAmount));

            log.debug("Prorated final cycle: {} full months + {} days = £{}",
                fullMonthsInPartial, remainingDays, proratedAmount);
        } else if (!endDate.isBefore(cycleEndDate.plusDays(1))) {
            // The cycle that started at lastCycleStart is fully within the period
            // This happens when endDate is at or after the cycle end
            totalRent = totalRent.add(cycleRent);
        }

        return totalRent;
    }

    /**
     * Count how many cycle start dates occurred BEFORE a given date.
     *
     * RENT IN ADVANCE: Rent is due on lease start and every cycleMonths thereafter.
     *
     * @param leaseStartDate The first cycle start date (lease start)
     * @param beforeDate Count cycles starting strictly before this date
     * @param cycleMonths Months per billing cycle
     * @return Number of cycles that have started (and thus rent is due)
     */
    private long countCycleStartDatesBefore(LocalDate leaseStartDate, LocalDate beforeDate, int cycleMonths) {
        if (beforeDate.isBefore(leaseStartDate) || beforeDate.equals(leaseStartDate)) {
            return 0;  // No cycles have started yet
        }

        // First cycle starts on lease start date - if we're past that, at least 1 cycle is due
        long cycleCount = 1;

        // Count subsequent cycle start dates
        LocalDate nextCycleStart = leaseStartDate.plusMonths(cycleMonths);
        while (nextCycleStart.isBefore(beforeDate)) {
            cycleCount++;
            nextCycleStart = nextCycleStart.plusMonths(cycleMonths);
        }

        return cycleCount;
    }

    /**
     * Count how many cycle start dates fall WITHIN a given period (inclusive of both start and end).
     *
     * RENT IN ADVANCE: Rent is due on lease start and every cycleMonths thereafter.
     * This counts cycle start dates that fall within [periodStart, periodEnd].
     *
     * @param leaseStartDate The first cycle start date (lease start)
     * @param periodStart Start of the period (inclusive)
     * @param periodEnd End of the period (inclusive)
     * @param cycleMonths Months per billing cycle
     * @return Number of cycle start dates within the period
     */
    public long countCycleStartDatesInPeriod(LocalDate leaseStartDate, LocalDate periodStart,
                                              LocalDate periodEnd, int cycleMonths) {
        if (leaseStartDate == null || periodStart == null || periodEnd == null) {
            return 0;
        }

        long cycleCount = 0;

        // Start from lease start date and iterate through all cycle start dates
        LocalDate cycleStart = leaseStartDate;

        // Skip cycles that are before the period start
        while (cycleStart.isBefore(periodStart)) {
            cycleStart = cycleStart.plusMonths(cycleMonths);
        }

        // Count cycles within the period [periodStart, periodEnd]
        while (!cycleStart.isAfter(periodEnd)) {
            cycleCount++;
            cycleStart = cycleStart.plusMonths(cycleMonths);
        }

        return cycleCount;
    }

    /**
     * Calculate rent due within a period based on cycle start dates.
     *
     * RENT IN ADVANCE: Full cycle rent is due on each cycle start date that falls within the period.
     *
     * @param leaseStartDate The lease start date (first cycle)
     * @param periodStart Start of the period (inclusive)
     * @param periodEnd End of the period (inclusive)
     * @param rentAmount The rent amount per cycle
     * @param frequencyMonths The billing cycle length in months
     * @return Total rent due in the period
     */
    public java.math.BigDecimal calculateRentDueInPeriod(LocalDate leaseStartDate, LocalDate periodStart,
                                                          LocalDate periodEnd, java.math.BigDecimal rentAmount,
                                                          Integer frequencyMonths) {
        int cycleMonths = (frequencyMonths != null && frequencyMonths > 0) ? frequencyMonths : 1;

        long cyclesDue = countCycleStartDatesInPeriod(leaseStartDate, periodStart, periodEnd, cycleMonths);

        java.math.BigDecimal rentDue = rentAmount.multiply(java.math.BigDecimal.valueOf(cyclesDue));

        log.debug("Rent due in period {}-{}: {} cycles × {} = {}",
            periodStart, periodEnd, cyclesDue, rentAmount, rentDue);

        return rentDue;
    }

    /**
     * Overload without leaseEndDate - assumes ongoing lease (no proration needed)
     */
    public java.math.BigDecimal calculateTenantOpeningBalance(Long leaseId, LocalDate leaseStartDate,
                                                               LocalDate asOfDate, java.math.BigDecimal rentAmount,
                                                               Integer frequencyMonths) {
        return calculateTenantOpeningBalance(leaseId, leaseStartDate, asOfDate, rentAmount, frequencyMonths, null);
    }

    /**
     * Overload for backward compatibility - defaults to monthly billing cycle
     * @deprecated Use calculateTenantOpeningBalance with frequencyMonths and leaseEndDate instead
     */
    @Deprecated
    public java.math.BigDecimal calculateTenantOpeningBalance(Long leaseId, LocalDate leaseStartDate,
                                                               LocalDate asOfDate, java.math.BigDecimal monthlyRent) {
        return calculateTenantOpeningBalance(leaseId, leaseStartDate, asOfDate, monthlyRent, 1, null);
    }

    /**
     * Get total rent received for a lease before a specific date.
     * Sums all INCOMING transactions before the specified date.
     *
     * @param leaseId The lease/invoice ID
     * @param beforeDate Sum payments before this date (exclusive)
     * @return Total amount received
     */
    public java.math.BigDecimal getTotalRentReceivedBefore(Long leaseId, LocalDate beforeDate) {
        // Query all transactions for this lease and filter to INCOMING before the date
        List<UnifiedTransaction> transactions = unifiedTransactionRepository.findByInvoiceId(leaseId);

        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        for (UnifiedTransaction txn : transactions) {
            // Only count INCOMING transactions (rent received)
            if (txn.getFlowDirection() != UnifiedTransaction.FlowDirection.INCOMING) {
                continue;
            }
            if (txn.getTransactionDate() != null && txn.getTransactionDate().isBefore(beforeDate)) {
                if (txn.getAmount() != null) {
                    total = total.add(txn.getAmount());
                }
            }
        }

        return total;
    }

    /**
     * Get total rent received for a lease within a date range.
     *
     * @param leaseId The lease/invoice ID
     * @param startDate Start of range (inclusive)
     * @param endDate End of range (inclusive)
     * @return Total amount received in the period
     */
    public java.math.BigDecimal getTotalRentReceivedInPeriod(Long leaseId, LocalDate startDate, LocalDate endDate) {
        List<UnifiedTransaction> transactions = unifiedTransactionRepository
            .findByInvoiceIdAndTransactionDateBetweenAndFlowDirection(
                leaseId, startDate, endDate, UnifiedTransaction.FlowDirection.INCOMING);

        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        for (UnifiedTransaction txn : transactions) {
            if (txn.getAmount() != null) {
                total = total.add(txn.getAmount());
            }
        }

        return total;
    }

    // ===== RELATED PAYMENTS EXTRACTION =====

    /**
     * Extract related payments for a specific period and set of properties.
     * This method traces:
     * 1. Transactions in the period (by propertyId + date range)
     * 2. Allocations linked to those transactions
     * 3. Payment batches from those allocations
     *
     * Used to populate the RELATED PAYMENTS section on monthly statement tabs.
     *
     * @param propertyIds List of property IDs from leaseMaster
     * @param startDate Period start
     * @param endDate Period end
     * @return List of payment batch summaries with allocation details
     */
    public List<PaymentBatchSummaryDTO> extractRelatedPaymentsForPeriod(
            List<Long> propertyIds, LocalDate startDate, LocalDate endDate) {

        log.info("Extracting related payments for {} properties from {} to {}",
            propertyIds.size(), startDate, endDate);
        log.info("Property IDs being searched: {}", propertyIds);

        if (propertyIds == null || propertyIds.isEmpty()) {
            log.warn("No property IDs provided for related payments extraction");
            return new ArrayList<>();
        }

        // Get all allocations for period's transactions (including unbatched)
        List<UnifiedAllocation> allocations;
        try {
            allocations = unifiedAllocationRepository.findAllAllocationsForPropertiesInPeriod(
                propertyIds, startDate, endDate);
            log.info("Query returned {} allocations", allocations != null ? allocations.size() : 0);
        } catch (Exception e) {
            log.warn("Error fetching allocations for related payments: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }

        if (allocations == null || allocations.isEmpty()) {
            log.info("No allocations found for properties {} in period {} to {}. " +
                    "This may mean allocations haven't been created yet for these transactions.",
                propertyIds, startDate, endDate);
            return new ArrayList<>();
        }

        log.info("Found {} allocations for period", allocations.size());

        // Group by batch ID (null batch ID = pending/unbatched)
        Map<String, List<UnifiedAllocation>> byBatch = new HashMap<>();
        for (UnifiedAllocation alloc : allocations) {
            String batchKey = alloc.getPaymentBatchId() != null ? alloc.getPaymentBatchId() : "PENDING";
            byBatch.computeIfAbsent(batchKey, k -> new ArrayList<>()).add(alloc);
        }

        List<PaymentBatchSummaryDTO> summaries = new ArrayList<>();

        for (Map.Entry<String, List<UnifiedAllocation>> entry : byBatch.entrySet()) {
            String batchId = entry.getKey();
            List<UnifiedAllocation> batchAllocations = entry.getValue();

            PaymentBatchSummaryDTO summary = new PaymentBatchSummaryDTO();
            summary.setBatchId(batchId);

            // Get batch details (if not pending)
            if (!"PENDING".equals(batchId)) {
                try {
                    PaymentBatch batch = paymentBatchRepository.findByBatchId(batchId).orElse(null);
                    if (batch != null) {
                        summary.setPaymentDate(batch.getPaymentDate());
                        summary.setBatchStatus(batch.getStatus() != null ? batch.getStatus().name() : "UNKNOWN");
                    } else {
                        summary.setBatchStatus("UNKNOWN");
                    }
                } catch (Exception e) {
                    log.warn("Error fetching batch {}: {}", batchId, e.getMessage());
                    summary.setBatchStatus("ERROR");
                }
            } else {
                summary.setBatchStatus("PENDING");
            }

            // Calculate totals by type and build detail rows
            BigDecimal ownerTotal = BigDecimal.ZERO;
            BigDecimal expenseTotal = BigDecimal.ZERO;
            BigDecimal commissionTotal = BigDecimal.ZERO;
            BigDecimal disbursementTotal = BigDecimal.ZERO;

            List<RelatedPaymentDTO> details = new ArrayList<>();
            Map<Long, Boolean> propertyIdsSeen = new HashMap<>();

            for (UnifiedAllocation alloc : batchAllocations) {
                // Build detail row
                RelatedPaymentDTO detail = new RelatedPaymentDTO();
                detail.setAllocationId(alloc.getId());
                detail.setBatchId(batchId);
                detail.setPaymentDate(alloc.getPaidDate());
                detail.setAllocationType(alloc.getAllocationType() != null ? alloc.getAllocationType().name() : "UNKNOWN");
                detail.setAmount(alloc.getAmount());
                detail.setPropertyName(alloc.getPropertyName());
                detail.setBatchStatus(summary.getBatchStatus());
                detail.setDescription(alloc.getDescription());
                detail.setTransactionId(alloc.getIncomingTransactionId());
                detail.setCategory(alloc.getCategory());

                details.add(detail);

                // Track property for count
                if (alloc.getPropertyId() != null) {
                    propertyIdsSeen.put(alloc.getPropertyId(), true);
                }

                // Accumulate by type
                BigDecimal amount = alloc.getAmount() != null ? alloc.getAmount() : BigDecimal.ZERO;
                if (alloc.getAllocationType() != null) {
                    switch (alloc.getAllocationType()) {
                        case OWNER -> ownerTotal = ownerTotal.add(amount);
                        case EXPENSE -> expenseTotal = expenseTotal.add(amount);
                        case COMMISSION -> commissionTotal = commissionTotal.add(amount);
                        case DISBURSEMENT -> disbursementTotal = disbursementTotal.add(amount);
                        default -> { /* OTHER type - ignore */ }
                    }
                }
            }

            summary.setTotalOwnerAllocations(ownerTotal);
            summary.setTotalExpenseAllocations(expenseTotal);
            summary.setTotalCommissionAllocations(commissionTotal);
            summary.setTotalDisbursementAllocations(disbursementTotal);
            summary.calculateNetPayment();
            summary.setPropertyCount(propertyIdsSeen.size());
            summary.setAllocations(details);

            summaries.add(summary);
        }

        // Sort by payment date (nulls/pending last)
        summaries.sort(Comparator.comparing(
            PaymentBatchSummaryDTO::getPaymentDate,
            Comparator.nullsLast(Comparator.naturalOrder())
        ));

        log.info("Extracted {} related payment batches for period", summaries.size());
        return summaries;
    }

    /**
     * Extract allocation summary per property for reconciliation columns.
     * Returns a map of propertyId -> LeaseAllocationSummaryDTO containing:
     * - Total OWNER allocations (money allocated to owner)
     * - Payment status (PAID, PENDING, BATCHED)
     * - Batch ID(s)
     * - Latest payment date
     *
     * @param propertyIds List of property IDs to query
     * @param startDate Period start
     * @param endDate Period end
     * @return Map of propertyId to allocation summary
     */
    public Map<Long, LeaseAllocationSummaryDTO> extractAllocationSummaryByProperty(
            List<Long> propertyIds, LocalDate startDate, LocalDate endDate) {

        log.info("Extracting allocation summary for {} properties from {} to {}",
            propertyIds.size(), startDate, endDate);

        Map<Long, LeaseAllocationSummaryDTO> result = new HashMap<>();

        if (propertyIds == null || propertyIds.isEmpty()) {
            log.warn("No property IDs provided for allocation summary extraction");
            return result;
        }

        try {
            List<Object[]> summaryRows = unifiedAllocationRepository.getLeaseAllocationSummaryForPeriod(
                propertyIds, startDate, endDate);

            log.info("Query returned {} property allocation summaries", summaryRows != null ? summaryRows.size() : 0);

            if (summaryRows != null) {
                for (Object[] row : summaryRows) {
                    LeaseAllocationSummaryDTO summary = new LeaseAllocationSummaryDTO();

                    // Column 0: property_id
                    Long propertyId = row[0] != null ? ((Number) row[0]).longValue() : null;
                    summary.setPropertyId(propertyId);

                    // Column 1: total_owner_allocated
                    BigDecimal totalAllocated = row[1] != null ?
                        new BigDecimal(row[1].toString()) : BigDecimal.ZERO;
                    summary.setTotalAllocatedAmount(totalAllocated);

                    // Column 2: max_payment_status
                    String paymentStatus = row[2] != null ? row[2].toString() : "NONE";
                    summary.setPaymentStatus(paymentStatus);

                    // Column 3: batch_ids (comma-separated)
                    String batchIds = row[3] != null ? row[3].toString() : null;
                    if (batchIds != null && batchIds.contains(",")) {
                        summary.setPrimaryBatchId("MULTIPLE");
                    } else {
                        summary.setPrimaryBatchId(batchIds != null ? batchIds : "-");
                    }

                    // Column 4: latest_paid_date
                    if (row[4] != null) {
                        if (row[4] instanceof java.sql.Date) {
                            summary.setLatestPaymentDate(((java.sql.Date) row[4]).toLocalDate());
                        } else if (row[4] instanceof LocalDate) {
                            summary.setLatestPaymentDate((LocalDate) row[4]);
                        }
                    }

                    // Column 5: allocation_count
                    int count = row[5] != null ? ((Number) row[5]).intValue() : 0;
                    summary.setAllocationCount(count);

                    if (propertyId != null) {
                        result.put(propertyId, summary);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error fetching allocation summary: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            e.printStackTrace();
        }

        log.info("Extracted allocation summaries for {} properties", result.size());
        return result;
    }

    /**
     * Extract allocation summary per invoice/lease for reconciliation columns.
     * Returns a map of invoiceId -> LeaseAllocationSummaryDTO.
     * Uses invoice_id for precise matching, falls back to date range for allocations without invoice_id.
     *
     * @param propertyIds List of property IDs to query
     * @param invoiceIds List of invoice IDs to query
     * @param startDate Period start (used for fallback when invoice_id is null)
     * @param endDate Period end
     * @return Map of invoiceId to allocation summary
     */
    public Map<Long, LeaseAllocationSummaryDTO> extractAllocationSummaryByInvoice(
            List<Long> propertyIds, List<Long> invoiceIds, LocalDate startDate, LocalDate endDate) {

        log.info("Extracting allocation summary for {} invoices from {} to {}",
            invoiceIds.size(), startDate, endDate);

        Map<Long, LeaseAllocationSummaryDTO> result = new HashMap<>();

        if (invoiceIds == null || invoiceIds.isEmpty()) {
            log.warn("No invoice IDs provided for allocation summary extraction");
            return result;
        }

        try {
            List<Object[]> summaryRows = unifiedAllocationRepository.getLeaseAllocationSummaryByInvoice(
                propertyIds, invoiceIds, startDate, endDate);

            log.info("Query returned {} invoice allocation summaries", summaryRows != null ? summaryRows.size() : 0);

            if (summaryRows != null) {
                for (Object[] row : summaryRows) {
                    LeaseAllocationSummaryDTO summary = new LeaseAllocationSummaryDTO();

                    // Column 0: invoice_id (COALESCE returns 0 for nulls)
                    Long invoiceId = row[0] != null ? ((Number) row[0]).longValue() : 0L;
                    summary.setInvoiceId(invoiceId);

                    // Column 1: property_id
                    Long propertyId = row[1] != null ? ((Number) row[1]).longValue() : null;
                    summary.setPropertyId(propertyId);

                    // Column 2: total_owner_allocated
                    BigDecimal totalAllocated = row[2] != null ?
                        new BigDecimal(row[2].toString()) : BigDecimal.ZERO;
                    summary.setTotalAllocatedAmount(totalAllocated);

                    // Column 3: max_payment_status
                    String paymentStatus = row[3] != null ? row[3].toString() : "NONE";
                    summary.setPaymentStatus(paymentStatus);

                    // Column 4: batch_ids (comma-separated)
                    String batchIds = row[4] != null ? row[4].toString() : null;
                    if (batchIds != null && batchIds.contains(",")) {
                        summary.setPrimaryBatchId("MULTIPLE");
                    } else {
                        summary.setPrimaryBatchId(batchIds != null ? batchIds : "-");
                    }

                    // Column 5: latest_paid_date
                    if (row[5] != null) {
                        if (row[5] instanceof java.sql.Date) {
                            summary.setLatestPaymentDate(((java.sql.Date) row[5]).toLocalDate());
                        } else if (row[5] instanceof LocalDate) {
                            summary.setLatestPaymentDate((LocalDate) row[5]);
                        }
                    }

                    // Column 6: allocation_count
                    int count = row[6] != null ? ((Number) row[6]).intValue() : 0;
                    summary.setAllocationCount(count);

                    // Key by invoice_id, or by property_id for legacy allocations (invoice_id = 0)
                    if (invoiceId != null && invoiceId > 0) {
                        result.put(invoiceId, summary);
                    } else if (propertyId != null) {
                        // Legacy allocations without invoice_id - store under negative property_id
                        // to distinguish from invoice-based keys
                        result.put(-propertyId, summary);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error fetching allocation summary by invoice: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            e.printStackTrace();
        }

        log.info("Extracted allocation summaries for {} invoices", result.size());
        return result;
    }

    /**
     * Extract batch references by invoice/lease and allocation type.
     * Returns a nested map: invoiceId -> allocationType -> batchIds (comma-separated)
     * Used to populate batch reference columns on monthly statement rows.
     *
     * IMPORTANT: Groups by invoice_id (lease) to ensure batch refs match the specific lease,
     * not all leases on the same property. Fallback to negative property_id for legacy allocations.
     */
    public Map<Long, Map<String, String>> extractBatchRefsByPropertyAndType(
            List<Long> propertyIds, List<Long> invoiceIds, LocalDate startDate, LocalDate endDate) {

        log.info("Extracting batch refs for {} invoices from {} to {}",
            invoiceIds != null ? invoiceIds.size() : 0, startDate, endDate);

        Map<Long, Map<String, String>> result = new HashMap<>();

        if (propertyIds == null || propertyIds.isEmpty()) {
            return result;
        }

        // Ensure we have invoice IDs to query
        if (invoiceIds == null || invoiceIds.isEmpty()) {
            log.warn("No invoice IDs provided for batch refs extraction");
            return result;
        }

        try {
            List<Object[]> rows = unifiedAllocationRepository.getBatchRefsByInvoiceAndType(
                propertyIds, invoiceIds, startDate, endDate);

            if (rows != null) {
                for (Object[] row : rows) {
                    // Column 0: invoice_id (COALESCE returns 0 for nulls)
                    Long invoiceId = row[0] != null ? ((Number) row[0]).longValue() : 0L;
                    // Column 1: property_id
                    Long propertyId = row[1] != null ? ((Number) row[1]).longValue() : null;
                    // Column 2: allocation_type
                    String allocationType = row[2] != null ? row[2].toString() : null;
                    // Column 3: batch_ids
                    String batchIds = row[3] != null ? row[3].toString() : "";

                    if (allocationType != null) {
                        // Key by invoice_id for precise matching, or negative property_id for legacy
                        Long key = invoiceId > 0 ? invoiceId : (propertyId != null ? -propertyId : null);
                        if (key != null) {
                            result.computeIfAbsent(key, k -> new HashMap<>())
                                  .put(allocationType, batchIds);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error fetching batch refs: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }

        log.info("Extracted batch refs for {} invoices/properties", result.size());
        return result;
    }

    /**
     * Extract payment batch summaries for reconciliation section.
     * Returns list of batch summaries with total paid amounts.
     */
    public List<PaymentBatchSummaryDTO> extractPaymentBatchSummariesForPeriod(
            List<Long> propertyIds, LocalDate startDate, LocalDate endDate) {

        log.info("Extracting payment batch summaries for {} properties from {} to {}",
            propertyIds.size(), startDate, endDate);

        List<PaymentBatchSummaryDTO> result = new ArrayList<>();

        if (propertyIds == null || propertyIds.isEmpty()) {
            return result;
        }

        try {
            List<Object[]> rows = unifiedAllocationRepository.getPaymentBatchSummariesForPeriod(
                propertyIds, startDate, endDate);

            if (rows != null) {
                for (Object[] row : rows) {
                    PaymentBatchSummaryDTO summary = new PaymentBatchSummaryDTO();

                    // Column 0: batch_id
                    summary.setBatchId(row[0] != null ? row[0].toString() : "");

                    // Column 1: payment_date
                    if (row[1] != null) {
                        if (row[1] instanceof java.sql.Date) {
                            summary.setPaymentDate(((java.sql.Date) row[1]).toLocalDate());
                        } else if (row[1] instanceof LocalDate) {
                            summary.setPaymentDate((LocalDate) row[1]);
                        }
                    }

                    // Column 2: status
                    summary.setBatchStatus(row[2] != null ? row[2].toString() : "");

                    // Column 3: total_owner
                    BigDecimal totalOwner = row[3] != null ?
                        new BigDecimal(row[3].toString()) : BigDecimal.ZERO;
                    summary.setTotalOwnerAllocations(totalOwner);

                    // Column 4: total_expense
                    BigDecimal totalExpense = row[4] != null ?
                        new BigDecimal(row[4].toString()) : BigDecimal.ZERO;
                    summary.setTotalExpenseAllocations(totalExpense);

                    // Column 5: total_commission
                    BigDecimal totalCommission = row[5] != null ?
                        new BigDecimal(row[5].toString()) : BigDecimal.ZERO;
                    summary.setTotalCommissionAllocations(totalCommission);

                    // Column 6: net_payment
                    BigDecimal netPayment = row[6] != null ?
                        new BigDecimal(row[6].toString()) : BigDecimal.ZERO;
                    summary.setNetPayment(netPayment);

                    result.add(summary);
                }
            }
        } catch (Exception e) {
            log.warn("Error fetching payment batch summaries: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }

        log.info("Extracted {} payment batch summaries", result.size());
        return result;
    }

    /**
     * Calculate total payments made for a period.
     * Used for running balance calculation in Payment Reconciliation.
     */
    public BigDecimal calculateTotalPaymentsForPeriod(
            List<Long> propertyIds, LocalDate startDate, LocalDate endDate) {

        List<PaymentBatchSummaryDTO> batches = extractPaymentBatchSummariesForPeriod(
            propertyIds, startDate, endDate);

        return batches.stream()
            .map(PaymentBatchSummaryDTO::getNetPayment)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // =====================================================================
    // PERIOD RECONCILIATION EXTRACTION METHODS
    // =====================================================================

    /**
     * Extract unallocated income as of a specific date.
     * Returns income transactions received BEFORE the date that are not fully allocated
     * BY PAYMENTS MADE BEFORE that date. This ensures B/F shows the true state at period start.
     * Used for "Brought Forward" section of reconciliation.
     */
    public List<UnallocatedIncomeDTO> extractUnallocatedIncomeAsOf(Long customerId, LocalDate asOfDate) {
        log.info("Extracting unallocated income for customer {} as of {}", customerId, asOfDate);

        List<UnallocatedIncomeDTO> result = new ArrayList<>();

        // Key fix: Only count allocations from payments made BEFORE asOfDate
        // This ensures B/F reflects the true unallocated state at period start
        String sql = """
            SELECT
                ht.id as transaction_id,
                ht.transaction_date,
                ht.property_id,
                p.property_name,
                ht.lease_reference,
                ht.description as tenant_name,
                ht.category,
                ht.amount as gross_amount,
                COALESCE(ht.net_to_owner_amount, ht.amount * 0.85) as net_due,
                COALESCE(SUM(tba.allocated_amount), 0) as allocated_amount
            FROM historical_transactions ht
            LEFT JOIN properties p ON ht.property_id = p.id
            LEFT JOIN transaction_batch_allocations tba ON ht.id = tba.transaction_id
                AND EXISTS (
                    SELECT 1 FROM payment_batches pb
                    WHERE pb.batch_id = tba.batch_reference
                    AND pb.payment_date < ?
                )
            WHERE ht.owner_id = ?
              AND ht.transaction_date < ?
              AND ht.category = 'rent'
            GROUP BY ht.id, ht.transaction_date, ht.property_id, p.property_name,
                     ht.lease_reference, ht.description, ht.category, ht.amount, ht.net_to_owner_amount
            HAVING COALESCE(ht.net_to_owner_amount, ht.amount * 0.85) - COALESCE(SUM(tba.allocated_amount), 0) > 0.01
            ORDER BY ht.transaction_date, p.property_name
        """;

        try {
            // Parameters: asOfDate (for allocation filter), customerId, asOfDate (for transaction filter)
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, asOfDate, customerId, asOfDate);

            for (Map<String, Object> row : rows) {
                UnallocatedIncomeDTO dto = new UnallocatedIncomeDTO();
                dto.setTransactionId(((Number) row.get("transaction_id")).longValue());
                dto.setTransactionDate(row.get("transaction_date") != null ?
                    ((java.sql.Date) row.get("transaction_date")).toLocalDate() : null);
                dto.setPropertyId(row.get("property_id") != null ?
                    ((Number) row.get("property_id")).longValue() : null);
                dto.setPropertyName((String) row.get("property_name"));
                dto.setLeaseReference((String) row.get("lease_reference"));
                dto.setTenantName((String) row.get("tenant_name"));
                dto.setCategory((String) row.get("category"));
                dto.setGrossAmount(row.get("gross_amount") != null ?
                    new BigDecimal(row.get("gross_amount").toString()) : BigDecimal.ZERO);
                dto.setNetDue(row.get("net_due") != null ?
                    new BigDecimal(row.get("net_due").toString()) : BigDecimal.ZERO);
                dto.setAllocatedAmount(row.get("allocated_amount") != null ?
                    new BigDecimal(row.get("allocated_amount").toString()) : BigDecimal.ZERO);
                dto.setRemainingUnallocated(dto.getNetDue().subtract(dto.getAllocatedAmount()));
                dto.setCommission(dto.getGrossAmount().subtract(dto.getNetDue()));

                result.add(dto);
            }
        } catch (Exception e) {
            log.warn("Error extracting unallocated income: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }

        log.info("Found {} unallocated income transactions", result.size());
        return result;
    }

    /**
     * Extract unallocated payments as of a specific date.
     * Returns payments made BEFORE the date where payment amount exceeds allocated income.
     * Used for "Brought Forward - Owner Credit" section of reconciliation.
     */
    public List<UnallocatedPaymentDTO> extractUnallocatedPaymentsAsOf(Long customerId, LocalDate asOfDate) {
        log.info("Extracting unallocated payments for customer {} as of {}", customerId, asOfDate);

        List<UnallocatedPaymentDTO> result = new ArrayList<>();

        String sql = """
            SELECT
                pb.batch_id,
                pb.payment_date,
                pb.total_payment,
                COALESCE(SUM(ABS(tba.allocated_amount)), 0) as total_allocated,
                pb.status,
                pb.payment_reference
            FROM payment_batches pb
            LEFT JOIN transaction_batch_allocations tba ON pb.batch_id = tba.batch_reference
            WHERE pb.beneficiary_id = ?
              AND pb.payment_date < ?
              AND pb.batch_type = 'OWNER_PAYMENT'
            GROUP BY pb.batch_id, pb.payment_date, pb.total_payment, pb.status, pb.payment_reference
            HAVING pb.total_payment - COALESCE(SUM(ABS(tba.allocated_amount)), 0) > 0.01
            ORDER BY pb.payment_date, pb.batch_id
        """;

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, customerId, asOfDate);

            for (Map<String, Object> row : rows) {
                UnallocatedPaymentDTO dto = new UnallocatedPaymentDTO();
                dto.setBatchId((String) row.get("batch_id"));
                dto.setPaymentDate(row.get("payment_date") != null ?
                    ((java.sql.Date) row.get("payment_date")).toLocalDate() : null);
                dto.setTotalPayment(row.get("total_payment") != null ?
                    new BigDecimal(row.get("total_payment").toString()) : BigDecimal.ZERO);
                dto.setTotalAllocated(row.get("total_allocated") != null ?
                    new BigDecimal(row.get("total_allocated").toString()) : BigDecimal.ZERO);
                dto.setUnallocatedAmount(dto.getTotalPayment().subtract(dto.getTotalAllocated()));
                dto.setStatus((String) row.get("status"));
                dto.setPaymentReference((String) row.get("payment_reference"));

                result.add(dto);
            }
        } catch (Exception e) {
            log.warn("Error extracting unallocated payments: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }

        log.info("Found {} unallocated payments", result.size());
        return result;
    }

    /**
     * Extract unallocated expenses as of a specific date.
     * Returns expense transactions incurred BEFORE the date that are not fully allocated
     * BY PAYMENTS MADE BEFORE that date. This ensures B/F shows the true state at period start.
     * Used for "Brought Forward" section of reconciliation.
     */
    public List<UnallocatedIncomeDTO> extractUnallocatedExpensesAsOf(Long customerId, LocalDate asOfDate) {
        log.info("Extracting unallocated expenses for customer {} as of {}", customerId, asOfDate);

        List<UnallocatedIncomeDTO> result = new ArrayList<>();

        // Key fix: Only count allocations from payments made BEFORE asOfDate
        String sql = """
            SELECT
                ht.id as transaction_id,
                ht.transaction_date,
                ht.property_id,
                p.property_name,
                ht.lease_reference,
                ht.description as tenant_name,
                ht.category,
                ABS(ht.amount) as gross_amount,
                ABS(COALESCE(ht.net_to_owner_amount, ht.amount)) as net_due,
                COALESCE(SUM(ABS(tba.allocated_amount)), 0) as allocated_amount
            FROM historical_transactions ht
            LEFT JOIN properties p ON ht.property_id = p.id
            LEFT JOIN transaction_batch_allocations tba ON ht.id = tba.transaction_id
                AND EXISTS (
                    SELECT 1 FROM payment_batches pb
                    WHERE pb.batch_id = tba.batch_reference
                    AND pb.payment_date < ?
                )
            WHERE ht.owner_id = ?
              AND ht.transaction_date < ?
              AND ht.category != 'rent'
              AND ht.amount < 0
            GROUP BY ht.id, ht.transaction_date, ht.property_id, p.property_name,
                     ht.lease_reference, ht.description, ht.category, ht.amount, ht.net_to_owner_amount
            HAVING ABS(COALESCE(ht.net_to_owner_amount, ht.amount)) - COALESCE(SUM(ABS(tba.allocated_amount)), 0) > 0.01
            ORDER BY ht.transaction_date, p.property_name
        """;

        try {
            // Parameters: asOfDate (for allocation filter), customerId, asOfDate (for transaction filter)
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, asOfDate, customerId, asOfDate);

            for (Map<String, Object> row : rows) {
                UnallocatedIncomeDTO dto = new UnallocatedIncomeDTO();
                dto.setTransactionId(((Number) row.get("transaction_id")).longValue());
                dto.setTransactionDate(row.get("transaction_date") != null ?
                    ((java.sql.Date) row.get("transaction_date")).toLocalDate() : null);
                dto.setPropertyId(row.get("property_id") != null ?
                    ((Number) row.get("property_id")).longValue() : null);
                dto.setPropertyName((String) row.get("property_name"));
                dto.setLeaseReference((String) row.get("lease_reference"));
                dto.setTenantName((String) row.get("tenant_name"));
                dto.setCategory((String) row.get("category"));
                dto.setGrossAmount(row.get("gross_amount") != null ?
                    new BigDecimal(row.get("gross_amount").toString()) : BigDecimal.ZERO);
                dto.setNetDue(row.get("net_due") != null ?
                    new BigDecimal(row.get("net_due").toString()) : BigDecimal.ZERO);
                dto.setAllocatedAmount(row.get("allocated_amount") != null ?
                    new BigDecimal(row.get("allocated_amount").toString()) : BigDecimal.ZERO);
                dto.setRemainingUnallocated(dto.getNetDue().subtract(dto.getAllocatedAmount()));

                result.add(dto);
            }
        } catch (Exception e) {
            log.warn("Error extracting unallocated expenses: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }

        log.info("Found {} unallocated expense transactions", result.size());
        return result;
    }

    /**
     * Extract unallocated expenses as of period end date.
     * Used for "Carried Forward" section of reconciliation.
     */
    public List<UnallocatedIncomeDTO> extractUnallocatedExpensesAsOfEndDate(Long customerId, LocalDate endDate) {
        log.info("Extracting unallocated expenses for customer {} as of end date {}", customerId, endDate);

        List<UnallocatedIncomeDTO> result = new ArrayList<>();

        // For C/F, include ALL allocations (no date filter on allocations)
        String sql = """
            SELECT
                ht.id as transaction_id,
                ht.transaction_date,
                ht.property_id,
                p.property_name,
                ht.lease_reference,
                ht.description as tenant_name,
                ht.category,
                ABS(ht.amount) as gross_amount,
                ABS(COALESCE(ht.net_to_owner_amount, ht.amount)) as net_due,
                COALESCE(SUM(ABS(tba.allocated_amount)), 0) as allocated_amount
            FROM historical_transactions ht
            LEFT JOIN properties p ON ht.property_id = p.id
            LEFT JOIN transaction_batch_allocations tba ON ht.id = tba.transaction_id
            WHERE ht.owner_id = ?
              AND ht.transaction_date <= ?
              AND ht.category != 'rent'
              AND ht.amount < 0
            GROUP BY ht.id, ht.transaction_date, ht.property_id, p.property_name,
                     ht.lease_reference, ht.description, ht.category, ht.amount, ht.net_to_owner_amount
            HAVING ABS(COALESCE(ht.net_to_owner_amount, ht.amount)) - COALESCE(SUM(ABS(tba.allocated_amount)), 0) > 0.01
            ORDER BY ht.transaction_date, p.property_name
        """;

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, customerId, endDate);

            for (Map<String, Object> row : rows) {
                UnallocatedIncomeDTO dto = new UnallocatedIncomeDTO();
                dto.setTransactionId(((Number) row.get("transaction_id")).longValue());
                dto.setTransactionDate(row.get("transaction_date") != null ?
                    ((java.sql.Date) row.get("transaction_date")).toLocalDate() : null);
                dto.setPropertyId(row.get("property_id") != null ?
                    ((Number) row.get("property_id")).longValue() : null);
                dto.setPropertyName((String) row.get("property_name"));
                dto.setLeaseReference((String) row.get("lease_reference"));
                dto.setTenantName((String) row.get("tenant_name"));
                dto.setCategory((String) row.get("category"));
                dto.setGrossAmount(row.get("gross_amount") != null ?
                    new BigDecimal(row.get("gross_amount").toString()) : BigDecimal.ZERO);
                dto.setNetDue(row.get("net_due") != null ?
                    new BigDecimal(row.get("net_due").toString()) : BigDecimal.ZERO);
                dto.setAllocatedAmount(row.get("allocated_amount") != null ?
                    new BigDecimal(row.get("allocated_amount").toString()) : BigDecimal.ZERO);
                dto.setRemainingUnallocated(dto.getNetDue().subtract(dto.getAllocatedAmount()));

                result.add(dto);
            }
        } catch (Exception e) {
            log.warn("Error extracting unallocated expenses for end date: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }

        log.info("Found {} unallocated expense transactions at end date", result.size());
        return result;
    }

    /**
     * Extract income received within a period.
     * Returns all rent transactions received during the period.
     */
    public List<UnallocatedIncomeDTO> extractIncomeReceivedInPeriod(
            Long customerId, LocalDate startDate, LocalDate endDate) {
        log.info("Extracting income for customer {} from {} to {}", customerId, startDate, endDate);

        List<UnallocatedIncomeDTO> result = new ArrayList<>();

        String sql = """
            SELECT
                ht.id as transaction_id,
                ht.transaction_date,
                ht.property_id,
                p.property_name,
                ht.lease_reference,
                ht.description as tenant_name,
                ht.category,
                ht.amount as gross_amount,
                COALESCE(ht.net_to_owner_amount, ht.amount * 0.85) as net_due,
                COALESCE(SUM(tba.allocated_amount), 0) as allocated_amount
            FROM historical_transactions ht
            LEFT JOIN properties p ON ht.property_id = p.id
            LEFT JOIN transaction_batch_allocations tba ON ht.id = tba.transaction_id
            WHERE ht.owner_id = ?
              AND ht.transaction_date >= ?
              AND ht.transaction_date <= ?
              AND ht.category = 'rent'
            GROUP BY ht.id, ht.transaction_date, ht.property_id, p.property_name,
                     ht.lease_reference, ht.description, ht.category, ht.amount, ht.net_to_owner_amount
            ORDER BY ht.transaction_date, p.property_name
        """;

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, customerId, startDate, endDate);

            for (Map<String, Object> row : rows) {
                UnallocatedIncomeDTO dto = new UnallocatedIncomeDTO();
                dto.setTransactionId(((Number) row.get("transaction_id")).longValue());
                dto.setTransactionDate(row.get("transaction_date") != null ?
                    ((java.sql.Date) row.get("transaction_date")).toLocalDate() : null);
                dto.setPropertyId(row.get("property_id") != null ?
                    ((Number) row.get("property_id")).longValue() : null);
                dto.setPropertyName((String) row.get("property_name"));
                dto.setLeaseReference((String) row.get("lease_reference"));
                dto.setTenantName((String) row.get("tenant_name"));
                dto.setCategory((String) row.get("category"));
                dto.setGrossAmount(row.get("gross_amount") != null ?
                    new BigDecimal(row.get("gross_amount").toString()) : BigDecimal.ZERO);
                dto.setNetDue(row.get("net_due") != null ?
                    new BigDecimal(row.get("net_due").toString()) : BigDecimal.ZERO);
                dto.setAllocatedAmount(row.get("allocated_amount") != null ?
                    new BigDecimal(row.get("allocated_amount").toString()) : BigDecimal.ZERO);
                dto.setRemainingUnallocated(dto.getNetDue().subtract(dto.getAllocatedAmount()));
                dto.setCommission(dto.getGrossAmount().subtract(dto.getNetDue()));

                result.add(dto);
            }
        } catch (Exception e) {
            log.warn("Error extracting income for period: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }

        log.info("Found {} income transactions in period", result.size());
        return result;
    }

    /**
     * Extract expenses incurred within a period.
     * Returns expense transactions during the period.
     */
    public List<UnallocatedIncomeDTO> extractExpensesInPeriod(
            Long customerId, LocalDate startDate, LocalDate endDate) {
        log.info("Extracting expenses for customer {} from {} to {}", customerId, startDate, endDate);

        List<UnallocatedIncomeDTO> result = new ArrayList<>();

        String sql = """
            SELECT
                ht.id as transaction_id,
                ht.transaction_date,
                ht.property_id,
                p.property_name,
                ht.lease_reference,
                ht.description,
                ht.category,
                ABS(ht.amount) as amount,
                COALESCE(SUM(ABS(tba.allocated_amount)), 0) as allocated_amount
            FROM historical_transactions ht
            LEFT JOIN properties p ON ht.property_id = p.id
            LEFT JOIN transaction_batch_allocations tba ON ht.id = tba.transaction_id
            WHERE ht.owner_id = ?
              AND ht.transaction_date >= ?
              AND ht.transaction_date <= ?
              AND ht.category IN ('cleaning', 'furnishings', 'maintenance', 'utilities', 'compliance', 'management', 'agency_fee')
            GROUP BY ht.id, ht.transaction_date, ht.property_id, p.property_name,
                     ht.lease_reference, ht.description, ht.category, ht.amount
            ORDER BY ht.transaction_date, p.property_name
        """;

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, customerId, startDate, endDate);

            for (Map<String, Object> row : rows) {
                UnallocatedIncomeDTO dto = new UnallocatedIncomeDTO();
                dto.setTransactionId(((Number) row.get("transaction_id")).longValue());
                dto.setTransactionDate(row.get("transaction_date") != null ?
                    ((java.sql.Date) row.get("transaction_date")).toLocalDate() : null);
                dto.setPropertyId(row.get("property_id") != null ?
                    ((Number) row.get("property_id")).longValue() : null);
                dto.setPropertyName((String) row.get("property_name"));
                dto.setLeaseReference((String) row.get("lease_reference"));
                dto.setCategory((String) row.get("category"));
                dto.setGrossAmount(row.get("amount") != null ?
                    new BigDecimal(row.get("amount").toString()).negate() : BigDecimal.ZERO);
                dto.setNetDue(dto.getGrossAmount()); // For expenses, gross = net
                dto.setAllocatedAmount(row.get("allocated_amount") != null ?
                    new BigDecimal(row.get("allocated_amount").toString()).negate() : BigDecimal.ZERO);
                dto.setRemainingUnallocated(dto.getNetDue().subtract(dto.getAllocatedAmount()));

                result.add(dto);
            }
        } catch (Exception e) {
            log.warn("Error extracting expenses for period: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }

        log.info("Found {} expense transactions in period", result.size());
        return result;
    }

    /**
     * Extract payments made within a period with their allocation details.
     * Shows what each payment covered (which income/expenses).
     *
     * REWRITTEN: Simpler approach using historical_transactions.owner_id and property assignments.
     * The query now:
     * 1. First finds all properties owned by this customer
     * 2. Then finds all allocations for those properties where paid_date is in range
     * 3. Groups by payment_batch_id
     */
    public List<PaymentWithAllocationsDTO> extractPaymentsWithAllocationsInPeriod(
            Long customerId, LocalDate startDate, LocalDate endDate, LocalDate periodStart) {
        log.info("RECONCILIATION: Extracting payments for customer {} from {} to {}", customerId, startDate, endDate);

        List<PaymentWithAllocationsDTO> result = new ArrayList<>();

        try {
            // STEP 1: Debug - check what data exists in unified_allocations
            log.info("RECONCILIATION DEBUG: Checking unified_allocations table structure...");

            String countAllSql = "SELECT COUNT(*) FROM unified_allocations WHERE payment_batch_id IS NOT NULL AND paid_date IS NOT NULL";
            Integer totalAllocations = jdbcTemplate.queryForObject(countAllSql, Integer.class);
            log.info("RECONCILIATION DEBUG: Total allocations with batch and paid_date: {}", totalAllocations);

            // Check allocations in date range (any owner)
            String countInRangeSql = "SELECT COUNT(*) FROM unified_allocations WHERE payment_batch_id IS NOT NULL AND paid_date >= ? AND paid_date <= ?";
            Integer allocsInRange = jdbcTemplate.queryForObject(countInRangeSql, Integer.class, startDate, endDate);
            log.info("RECONCILIATION DEBUG: Allocations in date range {} to {}: {}", startDate, endDate, allocsInRange);

            // Check what beneficiary_ids exist
            String beneficiariesSql = "SELECT DISTINCT beneficiary_id FROM unified_allocations WHERE payment_batch_id IS NOT NULL LIMIT 10";
            List<Map<String, Object>> beneficiaries = jdbcTemplate.queryForList(beneficiariesSql);
            log.info("RECONCILIATION DEBUG: Distinct beneficiary_ids: {}", beneficiaries);

            // Check historical_transactions owner_id
            String ownerIdsSql = "SELECT DISTINCT owner_id FROM historical_transactions WHERE owner_id IS NOT NULL LIMIT 10";
            List<Map<String, Object>> ownerIds = jdbcTemplate.queryForList(ownerIdsSql);
            log.info("RECONCILIATION DEBUG: Distinct owner_ids in historical_transactions: {}", ownerIds);

            // Get properties for this owner
            String propertiesSql = """
                SELECT p.id, p.property_name
                FROM properties p
                JOIN customer_property_assignments cpa ON p.id = cpa.property_id
                WHERE cpa.customer_id = ? AND cpa.assignment_type IN ('OWNER', 'MANAGER')
            """;
            List<Map<String, Object>> properties = jdbcTemplate.queryForList(propertiesSql, customerId);
            log.info("RECONCILIATION DEBUG: Properties for customer {}: {}", customerId, properties);

            if (properties.isEmpty()) {
                log.warn("RECONCILIATION: No properties found for customer {} - checking historical_transactions.owner_id", customerId);
            }

            // STEP 2: Query using BOTH beneficiary_id AND historical_transactions.owner_id
            // This covers:
            // - Allocations where beneficiary_id is set to the customer
            // - Allocations linked to historical_transactions where owner_id matches
            // - Allocations for properties owned by this customer
            String paymentSql = """
                SELECT
                    ua.payment_batch_id as batch_id,
                    ua.paid_date as payment_date,
                    SUM(CASE WHEN ua.allocation_type = 'OWNER' THEN ua.amount ELSE 0 END) as total_income,
                    SUM(CASE WHEN ua.allocation_type IN ('EXPENSE', 'COMMISSION') THEN ABS(ua.amount) ELSE 0 END) as total_deductions,
                    MAX(pb.status) as status,
                    MAX(pb.payment_reference) as payment_reference
                FROM unified_allocations ua
                LEFT JOIN payment_batches pb ON ua.payment_batch_id = pb.batch_id
                LEFT JOIN historical_transactions ht ON ua.historical_transaction_id = ht.id
                LEFT JOIN customer_property_assignments cpa ON ua.property_id = cpa.property_id
                    AND cpa.assignment_type IN ('OWNER', 'MANAGER')
                WHERE ua.paid_date >= ?
                  AND ua.paid_date <= ?
                  AND ua.payment_batch_id IS NOT NULL
                  AND ua.allocation_type IN ('OWNER', 'EXPENSE', 'COMMISSION')
                  AND (
                      ua.beneficiary_id = ?
                      OR ht.owner_id = ?
                      OR cpa.customer_id = ?
                  )
                GROUP BY ua.payment_batch_id, ua.paid_date
                ORDER BY ua.paid_date, ua.payment_batch_id
            """;

            log.info("RECONCILIATION: Executing payment query with customerId={}, startDate={}, endDate={}",
                customerId, startDate, endDate);

            List<Map<String, Object>> paymentRows = jdbcTemplate.queryForList(paymentSql,
                startDate, endDate, customerId, customerId, customerId);
            log.info("RECONCILIATION: Found {} payment batches", paymentRows.size());

            for (Map<String, Object> paymentRow : paymentRows) {
                PaymentWithAllocationsDTO payment = new PaymentWithAllocationsDTO();
                String batchId = (String) paymentRow.get("batch_id");
                payment.setBatchId(batchId);

                Object paymentDateObj = paymentRow.get("payment_date");
                if (paymentDateObj != null) {
                    if (paymentDateObj instanceof java.sql.Date) {
                        payment.setPaymentDate(((java.sql.Date) paymentDateObj).toLocalDate());
                    } else if (paymentDateObj instanceof LocalDate) {
                        payment.setPaymentDate((LocalDate) paymentDateObj);
                    }
                }

                // Calculate net payment from income - deductions
                BigDecimal totalIncome = paymentRow.get("total_income") != null ?
                    new BigDecimal(paymentRow.get("total_income").toString()) : BigDecimal.ZERO;
                BigDecimal totalDeductions = paymentRow.get("total_deductions") != null ?
                    new BigDecimal(paymentRow.get("total_deductions").toString()) : BigDecimal.ZERO;
                payment.setTotalPayment(totalIncome.subtract(totalDeductions));

                String status = (String) paymentRow.get("status");
                payment.setStatus(status != null ? status : "PAID");
                payment.setPaymentReference((String) paymentRow.get("payment_reference"));

                log.info("RECONCILIATION: Processing batch {} - income={}, deductions={}, net={}",
                    batchId, totalIncome, totalDeductions, payment.getTotalPayment());

                // Get allocations for this batch
                String allocationSql = """
                    SELECT
                        ua.historical_transaction_id as transaction_id,
                        COALESCE(ht.transaction_date, ut.transaction_date) as transaction_date,
                        COALESCE(ua.property_name, p.property_name) as property_name,
                        COALESCE(ua.category, ht.category, ut.category) as category,
                        COALESCE(ua.description, ht.description, ut.description) as description,
                        ua.amount as allocated_amount,
                        ua.allocation_type,
                        COALESCE(ht.net_to_owner_amount, ht.amount, ut.net_amount) as transaction_total,
                        (SELECT COUNT(*) FROM unified_allocations ua2
                         WHERE ua2.historical_transaction_id = ua.historical_transaction_id
                         AND ua2.payment_batch_id IS NOT NULL) as allocation_count
                    FROM unified_allocations ua
                    LEFT JOIN historical_transactions ht ON ua.historical_transaction_id = ht.id
                    LEFT JOIN unified_transactions ut ON ua.unified_transaction_id = ut.id
                    LEFT JOIN properties p ON COALESCE(ht.property_id, ut.property_id) = p.id
                    WHERE ua.payment_batch_id = ?
                      AND ua.allocation_type IN ('OWNER', 'EXPENSE')
                    ORDER BY ua.allocation_type DESC, COALESCE(ht.transaction_date, ut.transaction_date), ua.property_name
                """;

                List<Map<String, Object>> allocationRows = jdbcTemplate.queryForList(allocationSql, batchId);
                BigDecimal allocatedIncome = BigDecimal.ZERO;
                BigDecimal allocatedExpenses = BigDecimal.ZERO;

                for (Map<String, Object> allocRow : allocationRows) {
                    PaymentWithAllocationsDTO.AllocationLineDTO line = new PaymentWithAllocationsDTO.AllocationLineDTO();
                    line.setTransactionId(allocRow.get("transaction_id") != null ?
                        ((Number) allocRow.get("transaction_id")).longValue() : null);

                    Object txnDateObj = allocRow.get("transaction_date");
                    if (txnDateObj != null) {
                        if (txnDateObj instanceof java.sql.Date) {
                            line.setTransactionDate(((java.sql.Date) txnDateObj).toLocalDate());
                        } else if (txnDateObj instanceof LocalDate) {
                            line.setTransactionDate((LocalDate) txnDateObj);
                        }
                    }

                    line.setPropertyName((String) allocRow.get("property_name"));
                    line.setCategory((String) allocRow.get("category"));
                    line.setDescription((String) allocRow.get("description"));
                    BigDecimal allocAmount = allocRow.get("allocated_amount") != null ?
                        new BigDecimal(allocRow.get("allocated_amount").toString()) : BigDecimal.ZERO;
                    line.setAllocatedAmount(allocAmount);

                    String allocType = (String) allocRow.get("allocation_type");
                    line.setAllocationType(allocType);

                    int allocationCount = allocRow.get("allocation_count") != null ?
                        ((Number) allocRow.get("allocation_count")).intValue() : 1;
                    line.setPartial(allocationCount > 1);

                    if (line.getTransactionDate() != null && periodStart != null) {
                        line.setFromPriorPeriod(line.getTransactionDate().isBefore(periodStart));
                    }

                    line.setBatchId(batchId);

                    if ("EXPENSE".equals(allocType)) {
                        allocatedExpenses = allocatedExpenses.add(allocAmount.abs());
                    } else {
                        allocatedIncome = allocatedIncome.add(allocAmount.abs());
                    }
                    payment.addAllocation(line);
                }

                BigDecimal totalAllocated = allocatedIncome.subtract(allocatedExpenses);
                payment.setTotalAllocated(totalAllocated);
                payment.setUnallocatedAmount(payment.getTotalPayment().subtract(totalAllocated));

                result.add(payment);
            }
        } catch (Exception e) {
            log.error("RECONCILIATION ERROR: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            e.printStackTrace();
        }

        log.info("RECONCILIATION: Found {} payments with allocations in period", result.size());
        return result;
    }

    /**
     * Extract payment batch summaries for properties within a date range.
     * Uses the repository method that queries unified_allocations joined with payment_batches.
     * This is a more reliable query that works with PayProp imported data.
     *
     * @param propertyIds List of property IDs to get payments for
     * @param startDate Period start date
     * @param endDate Period end date
     * @return List of payment batch summaries
     */
    public List<PaymentBatchSummaryDTO> extractPaymentBatchesByProperties(
            List<Long> propertyIds, LocalDate startDate, LocalDate endDate) {

        log.info("Extracting payment batches for {} properties from {} to {}", propertyIds.size(), startDate, endDate);

        List<PaymentBatchSummaryDTO> result = new ArrayList<>();

        if (propertyIds.isEmpty()) {
            log.warn("No property IDs provided - returning empty list");
            return result;
        }

        try {
            List<Object[]> rows = unifiedAllocationRepository.getPaymentBatchSummariesForPeriod(
                propertyIds, startDate, endDate);

            log.info("Found {} payment batches from repository query", rows.size());

            for (Object[] row : rows) {
                PaymentBatchSummaryDTO dto = new PaymentBatchSummaryDTO();
                dto.setBatchId(row[0] != null ? row[0].toString() : null);

                if (row[1] != null) {
                    if (row[1] instanceof java.sql.Date) {
                        dto.setPaymentDate(((java.sql.Date) row[1]).toLocalDate());
                    } else if (row[1] instanceof LocalDate) {
                        dto.setPaymentDate((LocalDate) row[1]);
                    }
                }

                dto.setBatchStatus(row[2] != null ? row[2].toString() : null);

                BigDecimal totalOwner = row[3] != null ? new BigDecimal(row[3].toString()) : BigDecimal.ZERO;
                BigDecimal totalExpense = row[4] != null ? new BigDecimal(row[4].toString()) : BigDecimal.ZERO;
                BigDecimal totalCommission = row[5] != null ? new BigDecimal(row[5].toString()) : BigDecimal.ZERO;
                BigDecimal netPayment = row[6] != null ? new BigDecimal(row[6].toString()) : BigDecimal.ZERO;

                dto.setTotalOwnerAllocations(totalOwner);
                dto.setTotalExpenseAllocations(totalExpense);
                dto.setTotalCommissionAllocations(totalCommission);
                dto.setNetPayment(netPayment);

                result.add(dto);

                log.info("Payment batch {} on {}: owner={}, expense={}, commission={}, net={}",
                    dto.getBatchId(), dto.getPaymentDate(), totalOwner, totalExpense, totalCommission, netPayment);
            }
        } catch (Exception e) {
            log.error("Error extracting payment batches: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            e.printStackTrace();
        }

        log.info("Extracted {} payment batch summaries", result.size());
        return result;
    }

    /**
     * Extract unallocated income as of end of period.
     * Returns income transactions received up to and including endDate that are not fully allocated.
     * Used for "Carried Forward" section of reconciliation.
     */
    public List<UnallocatedIncomeDTO> extractUnallocatedIncomeAsOfEndDate(Long customerId, LocalDate endDate) {
        log.info("Extracting unallocated income for customer {} as of end of {}", customerId, endDate);

        List<UnallocatedIncomeDTO> result = new ArrayList<>();

        String sql = """
            SELECT
                ht.id as transaction_id,
                ht.transaction_date,
                ht.property_id,
                p.property_name,
                ht.lease_reference,
                ht.description as tenant_name,
                ht.category,
                ht.amount as gross_amount,
                COALESCE(ht.net_to_owner_amount, ht.amount * 0.85) as net_due,
                COALESCE(SUM(tba.allocated_amount), 0) as allocated_amount
            FROM historical_transactions ht
            LEFT JOIN properties p ON ht.property_id = p.id
            LEFT JOIN transaction_batch_allocations tba ON ht.id = tba.transaction_id
            WHERE ht.owner_id = ?
              AND ht.transaction_date <= ?
              AND ht.category = 'rent'
            GROUP BY ht.id, ht.transaction_date, ht.property_id, p.property_name,
                     ht.lease_reference, ht.description, ht.category, ht.amount, ht.net_to_owner_amount
            HAVING COALESCE(ht.net_to_owner_amount, ht.amount * 0.85) - COALESCE(SUM(tba.allocated_amount), 0) > 0.01
            ORDER BY ht.transaction_date, p.property_name
        """;

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, customerId, endDate);

            for (Map<String, Object> row : rows) {
                UnallocatedIncomeDTO dto = new UnallocatedIncomeDTO();
                dto.setTransactionId(((Number) row.get("transaction_id")).longValue());
                dto.setTransactionDate(row.get("transaction_date") != null ?
                    ((java.sql.Date) row.get("transaction_date")).toLocalDate() : null);
                dto.setPropertyId(row.get("property_id") != null ?
                    ((Number) row.get("property_id")).longValue() : null);
                dto.setPropertyName((String) row.get("property_name"));
                dto.setLeaseReference((String) row.get("lease_reference"));
                dto.setTenantName((String) row.get("tenant_name"));
                dto.setCategory((String) row.get("category"));
                dto.setGrossAmount(row.get("gross_amount") != null ?
                    new BigDecimal(row.get("gross_amount").toString()) : BigDecimal.ZERO);
                dto.setNetDue(row.get("net_due") != null ?
                    new BigDecimal(row.get("net_due").toString()) : BigDecimal.ZERO);
                dto.setAllocatedAmount(row.get("allocated_amount") != null ?
                    new BigDecimal(row.get("allocated_amount").toString()) : BigDecimal.ZERO);
                dto.setRemainingUnallocated(dto.getNetDue().subtract(dto.getAllocatedAmount()));
                dto.setCommission(dto.getGrossAmount().subtract(dto.getNetDue()));

                result.add(dto);
            }
        } catch (Exception e) {
            log.warn("Error extracting unallocated income at end: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }

        log.info("Found {} unallocated income transactions at end of period", result.size());
        return result;
    }

    /**
     * Extract unallocated payments as of end of period.
     * Returns payments made up to and including endDate where payment exceeds allocations.
     * Used for "Carried Forward - Owner Credit" section of reconciliation.
     */
    public List<UnallocatedPaymentDTO> extractUnallocatedPaymentsAsOfEndDate(Long customerId, LocalDate endDate) {
        log.info("Extracting unallocated payments for customer {} as of end of {}", customerId, endDate);

        List<UnallocatedPaymentDTO> result = new ArrayList<>();

        String sql = """
            SELECT
                pb.batch_id,
                pb.payment_date,
                pb.total_payment,
                COALESCE(SUM(ABS(tba.allocated_amount)), 0) as total_allocated,
                pb.status,
                pb.payment_reference
            FROM payment_batches pb
            LEFT JOIN transaction_batch_allocations tba ON pb.batch_id = tba.batch_reference
            WHERE pb.beneficiary_id = ?
              AND pb.payment_date <= ?
              AND pb.batch_type = 'OWNER_PAYMENT'
            GROUP BY pb.batch_id, pb.payment_date, pb.total_payment, pb.status, pb.payment_reference
            HAVING pb.total_payment - COALESCE(SUM(ABS(tba.allocated_amount)), 0) > 0.01
            ORDER BY pb.payment_date, pb.batch_id
        """;

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, customerId, endDate);

            for (Map<String, Object> row : rows) {
                UnallocatedPaymentDTO dto = new UnallocatedPaymentDTO();
                dto.setBatchId((String) row.get("batch_id"));
                dto.setPaymentDate(row.get("payment_date") != null ?
                    ((java.sql.Date) row.get("payment_date")).toLocalDate() : null);
                dto.setTotalPayment(row.get("total_payment") != null ?
                    new BigDecimal(row.get("total_payment").toString()) : BigDecimal.ZERO);
                dto.setTotalAllocated(row.get("total_allocated") != null ?
                    new BigDecimal(row.get("total_allocated").toString()) : BigDecimal.ZERO);
                dto.setUnallocatedAmount(dto.getTotalPayment().subtract(dto.getTotalAllocated()));
                dto.setStatus((String) row.get("status"));
                dto.setPaymentReference((String) row.get("payment_reference"));

                result.add(dto);
            }
        } catch (Exception e) {
            log.warn("Error extracting unallocated payments at end: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }

        log.info("Found {} unallocated payments at end of period", result.size());
        return result;
    }

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    // ===== BATCH-GROUPED PAYMENT EXTRACTION =====

    /**
     * Extract rent payments grouped by batch for a lease.
     * Returns only payments that have been assigned to a batch (excludes pending).
     * Filters by owner_payment_date (paidDate) within the given period.
     *
     * @param leaseId The lease/invoice ID
     * @param periodStart Start of period (filters by paidDate, not transactionDate)
     * @param periodEnd End of period (filters by paidDate, not transactionDate)
     * @return Map of batchId -> list of payments in that batch
     */
    public Map<String, List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO>> extractRentReceivedByBatch(
            Long leaseId, LocalDate periodStart, LocalDate periodEnd) {

        log.info("Extracting rent received by batch for lease {} from {} to {} (by paidDate)",
            leaseId, periodStart, periodEnd);

        // Get all payments for this lease (no date filter on transaction date)
        // We'll filter by paidDate after populating batch info
        List<UnifiedTransaction> transactions = unifiedTransactionRepository
            .findByInvoiceIdAndFlowDirection(leaseId, UnifiedTransaction.FlowDirection.INCOMING);

        List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO> allPayments = new ArrayList<>();

        for (UnifiedTransaction txn : transactions) {
            site.easy.to.build.crm.dto.statement.PaymentDetailDTO detail =
                new site.easy.to.build.crm.dto.statement.PaymentDetailDTO();

            detail.setPaymentDate(txn.getTransactionDate());
            detail.setAmount(txn.getAmount());
            detail.setDescription(txn.getDescription());
            detail.setCategory(txn.getCategory());
            detail.setTransactionId(txn.getId());

            allPayments.add(detail);
        }

        // Populate batch info from unified_allocations
        populateBatchInfo(allPayments);

        // Group by batch, filtering by paidDate and excluding unbatched
        Map<String, List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO>> result = new HashMap<>();

        for (site.easy.to.build.crm.dto.statement.PaymentDetailDTO payment : allPayments) {
            String batchId = payment.getBatchId();
            LocalDate paidDate = payment.getPaidDate();

            // Skip payments without batch (pending) or outside date range
            if (batchId == null || batchId.isEmpty()) {
                continue;
            }
            if (paidDate == null) {
                continue;
            }
            if (paidDate.isBefore(periodStart) || paidDate.isAfter(periodEnd)) {
                continue;
            }

            result.computeIfAbsent(batchId, k -> new ArrayList<>()).add(payment);
        }

        // Sort payments within each batch by payment date
        for (List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO> payments : result.values()) {
            payments.sort((a, b) -> a.getPaymentDate().compareTo(b.getPaymentDate()));
        }

        log.info("Extracted {} batches with payments for lease {}", result.size(), leaseId);
        return result;
    }

    /**
     * Extract expenses grouped by batch for a lease.
     * Returns only expenses that have been assigned to a batch (excludes pending).
     * Filters by owner_payment_date (paidDate) within the given period.
     *
     * @param leaseId The lease/invoice ID
     * @param periodStart Start of period (filters by paidDate, not transactionDate)
     * @param periodEnd End of period (filters by paidDate, not transactionDate)
     * @return Map of batchId -> list of expenses in that batch
     */
    public Map<String, List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO>> extractExpensesByBatch(
            Long leaseId, LocalDate periodStart, LocalDate periodEnd) {

        log.info("Extracting expenses by batch for lease {} from {} to {} (by paidDate)",
            leaseId, periodStart, periodEnd);

        // Get all outgoing transactions for this lease
        List<UnifiedTransaction> transactions = unifiedTransactionRepository
            .findByInvoiceIdAndFlowDirection(leaseId, UnifiedTransaction.FlowDirection.OUTGOING);

        List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO> allExpenses = new ArrayList<>();

        for (UnifiedTransaction txn : transactions) {
            // Filter out Owner and Commission categories - these are NOT expenses
            String category = txn.getCategory();
            if (category != null) {
                String lowerCategory = category.toLowerCase();
                if (lowerCategory.equals("owner") || lowerCategory.equals("commission") ||
                    lowerCategory.contains("owner_payment")) {
                    continue;
                }
            }

            site.easy.to.build.crm.dto.statement.PaymentDetailDTO detail =
                new site.easy.to.build.crm.dto.statement.PaymentDetailDTO();

            detail.setPaymentDate(txn.getTransactionDate());
            detail.setAmount(txn.getAmount());
            detail.setDescription(txn.getDescription());
            detail.setCategory(txn.getCategory());
            detail.setTransactionId(txn.getId());

            allExpenses.add(detail);
        }

        // Populate batch info from unified_allocations
        populateBatchInfo(allExpenses);

        // Group by batch, filtering by paidDate and excluding unbatched
        Map<String, List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO>> result = new HashMap<>();

        for (site.easy.to.build.crm.dto.statement.PaymentDetailDTO expense : allExpenses) {
            String batchId = expense.getBatchId();
            LocalDate paidDate = expense.getPaidDate();

            // Skip expenses without batch (pending) or outside date range
            if (batchId == null || batchId.isEmpty()) {
                continue;
            }
            if (paidDate == null) {
                continue;
            }
            if (paidDate.isBefore(periodStart) || paidDate.isAfter(periodEnd)) {
                continue;
            }

            result.computeIfAbsent(batchId, k -> new ArrayList<>()).add(expense);
        }

        // Sort expenses within each batch by expense date
        for (List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO> expenses : result.values()) {
            expenses.sort((a, b) -> a.getPaymentDate().compareTo(b.getPaymentDate()));
        }

        log.info("Extracted {} batches with expenses for lease {}", result.size(), leaseId);
        return result;
    }

    /**
     * Extract batch payment groups for a lease - combines rent, expenses, and commission.
     * Returns one BatchPaymentGroupDTO per batch per lease.
     * Filters by owner_payment_date (paidDate) within the given period.
     *
     * @param lease The lease master DTO (includes commission rate)
     * @param periodStart Start of period (filters by paidDate)
     * @param periodEnd End of period (filters by paidDate)
     * @return List of batch payment groups, sorted by owner payment date
     */
    public List<site.easy.to.build.crm.dto.statement.BatchPaymentGroupDTO> extractBatchPaymentGroups(
            LeaseMasterDTO lease, LocalDate periodStart, LocalDate periodEnd) {

        log.info("Extracting batch payment groups for lease {} from {} to {}",
            lease.getLeaseReference(), periodStart, periodEnd);

        // Get rent payments grouped by batch
        Map<String, List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO>> rentByBatch =
            extractRentReceivedByBatch(lease.getLeaseId(), periodStart, periodEnd);

        // Get expenses grouped by batch
        Map<String, List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO>> expensesByBatch =
            extractExpensesByBatch(lease.getLeaseId(), periodStart, periodEnd);

        // Collect all unique batch IDs
        java.util.Set<String> allBatchIds = new java.util.HashSet<>();
        allBatchIds.addAll(rentByBatch.keySet());
        allBatchIds.addAll(expensesByBatch.keySet());

        // Build batch payment groups
        List<site.easy.to.build.crm.dto.statement.BatchPaymentGroupDTO> result = new ArrayList<>();

        for (String batchId : allBatchIds) {
            site.easy.to.build.crm.dto.statement.BatchPaymentGroupDTO group =
                new site.easy.to.build.crm.dto.statement.BatchPaymentGroupDTO();

            group.setLeaseId(lease.getLeaseId());
            group.setLeaseReference(lease.getLeaseReference());
            group.setPropertyName(lease.getPropertyName());
            group.setBatchId(batchId);
            group.setCommissionRate(lease.getCommissionPercentage() != null ?
                lease.getCommissionPercentage().divide(new BigDecimal("100")) : null);

            // Add rent payments (up to 4)
            List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO> rentPayments =
                rentByBatch.getOrDefault(batchId, new ArrayList<>());
            LocalDate ownerPaymentDate = null;
            for (site.easy.to.build.crm.dto.statement.PaymentDetailDTO rent : rentPayments) {
                group.addRentPayment(rent);
                // Use the paidDate from the first payment as the owner payment date
                if (ownerPaymentDate == null && rent.getPaidDate() != null) {
                    ownerPaymentDate = rent.getPaidDate();
                }
            }

            // Add expenses (up to 4)
            List<site.easy.to.build.crm.dto.statement.PaymentDetailDTO> expenses =
                expensesByBatch.getOrDefault(batchId, new ArrayList<>());
            for (site.easy.to.build.crm.dto.statement.PaymentDetailDTO expense : expenses) {
                group.addExpense(expense);
                // If no owner payment date from rent, use expense paid date
                if (ownerPaymentDate == null && expense.getPaidDate() != null) {
                    ownerPaymentDate = expense.getPaidDate();
                }
            }

            group.setOwnerPaymentDate(ownerPaymentDate);
            group.calculateTotals();

            result.add(group);
        }

        // Sort by owner payment date
        result.sort((a, b) -> {
            if (a.getOwnerPaymentDate() == null && b.getOwnerPaymentDate() == null) return 0;
            if (a.getOwnerPaymentDate() == null) return 1;
            if (b.getOwnerPaymentDate() == null) return -1;
            return a.getOwnerPaymentDate().compareTo(b.getOwnerPaymentDate());
        });

        log.info("Extracted {} batch payment groups for lease {}", result.size(), lease.getLeaseReference());
        return result;
    }
}

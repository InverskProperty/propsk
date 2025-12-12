package site.easy.to.build.crm.service.statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.dto.statement.CustomerDTO;
import site.easy.to.build.crm.dto.statement.LeaseAllocationSummaryDTO;
import site.easy.to.build.crm.dto.statement.LeaseMasterDTO;
import site.easy.to.build.crm.dto.statement.PaymentBatchSummaryDTO;
import site.easy.to.build.crm.dto.statement.PropertyDTO;
import site.easy.to.build.crm.dto.statement.RelatedPaymentDTO;
import site.easy.to.build.crm.dto.statement.TransactionDTO;
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
     * Extract batch references by property and allocation type.
     * Returns a nested map: propertyId -> allocationType -> batchIds (comma-separated)
     * Used to populate batch reference columns on monthly statement rows.
     */
    public Map<Long, Map<String, String>> extractBatchRefsByPropertyAndType(
            List<Long> propertyIds, LocalDate startDate, LocalDate endDate) {

        log.info("Extracting batch refs for {} properties from {} to {}",
            propertyIds.size(), startDate, endDate);

        Map<Long, Map<String, String>> result = new HashMap<>();

        if (propertyIds == null || propertyIds.isEmpty()) {
            return result;
        }

        try {
            List<Object[]> rows = unifiedAllocationRepository.getBatchRefsByPropertyAndType(
                propertyIds, startDate, endDate);

            if (rows != null) {
                for (Object[] row : rows) {
                    Long propertyId = row[0] != null ? ((Number) row[0]).longValue() : null;
                    String allocationType = row[1] != null ? row[1].toString() : null;
                    String batchIds = row[2] != null ? row[2].toString() : "";

                    if (propertyId != null && allocationType != null) {
                        result.computeIfAbsent(propertyId, k -> new HashMap<>())
                              .put(allocationType, batchIds);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error fetching batch refs: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }

        log.info("Extracted batch refs for {} properties", result.size());
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
}

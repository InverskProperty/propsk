package site.easy.to.build.crm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import site.easy.to.build.crm.entity.*;
import site.easy.to.build.crm.entity.PaymentBatch.BatchType;
import site.easy.to.build.crm.entity.PaymentBatch.AdjustmentSource;
import site.easy.to.build.crm.entity.UnifiedAllocation.AllocationType;
import site.easy.to.build.crm.entity.UnifiedAllocation.AllocationSource;
import site.easy.to.build.crm.repository.*;
import site.easy.to.build.crm.service.payment.PaymentBatchService;
import site.easy.to.build.crm.service.payment.UnifiedAllocationService;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.payment.TransactionBatchAllocationService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Controller for Owner Payment Management UI
 *
 * Provides views for:
 * - Viewing transactions eligible for owner payment
 * - Creating single owner payments
 * - Creating batch owner payments
 * - Adding block balance contributions
 * - Managing payment status
 */
@Controller
@RequestMapping("/owner-payments")
public class OwnerPaymentController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private HistoricalTransactionRepository transactionRepository;

    @Autowired
    private UnifiedAllocationService allocationService;

    @Autowired
    private PaymentBatchService batchService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private UnifiedAllocationRepository allocationRepository;

    @Autowired
    private PaymentBatchRepository batchRepository;

    @Autowired
    private TransactionBatchAllocationService transactionAllocationService;

    // ===== MAIN DASHBOARD =====

    /**
     * Owner payments dashboard
     * Shows summary and quick actions
     */
    @GetMapping("")
    public String dashboard(Model model) {
        // Get summary statistics
        BigDecimal pendingTotal = allocationService.getTotalPendingOwnerPayments();
        long pendingCount = allocationService.getPendingAllocationCount();
        List<PaymentBatch> pendingBatches = batchService.getPendingBatches();

        model.addAttribute("pendingTotal", pendingTotal);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("pendingBatches", pendingBatches);

        // Get recent transactions without allocations (for quick payment creation)
        List<Map<String, Object>> unallocatedTransactions = getUnallocatedOwnerTransactions(20);
        model.addAttribute("unallocatedTransactions", unallocatedTransactions);

        return "owner-payments/dashboard";
    }

    // ===== NEW SIMPLIFIED ALLOCATION UI =====

    /**
     * New simplified transaction allocation UI
     * Uses TransactionBatchAllocation system with net_to_owner amounts
     * Supports both "Transactions First" and "Payment First" workflows
     */
    @GetMapping("/allocations")
    public String allocationsUI(Model model) {
        // Get owners for filter dropdown
        List<Customer> owners = customerRepository.findByCustomerType(CustomerType.PROPERTY_OWNER);
        model.addAttribute("owners", owners);

        // Get properties for filter dropdown (all properties with owners)
        List<Property> properties = propertyRepository.findAll();
        model.addAttribute("properties", properties);

        // Get blocks for filtering block properties
        List<Block> blocks = blockRepository.findAll();
        model.addAttribute("blocks", blocks);

        // Summary statistics using new allocation service
        BigDecimal totalUnallocated = BigDecimal.ZERO;
        int unallocatedCount = 0;
        for (Customer owner : owners) {
            BigDecimal ownerUnallocated = transactionAllocationService.getTotalUnallocatedForOwner(owner.getCustomerId());
            if (ownerUnallocated != null) {
                totalUnallocated = totalUnallocated.add(ownerUnallocated);
            }
            List<TransactionBatchAllocationService.UnallocatedTransactionDTO> txns =
                transactionAllocationService.getUnallocatedTransactionsForOwner(owner.getCustomerId());
            if (txns != null) {
                unallocatedCount += txns.size();
            }
        }
        model.addAttribute("totalUnallocated", totalUnallocated);
        model.addAttribute("unallocatedCount", unallocatedCount);

        return "owner-payments/index";
    }

    // ===== TRANSACTION SELECTION =====

    /**
     * View transactions for creating owner payments
     * Can filter by owner, property, date range
     */
    @GetMapping("/transactions")
    public String viewTransactions(
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) String status,
            Model model) {

        // Get owners for filter dropdown
        List<Customer> owners = customerRepository.findByCustomerType(CustomerType.PROPERTY_OWNER);
        model.addAttribute("owners", owners);

        // Get properties for filter dropdown
        List<Property> properties = propertyRepository.findAll();
        model.addAttribute("properties", properties);

        // Build query based on filters
        List<Map<String, Object>> transactions = getFilteredTransactions(ownerId, propertyId, fromDate, toDate, status);
        model.addAttribute("transactions", transactions);

        // Pass filter values back to view
        model.addAttribute("selectedOwnerId", ownerId);
        model.addAttribute("selectedPropertyId", propertyId);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("status", status);

        return "owner-payments/transactions";
    }

    // ===== SINGLE PAYMENT CREATION =====

    /**
     * Create a single owner payment from a transaction
     */
    @PostMapping("/create-single")
    public String createSinglePayment(
            @RequestParam Long transactionId,
            @RequestParam(required = false) LocalDate paymentDate,
            @RequestParam(required = false) String paymentReference,
            RedirectAttributes redirectAttributes) {

        try {
            // Get transaction
            HistoricalTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));

            // Create allocation if not exists
            UnifiedAllocation allocation = allocationService.createOwnerAllocationFromTransaction(
                transaction, transaction.getCustomer(), AllocationSource.MANUAL);

            // Create and immediately mark as paid
            LocalDate date = paymentDate != null ? paymentDate : LocalDate.now();
            PaymentBatch batch = batchService.createSingleAllocationBatch(allocation.getId(), date);

            if (paymentReference != null && !paymentReference.isEmpty()) {
                batchService.markBatchAsPaid(batch.getBatchId(), date, paymentReference);
            }

            redirectAttributes.addFlashAttribute("success",
                "Owner payment created: " + batch.getBatchId() + " for " + batch.getTotalPayment());

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create payment: " + e.getMessage());
        }

        return "redirect:/owner-payments/transactions";
    }

    // ===== BATCH PAYMENT CREATION =====

    /**
     * Show batch creation form with selected transactions
     */
    @GetMapping("/create-batch")
    public String showBatchForm(
            @RequestParam(required = false) List<Long> transactionIds,
            @RequestParam(required = false) Long ownerId,
            Model model) {

        // If specific transactions selected, get them
        if (transactionIds != null && !transactionIds.isEmpty()) {
            List<HistoricalTransaction> transactions = transactionRepository.findAllById(transactionIds);
            model.addAttribute("selectedTransactions", transactions);

            // Calculate totals
            BigDecimal total = transactions.stream()
                .map(t -> t.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            model.addAttribute("totalAmount", total);

            // Get owner from first transaction
            if (!transactions.isEmpty() && transactions.get(0).getCustomer() != null) {
                model.addAttribute("owner", transactions.get(0).getCustomer());
            }
        }

        // If owner specified, get their pending allocations
        if (ownerId != null) {
            Customer owner = customerRepository.findById(ownerId).orElse(null);
            model.addAttribute("owner", owner);

            List<UnifiedAllocation> pendingAllocations = allocationService.getPendingAllocationsForBeneficiary(ownerId);
            model.addAttribute("pendingAllocations", pendingAllocations);

            // Calculate net pending: Income (OWNER) - Expenses (others)
            BigDecimal pendingTotal = pendingAllocations.stream()
                .map(a -> {
                    if (a.getAllocationType() == UnifiedAllocation.AllocationType.OWNER) {
                        return a.getAmount(); // Add income
                    } else {
                        return a.getAmount().negate(); // Subtract expenses
                    }
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            model.addAttribute("pendingTotal", pendingTotal);
        }

        // Get blocks for balance contribution dropdown
        List<Block> blocks = blockRepository.findAll();
        model.addAttribute("blocks", blocks);

        return "owner-payments/create-batch";
    }

    /**
     * Create batch payment from form submission
     */
    @PostMapping("/create-batch")
    public String createBatchPayment(
            @RequestParam List<Long> allocationIds,
            @RequestParam Long ownerId,
            @RequestParam LocalDate paymentDate,
            @RequestParam(required = false) BigDecimal balanceContribution,
            @RequestParam(required = false) Long blockId,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) String paymentReference,
            @RequestParam(defaultValue = "false") boolean markAsPaid,
            RedirectAttributes redirectAttributes) {

        try {
            // Get owner
            Customer owner = customerRepository.findById(ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Owner not found"));

            // Create batch
            PaymentBatch batch = batchService.createBatch(
                allocationIds,
                BatchType.OWNER_PAYMENT,
                paymentDate,
                ownerId,
                owner.getName()
            );

            // Add balance contribution if specified
            if (balanceContribution != null && balanceContribution.compareTo(BigDecimal.ZERO) != 0) {
                AdjustmentSource source = blockId != null ? AdjustmentSource.BLOCK : AdjustmentSource.OWNER_BALANCE;
                String adjustmentNotes = notes != null ? notes : "Balance contribution";
                if (blockId != null) {
                    Block block = blockRepository.findById(blockId).orElse(null);
                    if (block != null) {
                        adjustmentNotes = "Contribution from " + block.getName() + " block account";
                    }
                }
                batchService.addBalanceAdjustment(batch.getBatchId(), balanceContribution, source, adjustmentNotes);
            }

            // Mark as pending
            batchService.markBatchAsPending(batch.getBatchId());

            // Optionally mark as paid
            if (markAsPaid) {
                batchService.markBatchAsPaid(batch.getBatchId(), paymentDate, paymentReference);
                redirectAttributes.addFlashAttribute("success",
                    "Batch payment " + batch.getBatchId() + " created and marked as paid: " + batch.getTotalPayment());
            } else {
                redirectAttributes.addFlashAttribute("success",
                    "Batch payment " + batch.getBatchId() + " created: " + batch.getTotalPayment());
            }

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create batch: " + e.getMessage());
        }

        return "redirect:/owner-payments";
    }

    // ===== BATCH MANAGEMENT =====

    /**
     * View batch details
     */
    @GetMapping("/batch/{batchId}")
    public String viewBatch(@PathVariable String batchId, Model model) {
        PaymentBatch batch = batchRepository.findByBatchId(batchId)
            .orElseThrow(() -> new IllegalArgumentException("Batch not found"));

        List<UnifiedAllocation> allocations = allocationRepository.findByPaymentBatchId(batchId);

        model.addAttribute("batch", batch);
        model.addAttribute("allocations", allocations);

        return "owner-payments/batch-details";
    }

    /**
     * Mark batch as paid
     */
    @PostMapping("/batch/{batchId}/pay")
    public String markBatchPaid(
            @PathVariable String batchId,
            @RequestParam LocalDate paidDate,
            @RequestParam(required = false) String paymentReference,
            RedirectAttributes redirectAttributes) {

        try {
            batchService.markBatchAsPaid(batchId, paidDate, paymentReference);
            redirectAttributes.addFlashAttribute("success", "Batch " + batchId + " marked as paid");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to mark as paid: " + e.getMessage());
        }

        return "redirect:/owner-payments/batch/" + batchId;
    }

    // ===== ALLOCATION CREATION FROM TRANSACTIONS =====

    /**
     * Create allocations from selected transactions
     */
    @PostMapping("/create-allocations")
    public String createAllocationsFromTransactions(
            @RequestParam List<Long> transactionIds,
            RedirectAttributes redirectAttributes) {

        try {
            int created = 0;
            for (Long txnId : transactionIds) {
                HistoricalTransaction txn = transactionRepository.findById(txnId).orElse(null);
                if (txn != null && txn.getCustomer() != null) {
                    allocationService.createOwnerAllocationFromTransaction(txn, txn.getCustomer(), AllocationSource.MANUAL);
                    created++;
                }
            }
            redirectAttributes.addFlashAttribute("success", "Created " + created + " allocations");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create allocations: " + e.getMessage());
        }

        return "redirect:/owner-payments/transactions";
    }

    // ===== HELPER METHODS =====

    private List<Map<String, Object>> getUnallocatedOwnerTransactions(int limit) {
        String sql = """
            SELECT ht.id, ht.transaction_date, ht.amount, ht.description,
                   p.name as property_name, c.name as owner_name
            FROM historical_transactions ht
            LEFT JOIN properties p ON ht.property_id = p.id
            LEFT JOIN customers c ON ht.customer_id = c.id
            LEFT JOIN unified_allocations ua ON ua.historical_transaction_id = ht.id
            WHERE ht.category IN ('owner_allocation', 'owner_payment', 'rent_income')
            AND ht.beneficiary_type IN ('beneficiary', 'owner')
            AND ua.id IS NULL
            ORDER BY ht.transaction_date DESC
            LIMIT ?
            """;
        return jdbcTemplate.queryForList(sql, limit);
    }

    private List<Map<String, Object>> getFilteredTransactions(
            Long ownerId, Long propertyId, String fromDate, String toDate, String status) {

        StringBuilder sql = new StringBuilder();
        sql.append("""
            SELECT ht.id, ht.transaction_date, ht.amount, ht.description, ht.category,
                   ht.payprop_batch_id,
                   p.id as property_id, p.name as property_name,
                   c.id as owner_id, c.name as owner_name,
                   ua.id as allocation_id, ua.payment_status, ua.payment_batch_id
            FROM historical_transactions ht
            LEFT JOIN properties p ON ht.property_id = p.id
            LEFT JOIN customers c ON ht.customer_id = c.id
            LEFT JOIN unified_allocations ua ON ua.historical_transaction_id = ht.id
            WHERE ht.category IN ('owner_allocation', 'owner_payment', 'rent_income', 'payment')
            AND ht.beneficiary_type IN ('beneficiary', 'beneficiary_payment', 'owner')
            """);

        List<Object> params = new ArrayList<>();

        if (ownerId != null) {
            sql.append(" AND ht.customer_id = ?");
            params.add(ownerId);
        }
        if (propertyId != null) {
            sql.append(" AND ht.property_id = ?");
            params.add(propertyId);
        }
        if (fromDate != null && !fromDate.isEmpty()) {
            sql.append(" AND ht.transaction_date >= ?");
            params.add(LocalDate.parse(fromDate));
        }
        if (toDate != null && !toDate.isEmpty()) {
            sql.append(" AND ht.transaction_date <= ?");
            params.add(LocalDate.parse(toDate));
        }
        if (status != null && !status.isEmpty()) {
            switch (status) {
                case "unallocated":
                    sql.append(" AND ua.id IS NULL");
                    break;
                case "pending":
                    sql.append(" AND ua.payment_status = 'PENDING'");
                    break;
                case "batched":
                    sql.append(" AND ua.payment_status = 'BATCHED'");
                    break;
                case "paid":
                    sql.append(" AND ua.payment_status = 'PAID'");
                    break;
            }
        }

        sql.append(" ORDER BY ht.transaction_date DESC LIMIT 200");

        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }
}

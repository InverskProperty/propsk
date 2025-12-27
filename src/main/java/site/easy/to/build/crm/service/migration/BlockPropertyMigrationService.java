package site.easy.to.build.crm.service.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One-time migration service to restructure block property expenses.
 *
 * This service:
 * 1. Moves small expenses back to their original historical-only flats
 * 2. Reassigns expenses from PayProp flats to historical-only flats
 * 3. Splits large expenses across multiple months/flats with matching Prestvale deposits
 *
 * Run once to clean up block property 69 for service charge accounting.
 */
@Service
public class BlockPropertyMigrationService {

    private static final Logger log = LoggerFactory.getLogger(BlockPropertyMigrationService.class);

    private static final Long BLOCK_PROPERTY_ID = 69L;
    private static final Long BLOCK_LEASE_ID = 86L;
    private static final BigDecimal EXPENSE_THRESHOLD = new BigDecimal("500.00");
    private static final Pattern MOVED_FROM_PATTERN = Pattern.compile("\\[Moved from (.+?)\\]");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * DTO to hold block expense details
     */
    public static class BlockExpense {
        public Long id;
        public LocalDate paidDate;
        public BigDecimal amount;
        public String description;
        public String paymentBatchId;
        public String source;
        public String originalFlatName; // Parsed from description
        public Long originalFlatId;
        public boolean originalFlatHasPayProp;
        public int ruleToApply; // 1, 2, or 3
    }

    /**
     * DTO to hold available income by flat and month
     */
    public static class FlatMonthIncome {
        public Long flatId;
        public String flatName;
        public YearMonth month;
        public BigDecimal totalIncome;
        public BigDecimal usedIncome;

        public BigDecimal getAvailable() {
            return totalIncome.subtract(usedIncome);
        }
    }

    /**
     * Analyze all block expenses and determine what actions are needed.
     * This is a DRY RUN - no changes are made.
     *
     * @return Summary of what would be changed
     */
    public String analyzeBlockExpenses() {
        log.info("=== ANALYZING BLOCK PROPERTY EXPENSES (DRY RUN) ===");

        StringBuilder report = new StringBuilder();
        report.append("=== BLOCK PROPERTY MIGRATION ANALYSIS ===\n\n");

        // Step 1: Get all MANUAL expenses on block property
        List<BlockExpense> expenses = getManualBlockExpenses();
        report.append("Total MANUAL expenses on block: ").append(expenses.size()).append("\n\n");

        // Step 2: Build income availability map
        Map<String, FlatMonthIncome> incomeMap = buildIncomeAvailabilityMap();
        report.append("Historical-only flats with income:\n");
        incomeMap.values().stream()
            .sorted(Comparator.comparing((FlatMonthIncome f) -> f.flatName).thenComparing(f -> f.month))
            .forEach(f -> report.append(String.format("  %s %s: £%.2f available\n",
                f.flatName, f.month, f.getAvailable())));
        report.append("\n");

        // Step 3: Categorize each expense
        int rule1Count = 0, rule2Count = 0, rule3Count = 0;
        BigDecimal rule1Total = BigDecimal.ZERO, rule2Total = BigDecimal.ZERO, rule3Total = BigDecimal.ZERO;

        report.append("=== EXPENSE CATEGORIZATION ===\n\n");

        for (BlockExpense expense : expenses) {
            categorizeExpense(expense);

            String action;
            switch (expense.ruleToApply) {
                case 1:
                    rule1Count++;
                    rule1Total = rule1Total.add(expense.amount);
                    action = String.format("RULE 1: Move back to %s", expense.originalFlatName);
                    break;
                case 2:
                    rule2Count++;
                    rule2Total = rule2Total.add(expense.amount);
                    action = String.format("RULE 2: Reassign to historical flat (original %s has PayProp)",
                        expense.originalFlatName);
                    break;
                case 3:
                    rule3Count++;
                    rule3Total = rule3Total.add(expense.amount);
                    action = "RULE 3: Split across months with Prestvale deposits";
                    break;
                default:
                    action = "UNKNOWN";
            }

            report.append(String.format("ID %d: £%.2f - %s\n  Description: %s\n  Action: %s\n\n",
                expense.id, expense.amount, expense.paidDate,
                expense.description != null ? expense.description.substring(0, Math.min(50, expense.description.length())) : "N/A",
                action));
        }

        report.append("=== SUMMARY ===\n");
        report.append(String.format("Rule 1 (move back): %d expenses, £%.2f\n", rule1Count, rule1Total));
        report.append(String.format("Rule 2 (reassign): %d expenses, £%.2f\n", rule2Count, rule2Total));
        report.append(String.format("Rule 3 (split): %d expenses, £%.2f\n", rule3Count, rule3Total));
        report.append(String.format("TOTAL: %d expenses, £%.2f\n",
            rule1Count + rule2Count + rule3Count,
            rule1Total.add(rule2Total).add(rule3Total)));

        log.info(report.toString());
        return report.toString();
    }

    /**
     * Execute the migration (LIVE RUN).
     *
     * @param dryRun If true, only analyze and report. If false, actually make changes.
     * @return Summary of changes made
     */
    @Transactional
    public String executeMigration(boolean dryRun) {
        if (dryRun) {
            return analyzeBlockExpenses();
        }

        log.info("=== EXECUTING BLOCK PROPERTY MIGRATION ===");

        StringBuilder report = new StringBuilder();
        report.append("=== BLOCK PROPERTY MIGRATION EXECUTION ===\n\n");

        // Get expenses and income map
        List<BlockExpense> expenses = getManualBlockExpenses();
        Map<String, FlatMonthIncome> incomeMap = buildIncomeAvailabilityMap();

        int rule1Applied = 0, rule2Applied = 0, rule3Applied = 0;

        for (BlockExpense expense : expenses) {
            categorizeExpense(expense);

            try {
                switch (expense.ruleToApply) {
                    case 1:
                        applyRule1(expense);
                        rule1Applied++;
                        report.append(String.format("RULE 1 APPLIED: ID %d moved to %s\n",
                            expense.id, expense.originalFlatName));
                        break;
                    case 2:
                        applyRule2(expense, incomeMap);
                        rule2Applied++;
                        report.append(String.format("RULE 2 APPLIED: ID %d reassigned\n", expense.id));
                        break;
                    case 3:
                        applyRule3(expense, incomeMap);
                        rule3Applied++;
                        report.append(String.format("RULE 3 APPLIED: ID %d split with deposits\n", expense.id));
                        break;
                }
            } catch (Exception e) {
                report.append(String.format("ERROR on ID %d: %s\n", expense.id, e.getMessage()));
                log.error("Error processing expense {}: {}", expense.id, e.getMessage(), e);
            }
        }

        report.append(String.format("\n=== COMPLETED: Rule1=%d, Rule2=%d, Rule3=%d ===\n",
            rule1Applied, rule2Applied, rule3Applied));

        log.info(report.toString());
        return report.toString();
    }

    /**
     * Get all MANUAL/HISTORICAL expenses on block property 69
     */
    private List<BlockExpense> getManualBlockExpenses() {
        String sql = """
            SELECT id, paid_date, amount, description, payment_batch_id, source
            FROM unified_allocations
            WHERE property_id = ?
              AND source IN ('MANUAL', 'HISTORICAL')
              AND allocation_type = 'EXPENSE'
              AND amount > 0
            ORDER BY paid_date, amount DESC
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            BlockExpense e = new BlockExpense();
            e.id = rs.getLong("id");
            e.paidDate = rs.getDate("paid_date") != null ? rs.getDate("paid_date").toLocalDate() : null;
            e.amount = rs.getBigDecimal("amount");
            e.description = rs.getString("description");
            e.paymentBatchId = rs.getString("payment_batch_id");
            e.source = rs.getString("source");

            // Parse original flat from description
            if (e.description != null) {
                Matcher m = MOVED_FROM_PATTERN.matcher(e.description);
                if (m.find()) {
                    e.originalFlatName = m.group(1).trim();
                }
            }

            return e;
        }, BLOCK_PROPERTY_ID);
    }

    /**
     * Build a map of available income by flat and month for historical-only flats
     */
    private Map<String, FlatMonthIncome> buildIncomeAvailabilityMap() {
        // First, find flats that have NO PayProp allocations
        String historicalOnlyFlatsSql = """
            SELECT p.id, p.property_name
            FROM properties p
            WHERE p.property_name LIKE '%Flat%West Gate%'
              AND NOT EXISTS (
                  SELECT 1 FROM unified_allocations ua
                  WHERE ua.property_id = p.id AND ua.source = 'PAYPROP'
              )
        """;

        List<Map<String, Object>> historicalFlats = jdbcTemplate.queryForList(historicalOnlyFlatsSql);

        Map<String, FlatMonthIncome> result = new HashMap<>();

        for (Map<String, Object> flat : historicalFlats) {
            Long flatId = ((Number) flat.get("id")).longValue();
            String flatName = (String) flat.get("property_name");

            // Get income by month for this flat
            String incomeSql = """
                SELECT DATE_FORMAT(paid_date, '%Y-%m') as month,
                       SUM(CASE WHEN allocation_type = 'OWNER' THEN amount ELSE 0 END) as income,
                       SUM(CASE WHEN allocation_type = 'EXPENSE' THEN amount ELSE 0 END) as expenses
                FROM unified_allocations
                WHERE property_id = ?
                  AND source IN ('MANUAL', 'HISTORICAL')
                GROUP BY DATE_FORMAT(paid_date, '%Y-%m')
            """;

            jdbcTemplate.query(incomeSql, (rs, rowNum) -> {
                String monthStr = rs.getString("month");
                if (monthStr != null) {
                    FlatMonthIncome fmi = new FlatMonthIncome();
                    fmi.flatId = flatId;
                    fmi.flatName = flatName;
                    fmi.month = YearMonth.parse(monthStr);
                    fmi.totalIncome = rs.getBigDecimal("income");
                    fmi.usedIncome = rs.getBigDecimal("expenses");

                    String key = flatId + "_" + monthStr;
                    result.put(key, fmi);
                }
                return null;
            }, flatId);
        }

        return result;
    }

    /**
     * Categorize an expense to determine which rule applies
     */
    private void categorizeExpense(BlockExpense expense) {
        // Rule 3: Large expenses (> £500)
        if (expense.amount.compareTo(EXPENSE_THRESHOLD) > 0) {
            expense.ruleToApply = 3;
            return;
        }

        // If no original flat parsed from description, default to Rule 2
        if (expense.originalFlatName == null) {
            expense.ruleToApply = 2;
            return;
        }

        // Look up original flat ID
        expense.originalFlatId = findFlatIdByName(expense.originalFlatName);
        if (expense.originalFlatId == null) {
            expense.ruleToApply = 2;
            return;
        }

        // Check if original flat has PayProp allocations
        expense.originalFlatHasPayProp = flatHasPayProp(expense.originalFlatId);

        if (expense.originalFlatHasPayProp) {
            // Rule 2: Original flat has PayProp, reassign to historical-only flat
            expense.ruleToApply = 2;
        } else {
            // Rule 1: Original flat is historical-only, move back
            expense.ruleToApply = 1;
        }
    }

    /**
     * Find flat ID by name (partial match)
     */
    private Long findFlatIdByName(String flatName) {
        String sql = "SELECT id FROM properties WHERE property_name LIKE ? LIMIT 1";
        try {
            return jdbcTemplate.queryForObject(sql, Long.class, "%" + flatName + "%");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if a flat has any PayProp allocations
     */
    private boolean flatHasPayProp(Long flatId) {
        String sql = "SELECT COUNT(*) FROM unified_allocations WHERE property_id = ? AND source = 'PAYPROP'";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, flatId);
        return count != null && count > 0;
    }

    /**
     * RULE 1: Move expense back to original historical-only flat
     */
    private void applyRule1(BlockExpense expense) {
        String sql = """
            UPDATE unified_allocations
            SET property_id = ?,
                property_name = (SELECT property_name FROM properties WHERE id = ?)
            WHERE id = ?
        """;

        jdbcTemplate.update(sql, expense.originalFlatId, expense.originalFlatId, expense.id);
        log.info("Rule 1: Moved allocation {} to flat {}", expense.id, expense.originalFlatId);
    }

    /**
     * RULE 2: Reassign expense to a historical-only flat with available income
     */
    private void applyRule2(BlockExpense expense, Map<String, FlatMonthIncome> incomeMap) {
        // Find a historical-only flat with available income in the expense month
        YearMonth expenseMonth = YearMonth.from(expense.paidDate);

        FlatMonthIncome targetFlat = incomeMap.values().stream()
            .filter(f -> f.month.equals(expenseMonth))
            .filter(f -> f.getAvailable().compareTo(expense.amount) >= 0)
            .findFirst()
            .orElse(null);

        if (targetFlat == null) {
            // Try to find any flat with available income
            targetFlat = incomeMap.values().stream()
                .filter(f -> f.getAvailable().compareTo(expense.amount) >= 0)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No flat with sufficient income for expense " + expense.id));
        }

        // Update the expense to point to the target flat
        String sql = """
            UPDATE unified_allocations
            SET property_id = ?,
                property_name = ?
            WHERE id = ?
        """;

        jdbcTemplate.update(sql, targetFlat.flatId, targetFlat.flatName, expense.id);
        targetFlat.usedIncome = targetFlat.usedIncome.add(expense.amount);

        log.info("Rule 2: Reassigned allocation {} to flat {}", expense.id, targetFlat.flatName);
    }

    /**
     * RULE 3: Split large expense across multiple months/flats with Prestvale deposits
     */
    private void applyRule3(BlockExpense expense, Map<String, FlatMonthIncome> incomeMap) {
        BigDecimal remaining = expense.amount;
        int splitNum = 1;
        YearMonth startMonth = YearMonth.from(expense.paidDate);

        // Get sorted list of available income slots
        List<FlatMonthIncome> availableSlots = incomeMap.values().stream()
            .filter(f -> !f.month.isBefore(startMonth))
            .filter(f -> f.getAvailable().compareTo(BigDecimal.ZERO) > 0)
            .sorted(Comparator.comparing((FlatMonthIncome f) -> f.month).thenComparing(f -> f.flatName))
            .toList();

        List<Object[]> splits = new ArrayList<>();

        for (FlatMonthIncome slot : availableSlots) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal allocAmount = remaining.min(slot.getAvailable());
            if (allocAmount.compareTo(BigDecimal.ZERO) > 0) {
                splits.add(new Object[]{
                    slot.flatId,
                    slot.flatName,
                    allocAmount,
                    slot.month,
                    splitNum++
                });

                slot.usedIncome = slot.usedIncome.add(allocAmount);
                remaining = remaining.subtract(allocAmount);
            }
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new RuntimeException("Insufficient income across all flats to cover expense " +
                expense.id + " (£" + remaining + " remaining)");
        }

        // Delete original expense
        jdbcTemplate.update("DELETE FROM unified_allocations WHERE id = ?", expense.id);

        // Create split allocations and deposits
        for (Object[] split : splits) {
            Long flatId = (Long) split[0];
            String flatName = (String) split[1];
            BigDecimal amount = (BigDecimal) split[2];
            YearMonth month = (YearMonth) split[3];
            int num = (int) split[4];

            LocalDate splitDate = month.atDay(expense.paidDate.getDayOfMonth());
            String batchId = findOrCreateBatchForMonth(month);

            // Create expense on flat
            String insertExpense = """
                INSERT INTO unified_allocations (
                    property_id, property_name, allocation_type, amount,
                    description, payment_batch_id, paid_date, source
                ) VALUES (?, ?, 'EXPENSE', ?, ?, ?, ?, 'MANUAL')
            """;

            String description = String.format("Block contribution: %s (%d/%d)",
                expense.description != null ? expense.description.substring(0, Math.min(30, expense.description.length())) : "Expense",
                num, splits.size());

            jdbcTemplate.update(insertExpense, flatId, flatName, amount, description, batchId, splitDate);

            // Create Prestvale deposit to block
            String insertDeposit = """
                INSERT INTO unified_incoming_transactions (
                    property_id, transaction_date, amount, payer_name,
                    description, source, lease_id
                ) VALUES (?, ?, ?, 'Prestvale Properties Limited', ?, 'MANUAL', ?)
            """;

            String depositDesc = String.format("Block contribution from %s for %s",
                flatName,
                expense.description != null ? expense.description.substring(0, Math.min(30, expense.description.length())) : "Expense");

            jdbcTemplate.update(insertDeposit, BLOCK_PROPERTY_ID, splitDate, amount, depositDesc, BLOCK_LEASE_ID);
        }

        log.info("Rule 3: Split allocation {} into {} parts with Prestvale deposits", expense.id, splits.size());
    }

    /**
     * Find or create a payment batch for a given month
     */
    private String findOrCreateBatchForMonth(YearMonth month) {
        String pattern = "HIST-" + month.getYear() + "-" + String.format("%02d", month.getMonthValue()) + "%";
        String sql = "SELECT payment_batch_id FROM unified_allocations WHERE payment_batch_id LIKE ? LIMIT 1";

        try {
            return jdbcTemplate.queryForObject(sql, String.class, pattern);
        } catch (Exception e) {
            // Create new batch ID if none exists
            return "HIST-" + month.getYear() + "-" + String.format("%02d", month.getMonthValue()) + "-BLOCK";
        }
    }

    /**
     * Validate the migration results
     */
    public String validateMigration() {
        StringBuilder report = new StringBuilder();
        report.append("=== MIGRATION VALIDATION ===\n\n");

        // Check block allocations by source
        String allocSql = """
            SELECT source, allocation_type, COUNT(*) as cnt, SUM(amount) as total
            FROM unified_allocations WHERE property_id = ?
            GROUP BY source, allocation_type
        """;

        report.append("Block allocations by source:\n");
        jdbcTemplate.query(allocSql, (rs, rowNum) -> {
            report.append(String.format("  %s %s: %d records, £%.2f\n",
                rs.getString("source"),
                rs.getString("allocation_type"),
                rs.getInt("cnt"),
                rs.getBigDecimal("total")));
            return null;
        }, BLOCK_PROPERTY_ID);

        // Check block income vs expenses
        String balanceSql = """
            SELECT
                (SELECT COALESCE(SUM(amount), 0) FROM unified_allocations WHERE property_id = ? AND allocation_type = 'EXPENSE') as expenses,
                (SELECT COALESCE(SUM(amount), 0) FROM unified_incoming_transactions WHERE property_id = ?) as income
        """;

        Map<String, Object> balance = jdbcTemplate.queryForMap(balanceSql, BLOCK_PROPERTY_ID, BLOCK_PROPERTY_ID);
        BigDecimal expenses = (BigDecimal) balance.get("expenses");
        BigDecimal income = (BigDecimal) balance.get("income");

        report.append(String.format("\nBlock expenses: £%.2f\n", expenses));
        report.append(String.format("Block income: £%.2f\n", income));
        report.append(String.format("Balance: £%.2f\n", income.subtract(expenses)));

        if (income.compareTo(expenses) >= 0) {
            report.append("\n✓ Block is balanced (income >= expenses)\n");
        } else {
            report.append("\n✗ WARNING: Block expenses exceed income!\n");
        }

        log.info(report.toString());
        return report.toString();
    }
}

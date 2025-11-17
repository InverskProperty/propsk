package site.easy.to.build.crm;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.UnifiedTransaction;
import site.easy.to.build.crm.repository.PropertyRepository;
import site.easy.to.build.crm.repository.UnifiedTransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest
public class QueryFlat3Rent {

    @Autowired
    private UnifiedTransactionRepository unifiedTransactionRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Test
    public void queryFlat3RentReceived() {
        // Find Flat 3
        Property flat3 = propertyRepository.findById(3L).orElse(null);

        if (flat3 == null) {
            System.out.println("❌ Flat 3 not found!");
            return;
        }

        System.out.println("🏠 Property: " + flat3.getPropertyName() + " (ID: " + flat3.getId() + ")");
        System.out.println();

        // Get all transactions for Flat 3
        List<UnifiedTransaction> transactions = unifiedTransactionRepository.findByPropertyId(3L);

        System.out.println("📊 Total Transactions: " + transactions.size());
        System.out.println();

        // Filter for rent received (INCOMING, type = RENT or RENT_PAYMENT)
        List<UnifiedTransaction> rentTransactions = transactions.stream()
            .filter(t -> t.getFlowDirection() == UnifiedTransaction.FlowDirection.INCOMING)
            .filter(t -> {
                String type = t.getTransactionType();
                return type != null && (
                    type.equals("RENT") ||
                    type.equals("RENT_PAYMENT") ||
                    type.contains("Rent")
                );
            })
            .sorted((a, b) -> a.getTransactionDate().compareTo(b.getTransactionDate()))
            .collect(Collectors.toList());

        System.out.println("💰 Rent Received Transactions: " + rentTransactions.size());
        System.out.println();

        // Calculate total by source
        Map<UnifiedTransaction.SourceSystem, BigDecimal> totalBySource = rentTransactions.stream()
            .collect(Collectors.groupingBy(
                UnifiedTransaction::getSourceSystem,
                Collectors.reducing(BigDecimal.ZERO, UnifiedTransaction::getAmount, BigDecimal::add)
            ));

        System.out.println("📈 Rent Received by Source:");
        totalBySource.forEach((source, total) -> {
            System.out.println("  " + source + ": £" + total);
        });

        BigDecimal grandTotal = rentTransactions.stream()
            .map(UnifiedTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        System.out.println("  ─────────────────────");
        System.out.println("  TOTAL: £" + grandTotal);
        System.out.println();

        // Show detailed transactions
        System.out.println("📋 Detailed Rent Transactions:");
        System.out.println("Date       | Source     | Type              | Amount    | Description");
        System.out.println("-----------|------------|-------------------|-----------|----------------------------------");

        for (UnifiedTransaction t : rentTransactions) {
            System.out.printf("%-10s | %-10s | %-17s | £%-8s | %s%n",
                t.getTransactionDate(),
                t.getSourceSystem(),
                t.getTransactionType(),
                t.getAmount(),
                t.getDescription() != null ? t.getDescription() : ""
            );
        }

        System.out.println();
        System.out.println("✅ Query complete!");
    }
}

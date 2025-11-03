package site.easy.to.build.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.GeneratedStatement;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * ✨ PHASE 3: Repository for accessing generated statement history
 */
@Repository
public interface GeneratedStatementRepository extends JpaRepository<GeneratedStatement, Long> {

    /**
     * Find all statements for a specific customer, ordered by most recent first
     */
    List<GeneratedStatement> findByCustomerIdOrderByGeneratedAtDesc(Long customerId);

    /**
     * Find statements for a customer within a specific period
     */
    List<GeneratedStatement> findByCustomerIdAndPeriodStartGreaterThanEqualAndPeriodEndLessThanEqualOrderByGeneratedAtDesc(
            Long customerId, LocalDate periodStart, LocalDate periodEnd);

    /**
     * Find a specific statement by customer and exact period
     */
    Optional<GeneratedStatement> findByCustomerIdAndPeriodStartAndPeriodEnd(
            Long customerId, LocalDate periodStart, LocalDate periodEnd);

    /**
     * Count statements for a customer
     */
    long countByCustomerId(Long customerId);

    /**
     * Find most recent statement for a customer
     */
    Optional<GeneratedStatement> findFirstByCustomerIdOrderByGeneratedAtDesc(Long customerId);
}

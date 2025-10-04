package site.easy.to.build.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.PaymentSource;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentSourceRepository extends JpaRepository<PaymentSource, Long> {

    /**
     * Find payment source by name
     */
    Optional<PaymentSource> findByName(String name);

    /**
     * Find all active payment sources
     */
    List<PaymentSource> findByIsActiveTrueOrderByNameAsc();

    /**
     * Find payment sources by type
     */
    List<PaymentSource> findBySourceTypeOrderByNameAsc(String sourceType);

    /**
     * Find payment sources created by user
     */
    List<PaymentSource> findByCreatedByIdOrderByCreatedAtDesc(Long userId);

    /**
     * Check if payment source name exists
     */
    boolean existsByName(String name);

    /**
     * Get payment sources with transaction counts
     */
    @Query("SELECT ps FROM PaymentSource ps ORDER BY ps.totalTransactions DESC, ps.name ASC")
    List<PaymentSource> findAllOrderByTransactionCountDesc();

    /**
     * Find active sources with recent imports (last 30 days)
     */
    @Query("SELECT ps FROM PaymentSource ps WHERE ps.isActive = true " +
           "AND ps.lastImportDate >= CURRENT_DATE - 30 ORDER BY ps.lastImportDate DESC")
    List<PaymentSource> findRecentlyUsedSources();
}

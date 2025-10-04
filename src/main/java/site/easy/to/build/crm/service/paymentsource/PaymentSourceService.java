package site.easy.to.build.crm.service.paymentsource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.easy.to.build.crm.entity.PaymentSource;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.repository.PaymentSourceRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing payment sources (isolated transaction data sources)
 */
@Service
public class PaymentSourceService {

    private static final Logger log = LoggerFactory.getLogger(PaymentSourceService.class);

    @Autowired
    private PaymentSourceRepository paymentSourceRepository;

    /**
     * Create a new payment source
     */
    @Transactional
    public PaymentSource createPaymentSource(String name, String description, String sourceType, User createdBy) {
        log.info("Creating payment source: {} (type: {})", name, sourceType);

        // Check if name already exists
        if (paymentSourceRepository.existsByName(name)) {
            throw new IllegalArgumentException("Payment source with name '" + name + "' already exists");
        }

        PaymentSource source = new PaymentSource(name, sourceType, createdBy);
        source.setDescription(description);

        PaymentSource saved = paymentSourceRepository.save(source);
        log.info("✅ Payment source created with ID: {}", saved.getId());

        return saved;
    }

    /**
     * Get payment source by ID
     */
    public Optional<PaymentSource> getPaymentSourceById(Long id) {
        return paymentSourceRepository.findById(id);
    }

    /**
     * Get payment source by name
     */
    public Optional<PaymentSource> getPaymentSourceByName(String name) {
        return paymentSourceRepository.findByName(name);
    }

    /**
     * Get all active payment sources
     */
    public List<PaymentSource> getAllActivePaymentSources() {
        return paymentSourceRepository.findByIsActiveTrueOrderByNameAsc();
    }

    /**
     * Get all payment sources (active and inactive)
     */
    public List<PaymentSource> getAllPaymentSources() {
        return paymentSourceRepository.findAll();
    }

    /**
     * Get payment sources by type
     */
    public List<PaymentSource> getPaymentSourcesByType(String sourceType) {
        return paymentSourceRepository.findBySourceTypeOrderByNameAsc(sourceType);
    }

    /**
     * Get recently used payment sources (last 30 days)
     */
    public List<PaymentSource> getRecentlyUsedSources() {
        return paymentSourceRepository.findRecentlyUsedSources();
    }

    /**
     * Update payment source
     */
    @Transactional
    public PaymentSource updatePaymentSource(Long id, String name, String description, String sourceType) {
        log.info("Updating payment source ID: {}", id);

        PaymentSource source = paymentSourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Payment source not found with ID: " + id));

        // Check if new name conflicts with another source
        if (!source.getName().equals(name) && paymentSourceRepository.existsByName(name)) {
            throw new IllegalArgumentException("Payment source with name '" + name + "' already exists");
        }

        source.setName(name);
        source.setDescription(description);
        source.setSourceType(sourceType);

        PaymentSource updated = paymentSourceRepository.save(source);
        log.info("✅ Payment source updated: {}", updated.getName());

        return updated;
    }

    /**
     * Deactivate payment source (soft delete)
     */
    @Transactional
    public void deactivatePaymentSource(Long id) {
        log.info("Deactivating payment source ID: {}", id);

        PaymentSource source = paymentSourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Payment source not found with ID: " + id));

        source.setIsActive(false);
        paymentSourceRepository.save(source);

        log.info("✅ Payment source deactivated: {}", source.getName());
    }

    /**
     * Reactivate payment source
     */
    @Transactional
    public void reactivatePaymentSource(Long id) {
        log.info("Reactivating payment source ID: {}", id);

        PaymentSource source = paymentSourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Payment source not found with ID: " + id));

        source.setIsActive(true);
        paymentSourceRepository.save(source);

        log.info("✅ Payment source reactivated: {}", source.getName());
    }

    /**
     * Delete payment source (hard delete - use with caution)
     */
    @Transactional
    public void deletePaymentSource(Long id) {
        log.warn("⚠️ Hard deleting payment source ID: {}", id);

        PaymentSource source = paymentSourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Payment source not found with ID: " + id));

        // Check if source has transactions - prevent deletion if it does
        if (source.getTotalTransactions() != null && source.getTotalTransactions() > 0) {
            throw new IllegalStateException("Cannot delete payment source '" + source.getName() +
                    "' as it has " + source.getTotalTransactions() + " transactions. Deactivate instead.");
        }

        paymentSourceRepository.delete(source);
        log.warn("Payment source deleted: {}", source.getName());
    }

    /**
     * Increment transaction count for a payment source
     */
    @Transactional
    public void incrementTransactionCount(Long paymentSourceId) {
        PaymentSource source = paymentSourceRepository.findById(paymentSourceId)
                .orElseThrow(() -> new IllegalArgumentException("Payment source not found with ID: " + paymentSourceId));

        source.incrementTransactionCount();
        source.updateLastImportDate();
        paymentSourceRepository.save(source);
    }

    /**
     * Update last import date
     */
    @Transactional
    public void updateLastImportDate(Long paymentSourceId, LocalDateTime importDate) {
        PaymentSource source = paymentSourceRepository.findById(paymentSourceId)
                .orElseThrow(() -> new IllegalArgumentException("Payment source not found with ID: " + paymentSourceId));

        source.setLastImportDate(importDate);
        paymentSourceRepository.save(source);
    }

    /**
     * Get payment source statistics
     */
    public PaymentSourceStats getPaymentSourceStats(Long paymentSourceId) {
        PaymentSource source = paymentSourceRepository.findById(paymentSourceId)
                .orElseThrow(() -> new IllegalArgumentException("Payment source not found with ID: " + paymentSourceId));

        PaymentSourceStats stats = new PaymentSourceStats();
        stats.setId(source.getId());
        stats.setName(source.getName());
        stats.setSourceType(source.getSourceType());
        stats.setTotalTransactions(source.getTotalTransactions());
        stats.setLastImportDate(source.getLastImportDate());
        stats.setIsActive(source.getIsActive());
        stats.setCreatedAt(source.getCreatedAt());

        return stats;
    }

    /**
     * DTO for payment source statistics
     */
    public static class PaymentSourceStats {
        private Long id;
        private String name;
        private String sourceType;
        private Integer totalTransactions;
        private LocalDateTime lastImportDate;
        private Boolean isActive;
        private LocalDateTime createdAt;

        // Getters and setters
        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }

        public Integer getTotalTransactions() {
            return totalTransactions;
        }

        public void setTotalTransactions(Integer totalTransactions) {
            this.totalTransactions = totalTransactions;
        }

        public LocalDateTime getLastImportDate() {
            return lastImportDate;
        }

        public void setLastImportDate(LocalDateTime lastImportDate) {
            this.lastImportDate = lastImportDate;
        }

        public Boolean getIsActive() {
            return isActive;
        }

        public void setIsActive(Boolean isActive) {
            this.isActive = isActive;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }
}

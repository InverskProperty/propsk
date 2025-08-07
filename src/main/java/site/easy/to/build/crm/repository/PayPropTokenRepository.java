// PayPropTokenRepository.java - NEW REPOSITORY for PayProp Token Persistence
package site.easy.to.build.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.PayPropToken;

import java.util.Optional;
import java.util.List;

@Repository
public interface PayPropTokenRepository extends JpaRepository<PayPropToken, Long> {
    
    /**
     * Find the most recent active token
     */
    @Query("SELECT t FROM PayPropToken t WHERE t.isActive = true ORDER BY t.obtainedAt DESC")
    Optional<PayPropToken> findMostRecentActiveToken();
    
    /**
     * Find active token by user ID
     */
    @Query("SELECT t FROM PayPropToken t WHERE t.userId = :userId AND t.isActive = true ORDER BY t.obtainedAt DESC")
    Optional<PayPropToken> findActiveTokenByUserId(@Param("userId") Long userId);
    
    /**
     * Find all active tokens
     */
    List<PayPropToken> findByIsActiveTrue();
    
    /**
     * Deactivate all tokens for a user
     */
    @Query("UPDATE PayPropToken t SET t.isActive = false WHERE t.userId = :userId")
    void deactivateAllTokensForUser(@Param("userId") Long userId);
    
    /**
     * Deactivate all tokens (for logout or reset)
     */
    @Query("UPDATE PayPropToken t SET t.isActive = false")
    void deactivateAllTokens();
}
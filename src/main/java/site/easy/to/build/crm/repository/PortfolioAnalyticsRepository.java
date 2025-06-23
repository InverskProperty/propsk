// PortfolioAnalyticsRepository.java - Repository for portfolio analytics
package site.easy.to.build.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.PortfolioAnalytics;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioAnalyticsRepository extends JpaRepository<PortfolioAnalytics, Long> {
    
    // Find analytics by portfolio and date
    Optional<PortfolioAnalytics> findByPortfolioIdAndCalculationDate(Long portfolioId, LocalDate calculationDate);
    
    // Get latest analytics for a portfolio
    @Query("SELECT pa FROM PortfolioAnalytics pa WHERE pa.portfolioId = :portfolioId ORDER BY pa.calculationDate DESC")
    List<PortfolioAnalytics> findByPortfolioIdOrderByCalculationDateDesc(@Param("portfolioId") Long portfolioId);
    
    @Query("SELECT pa FROM PortfolioAnalytics pa WHERE pa.portfolioId = :portfolioId ORDER BY pa.calculationDate DESC LIMIT 1")
    Optional<PortfolioAnalytics> findLatestByPortfolioId(@Param("portfolioId") Long portfolioId);
    
    // Time series data
    @Query("SELECT pa FROM PortfolioAnalytics pa WHERE pa.portfolioId = :portfolioId AND pa.calculationDate BETWEEN :startDate AND :endDate ORDER BY pa.calculationDate")
    List<PortfolioAnalytics> findByPortfolioIdAndDateRange(@Param("portfolioId") Long portfolioId,
                                                          @Param("startDate") LocalDate startDate,
                                                          @Param("endDate") LocalDate endDate);
    
    // Get analytics for all portfolios for a specific date
    List<PortfolioAnalytics> findByCalculationDate(LocalDate calculationDate);
    
    // Performance queries
    @Query("SELECT pa FROM PortfolioAnalytics pa WHERE pa.calculationDate = :date AND pa.occupancyRate < :threshold")
    List<PortfolioAnalytics> findPortfoliosWithLowOccupancy(@Param("date") LocalDate date, @Param("threshold") Double threshold);
    
    @Query("SELECT pa FROM PortfolioAnalytics pa WHERE pa.calculationDate = :date AND pa.incomeVariance < 0")
    List<PortfolioAnalytics> findPortfoliosUnderperformingIncome(@Param("date") LocalDate date);
    
    // Aggregated analytics across all portfolios
    @Query("SELECT SUM(pa.totalProperties), SUM(pa.occupiedProperties), AVG(pa.occupancyRate) " +
           "FROM PortfolioAnalytics pa WHERE pa.calculationDate = :date")
    Object[] getAggregatedAnalytics(@Param("date") LocalDate date);
    
    // Cleanup old analytics
    @Query("DELETE FROM PortfolioAnalytics pa WHERE pa.calculationDate < :cutoffDate")
    void deleteAnalyticsOlderThan(@Param("cutoffDate") LocalDate cutoffDate);
}
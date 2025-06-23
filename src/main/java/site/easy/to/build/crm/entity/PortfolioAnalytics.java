// PortfolioAnalytics.java - Entity for storing calculated portfolio metrics
package site.easy.to.build.crm.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "portfolio_analytics")
public class PortfolioAnalytics {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "portfolio_id", nullable = false)
    private Long portfolioId;
    
    @Column(name = "calculation_date", nullable = false)
    private LocalDate calculationDate;
    
    // Property Counts
    @Column(name = "total_properties")
    private Integer totalProperties = 0;
    
    @Column(name = "occupied_properties")
    private Integer occupiedProperties = 0;
    
    @Column(name = "vacant_properties")
    private Integer vacantProperties = 0;
    
    @Column(name = "under_maintenance")
    private Integer underMaintenance = 0;
    
    // Financial Metrics
    @Column(name = "total_monthly_rent", precision = 12, scale = 2)
    private BigDecimal totalMonthlyRent = BigDecimal.ZERO;
    
    @Column(name = "actual_monthly_income", precision = 12, scale = 2)
    private BigDecimal actualMonthlyIncome = BigDecimal.ZERO;
    
    @Column(name = "lost_monthly_income", precision = 12, scale = 2)
    private BigDecimal lostMonthlyIncome = BigDecimal.ZERO;
    
    @Column(name = "occupancy_rate", precision = 5, scale = 2)
    private BigDecimal occupancyRate = BigDecimal.ZERO;
    
    // Performance vs Targets
    @Column(name = "target_monthly_income", precision = 12, scale = 2)
    private BigDecimal targetMonthlyIncome = BigDecimal.ZERO;
    
    @Column(name = "target_occupancy_rate", precision = 5, scale = 2)
    private BigDecimal targetOccupancyRate = BigDecimal.ZERO;
    
    @Column(name = "income_variance", precision = 12, scale = 2)
    private BigDecimal incomeVariance = BigDecimal.ZERO;
    
    @Column(name = "occupancy_variance", precision = 5, scale = 2)
    private BigDecimal occupancyVariance = BigDecimal.ZERO;
    
    // Maintenance Metrics
    @Column(name = "maintenance_requests_open")
    private Integer maintenanceRequestsOpen = 0;
    
    @Column(name = "maintenance_costs_month", precision = 10, scale = 2)
    private BigDecimal maintenanceCostsMonth = BigDecimal.ZERO;
    
    @Column(name = "average_resolution_days", precision = 5, scale = 1)
    private BigDecimal averageResolutionDays = BigDecimal.ZERO;
    
    // Tenant Metrics
    @Column(name = "total_tenants")
    private Integer totalTenants = 0;
    
    @Column(name = "new_tenants_month")
    private Integer newTenantsMonth = 0;
    
    @Column(name = "departed_tenants_month")
    private Integer departedTenantsMonth = 0;
    
    @Column(name = "average_tenancy_length", precision = 5, scale = 1)
    private BigDecimal averageTenancyLength = BigDecimal.ZERO;
    
    // PayProp Sync Status
    @Column(name = "properties_synced")
    private Integer propertiesSynced = 0;
    
    @Column(name = "properties_pending_sync")
    private Integer propertiesPendingSync = 0;
    
    @Column(name = "last_sync_check")
    private LocalDateTime lastSyncCheck;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Relationship
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", insertable = false, updatable = false)
    private Portfolio portfolio;
    
    // Constructors
    public PortfolioAnalytics() {}
    
    public PortfolioAnalytics(Long portfolioId, LocalDate calculationDate) {
        this.portfolioId = portfolioId;
        this.calculationDate = calculationDate;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getPortfolioId() { return portfolioId; }
    public void setPortfolioId(Long portfolioId) { this.portfolioId = portfolioId; }
    
    public LocalDate getCalculationDate() { return calculationDate; }
    public void setCalculationDate(LocalDate calculationDate) { this.calculationDate = calculationDate; }
    
    public Integer getTotalProperties() { return totalProperties; }
    public void setTotalProperties(Integer totalProperties) { this.totalProperties = totalProperties; }
    
    public Integer getOccupiedProperties() { return occupiedProperties; }
    public void setOccupiedProperties(Integer occupiedProperties) { this.occupiedProperties = occupiedProperties; }
    
    public Integer getVacantProperties() { return vacantProperties; }
    public void setVacantProperties(Integer vacantProperties) { this.vacantProperties = vacantProperties; }
    
    public Integer getUnderMaintenance() { return underMaintenance; }
    public void setUnderMaintenance(Integer underMaintenance) { this.underMaintenance = underMaintenance; }
    
    public BigDecimal getTotalMonthlyRent() { return totalMonthlyRent; }
    public void setTotalMonthlyRent(BigDecimal totalMonthlyRent) { this.totalMonthlyRent = totalMonthlyRent; }
    
    public BigDecimal getActualMonthlyIncome() { return actualMonthlyIncome; }
    public void setActualMonthlyIncome(BigDecimal actualMonthlyIncome) { this.actualMonthlyIncome = actualMonthlyIncome; }
    
    public BigDecimal getLostMonthlyIncome() { return lostMonthlyIncome; }
    public void setLostMonthlyIncome(BigDecimal lostMonthlyIncome) { this.lostMonthlyIncome = lostMonthlyIncome; }
    
    public BigDecimal getOccupancyRate() { return occupancyRate; }
    public void setOccupancyRate(BigDecimal occupancyRate) { this.occupancyRate = occupancyRate; }
    
    public BigDecimal getTargetMonthlyIncome() { return targetMonthlyIncome; }
    public void setTargetMonthlyIncome(BigDecimal targetMonthlyIncome) { this.targetMonthlyIncome = targetMonthlyIncome; }
    
    public BigDecimal getTargetOccupancyRate() { return targetOccupancyRate; }
    public void setTargetOccupancyRate(BigDecimal targetOccupancyRate) { this.targetOccupancyRate = targetOccupancyRate; }
    
    public BigDecimal getIncomeVariance() { return incomeVariance; }
    public void setIncomeVariance(BigDecimal incomeVariance) { this.incomeVariance = incomeVariance; }
    
    public BigDecimal getOccupancyVariance() { return occupancyVariance; }
    public void setOccupancyVariance(BigDecimal occupancyVariance) { this.occupancyVariance = occupancyVariance; }
    
    public Integer getMaintenanceRequestsOpen() { return maintenanceRequestsOpen; }
    public void setMaintenanceRequestsOpen(Integer maintenanceRequestsOpen) { this.maintenanceRequestsOpen = maintenanceRequestsOpen; }
    
    public BigDecimal getMaintenanceCostsMonth() { return maintenanceCostsMonth; }
    public void setMaintenanceCostsMonth(BigDecimal maintenanceCostsMonth) { this.maintenanceCostsMonth = maintenanceCostsMonth; }
    
    public BigDecimal getAverageResolutionDays() { return averageResolutionDays; }
    public void setAverageResolutionDays(BigDecimal averageResolutionDays) { this.averageResolutionDays = averageResolutionDays; }
    
    public Integer getTotalTenants() { return totalTenants; }
    public void setTotalTenants(Integer totalTenants) { this.totalTenants = totalTenants; }
    
    public Integer getNewTenantsMonth() { return newTenantsMonth; }
    public void setNewTenantsMonth(Integer newTenantsMonth) { this.newTenantsMonth = newTenantsMonth; }
    
    public Integer getDepartedTenantsMonth() { return departedTenantsMonth; }
    public void setDepartedTenantsMonth(Integer departedTenantsMonth) { this.departedTenantsMonth = departedTenantsMonth; }
    
    public BigDecimal getAverageTenancyLength() { return averageTenancyLength; }
    public void setAverageTenancyLength(BigDecimal averageTenancyLength) { this.averageTenancyLength = averageTenancyLength; }
    
    public Integer getPropertiesSynced() { return propertiesSynced; }
    public void setPropertiesSynced(Integer propertiesSynced) { this.propertiesSynced = propertiesSynced; }
    
    public Integer getPropertiesPendingSync() { return propertiesPendingSync; }
    public void setPropertiesPendingSync(Integer propertiesPendingSync) { this.propertiesPendingSync = propertiesPendingSync; }
    
    public LocalDateTime getLastSyncCheck() { return lastSyncCheck; }
    public void setLastSyncCheck(LocalDateTime lastSyncCheck) { this.lastSyncCheck = lastSyncCheck; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public Portfolio getPortfolio() { return portfolio; }
    public void setPortfolio(Portfolio portfolio) { this.portfolio = portfolio; }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
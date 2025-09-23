package site.easy.to.build.crm.dto;

import site.easy.to.build.crm.enums.StatementDataSource;
import java.time.LocalDate;
import java.util.Set;

/**
 * Request DTO for statement generation with data source selection
 */
public class StatementGenerationRequest {

    private Long propertyOwnerId;
    private LocalDate fromDate;
    private LocalDate toDate;
    private Set<StatementDataSource> includedDataSources;
    private String statementType; // "PROPERTY_OWNER", "PORTFOLIO", "TENANT"
    private String outputFormat; // "XLSX", "GOOGLE_SHEETS", "PDF"
    private boolean includeExpenses = true;
    private boolean includeFormulas = true;
    private String notes;

    // Constructors
    public StatementGenerationRequest() {}

    public StatementGenerationRequest(Long propertyOwnerId, LocalDate fromDate, LocalDate toDate,
                                    Set<StatementDataSource> includedDataSources) {
        this.propertyOwnerId = propertyOwnerId;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.includedDataSources = includedDataSources;
    }

    // Getters and Setters
    public Long getPropertyOwnerId() {
        return propertyOwnerId;
    }

    public void setPropertyOwnerId(Long propertyOwnerId) {
        this.propertyOwnerId = propertyOwnerId;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public Set<StatementDataSource> getIncludedDataSources() {
        return includedDataSources;
    }

    public void setIncludedDataSources(Set<StatementDataSource> includedDataSources) {
        this.includedDataSources = includedDataSources;
    }

    public String getStatementType() {
        return statementType;
    }

    public void setStatementType(String statementType) {
        this.statementType = statementType;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }

    public boolean isIncludeExpenses() {
        return includeExpenses;
    }

    public void setIncludeExpenses(boolean includeExpenses) {
        this.includeExpenses = includeExpenses;
    }

    public boolean isIncludeFormulas() {
        return includeFormulas;
    }

    public void setIncludeFormulas(boolean includeFormulas) {
        this.includeFormulas = includeFormulas;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    // Helper methods
    public boolean includesDataSource(StatementDataSource dataSource) {
        return includedDataSources != null && includedDataSources.contains(dataSource);
    }

    public boolean includesHistoricalData() {
        return includesDataSource(StatementDataSource.HISTORICAL_PAYPROP) ||
               includesDataSource(StatementDataSource.HISTORICAL_OLD_BANK) ||
               includesDataSource(StatementDataSource.HISTORICAL_ROBERT_ELLIS);
    }

    public boolean includesLiveData() {
        return includesDataSource(StatementDataSource.LIVE_PAYPROP) ||
               includesDataSource(StatementDataSource.LOCAL_CRM);
    }
}
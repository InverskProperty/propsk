package site.easy.to.build.crm.service.payprop.raw;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Result object for PayProp raw import operations
 * Tracks success/failure, timing, and statistics for each import
 */
public class PayPropRawImportResult {
    
    private String endpoint;
    private boolean success;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int totalFetched;
    private int totalImported;
    private int totalErrors;
    private String errorMessage;
    private String details;
    
    public PayPropRawImportResult() {
        this.success = false;
        this.totalFetched = 0;
        this.totalImported = 0;
        this.totalErrors = 0;
    }
    
    public PayPropRawImportResult(String endpoint) {
        this();
        this.endpoint = endpoint;
    }
    
    // ===== GETTERS AND SETTERS =====
    
    public String getEndpoint() {
        return endpoint;
    }
    
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public LocalDateTime getStartTime() {
        return startTime;
    }
    
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }
    
    public LocalDateTime getEndTime() {
        return endTime;
    }
    
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
    
    public int getTotalFetched() {
        return totalFetched;
    }
    
    public void setTotalFetched(int totalFetched) {
        this.totalFetched = totalFetched;
    }
    
    public int getTotalImported() {
        return totalImported;
    }
    
    public void setTotalImported(int totalImported) {
        this.totalImported = totalImported;
    }
    
    public int getTotalErrors() {
        return totalErrors;
    }
    
    public void setTotalErrors(int totalErrors) {
        this.totalErrors = totalErrors;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public String getDetails() {
        return details;
    }
    
    public void setDetails(String details) {
        this.details = details;
    }
    
    // ===== CALCULATED PROPERTIES =====
    
    public Duration getDuration() {
        if (startTime == null || endTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(startTime, endTime);
    }
    
    public double getSuccessRate() {
        if (totalFetched == 0) {
            return 0.0;
        }
        return (double) totalImported / totalFetched * 100.0;
    }
    
    public boolean hasErrors() {
        return totalErrors > 0 || (errorMessage != null && !errorMessage.trim().isEmpty());
    }
    
    // ===== UTILITY METHODS =====
    
    public void incrementFetched() {
        this.totalFetched++;
    }
    
    public void incrementImported() {
        this.totalImported++;
    }
    
    public void incrementErrors() {
        this.totalErrors++;
    }
    
    public void addError(String error) {
        incrementErrors();
        if (this.errorMessage == null) {
            this.errorMessage = error;
        } else {
            this.errorMessage += "; " + error;
        }
    }
    
    // ===== STRING REPRESENTATION =====
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PayPropRawImportResult{");
        sb.append("endpoint='").append(endpoint).append('\'');
        sb.append(", success=").append(success);
        sb.append(", duration=").append(getDuration());
        sb.append(", fetched=").append(totalFetched);
        sb.append(", imported=").append(totalImported);
        sb.append(", errors=").append(totalErrors);
        sb.append(", successRate=").append(String.format("%.1f%%", getSuccessRate()));
        if (hasErrors()) {
            sb.append(", errorMessage='").append(errorMessage).append('\'');
        }
        sb.append('}');
        return sb.toString();
    }
    
    /**
     * Get a human-readable summary of the import result
     */
    public String getSummary() {
        if (success) {
            return String.format("✅ %s: %d/%d items imported successfully in %d seconds", 
                endpoint, totalImported, totalFetched, getDuration().getSeconds());
        } else {
            return String.format("❌ %s: Import failed after %d seconds - %s", 
                endpoint, getDuration().getSeconds(), 
                errorMessage != null ? errorMessage : "Unknown error");
        }
    }
}
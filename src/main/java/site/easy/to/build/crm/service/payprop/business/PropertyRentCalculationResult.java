package site.easy.to.build.crm.service.payprop.business;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Result object for property rent calculation operations
 * Tracks the success of solving the £995 vs £1,075 mystery
 */
public class PropertyRentCalculationResult {
    
    private boolean success;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int totalProperties;
    private int decisionsCalculated;
    private int decisionsStored;
    private int propertiesUpdated;
    private String errorMessage;
    
    public PropertyRentCalculationResult() {
        this.success = false;
        this.totalProperties = 0;
        this.decisionsCalculated = 0;
        this.decisionsStored = 0;
        this.propertiesUpdated = 0;
    }
    
    // ===== GETTERS AND SETTERS =====
    
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
    
    public int getTotalProperties() {
        return totalProperties;
    }
    
    public void setTotalProperties(int totalProperties) {
        this.totalProperties = totalProperties;
    }
    
    public int getDecisionsCalculated() {
        return decisionsCalculated;
    }
    
    public void setDecisionsCalculated(int decisionsCalculated) {
        this.decisionsCalculated = decisionsCalculated;
    }
    
    public int getDecisionsStored() {
        return decisionsStored;
    }
    
    public void setDecisionsStored(int decisionsStored) {
        this.decisionsStored = decisionsStored;
    }
    
    public int getPropertiesUpdated() {
        return propertiesUpdated;
    }
    
    public void setPropertiesUpdated(int propertiesUpdated) {
        this.propertiesUpdated = propertiesUpdated;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    // ===== CALCULATED PROPERTIES =====
    
    public Duration getDuration() {
        if (startTime == null || endTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(startTime, endTime);
    }
    
    public boolean hasErrors() {
        return errorMessage != null && !errorMessage.trim().isEmpty();
    }
    
    // ===== STRING REPRESENTATION =====
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PropertyRentCalculationResult{");
        sb.append("success=").append(success);
        sb.append(", duration=").append(getDuration());
        sb.append(", totalProperties=").append(totalProperties);
        sb.append(", decisionsCalculated=").append(decisionsCalculated);
        sb.append(", decisionsStored=").append(decisionsStored);
        sb.append(", propertiesUpdated=").append(propertiesUpdated);
        if (hasErrors()) {
            sb.append(", errorMessage='").append(errorMessage).append('\'');
        }
        sb.append('}');
        return sb.toString();
    }
    
    /**
     * Get a human-readable summary of the calculation result
     */
    public String getSummary() {
        if (success) {
            return String.format("✅ £995 vs £1,075 mystery solved: %d properties processed, %d updated in %d seconds", 
                totalProperties, propertiesUpdated, getDuration().getSeconds());
        } else {
            return String.format("❌ Rent calculation failed after %d seconds - %s", 
                getDuration().getSeconds(), 
                errorMessage != null ? errorMessage : "Unknown error");
        }
    }
}
package site.easy.to.build.crm.service.payprop.raw;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * PayProp Import Summary Report
 * 
 * Contains statistics about import run including issues encountered
 */
public class PayPropImportSummary {
    
    private String importRunId;
    private LocalDateTime generatedAt = LocalDateTime.now();
    private int totalIssues = 0;
    private List<IssueCount> issueCounts = new ArrayList<>();
    
    public String getImportRunId() {
        return importRunId;
    }
    
    public void setImportRunId(String importRunId) {
        this.importRunId = importRunId;
    }
    
    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }
    
    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }
    
    public int getTotalIssues() {
        return totalIssues;
    }
    
    public void setTotalIssues(int totalIssues) {
        this.totalIssues = totalIssues;
    }
    
    public List<IssueCount> getIssueCounts() {
        return issueCounts;
    }
    
    public void setIssueCounts(List<IssueCount> issueCounts) {
        this.issueCounts = issueCounts;
    }
    
    public void addIssueCount(IssueCount issueCount) {
        this.issueCounts.add(issueCount);
    }
    
    /**
     * Generate human-readable summary
     */
    public String getFormattedSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 PayProp Import Summary - ").append(importRunId).append("\n");
        sb.append("Generated: ").append(generatedAt).append("\n");
        sb.append("Total Issues: ").append(totalIssues).append("\n\n");
        
        if (totalIssues == 0) {
            sb.append("✅ No issues detected - perfect import!\n");
        } else {
            sb.append("Issues by Endpoint:\n");
            String currentEndpoint = "";
            
            for (IssueCount issueCount : issueCounts) {
                if (!issueCount.getEndpoint().equals(currentEndpoint)) {
                    currentEndpoint = issueCount.getEndpoint();
                    sb.append("\n🔗 ").append(currentEndpoint).append(":\n");
                }
                
                sb.append("  - ").append(issueCount.getIssueType())
                  .append(": ").append(issueCount.getCount()).append(" records");
                
                if (issueCount.getFinancialImpactCount() > 0) {
                    sb.append(" (⚠️ ").append(issueCount.getFinancialImpactCount())
                      .append(" with financial impact)");
                }
                sb.append("\n");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Check if import had significant issues
     */
    public boolean hasSignificantIssues() {
        return issueCounts.stream()
            .anyMatch(ic -> ic.getFinancialImpactCount() > 0 || ic.getCount() > 10);
    }
    
    /**
     * Get total financial impact count
     */
    public int getTotalFinancialImpact() {
        return issueCounts.stream()
            .mapToInt(IssueCount::getFinancialImpactCount)
            .sum();
    }
    
    /**
     * Issue count by type and endpoint
     */
    public static class IssueCount {
        private String endpoint;
        private String issueType;
        private int count;
        private int financialImpactCount;
        
        public String getEndpoint() {
            return endpoint;
        }
        
        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }
        
        public String getIssueType() {
            return issueType;
        }
        
        public void setIssueType(String issueType) {
            this.issueType = issueType;
        }
        
        public int getCount() {
            return count;
        }
        
        public void setCount(int count) {
            this.count = count;
        }
        
        public int getFinancialImpactCount() {
            return financialImpactCount;
        }
        
        public void setFinancialImpactCount(int financialImpactCount) {
            this.financialImpactCount = financialImpactCount;
        }
    }
}
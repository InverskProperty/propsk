package site.easy.to.build.crm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Configuration for commission rates
 * Allows setting commission percentages via application.properties
 */
@Configuration
@ConfigurationProperties(prefix = "commission")
public class CommissionConfig {

    /**
     * Management fee percentage (default: 10% = 0.10)
     */
    private BigDecimal managementFeePercent = new BigDecimal("0.10");

    /**
     * Service fee percentage (default: 5% = 0.05)
     */
    private BigDecimal serviceFeePercent = new BigDecimal("0.05");

    // Getters and Setters
    public BigDecimal getManagementFeePercent() {
        return managementFeePercent;
    }

    public void setManagementFeePercent(BigDecimal managementFeePercent) {
        this.managementFeePercent = managementFeePercent;
    }

    public BigDecimal getServiceFeePercent() {
        return serviceFeePercent;
    }

    public void setServiceFeePercent(BigDecimal serviceFeePercent) {
        this.serviceFeePercent = serviceFeePercent;
    }
}

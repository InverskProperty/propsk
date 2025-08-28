package site.easy.to.build.crm.controller;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Connection Pool Monitor Controller
 * Provides real-time visibility into database connection pool status
 */
@RestController
@RequestMapping("/admin/connection-pool")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER')")
public class ConnectionPoolMonitorController {

    @Autowired
    private DataSource dataSource;

    @GetMapping("/status")
    public Map<String, Object> getConnectionPoolStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            if (dataSource instanceof HikariDataSource) {
                HikariDataSource hikariDS = (HikariDataSource) dataSource;
                HikariPoolMXBean poolBean = hikariDS.getHikariPoolMXBean();
                
                status.put("poolName", hikariDS.getPoolName());
                status.put("activeConnections", poolBean.getActiveConnections());
                status.put("idleConnections", poolBean.getIdleConnections());
                status.put("totalConnections", poolBean.getTotalConnections());
                status.put("threadsAwaitingConnection", poolBean.getThreadsAwaitingConnection());
                status.put("maximumPoolSize", hikariDS.getMaximumPoolSize());
                status.put("minimumIdle", hikariDS.getMinimumIdle());
                status.put("connectionTimeout", hikariDS.getConnectionTimeout());
                status.put("idleTimeout", hikariDS.getIdleTimeout());
                status.put("maxLifetime", hikariDS.getMaxLifetime());
                
                // Calculate pool health
                int activeConnections = poolBean.getActiveConnections();
                int totalConnections = poolBean.getTotalConnections();
                int maxPoolSize = hikariDS.getMaximumPoolSize();
                
                double poolUtilization = (double) totalConnections / maxPoolSize * 100;
                double activeUtilization = (double) activeConnections / maxPoolSize * 100;
                
                status.put("poolUtilizationPercent", Math.round(poolUtilization * 100.0) / 100.0);
                status.put("activeUtilizationPercent", Math.round(activeUtilization * 100.0) / 100.0);
                
                // Determine health status
                String healthStatus;
                if (poolUtilization < 50) {
                    healthStatus = "HEALTHY";
                } else if (poolUtilization < 80) {
                    healthStatus = "WARNING";
                } else {
                    healthStatus = "CRITICAL";
                }
                
                status.put("healthStatus", healthStatus);
                status.put("threadsWaiting", poolBean.getThreadsAwaitingConnection() > 0);
                
            } else {
                status.put("error", "DataSource is not HikariCP - cannot get detailed stats");
                status.put("dataSourceType", dataSource.getClass().getSimpleName());
            }
            
            status.put("success", true);
            status.put("timestamp", System.currentTimeMillis());
            
        } catch (Exception e) {
            status.put("success", false);
            status.put("error", e.getMessage());
            status.put("timestamp", System.currentTimeMillis());
        }
        
        return status;
    }
    
    @GetMapping("/health-check")
    public Map<String, Object> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // Test getting a connection
            long startTime = System.currentTimeMillis();
            dataSource.getConnection().close();
            long connectionTime = System.currentTimeMillis() - startTime;
            
            health.put("canGetConnection", true);
            health.put("connectionTimeMs", connectionTime);
            
            if (connectionTime < 1000) {
                health.put("connectionSpeed", "FAST");
            } else if (connectionTime < 5000) {
                health.put("connectionSpeed", "SLOW");
            } else {
                health.put("connectionSpeed", "VERY_SLOW");
            }
            
        } catch (Exception e) {
            health.put("canGetConnection", false);
            health.put("error", e.getMessage());
        }
        
        health.put("timestamp", System.currentTimeMillis());
        return health;
    }
}
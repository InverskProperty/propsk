package site.easy.to.build.crm.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "customer_login_info")
public class CustomerLoginInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "username")
    private String username;

    @Column(name = "password")
    private String password;

    @Column(name = "token")
    private String token;

    @Column(name = "password_set")
    private Boolean passwordSet;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "login_attempts")
    private Integer loginAttempts = 0;

    @Column(name = "account_locked")
    private Boolean accountLocked = false;

    // Customer owns the FK (customers.profile_id -> customer_login_info.id)
    @OneToOne(mappedBy = "customerLoginInfo")
    @JsonIgnoreProperties("customerLoginInfo")
    private Customer customer;

    // Constructors
    public CustomerLoginInfo() {
        this.createdAt = LocalDateTime.now();
        this.passwordSet = false;
        this.loginAttempts = 0;
        this.accountLocked = false;
    }

    public CustomerLoginInfo(String username, String password, String token, Boolean passwordSet, Customer customer) {
        this();
        this.username = username;
        this.password = password;
        this.token = token;
        this.passwordSet = passwordSet;
        this.customer = customer;
    }

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
        this.updatedAt = LocalDateTime.now();
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
        this.updatedAt = LocalDateTime.now();
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
        this.updatedAt = LocalDateTime.now();
    }

    public Boolean getPasswordSet() {
        return passwordSet;
    }

    public boolean isPasswordSet() {
        return passwordSet != null && passwordSet;
    }

    public void setPasswordSet(Boolean passwordSet) {
        this.passwordSet = passwordSet;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getTokenExpiresAt() {
        return tokenExpiresAt;
    }

    public void setTokenExpiresAt(LocalDateTime tokenExpiresAt) {
        this.tokenExpiresAt = tokenExpiresAt;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
        this.updatedAt = LocalDateTime.now();
    }

    public Integer getLoginAttempts() {
        return loginAttempts;
    }

    public void setLoginAttempts(Integer loginAttempts) {
        this.loginAttempts = loginAttempts;
        this.updatedAt = LocalDateTime.now();
    }

    public Boolean getAccountLocked() {
        return accountLocked;
    }

    public boolean isAccountLocked() {
        return accountLocked != null && accountLocked;
    }

    public void setAccountLocked(Boolean accountLocked) {
        this.accountLocked = accountLocked;
        this.updatedAt = LocalDateTime.now();
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
        this.updatedAt = LocalDateTime.now();
    }

    // Convenience methods for backward compatibility
    public String getEmail() {
        return username;
    }

    public void setEmail(String email) {
        setUsername(email);
    }

    // Utility methods
    public boolean isTokenExpired() {
        if (tokenExpiresAt == null) {
            return true;
        }
        return LocalDateTime.now().isAfter(tokenExpiresAt);
    }

    public void incrementLoginAttempts() {
        this.loginAttempts = (this.loginAttempts == null) ? 1 : this.loginAttempts + 1;
        this.updatedAt = LocalDateTime.now();
        
        // Lock account after 5 failed attempts
        if (this.loginAttempts >= 5) {
            this.accountLocked = true;
        }
    }

    public void resetLoginAttempts() {
        this.loginAttempts = 0;
        this.accountLocked = false;
        this.lastLogin = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void unlockAccount() {
        this.accountLocked = false;
        this.loginAttempts = 0;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean canLogin() {
        return !isAccountLocked() && (tokenExpiresAt == null || !isTokenExpired());
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
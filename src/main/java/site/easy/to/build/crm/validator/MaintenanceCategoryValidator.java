package site.easy.to.build.crm.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@Component
public class MaintenanceCategoryValidator implements ConstraintValidator<ValidMaintenanceCategory, String> {

    @Autowired
    private DataSource dataSource;

    @Override
    public void initialize(ValidMaintenanceCategory constraintAnnotation) {
        // No initialization needed
    }

    @Override
    public boolean isValid(String categoryName, ConstraintValidatorContext context) {
        if (categoryName == null || categoryName.trim().isEmpty()) {
            return true; // Let @NotNull handle null validation
        }

        // Check against imported PayProp maintenance categories (case-insensitive)
        String sql = "SELECT COUNT(*) FROM payprop_maintenance_categories WHERE LOWER(description) = LOWER(?) AND is_active = true";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, categoryName.trim());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (Exception e) {
            // If database check fails, allow it (don't block ticket creation due to DB issues)
            return true;
        }
        
        return false;
    }
}
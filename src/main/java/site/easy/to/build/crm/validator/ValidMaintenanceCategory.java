package site.easy.to.build.crm.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaintenanceCategoryValidator.class)
public @interface ValidMaintenanceCategory {
    String message() default "Invalid maintenance category";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
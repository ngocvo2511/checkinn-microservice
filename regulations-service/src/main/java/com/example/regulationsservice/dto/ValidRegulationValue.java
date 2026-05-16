package com.example.regulationsservice.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = RegulationValueValidator.class)
@Documented
public @interface ValidRegulationValue {
    String message() default "Regulation value must be a non-negative decimal number not exceeding 999999.99";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

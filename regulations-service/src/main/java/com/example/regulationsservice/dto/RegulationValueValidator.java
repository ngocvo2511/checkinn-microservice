package com.example.regulationsservice.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;

public class RegulationValueValidator implements ConstraintValidator<ValidRegulationValue, String> {

    private static final BigDecimal MIN_VALUE = BigDecimal.ZERO;
    private static final BigDecimal MAX_VALUE = new BigDecimal("999999.99");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Allow null values (other validators can handle @NotBlank)
        if (value == null || value.trim().isEmpty()) {
            return true;
        }

        try {
            BigDecimal decimalValue = new BigDecimal(value.trim());

            // Check non-negative
            if (decimalValue.compareTo(MIN_VALUE) < 0) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                        "Regulation value cannot be negative: " + value
                ).addConstraintViolation();
                return false;
            }

            // Check max value
            if (decimalValue.compareTo(MAX_VALUE) > 0) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                        "Regulation value cannot exceed " + MAX_VALUE + ": " + value
                ).addConstraintViolation();
                return false;
            }

            return true;
        } catch (NumberFormatException ex) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "Regulation value must be a valid decimal number: " + value
            ).addConstraintViolation();
            return false;
        }
    }
}

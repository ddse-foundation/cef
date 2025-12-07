package org.ddse.ml.cef.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Configuration for CEF input validation.
 * 
 * <p>Provides JSR-380 Bean Validation support for all CEF DTOs and inputs.
 * Validates all requests before processing to ensure data integrity and
 * provide clear error messages.
 * 
 * <h2>Features</h2>
 * <ul>
 *   <li>Automatic bean validation via Spring's @Validated annotation</li>
 *   <li>Custom constraint validators for CEF-specific rules</li>
 *   <li>Programmatic validation for non-Spring contexts</li>
 * </ul>
 * 
 * @author mrmanna
 * @since v0.6
 */
@Configuration
public class CefValidationConfig {

    private static final Logger log = LoggerFactory.getLogger(CefValidationConfig.class);

    @Bean
    public LocalValidatorFactoryBean validator() {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        log.info("CEF Bean Validation initialized");
        return bean;
    }

    @Bean
    public CefValidator cefValidator(Validator validator) {
        return new CefValidator(validator);
    }

    /**
     * Programmatic validator for CEF inputs.
     * Use when automatic validation is not available (e.g., in non-Spring components).
     */
    public static class CefValidator {
        
        private final Validator validator;

        public CefValidator(Validator validator) {
            this.validator = validator;
        }

        /**
         * Validates an object and throws ConstraintViolationException if invalid.
         * 
         * @param object the object to validate
         * @throws ConstraintViolationException if validation fails
         */
        public <T> void validate(T object) {
            Set<ConstraintViolation<T>> violations = validator.validate(object);
            if (!violations.isEmpty()) {
                String message = violations.stream()
                        .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                        .collect(Collectors.joining(", "));
                throw new ConstraintViolationException("Validation failed: " + message, violations);
            }
        }

        /**
         * Validates an object and returns validation errors as a set.
         * Does not throw exception.
         * 
         * @param object the object to validate
         * @return set of constraint violations (empty if valid)
         */
        public <T> Set<ConstraintViolation<T>> getViolations(T object) {
            return validator.validate(object);
        }

        /**
         * Checks if an object is valid without throwing exception.
         * 
         * @param object the object to validate
         * @return true if valid, false otherwise
         */
        public <T> boolean isValid(T object) {
            return validator.validate(object).isEmpty();
        }
    }

    /**
     * Static utility for validation without Spring context.
     * Use for quick validation in standalone code.
     */
    public static class ValidationUtils {
        
        private static final Validator validator;
        
        static {
            ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
            validator = factory.getValidator();
        }

        /**
         * Validates an object and throws IllegalArgumentException if invalid.
         */
        public static <T> void validateOrThrow(T object) {
            Set<ConstraintViolation<T>> violations = validator.validate(object);
            if (!violations.isEmpty()) {
                String message = violations.stream()
                        .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                        .collect(Collectors.joining(", "));
                throw new IllegalArgumentException("Validation failed: " + message);
            }
        }

        /**
         * Validates an object and returns true if valid.
         */
        public static <T> boolean isValid(T object) {
            return validator.validate(object).isEmpty();
        }
    }
}

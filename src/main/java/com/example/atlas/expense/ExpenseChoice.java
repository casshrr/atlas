package com.example.atlas.expense;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ExpenseChoice.Validator.class)
public @interface ExpenseChoice {
    Kind value();
    String message() default "must be selected from the supported options";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    enum Kind { CATEGORY, CURRENCY }

    class Validator implements ConstraintValidator<ExpenseChoice, String> {
        private Kind kind;
        @Override public void initialize(ExpenseChoice annotation) { kind = annotation.value(); }
        @Override public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || (kind == Kind.CATEGORY ? ExpenseOptions.CATEGORIES : ExpenseOptions.CURRENCIES).contains(value);
        }
    }
}

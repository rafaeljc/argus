package io.github.rafaeljc.argus.alerts.web;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = LookbackWindowDaysValidator.class)
public @interface LookbackWindowDays {

    String message() default "must be one of the allowed lookback windows";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

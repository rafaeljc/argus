package io.github.rafaeljc.argus.alerts.web;

import io.github.rafaeljc.argus.alerts.domain.AlertLookbackWindow;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class LookbackWindowDaysValidator implements ConstraintValidator<LookbackWindowDays, Integer> {

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        return value == null || AlertLookbackWindow.ALLOWED_DAYS.contains(value);
    }
}

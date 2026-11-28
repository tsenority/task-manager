package com.taskmanager.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

    private int minCharacters;

    @Override
    public void initialize(ValidPassword constraintAnnotation) {
        minCharacters = constraintAnnotation.minCharacters();
    }

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return false;
        }
        int characterCount = password.codePointCount(0, password.length());
        int utf8ByteCount = password.getBytes(StandardCharsets.UTF_8).length;
        return characterCount >= minCharacters && utf8ByteCount <= 72;
    }
}

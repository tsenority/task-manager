package com.taskmanager.service;

import java.sql.SQLException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.hibernate.exception.ConstraintViolationException;

final class UserConstraintViolationClassifier {

    private static final Pattern USERNAME_CONSTRAINT =
            Pattern.compile("(?i)\\busers\\s*[.(]\\s*username\\b|\\busers\\.username\\b");
    private static final Pattern EMAIL_CONSTRAINT =
            Pattern.compile("(?i)\\busers\\s*[.(]\\s*email\\b|\\busers\\.email\\b");

    private UserConstraintViolationClassifier() {
    }

    static String duplicateField(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                SQLException sqlException = violation.getSQLException();
                if (sqlException != null && isUniqueViolation(sqlException)) {
                    String field = fieldFromConstraintName(violation.getConstraintName());
                    if (field != null) {
                        return field;
                    }
                }
            }
            if (cause instanceof SQLException sqlException && isUniqueViolation(sqlException)) {
                String message = sqlException.getMessage();
                if (message != null) {
                    if (USERNAME_CONSTRAINT.matcher(message).find()) {
                        return "username";
                    }
                    if (EMAIL_CONSTRAINT.matcher(message).find()) {
                        return "email";
                    }
                }
            }
        }
        return null;
    }

    private static String fieldFromConstraintName(String constraintName) {
        if (constraintName == null) {
            return null;
        }
        String normalized = constraintName.toLowerCase(Locale.ROOT);
        if (normalized.contains("username")) {
            return "username";
        }
        if (normalized.contains("email")) {
            return "email";
        }
        return null;
    }

    private static boolean isUniqueViolation(SQLException exception) {
        return "23505".equals(exception.getSQLState())
                || (exception.getErrorCode() == 1062 && "23000".equals(exception.getSQLState()));
    }
}

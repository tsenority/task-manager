package com.taskmanager.exception;

public class DuplicateAccountException extends RuntimeException {

    private final String field;

    public DuplicateAccountException(String field) {
        super("Duplicate account field: " + field);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}

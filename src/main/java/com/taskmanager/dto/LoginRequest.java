package com.taskmanager.dto;

import com.taskmanager.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Username must not be blank.")
        String username,
        @NotBlank(message = "Password must not be blank.")
        @ValidPassword(
                minCharacters = 1,
                message = "Password must contain at least one character and no more than 72 UTF-8 bytes.")
        String password) {

    public LoginRequest {
        username = username == null ? null : username.strip();
    }

    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=[REDACTED]]";
    }
}

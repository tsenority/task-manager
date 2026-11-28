package com.taskmanager.dto;

import com.taskmanager.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record RegisterRequest(
        @NotBlank(message = "Username must not be blank.")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters.")
        String username,
        @NotBlank(message = "Email must not be blank.")
        @Email(message = "Email must be a valid email address.")
        @Size(max = 254, message = "Email must not exceed 254 characters.")
        String email,
        @NotBlank(message = "Password must not be blank.")
        @ValidPassword(minCharacters = 8)
        String password) {

    public RegisterRequest {
        username = username == null ? null : username.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + ", email=" + email + ", password=[REDACTED]]";
    }
}

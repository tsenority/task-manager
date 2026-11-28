package com.taskmanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.taskmanager.dto.RegisterRequest;
import com.taskmanager.dto.UserResponse;
import com.taskmanager.entity.User;
import com.taskmanager.exception.DuplicateAccountException;
import com.taskmanager.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({AuthService.class, com.taskmanager.config.PasswordEncoderConfig.class})
class AuthServiceDatabaseTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registrationPersistsNormalizedIdentityAndBcryptHashOnly() {
        UserResponse response = authService.register(
                new RegisterRequest("  Alice  ", "  ALICE@Example.COM  ", "correct horse"));

        User stored = userRepository.findById(response.id()).orElseThrow();
        assertThat(stored.getUsername()).isEqualTo("Alice");
        assertThat(stored.getEmail()).isEqualTo("alice@example.com");
        assertThat(stored.getPassword()).startsWith("$2");
        assertThat(passwordEncoder.matches("correct horse", stored.getPassword())).isTrue();
        assertThat(response.toString()).doesNotContain(stored.getPassword()).doesNotContain("correct horse");
    }

    @Test
    void registrationRejectsExistingUsernameAndEmailWithoutChangingExistingRows() {
        User existing = userRepository.saveAndFlush(
                new User("existing-user", "existing@example.com", "existing-hash"));

        assertThatThrownBy(() -> authService.register(
                        new RegisterRequest("existing-user", "new@example.com", "correct horse")))
                .isInstanceOf(DuplicateAccountException.class)
                .hasMessage("Duplicate account field: username");
        assertThatThrownBy(() -> authService.register(
                        new RegisterRequest("new-user", "EXISTING@example.com", "correct horse")))
                .isInstanceOf(DuplicateAccountException.class)
                .hasMessage("Duplicate account field: email");

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(userRepository.findById(existing.getId())).contains(existing);
    }
}

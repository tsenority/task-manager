package com.taskmanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.taskmanager.dto.LoginRequest;
import com.taskmanager.dto.RegisterRequest;
import com.taskmanager.dto.UserResponse;
import com.taskmanager.entity.User;
import com.taskmanager.exception.DuplicateAccountException;
import com.taskmanager.exception.InvalidCredentialsException;
import com.taskmanager.repository.UserRepository;
import java.sql.SQLException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        authService = new AuthService(userRepository, passwordEncoder);
    }

    @Test
    void registrationNormalizesIdentityAndStoresOnlyBcryptPasswordHash() {
        RegisterRequest request =
                new RegisterRequest("  Alice  ", "  ALICE@Example.COM  ", "correct horse");
        assertThat(request.toString()).doesNotContain("correct horse").contains("[REDACTED]");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getUsername()).isEqualTo("Alice");
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getPassword()).isNotEqualTo("correct horse");
        assertThat(saved.getPassword()).startsWith("$2");
        assertThat(passwordEncoder.matches("correct horse", saved.getPassword())).isTrue();
        assertThat(response).isEqualTo(new UserResponse(null, "Alice", "alice@example.com"));
        assertThat(response.toString())
                .doesNotContain("correct horse")
                .doesNotContain(saved.getPassword());
    }

    @Test
    void existingUsernameIsRejectedBeforePasswordEncodingOrPersistence() {
        when(userRepository.existsByUsername("Alice")).thenReturn(true);

        assertThatThrownBy(() ->
                        authService.register(new RegisterRequest("Alice", "alice@example.com", "correct horse")))
                .isInstanceOf(DuplicateAccountException.class)
                .hasMessage("Duplicate account field: username");

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void namedUniqueConstraintViolationBecomesDuplicateAccountConflict() {
        SQLException sqlException = new SQLException("unique key conflict", "23505", 23505);
        ConstraintViolationException constraintViolation =
                new ConstraintViolationException("unique key conflict", sqlException, "uk_users_email");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("constraint violation", constraintViolation));

        assertThatThrownBy(() ->
                        authService.register(new RegisterRequest("Alice", "alice@example.com", "correct horse")))
                .isInstanceOf(DuplicateAccountException.class)
                .hasMessage("Duplicate account field: email");
    }

    @Test
    void opaqueUniqueConstraintIsMappedOnlyWhenDatabaseConfirmsRequestedDuplicate() {
        SQLException sqlException = new SQLException("unique key conflict", "23505", 23505);
        ConstraintViolationException constraintViolation =
                new ConstraintViolationException("unique key conflict", sqlException, "UK_OPAQUE_INDEX");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("constraint violation", constraintViolation));
        when(userRepository.existsByUsername("Alice")).thenReturn(false, true);

        assertThatThrownBy(() ->
                        authService.register(new RegisterRequest("Alice", "alice@example.com", "correct horse")))
                .isInstanceOf(DuplicateAccountException.class)
                .hasMessage("Duplicate account field: username");
    }

    @Test
    void nonUniqueIntegrityViolationIsNotMisreportedAsDuplicateAccount() {
        SQLException sqlException = new SQLException("check constraint conflict", "23514", 23514);
        ConstraintViolationException constraintViolation =
                new ConstraintViolationException("check constraint conflict", sqlException, "uk_users_username");
        DataIntegrityViolationException databaseException =
                new DataIntegrityViolationException("constraint violation", constraintViolation);
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(databaseException);

        assertThatThrownBy(() ->
                        authService.register(new RegisterRequest("Alice", "alice@example.com", "correct horse")))
                .isSameAs(databaseException);
    }

    @Test
    void loginWithCorrectPasswordReturnsOnlySafeUserFields() {
        String hash = passwordEncoder.encode("correct horse");
        User user = new User("Alice", "alice@example.com", hash);
        user.setId(42L);
        when(userRepository.findByUsername("Alice")).thenReturn(Optional.of(user));

        UserResponse response = authService.login(new LoginRequest(" Alice ", "correct horse"));

        assertThat(response).isEqualTo(new UserResponse(42L, "Alice", "alice@example.com"));
        assertThat(response.toString()).doesNotContain(hash).doesNotContain("correct horse");
    }

    @Test
    void unknownUserAndIncorrectPasswordUseTheSameGenericFailure() {
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("Alice"))
                .thenReturn(Optional.of(new User(
                        "Alice", "alice@example.com", passwordEncoder.encode("correct horse"))));

        assertThatThrownBy(() -> authService.login(new LoginRequest("missing", "incorrect password")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid username or password.");
        assertThatThrownBy(() -> authService.login(new LoginRequest("Alice", "incorrect password")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid username or password.");
    }

    @Test
    void unknownUsernamePerformsBcryptComparisonAgainstDummyHash() {
        PasswordEncoder encoder = org.mockito.Mockito.spy(new BCryptPasswordEncoder(4));
        AuthService service = new AuthService(userRepository, encoder);
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("missing", "incorrect password")))
                .isInstanceOf(InvalidCredentialsException.class);

        ArgumentCaptor<String> comparedHash = ArgumentCaptor.forClass(String.class);
        verify(encoder).matches(org.mockito.ArgumentMatchers.eq("incorrect password"), comparedHash.capture());
        assertThat(comparedHash.getValue()).startsWith("$2");
        assertThat(comparedHash.getValue()).isNotEqualTo("incorrect password");
    }
}

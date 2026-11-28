package com.taskmanager.service;

import com.taskmanager.dto.LoginRequest;
import com.taskmanager.dto.RegisterRequest;
import com.taskmanager.dto.UserResponse;
import com.taskmanager.entity.User;
import com.taskmanager.exception.DuplicateAccountException;
import com.taskmanager.exception.InvalidCredentialsException;
import com.taskmanager.repository.UserRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public UserResponse register(RegisterRequest request) {
        String username = request.username().strip();
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateAccountException("username");
        }
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateAccountException("email");
        }

        User user = new User(username, email, passwordEncoder.encode(request.password()));
        try {
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            String field = UserConstraintViolationClassifier.duplicateField(exception);
            if (field == null && userRepository.existsByUsername(username)) {
                field = "username";
            }
            if (field == null && userRepository.existsByEmail(email)) {
                field = "email";
            }
            if (field == null) {
                throw exception;
            }
            throw new DuplicateAccountException(field);
        }
    }

    @Transactional(readOnly = true)
    public UserResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username().strip()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }
        return UserResponse.from(user);
    }
}

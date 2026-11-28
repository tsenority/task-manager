package com.taskmanager.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.taskmanager.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByUsernameReturnsMatchingUserOrEmptyWhenMissing() {
        // Arrange
        User user = userRepository.saveAndFlush(new User("alice", "alice@example.com", "password"));

        // Act
        var foundUser = userRepository.findByUsername("alice");
        var missingUser = userRepository.findByUsername("missing");

        // Assert
        assertThat(foundUser).contains(user);
        assertThat(missingUser).isEmpty();
    }

    @Test
    void findByEmailReturnsMatchingUserOrEmptyWhenMissing() {
        // Arrange
        User user = userRepository.saveAndFlush(new User("bob", "bob@example.com", "password"));

        // Act
        var foundUser = userRepository.findByEmail("bob@example.com");
        var missingUser = userRepository.findByEmail("missing@example.com");

        // Assert
        assertThat(foundUser).contains(user);
        assertThat(missingUser).isEmpty();
    }

    @Test
    void existsByUsernameReturnsTrueForExistingUsernameAndFalseOtherwise() {
        // Arrange
        userRepository.saveAndFlush(new User("carol", "carol@example.com", "password"));

        // Act
        boolean existingUsername = userRepository.existsByUsername("carol");
        boolean missingUsername = userRepository.existsByUsername("missing");

        // Assert
        assertThat(existingUsername).isTrue();
        assertThat(missingUsername).isFalse();
    }

    @Test
    void existsByEmailReturnsTrueForExistingEmailAndFalseOtherwise() {
        // Arrange
        userRepository.saveAndFlush(new User("dave", "dave@example.com", "password"));

        // Act
        boolean existingEmail = userRepository.existsByEmail("dave@example.com");
        boolean missingEmail = userRepository.existsByEmail("missing@example.com");

        // Assert
        assertThat(existingEmail).isTrue();
        assertThat(missingEmail).isFalse();
    }

    @Test
    void databaseEnforcesUsernameUniqueness() {
        userRepository.saveAndFlush(new User("unique-user", "first@example.com", "hash"));

        assertThatThrownBy(() ->
                        userRepository.saveAndFlush(new User("unique-user", "second@example.com", "hash")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseEnforcesEmailUniqueness() {
        userRepository.saveAndFlush(new User("first-user", "unique@example.com", "hash"));

        assertThatThrownBy(() ->
                        userRepository.saveAndFlush(new User("second-user", "unique@example.com", "hash")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

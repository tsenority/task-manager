package com.taskmanager.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.taskmanager.entity.Task;
import com.taskmanager.entity.User;
import com.taskmanager.enums.TaskPriority;
import com.taskmanager.enums.TaskStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class TaskRepositoryTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findAllByUserIdReturnsOnlyTasksOwnedByRequestedUser() {
        // Arrange
        User firstUser = userRepository.saveAndFlush(new User("first", "first@example.com", "password"));
        User secondUser = userRepository.saveAndFlush(new User("second", "second@example.com", "password"));
        Task firstUsersTask = taskRepository.saveAndFlush(createTask("First user's task", firstUser));
        Task secondUsersTask = taskRepository.saveAndFlush(createTask("Second user's task", secondUser));

        // Act
        List<Task> firstUsersTasks = taskRepository.findAllByUser_Id(firstUser.getId());
        List<Task> secondUsersTasks = taskRepository.findAllByUser_Id(secondUser.getId());
        List<Task> tasksWithoutOwner = taskRepository.findAllByUser_Id(Long.MAX_VALUE);

        // Assert
        assertThat(firstUsersTasks).containsExactly(firstUsersTask).doesNotContain(secondUsersTask);
        assertThat(secondUsersTasks).containsExactly(secondUsersTask).doesNotContain(firstUsersTask);
        assertThat(tasksWithoutOwner).isEmpty();
    }

    @Test
    void findByIdAndUserIdReturnsOwnedTaskAndEmptyForMissingOrOtherUsersTask() {
        // Arrange
        User firstUser = userRepository.saveAndFlush(new User("owner", "owner@example.com", "password"));
        User secondUser = userRepository.saveAndFlush(new User("other", "other@example.com", "password"));
        Task firstUsersTask = taskRepository.saveAndFlush(createTask("Owned task", firstUser));
        Task secondUsersTask = taskRepository.saveAndFlush(createTask("Other user's task", secondUser));

        // Act
        var ownedTask = taskRepository.findByIdAndUser_Id(firstUsersTask.getId(), firstUser.getId());
        var otherUsersTask = taskRepository.findByIdAndUser_Id(secondUsersTask.getId(), firstUser.getId());
        var missingTask = taskRepository.findByIdAndUser_Id(Long.MAX_VALUE, firstUser.getId());

        // Assert
        assertThat(ownedTask).contains(firstUsersTask);
        assertThat(otherUsersTask).isEmpty();
        assertThat(missingTask).isEmpty();
    }

    private Task createTask(String title, User user) {
        return new Task(
                title,
                "Repository test task",
                TaskStatus.TODO,
                TaskPriority.MEDIUM,
                LocalDate.of(2026, 12, 31),
                LocalDateTime.of(2026, 1, 1, 12, 0),
                user);
    }
}

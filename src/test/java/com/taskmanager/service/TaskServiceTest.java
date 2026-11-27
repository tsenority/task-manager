package com.taskmanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.taskmanager.entity.Task;
import com.taskmanager.entity.User;
import com.taskmanager.enums.TaskPriority;
import com.taskmanager.enums.TaskStatus;
import com.taskmanager.exception.TaskNotFoundException;
import com.taskmanager.exception.UserNotFoundException;
import com.taskmanager.repository.TaskRepository;
import com.taskmanager.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TaskService taskService;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = new User("owner", "owner@example.com", "password");
        owner.setId(1L);
    }

    @Test
    void createTaskAssignsOwnerTimestampAndDefaults() {
        // Arrange
        User suppliedOwner = new User("other", "other@example.com", "password");
        Task task = new Task("Task", "Description", null, null, null, null, suppliedOwner);
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(taskRepository.save(task)).thenReturn(task);

        LocalDateTime beforeCreation = LocalDateTime.now();

        // Act
        Task createdTask = taskService.createTask(owner.getId(), task);
        LocalDateTime afterCreation = LocalDateTime.now();

        // Assert
        assertThat(createdTask.getUser()).isSameAs(owner);
        assertThat(createdTask.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(createdTask.getPriority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(createdTask.getCreatedAt()).isBetween(beforeCreation, afterCreation);
        verify(taskRepository).save(task);
    }

    @Test
    void createTaskPreservesProvidedStatusAndPriority() {
        // Arrange
        Task task = new Task(
                "Task", "Description", TaskStatus.IN_PROGRESS, TaskPriority.HIGH, null, null, null);
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(taskRepository.save(task)).thenReturn(task);

        // Act
        Task createdTask = taskService.createTask(owner.getId(), task);

        // Assert
        assertThat(createdTask.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(createdTask.getPriority()).isEqualTo(TaskPriority.HIGH);
    }

    @Test
    void createTaskClearsProvidedIdToCreateANewRecord() {
        // Arrange
        Task task = new Task(
                "Task", "Description", TaskStatus.TODO, TaskPriority.MEDIUM, null, null, null);
        task.setId(500L);
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(taskRepository.save(task)).thenReturn(task);

        // Act
        Task createdTask = taskService.createTask(owner.getId(), task);

        // Assert
        assertThat(createdTask.getId()).isNull();
        verify(taskRepository).save(task);
    }

    @Test
    void getTasksChecksUserAndQueriesOnlyThatUsersTasks() {
        // Arrange
        List<Task> tasks = List.of(taskOwnedBy(owner));
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(taskRepository.findAllByUser_Id(owner.getId())).thenReturn(tasks);

        // Act
        List<Task> result = taskService.getTasks(owner.getId());

        // Assert
        assertThat(result).isSameAs(tasks);
        verify(taskRepository).findAllByUser_Id(owner.getId());
    }

    @Test
    void getTaskReturnsTheOwnersTask() {
        // Arrange
        Task task = taskOwnedBy(owner);
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(taskRepository.findByIdAndUser_Id(task.getId(), owner.getId())).thenReturn(Optional.of(task));

        // Act
        Task result = taskService.getTask(owner.getId(), task.getId());

        // Assert
        assertThat(result).isSameAs(task);
    }

    @Test
    void updateTaskReplacesEditableFieldsAndPreservesOwnershipAndCreationTime() {
        // Arrange
        Task task = taskOwnedBy(owner);
        LocalDateTime createdAt = task.getCreatedAt();
        Task replacement = new Task(
                "Updated", "Updated description", TaskStatus.COMPLETED, TaskPriority.LOW,
                LocalDate.of(2027, 1, 15), null, null);
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(taskRepository.findByIdAndUser_Id(task.getId(), owner.getId())).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        // Act
        Task updatedTask = taskService.updateTask(owner.getId(), task.getId(), replacement);

        // Assert
        assertThat(updatedTask.getTitle()).isEqualTo("Updated");
        assertThat(updatedTask.getDescription()).isEqualTo("Updated description");
        assertThat(updatedTask.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(updatedTask.getPriority()).isEqualTo(TaskPriority.LOW);
        assertThat(updatedTask.getDueDate()).isEqualTo(LocalDate.of(2027, 1, 15));
        assertThat(updatedTask.getId()).isEqualTo(task.getId());
        assertThat(updatedTask.getUser()).isSameAs(owner);
        assertThat(updatedTask.getCreatedAt()).isEqualTo(createdAt);
        verify(taskRepository).save(task);
    }

    @Test
    void updateTaskRejectsNullStatusWithoutSaving() {
        assertInvalidReplacement(new Task(
                "Updated", "Description", null, TaskPriority.MEDIUM, null, null, null),
                "Task status is required for update");
    }

    @Test
    void updateTaskRejectsNullPriorityWithoutSaving() {
        assertInvalidReplacement(new Task(
                "Updated", "Description", TaskStatus.TODO, null, null, null, null),
                "Task priority is required for update");
    }

    @Test
    void deleteTaskDeletesOnlyTheOwnersTask() {
        // Arrange
        Task task = taskOwnedBy(owner);
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(taskRepository.findByIdAndUser_Id(task.getId(), owner.getId())).thenReturn(Optional.of(task));

        // Act
        taskService.deleteTask(owner.getId(), task.getId());

        // Assert
        verify(taskRepository).delete(task);
    }

    @Test
    void operationsRejectNonexistentUserBeforeAccessingTasks() {
        // Arrange
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // Act
        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class, () -> taskService.getTasks(99L));

        // Assert
        assertThat(exception).hasMessage("User not found: 99");
        verify(taskRepository, never()).findAllByUser_Id(99L);
    }

    @Test
    void getTaskRejectsNonexistentTask() {
        // Arrange
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(taskRepository.findByIdAndUser_Id(10L, owner.getId())).thenReturn(Optional.empty());

        // Act / Assert
        assertThrows(TaskNotFoundException.class, () -> taskService.getTask(owner.getId(), 10L));
    }

    @Test
    void getTaskHidesWhetherTaskExistsForAnotherUser() {
        // Arrange
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(taskRepository.findByIdAndUser_Id(10L, owner.getId())).thenReturn(Optional.empty());

        // Act / Assert
        assertThrows(TaskNotFoundException.class, () -> taskService.getTask(owner.getId(), 10L));
    }

    @Test
    void updateTaskHidesWhetherTaskExistsForAnotherUserWithoutSaving() {
        // Arrange
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(taskRepository.findByIdAndUser_Id(10L, owner.getId())).thenReturn(Optional.empty());

        // Act / Assert
        assertThrows(
                TaskNotFoundException.class,
                () -> taskService.updateTask(owner.getId(), 10L, taskOwnedBy(owner)));
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void deleteTaskHidesWhetherTaskExistsForAnotherUserWithoutDeleting() {
        // Arrange
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(taskRepository.findByIdAndUser_Id(10L, owner.getId())).thenReturn(Optional.empty());

        // Act / Assert
        assertThrows(TaskNotFoundException.class, () -> taskService.deleteTask(owner.getId(), 10L));
        verify(taskRepository, never()).delete(any(Task.class));
    }

    private void assertInvalidReplacement(Task replacement, String expectedMessage) {
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> taskService.updateTask(owner.getId(), 10L, replacement));

        assertThat(exception).hasMessage(expectedMessage);
        verify(taskRepository, never()).findByIdAndUser_Id(10L, owner.getId());
        verify(taskRepository, never()).save(any(Task.class));
    }

    private Task taskOwnedBy(User user) {
        Task task = new Task(
                "Task", "Description", TaskStatus.TODO, TaskPriority.MEDIUM,
                LocalDate.of(2026, 12, 31), LocalDateTime.of(2026, 1, 1, 12, 0), user);
        task.setId(10L);
        return task;
    }
}

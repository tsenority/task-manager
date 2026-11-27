package com.taskmanager.service;

import com.taskmanager.entity.Task;
import com.taskmanager.entity.User;
import com.taskmanager.enums.TaskPriority;
import com.taskmanager.enums.TaskStatus;
import com.taskmanager.exception.TaskNotFoundException;
import com.taskmanager.exception.UserNotFoundException;
import com.taskmanager.repository.TaskRepository;
import com.taskmanager.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public TaskService(TaskRepository taskRepository, UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    public Task createTask(Long userId, Task task) {
        User user = requireUser(userId);
        task.setId(null);
        task.setUser(user);
        task.setCreatedAt(LocalDateTime.now());
        if (task.getStatus() == null) {
            task.setStatus(TaskStatus.TODO);
        }
        if (task.getPriority() == null) {
            task.setPriority(TaskPriority.MEDIUM);
        }
        return taskRepository.save(task);
    }

    public List<Task> getTasks(Long userId) {
        requireUser(userId);
        return taskRepository.findAllByUser_Id(userId);
    }

    public Task getTask(Long userId, Long taskId) {
        requireUser(userId);
        return requireOwnedTask(userId, taskId);
    }

    public Task updateTask(Long userId, Long taskId, Task replacement) {
        requireUser(userId);
        if (replacement.getStatus() == null) {
            throw new IllegalArgumentException("Task status is required for update");
        }
        if (replacement.getPriority() == null) {
            throw new IllegalArgumentException("Task priority is required for update");
        }
        Task task = requireOwnedTask(userId, taskId);
        task.setTitle(replacement.getTitle());
        task.setDescription(replacement.getDescription());
        task.setStatus(replacement.getStatus());
        task.setPriority(replacement.getPriority());
        task.setDueDate(replacement.getDueDate());
        return taskRepository.save(task);
    }

    public void deleteTask(Long userId, Long taskId) {
        requireUser(userId);
        taskRepository.delete(requireOwnedTask(userId, taskId));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private Task requireOwnedTask(Long userId, Long taskId) {
        return taskRepository.findByIdAndUser_Id(taskId, userId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));
    }
}

package com.taskmanager.dto;

import com.taskmanager.enums.TaskPriority;
import com.taskmanager.enums.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record TaskRequest(
        @NotBlank(message = "Title must not be blank.")
        @Size(max = 100, message = "Title must not exceed 100 characters.")
        String title,
        @Size(max = 500, message = "Description must not exceed 500 characters.")
        String description,
        @NotNull(groups = Update.class, message = "Status is required for updates.")
        TaskStatus status,
        @NotNull(groups = Update.class, message = "Priority is required for updates.")
        TaskPriority priority,
        LocalDate dueDate) {

    public interface Update {
    }
}

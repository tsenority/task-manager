package com.taskmanager.dto;

import com.taskmanager.enums.TaskPriority;
import com.taskmanager.enums.TaskStatus;
import java.time.LocalDate;

public record TaskRequest(
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        LocalDate dueDate) {
}

package com.taskmanager.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.taskmanager.entity.Task;
import com.taskmanager.entity.User;
import com.taskmanager.enums.TaskPriority;
import com.taskmanager.enums.TaskStatus;
import com.taskmanager.exception.TaskNotFoundException;
import com.taskmanager.exception.UserNotFoundException;
import com.taskmanager.service.TaskService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(TaskController.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;

    @Test
    void getTasksReturnsTasksWithoutUserData() throws Exception {
        Task task = task(10L);
        when(taskService.getTasks(1L)).thenReturn(List.of(task));

        MvcResult result = mockMvc.perform(get("/api/users/1/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].title").value("Original task"))
                .andExpect(jsonPath("$[0].status").value("TODO"))
                .andExpect(jsonPath("$[0].priority").value("MEDIUM"))
                .andExpect(jsonPath("$[0].user").doesNotExist())
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andReturn();

        assertResponseDoesNotExposeUser(result);
        verify(taskService).getTasks(1L);
    }

    @Test
    void createTaskReturnsCreatedResponseAndDelegatesOnlyEditableFields() throws Exception {
        Task savedTask = task(10L);
        when(taskService.createTask(eq(1L), any(Task.class))).thenReturn(savedTask);

        MvcResult result = mockMvc.perform(post("/api/users/1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Original task",
                                  "description": "Original description",
                                  "status": "TODO",
                                  "priority": "MEDIUM",
                                  "dueDate": "2027-01-15",
                                  "id": 999,
                                  "createdAt": "2020-01-01T00:00:00",
                                  "user": {"id": 99, "password": "do-not-accept"}
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.endsWith("/api/users/1/tasks/10")))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.title").value("Original task"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-27T18:00:00"))
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskService).createTask(eq(1L), taskCaptor.capture());
        assertThat(taskCaptor.getValue().getTitle()).isEqualTo("Original task");
        assertThat(taskCaptor.getValue().getDescription()).isEqualTo("Original description");
        assertThat(taskCaptor.getValue().getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(taskCaptor.getValue().getPriority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(taskCaptor.getValue().getDueDate()).isEqualTo(LocalDate.of(2027, 1, 15));
        assertThat(taskCaptor.getValue().getId()).isNull();
        assertThat(taskCaptor.getValue().getCreatedAt()).isNull();
        assertThat(taskCaptor.getValue().getUser()).isNull();
        assertResponseDoesNotExposeUser(result);
    }

    @Test
    void getTaskReturnsTheRequestedTaskWithoutUserData() throws Exception {
        when(taskService.getTask(1L, 10L)).thenReturn(task(10L));

        MvcResult result = mockMvc.perform(get("/api/users/1/tasks/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.title").value("Original task"))
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        assertResponseDoesNotExposeUser(result);
        verify(taskService).getTask(1L, 10L);
    }

    @Test
    void updateTaskReturnsUpdatedResponseAndDelegatesPathIdsAndEditableFields() throws Exception {
        Task updatedTask = new Task(
                "Updated task",
                "Updated description",
                TaskStatus.COMPLETED,
                TaskPriority.HIGH,
                LocalDate.of(2027, 2, 1),
                LocalDateTime.of(2026, 11, 27, 18, 0),
                user());
        updatedTask.setId(10L);
        when(taskService.updateTask(eq(1L), eq(10L), any(Task.class))).thenReturn(updatedTask);

        MvcResult result = mockMvc.perform(put("/api/users/1/tasks/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Updated task",
                                  "description": "Updated description",
                                  "status": "COMPLETED",
                                  "priority": "HIGH",
                                  "dueDate": "2027-02-01"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated task"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskService).updateTask(eq(1L), eq(10L), taskCaptor.capture());
        assertThat(taskCaptor.getValue().getTitle()).isEqualTo("Updated task");
        assertThat(taskCaptor.getValue().getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(taskCaptor.getValue().getDueDate()).isEqualTo(LocalDate.of(2027, 2, 1));
        assertThat(taskCaptor.getValue().getId()).isNull();
        assertThat(taskCaptor.getValue().getUser()).isNull();
        assertResponseDoesNotExposeUser(result);
    }

    @Test
    void deleteTaskReturnsNoContentAndDelegatesPathIds() throws Exception {
        mockMvc.perform(delete("/api/users/1/tasks/10"))
                .andExpect(status().isNoContent())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(""));

        verify(taskService).deleteTask(1L, 10L);
    }

    @Test
    void missingUserReturnsNotFound() throws Exception {
        when(taskService.getTasks(99L)).thenThrow(new UserNotFoundException(99L));

        mockMvc.perform(get("/api/users/99/tasks"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("User not found: 99"));
    }

    @Test
    void missingOrUnownedTaskReturnsNotFound() throws Exception {
        when(taskService.getTask(1L, 99L)).thenThrow(new TaskNotFoundException(99L));

        mockMvc.perform(get("/api/users/1/tasks/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Task not found: 99"));
    }

    @Test
    void invalidUpdateReturnsBadRequest() throws Exception {
        when(taskService.updateTask(eq(1L), eq(10L), any(Task.class)))
                .thenThrow(new IllegalArgumentException("Task status is required for update"));

        mockMvc.perform(put("/api/users/1/tasks/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Updated task",
                                  "description": "Updated description",
                                  "priority": "HIGH"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Task status is required for update"));
    }

    @Test
    void malformedRequestBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/users/1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Invalid request body"));
    }

    private Task task(Long id) {
        Task task = new Task(
                "Original task",
                "Original description",
                TaskStatus.TODO,
                TaskPriority.MEDIUM,
                LocalDate.of(2027, 1, 15),
                LocalDateTime.of(2026, 11, 27, 18, 0),
                user());
        task.setId(id);
        return task;
    }

    private User user() {
        User user = new User("owner", "owner@example.com", "secret-password");
        user.setId(1L);
        return user;
    }

    private void assertResponseDoesNotExposeUser(MvcResult result) throws Exception {
        String response = result.getResponse().getContentAsString();
        assertThat(response).doesNotContain("secret-password");
        assertThat(response).doesNotContain("\"user\"");
        assertThat(response).doesNotContain("\"password\"");
    }
}

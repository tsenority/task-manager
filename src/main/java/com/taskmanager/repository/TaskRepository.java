package com.taskmanager.repository;

import com.taskmanager.entity.Task;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findAllByUser_Id(Long userId);

    Optional<Task> findByIdAndUser_Id(Long taskId, Long userId);
}

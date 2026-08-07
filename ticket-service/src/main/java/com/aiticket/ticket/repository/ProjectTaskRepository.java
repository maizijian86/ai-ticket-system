package com.aiticket.ticket.repository;

import com.aiticket.ticket.entity.ProjectTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectTaskRepository extends JpaRepository<ProjectTask, Long> {

    // Find by project
    List<ProjectTask> findByProjectIdOrderByCreatedAtAsc(Long projectId);

    // Find by project and status
    List<ProjectTask> findByProjectIdAndStatusOrderByPriorityAscCreatedAtAsc(Long projectId, String status);

    // Find by module
    List<ProjectTask> findByModuleIdOrderByCreatedAtAsc(Long moduleId);

    // Find by module and status
    List<ProjectTask> findByModuleIdAndStatusOrderByPriorityAscCreatedAtAsc(Long moduleId, String status);

    // Find root tasks (no parent)
    List<ProjectTask> findByProjectIdAndParentTaskIdIsNullOrderByCreatedAtAsc(Long projectId);

    // Find subtasks
    List<ProjectTask> findByParentTaskIdOrderByCreatedAtAsc(Long parentTaskId);

    // Count by project
    long countByProjectId(Long projectId);

    // Count by project and status
    long countByProjectIdAndStatus(Long projectId, String status);

    // Count by module
    long countByModuleId(Long moduleId);

    // Count by module and status
    long countByModuleIdAndStatus(Long moduleId, String status);

    // Update status
    @Modifying
    @Query("UPDATE ProjectTask t SET t.status = :status WHERE t.id = :taskId")
    int updateStatus(@Param("taskId") Long taskId, @Param("status") String status);

    // Complete task
    @Modifying
    @Query("UPDATE ProjectTask t SET t.status = 'DONE', t.completedAt = :now WHERE t.id = :taskId")
    int completeTask(@Param("taskId") Long taskId, @Param("now") java.time.LocalDateTime now);

    // Delete by project
    void deleteByProjectId(Long projectId);

    // Delete by module
    void deleteByModuleId(Long moduleId);

    // Find tasks needing AI estimation
    @Query("SELECT t FROM ProjectTask t WHERE t.aiEstimatedHours IS NULL AND t.deletedAt IS NULL")
    List<ProjectTask> findTasksNeedingEstimation();
}

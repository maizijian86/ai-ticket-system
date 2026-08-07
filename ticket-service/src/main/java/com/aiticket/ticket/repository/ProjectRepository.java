package com.aiticket.ticket.repository;

import com.aiticket.ticket.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long>, JpaSpecificationExecutor<Project> {

    // Find by creator
    Page<Project> findByCreatorIdAndDeletedAtIsNull(Long creatorId, Pageable pageable);

    // Find by status
    Page<Project> findByStatusAndDeletedAtIsNull(String status, Pageable pageable);

    // Count by status
    long countByStatusAndDeletedAtIsNull(String status);

    // Find active projects (not completed/archived)
    @Query("SELECT p FROM Project p WHERE p.status IN ('PLANNING', 'IN_PROGRESS', 'TESTING') AND p.deletedAt IS NULL ORDER BY p.updatedAt DESC")
    List<Project> findActiveProjects();

    // Update progress
    @Modifying
    @Query("UPDATE Project p SET p.progress = :progress, p.totalTasks = :totalTasks, p.completedTasks = :completedTasks, p.updatedAt = :now WHERE p.id = :projectId")
    int updateProgress(@Param("projectId") Long projectId,
                       @Param("progress") Integer progress,
                       @Param("totalTasks") Integer totalTasks,
                       @Param("completedTasks") Integer completedTasks,
                       @Param("now") LocalDateTime now);

    // Update status
    @Modifying
    @Query("UPDATE Project p SET p.status = :status, p.updatedAt = :now WHERE p.id = :projectId")
    int updateStatus(@Param("projectId") Long projectId,
                     @Param("status") String status,
                     @Param("now") LocalDateTime now);

    // Update AI analysis results
    @Modifying
    @Query("UPDATE Project p SET p.aiPrd = :aiPrd, p.aiTechStack = :aiTechStack, p.aiEstimatedHours = :aiEstimatedHours, p.aiEstimatedDays = :aiEstimatedDays, p.aiRiskAnalysis = :aiRiskAnalysis, p.updatedAt = :now WHERE p.id = :projectId")
    int updateAiAnalysis(@Param("projectId") Long projectId,
                         @Param("aiPrd") String aiPrd,
                         @Param("aiTechStack") String aiTechStack,
                         @Param("aiEstimatedHours") Integer aiEstimatedHours,
                         @Param("aiEstimatedDays") Integer aiEstimatedDays,
                         @Param("aiRiskAnalysis") String aiRiskAnalysis,
                         @Param("now") LocalDateTime now);
}

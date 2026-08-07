package com.aiticket.ticket.repository;

import com.aiticket.ticket.entity.ProjectModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectModuleRepository extends JpaRepository<ProjectModule, Long> {

    // Find by project
    List<ProjectModule> findByProjectIdOrderBySortOrderAsc(Long projectId);

    // Find by project and status
    List<ProjectModule> findByProjectIdAndStatusOrderBySortOrderAsc(Long projectId, String status);

    // Count by project
    long countByProjectId(Long projectId);

    // Count completed by project
    long countByProjectIdAndStatus(Long projectId, String status);

    // Update progress
    @Modifying
    @Query("UPDATE ProjectModule m SET m.progress = :progress, m.totalTasks = :totalTasks, m.completedTasks = :completedTasks WHERE m.id = :moduleId")
    int updateProgress(@Param("moduleId") Long moduleId,
                       @Param("progress") Integer progress,
                       @Param("totalTasks") Integer totalTasks,
                       @Param("completedTasks") Integer completedTasks);

    // Update status
    @Modifying
    @Query("UPDATE ProjectModule m SET m.status = :status WHERE m.id = :moduleId")
    int updateStatus(@Param("moduleId") Long moduleId, @Param("status") String status);

    // Delete by project
    void deleteByProjectId(Long projectId);
}

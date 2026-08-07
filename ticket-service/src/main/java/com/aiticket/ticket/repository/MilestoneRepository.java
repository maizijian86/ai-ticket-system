package com.aiticket.ticket.repository;

import com.aiticket.ticket.entity.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MilestoneRepository extends JpaRepository<Milestone, Long> {

    // Find by project
    List<Milestone> findByProjectIdOrderBySortOrderAsc(Long projectId);

    // Find by project and status
    List<Milestone> findByProjectIdAndStatusOrderBySortOrderAsc(Long projectId, String status);

    // Count by project
    long countByProjectId(Long projectId);

    // Count achieved by project
    long countByProjectIdAndStatus(Long projectId, String status);

    // Update status
    @Modifying
    @Query("UPDATE Milestone m SET m.status = :status, m.actualDate = :actualDate WHERE m.id = :milestoneId")
    int updateStatus(@Param("milestoneId") Long milestoneId,
                     @Param("status") String status,
                     @Param("actualDate") java.time.LocalDate actualDate);

    // Delete by project
    void deleteByProjectId(Long projectId);
}

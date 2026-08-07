package com.aiticket.ticket.repository;

import com.aiticket.ticket.entity.ProjectDiscussion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectDiscussionRepository extends JpaRepository<ProjectDiscussion, Long> {

    // Find by project
    List<ProjectDiscussion> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    // Find by project with pagination
    Page<ProjectDiscussion> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);

    // Find by target (module or task)
    List<ProjectDiscussion> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId);

    // Count by project
    long countByProjectId(Long projectId);

    // Delete by project
    void deleteByProjectId(Long projectId);

    // Delete by target
    void deleteByTargetTypeAndTargetId(String targetType, Long targetId);
}

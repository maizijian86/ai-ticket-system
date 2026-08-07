package com.aiticket.ticket.service;

import com.aiticket.ticket.entity.Project;
import com.aiticket.ticket.entity.ProjectModule;
import com.aiticket.ticket.entity.ProjectTask;
import com.aiticket.ticket.entity.Milestone;
import com.aiticket.ticket.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectModuleRepository moduleRepository;
    private final ProjectTaskRepository taskRepository;
    private final MilestoneRepository milestoneRepository;
    private final ProjectDiscussionRepository discussionRepository;

    // ==================== Project CRUD ====================

    @Transactional
    public Project createProject(Project project) {
        log.info("Creating project: {}", project.getName());
        project.setStatus("PLANNING");
        project.setProgress(0);
        project.setTotalTasks(0);
        project.setCompletedTasks(0);
        return projectRepository.save(project);
    }

    public Project getProject(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found: " + id));
    }

    public Page<Project> getMyProjects(Long userId, Pageable pageable) {
        return projectRepository.findByCreatorIdAndDeletedAtIsNull(userId, pageable);
    }

    public Page<Project> getProjectsByStatus(String status, Pageable pageable) {
        return projectRepository.findByStatusAndDeletedAtIsNull(status, pageable);
    }

    @Transactional
    public Project updateProject(Long id, Project updates) {
        Project project = getProject(id);
        if (updates.getName() != null) project.setName(updates.getName());
        if (updates.getDescription() != null) project.setDescription(updates.getDescription());
        if (updates.getProjectType() != null) project.setProjectType(updates.getProjectType());
        if (updates.getDeadline() != null) project.setDeadline(updates.getDeadline());
        if (updates.getPriority() != null) project.setPriority(updates.getPriority());
        return projectRepository.save(project);
    }

    @Transactional
    public void deleteProject(Long id) {
        Project project = getProject(id);
        project.softDelete();
        projectRepository.save(project);
    }

    // ==================== Project Status ====================

    @Transactional
    public Project startProject(Long id) {
        Project project = getProject(id);
        project.setStatus("IN_PROGRESS");
        project.setActualStartDate(java.time.LocalDate.now());
        return projectRepository.save(project);
    }

    @Transactional
    public Project completeProject(Long id) {
        Project project = getProject(id);
        project.setStatus("COMPLETED");
        project.setProgress(100);
        project.setActualEndDate(java.time.LocalDate.now());
        return projectRepository.save(project);
    }

    @Transactional
    public Project archiveProject(Long id) {
        Project project = getProject(id);
        project.setStatus("ARCHIVED");
        return projectRepository.save(project);
    }

    // ==================== AI Analysis ====================

    @Transactional
    public Project updateAiAnalysis(Long projectId, String aiPrd, String aiTechStack,
                                    Integer aiEstimatedHours, Integer aiEstimatedDays,
                                    String aiRiskAnalysis) {
        Project project = getProject(projectId);
        project.setAiPrd(aiPrd);
        project.setAiTechStack(aiTechStack);
        project.setAiEstimatedHours(aiEstimatedHours);
        project.setAiEstimatedDays(aiEstimatedDays);
        project.setAiRiskAnalysis(aiRiskAnalysis);
        return projectRepository.save(project);
    }

    // ==================== Module Operations ====================

    @Transactional
    public ProjectModule createModule(ProjectModule module) {
        log.info("Creating module: {} for project: {}", module.getName(), module.getProjectId());
        module.setStatus("PENDING");
        module.setProgress(0);
        module.setTotalTasks(0);
        module.setCompletedTasks(0);
        ProjectModule saved = moduleRepository.save(module);
        updateProjectProgress(module.getProjectId());
        return saved;
    }

    public List<ProjectModule> getModules(Long projectId) {
        return moduleRepository.findByProjectIdOrderBySortOrderAsc(projectId);
    }

    @Transactional
    public ProjectModule updateModule(Long id, ProjectModule updates) {
        ProjectModule module = moduleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Module not found: " + id));
        if (updates.getName() != null) module.setName(updates.getName());
        if (updates.getDescription() != null) module.setDescription(updates.getDescription());
        if (updates.getSortOrder() != null) module.setSortOrder(updates.getSortOrder());
        return moduleRepository.save(module);
    }

    @Transactional
    public void deleteModule(Long id) {
        ProjectModule module = moduleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Module not found: " + id));
        Long projectId = module.getProjectId();
        moduleRepository.deleteById(id);
        updateProjectProgress(projectId);
    }

    // ==================== Task Operations ====================

    @Transactional
    public ProjectTask createTask(ProjectTask task) {
        log.info("Creating task: {} for project: {}", task.getTitle(), task.getProjectId());
        task.setStatus("TODO");
        ProjectTask saved = taskRepository.save(task);
        updateProjectProgress(task.getProjectId());
        if (task.getModuleId() != null) {
            updateModuleProgress(task.getModuleId());
        }
        return saved;
    }

    public List<ProjectTask> getTasks(Long projectId) {
        return taskRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
    }

    public List<ProjectTask> getTasksByStatus(Long projectId, String status) {
        return taskRepository.findByProjectIdAndStatusOrderByPriorityAscCreatedAtAsc(projectId, status);
    }

    public List<ProjectTask> getModuleTasks(Long moduleId) {
        return taskRepository.findByModuleIdOrderByCreatedAtAsc(moduleId);
    }

    public List<ProjectTask> getSubTasks(Long parentTaskId) {
        return taskRepository.findByParentTaskIdOrderByCreatedAtAsc(parentTaskId);
    }

    @Transactional
    public ProjectTask updateTask(Long id, ProjectTask updates) {
        ProjectTask task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found: " + id));
        if (updates.getTitle() != null) task.setTitle(updates.getTitle());
        if (updates.getDescription() != null) task.setDescription(updates.getDescription());
        if (updates.getTaskType() != null) task.setTaskType(updates.getTaskType());
        if (updates.getPriority() != null) task.setPriority(updates.getPriority());
        if (updates.getEstimatedHours() != null) task.setEstimatedHours(updates.getEstimatedHours());
        if (updates.getDeadline() != null) task.setDeadline(updates.getDeadline());
        return taskRepository.save(task);
    }

    @Transactional
    public ProjectTask updateTaskStatus(Long taskId, String status) {
        ProjectTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));
        task.setStatus(status);
        if ("DONE".equals(status)) {
            task.setCompletedAt(LocalDateTime.now());
        }
        ProjectTask saved = taskRepository.save(task);
        updateProjectProgress(task.getProjectId());
        if (task.getModuleId() != null) {
            updateModuleProgress(task.getModuleId());
        }
        return saved;
    }

    @Transactional
    public void deleteTask(Long id) {
        ProjectTask task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found: " + id));
        Long projectId = task.getProjectId();
        Long moduleId = task.getModuleId();
        taskRepository.deleteById(id);
        updateProjectProgress(projectId);
        if (moduleId != null) {
            updateModuleProgress(moduleId);
        }
    }

    // ==================== Milestone Operations ====================

    @Transactional
    public Milestone createMilestone(Milestone milestone) {
        log.info("Creating milestone: {} for project: {}", milestone.getName(), milestone.getProjectId());
        milestone.setStatus("PENDING");
        return milestoneRepository.save(milestone);
    }

    public List<Milestone> getMilestones(Long projectId) {
        return milestoneRepository.findByProjectIdOrderBySortOrderAsc(projectId);
    }

    @Transactional
    public Milestone updateMilestone(Long id, Milestone updates) {
        Milestone milestone = milestoneRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Milestone not found: " + id));
        if (updates.getName() != null) milestone.setName(updates.getName());
        if (updates.getDescription() != null) milestone.setDescription(updates.getDescription());
        if (updates.getDueDate() != null) milestone.setDueDate(updates.getDueDate());
        if (updates.getSortOrder() != null) milestone.setSortOrder(updates.getSortOrder());
        return milestoneRepository.save(milestone);
    }

    @Transactional
    public Milestone completeMilestone(Long id) {
        Milestone milestone = milestoneRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Milestone not found: " + id));
        milestone.setStatus("ACHIEVED");
        milestone.setActualDate(java.time.LocalDate.now());
        return milestoneRepository.save(milestone);
    }

    @Transactional
    public void deleteMilestone(Long id) {
        milestoneRepository.deleteById(id);
    }

    // ==================== Discussion Operations ====================

    @Transactional
    public com.aiticket.ticket.entity.ProjectDiscussion addDiscussion(
            com.aiticket.ticket.entity.ProjectDiscussion discussion) {
        log.info("Adding discussion to project: {}", discussion.getProjectId());
        return discussionRepository.save(discussion);
    }

    public List<com.aiticket.ticket.entity.ProjectDiscussion> getProjectDiscussions(Long projectId) {
        return discussionRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    public List<com.aiticket.ticket.entity.ProjectDiscussion> getTargetDiscussions(String targetType, Long targetId) {
        return discussionRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, targetId);
    }

    // ==================== Statistics ====================

    public long countProjectsByStatus(String status) {
        return projectRepository.countByStatusAndDeletedAtIsNull(status);
    }

    public List<Project> getActiveProjects() {
        return projectRepository.findActiveProjects();
    }

    // ==================== Private Helpers ====================

    private void updateProjectProgress(Long projectId) {
        long totalTasks = taskRepository.countByProjectId(projectId);
        long completedTasks = taskRepository.countByProjectIdAndStatus(projectId, "DONE");
        int progress = totalTasks > 0 ? (int) (completedTasks * 100 / totalTasks) : 0;

        Project project = getProject(projectId);
        project.setTotalTasks((int) totalTasks);
        project.setCompletedTasks((int) completedTasks);
        project.setProgress(progress);
        projectRepository.save(project);
    }

    private void updateModuleProgress(Long moduleId) {
        long totalTasks = taskRepository.countByModuleId(moduleId);
        long completedTasks = taskRepository.countByModuleIdAndStatus(moduleId, "DONE");
        int progress = totalTasks > 0 ? (int) (completedTasks * 100 / totalTasks) : 0;

        ProjectModule module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new RuntimeException("Module not found: " + moduleId));
        module.setTotalTasks((int) totalTasks);
        module.setCompletedTasks((int) completedTasks);
        module.setProgress(progress);
        if (progress == 100) {
            module.setStatus("COMPLETED");
        } else if (progress > 0) {
            module.setStatus("IN_PROGRESS");
        }
        moduleRepository.save(module);
    }
}

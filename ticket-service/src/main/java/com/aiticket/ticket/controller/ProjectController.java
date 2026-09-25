package com.aiticket.ticket.controller;

import com.aiticket.common.dto.Result;
import com.aiticket.common.exception.BusinessException;
import com.aiticket.ticket.entity.Project;
import com.aiticket.ticket.entity.ProjectModule;
import com.aiticket.ticket.entity.ProjectTask;
import com.aiticket.ticket.entity.Milestone;
import com.aiticket.ticket.entity.ProjectDiscussion;
import com.aiticket.ticket.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    // ==================== Project CRUD ====================

    /**
     * 创建项目。
     *
     * creator_id 是 NOT NULL 列，而前端从不（也不该）在请求体里携带 createorId —— 此前
     * 这里直接把请求体存库，导致任何创建请求都以 "Column 'creator_id' cannot be null"
     * 失败，项目管理模块的「创建项目」从来就没成功过。
     *
     * 身份只能取自网关注入的 X-User-Id：它来源于已经过签名的 Access Token。若采信请求体
     * 里的值，任何人都能把项目挂到别人名下。
     */
    @PostMapping
    public Result<Project> createProject(
            @RequestBody Project project,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        project.setCreatorId(userId);
        project.setCreatorName(userName);
        return Result.success(projectService.createProject(project));
    }

    @GetMapping("/{id}")
    public Result<Project> getProject(@PathVariable Long id) {
        return Result.success(projectService.getProject(id));
    }

    @GetMapping("/my")
    public Result<Page<Project>> getMyProjects(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(projectService.getMyProjects(userId, PageRequest.of(page, size)));
    }

    @GetMapping("/status/{status}")
    public Result<Page<Project>> getProjectsByStatus(
            @PathVariable String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(projectService.getProjectsByStatus(status, PageRequest.of(page, size)));
    }

    @PutMapping("/{id}")
    public Result<Project> updateProject(@PathVariable Long id, @RequestBody Project project) {
        return Result.success(projectService.updateProject(id, project));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteProject(@PathVariable Long id) {
        projectService.deleteProject(id);
        return Result.success();
    }

    // ==================== Project Status ====================

    @PutMapping("/{id}/start")
    public Result<Project> startProject(@PathVariable Long id) {
        return Result.success(projectService.startProject(id));
    }

    @PutMapping("/{id}/complete")
    public Result<Project> completeProject(@PathVariable Long id) {
        return Result.success(projectService.completeProject(id));
    }

    @PutMapping("/{id}/archive")
    public Result<Project> archiveProject(@PathVariable Long id) {
        return Result.success(projectService.archiveProject(id));
    }

    // ==================== AI Analysis ====================

    @PutMapping("/{id}/ai-analysis")
    public Result<Project> updateAiAnalysis(
            @PathVariable Long id,
            @RequestBody AiAnalysisRequest request) {
        return Result.success(projectService.updateAiAnalysis(
                id,
                request.getAiPrd(),
                request.getAiTechStack(),
                request.getAiEstimatedHours(),
                request.getAiEstimatedDays(),
                request.getAiRiskAnalysis()
        ));
    }

    // ==================== Module Operations ====================

    @PostMapping("/{projectId}/modules")
    public Result<ProjectModule> createModule(
            @PathVariable Long projectId,
            @RequestBody ProjectModule module) {
        module.setProjectId(projectId);
        return Result.success(projectService.createModule(module));
    }

    @GetMapping("/{projectId}/modules")
    public Result<List<ProjectModule>> getModules(@PathVariable Long projectId) {
        return Result.success(projectService.getModules(projectId));
    }

    @PutMapping("/modules/{id}")
    public Result<ProjectModule> updateModule(@PathVariable Long id, @RequestBody ProjectModule module) {
        return Result.success(projectService.updateModule(id, module));
    }

    @DeleteMapping("/modules/{id}")
    public Result<Void> deleteModule(@PathVariable Long id) {
        projectService.deleteModule(id);
        return Result.success();
    }

    // ==================== Task Operations ====================

    @PostMapping("/{projectId}/tasks")
    public Result<ProjectTask> createTask(
            @PathVariable Long projectId,
            @RequestBody ProjectTask task) {
        task.setProjectId(projectId);
        return Result.success(projectService.createTask(task));
    }

    @GetMapping("/{projectId}/tasks")
    public Result<List<ProjectTask>> getTasks(@PathVariable Long projectId) {
        return Result.success(projectService.getTasks(projectId));
    }

    @GetMapping("/{projectId}/tasks/status/{status}")
    public Result<List<ProjectTask>> getTasksByStatus(
            @PathVariable Long projectId,
            @PathVariable String status) {
        return Result.success(projectService.getTasksByStatus(projectId, status));
    }

    @GetMapping("/modules/{moduleId}/tasks")
    public Result<List<ProjectTask>> getModuleTasks(@PathVariable Long moduleId) {
        return Result.success(projectService.getModuleTasks(moduleId));
    }

    @GetMapping("/tasks/{taskId}/subtasks")
    public Result<List<ProjectTask>> getSubTasks(@PathVariable Long taskId) {
        return Result.success(projectService.getSubTasks(taskId));
    }

    @PutMapping("/tasks/{id}")
    public Result<ProjectTask> updateTask(@PathVariable Long id, @RequestBody ProjectTask task) {
        return Result.success(projectService.updateTask(id, task));
    }

    @PutMapping("/tasks/{id}/status")
    public Result<ProjectTask> updateTaskStatus(
            @PathVariable Long id,
            @RequestBody StatusRequest request) {
        return Result.success(projectService.updateTaskStatus(id, request.getStatus()));
    }

    @DeleteMapping("/tasks/{id}")
    public Result<Void> deleteTask(@PathVariable Long id) {
        projectService.deleteTask(id);
        return Result.success();
    }

    // ==================== Milestone Operations ====================

    @PostMapping("/{projectId}/milestones")
    public Result<Milestone> createMilestone(
            @PathVariable Long projectId,
            @RequestBody Milestone milestone) {
        milestone.setProjectId(projectId);
        return Result.success(projectService.createMilestone(milestone));
    }

    @GetMapping("/{projectId}/milestones")
    public Result<List<Milestone>> getMilestones(@PathVariable Long projectId) {
        return Result.success(projectService.getMilestones(projectId));
    }

    @PutMapping("/milestones/{id}")
    public Result<Milestone> updateMilestone(@PathVariable Long id, @RequestBody Milestone milestone) {
        return Result.success(projectService.updateMilestone(id, milestone));
    }

    @PutMapping("/milestones/{id}/complete")
    public Result<Milestone> completeMilestone(@PathVariable Long id) {
        return Result.success(projectService.completeMilestone(id));
    }

    @DeleteMapping("/milestones/{id}")
    public Result<Void> deleteMilestone(@PathVariable Long id) {
        projectService.deleteMilestone(id);
        return Result.success();
    }

    // ==================== Discussion Operations ====================

    /**
     * 发表讨论。
     *
     * user_id 是 NOT NULL 列，而前端只发 content/targetType/targetId —— 此前这里直接把
     * 请求体存库，于是每一次发表都以 "Column 'user_id' cannot be null" 失败，评论一条
     * 也存不进去（HTTP 200 但信封里是 code:500，前端只弹「发表失败」）。与 createProject
     * 同一类问题、同一套修法。
     *
     * 身份只能取自网关注入的 X-User-Id：它来源于已经过签名的 Access Token。若采信请求体
     * 里的 userId/userName，任何人都能以别人的名义发言。
     */
    @PostMapping("/{projectId}/discussions")
    public Result<ProjectDiscussion> addDiscussion(
            @PathVariable Long projectId,
            @RequestBody ProjectDiscussion discussion,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        discussion.setProjectId(projectId);
        discussion.setUserId(userId);
        discussion.setUserName(userName);
        return Result.success(projectService.addDiscussion(discussion));
    }

    @GetMapping("/{projectId}/discussions")
    public Result<List<ProjectDiscussion>> getProjectDiscussions(@PathVariable Long projectId) {
        return Result.success(projectService.getProjectDiscussions(projectId));
    }

    @GetMapping("/discussions/{targetType}/{targetId}")
    public Result<List<ProjectDiscussion>> getTargetDiscussions(
            @PathVariable String targetType,
            @PathVariable Long targetId) {
        return Result.success(projectService.getTargetDiscussions(targetType, targetId));
    }

    // ==================== Statistics ====================

    @GetMapping("/stats/active")
    public Result<List<Project>> getActiveProjects() {
        return Result.success(projectService.getActiveProjects());
    }

    @GetMapping("/stats/count/{status}")
    public Result<Long> countProjectsByStatus(@PathVariable String status) {
        return Result.success(projectService.countProjectsByStatus(status));
    }

    // ==================== DTO Classes ====================

    @lombok.Data
    public static class AiAnalysisRequest {
        private String aiPrd;
        private String aiTechStack;
        private Integer aiEstimatedHours;
        private Integer aiEstimatedDays;
        private String aiRiskAnalysis;
    }

    @lombok.Data
    public static class StatusRequest {
        private String status;
    }
}

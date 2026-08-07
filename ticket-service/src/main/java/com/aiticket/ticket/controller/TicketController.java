package com.aiticket.ticket.controller;

import com.aiticket.common.dto.PageResult;
import com.aiticket.common.dto.Result;
import com.aiticket.ticket.dto.*;
import com.aiticket.ticket.entity.Ticket;
import com.aiticket.ticket.repository.TicketRepository;
import com.aiticket.ticket.service.ChatService;
import com.aiticket.ticket.service.TicketAiService;
import com.aiticket.ticket.service.TicketService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final ChatService chatService;
    private final TicketRepository ticketRepository;
    private final TicketAiService ticketAiService;

    @PostMapping
    public Result<TicketDTO> createTicket(@Valid @RequestBody CreateTicketRequest request,
                                          @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                          @RequestHeader(value = "X-User-Name", required = false) String username) {
        if (userId == null) userId = 1L; // fallback for testing
        if (username == null) username = "anonymous";
        return Result.success(ticketService.createTicket(request, userId, username));
    }

    @GetMapping("/{id}")
    public Result<TicketDTO> getTicket(@PathVariable Long id) {
        return Result.success(ticketService.getTicket(id));
    }

    @GetMapping
    public Result<PageResult<TicketDTO>> listTickets(TicketQueryRequest query,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (userId == null) userId = 1L;
        if (role == null) role = "USER";
        return Result.success(ticketService.listTickets(query, userId, "ADMIN".equals(role)));
    }

    @GetMapping("/my")
    public Result<PageResult<TicketDTO>> listMyTickets(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) userId = 1L;
        return Result.success(ticketService.listMyTickets(userId, page, pageSize));
    }

    @GetMapping("/pending")
    public Result<PageResult<TicketDTO>> listPendingTickets(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) userId = 1L;
        return Result.success(ticketService.listPendingTickets(userId, page, pageSize));
    }

    @GetMapping("/my/accepted")
    public Result<PageResult<TicketDTO>> listMyAcceptedTickets(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) userId = 1L;
        return Result.success(ticketService.listMyAcceptedTickets(userId, page, pageSize));
    }

    @GetMapping("/completed")
    public Result<PageResult<TicketDTO>> listCompletedTickets(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(ticketService.listCompletedTickets(page, pageSize));
    }

    @PutMapping("/{id}")
    public Result<TicketDTO> updateTicket(@PathVariable Long id,
                                          @RequestBody UpdateTicketRequest request) {
        return Result.success(ticketService.updateTicket(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteTicket(@PathVariable Long id) {
        ticketService.deleteTicket(id);
        return Result.success();
    }

    @PutMapping("/{id}/assign")
    public Result<TicketDTO> assignHandler(@PathVariable Long id,
                                            @Valid @RequestBody AssignHandlerRequest request) {
        return Result.success(ticketService.assignHandler(id, request.getHandlerId(), null));
    }

    @PutMapping("/{id}/start")
    public Result<TicketDTO> startProcessing(@PathVariable Long id) {
        return Result.success(ticketService.startProcessing(id));
    }

    @PutMapping("/{id}/resolve")
    public Result<TicketDTO> resolve(@PathVariable Long id) {
        return Result.success(ticketService.resolve(id));
    }

    @PutMapping("/{id}/close")
    public Result<TicketDTO> close(@PathVariable Long id) {
        return Result.success(ticketService.close(id));
    }

    @PutMapping("/{id}/reopen")
    public Result<TicketDTO> reopen(@PathVariable Long id) {
        return Result.success(ticketService.reopen(id));
    }

    @PostMapping("/{id}/comment")
    public Result<CommentDTO> addComment(@PathVariable Long id,
                                         @Valid @RequestBody AddCommentRequest request,
                                         @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                         @RequestHeader(value = "X-User-Name", required = false) String username) {
        if (userId == null) userId = 1L;
        if (username == null) username = "anonymous";
        return Result.success(ticketService.addComment(id, request, userId, username));
    }

    @GetMapping("/{id}/comments")
    public Result<List<CommentDTO>> getComments(@PathVariable Long id) {
        return Result.success(ticketService.getComments(id));
    }

    // Accept ticket (handler picks up)
    @PostMapping("/{id}/accept")
    public Result<TicketDTO> acceptTicket(@PathVariable Long id,
                                          @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                          @RequestHeader(value = "X-User-Name", required = false) String username) {
        if (userId == null) userId = 1L;
        if (username == null) username = "anonymous";
        return Result.success(ticketService.acceptTicket(id, userId, username));
    }

    // Complete ticket (handler submits for approval)
    @PostMapping("/{id}/complete")
    public Result<TicketDTO> completeTicket(@PathVariable Long id,
                                            @Valid @RequestBody CompleteTicketRequest request) {
        return Result.success(ticketService.completeTicket(id, request));
    }

    // Approve or reject completion (user confirms)
    @PostMapping("/{id}/approve")
    public Result<TicketDTO> approveTicket(@PathVariable Long id,
                                           @Valid @RequestBody ApproveTicketRequest request) {
        return Result.success(ticketService.approveTicket(id, request.getApproved(), request.getReason()));
    }

    // Chat endpoints
    @GetMapping("/{id}/chat")
    public Result<List<ChatMessageDTO>> getChatHistory(@PathVariable Long id) {
        return Result.success(chatService.getChatHistory(id));
    }

    @PostMapping("/{id}/chat")
    public Result<ChatMessageDTO> sendChatMessage(@PathVariable Long id,
                                                  @Valid @RequestBody SendChatRequest request,
                                                  @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                                  @RequestHeader(value = "X-User-Name", required = false) String username,
                                                  @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (userId == null) userId = 1L;
        if (username == null) username = "anonymous";
        if (role == null) role = "USER";
        return Result.success(chatService.sendMessage(id, userId, username, role, request.getContent()));
    }

    @GetMapping("/stats")
    public Result<TicketStatsDTO> getStats(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(required = false) Long excludeCreatorId) {
        if (userId == null) userId = 1L;
        return Result.success(ticketService.getStats(userId, excludeCreatorId));
    }

    // ==================== AI分析接口 ====================

    /**
     * AI开发分析 - 生成技术建议和任务拆解（PRD通过下载获取）
     */
    @PostMapping("/{id}/ai-analysis")
    public Result<TicketDTO> analyzeTicket(@PathVariable Long id) {
        log.info("开始AI分析工单: {}", id);
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("工单不存在"));

        // 构建项目信息
        StringBuilder projectInfo = new StringBuilder();
        projectInfo.append("项目名称: ").append(ticket.getTitle()).append("\n");
        projectInfo.append("项目描述: ").append(ticket.getContent()).append("\n");
        if (ticket.getProjectType() != null) {
            projectInfo.append("项目类型: ").append(ticket.getProjectType()).append("\n");
        }
        if (ticket.getTechStack() != null) {
            projectInfo.append("技术栈: ").append(ticket.getTechStack()).append("\n");
        }
        if (ticket.getDetailedRequirements() != null) {
            projectInfo.append("详细需求: ").append(ticket.getDetailedRequirements()).append("\n");
        }

        // 调用AI分析技术栈
        try {
            String techSuggestion = ticketAiService.analyzeTechStack(ticket.getCreatorId(), projectInfo.toString());
            ticket.setAiTechSuggestion(techSuggestion);
        } catch (Exception e) {
            log.error("分析技术栈失败", e);
            ticket.setAiTechSuggestion("技术栈分析失败，请稍后重试");
        }

        // 调用AI拆解任务
        try {
            String taskBreakdown = ticketAiService.decomposeTasks(ticket.getCreatorId(), projectInfo.toString());
            ticket.setAiTaskBreakdown(taskBreakdown);
        } catch (Exception e) {
            log.error("拆解任务失败", e);
            ticket.setAiTaskBreakdown("任务拆解失败，请稍后重试");
        }

        ticketRepository.save(ticket);
        log.info("AI分析完成: {}", id);

        return Result.success(ticketService.getTicket(id));
    }

    /**
     * 下载PRD文档
     */
    @GetMapping("/{id}/download-prd")
    public void downloadPrd(@PathVariable Long id, HttpServletResponse response) throws IOException {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("工单不存在"));

        // 构建项目信息
        StringBuilder projectInfo = new StringBuilder();
        projectInfo.append("项目名称: ").append(ticket.getTitle()).append("\n");
        projectInfo.append("项目描述: ").append(ticket.getContent()).append("\n");
        if (ticket.getProjectType() != null) {
            projectInfo.append("项目类型: ").append(ticket.getProjectType()).append("\n");
        }
        if (ticket.getTechStack() != null) {
            projectInfo.append("技术栈: ").append(ticket.getTechStack()).append("\n");
        }
        if (ticket.getDetailedRequirements() != null) {
            projectInfo.append("详细需求: ").append(ticket.getDetailedRequirements()).append("\n");
        }

        // 调用AI生成PRD
        String prdContent = ticketAiService.generatePrd(ticket.getCreatorId(), projectInfo.toString());

        // 设置响应头，触发下载
        response.setContentType("text/markdown; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        String fileName = "PRD_" + ticket.getTitle() + "_" + System.currentTimeMillis() + ".md";
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

        // 写入文件内容
        java.io.PrintWriter out = response.getWriter();
        out.print(prdContent);
        out.flush();
    }

    /**
     * 接单 - 接单后工单从大厅消失，发布者收到通知
     */
    @PostMapping("/{id}/pickup")
    public Result<TicketDTO> pickupTicket(@PathVariable Long id,
                                          @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                          @RequestHeader(value = "X-User-Name", required = false) String username) {
        if (userId == null) userId = 1L;
        if (username == null) username = "anonymous";

        log.info("用户{}接单工单: {}", username, id);
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("工单不存在"));

        // 检查是否已接单
        if (ticket.getAcceptedBy() != null) {
            throw new RuntimeException("该工单已被接单");
        }

        // 检查是否是自己的工单
        if (ticket.getCreatorId().equals(userId)) {
            throw new RuntimeException("不能接自己的工单");
        }

        // 设置接单信息
        ticket.setAcceptedBy(userId);
        ticket.setAcceptedByName(username);
        ticket.setIsVisible(false);  // 从大厅隐藏
        ticket.setStatus(com.aiticket.common.enums.TicketStatus.ACCEPTED);
        ticket.setAcceptedAt(java.time.LocalDateTime.now());

        ticketRepository.save(ticket);

        // 发送系统消息通知发布者
        try {
            chatService.sendMessage(id, userId, username, "HANDLER",
                    "我已接单，开始为您开发项目！如有任何问题请随时沟通。");
        } catch (Exception e) {
            log.error("发送接单通知失败", e);
        }

        return Result.success(ticketService.getTicket(id));
    }

    // ==================== AI聊天接口 ====================

    /**
     * AI开发助手聊天 - 带上下文记忆
     * 用户可以针对当前工单持续提问
     */
    @PostMapping("/{id}/ai-chat")
    public Result<ChatMessageDTO> aiChat(@PathVariable Long id,
                                         @RequestBody Map<String, String> request,
                                         @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                         @RequestHeader(value = "X-User-Name", required = false) String username) {
        if (userId == null) userId = 1L;
        if (username == null) username = "anonymous";

        String userMessage = request.get("message");
        if (userMessage == null || userMessage.isBlank()) {
            throw new RuntimeException("消息内容不能为空");
        }

        log.info("AI聊天: ticketId={}, userId={}, message={}", id, userId, userMessage);

        // 获取工单信息作为上下文
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("工单不存在"));

        // 构建项目上下文
        StringBuilder context = new StringBuilder();
        context.append("当前项目信息：\n");
        context.append("项目名称: ").append(ticket.getTitle()).append("\n");
        context.append("项目描述: ").append(ticket.getContent()).append("\n");
        if (ticket.getProjectType() != null) {
            context.append("项目类型: ").append(ticket.getProjectType()).append("\n");
        }
        if (ticket.getTechStack() != null) {
            context.append("技术栈: ").append(ticket.getTechStack()).append("\n");
        }
        if (ticket.getDetailedRequirements() != null) {
            context.append("详细需求: ").append(ticket.getDetailedRequirements()).append("\n");
        }
        context.append("\n用户问题: ").append(userMessage);

        // 调用AI聊天（带上下文记忆）
        String aiResponse = ticketAiService.chatWithDeveloper(id, context.toString());

        // 保存到聊天记录
        ChatMessageDTO response = ChatMessageDTO.builder()
                .ticketId(id)
                .userId(userId)
                .userName("AI助手")
                .content(aiResponse)
                .senderType("AI")
                .createdAt(java.time.LocalDateTime.now())
                .build();

        return Result.success(response);
    }
}

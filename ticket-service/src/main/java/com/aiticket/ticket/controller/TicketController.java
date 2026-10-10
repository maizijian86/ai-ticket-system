package com.aiticket.ticket.controller;

import com.aiticket.common.dto.PageResult;
import com.aiticket.common.dto.Result;
import com.aiticket.common.enums.TicketStatus;
import com.aiticket.common.exception.BusinessException;
import com.aiticket.ticket.dto.*;
import com.aiticket.ticket.entity.Ticket;
import com.aiticket.ticket.enums.AiFeature;
import com.aiticket.ticket.repository.TicketRepository;
import com.aiticket.ticket.service.AiQuotaGate;
import com.aiticket.ticket.service.AiQuotaService;
import com.aiticket.ticket.service.ChatService;
import com.aiticket.ticket.service.ConversationMemoryManager;
import com.aiticket.ticket.service.TicketAiService;
import com.aiticket.ticket.service.TicketService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    private final ConversationMemoryManager memoryManager;
    private final AiQuotaService quotaService;
    private final AiQuotaGate quotaGate;

    @PostMapping
    public Result<TicketDTO> createTicket(@Valid @RequestBody CreateTicketRequest request,
                                          @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                          @RequestHeader(value = "X-User-Name", required = false) String username) {
        if (userId == null) userId = 1L; // fallback for testing
        if (username == null) username = "anonymous";
        return Result.success(ticketService.createTicket(request, userId, username));
    }

    @GetMapping("/{id}")
    public Result<TicketDTO> getTicket(@PathVariable Long id,
                                       @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.success(ticketService.getTicket(id, userId));
    }

    /**
     * 大厅列表。**对访客开放**（网关按"仅 GET + 精确路径"放行）。
     *
     * <p>访客的查询参数必须收窄，否则这个公开接口会变成越权读取的入口：
     * <ul>
     *   <li>{@code handlerId} / {@code creatorId} / {@code participantId} —— 传谁的 id 就能
     *       列出谁的单，属于他人数据，匿名一律清掉；</li>
     *   <li>{@code includeAll} —— 跳过"大厅可见性"谓词，会看到被接单后已下架的工单；</li>
     *   <li>状态强制 {@code OPEN} —— 大厅的语义就是"还没人接"，访客不需要看到别的档位，
     *       这也顺带堵住"用 status 枚举出全站工单"这条枚举路径。</li>
     * </ul>
     *
     * <p>这里原来有 {@code userId == null → 1L} 的兜底：那不只是"看着像已登录"，而是让
     * 匿名请求以用户 1 的身份去查自己的单，属于把权限白送。已删除 —— 缺身份就是访客。
     */
    @GetMapping
    public Result<PageResult<TicketDTO>> listTickets(TicketQueryRequest query,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        boolean isAdmin = "ADMIN".equals(role);
        if (userId == null) {
            query.setHandlerId(null);
            query.setCreatorId(null);
            query.setParticipantId(null);
            query.setExcludeCreatorId(null);
            query.setIncludeAll(null);
            query.setStatus(TicketStatus.OPEN);
            query.setStatuses(null);
            log.debug("匿名访客查询大厅，已收窄查询条件: keyword={}", query.getKeyword());
        }
        return Result.success(ticketService.listTickets(query, userId, isAdmin));
    }

    @GetMapping("/my")
    public Result<PageResult<TicketDTO>> listMyTickets(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) userId = 1L;
        return Result.success(ticketService.listMyTickets(userId, page, pageSize));
    }

    // 此处原有 GET /pending。它返回的是「handler_id = 我 且 status = ACCEPTED」，
    // 与 /my/accepted 含义重叠，但前端「待处理」菜单要的是三种状态（含待验收、
    // 被驳回），单状态接口表达不了。前端改走 listTickets + statuses 多状态筛选，
    // 这个接口已无调用方，删除。

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
                                          @RequestBody UpdateTicketRequest request,
                                          @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.success(ticketService.updateTicket(id, request, userId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteTicket(@PathVariable Long id,
                                     @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        ticketService.deleteTicket(id, userId);
        return Result.success();
    }

    // 此处原有 PUT /{id}/assign、/{id}/start、/{id}/resolve —— 三条废弃的接单/流转
    // 路径，零校验、可重复调用、可把他人名下的工单改到自己名下，正是"接单这件事
    // 有三条路径"里的第三条。接单现已收敛到 POST /{id}/pickup 一处，全部删除。

    @PutMapping("/{id}/close")
    public Result<TicketDTO> close(@PathVariable Long id,
                                   @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.success(ticketService.close(id, userId));
    }

    @PutMapping("/{id}/reopen")
    public Result<TicketDTO> reopen(@PathVariable Long id,
                                    @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.success(ticketService.reopen(id, userId));
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

    /**
     * 工单留言。**对访客开放**，因此内部留言（{@code is_internal=true}）的过滤必须
     * 在服务端按请求者身份做 —— 详见 {@code TicketService.getComments}。
     */
    @GetMapping("/{id}/comments")
    public Result<List<CommentDTO>> getComments(@PathVariable Long id,
                                               @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.success(ticketService.getComments(id, userId));
    }

    // 此处原有 POST /{id}/accept —— 第二条接单路径（与 /pickup 各写一组字段，
    // 于是接单后工单从大厅下架却不在「我接的单」里）。已删除，只留 /pickup。

    // Complete ticket (handler submits for approval)
    @PostMapping("/{id}/complete")
    public Result<TicketDTO> completeTicket(@PathVariable Long id,
                                            @Valid @RequestBody CompleteTicketRequest request,
                                            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.success(ticketService.completeTicket(id, request, userId));
    }

    // Approve or reject completion (user confirms)
    @PostMapping("/{id}/approve")
    public Result<TicketDTO> approveTicket(@PathVariable Long id,
                                           @Valid @RequestBody ApproveTicketRequest request,
                                           @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.success(ticketService.approveTicket(id, request.getApproved(), request.getReason(), userId));
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

    // ==================== AI 辅助开发接口 ====================
    //
    // 三个入口共用一套骨架：校验接单人 → 预占额度（短事务）→ 调 AI（事务外，可能几分钟）
    // → 成功落定 / 失败退款。骨架在 AiQuotaGate.run(...)，这里只负责"给 AI 喂什么上下文"
    // 和"结果写到哪里"。
    //
    // 三个入口此前都**没有任何身份校验**（方法签名里连 X-User-Id 都没取），aiChat 甚至还有
    // userId == null → 1L 的兜底 —— 也就是任何人都能白烧模型额度。这个兜底已全部删除：
    // 缺身份一律 401，非接单人一律 403。

    /**
     * AI 开发分析 —— 一次消耗同时产出「技术建议」和「任务拆解」两个 tab。
     *
     * <p>两个 tab 算**一个**功能（{@link AiFeature#ANALYSIS}）：用户按的是一次，就不该扣两次。
     *
     * <p>成功判据是"两个都拿到了"。此前这里对两次调用各自 try/catch，失败就写一句
     * "×××分析失败，请稍后重试"再照常保存、返回 200 —— 接入额度后那样的行为等于
     * "扣了费只给半个结果，且用户没有第二次机会"。现在任一失败即整体失败并退款。
     *
     * <p><b>分析与对话共用一次消耗</b>（2026-10-10 定的）：同组的另一半先花掉那一次之后，
     * 这里**不再扣费**、也不必再进额度门禁 —— 进去了只会撞 409"已经用过了"。
     * 免费那一半仍然只允许生成一次，判据是工单上还没有内容；否则反复调这个接口就是
     * 拿它当免费的模型入口，额度体系也就白设了。
     */
    @PostMapping("/{id}/ai-analysis")
    public Result<TicketDTO> analyzeTicket(@PathVariable Long id,
                                           @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        log.info("开始AI分析工单: {}, 操作人: {}", id, userId);
        Ticket ticket = ticketService.requireHandlerTicket(id, userId, "使用 AI 辅助开发");
        String projectInfo = buildProjectInfo(ticket);

        AiAnalysisResult result;
        if (quotaService.isSpent(id, AiFeature.ANALYSIS)) {
            if (hasText(ticket.getAiTechSuggestion()) && hasText(ticket.getAiTaskBreakdown())) {
                throw new BusinessException(409, "该工单的 AI 分析已经生成过了");
            }
            log.info("分析与对话共用一次额度，本次分析不再扣费: ticketId={}", id);
            result = callAnalysis(id, projectInfo);
        } else {
            result = quotaGate.run(id, userId, AiFeature.ANALYSIS, () -> callAnalysis(id, projectInfo));
        }

        TicketDTO dto = ticketService.saveAiAnalysis(id, result.techSuggestion(), result.taskBreakdown());
        log.info("AI分析完成: {}", id);
        return Result.success(dto);
    }

    /** 真正喂给模型的那两次调用。抽出来是因为"扣费"和"同组已付过"两条路都要调同一段。 */
    private AiAnalysisResult callAnalysis(Long id, String projectInfo) {
        String techSuggestion = ticketAiService.analyzeTechStack(
                memoryManager.techStackScope(id), projectInfo);
        String taskBreakdown = ticketAiService.decomposeTasks(
                memoryManager.taskBreakdownScope(id), projectInfo);
        return new AiAnalysisResult(techSuggestion, taskBreakdown);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    /** AI 分析一次产出的两条内容。用 record 只是为了在 lambda 里传两个值。 */
    private record AiAnalysisResult(String techSuggestion, String taskBreakdown) {}

    /**
     * 下载 PRD 文档。
     *
     * <p><b>顺序是这个接口的全部难点</b>：一旦开始写响应体就再也改不了状态码了，
     * 所以「校验 → 取存量/调 AI → 落库」必须在 {@code setHeader} 之前全部做完。
     * 原实现把模型调用放在设置响应头之前是对的，但**从不把 PRD 落库**：每点一次下载
     * 就重跑一次模型。功能改成"每工单一次"后，客户端在下载途中超时 = 这次额度永久白烧、
     * 内容再也取不回来。现在生成后先落库，之后重复下载命中存量，**不再扣费**。
     */
    @GetMapping("/{id}/download-prd")
    public void downloadPrd(@PathVariable Long id,
                            @RequestHeader(value = "X-User-Id", required = false) Long userId,
                            HttpServletResponse response) throws IOException {
        Ticket ticket = ticketService.requireHandlerTicket(id, userId, "使用 AI 辅助开发");

        String prdContent = ticket.getAiPrd();
        if (prdContent == null || prdContent.isBlank()) {
            String projectInfo = buildProjectInfo(ticket);
            prdContent = quotaGate.run(id, userId, AiFeature.PRD,
                    () -> ticketAiService.generatePrd(memoryManager.prdScope(id), projectInfo));
            ticketService.saveAiPrd(id, prdContent);
        } else {
            log.info("PRD 命中存量，直接下载不扣额度: ticketId={}", id);
        }

        // 设置响应头，触发下载
        response.setContentType("text/markdown; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");

        // HTTP 响应头只能承载 ISO-8859-1，直接把中文标题拼进 filename 会让
        // Tomcat 判定整个 Content-Disposition 非法并**整条丢弃**（日志里那条
        // "The HTTP response header [Content-Disposition] ... has been removed
        //  from the response because it is invalid" 就是它），浏览器于是拿不到
        // 文件名。这里按 RFC 5987 双写：filename 给老浏览器用纯 ASCII 兜底，
        // filename*=UTF-8'' 给现代浏览器还原中文。
        String title = (ticket.getTitle() == null || ticket.getTitle().isBlank())
                ? "ticket" : ticket.getTitle();
        String suffix = System.currentTimeMillis() + ".md";
        String asciiName = "PRD_" + ticket.getId() + "_" + suffix;
        String encodedName = URLEncoder.encode("PRD_" + title + "_" + suffix, StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + asciiName + "\"; filename*=UTF-8''" + encodedName);

        // 写入文件内容
        java.io.PrintWriter out = response.getWriter();
        out.print(prdContent);
        out.flush();
    }

    /**
     * 工单的 AI 额度状态，供详情页渲染按钮/遮罩/剩余次数徽标。
     *
     * <p>{@code X-User-Id} 可空只是为了容错 —— 这个接口**不在网关匿名白名单里**，
     * 未登录根本到不了这里，缺身份时按"非接单人"返回。
     */
    @GetMapping("/{id}/ai-usage")
    public Result<AiQuotaStatusDTO> getAiUsage(@PathVariable Long id,
                                               @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "工单不存在"));
        return Result.success(quotaService.status(id, userId, ticket.getHandlerId()));
    }

    /** 拼接喂给模型的工单上下文。三个入口的措辞完全一致，合并成一处避免各自漂移。 */
    private static String buildProjectInfo(Ticket ticket) {
        StringBuilder info = new StringBuilder();
        info.append("项目名称: ").append(ticket.getTitle()).append("\n");
        info.append("项目描述: ").append(ticket.getContent()).append("\n");
        if (ticket.getProjectType() != null) {
            info.append("项目类型: ").append(ticket.getProjectType()).append("\n");
        }
        if (ticket.getTechStack() != null) {
            info.append("技术栈: ").append(ticket.getTechStack()).append("\n");
        }
        if (ticket.getDetailedRequirements() != null) {
            info.append("详细需求: ").append(ticket.getDetailedRequirements()).append("\n");
        }
        return info.toString();
    }

    /**
     * 接单 —— 全系统唯一的接单入口，接单后工单从大厅消失。
     *
     * 校验与状态变更都在 TicketService.pickupTicket 里（那里才拿得到事务），
     * 这里只做参数兜底和转发。此前这段逻辑内联在控制器里，既不参与事务，
     * 又和 /assign、/accept 各写一组字段，是"接单三条路径"的根源。
     */
    @PostMapping("/{id}/pickup")
    public Result<TicketDTO> pickupTicket(@PathVariable Long id,
                                          @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                          @RequestHeader(value = "X-User-Name", required = false) String username) {
        // 这里原来有 `if (userId == null) userId = 1L;` —— 缺身份头就等于以"用户 1"
        // 的名义接单。接单是"站哪边"的核心动作，兜底改由 TicketService 抛 401。
        if (username == null) username = "anonymous";
        log.info("用户{}接单工单: {}", username, id);
        return Result.success(ticketService.pickupTicket(id, userId, username));
    }

    // ==================== AI聊天接口 ====================

    /**
     * AI 开发助手聊天 —— 带上下文记忆，接单人可针对当前工单持续提问。
     *
     * <p>计费语义：**首条消息消耗 1 次额度解锁「AI 分析与对话」这一格，之后多轮对话不再
     * 扣费、也不计条数**。解锁凭证就是 {@code ai_usage} 里那一行
     * {@code (ticket_id, feature=ANALYSIS, status=SUCCESS)} —— 它存在就说明这一格付过了。
     * 所以这里只有"还没付过"的那一条消息会经过 {@link AiQuotaGate}。
     *
     * <p>记在 {@code ANALYSIS} 上而不是 {@code CHAT} 上，是这两个功能共用一次消耗的必然
     * 结果：记在各自名下就成了两条唯一键，谁也挡不住谁，并发下会被扣成两次。
     *
     * <p>原实现有 {@code userId == null → 1L} 的兜底，且完全不校验调用者是谁 ——
     * 任何人都能拿任意工单的上下文白嫖模型。已删除。
     */
    @PostMapping("/{id}/ai-chat")
    public Result<ChatMessageDTO> aiChat(@PathVariable Long id,
                                         @RequestBody Map<String, String> request,
                                         @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        Ticket ticket = ticketService.requireHandlerTicket(id, userId, "使用 AI 对话");

        String userMessage = request.get("message");
        if (userMessage == null || userMessage.isBlank()) {
            throw new BusinessException(400, "消息内容不能为空");
        }

        String context = "当前项目信息：\n" + buildProjectInfo(ticket) + "\n用户问题: " + userMessage;

        // AI 调用（带上下文记忆）。这一格付过了就不再经过额度门禁 —— 一次付费，
        // 本工单内畅聊；同组的 AI 分析也是同一个道理，它那边少扣的那一次由这条消息付掉了。
        boolean paid = quotaService.isSpent(id, AiFeature.ANALYSIS);
        String aiResponse;
        if (paid) {
            aiResponse = ticketAiService.chatWithDeveloper(memoryManager.chatScope(id), context);
        } else {
            log.info("AI聊天首次解锁(与AI分析共用一次额度): ticketId={}, userId={}", id, userId);
            aiResponse = quotaGate.run(id, userId, AiFeature.ANALYSIS,
                    () -> ticketAiService.chatWithDeveloper(memoryManager.chatScope(id), context));
        }

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

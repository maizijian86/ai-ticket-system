package com.aiticket.ticket.service;

import com.aiticket.common.dto.PageResult;
import com.aiticket.common.enums.Priority;
import com.aiticket.common.enums.TicketCategory;
import com.aiticket.common.enums.TicketStatus;
import com.aiticket.common.exception.BusinessException;
import com.aiticket.ticket.dto.*;
import com.aiticket.ticket.entity.Ticket;
import com.aiticket.ticket.entity.TicketComment;
import com.aiticket.ticket.feign.AiServiceClient;
import com.aiticket.ticket.repository.TicketCommentRepository;
import com.aiticket.ticket.repository.TicketRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    /**
     * 「进行中」的状态集：接单之后、验收通过之前的那一段。
     *
     * <p>三个状态在这里合成一个"还没结"的概念，是给大厅「进行中」卡用的：
     * <ul>
     *   <li>ACCEPTED —— 接单人手上，还没交付</li>
     *   <li>PENDING_APPROVAL —— 接单人已交付，等发布者点验收</li>
     *   <li>REJECTED —— 被驳回，等接单人返工后重新交付</li>
     * </ul>
     *
     * <p>这个集合此前只以字面量 'ACCEPTED,PENDING_APPROVAL,REJECTED' 的形式活在
     * 前端「待处理」页的查询里，后端没有任何一处定义它。现在前端那页要删、卡片
     * 改由统计数字驱动，所以口径收到这里一份，前端只是展示者。
     *
     * <p>注意它**不等于**"所有未完成状态"：OPEN（还没人接）和 CLOSED（关闭）
     * 都不在里面 —— 「进行中」说的是"已经有人在做"。
     */
    public static final List<TicketStatus> ACTIVE_STATUSES = List.of(
            TicketStatus.ACCEPTED,
            TicketStatus.PENDING_APPROVAL,
            TicketStatus.REJECTED
    );

    /**
     * 「已完结」的状态集：COMPLETED / CLOSED。
     *
     * <p>编辑入口和重开入口用这一份。与 {@link #ACTIVE_STATUSES} 是**两个正交的
     * 口径**，不要合并：
     * <ul>
     *   <li>{@code ACTIVE_STATUSES} —— 已经有人在做（统计「进行中」用）</li>
     *   <li>{@code ARCHIVED_STATUSES} —— 已经盖棺（不给编辑、可以重开）</li>
     * </ul>
     * OPEN 只在后者之外：没人接、能改、不能重开。
     *
     * <p>前端 {@code TicketDetail.vue} 有一份同义的 isArchived，两处必须同改。
     */
    public static final List<TicketStatus> ARCHIVED_STATUSES = List.of(
            TicketStatus.COMPLETED,
            TicketStatus.CLOSED
    );

    private final TicketRepository ticketRepository;
    private final TicketCommentRepository ticketCommentRepository;
    private final AiServiceClient aiServiceClient;
    private final ChatService chatService;
    private final ConversationMemoryManager memoryManager;

    @Transactional
    public TicketDTO createTicket(CreateTicketRequest request, Long creatorId, String creatorName) {
        Ticket ticket = new Ticket();
        ticket.setTitle(request.getTitle());
        ticket.setContent(request.getContent());
        ticket.setUrgency(request.getUrgency());
        ticket.setAttachments(request.getAttachments() != null ?
                new java.util.HashMap<>() {{ put("files", request.getAttachments()); }} : null);
        ticket.setGithubRepos(request.getGithubRepos());
        ticket.setPrice(request.getPrice());
        ticket.setAiPriceSuggestion(request.getAiPriceSuggestion());
        ticket.setCreatorId(creatorId);
        ticket.setCreatorName(creatorName);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setPriority(Priority.P2);

        // 开发者需求字段
        ticket.setProjectType(request.getProjectType());
        ticket.setTechStack(request.getTechStack());
        ticket.setBudget(request.getBudget());
        ticket.setDeadline(request.getDeadline());
        ticket.setDetailedRequirements(request.getDetailedRequirements());
        ticket.setIsVisible(true);

        ticket = ticketRepository.save(ticket);

        log.info("Ticket created: id={}, title={}, creator={}", ticket.getId(), ticket.getTitle(), creatorName);
        return toDTO(ticket);
    }

    public TicketDTO getTicket(Long id) {
        return getTicket(id, null);
    }

    /**
     * 工单详情。
     *
     * @param viewerId 请求者的用户 id，**可以是 null**（访客）。详情页现在是公开的，
     *                 匿名也能看完整内容 —— 唯一按身份分叉的是内部留言：只有该工单的
     *                 发布者和接单人看得到 {@code is_internal = true} 的留言。
     */
    public TicketDTO getTicket(Long id, Long viewerId) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "Ticket not found"));
        return toDTO(ticket, true, canSeeInternalComments(ticket, viewerId));
    }

    /**
     * 我是不是这张工单的"参与者"（发布者或接单人）。
     *
     * <p>用 {@code equals} 而不是 {@code ==}：两个都是 {@code Long}，{@code ==} 比的是
     * 引用，而 Long 只缓存 -128..127 —— 换成 {@code ==} 会变成"用户 5 看得见内部留言、
     * 用户 300 看不见"。{@code viewerId} 为 null 时整体为 false，访客天然看不到。
     */
    private static boolean isParticipant(Ticket ticket, Long viewerId) {
        return viewerId != null
                && (viewerId.equals(ticket.getCreatorId()) || viewerId.equals(ticket.getHandlerId()));
    }

    private static boolean canSeeInternalComments(Ticket ticket, Long viewerId) {
        return isParticipant(ticket, viewerId);
    }

    public PageResult<TicketDTO> listTickets(TicketQueryRequest query, Long userId, boolean isAdmin) {
        // ADMIN: 查询所有；普通用户: 只查自己的
        // 只有 ACCEPTED/COMPLETED 才按 handlerId 过滤；OPEN 是全局的（待接单）
        //
        // 这里**故意不看 includeAll**。includeAll 的语义只有一个：跳过下面
        // buildSpecification 里的「大厅可见性」谓词（工单被接单后会置
        // is_visible=false，从而从大厅消失）。它跟"查谁的工单"是两件不相干的事。
        // 此前两者共用一个开关，导致「已完成」这类页面一旦传 includeAll 想看到
        // 隐藏工单，归属范围会顺带从"我的"放大成"全站所有人的"。
        // 归属一律由调用方显式传 handlerId/creatorId 决定。
        if (!isAdmin && query.getHandlerId() == null && query.getCreatorId() == null
                && query.getParticipantId() == null) {
            TicketStatus status = query.getStatus();
            if (status == TicketStatus.ACCEPTED || status == TicketStatus.COMPLETED) {
                query.setHandlerId(userId);
            }
        }

        Specification<Ticket> spec = buildSpecification(query);
        PageRequest pageRequest = PageRequest.of(
                query.getPage() - 1,
                query.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<Ticket> page = ticketRepository.findAll(spec, pageRequest);
        List<TicketDTO> dtos = page.getContent().stream()
                .map(t -> toDTO(t, false, false))
                .collect(Collectors.toList());

        return PageResult.of(page.getTotalElements(), query.getPage(), query.getPageSize(), dtos);
    }

    public PageResult<TicketDTO> listMyTickets(Long userId, int pageNum, int pageSize) {
        PageRequest pageRequest = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Ticket> ticketPage = ticketRepository.findByCreatorIdAndDeletedAtIsNull(userId, pageRequest);

        List<TicketDTO> dtos = ticketPage.getContent().stream()
                .map(t -> toDTO(t, false, false))
                .collect(Collectors.toList());

        return PageResult.of(ticketPage.getTotalElements(), pageNum, pageSize, dtos);
    }

    public PageResult<TicketDTO> listMyAcceptedTickets(Long userId, int pageNum, int pageSize) {
        PageRequest pageRequest = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "acceptedAt"));
        Page<Ticket> ticketPage = ticketRepository.findByHandlerIdAndDeletedAtIsNull(userId, pageRequest);

        List<TicketDTO> dtos = ticketPage.getContent().stream()
                .map(t -> toDTO(t, false, false))
                .collect(Collectors.toList());

        return PageResult.of(ticketPage.getTotalElements(), pageNum, pageSize, dtos);
    }

    public PageResult<TicketDTO> listCompletedTickets(int pageNum, int pageSize) {
        PageRequest pageRequest = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "completedAt"));
        Page<Ticket> ticketPage = ticketRepository.findByStatusAndDeletedAtIsNull(TicketStatus.COMPLETED, pageRequest);

        List<TicketDTO> dtos = ticketPage.getContent().stream()
                .map(t -> toDTO(t, false, false))
                .collect(Collectors.toList());

        return PageResult.of(ticketPage.getTotalElements(), pageNum, pageSize, dtos);
    }

    @Transactional
    public TicketDTO updateTicket(Long id, UpdateTicketRequest request, Long userId) {
        Ticket ticket = requireCreator(id, userId, "编辑该工单");

        // 已完结的工单不给再改：私聊与 AI 记忆在归档时都已清空，改了也没人看得到。
        // 用 ARCHIVED_STATUSES 而不是 ACTIVE_STATUSES —— 后者不含 OPEN，会把
        // "还没人接的单不能编辑"变成事实，正好相反。
        if (ARCHIVED_STATUSES.contains(ticket.getStatus())) {
            throw new BusinessException(400, "已完结的工单不能编辑");
        }

        if (request.getTitle() != null) {
            ticket.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            ticket.setContent(request.getContent());
        }
        if (request.getCategory() != null) {
            ticket.setCategory(request.getCategory());
        }
        if (request.getPriority() != null) {
            ticket.setPriority(request.getPriority());
        }
        if (request.getUrgency() != null) {
            ticket.setUrgency(request.getUrgency());
        }
        if (request.getAttachments() != null) {
            ticket.setAttachments(new java.util.HashMap<>() {{ put("files", request.getAttachments()); }});
        }
        if (request.getGithubRepos() != null) {
            ticket.setGithubRepos(request.getGithubRepos());
        }
        if (request.getPrice() != null) {
            ticket.setPrice(request.getPrice());
        }

        ticket = ticketRepository.save(ticket);
        log.info("Ticket updated: id={}", id);
        return toDTO(ticket);
    }

    @Transactional
    public void deleteTicket(Long id, Long userId) {
        Ticket ticket = requireCreator(id, userId, "删除该工单");
        ticket.softDelete();
        ticketRepository.save(ticket);
        log.info("Ticket deleted: id={}", id);
    }

    /**
     * 接单 —— 全系统**唯一**的接单入口。
     *
     * 此前接单有三条路径，写两组互不相干的字段，而且没有任何查询看过其中一组：
     *
     *   PUT  /{id}/assign   → 只写 handler_id，零校验（可重复点、可把别人的单改到自己名下）
     *   POST /{id}/accept   → 写 handler_id + status，与 pickup 重复
     *   POST /{id}/pickup   → 写 accepted_by，但所有列表/统计查的都是 handler_id
     *
     * 后果是「接单」按钮点下去，工单从大厅下架了，却不在「我接的单」里 —— 两边
     * 各查一个字段，谁也不认对方写的那组。现在统一到 handler_id（因为所有查询
     * 都用它），并且把校验收在这一处。
     *
     * 注意 isVisible=false：这是"接单后大厅不再展示"的唯一机制，buildSpecification
     * 已经在按它过滤。但反过来说，凡是想查到自己接的单的列表，都必须带
     * includeAll=true，否则工单一被接单就从那个列表里消失。
     */
    @Transactional
    public TicketDTO pickupTicket(Long ticketId, Long userId, String username) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(404, "工单不存在"));
        requireLogin(userId);

        if (ticket.getHandlerId() != null) {
            throw new BusinessException(409, "该工单已被接单");
        }
        if (ticket.getCreatorId().equals(userId)) {
            throw new BusinessException(400, "不能接自己的工单");
        }
        // 此前没有任何状态校验，而 accepted_by 在库里全是 NULL，于是守卫形同虚设 ——
        // 任何已完成的、已关闭的、被驳回的工单都能被"接单"。
        if (ticket.getStatus() != TicketStatus.OPEN) {
            throw new BusinessException(400, "该工单当前状态不可接单");
        }

        ticket.setHandlerId(userId);
        ticket.setHandlerName(username);
        ticket.setIsVisible(false);
        ticket.setStatus(TicketStatus.ACCEPTED);
        ticket.setAcceptedAt(LocalDateTime.now());
        ticket = ticketRepository.save(ticket);

        log.info("Ticket picked up: id={}, handler={}", ticketId, username);

        // 通知发布者：在私聊里留一条系统消息。失败不影响接单本身。
        try {
            chatService.sendMessage(ticketId, userId, username, "HANDLER",
                    "我已接单，开始为您开发项目！如有任何问题请随时沟通。");
        } catch (Exception e) {
            log.error("发送接单通知失败", e);
        }

        return toDTO(ticket);
    }

    @Transactional
    public TicketDTO close(Long ticketId, Long userId) {
        Ticket ticket = requireCreator(ticketId, userId, "关闭该工单");

        // 这两条守卫是本批补的：close 此前是全文件唯一没有状态守卫的写方法
        // （pickup/complete/approve/reopen 都有）。COMPLETED 是已验收、私聊与
        // AI 记忆都已清空的终态，再推到 CLOSED 会让 completedAt 与 status 打架；
        // 重复 close 则会把 closedAt 越刷越新，把"什么时候关的"抹掉。
        // 只挡已完结 —— close 的语义本来就是发布者中途叫停，不该砍。
        if (ticket.getStatus() == TicketStatus.COMPLETED) {
            throw new BusinessException(400, "已完成的工单不能关闭");
        }
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new BusinessException(400, "该工单已关闭");
        }

        ticket.setStatus(TicketStatus.CLOSED);
        ticket.setClosedAt(LocalDateTime.now());
        ticket = ticketRepository.save(ticket);

        log.info("Ticket closed: id={}", ticketId);
        return toDTO(ticket);
    }

    /**
     * 接单者提交交付，进入待验收。
     *
     * 入参状态是 ACCEPTED 或 REJECTED：后者是"被驳回后重新交付"。此前只允许
     * ACCEPTED，而验收驳回会把状态置为 REJECTED —— 那 REJECTED 就是个**死状态**，
     * 接单者再也交不了，工单永远卡住。既然「待处理」把 REJECTED 列进来让人处理，
     * 这里就必须放行。
     */
    @Transactional
    public TicketDTO completeTicket(Long ticketId, CompleteTicketRequest request, Long userId) {
        Ticket ticket = requireHandler(ticketId, userId, "提交交付");

        if (ticket.getStatus() != TicketStatus.ACCEPTED
                && ticket.getStatus() != TicketStatus.REJECTED) {
            throw new BusinessException(400, "只有进行中或被驳回的工单可以提交交付");
        }

        if (request.getCompletionProof() != null) {
            ticket.setCompletionProof(request.getCompletionProof());
        }
        if (request.getGithubRepos() != null) {
            ticket.setGithubRepos(request.getGithubRepos());
        }
        // 重新交付时清掉上一次的驳回原因，否则验收页会挂着一条早已修掉的旧理由
        ticket.setRejectionReason(null);
        ticket.setStatus(TicketStatus.PENDING_APPROVAL);
        ticket = ticketRepository.save(ticket);

        log.info("Ticket completed (pending approval): id={}", ticketId);
        return toDTO(ticket);
    }

    @Transactional
    public TicketDTO approveTicket(Long ticketId, Boolean approved, String reason, Long userId) {
        Ticket ticket = requireCreator(ticketId, userId, "验收该工单");

        if (ticket.getStatus() != TicketStatus.PENDING_APPROVAL) {
            throw new BusinessException(400, "只有待验收的工单可以验收");
        }

        if (approved) {
            ticket.setStatus(TicketStatus.COMPLETED);
            ticket.setCompletedAt(LocalDateTime.now());
            // 验收通过 = 工单闭环归档，沟通记录随之清理。两处都要清，否则会出现
            // 「人看得见的没了、模型还记得」的错位：
            //   - 私聊（ticket_chat 表）整条删除
            //   - AI 对话的 Redis 记忆（ticket:chat:{id}）此前漏了，一并清掉
            // 前端在验收确认框和「去完成」弹窗里都写明了这一点，不让它变成
            // 一个背后悄悄发生的行为。
            ticket.setRejectionReason(null);
            chatService.deleteByTicketId(ticketId);
            memoryManager.chatScopeAndClear(ticketId);
            log.info("Ticket approved and completed: id={}", ticketId);
        } else {
            if (reason == null || reason.isBlank()) {
                throw new BusinessException(400, "驳回时必须填写理由");
            }
            ticket.setStatus(TicketStatus.REJECTED);
            ticket.setRejectionReason(reason);
            log.info("Ticket rejected: id={}, reason={}", ticketId, reason);
        }

        ticket = ticketRepository.save(ticket);
        return toDTO(ticket);
    }

    @Transactional
    public TicketDTO reopen(Long ticketId, Long userId) {
        Ticket ticket = requireCreator(ticketId, userId, "重新开放该工单");

        if (!ARCHIVED_STATUSES.contains(ticket.getStatus())) {
            throw new BusinessException(400, "只有已完成或已关闭的工单可以重新开放");
        }

        ticket.setStatus(TicketStatus.OPEN);
        ticket.setResolvedAt(null);
        ticket.setClosedAt(null);
        // 重新开放必须把接单人也清掉、并放回大厅。
        // 只改 status 的话会得到一个**谁也接不了、大厅也看不见**的死单：
        // pickup 要求 handlerId 为空，而 is_visible 仍是 false —— 两扇门同时关着。
        ticket.setHandlerId(null);
        ticket.setHandlerName(null);
        ticket.setIsVisible(true);
        // 上一轮的交付痕迹也要清。交付凭证卡（TicketDetail.vue）只判
        // `v-if="ticket.completionProof"`、不看状态，不清的话一张刚重开、显示
        // "待接单"的工单下面会挂着上一个接单人的交付凭证。
        ticket.setCompletionProof(null);
        ticket.setRejectionReason(null);
        ticket = ticketRepository.save(ticket);

        log.info("Ticket reopened: id={}", ticketId);
        return toDTO(ticket);
    }

    // ---------------- 「这一单里我站哪边」的三道门 ----------------

    /**
     * 六个写端点的共同前两道门：先证明"你是谁"，再证明"这一单里你站哪边"。
     *
     * <p>顺序是 404 → 401 → 403，**状态守卫（400）一律排在最后**。三条理由：
     * <ol>
     *   <li>404 最先：id 不存在时没有"别人"可言，404 是唯一诚实的答案。这不额外
     *       泄露存在性 —— GET /{id} 本来就人人可读，大厅也能枚举。</li>
     *   <li>401 先于 403：没有身份就谈不上"角色不对"，口径也与网关缺 token 时的
     *       401「请先登录」一致（直连 :8082 和走网关报的是同一件事）。</li>
     *   <li>403 先于 400：不把资源状态告诉无权的人。否则"只有进行中或被驳回的
     *       工单可以提交交付"会告诉一个非接单人"这单现在是 ACCEPTED"，还暗示
     *       "等状态对了就能交付" —— 这是假的。</li>
     * </ol>
     *
     * <p>用户 id 一律用 {@code userId.equals(...)} 比，**绝不用 `==`**：两个都是
     * Long，`==` 比的是引用，而 Long 只缓存 -128..127，会变成"用户 5 能行、
     * 用户 300 不行"。
     */
    private Ticket loadTicketOr404(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "工单不存在"));
    }

    /** 缺身份头 = 没登录。**不要**兜底成某个固定用户，那等于把权限白送出去。 */
    private void requireLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
    }

    /** 载入 + 证明我是发布者，返回实体供调用方继续做状态守卫与字段写入。 */
    private Ticket requireCreator(Long id, Long userId, String action) {
        Ticket ticket = loadTicketOr404(id);
        requireLogin(userId);
        if (!userId.equals(ticket.getCreatorId())) {
            throw new BusinessException(403, "只有发布者才能" + action);
        }
        return ticket;
    }

    /** 载入 + 证明我是接单人。编辑/删除/关闭/重开走 creator，交付走 handler。 */
    private Ticket requireHandler(Long id, Long userId, String action) {
        Ticket ticket = loadTicketOr404(id);
        requireLogin(userId);
        if (!userId.equals(ticket.getHandlerId())) {
            throw new BusinessException(403, "只有接单人可以" + action);
        }
        return ticket;
    }

    /**
     * 给「AI 辅助开发」三个入口用的接单人门禁：载入工单 + 要求我是接单人。
     *
     * <p>就是 {@link #requireHandler} 的公开版，没有另写一套校验 —— 权限规则一旦
     * 出现第二份实现，两处迟早会分叉。公开是因为 AI 门禁在 {@code AiQuotaService}/
     * 控制器一侧，需要复用它，而不是各自 {@code findById} 再比一次 handlerId。
     *
     * <p>错误码顺序刻意是 404 → 401 → 403：工单不存在和没登录要分开说，
     * 而"登录了但不是接单人"才谈得上 403。
     */
    public Ticket requireHandlerTicket(Long id, Long userId, String action) {
        return requireHandler(id, userId, action);
    }

    /**
     * 落库 AI 分析结果（技术建议 + 任务拆解）。
     *
     * <p>由控制器在 {@code AiQuotaGate.run(...)} 里于**事务外**拿到结果后调用，本方法
     * 自己开一个短事务写入。之所以结果必须落库而不是直接拼进响应：额度已经扣了，
     * 若响应在返回途中丢了（客户端超时、连接断开），不落库这次消耗就白花、内容再也
     * 取不回来 —— 而这个功能每张工单只能用一次，用户没有第二次机会。
     */
    @Transactional
    public TicketDTO saveAiAnalysis(Long ticketId, String techSuggestion, String taskBreakdown) {
        Ticket ticket = loadTicketOr404(ticketId);
        ticket.setAiTechSuggestion(techSuggestion);
        ticket.setAiTaskBreakdown(taskBreakdown);
        ticketRepository.save(ticket);
        return toDTO(ticket, false, false);
    }

    /**
     * 落库 AI 生成的 PRD，供后续重复下载（不重复扣费）。
     *
     * <p>下载接口此前**从不持久化** PRD：每点一次下载就重跑一次模型，几分钟 + 一次
     * 额度。功能改成"每工单一次"之后，不落库就意味着客户端下载中断 = 额度永久损失。
     */
    @Transactional
    public void saveAiPrd(Long ticketId, String prdContent) {
        Ticket ticket = loadTicketOr404(ticketId);
        ticket.setAiPrd(prdContent);
        ticketRepository.save(ticket);
    }

    public CommentDTO addComment(Long ticketId, AddCommentRequest request, Long userId, String userName) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(404, "Ticket not found"));

        TicketComment comment = new TicketComment();
        comment.setTicketId(ticketId);
        comment.setUserId(userId);
        comment.setUserName(userName);
        comment.setContent(request.getContent());
        comment.setIsInternal(request.getIsInternal());
        comment.setIsAiSuggested(request.getIsAiSuggested());

        comment = ticketCommentRepository.save(comment);
        log.info("Comment added: ticketId={}, userId={}", ticketId, userId);
        return toCommentDTO(comment);
    }

    public List<CommentDTO> getComments(Long ticketId) {
        return getComments(ticketId, null);
    }

    /**
     * 工单留言列表，按请求者身份过滤内部留言。
     *
     * <p>这个接口在网关放行匿名 GET 之后是**公开**的，所以过滤必须发生在服务端。
     * 此前它无条件返回全部留言（含 {@code is_internal = true}），前端只是把内部留言
     * 弱化显示 —— 也就是任何人打开 devtools 就能读到别人的内部沟通。详情公开后
     * 这个口子会连访客一起放进来，趁这次一并堵上。
     */
    public List<CommentDTO> getComments(Long ticketId, Long viewerId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(404, "Ticket not found"));
        List<TicketComment> comments = canSeeInternalComments(ticket, viewerId)
                ? ticketCommentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)
                // 仓库里现成的非内部查询，不用在内存里 filter —— 省得以后有人改成
                // "查全量再过滤"时忘了过滤
                : ticketCommentRepository.findByTicketIdAndIsInternalFalseOrderByCreatedAtAsc(ticketId);
        return comments.stream()
                .map(this::toCommentDTO)
                .collect(Collectors.toList());
    }

    public TicketStatsDTO getStats(Long userId, Long excludeCreatorId) {
        // OPEN: exclude own tickets if excludeCreatorId provided
        long open = (excludeCreatorId != null)
                ? ticketRepository.countOpenExcludingCreator(excludeCreatorId)
                : ticketRepository.countByStatusAndDeletedAtIsNull(TicketStatus.OPEN);

        if (userId != null) {
            // 「我」这个视角下的数字，口径按"这一单里我站哪边"分开：
            //   开发中   —— 我接的单（handlerId）
            //   待验收   —— 我发布的单（creatorId），见 pendingApprovalAsCreator
            //   已完成   —— 我参与的（发布者或接单人），见 completedAsParticipant
            //   进行中   —— 我发布的 / 我接的 两个方向各一个数，见 ACTIVE_STATUSES
            long accepted = ticketRepository.countByHandlerIdAndStatusAndDeletedAtIsNull(userId, TicketStatus.ACCEPTED);
            long pendingApproval = ticketRepository.countByHandlerIdAndStatusAndDeletedAtIsNull(userId, TicketStatus.PENDING_APPROVAL);
            long completed = ticketRepository.countByHandlerIdAndStatusAndDeletedAtIsNull(userId, TicketStatus.COMPLETED);
            long pendingApprovalAsCreator = ticketRepository.countByCreatorIdAndStatusAndDeletedAtIsNull(userId, TicketStatus.PENDING_APPROVAL);
            long completedAsParticipant = ticketRepository.countByParticipantIdAndStatus(userId, TicketStatus.COMPLETED);
            long createdTotal = ticketRepository.countByCreatorIdAndDeletedAtIsNull(userId);
            long handledTotal = ticketRepository.countByHandlerIdAndDeletedAtIsNull(userId);
            long activeAsCreator = ticketRepository.countByCreatorIdAndStatusInAndDeletedAtIsNull(userId, ACTIVE_STATUSES);
            long activeAsHandler = ticketRepository.countByHandlerIdAndStatusInAndDeletedAtIsNull(userId, ACTIVE_STATUSES);
            return new TicketStatsDTO(open, accepted, pendingApproval, completed,
                    pendingApprovalAsCreator, completedAsParticipant,
                    createdTotal, handledTotal, activeAsCreator, activeAsHandler);
        } else {
            // Global stats —— 没有"我"，四个新字段只能是 0（它们是纯个人口径的）。
            long accepted = ticketRepository.countByStatusAndDeletedAtIsNull(TicketStatus.ACCEPTED);
            long pendingApproval = ticketRepository.countByStatusAndDeletedAtIsNull(TicketStatus.PENDING_APPROVAL);
            long completed = ticketRepository.countByStatusAndDeletedAtIsNull(TicketStatus.COMPLETED);
            return new TicketStatsDTO(open, accepted, pendingApproval, completed,
                    pendingApproval, completed, 0L, 0L, 0L, 0L);
        }
    }

    private Specification<Ticket> buildSpecification(TicketQueryRequest query) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(criteriaBuilder.isNull(root.get("deletedAt")));

            // 默认只显示在大厅的工单（isVisible=true或null）。
            //
            // 注意是 TRUE.equals 而不是 FALSE.equals —— 这里原本写反了：条件写成
            // !FALSE.equals(includeAll)，于是它的真实语义变成了"传 false 才跳过
            // 可见性谓词"，与字段名和文档都相反。传 includeAll=true 想看到隐藏工单，
            // 结果可见性谓词反而被加上，接单后 is_visible=false 的工单一条也查不到。
            //
            // 它一直没被发现，是因为修复前全前端没有任何地方传过 includeAll；
            // 「待处理」「我接的单」「已完成」这几个列表一开始传这个参数，
            // 就会集体变成空表。实测：includeAll=true 返回 16（可见），false 返回 20（全部）。
            if (!Boolean.TRUE.equals(query.getIncludeAll())) {
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.equal(root.get("isVisible"), true),
                        criteriaBuilder.isNull(root.get("isVisible"))
                ));
            }

            if (query.getCreatorId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("creatorId"), query.getCreatorId()));
            }
            if (query.getExcludeCreatorId() != null) {
                predicates.add(criteriaBuilder.notEqual(root.get("creatorId"), query.getExcludeCreatorId()));
            }
            if (query.getHandlerId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("handlerId"), query.getHandlerId()));
            }
            // 「我参与的」：发布者或接单人都算。见 TicketQueryRequest.participantId。
            // handlerId 可以是 NULL（还没人接的单），NULL = X 在 SQL 里是 UNKNOWN，
            // 但 OR 的另一边成立就整体成立 —— 所以这里不需要额外判空。
            if (query.getParticipantId() != null) {
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.equal(root.get("creatorId"), query.getParticipantId()),
                        criteriaBuilder.equal(root.get("handlerId"), query.getParticipantId())
                ));
            }
            // statuses（多值）优先于 status（单值），二者互斥，不要同时传。
            // 多值这条路是给「待处理」用的：接单人要看"进行中 + 待验收 + 被驳回"三种，
            // 而分页在服务端做，前端筛选会让 total 和实际行数对不上。
            if (query.getStatuses() != null && !query.getStatuses().isEmpty()) {
                predicates.add(root.get("status").in(query.getStatuses()));
            } else if (query.getStatus() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), query.getStatus()));
            }
            if (query.getCategory() != null) {
                predicates.add(criteriaBuilder.equal(root.get("category"), query.getCategory()));
            }
            if (query.getPriority() != null) {
                predicates.add(criteriaBuilder.equal(root.get("priority"), query.getPriority()));
            }
            if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
                String pattern = "%" + query.getKeyword() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(root.get("title"), pattern),
                        criteriaBuilder.like(root.get("content"), pattern)
                ));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private TicketDTO toDTO(Ticket ticket) {
        return toDTO(ticket, false, false);
    }

    /**
     * @param includeComments         是否把留言一并塞进 DTO
     * @param includeInternalComments 是否包含 {@code is_internal = true} 的留言。
     *                                只有该工单的发布者/接单人传 true —— 这个参数没有
     *                                "默认安全但容易被写错"的形态，调用方必须显式表态，
     *                                因为写错的代价是把别人的内部沟通泄露给访客。
     */
    private TicketDTO toDTO(Ticket ticket, boolean includeComments, boolean includeInternalComments) {
        TicketDTO.TicketDTOBuilder builder = TicketDTO.builder()
                .id(ticket.getId())
                .title(ticket.getTitle())
                .content(ticket.getContent())
                .category(ticket.getCategory())
                .categoryConfidence(ticket.getCategoryConfidence())
                .priority(ticket.getPriority())
                .priorityScore(ticket.getPriorityScore())
                .status(ticket.getStatus())
                .urgency(ticket.getUrgency())
                .creatorId(ticket.getCreatorId())
                .creatorName(ticket.getCreatorName())
                .handlerId(ticket.getHandlerId())
                .handlerName(ticket.getHandlerName())
                .recommendedHandlerId(ticket.getRecommendedHandlerId())
                .recommendReason(ticket.getRecommendReason())
                .aiSummary(ticket.getAiSummary())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .resolvedAt(ticket.getResolvedAt())
                .closedAt(ticket.getClosedAt());

        if (ticket.getAttachments() != null && ticket.getAttachments().containsKey("files")) {
            builder.attachments((List) ticket.getAttachments().get("files"));
        }

        builder.githubRepos(ticket.getGithubRepos())
                .price(ticket.getPrice())
                .aiPriceSuggestion(ticket.getAiPriceSuggestion())
                .completionProof(ticket.getCompletionProof())
                .rejectionReason(ticket.getRejectionReason())
                .acceptedAt(ticket.getAcceptedAt())
                .completedAt(ticket.getCompletedAt());

        // 开发者需求字段
        builder.techStack(ticket.getTechStack())
                .projectType(ticket.getProjectType())
                .budget(ticket.getBudget())
                .deadline(ticket.getDeadline())
                .detailedRequirements(ticket.getDetailedRequirements());

        // AI生成内容
        builder.aiPrd(ticket.getAiPrd())
                .aiTechSuggestion(ticket.getAiTechSuggestion())
                .aiTaskBreakdown(ticket.getAiTaskBreakdown())
                .aiEstimatedHours(ticket.getAiEstimatedHours());

        // 接单状态。接单人就是 handlerId/handlerName（见 pickupTicket 的注释）；
        // 曾经并存的 accepted_by / accepted_by_name 已废弃删除 —— 没有任何查询
        // 看过它们，留着只会让"接单人到底是哪个字段"继续含糊下去。
        builder.isVisible(ticket.getIsVisible());

        if (includeComments) {
            List<CommentDTO> comments = (includeInternalComments
                    ? ticketCommentRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId())
                    : ticketCommentRepository.findByTicketIdAndIsInternalFalseOrderByCreatedAtAsc(ticket.getId()))
                    .stream()
                    .map(this::toCommentDTO)
                    .collect(Collectors.toList());
            builder.comments(comments);
            // commentCount 与 comments 必须同源：一个走过滤、一个走总数，界面上就会
            // 出现"写着 5 条评论、列表里只有 3 条"的诡异差异
            builder.commentCount((long) comments.size());
        } else {
            builder.commentCount(ticketCommentRepository.countByTicketId(ticket.getId()));
        }

        return builder.build();
    }

    private CommentDTO toCommentDTO(TicketComment comment) {
        return CommentDTO.builder()
                .id(comment.getId())
                .ticketId(comment.getTicketId())
                .userId(comment.getUserId())
                .userName(comment.getUserName())
                .content(comment.getContent())
                .isInternal(comment.getIsInternal())
                .isAiSuggested(comment.getIsAiSuggested())
                .references(comment.getReferences())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}

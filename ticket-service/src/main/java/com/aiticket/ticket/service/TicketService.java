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
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "Ticket not found"));
        return toDTO(ticket, true);
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
        if (!isAdmin && query.getHandlerId() == null && query.getCreatorId() == null) {
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
                .map(t -> toDTO(t, false))
                .collect(Collectors.toList());

        return PageResult.of(page.getTotalElements(), query.getPage(), query.getPageSize(), dtos);
    }

    public PageResult<TicketDTO> listMyTickets(Long userId, int pageNum, int pageSize) {
        PageRequest pageRequest = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Ticket> ticketPage = ticketRepository.findByCreatorIdAndDeletedAtIsNull(userId, pageRequest);

        List<TicketDTO> dtos = ticketPage.getContent().stream()
                .map(t -> toDTO(t, false))
                .collect(Collectors.toList());

        return PageResult.of(ticketPage.getTotalElements(), pageNum, pageSize, dtos);
    }

    public PageResult<TicketDTO> listMyAcceptedTickets(Long userId, int pageNum, int pageSize) {
        PageRequest pageRequest = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "acceptedAt"));
        Page<Ticket> ticketPage = ticketRepository.findByHandlerIdAndDeletedAtIsNull(userId, pageRequest);

        List<TicketDTO> dtos = ticketPage.getContent().stream()
                .map(t -> toDTO(t, false))
                .collect(Collectors.toList());

        return PageResult.of(ticketPage.getTotalElements(), pageNum, pageSize, dtos);
    }

    public PageResult<TicketDTO> listCompletedTickets(int pageNum, int pageSize) {
        PageRequest pageRequest = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "completedAt"));
        Page<Ticket> ticketPage = ticketRepository.findByStatusAndDeletedAtIsNull(TicketStatus.COMPLETED, pageRequest);

        List<TicketDTO> dtos = ticketPage.getContent().stream()
                .map(t -> toDTO(t, false))
                .collect(Collectors.toList());

        return PageResult.of(ticketPage.getTotalElements(), pageNum, pageSize, dtos);
    }

    @Transactional
    public TicketDTO updateTicket(Long id, UpdateTicketRequest request) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "Ticket not found"));

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
    public void deleteTicket(Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "Ticket not found"));
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
    public TicketDTO close(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(404, "Ticket not found"));

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
    public TicketDTO completeTicket(Long ticketId, CompleteTicketRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(404, "Ticket not found"));

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
    public TicketDTO approveTicket(Long ticketId, Boolean approved, String reason) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(404, "Ticket not found"));

        if (ticket.getStatus() != TicketStatus.PENDING_APPROVAL) {
            throw new BusinessException(400, "Only PENDING_APPROVAL tickets can be approved");
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
                throw new BusinessException(400, "Rejection reason is required");
            }
            ticket.setStatus(TicketStatus.REJECTED);
            ticket.setRejectionReason(reason);
            log.info("Ticket rejected: id={}, reason={}", ticketId, reason);
        }

        ticket = ticketRepository.save(ticket);
        return toDTO(ticket);
    }

    @Transactional
    public TicketDTO reopen(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(404, "Ticket not found"));

        if (ticket.getStatus() != TicketStatus.COMPLETED && ticket.getStatus() != TicketStatus.CLOSED) {
            throw new BusinessException(400, "Only completed or closed tickets can be reopened");
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
        ticket = ticketRepository.save(ticket);

        log.info("Ticket reopened: id={}", ticketId);
        return toDTO(ticket);
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
        return ticketCommentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)
                .stream()
                .map(this::toCommentDTO)
                .collect(Collectors.toList());
    }

    public TicketStatsDTO getStats(Long handlerId, Long excludeCreatorId) {
        // OPEN: exclude own tickets if excludeCreatorId provided
        long open = (excludeCreatorId != null)
                ? ticketRepository.countOpenExcludingCreator(excludeCreatorId)
                : ticketRepository.countByStatusAndDeletedAtIsNull(TicketStatus.OPEN);

        if (handlerId != null) {
            // Stats for specific handler's accepted tickets
            long accepted = ticketRepository.countByHandlerIdAndStatusAndDeletedAtIsNull(handlerId, TicketStatus.ACCEPTED);
            long pendingApproval = ticketRepository.countByHandlerIdAndStatusAndDeletedAtIsNull(handlerId, TicketStatus.PENDING_APPROVAL);
            long completed = ticketRepository.countByHandlerIdAndStatusAndDeletedAtIsNull(handlerId, TicketStatus.COMPLETED);
            return new TicketStatsDTO(open, accepted, pendingApproval, completed);
        } else {
            // Global stats
            long accepted = ticketRepository.countByStatusAndDeletedAtIsNull(TicketStatus.ACCEPTED);
            long pendingApproval = ticketRepository.countByStatusAndDeletedAtIsNull(TicketStatus.PENDING_APPROVAL);
            long completed = ticketRepository.countByStatusAndDeletedAtIsNull(TicketStatus.COMPLETED);
            return new TicketStatsDTO(open, accepted, pendingApproval, completed);
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
        return toDTO(ticket, false);
    }

    private TicketDTO toDTO(Ticket ticket, boolean includeComments) {
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
            List<CommentDTO> comments = ticketCommentRepository
                    .findByTicketIdOrderByCreatedAtAsc(ticket.getId())
                    .stream()
                    .map(this::toCommentDTO)
                    .collect(Collectors.toList());
            builder.comments(comments);
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

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

        ticket = ticketRepository.save(ticket);

        // AI analysis removed - only manual "AI生成标题" button triggers AI
        // Previously auto-called: classify, priority, recommendHandler, generateSummary

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
        // includeAll=true 时跳过 handlerId 过滤
        if (!isAdmin && query.getHandlerId() == null && query.getCreatorId() == null
                && !Boolean.TRUE.equals(query.getIncludeAll())) {
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

    public PageResult<TicketDTO> listPendingTickets(Long handlerId, int pageNum, int pageSize) {
        PageRequest pageRequest = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Ticket> ticketPage;
        if (handlerId != null) {
            ticketPage = ticketRepository.findByHandlerIdAndDeletedAtIsNull(handlerId, pageRequest);
        } else {
            ticketPage = ticketRepository.findOpenTickets(pageRequest);
        }

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

    @Transactional
    public TicketDTO assignHandler(Long ticketId, Long handlerId, String handlerName) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(404, "Ticket not found"));

        ticket.setHandlerId(handlerId);
        ticket.setHandlerName(handlerName);
        ticket = ticketRepository.save(ticket);

        log.info("Ticket assigned: id={}, handler={}", ticketId, handlerName);
        return toDTO(ticket);
    }

    @Transactional
    public TicketDTO startProcessing(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(404, "Ticket not found"));

        if (ticket.getHandlerId() == null) {
            throw new BusinessException(400, "Ticket must be assigned before starting processing");
        }

        ticket.setStatus(TicketStatus.ACCEPTED);
        ticket = ticketRepository.save(ticket);

        log.info("Ticket processing started: id={}", ticketId);
        return toDTO(ticket);
    }

    @Transactional
    public TicketDTO resolve(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(404, "Ticket not found"));

        ticket.setStatus(TicketStatus.COMPLETED);
        ticket.setResolvedAt(LocalDateTime.now());
        ticket = ticketRepository.save(ticket);

        log.info("Ticket resolved: id={}", ticketId);
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

    @Transactional
    public TicketDTO acceptTicket(Long ticketId, Long handlerId, String handlerName) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(404, "Ticket not found"));

        if (ticket.getStatus() != TicketStatus.OPEN) {
            throw new BusinessException(400, "Only OPEN tickets can be accepted");
        }
        if (ticket.getHandlerId() != null) {
            throw new BusinessException(400, "Ticket already has a handler");
        }

        ticket.setHandlerId(handlerId);
        ticket.setHandlerName(handlerName);
        ticket.setStatus(TicketStatus.ACCEPTED);
        ticket.setAcceptedAt(LocalDateTime.now());
        ticket = ticketRepository.save(ticket);

        log.info("Ticket accepted: id={}, handler={}", ticketId, handlerName);
        return toDTO(ticket);
    }

    @Transactional
    public TicketDTO completeTicket(Long ticketId, CompleteTicketRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(404, "Ticket not found"));

        if (ticket.getStatus() != TicketStatus.ACCEPTED) {
            throw new BusinessException(400, "Only ACCEPTED tickets can be completed");
        }

        if (request.getCompletionProof() != null) {
            ticket.setCompletionProof(request.getCompletionProof());
        }
        if (request.getGithubRepos() != null) {
            ticket.setGithubRepos(request.getGithubRepos());
        }
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
            // Delete chat history after completion
            chatService.deleteByTicketId(ticketId);
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

            if (query.getCreatorId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("creatorId"), query.getCreatorId()));
            }
            if (query.getExcludeCreatorId() != null) {
                predicates.add(criteriaBuilder.notEqual(root.get("creatorId"), query.getExcludeCreatorId()));
            }
            if (query.getHandlerId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("handlerId"), query.getHandlerId()));
            }
            if (query.getStatus() != null) {
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

package com.aiticket.ticket.controller;

import com.aiticket.common.dto.PageResult;
import com.aiticket.common.dto.Result;
import com.aiticket.ticket.dto.*;
import com.aiticket.ticket.service.ChatService;
import com.aiticket.ticket.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final ChatService chatService;

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
}

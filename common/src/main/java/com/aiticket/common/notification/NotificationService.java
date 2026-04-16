package com.aiticket.common.notification;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class NotificationService {

    // Store emitters by user ID
    private final Map<Long, List<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // No timeout

        userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(e -> removeEmitter(userId, emitter));

        // Send initial connection event
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"status\":\"connected\"}"));
        } catch (IOException e) {
            log.error("Failed to send initial connection event for user {}", userId, e);
        }

        log.info("User {} subscribed to notifications", userId);
        return emitter;
    }

    public void sendToUser(Long userId, NotificationMessage message) {
        List<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            log.debug("No active emitter for user {}", userId);
            return;
        }

        String data = message.toJson();
        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(message.getType())
                        .data(data));
            } catch (IOException e) {
                log.warn("Failed to send notification to user {}, removing emitter", userId);
                deadEmitters.add(emitter);
            }
        }

        deadEmitters.forEach(emitters::remove);
    }

    public void sendToHandlers(NotificationMessage message) {
        List<SseEmitter> allHandlersEmitters = new CopyOnWriteArrayList<>();

        // Collect emitters for all handlers (role = HANDLER)
        // Note: In production, you'd want to query the user service for handler IDs
        for (Map.Entry<Long, List<SseEmitter>> entry : userEmitters.entrySet()) {
            allHandlersEmitters.addAll(entry.getValue());
        }

        if (allHandlersEmitters.isEmpty()) {
            log.debug("No handler emitters for notification: {}", message.getType());
            return;
        }

        String data = message.toJson();
        for (SseEmitter emitter : allHandlersEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(message.getType())
                        .data(data));
            } catch (IOException e) {
                log.warn("Failed to send notification to handler");
            }
        }
    }

    public void notifyNewTicket(Long handlerId, String ticketTitle, String creatorName) {
        sendToUser(handlerId, NotificationMessage.builder()
                .type("new_ticket")
                .title("新工单待处理")
                .content(String.format("用户 %s 提交了新工单: %s", creatorName, ticketTitle))
                .data(Map.of(
                        "ticketTitle", ticketTitle,
                        "creatorName", creatorName
                ))
                .build());
    }

    public void notifyTicketAssigned(Long userId, String ticketTitle, String assignedBy) {
        sendToUser(userId, NotificationMessage.builder()
                .type("ticket_assigned")
                .title("工单已分配")
                .content(String.format("工单 \"%s\" 已分配给您", ticketTitle))
                .data(Map.of("ticketTitle", ticketTitle))
                .build());
    }

    public void notifyTicketUpdated(Long userId, String ticketTitle, String updateType) {
        sendToUser(userId, NotificationMessage.builder()
                .type("ticket_updated")
                .title("工单状态更新")
                .content(String.format("工单 \"%s\" 已%s", ticketTitle, updateType))
                .data(Map.of(
                        "ticketTitle", ticketTitle,
                        "updateType", updateType
                ))
                .build());
    }

    public void notifyAiSuggestion(Long handlerId, String ticketTitle, String suggestionType) {
        sendToUser(handlerId, NotificationMessage.builder()
                .type("ai_suggestion")
                .title("AI 建议")
                .content(String.format("工单 \"%s\" 有新的 AI %s", ticketTitle, suggestionType))
                .data(Map.of(
                        "ticketTitle", ticketTitle,
                        "suggestionType", suggestionType
                ))
                .build());
    }

    public void notifyComment(Long userId, String ticketTitle, String commenterName, boolean isInternal) {
        String type = isInternal ? "内部留言" : "新回复";
        sendToUser(userId, NotificationMessage.builder()
                .type("new_comment")
                .title(type)
                .content(String.format("%s 在工单 \"%s\" 中留言", commenterName, ticketTitle))
                .data(Map.of(
                        "ticketTitle", ticketTitle,
                        "commenterName", commenterName
                ))
                .build());
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        List<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                userEmitters.remove(userId);
            }
        }
        log.info("Emitter removed for user {}", userId);
    }

    public long getActiveConnectionCount() {
        return userEmitters.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    @Data
    @Builder
    public static class NotificationMessage {
        private String type;
        private String title;
        private String content;
        private Map<String, Object> data;
        private long timestamp;

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"type\":\"").append(type).append("\",");
            sb.append("\"title\":\"").append(title != null ? escapeJson(title) : "").append("\",");
            sb.append("\"content\":\"").append(content != null ? escapeJson(content) : "").append("\",");
            sb.append("\"timestamp\":").append(timestamp > 0 ? timestamp : System.currentTimeMillis());
            if (data != null && !data.isEmpty()) {
                sb.append(",\"data\":{");
                boolean first = true;
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    if (!first) sb.append(",");
                    sb.append("\"").append(entry.getKey()).append("\":");
                    Object value = entry.getValue();
                    if (value instanceof String) {
                        sb.append("\"").append(escapeJson((String) value)).append("\"");
                    } else {
                        sb.append(value);
                    }
                    first = false;
                }
                sb.append("}");
            }
            sb.append("}");
            return sb.toString();
        }

        private String escapeJson(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }
    }
}

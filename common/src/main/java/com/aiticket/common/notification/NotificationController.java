package com.aiticket.common.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestHeader("X-User-Id") Long userId) {
        return notificationService.subscribe(userId);
    }

    @GetMapping("/stats")
    public Object getStats() {
        return new Object() {
            public final long activeConnections = notificationService.getActiveConnectionCount();
        };
    }
}

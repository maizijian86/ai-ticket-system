package com.aiticket.ticket.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "ai-service", url = "${ai-service.url:http://localhost:8083}")
public interface AiServiceClient {

    @PostMapping("/api/v1/ai/classify")
    Map<String, Object> classify(@RequestBody Map<String, String> request);

    @PostMapping("/api/v1/ai/priority")
    Map<String, Object> evaluatePriority(@RequestBody Map<String, Object> request);

    @PostMapping("/api/v1/ai/recommend-handler")
    Map<String, Object> recommendHandler(@RequestBody Map<String, Object> request);

    @PostMapping("/api/v1/ai/summary")
    Map<String, Object> generateSummary(@RequestBody Map<String, String> request);

    @PostMapping("/api/v1/ai/suggest-reply")
    Map<String, Object> suggestReply(@RequestBody Map<String, Object> request);

    @PostMapping("/api/v1/ai/recommend-price")
    Map<String, Object> recommendPrice(@RequestBody Map<String, Object> request);
}

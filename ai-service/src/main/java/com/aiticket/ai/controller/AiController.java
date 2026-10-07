package com.aiticket.ai.controller;

import com.aiticket.ai.service.DeveloperAiService;
import com.aiticket.common.dto.Result;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final DeveloperAiService developerAiService;

    /**
     * 生成PRD并下载为Markdown文件
     */
    @PostMapping("/generate-prd")
    public void generatePrd(@Valid @RequestBody PrdGenerateRequest request,
                            HttpServletResponse response) throws IOException {
        String prdContent = developerAiService.generatePrd(
                request.getUserId(),
                request.getProjectInfo()
        );

        response.setContentType("text/markdown; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        String fileName = "PRD_" + System.currentTimeMillis() + ".md";
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

        PrintWriter out = response.getWriter();
        out.print(prdContent);
        out.flush();
    }

    /**
     * 根据工单内容生成开发建议
     */
    @PostMapping("/dev-advice")
    public Result<DevAdviceResponse> generateDevAdvice(@Valid @RequestBody DevAdviceRequest request) {
        String advice = developerAiService.generateDevAdvice(
                request.getUserId(),
                request.getTicketContent()
        );
        return Result.success(new DevAdviceResponse(advice));
    }

    @lombok.Data
    public static class PrdGenerateRequest {
        private Long userId;
        private String projectInfo;
    }

    @lombok.Data
    public static class DevAdviceRequest {
        private Long userId;
        private String ticketContent;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class DevAdviceResponse {
        private String advice;
    }
}

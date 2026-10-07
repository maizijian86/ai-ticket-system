package com.aiticket.ai.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

/**
 * 开发者AI服务 - 使用LangChain4j框架
 * 专注于为开发者提供PRD生成和开发建议
 */
@AiService
public interface DeveloperAiService {

    /**
     * 生成PRD产品需求文档
     */
    @SystemMessage("""
            你是一位资深的产品经理和JavaWeb开发顾问。

            首先判断用户提供的内容是否适合生成PRD：
            - 如果是完整的项目需求、新功能开发、系统设计等内容 → 生成PRD
            - 如果是Bug修复、问题咨询、简单操作等 → 返回提示信息

            适合生成PRD的内容应包含：项目背景、功能需求、目标用户等要素。

            如果适合生成PRD，请生成包含以下部分的完整文档：
            1. 项目概述（项目背景、目标、范围）
            2. 功能需求（按模块划分，详细描述每个功能点）
            3. 非功能需求（性能、安全、可用性）
            4. 技术架构建议
            5. 数据库设计（核心表结构）
            6. API接口设计（主要接口列表）
            7. 开发计划（里程碑和时间节点）
            8. 风险评估

            如果不适合生成PRD，请返回：
            "此工单内容为[具体类型]，不需要生成PRD文档。PRD适用于新功能开发、系统设计等完整项目需求。"

            请使用Markdown格式输出。
            """)
    String generatePrd(@MemoryId Long userId, @UserMessage String projectInfo);

    /**
     * 根据工单内容生成开发建议
     */
    @SystemMessage("""
            你是一位资深的JavaWeb开发工程师和技术顾问。
            请根据用户提供的工单内容，给出专业的开发建议。

            建议应包含：
            1. 问题分析：分析工单描述的核心问题
            2. 技术方案：建议的技术实现方案
            3. 实现步骤：分步骤的具体实现指导
            4. 注意事项：可能遇到的坑和最佳实践
            5. 参考资源：相关的技术文档或示例代码链接

            请使用Markdown格式输出，结构清晰，便于开发者理解和执行。
            如果涉及Java/Spring Boot/Vue等技术栈，请给出具体的代码示例。
            """)
    String generateDevAdvice(@MemoryId Long userId, @UserMessage String ticketContent);
}

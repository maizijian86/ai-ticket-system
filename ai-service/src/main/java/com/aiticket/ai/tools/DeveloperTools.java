package com.aiticket.ai.tools;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 开发者工具类 - 提供给AI调用的工具方法
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeveloperTools {

    private final EmbeddingStore<TextSegment> embeddingStore;

    /**
     * 保存PRD文档到向量数据库
     */
    @Tool("保存PRD文档到知识库，以便后续检索相似项目的PRD")
    public String savePrdToKnowledgeBase(String projectName, String prdContent) {
        try {
            Document document = Document.from(prdContent);

            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .embeddingStore(embeddingStore)
                    .build();

            ingestor.ingest(document);
            log.info("PRD文档已保存到向量数据库: {}", projectName);
            return "PRD文档已成功保存到知识库，项目名: " + projectName;
        } catch (Exception e) {
            log.error("保存PRD文档失败", e);
            return "保存失败: " + e.getMessage();
        }
    }

    /**
     * 查询相似项目案例
     */
    @Tool("查询相似项目的历史案例和最佳实践")
    public String querySimilarProjects(String query) {
        try {
            // 简化实现 - 返回提示信息
            return "正在查询相似项目案例... 暂未找到相似项目案例，这是第一个项目。";
        } catch (Exception e) {
            log.error("查询相似项目失败", e);
            return "查询失败: " + e.getMessage();
        }
    }

    /**
     * 生成技术栈推荐报告
     */
    @Tool("生成技术栈推荐报告，包含版本信息和配置建议")
    public String generateTechStackReport(String projectType, String requirements) {
        StringBuilder report = new StringBuilder();
        report.append("## 技术栈推荐报告\n\n");
        report.append("### 项目类型: ").append(projectType).append("\n\n");

        // 根据项目类型推荐技术栈
        if (projectType.toLowerCase().contains("javaweb") || projectType.toLowerCase().contains("java")) {
            report.append("### 后端技术栈\n");
            report.append("- **框架**: Spring Boot 3.2.x\n");
            report.append("- **ORM**: MyBatis-Plus 3.5.x\n");
            report.append("- **安全**: Spring Security + JWT\n");
            report.append("- **文档**: Knife4j (Swagger)\n\n");

            report.append("### 前端技术栈\n");
            report.append("- **框架**: Vue 3 + Composition API\n");
            report.append("- **UI**: Element Plus\n");
            report.append("- **构建**: Vite\n");
            report.append("- **状态管理**: Pinia\n\n");

            report.append("### 数据库\n");
            report.append("- **主库**: MySQL 8.0\n");
            report.append("- **缓存**: Redis 7.x\n\n");

            report.append("### 部署方案\n");
            report.append("- **容器**: Docker + Docker Compose\n");
            report.append("- **反向代理**: Nginx\n");
        }

        return report.toString();
    }
}

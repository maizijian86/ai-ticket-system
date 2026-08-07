package com.aiticket.ai.config;

import com.aiticket.ai.entity.KnowledgeBase;
import com.aiticket.ai.repository.KnowledgeBaseRepository;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Slf4j
@Configuration
public class LangChainConfig {

    /**
     * JVM内存向量数据库
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }

    /**
     * RAG内容检索器
     */
    @Bean
    public ContentRetriever contentRetriever(EmbeddingStore<TextSegment> store) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .minScore(0.6)
                .maxResults(3)
                .build();
    }

    /**
     * 启动时加载知识库文档到向量数据库
     */
    @Bean
    public CommandLineRunner loadKnowledgeBase(KnowledgeBaseRepository repository,
                                               EmbeddingStore<TextSegment> store) {
        return args -> {
            try {
                List<KnowledgeBase> knowledgeList = repository.findAll();
                if (knowledgeList.isEmpty()) {
                    log.info("知识库为空，跳过加载");
                    return;
                }

                EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                        .embeddingStore(store)
                        .build();

                for (KnowledgeBase kb : knowledgeList) {
                    Document document = Document.from(kb.getContent());
                    ingestor.ingest(document);
                    log.info("已加载知识库文档: {}", kb.getTitle());
                }

                log.info("知识库加载完成，共加载 {} 篇文档", knowledgeList.size());
            } catch (Exception e) {
                log.error("加载知识库失败", e);
            }
        };
    }
}

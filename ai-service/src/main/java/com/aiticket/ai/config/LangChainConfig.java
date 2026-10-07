package com.aiticket.ai.config;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.ClassPathDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class LangChainConfig {

    private static final Logger log = LoggerFactory.getLogger(LangChainConfig.class);

    /**
     * 构建向量数据库操作对象
     * 从 docs 目录加载文档到内存
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        // 加载文档
        List<Document> documents = ClassPathDocumentLoader.loadDocuments("docs");
        log.info("从 docs 目录加载了 {} 篇文档", documents.size());

        // 构建文档分割器
        DocumentSplitter splitter = DocumentSplitters.recursive(500, 100);

        // 构建向量数据库
        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();

        // 构建 EmbeddingStoreIngestor，完成文本切割、向量化、存储
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingStore(store)
                .documentSplitter(splitter)
                .build();
        ingestor.ingest(documents);

        log.info("文档向量化完成");
        return store;
    }

    /**
     * 构建向量数据库检索对象
     */
    @Bean
    public ContentRetriever contentRetriever(EmbeddingStore<TextSegment> store) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .minScore(0.6)
                .maxResults(5)
                .build();
    }
}

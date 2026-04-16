package com.aiticket.ai.repository;

import com.aiticket.ai.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {

    List<KnowledgeBase> findByStatus(String status);

    List<KnowledgeBase> findByCategory(String category);

    // MySQL FULLTEXT search using MATCH...AGAINST
    @Query(value = "SELECT * FROM knowledge_base WHERE status = 'PUBLISHED' " +
            "AND MATCH(title, content) AGAINST(:keyword IN NATURAL LANGUAGE MODE) " +
            "ORDER BY MATCH(title, content) AGAINST(:keyword IN NATURAL LANGUAGE MODE) DESC, " +
            "helpful_count DESC LIMIT :limit",
            nativeQuery = true)
    List<KnowledgeBase> findByKeyword(@Param("keyword") String keyword, @Param("limit") int limit);

    // Fallback LIKE search when FULLTEXT doesn't have results
    @Query(value = "SELECT * FROM knowledge_base WHERE status = 'PUBLISHED' " +
            "AND (title LIKE CONCAT('%', :keyword, '%') OR content LIKE CONCAT('%', :keyword, '%')) " +
            "ORDER BY helpful_count DESC, view_count DESC LIMIT :limit",
            nativeQuery = true)
    List<KnowledgeBase> findByKeywordFallback(@Param("keyword") String keyword, @Param("limit") int limit);

    @Modifying
    @Query("UPDATE KnowledgeBase k SET k.viewCount = k.viewCount + 1 WHERE k.id = :id")
    void incrementViewCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE KnowledgeBase k SET k.helpfulCount = k.helpfulCount + 1 WHERE k.id = :id")
    void incrementHelpfulCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE KnowledgeBase k SET k.notHelpfulCount = k.notHelpfulCount + 1 WHERE k.id = :id")
    void incrementNotHelpfulCount(@Param("id") Long id);
}

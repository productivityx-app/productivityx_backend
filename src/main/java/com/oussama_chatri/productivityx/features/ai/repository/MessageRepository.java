package com.oussama_chatri.productivityx.features.ai.repository;

import com.oussama_chatri.productivityx.features.ai.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    @Query("""
            SELECT m FROM Message m
            WHERE m.conversation.id = :conversationId
            ORDER BY m.createdAt ASC
            """)
    List<Message> findByConversationIdOrdered(@Param("conversationId") UUID conversationId);

    // Recent messages only — avoids loading unbounded history into the Gemini context window
    @Query("""
            SELECT m FROM Message m
            WHERE m.conversation.id = :conversationId
            ORDER BY m.createdAt DESC
            LIMIT :limit
            """)
    List<Message> findRecentByConversationId(
            @Param("conversationId") UUID conversationId,
            @Param("limit") int limit);
}

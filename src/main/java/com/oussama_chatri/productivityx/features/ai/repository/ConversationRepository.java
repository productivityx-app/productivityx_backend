package com.oussama_chatri.productivityx.features.ai.repository;

import com.oussama_chatri.productivityx.features.ai.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    @Query("""
            SELECT c FROM Conversation c
            WHERE c.userId = :userId AND c.archived = false
            ORDER BY c.updatedAt DESC
            """)
    Page<Conversation> findActiveByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            SELECT c FROM Conversation c
            LEFT JOIN FETCH c.messages m
            WHERE c.id = :id AND c.userId = :userId
            ORDER BY m.createdAt ASC
            """)
    Optional<Conversation> findByIdAndUserIdWithMessages(
            @Param("id") UUID id,
            @Param("userId") UUID userId);

    Optional<Conversation> findByIdAndUserId(UUID id, UUID userId);
}

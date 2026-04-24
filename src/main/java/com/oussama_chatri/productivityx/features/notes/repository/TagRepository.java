package com.oussama_chatri.productivityx.features.notes.repository;

import com.oussama_chatri.productivityx.features.notes.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface TagRepository extends JpaRepository<Tag, UUID> {

    List<Tag> findByUserIdOrderByNameAsc(UUID userId);

    Optional<Tag> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndName(UUID userId, String name);

    // Single-query bulk load — replaces N+1 loop in NoteServiceImpl
    @Query("SELECT t FROM Tag t WHERE t.id IN :ids AND t.userId = :userId")
    Set<Tag> findAllByIdInAndUserId(@Param("ids") Set<UUID> ids, @Param("userId") UUID userId);

    @Query("""
            SELECT t, COUNT(n) FROM Tag t
            LEFT JOIN t.notes n ON n.deleted = false
            WHERE t.userId = :userId
            GROUP BY t.id
            ORDER BY t.name ASC
            """)
    List<Object[]> findTagsWithNoteCount(@Param("userId") UUID userId);
}

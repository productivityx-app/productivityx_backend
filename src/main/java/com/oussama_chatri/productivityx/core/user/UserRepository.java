package com.oussama_chatri.productivityx.core.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByPhone(String phone);

    // Multi-identifier lookup — supports login by email, username, or phone
    @Query("""
            SELECT u FROM User u
            WHERE LOWER(u.email) = LOWER(:identifier)
               OR u.username = :identifier
               OR u.phone = :identifier
            """)
    Optional<User> findByIdentifier(@Param("identifier") String identifier);
}

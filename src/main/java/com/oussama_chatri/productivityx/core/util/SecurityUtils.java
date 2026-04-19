package com.oussama_chatri.productivityx.core.util;

import com.oussama_chatri.productivityx.core.exception.AppException;
import com.oussama_chatri.productivityx.core.exception.ErrorCode;
import com.oussama_chatri.productivityx.core.security.JwtService;
import com.oussama_chatri.productivityx.core.user.User;
import com.oussama_chatri.productivityx.core.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    /**
     * Returns the authenticated user's UUID without hitting the database.
     * The UUID is embedded in the JWT subject and stored in the Spring Security
     * principal name by JwtAuthFilter.
     */
    public UUID currentUserId() {
        Authentication auth = requireAuthentication();
        // Principal name is set to the userId string by JwtAuthFilter
        String principalName = auth.getName();
        try {
            return UUID.fromString(principalName);
        } catch (IllegalArgumentException ex) {
            // Fallback: principal is email (pre-existing sessions), resolve via UserDetails
            if (auth.getPrincipal() instanceof UserDetails ud) {
                return userRepository.findByEmail(ud.getUsername())
                        .map(User::getId)
                        .orElseThrow(() -> AppException.unauthorized(ErrorCode.AUTH_TOKEN_INVALID));
            }
            throw AppException.unauthorized(ErrorCode.AUTH_TOKEN_INVALID);
        }
    }

    /**
     * Fetches the full User entity. Use this only when you need fields beyond the ID.
     * Prefer {@link #currentUserId()} for authorization checks.
     */
    public User currentUser() {
        UUID id = currentUserId();
        return userRepository.findById(id)
                .orElseThrow(() -> AppException.unauthorized(ErrorCode.AUTH_TOKEN_INVALID));
    }

    public void assertOwnership(UUID ownerId) {
        if (!currentUserId().equals(ownerId)) {
            throw AppException.forbidden();
        }
    }

    private Authentication requireAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw AppException.unauthorized(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return auth;
    }
}
package com.oussama_chatri.productivityx.features.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthResponse {

    private final String accessToken;
    private final String tokenType;
    private final long expiresIn;

    public static AuthResponse of(String accessToken, long expiresInMs) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(expiresInMs / 1000)
                .build();
    }
}

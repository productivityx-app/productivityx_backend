package com.oussama_chatri.productivityx.features.profile.dto.response;

import com.oussama_chatri.productivityx.core.enums.Language;
import com.oussama_chatri.productivityx.core.enums.Theme;
import com.oussama_chatri.productivityx.features.profile.entity.Profile;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class ProfileResponse {

    private final UUID id;
    private final UUID userId;
    private final String firstName;
    private final String lastName;
    private final String fullName;
    private final String avatarUrl;
    private final String bio;
    private final String timezone;
    private final Language language;
    private final Theme theme;
    private final Instant createdAt;
    private final Instant updatedAt;

    public static ProfileResponse from(Profile profile) {
        return ProfileResponse.builder()
                .id(profile.getId())
                .userId(profile.getUserId())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .fullName(profile.getFirstName() + " " + profile.getLastName())
                .avatarUrl(profile.getAvatarUrl())
                .bio(profile.getBio())
                .timezone(profile.getTimezone())
                .language(profile.getLanguage())
                .theme(profile.getTheme())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
}

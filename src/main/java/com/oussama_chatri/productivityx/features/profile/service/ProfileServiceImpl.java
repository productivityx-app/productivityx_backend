package com.oussama_chatri.productivityx.features.profile.service;

import com.oussama_chatri.productivityx.core.exception.AppException;
import com.oussama_chatri.productivityx.core.exception.ErrorCode;
import com.oussama_chatri.productivityx.core.util.SecurityUtils;
import com.oussama_chatri.productivityx.features.profile.dto.request.UpdateAvatarRequest;
import com.oussama_chatri.productivityx.features.profile.dto.request.UpdateProfileRequest;
import com.oussama_chatri.productivityx.features.profile.dto.response.ProfileResponse;
import com.oussama_chatri.productivityx.features.profile.entity.Profile;
import com.oussama_chatri.productivityx.features.profile.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final ProfileRepository profileRepository;
    private final SecurityUtils securityUtils;

    @Override
    @Transactional(readOnly = true)
    public ProfileResponse getProfile() {
        UUID userId = securityUtils.currentUserId();
        return ProfileResponse.from(findProfile(userId));
    }

    @Override
    @Transactional
    public ProfileResponse updateProfile(UpdateProfileRequest request) {
        UUID userId = securityUtils.currentUserId();
        Profile profile = findProfile(userId);

        if (request.getFirstName() != null) profile.setFirstName(request.getFirstName().trim());
        if (request.getLastName() != null)  profile.setLastName(request.getLastName().trim());
        if (request.getBio() != null)       profile.setBio(request.getBio().trim());
        if (request.getTimezone() != null)  profile.setTimezone(request.getTimezone());
        if (request.getLanguage() != null)  profile.setLanguage(request.getLanguage());
        if (request.getTheme() != null)     profile.setTheme(request.getTheme());

        return ProfileResponse.from(profileRepository.save(profile));
    }

    @Override
    @Transactional
    public ProfileResponse updateAvatar(UpdateAvatarRequest request) {
        UUID userId = securityUtils.currentUserId();
        Profile profile = findProfile(userId);
        profile.setAvatarUrl(request.getAvatarUrl());
        return ProfileResponse.from(profileRepository.save(profile));
    }

    private Profile findProfile(UUID userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.RES_USER_NOT_FOUND));
    }
}

package com.oussama_chatri.productivityx.features.profile.service;

import com.oussama_chatri.productivityx.features.profile.dto.request.UpdateAvatarRequest;
import com.oussama_chatri.productivityx.features.profile.dto.request.UpdateProfileRequest;
import com.oussama_chatri.productivityx.features.profile.dto.response.ProfileResponse;

public interface ProfileService {

    ProfileResponse getProfile();

    ProfileResponse updateProfile(UpdateProfileRequest request);

    ProfileResponse updateAvatar(UpdateAvatarRequest request);
}

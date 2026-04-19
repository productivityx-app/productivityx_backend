package com.oussama_chatri.productivityx.features.preferences.service;

import com.oussama_chatri.productivityx.features.preferences.dto.request.UpdatePreferencesRequest;
import com.oussama_chatri.productivityx.features.preferences.dto.response.UserPreferencesResponse;

public interface PreferencesService {

    UserPreferencesResponse getPreferences();

    UserPreferencesResponse updatePreferences(UpdatePreferencesRequest request);
}

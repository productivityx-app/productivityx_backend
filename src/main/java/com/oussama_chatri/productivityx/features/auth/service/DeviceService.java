package com.oussama_chatri.productivityx.features.auth.service;

import com.oussama_chatri.productivityx.features.auth.dto.request.UpdatePushTokenRequest;
import com.oussama_chatri.productivityx.features.auth.dto.response.DeviceResponse;

import java.util.List;
import java.util.UUID;

public interface DeviceService {

    List<DeviceResponse> listDevices();

    void revokeDevice(String deviceId);

    void updatePushToken(String deviceId, UpdatePushTokenRequest request);
}
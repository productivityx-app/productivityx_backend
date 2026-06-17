package com.oussama_chatri.productivityx.features.auth.service;

import com.oussama_chatri.productivityx.core.exception.AppException;
import com.oussama_chatri.productivityx.core.exception.ErrorCode;
import com.oussama_chatri.productivityx.core.util.SecurityUtils;
import com.oussama_chatri.productivityx.features.auth.dto.request.UpdatePushTokenRequest;
import com.oussama_chatri.productivityx.features.auth.dto.response.DeviceResponse;
import com.oussama_chatri.productivityx.features.auth.repository.RefreshTokenRepository;
import com.oussama_chatri.productivityx.features.auth.repository.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceServiceImpl implements DeviceService {

    private final UserDeviceRepository userDeviceRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecurityUtils securityUtils;

    @Override
    @Transactional(readOnly = true)
    public List<DeviceResponse> listDevices() {
        UUID userId = securityUtils.currentUserId();
        return userDeviceRepository.findByUserIdOrderByLastSeenAtDesc(userId).stream()
                .map(DeviceResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void revokeDevice(String deviceId) {
        UUID userId = securityUtils.currentUserId();

        // Revoke all refresh tokens for this device before removing the record
        refreshTokenRepository.revokeAllForDevice(deviceId, Instant.now());
        userDeviceRepository.deleteByUserIdAndDeviceId(userId, deviceId);

        log.info("Device revoked userId={} deviceId={}", userId, deviceId);
    }

    @Override
    @Transactional
    public void updatePushToken(String deviceId, UpdatePushTokenRequest request) {
        UUID userId = securityUtils.currentUserId();
        userDeviceRepository.updatePushToken(userId, deviceId, request.getPushToken());
        log.debug("Push token updated userId={} deviceId={}", userId, deviceId);
    }
}
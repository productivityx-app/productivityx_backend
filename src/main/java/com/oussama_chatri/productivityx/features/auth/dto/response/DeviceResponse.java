package com.oussama_chatri.productivityx.features.auth.dto.response;

import com.oussama_chatri.productivityx.core.enums.Platform;
import com.oussama_chatri.productivityx.features.auth.entity.UserDevice;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class DeviceResponse {

    private final UUID id;
    private final String deviceId;
    private final String deviceName;
    private final Platform platform;
    private final Instant lastSeenAt;
    private final Instant createdAt;

    public static DeviceResponse from(UserDevice device) {
        return DeviceResponse.builder()
                .id(device.getId())
                .deviceId(device.getDeviceId())
                .deviceName(device.getDeviceName())
                .platform(device.getPlatform())
                .lastSeenAt(device.getLastSeenAt())
                .createdAt(device.getCreatedAt())
                .build();
    }
}
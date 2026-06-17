package com.oussama_chatri.productivityx.features.auth.controller;

import com.oussama_chatri.productivityx.core.dto.ApiResponse;
import com.oussama_chatri.productivityx.features.auth.dto.request.UpdatePushTokenRequest;
import com.oussama_chatri.productivityx.features.auth.dto.response.DeviceResponse;
import com.oussama_chatri.productivityx.features.auth.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@Tag(name = "Devices", description = "Multi-device session management — list and revoke active devices")
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping
    @Operation(summary = "List all active devices for the current user")
    public ResponseEntity<ApiResponse<List<DeviceResponse>>> listDevices() {
        return ResponseEntity.ok(ApiResponse.ok(deviceService.listDevices()));
    }

    @DeleteMapping("/{deviceId}")
    @Operation(summary = "Revoke a device — invalidates all refresh tokens for that device")
    public ResponseEntity<ApiResponse<Void>> revokeDevice(@PathVariable String deviceId) {
        deviceService.revokeDevice(deviceId);
        return ResponseEntity.ok(ApiResponse.message("Device revoked."));
    }

    @PatchMapping("/{deviceId}/push-token")
    @Operation(summary = "Update FCM push token for a device")
    public ResponseEntity<ApiResponse<Void>> updatePushToken(
            @PathVariable String deviceId,
            @Valid @RequestBody UpdatePushTokenRequest request) {
        deviceService.updatePushToken(deviceId, request);
        return ResponseEntity.ok(ApiResponse.message("Push token updated."));
    }
}
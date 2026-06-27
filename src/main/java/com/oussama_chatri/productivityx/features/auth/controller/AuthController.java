package com.oussama_chatri.productivityx.features.auth.controller;

import com.oussama_chatri.productivityx.core.dto.ApiResponse;
import com.oussama_chatri.productivityx.core.exception.AppException;
import com.oussama_chatri.productivityx.features.auth.dto.request.*;
import com.oussama_chatri.productivityx.features.auth.dto.response.AuthResponse;
import com.oussama_chatri.productivityx.features.auth.dto.response.ForgotPasswordOtpVerifiedResponse;
import com.oussama_chatri.productivityx.features.auth.dto.response.UserResponse;
import com.oussama_chatri.productivityx.features.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Arrays;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth")
public class AuthController {

    private final AuthService authService;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @PostMapping("/register")
    @Operation(summary = "Register a new account — sends verification email with OTP + magic link")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Registration successful"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error — missing or invalid fields"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email or username already exists")
    })
    public ResponseEntity<ApiResponse<Void>> register(
            @Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(201)
                .body(ApiResponse.message(
                        "Check your email to verify your account."));
    }

    @GetMapping("/verify-email")
    @Operation(summary = "Verify email via magic link (GET — for browser clicks from email). Redirects to frontend.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "302", description = "Redirects to frontend with ?status=verified or ?error=ERROR_CODE"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Token missing"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Token invalid or expired")
    })
    public void verifyEmailGet(
            @RequestParam String token,
            HttpServletResponse response) throws IOException {

        try {
            authService.verifyEmail(token, response);
            response.sendRedirect(frontendUrl + "/verify-email?status=verified");
        } catch (AppException e) {
            response.sendRedirect(frontendUrl + "/verify-email?error=" + e.getErrorCode().getCode());
        }
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email via magic link — supports POST body and ?token= query param")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Email verified, tokens returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Token missing"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Token invalid or expired")
    })
    public ResponseEntity<ApiResponse<AuthResponse>> verifyEmail(
            @Valid @RequestBody(required = false) VerifyEmailRequest body,
            @RequestParam(required = false) String token,
            HttpServletResponse response) {

        String resolved = resolveToken(token, body != null ? body.getToken() : null);
        if (resolved == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(
                            "VAL_005", "Token is required."));
        }

        AuthResponse authResponse = authService.verifyEmail(resolved, response);
        return ResponseEntity.ok(ApiResponse.ok(
                authResponse, "Email verified successfully."));
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Verify email via 6-digit OTP — brute-force protected per user")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OTP verified, tokens returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid email format or OTP format"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "OTP incorrect or expired")
    })
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request,
            HttpServletResponse response) {
        return ResponseEntity.ok(ApiResponse.ok(
                authService.verifyOtp(request, response), "Email verified successfully."));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend verification email — rate-limited to 3 per 10 minutes per user")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Verification email sent if account exists"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid email format"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request);
        return ResponseEntity.ok(ApiResponse.message(
                "If that email exists, a new verification link has been sent."));
    }

    @PostMapping("/login")
    @Operation(summary = "Login — returns accessToken in body, sets refreshToken as HttpOnly cookie. " +
            "Pass deviceId, deviceName, platform for multi-device tracking.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials or unverified email"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "423", description = "Account locked after failed attempts")
    })
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        return ResponseEntity.ok(ApiResponse.ok(
                authService.login(request, httpRequest, response), "Login successful."));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token — reads from HttpOnly cookie, returns new accessToken. " +
            "Pass X-Device-Id header for device-bound refresh verification.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token rotated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Refresh token missing, invalid, or revoked")
    })
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            HttpServletRequest request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            HttpServletResponse response) {
        String rawToken = extractRefreshCookie(request);
        return ResponseEntity.ok(ApiResponse.ok(
                authService.refresh(rawToken, deviceId, response), "Token refreshed."));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout — revokes current refresh token and clears the cookie")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logged out successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        authService.logout(extractRefreshCookie(request), response);
        return ResponseEntity.ok(ApiResponse.message("Logged out."));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset — sends email with OTP + reset link. Always returns 200.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Reset email sent if account exists"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid email format")
    })
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.message(
                "If that email exists, a reset code has been sent."));
    }

    @PostMapping("/verify-forgot-otp")
    @Operation(summary = "Verify the password-reset OTP — returns a short-lived resetToken for /reset-password")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OTP verified, resetToken returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid email format or OTP format"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "OTP incorrect or expired")
    })
    public ResponseEntity<ApiResponse<ForgotPasswordOtpVerifiedResponse>> verifyForgotPasswordOtp(
            @Valid @RequestBody VerifyForgotPasswordOtpRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                authService.verifyForgotPasswordOtp(request),
                "OTP verified. Use the resetToken to set your new password."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using the token from the reset email or verify-forgot-otp")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password reset successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Weak password or invalid token format"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Token invalid or expired")
    })
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.message(
                "Password reset. Please log in with your new password."));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password for the authenticated user — requires current password")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password changed successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Weak new password"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Current password incorrect or not authenticated")
    })
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.message(
                "Password changed."));
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current user's full profile, preferences, and account info")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User info returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<ApiResponse<UserResponse>> me() {
        return ResponseEntity.ok(ApiResponse.ok(authService.me()));
    }

    @DeleteMapping("/account")
    @Operation(summary = "Permanently delete the authenticated account — requires password confirmation")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Password missing or invalid"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Wrong password or not authenticated")
    })
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @Valid @RequestBody DeleteAccountRequest request) {
        authService.deleteAccount(request);
        return ResponseEntity.ok(ApiResponse.message(
                "Account deleted."));
    }

    private String extractRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> "refreshToken".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private String resolveToken(String queryParam, String bodyToken) {
        if (queryParam != null && !queryParam.isBlank()) return queryParam;
        if (bodyToken != null && !bodyToken.isBlank()) return bodyToken;
        return null;
    }
}
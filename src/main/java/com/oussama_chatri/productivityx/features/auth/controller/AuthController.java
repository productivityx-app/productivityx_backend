package com.oussama_chatri.productivityx.features.auth.controller;

import com.oussama_chatri.productivityx.core.dto.ApiResponse;
import com.oussama_chatri.productivityx.features.auth.dto.request.*;
import com.oussama_chatri.productivityx.features.auth.dto.response.AuthResponse;
import com.oussama_chatri.productivityx.features.auth.dto.response.UserResponse;
import com.oussama_chatri.productivityx.features.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Full auth lifecycle — register, verify, login, refresh, logout, password flows")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new account — sends a verification email on success")
    public ResponseEntity<ApiResponse<Void>> register(
            @Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok(ApiResponse.message("Check your email to verify your account."));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email via magic link — supports POST body and ?token= query param")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyEmail(
            @Valid @RequestBody(required = false) VerifyEmailRequest body,
            @RequestParam(required = false) String token,
            HttpServletResponse response) {

        String resolved = resolveToken(token, body != null ? body.getToken() : null);
        if (resolved == null) {
            // Return Void error properly — no generic type mismatch
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VAL_005", "Token is required."));
        }

        AuthResponse authResponse = authService.verifyEmail(resolved, response);
        return ResponseEntity.ok(ApiResponse.ok(authResponse, "Email verified successfully."));
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Verify email via 6-digit OTP — brute-force protected per user")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request,
            HttpServletResponse response) {
        return ResponseEntity.ok(ApiResponse.ok(
                authService.verifyOtp(request, response), "Email verified successfully."));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend verification email — rate-limited to 3 per 10 minutes per user")
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request);
        return ResponseEntity.ok(ApiResponse.message(
                "If that email exists, a new verification link has been sent."));
    }

    @PostMapping("/login")
    @Operation(summary = "Login — returns accessToken in body, sets refreshToken as HttpOnly cookie")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        return ResponseEntity.ok(ApiResponse.ok(
                authService.login(request, httpRequest, response), "Login successful."));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token — reads from HttpOnly cookie, returns new accessToken")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {
        String rawToken = extractRefreshCookie(request);
        return ResponseEntity.ok(ApiResponse.ok(
                authService.refresh(rawToken, response), "Token refreshed."));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout — revokes current refresh token and clears the cookie")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        authService.logout(extractRefreshCookie(request), response);
        return ResponseEntity.ok(ApiResponse.message("Logged out."));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset — always returns 200 to prevent user enumeration")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.message(
                "If that email exists, a reset link has been sent."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using the token from the reset email")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.message(
                "Password reset. Please log in with your new password."));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password for the authenticated user — requires current password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.message("Password changed."));
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current user's full profile, preferences, and account info")
    public ResponseEntity<ApiResponse<UserResponse>> me() {
        return ResponseEntity.ok(ApiResponse.ok(authService.me()));
    }

    @DeleteMapping("/account")
    @Operation(summary = "Permanently delete the authenticated account — requires password confirmation")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @Valid @RequestBody DeleteAccountRequest request) {
        authService.deleteAccount(request);
        return ResponseEntity.ok(ApiResponse.message("Account deleted."));
    }

    // Helpers

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
        if (bodyToken  != null && !bodyToken.isBlank())  return bodyToken;
        return null;
    }
}
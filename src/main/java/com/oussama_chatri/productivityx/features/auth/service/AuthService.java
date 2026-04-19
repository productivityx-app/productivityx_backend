package com.oussama_chatri.productivityx.features.auth.service;

import com.oussama_chatri.productivityx.features.auth.dto.request.*;
import com.oussama_chatri.productivityx.features.auth.dto.response.AuthResponse;
import com.oussama_chatri.productivityx.features.auth.dto.response.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {

    void register(RegisterRequest request);

    AuthResponse verifyEmail(String token, HttpServletResponse response);
    AuthResponse verifyOtp(VerifyOtpRequest request, HttpServletResponse response);
    void resendVerification(ResendVerificationRequest request);

    AuthResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response);

    AuthResponse refresh(String refreshToken, HttpServletResponse response);

    void logout(String refreshToken, HttpServletResponse response);

    void forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);

    void changePassword(ChangePasswordRequest request);

    void deleteAccount(DeleteAccountRequest request);

    UserResponse me();
}
package com.oussama_chatri.productivityx.features.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ForgotPasswordOtpVerifiedResponse {

    private final String resetToken;
}
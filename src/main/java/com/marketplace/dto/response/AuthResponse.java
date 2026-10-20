package com.marketplace.dto.response;


/**
 * {@code data} trả về sau register/login/refresh.
 *
 * @param expiresIn thời hạn access token, đơn vị giây
 */
public record AuthResponse(
    String accessToken, String refreshToken, long expiresIn, UserSummaryResponse user) {}

package com.enkryptify.internal.model;

public record TokenExchangeResponse(
        String accessToken,
        int expiresIn,
        String tokenType
) {
}

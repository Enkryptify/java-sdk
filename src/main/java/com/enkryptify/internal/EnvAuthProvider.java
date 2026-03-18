package com.enkryptify.internal;

import com.enkryptify.EnkryptifyAuthProvider;
import com.enkryptify.exception.EnkryptifyException;

public class EnvAuthProvider implements EnkryptifyAuthProvider {

    public EnvAuthProvider() {
        String token = System.getenv("ENKRYPTIFY_TOKEN");
        if (token == null || token.isBlank()) {
            throw new EnkryptifyException(
                    "ENKRYPTIFY_TOKEN environment variable is not set. Set it before initializing the SDK:\n" +
                    "  export ENKRYPTIFY_TOKEN=\"ek_...\"\n" +
                    "Docs: https://docs.enkryptify.com/sdk/auth#environment-variables");
        }
        TokenStore.store(this, token);
    }
}

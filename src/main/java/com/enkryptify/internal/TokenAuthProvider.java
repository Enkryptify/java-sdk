package com.enkryptify.internal;

import com.enkryptify.EnkryptifyAuthProvider;

public class TokenAuthProvider implements EnkryptifyAuthProvider {

    public TokenAuthProvider(String token) {
        TokenStore.store(this, token);
    }
}

package com.enkryptify.internal;

import com.enkryptify.EnkryptifyAuthProvider;
import com.enkryptify.exception.EnkryptifyException;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class TokenStore {

    private static final Map<Object, String> store =
            Collections.synchronizedMap(new WeakHashMap<>());

    private TokenStore() {
    }

    public static void store(EnkryptifyAuthProvider provider, String token) {
        store.put(provider, token);
    }

    public static String retrieve(EnkryptifyAuthProvider provider) {
        String token = store.get(provider);
        if (token == null) {
            throw new EnkryptifyException(
                    "Invalid or destroyed auth provider. Create a new one via Enkryptify.fromEnv() or Enkryptify.fromKubernetes().\n" +
                    "Docs: https://docs.enkryptify.com/sdk/auth");
        }
        return token;
    }
}

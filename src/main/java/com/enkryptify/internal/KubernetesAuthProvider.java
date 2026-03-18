package com.enkryptify.internal;

import com.enkryptify.EnkryptifyAuthProvider;
import com.enkryptify.exception.KubernetesAuthException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class KubernetesAuthProvider implements EnkryptifyAuthProvider {

    private static final String DEFAULT_TOKEN_PATH = "/var/run/secrets/tokens/token";
    private static final String ENV_TOKEN_PATH = "ENKRYPTIFY_TOKEN_PATH";

    private final String tokenPath;

    public KubernetesAuthProvider(String tokenPath) {
        String envPath = System.getenv(ENV_TOKEN_PATH);
        if (envPath != null && !envPath.isBlank()) {
            this.tokenPath = envPath;
        } else if (tokenPath != null && !tokenPath.isBlank()) {
            this.tokenPath = tokenPath;
        } else {
            this.tokenPath = DEFAULT_TOKEN_PATH;
        }
    }

    public String readToken() {
        try {
            String token = Files.readString(Path.of(tokenPath)).trim();
            if (token.isEmpty()) {
                throw new KubernetesAuthException(
                        "Kubernetes service account token file is empty: " + tokenPath);
            }
            return token;
        } catch (IOException e) {
            throw new KubernetesAuthException(
                    "Failed to read Kubernetes service account token from " + tokenPath, e);
        }
    }
}

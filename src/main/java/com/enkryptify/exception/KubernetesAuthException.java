package com.enkryptify.exception;

public class KubernetesAuthException extends EnkryptifyException {

    private static final String DOCS_URL = "https://docs.enkryptify.com/sdk/auth#kubernetes";

    public KubernetesAuthException(String message) {
        super(message + "\nDocs: " + DOCS_URL);
    }

    public KubernetesAuthException(String message, Throwable cause) {
        super(message + "\nDocs: " + DOCS_URL, cause);
    }
}

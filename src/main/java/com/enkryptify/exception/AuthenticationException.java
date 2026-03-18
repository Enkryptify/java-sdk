package com.enkryptify.exception;

public class AuthenticationException extends EnkryptifyException {

    public AuthenticationException() {
        super("Authentication failed (HTTP 401). Token is invalid, expired, or revoked. " +
              "Generate a new token in your Enkryptify dashboard.\n" +
              "Docs: https://docs.enkryptify.com/sdk/auth#token-issues");
    }
}

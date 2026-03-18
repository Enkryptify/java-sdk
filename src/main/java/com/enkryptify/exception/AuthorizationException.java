package com.enkryptify.exception;

public class AuthorizationException extends EnkryptifyException {

    public AuthorizationException() {
        super("Authorization failed (HTTP 403). Token does not have access to this resource. " +
              "Check that your token has the required permissions.\n" +
              "Docs: https://docs.enkryptify.com/sdk/auth#permissions");
    }
}

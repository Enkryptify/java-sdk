package com.enkryptify.exception;

public class EnkryptifyException extends RuntimeException {

    public EnkryptifyException(String message) {
        super(message);
    }

    public EnkryptifyException(String message, Throwable cause) {
        super(message, cause);
    }
}

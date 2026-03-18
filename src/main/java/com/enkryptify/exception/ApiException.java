package com.enkryptify.exception;

public class ApiException extends EnkryptifyException {

    private final int status;

    public ApiException(int status, String statusText, String method, String endpoint) {
        super("API request failed (HTTP " + status + ") for " + method + " " + endpoint + ". " +
              (statusText != null && !statusText.isEmpty() ? statusText + ". " : "") +
              "This may be a temporary server issue — retry in a few moments.\n" +
              "Docs: https://docs.enkryptify.com/sdk/troubleshooting#api-errors");
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}

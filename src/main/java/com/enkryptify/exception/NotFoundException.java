package com.enkryptify.exception;

public class NotFoundException extends EnkryptifyException {

    private final String method;
    private final String endpoint;

    public NotFoundException(String method, String endpoint) {
        super("Resource not found (HTTP 404) for " + method + " " + endpoint + ". " +
              "Workspace, project, or environment not found. Verify your configuration.\n" +
              "Docs: https://docs.enkryptify.com/sdk/troubleshooting#not-found");
        this.method = method;
        this.endpoint = endpoint;
    }

    public String getMethod() {
        return method;
    }

    public String getEndpoint() {
        return endpoint;
    }
}

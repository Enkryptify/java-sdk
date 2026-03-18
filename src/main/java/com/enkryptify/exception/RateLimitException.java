package com.enkryptify.exception;

public class RateLimitException extends EnkryptifyException {

    private final Integer retryAfter;

    public RateLimitException(String retryAfterHeader) {
        super(buildMessage(retryAfterHeader));
        this.retryAfter = parseRetryAfter(retryAfterHeader);
    }

    public Integer getRetryAfter() {
        return retryAfter;
    }

    private static Integer parseRetryAfter(String retryAfterHeader) {
        if (retryAfterHeader == null || retryAfterHeader.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(retryAfterHeader.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String buildMessage(String retryAfterHeader) {
        Integer retrySeconds = parseRetryAfter(retryAfterHeader);
        String retryPart = retrySeconds != null
                ? "Retry after " + retrySeconds + " seconds."
                : "Please retry later.";
        return "Rate limited (HTTP 429). " + retryPart + "\n" +
               "Docs: https://docs.enkryptify.com/sdk/troubleshooting#rate-limiting";
    }
}

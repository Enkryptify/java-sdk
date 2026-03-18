package com.enkryptify;

public record EnkryptifyConfig(
        String workspace,
        String project,
        String environment,
        EnkryptifyAuthProvider auth,
        String token,
        String baseUrl,
        boolean useTokenExchange,
        boolean strict,
        boolean usePersonalValues,
        boolean cacheEnabled,
        long cacheTtl,
        boolean cacheEager,
        LogLevel logLevel
) {

    public enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }

    public static Builder builder(String workspace, String project, String environment) {
        return new Builder(workspace, project, environment);
    }

    public static final class Builder {
        private final String workspace;
        private final String project;
        private final String environment;
        private EnkryptifyAuthProvider auth;
        private String token;
        private String baseUrl = "https://api.enkryptify.com";
        private boolean useTokenExchange = false;
        private boolean strict = true;
        private boolean usePersonalValues = true;
        private boolean cacheEnabled = true;
        private long cacheTtl = -1;
        private boolean cacheEager = true;
        private LogLevel logLevel = LogLevel.INFO;

        private Builder(String workspace, String project, String environment) {
            this.workspace = workspace;
            this.project = project;
            this.environment = environment;
        }

        public Builder auth(EnkryptifyAuthProvider auth) {
            this.auth = auth;
            return this;
        }

        public Builder token(String token) {
            this.token = token;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder useTokenExchange(boolean useTokenExchange) {
            this.useTokenExchange = useTokenExchange;
            return this;
        }

        public Builder strict(boolean strict) {
            this.strict = strict;
            return this;
        }

        public Builder usePersonalValues(boolean usePersonalValues) {
            this.usePersonalValues = usePersonalValues;
            return this;
        }

        public Builder cacheEnabled(boolean cacheEnabled) {
            this.cacheEnabled = cacheEnabled;
            return this;
        }

        public Builder cacheTtl(long cacheTtl) {
            this.cacheTtl = cacheTtl;
            return this;
        }

        public Builder cacheEager(boolean cacheEager) {
            this.cacheEager = cacheEager;
            return this;
        }

        public Builder logLevel(LogLevel logLevel) {
            this.logLevel = logLevel;
            return this;
        }

        public EnkryptifyConfig build() {
            return new EnkryptifyConfig(
                    workspace, project, environment,
                    auth, token, baseUrl, useTokenExchange,
                    strict, usePersonalValues,
                    cacheEnabled, cacheTtl, cacheEager,
                    logLevel
            );
        }
    }
}

package com.enkryptify;

import com.enkryptify.exception.EnkryptifyException;
import com.enkryptify.exception.NotFoundException;
import com.enkryptify.exception.SecretNotFoundException;
import com.enkryptify.internal.*;
import com.enkryptify.internal.model.Secret;
import com.enkryptify.internal.model.SecretValue;

import java.util.List;
import java.util.Optional;

public class Enkryptify implements AutoCloseable {

    private final String workspace;
    private final String project;
    private final String environment;
    private final boolean strict;
    private final boolean usePersonalValues;
    private final boolean cacheEnabled;
    private final boolean cacheEager;

    private final Logger logger;
    private final SecretCache cache;
    private final EnkryptifyApi api;
    private final TokenExchangeManager tokenExchange;
    private final KubernetesExchangeManager k8sExchange;
    private final EnkryptifyAuthProvider authProvider;

    private boolean eagerLoaded;
    private boolean destroyed;

    public Enkryptify(EnkryptifyConfig config) {
        if (config.workspace() == null || config.workspace().isBlank()) {
            throw new EnkryptifyException("workspace is required");
        }
        if (config.project() == null || config.project().isBlank()) {
            throw new EnkryptifyException("project is required");
        }
        if (config.environment() == null || config.environment().isBlank()) {
            throw new EnkryptifyException("environment is required");
        }

        this.workspace = config.workspace();
        this.project = config.project();
        this.environment = config.environment();
        this.strict = config.strict();
        this.usePersonalValues = config.usePersonalValues();
        this.cacheEnabled = config.cacheEnabled();
        this.cacheEager = config.cacheEager();

        this.logger = new Logger(config.logLevel());

        // Resolve auth provider: token option → auth option → env fallback
        if (config.token() != null && !config.token().isBlank()) {
            this.authProvider = new TokenAuthProvider(config.token());
        } else if (config.auth() != null) {
            this.authProvider = config.auth();
        } else {
            this.authProvider = new EnvAuthProvider();
        }

        // Validate token format (skip for Kubernetes auth — token is deferred)
        boolean isKubernetesAuth = authProvider instanceof KubernetesAuthProvider;
        if (!isKubernetesAuth) {
            String token = TokenStore.retrieve(authProvider);
            validateTokenFormat(token);
        }

        // Initialize cache
        this.cache = cacheEnabled ? new SecretCache(config.cacheTtl()) : null;

        // Initialize API client
        this.api = new EnkryptifyApi(config.baseUrl(), authProvider);

        // Initialize token exchange
        if (isKubernetesAuth) {
            this.tokenExchange = null;
            this.k8sExchange = new KubernetesExchangeManager(
                    workspace, config.baseUrl(), (KubernetesAuthProvider) authProvider, logger);
        } else if (config.useTokenExchange()) {
            this.k8sExchange = null;
            this.tokenExchange = new TokenExchangeManager(
                    TokenStore.retrieve(authProvider), config.baseUrl(), authProvider, logger);
        } else {
            this.tokenExchange = null;
            this.k8sExchange = null;
        }

        logger.info("Initialized (workspace=" + workspace + ", project=" + project + ", environment=" + environment + ")");
    }

    public static EnkryptifyAuthProvider fromEnv() {
        return new EnvAuthProvider();
    }

    public static EnkryptifyAuthProvider fromKubernetes() {
        return new KubernetesAuthProvider(null);
    }

    public static EnkryptifyAuthProvider fromKubernetes(String tokenPath) {
        return new KubernetesAuthProvider(tokenPath);
    }

    public String get(String key) {
        return get(key, true);
    }

    public String get(String key, boolean useCache) {
        guardDestroyed();

        // Check cache first
        if (cacheEnabled && useCache && cache != null) {
            Optional<String> cached = cache.get(key);
            if (cached.isPresent()) {
                logger.debug("Cache hit for \"" + key + "\"");
                return cached.get();
            }
        }

        // Ensure token is exchanged
        if (k8sExchange != null) {
            k8sExchange.ensureToken();
        } else if (tokenExchange != null) {
            tokenExchange.ensureToken();
        }

        // Eager load or single fetch
        if (cacheEnabled && cacheEager && !eagerLoaded) {
            return fetchAndCacheAll(key);
        } else {
            return fetchAndCacheSingle(key);
        }
    }

    public String getFromCache(String key) {
        guardDestroyed();

        if (!cacheEnabled || cache == null) {
            throw new EnkryptifyException("Cache is disabled. Enable it in the config to use getFromCache().");
        }

        Optional<String> cached = cache.get(key);
        if (cached.isPresent()) {
            return cached.get();
        }

        throw new SecretNotFoundException(key, workspace, environment);
    }

    public void preload() {
        guardDestroyed();

        if (!cacheEnabled || cache == null) {
            throw new EnkryptifyException("Cache is disabled. Enable it in the config to use preload().");
        }

        if (k8sExchange != null) {
            k8sExchange.ensureToken();
        } else if (tokenExchange != null) {
            tokenExchange.ensureToken();
        }

        List<Secret> secrets = api.fetchAllSecrets(workspace, project, environment);
        int count = 0;
        for (Secret secret : secrets) {
            String value = resolveValue(secret);
            if (value != null) {
                cache.set(secret.name(), value);
                count++;
            }
        }
        eagerLoaded = true;
        logger.info("Preloaded " + count + " secrets");
    }

    public void destroy() {
        if (k8sExchange != null) {
            k8sExchange.destroy();
        }
        if (tokenExchange != null) {
            tokenExchange.destroy();
        }
        if (cache != null) {
            cache.clear();
        }
        destroyed = true;
        logger.info("Destroyed");
    }

    @Override
    public void close() {
        destroy();
    }

    private String fetchAndCacheAll(String key) {
        List<Secret> secrets = api.fetchAllSecrets(workspace, project, environment);

        String found = null;
        for (Secret secret : secrets) {
            String value = resolveValue(secret);
            if (value != null && cache != null) {
                cache.set(secret.name(), value);
            }
            if (secret.name().equals(key)) {
                found = value;
            }
        }
        eagerLoaded = true;

        if (found != null) {
            return found;
        }
        return handleNotFound(key);
    }

    private String fetchAndCacheSingle(String key) {
        Secret secret;
        try {
            secret = api.fetchSecret(workspace, project, key, environment);
        } catch (NotFoundException e) {
            return handleNotFound(key);
        }

        String value = resolveValue(secret);
        if (value == null) {
            return handleNotFound(key);
        }

        if (cacheEnabled && cache != null) {
            cache.set(key, value);
        }
        return value;
    }

    private String handleNotFound(String key) {
        if (strict) {
            throw new SecretNotFoundException(key, workspace, environment);
        }
        logger.warn("Secret \"" + key + "\" not found, returning empty string (strict mode disabled)");
        return "";
    }

    private String resolveValue(Secret secret) {
        if (secret.values() == null) {
            return null;
        }

        List<SecretValue> envValues = secret.values().stream()
                .filter(v -> environment.equals(v.environmentId()))
                .toList();

        if (usePersonalValues) {
            // Prefer personal value
            Optional<SecretValue> personal = envValues.stream()
                    .filter(SecretValue::isPersonal)
                    .findFirst();
            if (personal.isPresent()) {
                return personal.get().value();
            }
            // Fall back to shared value
            Optional<SecretValue> shared = envValues.stream()
                    .filter(v -> !v.isPersonal())
                    .findFirst();
            if (shared.isPresent()) {
                return shared.get().value();
            }
        } else {
            // Only use shared values
            Optional<SecretValue> shared = envValues.stream()
                    .filter(v -> !v.isPersonal())
                    .findFirst();
            if (shared.isPresent()) {
                return shared.get().value();
            }
        }

        return null;
    }

    private void guardDestroyed() {
        if (destroyed) {
            throw new EnkryptifyException(
                    "Client has been destroyed. Create a new instance to continue.\n" +
                    "Docs: https://docs.enkryptify.com/sdk/lifecycle");
        }
    }

    private static void validateTokenFormat(String token) {
        if (token == null || token.isBlank()) {
            throw new EnkryptifyException(
                    "Token is empty or missing. Provide a token via config or ENKRYPTIFY_TOKEN env var.\n" +
                    "Docs: https://docs.enkryptify.com/sdk/auth");
        }
        if (token.startsWith("ek_live_")) {
            return;
        }
        // Check if it looks like a JWT (3 dot-separated segments)
        long dotCount = token.chars().filter(c -> c == '.').count();
        if (dotCount == 2) {
            return;
        }
        throw new EnkryptifyException(
                "Invalid token format. Expected an Enkryptify token (ek_live_...) or JWT.\n" +
                "Docs: https://docs.enkryptify.com/sdk/auth");
    }
}

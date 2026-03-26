package com.enkryptify.internal;

import com.enkryptify.exception.KubernetesAuthException;
import com.enkryptify.internal.model.TokenExchangeResponse;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.*;

public class KubernetesExchangeManager {

    private static final Logger logger = LoggerFactory.getLogger(KubernetesExchangeManager.class);

    private final String workspaceId;
    private final String baseUrl;
    private final KubernetesAuthProvider auth;
    private final HttpClient httpClient;
    private final Gson gson;
    private final ScheduledExecutorService scheduler;

    private CompletableFuture<Void> exchangePromise;
    private ScheduledFuture<?> refreshTask;
    private volatile boolean destroyed;
    private volatile boolean needsExchange = true;

    public KubernetesExchangeManager(String workspaceId, String baseUrl,
                                     KubernetesAuthProvider auth) {
        this.workspaceId = workspaceId;
        this.baseUrl = baseUrl;
        this.auth = auth;
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "enkryptify-k8s-token-refresh");
            t.setDaemon(true);
            return t;
        });
    }

    public synchronized void ensureToken() {
        if (destroyed) return;

        if (!needsExchange) {
            if (exchangePromise != null) {
                try {
                    exchangePromise.join();
                } catch (CompletionException | CancellationException e) {
                    throw new KubernetesAuthException("Kubernetes OIDC token exchange failed", e.getCause());
                }
            }
            return;
        }

        exchangePromise = CompletableFuture.runAsync(this::exchange);
        try {
            exchangePromise.join();
        } catch (CompletionException | CancellationException e) {
            throw new KubernetesAuthException("Kubernetes OIDC token exchange failed", e.getCause());
        } finally {
            exchangePromise = null;
        }
    }

    private void exchange() {
        try {
            logger.debug("Exchanging Kubernetes OIDC token...");

            String saToken = auth.readToken();

            String body = gson.toJson(Map.of(
                    "token", saToken,
                    "workspaceId", workspaceId
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/auth/oidc/exchange"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new KubernetesAuthException(
                        "Kubernetes OIDC token exchange failed (HTTP " + response.statusCode() + ")");
            }

            TokenExchangeResponse exchangeResponse = gson.fromJson(response.body(), TokenExchangeResponse.class);
            TokenStore.store(auth, exchangeResponse.accessToken());
            needsExchange = false;

            logger.debug("Kubernetes OIDC token exchanged successfully, expires in " + exchangeResponse.expiresIn() + "s");

            scheduleRefresh(exchangeResponse.expiresIn());
        } catch (KubernetesAuthException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new KubernetesAuthException("Kubernetes OIDC token exchange failed: " + e.getMessage(), e);
        }
    }

    private void scheduleRefresh(int expiresInSeconds) {
        if (refreshTask != null) {
            refreshTask.cancel(false);
        }

        long delaySeconds = Math.max(expiresInSeconds - 60, 0);
        refreshTask = scheduler.schedule(() -> {
            if (!destroyed) {
                needsExchange = true;
                exchange();
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    public void destroy() {
        destroyed = true;
        if (refreshTask != null) {
            refreshTask.cancel(false);
        }
        scheduler.shutdownNow();
    }
}

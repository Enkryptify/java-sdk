package com.enkryptify.internal;

import com.enkryptify.EnkryptifyAuthProvider;
import com.enkryptify.internal.model.TokenExchangeResponse;
import com.google.gson.Gson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.*;

public class TokenExchangeManager {

    private final String staticToken;
    private final String baseUrl;
    private final EnkryptifyAuthProvider authProvider;
    private final Logger logger;
    private final HttpClient httpClient;
    private final Gson gson;
    private final ScheduledExecutorService scheduler;

    private CompletableFuture<Void> exchangePromise;
    private ScheduledFuture<?> refreshTask;
    private volatile boolean destroyed;

    public TokenExchangeManager(String staticToken, String baseUrl,
                                EnkryptifyAuthProvider authProvider, Logger logger) {
        this.staticToken = staticToken;
        this.baseUrl = baseUrl;
        this.authProvider = authProvider;
        this.logger = logger;
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "enkryptify-token-refresh");
            t.setDaemon(true);
            return t;
        });
    }

    public synchronized void ensureToken() {
        if (destroyed) return;

        if (exchangePromise != null) {
            try {
                exchangePromise.join();
            } catch (CompletionException | CancellationException e) {
                // exchange failed, will fall back to static token
            }
            return;
        }

        exchangePromise = CompletableFuture.runAsync(this::exchange);
        try {
            exchangePromise.join();
        } catch (CompletionException | CancellationException e) {
            // exchange failed, will fall back to static token
        } finally {
            exchangePromise = null;
        }
    }

    private void exchange() {
        try {
            logger.debug("Exchanging token...");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/auth/exchange"))
                    .header("Authorization", "Bearer " + staticToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logger.warn("Token exchange failed (HTTP " + response.statusCode() + "), falling back to static token");
                TokenStore.store(authProvider, staticToken);
                return;
            }

            TokenExchangeResponse exchangeResponse = gson.fromJson(response.body(), TokenExchangeResponse.class);
            TokenStore.store(authProvider, exchangeResponse.accessToken());

            logger.debug("Token exchanged successfully, expires in " + exchangeResponse.expiresIn() + "s");

            scheduleRefresh(exchangeResponse.expiresIn());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.warn("Token exchange failed: " + e.getMessage() + ", falling back to static token");
            TokenStore.store(authProvider, staticToken);
        }
    }

    private void scheduleRefresh(int expiresInSeconds) {
        if (refreshTask != null) {
            refreshTask.cancel(false);
        }

        long delaySeconds = Math.max(expiresInSeconds - 60, 0);
        refreshTask = scheduler.schedule(() -> {
            if (!destroyed) {
                TokenStore.store(authProvider, staticToken);
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

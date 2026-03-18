package com.enkryptify.internal;

import com.enkryptify.EnkryptifyAuthProvider;
import com.enkryptify.exception.*;
import com.enkryptify.internal.model.Secret;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class EnkryptifyApi {

    private final String baseUrl;
    private final EnkryptifyAuthProvider authProvider;
    private final HttpClient httpClient;
    private final Gson gson;

    public EnkryptifyApi(String baseUrl, EnkryptifyAuthProvider authProvider) {
        this.baseUrl = baseUrl;
        this.authProvider = authProvider;
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
    }

    public Secret fetchSecret(String workspace, String project, String secretName, String environmentId) {
        String path = "/v1/workspace/" + encode(workspace) +
                      "/project/" + encode(project) +
                      "/secret/" + encode(secretName) +
                      "?environmentId=" + encode(environmentId) + "&resolve=true";
        String body = request("GET", path);
        return gson.fromJson(body, Secret.class);
    }

    public List<Secret> fetchAllSecrets(String workspace, String project, String environmentId) {
        String path = "/v1/workspace/" + encode(workspace) +
                      "/project/" + encode(project) +
                      "/secret?environmentId=" + encode(environmentId) + "&resolve=true";
        String body = request("GET", path);
        return gson.fromJson(body, new TypeToken<List<Secret>>() {}.getType());
    }

    private String request(String method, String path) {
        String token = TokenStore.retrieve(authProvider);
        String url = baseUrl + path;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new EnkryptifyException("HTTP request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EnkryptifyException("HTTP request interrupted", e);
        }

        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return response.body();
        }

        switch (status) {
            case 401 -> throw new AuthenticationException();
            case 403 -> throw new AuthorizationException();
            case 404 -> throw new NotFoundException(method, path);
            case 429 -> throw new RateLimitException(
                    response.headers().firstValue("Retry-After").orElse(null));
            default -> throw new ApiException(status, "", method, path);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

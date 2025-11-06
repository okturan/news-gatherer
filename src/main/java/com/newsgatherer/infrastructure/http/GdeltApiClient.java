package com.newsgatherer.infrastructure.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsgatherer.config.GdeltApiConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * HTTP client for GDELT API communication.
 * Uses modern Java HttpClient with connection pooling and timeout management.
 */
public class GdeltApiClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final GdeltApiConfig config;

    public GdeltApiClient(GdeltApiConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(config.getTimeout())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    /**
     * Fetches articles from GDELT API.
     *
     * @param queryString the URL-encoded query string
     * @return JSON response as JsonNode
     * @throws GdeltApiException if the API request fails
     */
    public JsonNode fetchArticles(String queryString) throws GdeltApiException {
        try {
            URI uri = new URI(config.getApiEndpoint() + "?" + queryString);
            HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .header("Accept", "application/json")
                .build();

            HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );

            validateResponse(response);

            return objectMapper.readTree(response.body());

        } catch (Exception e) {
            throw new GdeltApiException("Failed to fetch articles from GDELT API", e);
        }
    }

    /**
     * Validates HTTP response status.
     */
    private void validateResponse(HttpResponse<String> response) throws GdeltApiException {
        if (response.statusCode() != 200) {
            throw new GdeltApiException(
                "GDELT API returned status code " + response.statusCode()
            );
        }
    }
}

package com.newsgatherer.infrastructure.text;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Canonicalizes URLs by removing tracking parameters and normalizing structure.
 */
public class UrlCanonicalizer {
    private static final Set<String> TRACKING_PARAMS = Set.of(
        "gclid", "fbclid", "xtor", "trk", "ref"
    );

    /**
     * Canonicalizes a URL by:
     * 1. Removing tracking query parameters (utm_*, gclid, fbclid, etc.)
     * 2. Stripping trailing slashes
     * 3. Removing fragments
     *
     * @param url the URL to canonicalize
     * @return canonicalized URL, or original URL if parsing fails
     */
    public String canonicalize(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }

        try {
            URI uri = new URI(url);
            Map<String, List<String>> filteredParams = filterQueryParameters(uri);
            String cleanQuery = buildQueryString(filteredParams);

            return buildCleanUri(uri, cleanQuery);
        } catch (Exception e) {
            // Return original URL if canonicalization fails
            return url;
        }
    }

    /**
     * Filters out tracking parameters from query string.
     */
    private Map<String, List<String>> filterQueryParameters(URI uri) {
        String query = uri.getQuery();
        if (query == null) {
            return Map.of();
        }

        Map<String, List<String>> filteredParams = new LinkedHashMap<>();

        for (String keyValuePair : query.split("&")) {
            String[] parts = keyValuePair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);

            if (shouldKeepParameter(key)) {
                String value = parts.length > 1 ? parts[1] : "";
                filteredParams.computeIfAbsent(key, k -> new ArrayList<>())
                              .add(value);
            }
        }

        return filteredParams;
    }

    /**
     * Determines if a query parameter should be kept.
     * Filters out utm_* parameters and known tracking parameters.
     */
    private boolean shouldKeepParameter(String key) {
        return !key.startsWith("utm_") && !TRACKING_PARAMS.contains(key);
    }

    /**
     * Builds a query string from filtered parameters.
     */
    private String buildQueryString(Map<String, List<String>> params) {
        if (params.isEmpty()) {
            return null;
        }

        return params.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
            .collect(Collectors.joining("&"));
    }

    /**
     * Builds a clean URI with filtered query parameters.
     */
    private String buildCleanUri(URI original, String cleanQuery) throws URISyntaxException {
        String path = stripTrailingSlash(original.getPath());

        URI cleanUri = new URI(
            original.getScheme(),
            original.getUserInfo(),
            original.getHost(),
            original.getPort(),
            path,
            cleanQuery,
            null  // Remove fragment
        );

        return cleanUri.toString();
    }

    /**
     * Removes trailing slash from path.
     */
    private String stripTrailingSlash(String path) {
        if (path != null && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }
}

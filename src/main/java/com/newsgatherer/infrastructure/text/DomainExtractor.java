package com.newsgatherer.infrastructure.text;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Extracts and normalizes domain names from URLs.
 */
public class DomainExtractor {

    /**
     * Extracts the domain from a URL, removing 'www.' prefix if present.
     *
     * @param url the URL to extract domain from
     * @return the normalized domain name, or empty string if extraction fails
     */
    public String extractDomain(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }

        try {
            URI uri = new URI(url);
            String host = uri.getHost();

            if (host == null) {
                return "";
            }

            String normalized = host.toLowerCase(Locale.ROOT);

            // Remove www. prefix
            if (normalized.startsWith("www.")) {
                normalized = normalized.substring(4);
            }

            return normalized;
        } catch (URISyntaxException e) {
            return "";
        }
    }
}

package com.newsgatherer.config;

import java.time.Duration;

/**
 * Configuration for GDELT API access.
 */
public class GdeltApiConfig {
    private final String apiEndpoint;
    private final Duration timeout;
    private final int maxRecords;
    private final Duration defaultTimespan;

    private GdeltApiConfig(Builder builder) {
        this.apiEndpoint = builder.apiEndpoint;
        this.timeout = builder.timeout;
        this.maxRecords = builder.maxRecords;
        this.defaultTimespan = builder.defaultTimespan;
    }

    public String getApiEndpoint() {
        return apiEndpoint;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public int getMaxRecords() {
        return maxRecords;
    }

    public Duration getDefaultTimespan() {
        return defaultTimespan;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String apiEndpoint = "https://api.gdeltproject.org/api/v2/doc/doc";
        private Duration timeout = Duration.ofSeconds(30);
        private int maxRecords = 200;
        private Duration defaultTimespan = Duration.ofHours(2);

        public Builder apiEndpoint(String apiEndpoint) {
            this.apiEndpoint = apiEndpoint;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxRecords(int maxRecords) {
            if (maxRecords <= 0 || maxRecords > 1000) {
                throw new IllegalArgumentException("maxRecords must be between 1 and 1000");
            }
            this.maxRecords = maxRecords;
            return this;
        }

        public Builder defaultTimespan(Duration timespan) {
            this.defaultTimespan = timespan;
            return this;
        }

        public GdeltApiConfig build() {
            if (apiEndpoint == null || apiEndpoint.isEmpty()) {
                throw new IllegalStateException("API endpoint cannot be null or empty");
            }
            return new GdeltApiConfig(this);
        }
    }
}

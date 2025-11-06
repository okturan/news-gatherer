package com.newsgatherer.config;

import java.time.Duration;

/**
 * Configuration for article clustering parameters.
 */
public class ClusteringConfig {
    private final Duration timeWindow;
    private final double similarityThreshold;
    private final int shingleSize;

    private ClusteringConfig(Builder builder) {
        this.timeWindow = builder.timeWindow;
        this.similarityThreshold = builder.similarityThreshold;
        this.shingleSize = builder.shingleSize;
    }

    public Duration getTimeWindow() {
        return timeWindow;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public int getShingleSize() {
        return shingleSize;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Duration timeWindow = Duration.ofHours(48);
        private double similarityThreshold = 0.80;
        private int shingleSize = 4;

        public Builder timeWindow(Duration timeWindow) {
            this.timeWindow = timeWindow;
            return this;
        }

        public Builder timeWindowHours(int hours) {
            if (hours <= 0 || hours > 168) {
                throw new IllegalArgumentException("Time window must be between 1 and 168 hours");
            }
            this.timeWindow = Duration.ofHours(hours);
            return this;
        }

        public Builder similarityThreshold(double threshold) {
            if (threshold < 0.0 || threshold > 1.0) {
                throw new IllegalArgumentException("Similarity threshold must be between 0.0 and 1.0");
            }
            this.similarityThreshold = threshold;
            return this;
        }

        public Builder shingleSize(int size) {
            if (size < 2 || size > 10) {
                throw new IllegalArgumentException("Shingle size must be between 2 and 10");
            }
            this.shingleSize = size;
            return this;
        }

        public ClusteringConfig build() {
            return new ClusteringConfig(this);
        }
    }
}

package com.newsgatherer.config;

import java.time.Duration;

/** Utility helpers for reading configuration values from the environment. */
public final class Env {

    private Env() {
        // utility class
    }

    public static String stringOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    public static Duration durationFromMillis(String key, Duration fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            long millis = Long.parseLong(value.trim());
            if (millis <= 0) {
                System.err.printf("[%s] must be positive; using default %d ms%n", key, fallback.toMillis());
                return fallback;
            }
            return Duration.ofMillis(millis);
        } catch (NumberFormatException ex) {
            System.err.printf("Invalid %s value '%s'; using default %d ms%n", key, value, fallback.toMillis());
            return fallback;
        }
    }

    public static Duration durationFromDays(String key, Duration fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            long days = Long.parseLong(value.trim());
            if (days <= 0) {
                System.err.printf("[%s] must be positive; using default %d days%n", key, fallback.toDays());
                return fallback;
            }
            return Duration.ofDays(days);
        } catch (NumberFormatException ex) {
            System.err.printf("Invalid %s value '%s'; using default %d days%n", key, value, fallback.toDays());
            return fallback;
        }
    }
}

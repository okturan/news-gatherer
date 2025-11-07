package com.newsgatherer;

/**
 * Type of news source with priority for canonical selection.
 */
public enum SourceType {
    WIRE(1),        // News agencies (highest priority)
    PUBLISHER(2),   // Direct publishers
    AGGREGATOR(3);  // News aggregators (lowest priority)

    private final int priority;

    SourceType(int priority) {
        this.priority = priority;
    }

    public int getPriority() {
        return priority;
    }

    /**
     * Determines source type from domain name.
     */
    public static SourceType fromDomain(String domain) {
        if (domain == null) {
            return PUBLISHER;
        }

        String lowerDomain = domain.toLowerCase();

        if (Config.WIRE_DOMAINS.contains(lowerDomain)) {
            return WIRE;
        }
        if (Config.AGGREGATOR_DOMAINS.contains(lowerDomain)) {
            return AGGREGATOR;
        }
        return PUBLISHER;
    }
}

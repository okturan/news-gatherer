package com.newsgatherer.domain.model;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;

/**
 * Represents the type of news source for articles.
 * Each source type has a priority for canonical selection and associated domains.
 *
 * Priority order: WIRE (1) > PUBLISHER (2) > AGGREGATOR (3)
 */
public enum SourceType {
    /**
     * Wire services - Highest priority for canonical selection.
     * Examples: aa.com.tr, iha.com.tr, dha.com.tr
     */
    WIRE(1, Set.of("aa.com.tr", "iha.com.tr", "dha.com.tr")),

    /**
     * News publishers - Medium priority.
     * Default type for domains not classified as WIRE or AGGREGATOR.
     */
    PUBLISHER(2, Set.of()),

    /**
     * News aggregators - Lowest priority.
     * Examples: ensonhaber.com, haberler.com, sondakika.com
     */
    AGGREGATOR(3, Set.of(
        "ensonhaber.com",
        "haberler.com",
        "sondakika.com",
        "gazeteoku.com"
    ));

    private final int priority;
    private final Set<String> domains;

    SourceType(int priority, Set<String> domains) {
        this.priority = priority;
        this.domains = domains;
    }

    /**
     * Returns the priority of this source type.
     * Lower numbers indicate higher priority.
     *
     * @return the priority value (1 = highest, 3 = lowest)
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Returns the set of domains associated with this source type.
     *
     * @return immutable set of domain names
     */
    public Set<String> getDomains() {
        return domains;
    }

    /**
     * Determines the source type from a domain name.
     *
     * @param domain the domain name (e.g., "aa.com.tr")
     * @return the corresponding SourceType, or PUBLISHER if not found
     */
    public static SourceType fromDomain(String domain) {
        if (domain == null || domain.isEmpty()) {
            return PUBLISHER;
        }

        return Arrays.stream(values())
            .filter(type -> type.domains.contains(domain))
            .findFirst()
            .orElse(PUBLISHER);
    }

    /**
     * Returns a comparator that sorts SourceTypes by priority.
     * Lower priority numbers come first (WIRE < PUBLISHER < AGGREGATOR).
     *
     * @return a priority-based comparator
     */
    public static Comparator<SourceType> byPriority() {
        return Comparator.comparingInt(SourceType::getPriority);
    }
}

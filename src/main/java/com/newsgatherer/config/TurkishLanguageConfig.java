package com.newsgatherer.config;

import java.util.Locale;
import java.util.Set;

/**
 * Configuration for Turkish language processing.
 * Contains stop words and locale settings specific to Turkish text normalization.
 */
public class TurkishLanguageConfig {
    private final Locale locale;
    private final Set<String> stopWords;

    public TurkishLanguageConfig() {
        this.locale = new Locale("tr", "TR");
        this.stopWords = Set.of(
            "son", "dakika", "video", "galeri",
            "izle", "foto", "yorum", "haber",
            "haberi", "güncel", "flas", "flaş"
        );
    }

    /**
     * Returns the Turkish locale.
     *
     * @return Turkish locale (tr_TR)
     */
    public Locale getLocale() {
        return locale;
    }

    /**
     * Returns the set of Turkish stop words to filter out during normalization.
     *
     * @return immutable set of stop words
     */
    public Set<String> getStopWords() {
        return stopWords;
    }
}

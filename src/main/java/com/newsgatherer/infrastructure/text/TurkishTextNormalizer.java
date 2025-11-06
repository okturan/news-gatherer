package com.newsgatherer.infrastructure.text;

import com.newsgatherer.config.TurkishLanguageConfig;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Normalizes Turkish text for similarity comparison.
 * Handles Turkish-specific case conversion and stop word removal.
 */
public class TurkishTextNormalizer {
    private final Locale locale;
    private final Set<String> stopWords;

    public TurkishTextNormalizer(TurkishLanguageConfig config) {
        this.locale = config.getLocale();
        this.stopWords = config.getStopWords();
    }

    /**
     * Normalizes the given text by:
     * 1. Converting to lowercase (Turkish-aware)
     * 2. Removing punctuation
     * 3. Filtering out stop words
     * 4. Normalizing whitespace
     *
     * @param text the text to normalize
     * @return normalized text
     */
    public String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String normalized = normalizeCase(text);
        normalized = removePunctuation(normalized);
        normalized = removeStopWords(normalized);

        return normalized.trim();
    }

    /**
     * Converts text to lowercase using Turkish locale rules.
     * Handles special Turkish characters like İ/i and I/ı correctly.
     */
    private String normalizeCase(String text) {
        return text.replace("İ", "i")
                   .replace("I", "ı")
                   .toLowerCase(locale);
    }

    /**
     * Removes all punctuation and normalizes whitespace.
     */
    private String removePunctuation(String text) {
        return text.replaceAll("[\\p{Punct}]+", " ")
                   .replaceAll("\\s+", " ");
    }

    /**
     * Filters out Turkish stop words.
     */
    private String removeStopWords(String text) {
        return Arrays.stream(text.split(" "))
                     .filter(word -> !stopWords.contains(word))
                     .collect(Collectors.joining(" "));
    }
}

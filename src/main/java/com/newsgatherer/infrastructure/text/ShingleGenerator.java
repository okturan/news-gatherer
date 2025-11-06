package com.newsgatherer.infrastructure.text;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Generates character shingles (n-grams) from text for similarity comparison.
 */
public class ShingleGenerator {
    private final int shingleSize;

    public ShingleGenerator(int shingleSize) {
        if (shingleSize < 2 || shingleSize > 10) {
            throw new IllegalArgumentException("Shingle size must be between 2 and 10");
        }
        this.shingleSize = shingleSize;
    }

    /**
     * Generates character shingles from the given text.
     *
     * @param text the text to process
     * @return set of shingles, or empty set if text is null/empty
     */
    public Set<String> generate(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptySet();
        }

        // Pad text with spaces for boundary shingles
        String paddedText = " " + text + " ";
        Set<String> shingles = new HashSet<>();

        int maxIndex = Math.max(0, paddedText.length() - shingleSize);
        for (int i = 0; i <= maxIndex; i++) {
            shingles.add(paddedText.substring(i, i + shingleSize));
        }

        return shingles;
    }

    public int getShingleSize() {
        return shingleSize;
    }
}

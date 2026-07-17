package com.newsgatherer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.newsgatherer.client.GdeltApiClient;
import com.newsgatherer.client.GdeltApiClient.FetchResult;
import com.newsgatherer.config.CliOptions;
import com.newsgatherer.domain.Article;
import com.newsgatherer.output.ArticlePrinter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GdeltNewsGathererTest {

    @Test
    void offlineDemoUsesRealRendererWithoutApiOrDatabaseAccess() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ArticlePrinter printer = new ArticlePrinter(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        GdeltNewsGatherer gatherer = new GdeltNewsGatherer(
            new GdeltApiClient(),
            printer,
            new ObjectMapper().registerModule(new JavaTimeModule())
        );

        gatherer.runDemo(CliOptions.parse(new String[] {"--demo"}));

        String output = bytes.toString(StandardCharsets.UTF_8);
        assertThat(output).containsSubsequence(
            "News Gatherer • deterministic offline demo",
            "No network requests or database writes are performed.",
            "[001] 2026-07-17 09:15 • kamu.example",
            "[002] 2026-07-17 08:42 • arsiv.example",
            "[003] 2026-07-17 07:30 • lab.example",
            "=== SUMMARY ===",
            "Fetched:      5",
            "New articles: 3",
            "Duplicates:   2"
        );
    }

    @Test
    void splitsSaturatedWindowEvenWhenSomeRawRowsWereDiscarded() {
        Article parsedArticle = new Article(
            "https://example.com/valid",
            "Valid",
            "example.com",
            "English",
            "United States",
            null,
            null,
            "https://example.com/valid"
        );
        FetchResult saturated = new FetchResult(List.of(parsedArticle), 200);

        assertThat(GdeltNewsGatherer.shouldSplitWindow(saturated, Duration.ofHours(1))).isTrue();
        assertThat(GdeltNewsGatherer.shouldSplitWindow(saturated, Duration.ofMinutes(30))).isFalse();
    }
}

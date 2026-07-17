package com.newsgatherer.output;

import com.newsgatherer.domain.Article;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArticlePrinterTest {

    @Test
    void printsArticlesInDeterministicRecencyOrder() {
        Article older = article(
            "https://older.example/story",
            "Older story",
            "older.example",
            ZonedDateTime.of(2025, 11, 9, 5, 42, 0, 0, ZoneId.of("Europe/Istanbul")),
            null
        );
        Article sameTimeB = article(
            "https://beta.example/story",
            "Beta story",
            "beta.example",
            null,
            ZonedDateTime.of(2025, 11, 9, 3, 45, 0, 0, ZoneOffset.UTC)
        );
        Article sameTimeA = article(
            "https://alpha.example/story",
            "Alpha story",
            "alpha.example",
            null,
            ZonedDateTime.of(2025, 11, 9, 3, 45, 0, 0, ZoneOffset.UTC)
        );
        List<Article> input = new ArrayList<>(List.of(older, sameTimeB, sameTimeA));

        String output = print(input, 5, 2);

        assertThat(output).isEqualTo("""
            [001] 2025-11-09 03:45 • alpha.example
                  Alpha story
                  https://alpha.example/story

            [002] 2025-11-09 03:45 • beta.example
                  Beta story
                  https://beta.example/story

            [003] 2025-11-09 05:42 • older.example
                  Older story
                  https://older.example/story

            === SUMMARY ===
            Fetched:      5
            New articles: 3
            Duplicates:   2

            """);
        assertThat(input).containsExactly(older, sameTimeB, sameTimeA);
    }

    @Test
    void usesSeenDateAndPlacesUndatedArticlesLast() {
        Article dated = article(
            "https://dated.example/story",
            "Dated story",
            "dated.example",
            ZonedDateTime.of(2025, 1, 2, 3, 4, 0, 0, ZoneOffset.UTC),
            null
        );
        Article undated = article(
            "https://undated.example/story",
            "Undated story",
            "undated.example",
            null,
            null
        );

        String output = print(List.of(undated, dated), 2, 0);

        assertThat(output).containsSubsequence(
            "[001] 2025-01-02 03:04 • dated.example",
            "[002] NO DATE • undated.example"
        );
    }

    @Test
    void rejectsInvalidInputsBeforeWriting() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ArticlePrinter printer = new ArticlePrinter(new PrintStream(bytes, true, StandardCharsets.UTF_8));

        assertThatThrownBy(() -> printer.printArticles(null, 0, 0))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("articles cannot be null");
        assertThatThrownBy(() -> printer.printArticles(List.of(), -1, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("totalFetched cannot be negative");
        assertThat(bytes.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    private String print(List<Article> articles, long totalFetched, long duplicateCount) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream stream = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        new ArticlePrinter(stream).printArticles(articles, totalFetched, duplicateCount);
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private Article article(String url,
                            String title,
                            String domain,
                            ZonedDateTime seenDate,
                            ZonedDateTime publishedDate) {
        return new Article(url, title, domain, "Turkish", "Turkey", seenDate, publishedDate, url);
    }
}

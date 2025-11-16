# News Gatherer

A lean Java 25 CLI that pulls Turkish articles from the GDELT DOC 2.0 API, deduplicates them by canonical URL, and stores every fresh story in a local SQLite database for later analysis.

## Quick Start

```bash
mvn clean package
java -jar target/news-gatherer-2.0.0-SNAPSHOT.jar
```

### Backfill Entire Weeks

Use the new CLI flags to chunk the lookback window so you can safely ingest every Turkish article without blowing past the 250-row cap. Example: fetch the last 7 days using 1‑hour slices (the default window) while respecting the built-in rate limit:

```bash
mvn clean package
java -jar target/news-gatherer-2.0.0-SNAPSHOT.jar --lookback=7d --window=1h
```

Other useful flags:

- `--query=...` – override the default `sourcecountry:turkey sourcelang:turkish` query
- `--timespan=...` – single-run timespan (e.g., `30m`, `2h`) when not using backfill mode
- `--window=...` – chunk size for the backfill loop (supports `m`, `h`, `d` suffixes)

## What It Does

1. **Fetches** up to 200 Turkish articles from GDELT’s `/doc` API over the last 2 hours (configurable) and warns when the response hits the 250‑row cap.
2. **Canonicalizes** URLs by trimming tracking parameters (`utm_*`, `fbclid`, etc.) and trailing slashes so different share links collapse to the same key.
3. **Deduplicates** incrementally via SQLite (`output/news-gatherer.db`). Once a canonical URL is seen, it will be skipped for the next 7 days (retention window).
4. **Persists** new articles (URL, title, domain, language, timestamps) and prints them in recency order with fetched/new/duplicate counts.

### Storage Layout

- Database: `output/news-gatherer.db`
- `seen_urls(canonical_url TEXT PRIMARY KEY, first_seen INTEGER)`
- `articles(id INTEGER PK, url, title, domain, language, source_country, seen_date, published_date, canonical_url, stored_at)`
- Inspect with `sqlite3 output/news-gatherer.db 'SELECT title, domain FROM articles ORDER BY id DESC LIMIT 5;'`

## Example Output

```
[001] 2025-11-09 03:45 • aa.com.tr
      Cumhurbaşkanı Erdoğan yurda döndü
      https://www.aa.com.tr/tr/politika/cumhurbaskani-erdogan-yurda-dondu/123456

[002] 2025-11-09 03:42 • hurriyet.com.tr
      İstanbul'da kuvvetli fırtına uyarısı
      https://www.hurriyet.com.tr/gundem/istanbulda-kuvvetli-firtina-uyarisi-123456

=== SUMMARY ===
Fetched:      153
New articles: 42
Duplicates:   111
```

## Architecture

```
src/main/java/com/newsgatherer/
├── GdeltNewsGatherer.java          // CLI entry point & workflow orchestration
├── config/Config.java              // Tunables (API, rate limits, storage)
├── domain/Article.java             // Record for parsed articles
├── client/GdeltApiClient.java      // HTTP client + rate limiting + JSON fetch
├── parser/ArticleParser.java       // JSON → Article mapping & canonicalization
├── output/ArticlePrinter.java      // Console formatting for new articles
└── storage/ArticleRepository.java  // SQLite persistence + seen-url tracking
```

No DI, no frameworks, no background services—just a few single-purpose classes wired together in the CLI.

## Configuration

Key knobs in `config/Config.java`:

```java
// GDELT API
public static final String GDELT_API_ENDPOINT = "https://api.gdeltproject.org/api/v2/doc/doc";
public static final int MAX_ARTICLES = 200;         // request size (≤250)
public static final String DEFAULT_TIMESPAN = "2h"; // window per run
public static final Duration MIN_REQUEST_INTERVAL = Duration.ofMillis(2000); // 0.5 QPS

// Storage
public static final String OUTPUT_DIR = "output";
public static final String DATABASE_FILE = OUTPUT_DIR + "/news-gatherer.db";
public static final Duration SEEN_URL_RETENTION = Duration.ofDays(7);
```

## GDELT Constraints & Tips

- The DOC API caps responses at 250 rows. If you regularly see the “near limit” warning, rerun with a shorter `TIMESPAN` (e.g., `1h` or `30m`) or build a time-splitting loop.
- GDELT does not expose article bodies or summaries—only metadata. For Ground-News‑style comparisons you’d need to fetch each URL yourself.
- Rate limiting is mandatory: sustained >0.5 QPS can get throttled. The CLI enforces a 2‑second gap between requests.

## How It Works Internally

1. **HTTP** – `GdeltApiClient` builds the query string (`mode=artlist`, `timespan`, `maxrecords`) and uses Java’s `HttpClient` with HTTP/2 and a built-in rate limiter.
2. **Parsing** – `ArticleParser` reads the JSON, handles both `articles` and `artlist` payloads, and maps each node to an `Article` record.
3. **Canonicalization** – Domains fall back to the URL host, tracking params are stripped, and everything is lowercased via `Locale.ROOT` for stability.
4. **Deduplication** – `seen_urls` is loaded at startup, pruned (entries older than 7 days deleted), and updated with `INSERT OR IGNORE` for each new canonical URL.
5. **Persistence** – `ArticleRepository` batches fresh stories into the `articles` table with ISO-8601 timestamps and the run’s `stored_at` epoch so downstream tooling can group by ingest.
6. **Display** – `ArticlePrinter` shows new items in reverse chronological order along with run-level metrics so you can spot anomalies quickly.

## Java 25 Features in Play

- **Records** for `Article` (immutable, concise data carrier)
- **Stream::toList** for tidy collection materialization without collectors
- **Text blocks** for multi-line SQL DDL/insert statements

## Development Notes

- `sqlite3 output/news-gatherer.db` is the fastest way to explore historical runs. Keep ad‑hoc queries in `data/` if they become reusable.
- When adding new metadata columns, update the `articles` table DDL and the insert statement; SQLite will retain existing rows (new columns default to `NULL`).
- Run `mvn clean verify` before sharing changes—Checkstyle is wired into the build and enforces basic hygiene.

Happy collecting!

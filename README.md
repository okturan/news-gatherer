# News Gatherer

A lightweight Turkish news clustering tool. Fetches articles from GDELT, groups similar stories, and displays them with canonical source selection.

**624 lines of Java 25 | 4 files | Zero complexity overhead**

## Quick Start

```bash
# Build
mvn clean package

# Run
java -jar target/news-gatherer-2.0.0-SNAPSHOT.jar
```

## What It Does

1. Fetches Turkish news from GDELT API (200 articles, last 2 hours)
2. Normalizes titles (Turkish lowercase, stop words, punctuation)
3. Generates character shingles (4-char n-grams)
4. Clusters similar articles (Jaccard similarity ≥ 80%, 48h window)
5. Selects canonical source (Wire > Publisher > Aggregator)
6. Displays deduplicated stories

## Example Output

```
[1] 2025-11-07 02:15 • CANONICAL (WIRE) aa.com.tr
     İstanbul'da önemli gelişme
     https://www.aa.com.tr/tr/gundem/istanbul-onemli-gelisme/123456
     Members (3):
       ★ WIRE        aa.com.tr            11-07 02:15  İstanbul'da önemli gelişme
       • PUBLISHER   hurriyet.com.tr      11-07 02:16  İstanbul gelişmesi
       • AGGREGATOR  ensonhaber.com       11-07 02:20  Son Dakika: İstanbul haberi

=== METRICS ===
Articles fetched:   200
Story clusters:     45
Avg items/cluster:  4.44
```

## Architecture

**4 files, 624 lines:**

```
src/main/java/com/newsgatherer/
├── GdeltNewsClustering.java    495 lines  Main logic
├── Config.java                  57 lines  Constants
├── Article.java                 33 lines  Data model
└── SourceType.java              39 lines  Enum
```

**No interfaces, no DI, no layers - just simple, readable code.**

## Configuration

Edit `Config.java`:

```java
// GDELT API Configuration
public static final String GDELT_API_ENDPOINT = "https://api.gdeltproject.org/api/v2/doc/doc";
public static final int MAX_ARTICLES = 200;
public static final String DEFAULT_TIMESPAN = "2h";

// GDELT API Limits (respects DOC 2.0 API constraints)
public static final int GDELT_MAX_RECORDS = 250;      // Hard API limit
public static final int SAFE_MAX_RECORDS = 240;       // Safety margin
public static final Duration MIN_REQUEST_INTERVAL = Duration.ofMillis(2000);  // 0.5 QPS

// Clustering
public static final Duration TIME_WINDOW = Duration.ofHours(48);
public static final double SIMILARITY_THRESHOLD = 0.80;
public static final int SHINGLE_SIZE = 4;

// Turkish stop words
public static final Set<String> STOP_WORDS = Set.of(
    "son", "dakika", "video", "galeri", ...
);
```

### GDELT API Limits

The implementation respects GDELT DOC 2.0 API constraints:

- **Max 250 articles** per request (mode=artlist limit)
- **Rate limited** to 0.5 QPS to avoid throttling
- **Warns** when hitting near the 250-article limit
- **URL deduplication** via canonicalization (strips UTM params, fbclid, etc.)

**For large time windows (>4h):** Manually split into smaller slices to avoid truncation at 250 articles.

## How It Works

### Text Processing
- Turkish case: İ→i, I→ı
- Remove punctuation: `[\\p{Punct}]+` → space
- Remove stop words: "son dakika video" etc.
- Generate shingles: "test" → " tes", "test", "est "

### Clustering
- Sort by time (newest first)
- Compare within 48h window using Jaccard similarity
- Union-Find for efficient grouping: O(n×α(n)) ≈ O(n)
- Jaccard threshold: 0.80 (80% overlap required)

### Canonical Selection
Priority: WIRE (1) > PUBLISHER (2) > AGGREGATOR (3)
Tie-break: earliest timestamp

## Performance

**Current (optimized):**
- 100 articles: <1s
- 500 articles: 1-2s
- 1,000 articles: 2-4s

**Optimizations applied:**
- Iterate smaller set in Jaccard similarity
- Pre-compiled regex patterns
- Epoch millis for time comparisons
- Streaming JSON parsing
- No defensive copies

**For 10,000+ articles:** Implement inverted shingle index (see CHANGELOG.md)

## Java 25 Features

- Records (Article)
- Pattern matching for switch (JSON parsing)
- Unnamed variables `_` (unused lambda params, catch blocks)
- Sequenced collections (getFirst)
- Text blocks
- Locale.of()

## Why This Architecture?

This is a **deliberately simple** codebase following YAGNI principles:

✅ **Use this approach for:**
- CLI tools
- Single-purpose apps
- Scripts that grew up
- Small teams (1-3 devs)

❌ **Don't use for:**
- Large teams (10+ devs)
- Multiple deployment targets
- Heavy mocking requirements
- Enterprise with rigid architecture

## Design Philosophy

**Pragmatism over purity:**
- One file for main logic (not 31 files)
- Constants instead of builders (not 3 config classes)
- Direct calls instead of DI (not 158-line container)
- Same functionality, 70% less code

**Before (over-engineered):** 2,097 lines, 31 files, 8 packages, 4 layers
**After (pragmatic):** 624 lines, 4 files, 1 package, simple

See `CHANGELOG.md` for refactoring journey.

## Development

### Adding Features

```java
// Example: Add JSON output format
private void displayAsJSON(List<List<Article>> clusters) {
    // Implementation here
}

// In main()
if (args.length > 0 && "json".equals(args[0])) {
    app.displayAsJSON(clusters);
} else {
    app.displayClusters(clusters);
}
```

### Testing

```bash
mvn test                    # Run tests
mvn clean verify            # Full build with tests
```

### Code Style

- Keep methods under 30 lines
- Use descriptive names
- Add comments for complex algorithms only
- Pre-compile regex as static fields
- Validate inputs early

## Contributing

This codebase values **simplicity**. Welcome contributions:

✅ Bug fixes
✅ Performance improvements
✅ Better algorithms
✅ Documentation

❌ Abstraction layers
❌ Interface hierarchies
❌ Dependency injection
❌ Complexity for "future needs"

## Requirements

- Java 25+
- Maven 3.9+

## License

MIT License - Use freely for personal or commercial projects.

## Acknowledgments

- GDELT Project - Global news database
- Java 25 - Modern language features
- Jackson - JSON processing

---

**Built with ☕ and pragmatism** | 624 lines of simple, effective code

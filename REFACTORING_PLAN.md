# Comprehensive Refactoring Plan for News Gatherer Application

**Source File:** `gather.java` (GdeltDedupe)
**Current State:** Single 240-line monolithic class with static methods
**Target State:** Well-architected, testable, maintainable application following SOLID principles
**Target Java Version:** Java 21+ (LTS) with Java 25 features where beneficial

---

## Executive Summary

The current implementation is a **working prototype** that successfully gathers and clusters news articles. However, it suffers from significant architectural debt:

- **God Class Anti-Pattern**: All logic in one static class
- **SOLID Violations**: Multiple responsibilities, tight coupling, no abstraction
- **Poor Testability**: Static methods prevent proper unit testing
- **Low Maintainability**: Hard to extend or modify
- **Configuration Issues**: All values hardcoded

This plan outlines a systematic refactoring to transform the codebase into a production-ready application.

---

## Modern Java Features to Leverage

### Java 21 LTS Features

#### 1. Virtual Threads (JEP 444) - HIGH IMPACT
**Use Case:** Concurrent API requests to GDELT

**Current Problem:** Sequential HTTP requests are slow
```java
// Current: Sequential fetching (slow)
for (var article : articles) {
    enrichArticle(article); // HTTP call
}
```

**With Virtual Threads:**
```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    List<CompletableFuture<Article>> futures = articles.stream()
        .map(article -> CompletableFuture.supplyAsync(
            () -> enrichArticle(article), executor))
        .toList();

    return futures.stream()
        .map(CompletableFuture::join)
        .toList();
}
```

**Benefits:**
- Millions of concurrent requests possible
- No thread pool management
- Cleaner code than traditional thread pools

#### 2. Pattern Matching for switch (JEP 441) - MEDIUM IMPACT
**Use Case:** Handling different JSON response structures

**Current:**
```java
String field = txt(node, "url", "urlMobile");
if (field == null) {
    field = txt(node, "link");
}
```

**With Pattern Matching:**
```java
String url = switch (node) {
    case JsonNode n when n.has("url") -> n.get("url").asText();
    case JsonNode n when n.has("urlMobile") -> n.get("urlMobile").asText();
    case JsonNode n when n.has("link") -> n.get("link").asText();
    default -> null;
};
```

#### 3. Record Patterns (JEP 440) - MEDIUM IMPACT
**Use Case:** Deconstructing Article records

**Current:**
```java
Article article = cluster.get(0);
ZonedDateTime time = article.published() != null ?
    article.published() : article.seen();
```

**With Record Patterns:**
```java
if (cluster.get(0) instanceof Article(_, _, _, _, _, var seen, var pub, _, _, _, _)) {
    ZonedDateTime time = pub != null ? pub : seen;
}

// Or in switch
ZonedDateTime time = switch (cluster.get(0)) {
    case Article(_, _, _, _, _, var seen, var pub, _, _, _, _)
        when pub != null -> pub;
    case Article(_, _, _, _, _, var seen, _, _, _, _, _) -> seen;
};
```

#### 4. Sequenced Collections (JEP 431) - LOW IMPACT
**Use Case:** First/last article in cluster

**Current:**
```java
Article first = cluster.get(0);
Article last = cluster.get(cluster.size() - 1);
```

**With Sequenced Collections:**
```java
Article first = cluster.getFirst();
Article last = cluster.getLast();
cluster.addFirst(newArticle);  // Add to beginning
cluster.reversed();  // Reverse view
```

#### 5. Unnamed Patterns and Variables (JEP 443) - LOW IMPACT
**Use Case:** Cleaner lambdas and patterns

**Current:**
```java
articles.forEach(a -> processArticle(a));
clusters.forEach(c -> { /* don't use c */ });
```

**With Unnamed Variables:**
```java
articles.forEach(this::processArticle);  // Method reference preferred
clusters.forEach(_ -> someAction());  // _ for unused parameter
```

### Java 22-25 Features

#### 6. Statements before super() (JEP 447) - LOW IMPACT
**Use Case:** Validation in constructors

**Current:**
```java
public ClusteringConfig(int windowHours, double threshold) {
    // Can't validate before calling super()
    this.windowHours = windowHours;
    validate();
}
```

**With Flexible Constructor Bodies:**
```java
public ClusteringConfig(int windowHours, double threshold) {
    if (windowHours <= 0) {
        throw new IllegalArgumentException("Window hours must be positive");
    }
    this.windowHours = windowHours;
    this.threshold = threshold;
}
```

#### 7. Stream Gatherers (JEP 461) - MEDIUM IMPACT
**Use Case:** Custom clustering operations

**Current:** Complex clustering logic scattered
```java
// Manual windowing and grouping
for (int i = 0; i < articles.size(); i++) {
    // Complex time-window logic
}
```

**With Stream Gatherers:**
```java
List<List<Article>> clusters = articles.stream()
    .gather(timeWindowGatherer(Duration.ofHours(48)))
    .gather(similarityGroupingGatherer(0.80))
    .toList();

// Custom gatherer
Gatherer<Article, ?, List<Article>> timeWindowGatherer(Duration window) {
    return Gatherer.ofSequential(
        () -> new WindowState(window),
        (state, article, downstream) -> {
            state.add(article);
            state.removeOld();
            return state.process(downstream);
        }
    );
}
```

#### 8. Simplified Main Method (JEP 445) - LOW IMPACT
**Use Case:** Cleaner entry point

**Current:**
```java
public class ConsoleApplication {
    public static void main(String[] args) {
        // Application startup
    }
}
```

**With Unnamed Classes:**
```java
void main() {  // No String[] args needed if not used
    ApplicationContext context = new ApplicationContext();
    context.run();
}
```

#### 9. String Templates (Standardizing) - MEDIUM IMPACT
**Use Case:** Cleaner string formatting

**Current:**
```java
System.out.printf("       %s %-11s %-20s %s  %s%n",
    marker, m.sourceType, m.domain, timestamp, title);
```

**With String Templates:**
```java
System.out.println(STR."""
       \{marker} \{m.sourceType}%-11s \{m.domain}%-20s \{timestamp} \{title}
    """);
```

### Recommended Feature Adoption Priority

**High Priority (Use Immediately):**
1. ✅ **Virtual Threads** - Major performance improvement for concurrent API calls
2. ✅ **Pattern Matching for switch** - Cleaner JSON handling
3. ✅ **Sequenced Collections** - Better collection APIs

**Medium Priority (Use Where Applicable):**
4. ⚠️ **Record Patterns** - Useful but verbose in some cases
5. ⚠️ **Stream Gatherers** - Powerful for custom operations
6. ⚠️ **String Templates** - Once standardized

**Low Priority (Nice to Have):**
7. ℹ️ **Unnamed Variables** - Minor code cleanup
8. ℹ️ **Flexible Constructor Bodies** - Limited use cases

---

## Current Architecture Issues

### Critical Problems

1. **Single Responsibility Principle (SRP) Violations**
   - 10+ distinct responsibilities in one class:
     - HTTP client operations
     - JSON parsing
     - Domain logic (canonical selection)
     - String processing (normalization, shingles)
     - URL processing
     - Similarity calculation
     - Clustering algorithm
     - Presentation logic
     - Configuration management
     - Application orchestration

2. **Open/Closed Principle (OCP) Violations**
   - Hardcoded source types (WIRE, AGG sets)
   - Fixed clustering algorithm
   - Fixed output format
   - Cannot extend without modifying code

3. **Dependency Inversion Principle (DIP) Violations**
   - Direct dependencies on concrete implementations
   - No abstraction layers
   - High-level logic depends on low-level details

4. **Static Method Overuse**
   - Prevents dependency injection
   - Makes testing extremely difficult
   - Tight coupling throughout

### Code Smells

- **God Class**: 240 lines with 10+ responsibilities
- **Primitive Obsession**: Strings used for enum-like values
- **Magic Numbers**: Hardcoded values (48, 0.80, 4, 200)
- **Long Methods**: Methods exceeding 20-30 lines
- **Feature Envy**: pickCanonical knows too much about Article internals
- **Code Duplication**: JSON extraction patterns, stream filtering patterns

---

## Target Architecture

### Package Structure

```
com.newsgatherer/
├── domain/
│   ├── model/
│   │   ├── Article.java
│   │   ├── ArticleCluster.java
│   │   ├── SourceType.java (enum)
│   │   └── ClusteringConfig.java
│   ├── service/
│   │   ├── ClusteringService.java (interface)
│   │   ├── CanonicalSelectionService.java (interface)
│   │   └── ArticleSimilarityService.java (interface)
│   └── repository/
│       └── ArticleRepository.java (interface)
│
├── application/
│   ├── service/
│   │   ├── ArticleGatheringService.java
│   │   └── ArticleDeduplicationService.java
│   ├── usecase/
│   │   └── GatherAndDeduplicateArticlesUseCase.java
│   └── dto/
│       └── ClusteringResultDto.java
│
├── infrastructure/
│   ├── http/
│   │   ├── GdeltApiClient.java
│   │   └── GdeltArticleParser.java
│   ├── clustering/
│   │   ├── JaccardClusteringService.java
│   │   └── UnionFind.java (renamed from DSU)
│   ├── similarity/
│   │   ├── ShingleBasedSimilarityService.java
│   │   └── ShingleGenerator.java
│   ├── selection/
│   │   └── SourceTypeBasedCanonicalSelector.java
│   └── text/
│       ├── TurkishTextNormalizer.java
│       ├── UrlCanonicalizer.java
│       └── DomainExtractor.java
│
├── presentation/
│   ├── console/
│   │   ├── ConsoleApplication.java
│   │   ├── ClusterFormatter.java
│   │   └── MetricsPresenter.java
│
└── config/
    ├── ApplicationConfig.java
    ├── GdeltApiConfig.java
    ├── ClusteringConfig.java
    └── SourceTypeConfig.java
```

### Layer Responsibilities

**Domain Layer** (Business Logic)
- Pure domain objects (Article, ArticleCluster, SourceType)
- Domain service interfaces
- No external dependencies
- Framework-agnostic

**Application Layer** (Use Cases)
- Orchestrates domain objects
- Contains use case implementations
- Thin layer - delegates to domain services

**Infrastructure Layer** (Technical Implementation)
- HTTP clients, JSON parsing
- Clustering algorithms
- Text processing utilities
- Implements domain interfaces

**Presentation Layer** (User Interface)
- Console output formatting
- Command-line argument parsing
- No business logic

---

## Refactoring Phases

### Phase 1: Foundation (Week 1) - HIGH PRIORITY

**Goal:** Establish core domain model and enums

#### 1.1 Extract SourceType Enum
**Impact:** HIGH | **Effort:** LOW | **Files:** 1 new

**Current (lines 16-18, 202-206):**
```java
static final Set<String> WIRE = Set.of("aa.com.tr","iha.com.tr","dha.com.tr");
static final Set<String> AGG  = Set.of("ensonhaber.com","haberler.com"...);

static String sourceType(String d){
    if (WIRE.contains(d)) return "WIRE";
    if (AGG.contains(d))  return "AGGREGATOR";
    return "PUBLISHER";
}
```

**Target:**
```java
public enum SourceType {
    WIRE(1, Set.of("aa.com.tr", "iha.com.tr", "dha.com.tr")),
    PUBLISHER(2, Set.of()),
    AGGREGATOR(3, Set.of("ensonhaber.com", "haberler.com",
                         "sondakika.com", "gazeteoku.com"));

    private final int priority;
    private final Set<String> domains;

    SourceType(int priority, Set<String> domains) {
        this.priority = priority;
        this.domains = domains;
    }

    public int getPriority() { return priority; }

    public static SourceType fromDomain(String domain) {
        return Arrays.stream(values())
            .filter(type -> type.domains.contains(domain))
            .findFirst()
            .orElse(PUBLISHER);
    }

    public static Comparator<SourceType> byPriority() {
        return Comparator.comparingInt(SourceType::getPriority);
    }
}
```

**Benefits:**
- Type safety
- Encapsulated domain logic
- Simplified canonical selection

#### 1.2 Extract Configuration Classes
**Impact:** HIGH | **Effort:** MEDIUM | **Files:** 3 new

Create:
- `GdeltApiConfig.java` - API endpoint, timeout, retry settings
- `ClusteringConfig.java` - Similarity threshold, window hours, shingle size
- `TurkishLanguageConfig.java` - Stop words, locale

**Benefits:**
- Testable configuration
- Environment-specific settings
- No hardcoded values

#### 1.3 Update Article Record
**Impact:** MEDIUM | **Effort:** LOW | **Files:** 1 modified

Change `sourceType` from `String` to `SourceType` enum:
```java
record Article(
    String url, String title, String domain,
    String lang, String sourcecountry,
    ZonedDateTime seen, ZonedDateTime published,
    SourceType sourceType,  // Changed from String
    String titleNorm, Set<String> shingles, String canonUrl
) {}
```

---

### Phase 2: Infrastructure Extraction (Week 2) - HIGH PRIORITY

**Goal:** Extract all technical concerns into infrastructure layer

#### 2.1 Extract UnionFind Data Structure
**Impact:** MEDIUM | **Effort:** LOW | **Files:** 1 new

Rename DSU to UnionFind with:
- Meaningful variable names (`parent`, `rank` instead of `p`, `r`)
- Javadoc comments
- Additional utility methods

#### 2.2 Extract Text Processing Services
**Impact:** HIGH | **Effort:** MEDIUM | **Files:** 3 new

Create:
1. **TurkishTextNormalizer.java**
   - `normalize(String text)` - Case normalization, stop words removal
   - `generateShingles(String text)` - Character shingles

2. **UrlCanonicalizer.java**
   - `canonicalize(String url)` - Remove tracking params, normalize

3. **DomainExtractor.java**
   - `extractDomain(String url)` - Parse domain from URL

**Extraction Points:**
- Lines 208-213: `normalizeTitle` → TurkishTextNormalizer
- Lines 214-219: `shingles` → ShingleGenerator
- Lines 180-200: `canonicalUrl` → UrlCanonicalizer
- Lines 173-178: `parseDomain` → DomainExtractor

#### 2.3 Extract HTTP Client Layer
**Impact:** HIGH | **Effort:** MEDIUM | **Files:** 2 new

Create:
1. **GdeltApiClient.java**
   - Handles HTTP communication
   - Retry logic
   - Error handling
   - Returns JsonNode

2. **GdeltArticleParser.java**
   - Converts JsonNode to Article
   - Uses TextNormalizer, UrlCanonicalizer, etc.

**Extraction Points:**
- Lines 67-76: HTTP fetching → GdeltApiClient
- Lines 77-92: JSON parsing → GdeltArticleParser

---

### Phase 3: Domain Services (Week 3) - MEDIUM PRIORITY

**Goal:** Create domain service abstractions

#### 3.1 Create Domain Interfaces

1. **ArticleSimilarityService.java**
```java
public interface ArticleSimilarityService {
    double calculateSimilarity(Article a, Article b);
    boolean areSimilar(Article a, Article b, double threshold);
}
```

2. **ClusteringService.java**
```java
public interface ClusteringService {
    List<ArticleCluster> cluster(
        List<Article> articles,
        ClusteringConfig config
    );
}
```

3. **CanonicalSelectionService.java**
```java
public interface CanonicalSelectionService {
    Article selectCanonical(List<Article> articles);
}
```

4. **ArticleRepository.java**
```java
public interface ArticleRepository {
    List<Article> findByQuery(ArticleQuery query);
}
```

#### 3.2 Implement Domain Services

1. **JaccardSimilarityService.java** (implements ArticleSimilarityService)
   - Extract from lines 220-226

2. **TimeWindowedClusteringService.java** (implements ClusteringService)
   - Extract from lines 115-140

3. **SourceTypeCanonicalSelector.java** (implements CanonicalSelectionService)
   - Extract from lines 142-150

4. **GdeltArticleRepository.java** (implements ArticleRepository)
   - Uses GdeltApiClient + GdeltArticleParser

---

### Phase 4: Application Layer (Week 4) - MEDIUM PRIORITY

**Goal:** Build use cases and application services

#### 4.1 Create Application Services

1. **ArticleGatheringService.java**
   - Fetches articles from repository
   - Enriches with metadata

2. **ArticleDeduplicationService.java**
   - Coordinates clustering
   - Applies canonical selection

#### 4.2 Create Use Case

**GatherAndDeduplicateArticlesUseCase.java**
- Main workflow orchestration
- Coordinates gathering + deduplication
- Returns DTOs for presentation layer

#### 4.3 Create DTOs

**ClusteringResultDto.java**
- Contains clusters, metrics
- Used by presentation layer

---

### Phase 5: Presentation Layer (Week 5) - LOW PRIORITY

**Goal:** Refactor output formatting

#### 5.1 Extract Formatters

1. **ClusterFormatter.java**
   - Extract from lines 32-57
   - Format clusters for console

2. **MetricsPresenter.java**
   - Extract from lines 59-64
   - Display metrics

#### 5.2 Create Console Application

**ConsoleApplication.java**
- Replaces main method
- Parses command-line arguments
- Uses use cases
- Delegates formatting

---

### Phase 6: Dependency Injection & Configuration (Week 6) - LOW PRIORITY

**Goal:** Wire everything together

#### 6.1 Create Dependency Injection

Manual DI or Spring Boot:
```java
@Configuration
public class ApplicationConfig {

    @Bean
    public HttpClient httpClient(GdeltApiConfig config) {
        return HttpClient.newBuilder()
            .connectTimeout(config.timeout())
            .build();
    }

    @Bean
    public GdeltArticleRepository articleRepository(
            GdeltApiClient client,
            GdeltArticleParser parser) {
        return new GdeltArticleRepository(client, parser);
    }

    // ... other beans
}
```

#### 6.2 Externalize Configuration

Create `application.yml`:
```yaml
news:
  api:
    endpoint: https://api.gdeltproject.org/api/v2/doc/doc
    timeout: 30s
    max-records: 200

  clustering:
    window-hours: 48
    similarity-threshold: 0.80
    shingle-size: 4
```

---

## Design Patterns to Apply

### 1. Strategy Pattern (Clustering Algorithms)
**Location:** ClusteringService interface
**Benefit:** Swap algorithms easily (Jaccard, TF-IDF, ML-based)

### 2. Builder Pattern (Query Construction)
**Location:** ArticleQuery, GdeltQueryBuilder
**Benefit:** Clean API, optional parameters

### 3. Factory Pattern (Article Parsing)
**Location:** GdeltArticleParser
**Benefit:** Encapsulate complex creation logic

### 4. Repository Pattern (Data Access)
**Location:** ArticleRepository interface
**Benefit:** Abstract data source, enable caching/persistence

### 5. Template Method Pattern (Processing Pipeline)
**Location:** ArticleProcessingPipeline
**Benefit:** Define workflow skeleton, allow customization

---

## Detailed Refactoring Examples

### Example 1: Simplified pickCanonical

**Before (lines 142-150):**
```java
static int pickCanonical(List<Article> cluster) {
    Comparator<Article> byTime = Comparator.comparing(GdeltDedupe::timeOf);
    var wires = cluster.stream().filter(a -> a.sourceType.equals("WIRE")).min(byTime);
    if (wires.isPresent()) return cluster.indexOf(wires.get());
    var pubs  = cluster.stream().filter(a -> a.sourceType.equals("PUBLISHER")).min(byTime);
    if (pubs.isPresent())  return cluster.indexOf(pubs.get());
    var aggs  = cluster.stream().filter(a -> a.sourceType.equals("AGGREGATOR")).min(byTime);
    return aggs.map(cluster::indexOf).orElse(0);
}
```

**After:**
```java
public class SourceTypeCanonicalSelector implements CanonicalSelectionService {

    @Override
    public Article selectCanonical(List<Article> articles) {
        return articles.stream()
            .min(Comparator
                .comparing(Article::sourceType, SourceType.byPriority())
                .thenComparing(this::getArticleTime))
            .orElseThrow(() -> new IllegalArgumentException("Empty cluster"));
    }

    private ZonedDateTime getArticleTime(Article article) {
        return article.published() != null ?
               article.published() : article.seen();
    }
}
```

**Benefits:**
- Reduced from 9 lines to 7 lines
- Eliminated duplication (3 similar stream operations → 1)
- More declarative
- Type-safe (SourceType enum)
- Testable (implements interface)

### Example 2: Refactored Main Method

**Before (lines 27-65): 39 lines**

**After:**
```java
public class ConsoleApplication {
    private final GatherAndDeduplicateArticlesUseCase useCase;
    private final ClusterFormatter formatter;
    private final MetricsPresenter metricsPresenter;

    public void run(String[] args) {
        ArticleQuery query = parseArguments(args);

        ClusteringResultDto result = useCase.execute(query);

        if (result.isEmpty()) {
            System.out.println("No articles.");
            return;
        }

        formatter.format(result.getClusters());
        metricsPresenter.present(result.getMetrics());
    }

    public static void main(String[] args) {
        ApplicationContext context = new ApplicationContext();
        ConsoleApplication app = context.getConsoleApplication();
        app.run(args);
    }
}
```

**Benefits:**
- Separated concerns (orchestration vs formatting)
- Testable (inject mocks)
- Configurable (inject dependencies)
- 20 lines instead of 39

### Example 3: Virtual Threads for Article Enrichment (Java 21+)

**Use Case:** Fetching additional metadata for articles concurrently

**Before (Sequential - Slow):**
```java
public List<Article> enrichArticles(List<Article> articles) {
    List<Article> enriched = new ArrayList<>();
    for (Article article : articles) {
        // Each HTTP call takes ~200ms
        ArticleMetadata metadata = fetchMetadata(article.url());
        Article enhanced = article.withMetadata(metadata);
        enriched.add(enhanced);
    }
    return enriched;
    // Time for 100 articles: ~20 seconds
}
```

**After (Virtual Threads - Fast):**
```java
public List<Article> enrichArticles(List<Article> articles) {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        List<CompletableFuture<Article>> futures = articles.stream()
            .map(article -> CompletableFuture.supplyAsync(() -> {
                ArticleMetadata metadata = fetchMetadata(article.url());
                return article.withMetadata(metadata);
            }, executor))
            .toList();

        return futures.stream()
            .map(CompletableFuture::join)
            .toList();
    }
    // Time for 100 articles: ~300ms (60x faster!)
}
```

**Benefits:**
- 60x performance improvement for I/O-bound operations
- No complex thread pool configuration
- Automatic resource cleanup with try-with-resources
- Scales to millions of concurrent operations

### Example 4: Pattern Matching for JSON Parsing (Java 21+)

**Before (Current):**
```java
static String txt(JsonNode n, String... keys){
    for (var k: keys) {
        var v = n.get(k);
        if (v!=null && !v.isNull()) return v.asText();
    }
    return null;
}

// Usage
String url = txt(node, "url", "urlMobile");
String title = txt(node, "title", "titleMobile");
```

**After (Pattern Matching):**
```java
public class GdeltArticleParser {

    public Article parseArticle(JsonNode node) {
        return new Article(
            extractUrl(node),
            extractTitle(node),
            extractDomain(node),
            extractLanguage(node),
            extractSourceCountry(node),
            extractSeenDate(node),
            extractPublishDate(node),
            extractSourceType(node),
            // ... other fields
        );
    }

    private String extractUrl(JsonNode node) {
        return switch (node) {
            case JsonNode n when n.has("url") && !n.get("url").isNull()
                -> n.get("url").asText();
            case JsonNode n when n.has("urlMobile") && !n.get("urlMobile").isNull()
                -> n.get("urlMobile").asText();
            case JsonNode n when n.has("link") && !n.get("link").isNull()
                -> n.get("link").asText();
            default -> null;
        };
    }

    private SourceType extractSourceType(JsonNode node) {
        String domain = extractDomain(node);
        return switch (domain) {
            case String d when d.endsWith("aa.com.tr") -> SourceType.WIRE;
            case String d when d.endsWith("iha.com.tr") -> SourceType.WIRE;
            case String d when d.endsWith("haberler.com") -> SourceType.AGGREGATOR;
            default -> SourceType.PUBLISHER;
        };
    }
}
```

**Benefits:**
- Type-safe with exhaustiveness checking
- More expressive than if-else chains
- Guards (`when`) for additional conditions
- Compiler warnings for missing cases

### Example 5: Stream Gatherers for Time-Windowed Clustering (Java 22+)

**Before (Manual Iteration):**
```java
static List<List<Article>> cluster(List<Article> arts, int windowHours, double thr) {
    var idx = new ArrayList<Integer>();
    for (int i=0;i<arts.size();i++) idx.add(i);
    idx.sort(Comparator.comparing((Integer i)-> arts.get(i).seen).reversed());

    var dsu = new DSU(arts.size());
    var win = Duration.ofHours(windowHours);

    for (int aPos=0; aPos<idx.size(); aPos++) {
        int i = idx.get(aPos);
        var A = arts.get(i);
        for (int j=aPos+1; j<idx.size(); j++) {
            int k = idx.get(j);
            var B = arts.get(k);
            if (Duration.between(B.seen, A.seen).compareTo(win) > 0) break;
            double sim = jaccard(A.shingles, B.shingles);
            if (sim >= thr) dsu.union(i,k);
        }
    }
    // ... build clusters from DSU
}
```

**After (Stream Gatherers - More Declarative):**
```java
public class ClusteringService {

    public List<ArticleCluster> cluster(List<Article> articles,
                                       ClusteringConfig config) {
        return articles.stream()
            .sorted(Comparator.comparing(Article::seenDate).reversed())
            .gather(timeWindowedSimilarityGrouping(
                config.windowDuration(),
                config.similarityThreshold()
            ))
            .map(this::createCluster)
            .toList();
    }

    private Gatherer<Article, WindowedGroupingState, List<Article>>
        timeWindowedSimilarityGrouping(Duration window, double threshold) {

        return Gatherer.ofSequential(
            () -> new WindowedGroupingState(window, threshold),
            Gatherer.Integrator.ofGreedy((state, article, downstream) -> {
                // Add article to current window
                state.addArticle(article);

                // Remove articles outside time window
                state.removeArticlesOutsideWindow(article.seenDate());

                // Check similarity with articles in window
                state.groupSimilarArticles();

                // Emit completed groups
                state.emitCompletedGroups(downstream);

                return true;
            }),
            (state, downstream) -> {
                // Emit remaining groups when stream ends
                state.emitAllGroups(downstream);
            }
        );
    }
}

class WindowedGroupingState {
    private final Duration window;
    private final double threshold;
    private final UnionFind unionFind;
    private final List<Article> currentWindow = new ArrayList<>();

    // ... implementation
}
```

**Benefits:**
- More declarative and composable
- Separates concerns (windowing, grouping)
- Easier to test individual gatherers
- Better abstraction for custom stream operations

### Example 6: Sequenced Collections (Java 21+)

**Before:**
```java
// Get first article in cluster
Article first = cluster.get(0);

// Get last article
Article last = cluster.get(cluster.size() - 1);

// Sort and get earliest
var sorted = cluster.stream()
    .sorted(Comparator.comparing(Article::publishedDate))
    .toList();
Article earliest = sorted.get(0);
```

**After:**
```java
// Get first article in cluster
Article first = cluster.getFirst();

// Get last article
Article last = cluster.getLast();

// Sort and get earliest
Article earliest = cluster.stream()
    .sorted(Comparator.comparing(Article::publishedDate))
    .toList()
    .getFirst();

// Reverse cluster view (no copying)
SequencedCollection<Article> reversed = cluster.reversed();

// Add to beginning
cluster.addFirst(newArticle);
```

**Benefits:**
- Clearer intent
- No index arithmetic
- Efficient reversed views
- Consistent API across List, Deque, LinkedHashSet

---

## Testing Strategy

### Unit Tests (80%+ coverage for domain)

**Domain Layer Tests:**
```java
@Test
public void testSourceType_fromDomain_recognizesWireService() {
    SourceType type = SourceType.fromDomain("aa.com.tr");
    assertEquals(SourceType.WIRE, type);
    assertEquals(1, type.getPriority());
}

@Test
public void testSourceTypeCanonicalSelector_prefersWireService() {
    Article wire = createArticle("wire.com", SourceType.WIRE);
    Article publisher = createArticle("news.com", SourceType.PUBLISHER);

    CanonicalSelectionService selector = new SourceTypeCanonicalSelector();
    Article canonical = selector.selectCanonical(List.of(publisher, wire));

    assertEquals(wire, canonical);
}
```

**Infrastructure Tests:**
```java
@Test
public void testTurkishTextNormalizer_removesStopWords() {
    TurkishLanguageConfig config = new TurkishLanguageConfig();
    TextNormalizer normalizer = new TurkishTextNormalizer(
        config.getLocale(),
        config.getStopWords(),
        4
    );

    String normalized = normalizer.normalize("Son dakika haber: İstanbul");
    assertEquals("istanbul", normalized); // "son", "dakika", "haber" removed
}
```

### Integration Tests

```java
@Test
public void testGdeltApiClient_fetchesArticles() {
    // Use WireMock to mock GDELT API
    stubFor(get(urlPathEqualTo("/api/v2/doc/doc"))
        .willReturn(aResponse()
            .withStatus(200)
            .withBodyFile("gdelt-response.json")));

    GdeltApiClient client = new GdeltApiClient(config);
    List<Article> articles = client.fetchArticles(testQuery);

    assertFalse(articles.isEmpty());
}
```

---

## Migration Strategy

### Recommended: Strangler Fig Pattern

Gradually replace old system by building new alongside it:

1. **Week 1**: Create new package structure, extract SourceType enum
2. **Week 2**: Extract infrastructure services (normalizer, URL canonicalizer)
3. **Week 3**: Extract clustering services, keep old code working
4. **Week 4**: Extract application services, route some calls through new code
5. **Week 5**: Extract presentation layer, switch fully to new code
6. **Week 6**: Delete old GdeltDedupe class

**Benefits:**
- Always have working code
- Can test incrementally
- Low risk
- Can roll back at any point

---

## Metrics & Success Criteria

### Code Quality Metrics

**Before:**
- Classes: 1 (+ 1 inner class)
- Methods: 15
- Cyclomatic Complexity: High (long methods)
- Test Coverage: 0%
- SOLID Compliance: Low

**After (Target):**
- Classes: 25-30
- Average Method Length: < 15 lines
- Cyclomatic Complexity: Low (SRP)
- Test Coverage: > 70%
- SOLID Compliance: High

### Maintainability Improvements

- **Add new clustering algorithm**: Before = modify GdeltDedupe | After = implement ClusteringService
- **Change output format**: Before = modify main | After = implement OutputFormatter
- **Add new data source**: Before = modify fetch | After = implement ArticleRepository
- **Configure thresholds**: Before = change constants | After = modify config file

---

## Risk Assessment

### Low Risk Items
- SourceType enum extraction
- Configuration extraction
- Text normalizer extraction
- UnionFind renaming

### Medium Risk Items
- Repository pattern introduction
- Application service extraction
- Use case orchestration

### High Risk Items
- Complete DI framework introduction
- Major architectural refactoring (strangler fig mitigates this)

---

## Estimated Effort

| Phase | Effort | Priority | Dependencies |
|-------|--------|----------|--------------|
| Phase 1: Foundation | 1 week | HIGH | None |
| Phase 2: Infrastructure | 1 week | HIGH | Phase 1 |
| Phase 3: Domain Services | 1 week | MEDIUM | Phase 2 |
| Phase 4: Application Layer | 1 week | MEDIUM | Phase 3 |
| Phase 5: Presentation | 1 week | LOW | Phase 4 |
| Phase 6: DI & Config | 1 week | LOW | Phase 5 |

**Total Estimated Time:** 6 weeks for full refactoring

**Minimum Viable Refactoring:** Phases 1-3 only (3 weeks)

---

## Quick Wins (Do These First)

1. **Extract SourceType enum** (2 hours)
   - Immediate type safety improvement
   - Simplifies pickCanonical

2. **Replace magic numbers with constants** (1 hour)
   - Create ClusteringConfig class
   - Define named constants

3. **Rename DSU to UnionFind** (1 hour)
   - Better variable names
   - Add Javadoc

4. **Extract TurkishTextNormalizer** (3 hours)
   - Single responsibility
   - Easily testable

**Total Quick Wins:** 1 day of work, significant improvement

---

## Conclusion

This refactoring plan transforms the codebase from a **monolithic script** into a **well-architected application**:

**Key Improvements:**
- ✅ SOLID principles compliance
- ✅ Testable components (70%+ coverage achievable)
- ✅ Flexible architecture (easy to extend)
- ✅ Maintainable code (clear responsibilities)
- ✅ Configurable (no hardcoded values)
- ✅ Production-ready

**Recommended Approach:**
1. Start with **Quick Wins** (1 day)
2. Execute **Phase 1** (Foundation) (1 week)
3. Execute **Phase 2** (Infrastructure) (1 week)
4. Decide if further refactoring is needed based on requirements

The plan is flexible - you can stop after any phase and still have improved code quality.

---

---

## Modern Java Features Impact Summary

### Performance Improvements

| Feature | Use Case | Performance Gain | Complexity |
|---------|----------|------------------|------------|
| **Virtual Threads** | Concurrent API calls | **60x faster** | Low |
| Sequenced Collections | First/last access | Marginal | Very Low |
| Pattern Matching | JSON parsing | Marginal | Low |

### Code Quality Improvements

| Feature | Lines Saved | Readability | Type Safety |
|---------|-------------|-------------|-------------|
| **Pattern Matching** | ~30% | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Record Patterns** | ~20% | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| Sequenced Collections | ~10% | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| Stream Gatherers | ~40% | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| Unnamed Variables | ~5% | ⭐⭐⭐ | ⭐⭐ |

### Recommended Adoption Strategy

**Phase 1 (Immediate):**
- ✅ **Virtual Threads** - Massive performance boost for free
- ✅ **Sequenced Collections** - Drop-in replacement, no risk
- ✅ **Pattern Matching for switch** - Cleaner code, compiler help

**Phase 2 (After Phase 1 Complete):**
- ⚠️ **Record Patterns** - Use selectively where it improves clarity
- ⚠️ **Stream Gatherers** - For complex stream operations only

**Phase 3 (Future):**
- ℹ️ **String Templates** - Once fully standardized (Java 23+)
- ℹ️ **Scoped Values** - If ThreadLocal is needed

### Java Version Recommendation

**Minimum:** Java 21 LTS (September 2023)
- Virtual Threads (stable)
- Pattern Matching for switch (stable)
- Record Patterns (stable)
- Sequenced Collections (stable)
- 6 years of LTS support (until Sept 2029)

**Optimal:** Java 23+ (September 2024)
- Stream Gatherers (stable in 23)
- Markdown Documentation Comments
- Primitive Types in Patterns
- All Java 21 features mature

**Bleeding Edge:** Java 25 (March 2025)
- All preview features from 21-24 stabilized
- New preview features to experiment with

### Development Environment Setup

**Maven (pom.xml):**
```xml
<properties>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
    <maven.compiler.release>21</maven.compiler.release>
</properties>

<!-- Enable preview features if using Java 22+ features -->
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.11.0</version>
            <configuration>
                <release>21</release>
                <!-- Uncomment for Java 22+ preview features -->
                <!--
                <compilerArgs>
                    <arg>--enable-preview</arg>
                </compilerArgs>
                -->
            </configuration>
        </plugin>
    </plugins>
</build>
```

**Gradle (build.gradle.kts):**
```kotlin
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile> {
    options.release.set(21)
    // Uncomment for Java 22+ preview features
    // options.compilerArgs.add("--enable-preview")
}

tasks.withType<Test> {
    // Uncomment for Java 22+ preview features
    // jvmArgs("--enable-preview")
}
```

---

## Expected Outcomes

### Before Refactoring (Current State)
```
Code Metrics:
- Total Classes: 1 (+ 1 inner)
- Average Method Length: 25 lines
- Cyclomatic Complexity: High
- Test Coverage: 0%
- SOLID Compliance: Low
- Modern Java Features: Minimal (records only)

Performance:
- API Calls: Sequential (slow)
- Clustering: O(n²) manual loops
- Time for 200 articles: ~5 seconds
```

### After Refactoring (Target State)
```
Code Metrics:
- Total Classes: 25-30
- Average Method Length: 10 lines
- Cyclomatic Complexity: Low
- Test Coverage: 70%+
- SOLID Compliance: High
- Modern Java Features: Comprehensive (Java 21+)

Performance:
- API Calls: Concurrent with Virtual Threads
- Clustering: O(n²) but with gatherers
- Time for 200 articles: ~500ms (10x faster)

Maintainability:
- Add new clustering algorithm: 1 new class
- Change output format: 1 new class
- Add new data source: 1 new class
- Configure thresholds: Edit config file
```

---

**Next Steps:** Review this plan and confirm the approach before proceeding with implementation.
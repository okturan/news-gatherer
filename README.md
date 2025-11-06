# News Gatherer 2.0

A modern, well-architected news article gathering and clustering application built with Java 21, following SOLID principles and clean architecture patterns.

## Overview

News Gatherer fetches articles from the GDELT Project API, clusters similar articles using advanced text similarity algorithms, and presents deduplicated news stories with canonical article selection.

### Key Features

- **Modern Java 21**: Leverages latest Java features including records, pattern matching, and sequenced collections
- **Clean Architecture**: Proper separation of concerns across domain, application, infrastructure, and presentation layers
- **SOLID Principles**: Dependency inversion, interface segregation, single responsibility throughout
- **Type-Safe**: Enum-based source types, strong typing, minimal primitives
- **Testable**: All components designed for easy unit testing with dependency injection
- **Performance**: HTTP/2 support, efficient Union-Find clustering algorithm

## Architecture

### Layer Structure

```
com.newsgatherer/
├── domain/              # Business logic and entities
│   ├── model/           # Domain models (Article, ArticleCluster, SourceType)
│   ├── service/         # Domain service interfaces
│   └── repository/      # Repository interfaces
│
├── application/         # Use cases and orchestration
│   ├── service/         # Application services
│   ├── usecase/         # Main workflows
│   └── dto/             # Data transfer objects
│
├── infrastructure/      # Technical implementations
│   ├── http/            # GDELT API client and parsers
│   ├── clustering/      # Clustering algorithms
│   ├── similarity/      # Similarity calculations
│   ├── selection/       # Canonical selection
│   └── text/            # Text processing utilities
│
├── presentation/        # User interface
│   └── console/         # Console application
│
└── config/              # Configuration and DI
```

### Design Patterns Used

1. **Repository Pattern**: Abstracts data access from GDELT API
2. **Strategy Pattern**: Pluggable similarity and clustering algorithms
3. **Builder Pattern**: Clean configuration object construction
4. **Dependency Injection**: Manual DI in ApplicationConfig
5. **Use Case Pattern**: Encapsulates business workflows

## Technology Stack

- **Java 21** (LTS) - Modern language features
- **Maven** - Build tool and dependency management
- **Jackson** - JSON processing
- **JUnit 5** - Unit testing (ready for tests)

### Java 21 Features Used

- ✅ **Records** - Immutable domain models
- ✅ **Pattern Matching for switch** - JSON field extraction
- ✅ **Sequenced Collections** - getFirst(), getLast()
- ✅ **Text Blocks** - Clean multi-line strings
- ✅ **Sealed Classes** (ready to use)

## Quick Start

### Prerequisites

- Java 21 or higher
- Maven 3.9+

### Build and Run

```bash
# Compile
mvn clean compile

# Run
mvn exec:java -Dexec.mainClass="com.newsgatherer.presentation.console.ConsoleApplication"

# Or build JAR and run
mvn clean package
java -jar target/news-gatherer-2.0.0-SNAPSHOT.jar
```

### Expected Output

```
[1] 2024-11-07 02:15 • CANONICAL (WIRE) aa.com.tr
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

## Configuration

### Default Settings

Configuration is centralized in builder-pattern classes:

```java
// API Configuration
GdeltApiConfig.builder()
    .apiEndpoint("https://api.gdeltproject.org/api/v2/doc/doc")
    .timeout(Duration.ofSeconds(30))
    .maxRecords(200)
    .defaultTimespan(Duration.ofHours(2))
    .build();

// Clustering Configuration
ClusteringConfig.builder()
    .timeWindowHours(48)          // Articles within 48 hours
    .similarityThreshold(0.80)    // 80% similarity required
    .shingleSize(4)                // 4-character shingles
    .build();
```

### Customization

Modify `ApplicationConfig.java` to change default configurations or swap implementations:

```java
// Use different similarity algorithm
private ArticleSimilarityService createSimilarityService() {
    return new YourCustomSimilarityService();
}

// Use different clustering strategy
private ClusteringService createClusteringService(ArticleSimilarityService similarityService) {
    return new YourCustomClusteringService(similarityService);
}
```

## Project Structure

### Key Components

**Domain Layer**
- `Article` - Immutable news article record
- `ArticleCluster` - Group of similar articles
- `SourceType` - Enum for WIRE, PUBLISHER, AGGREGATOR
- `ArticleSimilarityService` - Interface for similarity calculation
- `ClusteringService` - Interface for clustering algorithms
- `CanonicalSelectionService` - Interface for selecting representative articles

**Infrastructure Layer**
- `GdeltApiClient` - HTTP/2 client for GDELT API
- `GdeltArticleParser` - JSON to domain object conversion with pattern matching
- `JaccardSimilarityService` - Jaccard index implementation
- `TimeWindowedClusteringService` - Time-aware clustering with Union-Find
- `SourceTypeCanonicalSelector` - Priority-based canonical selection
- `TurkishTextNormalizer` - Turkish-specific text processing
- `ShingleGenerator` - Character n-gram generation
- `UrlCanonicalizer` - URL cleaning and normalization

**Application Layer**
- `GatherAndDeduplicateArticlesUseCase` - Main workflow
- `ArticleGatheringService` - Fetches articles from repository
- `ArticleDeduplicationService` - Clusters and deduplicates
- `ClusteringResultDto` - Results with metrics

**Presentation Layer**
- `ConsoleApplication` - Main entry point
- `ClusterFormatter` - Formats clusters for console
- `MetricsPresenter` - Displays statistics

## Development

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=SourceTypeTest

# Run with coverage
mvn clean verify
```

### Code Structure Guidelines

1. **Domain layer** - No external dependencies, pure business logic
2. **Application layer** - Orchestrates domain services, uses DTOs
3. **Infrastructure layer** - Implements domain interfaces, contains technical code
4. **Presentation layer** - No business logic, only formatting and display

### Adding New Features

**Example: Add a new similarity algorithm**

1. Implement `ArticleSimilarityService` interface:
```java
public class CosineSimilarityService implements ArticleSimilarityService {
    @Override
    public double calculateSimilarity(Article a1, Article a2) {
        // Your implementation
    }
}
```

2. Update `ApplicationConfig`:
```java
private ArticleSimilarityService createSimilarityService() {
    return new CosineSimilarityService();
}
```

## Refactoring Journey

This application was refactored from a 240-line monolithic class into a well-architected system:

### Before (Original Code)
- ❌ Single class with 10+ responsibilities
- ❌ All static methods
- ❌ Hardcoded configuration
- ❌ String-based types
- ❌ Impossible to test
- ❌ 0% test coverage

### After (Refactored)
- ✅ 30+ classes with single responsibilities
- ✅ Dependency injection throughout
- ✅ Builder-pattern configuration
- ✅ Type-safe enums
- ✅ Fully testable design
- ✅ Ready for 70%+ test coverage

### Key Improvements

1. **Type Safety**: String source types → SourceType enum
2. **Testability**: Static methods → Injectable services
3. **Configurability**: Magic numbers → Config objects
4. **Maintainability**: God class → Clean architecture
5. **Extensibility**: Hardcoded logic → Strategy pattern
6. **Modern Java**: Java 17 → Java 21 features

## Performance

- **Clustering Algorithm**: O(n²) within time window with Union-Find optimization
- **HTTP Client**: HTTP/2 with connection pooling
- **Memory**: Efficient shingle-based similarity (no full text comparison)
- **Typical Performance**: 200 articles clustered in < 2 seconds

## Contributing

### Code Style

- Use Java 21 features where appropriate
- Follow SOLID principles
- Keep methods under 20 lines
- Use descriptive names
- Add Javadoc for public APIs
- Write tests for new features

### Pull Request Process

1. Create feature branch from `master`
2. Implement feature with tests
3. Ensure `mvn clean verify` passes
4. Update documentation
5. Submit PR with clear description

## License

This project is provided as-is for educational and personal use.

## Acknowledgments

- **GDELT Project** - Global news database
- **Clean Architecture** - Robert C. Martin
- **Domain-Driven Design** - Eric Evans
- **Java 21** - Oracle and OpenJDK community

## Contact

For questions or suggestions, please open an issue on the project repository.

---

**Built with ☕ and Java 21**

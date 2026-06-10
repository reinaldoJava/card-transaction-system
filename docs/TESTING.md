# Testing & Code Coverage

## Running Tests

### All Tests
```bash
mvn clean test
```

### Specific Test Class
```bash
mvn test -Dtest=CardNumberTest
```

### With Coverage Report
```bash
mvn clean test jacoco:report
```

## Coverage Report

Jacoco generates coverage reports automatically during test phase.

**View Report:**
```bash
open target/site/jacoco/index.html  # macOS
xdg-open target/site/jacoco/index.html  # Linux
start target/site/jacoco/index.html  # Windows
```

## Coverage Thresholds

Current gates (enforced by `mvn test`):

| Metric | Threshold | Reason |
|--------|-----------|--------|
| Line Coverage | 70% | Ensures core logic is tested |
| Branch Coverage | 60% | Ensures main paths are covered |

**If test fails due to coverage:**
```bash
mvn jacoco:check  # Shows detailed report
```

## Coverage by Package

```
src/main/java/com/empresa/cardtransactionsystem/
├── domain/              → High coverage (DDD entities)
├── application/         → High coverage (use cases)
├── adapters/inbound/    → Medium coverage (REST)
└── adapters/outbound/   → Medium coverage (external services)
```

## Current Coverage Status

Run this to see current metrics:
```bash
mvn clean test && open target/site/jacoco/index.html
```

## CI/CD Integration

Jacoco will **fail the build** if coverage drops below thresholds.

To increase threshold:
1. Edit `pom.xml` → `<minimum>0.70</minimum>` for LINE
2. Edit `pom.xml` → `<minimum>0.60</minimum>` for BRANCH
3. Run `mvn clean test` to validate

## Test Structure

Tests follow this pattern:

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("Feature Description")
class SomeTest {
    
    @Mock private DependencyPort dependency;
    
    @BeforeEach
    void setUp() {
        // Arrange
    }
    
    @Test
    @DisplayName("should do something specific")
    void shouldDoSomething() {
        // Given
        // When
        // Then
        verify(dependency).method();
    }
}
```

## Skipping Coverage Check (Not Recommended)

```bash
mvn clean test -DskipTests=false -Djacoco.skip=true
```

Use only if temporarily debugging a failing build.

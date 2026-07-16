# CBR Active Memory Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #152 — feat: CBR active memory management — relevance decay, supersession, case compaction
**Issue group:** #93, #152

**Goal:** Fix temporal decay placement (decorator instead of store-level), add decay variants, implement case supersession with audit metadata.

**Architecture:** Temporal decay moves from inline store logic to a `@Decorator @Priority(80)` that applies after reranking. `ScoredCbrCase` gains `Instant storedAt` as domain metadata. Supersession adds `supersede()`/`reinstate()` to the SPI with store-level filter exclusion. All existing decorators migrate from constructor-based `ScoredCbrCase` creation to `with*` methods to preserve `storedAt` through the chain.

**Tech Stack:** Java 21, Quarkus CDI decorators, Qdrant gRPC client, JPA/Hibernate, JUnit 5 + AssertJ

## Global Constraints

- Java 21 language level on Java 26 JVM
- `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install` for builds
- Use `mvn` not `./mvnw`
- Pre-release: breaking changes cost nothing
- Reactive parity required for all SPI changes (blocking + `Uni<>` variants)
- All decorator methods that don't enhance a capability delegate directly to the delegate

---

### Task 1: `ScoredCbrCase` — add `storedAt` field and `with*` methods

**Files:**
- Modify: `memory-api/src/main/java/io/casehub/neocortex/memory/cbr/ScoredCbrCase.java`
- Test: `memory-api/src/test/java/io/casehub/neocortex/memory/cbr/ScoredCbrCaseTest.java` (create if absent)

**Interfaces:**
- Produces: `ScoredCbrCase<C>(C cbrCase, String caseId, double score, boolean reranked, Map<String,Double> featureSimilarities, Instant storedAt)` — canonical constructor with 6 fields. `withScore(double)`, `withReranked()`, `withStoredAt(Instant)` — immutable copy methods.

This is the foundation — every subsequent task depends on this record shape.

- [ ] **Step 1: Write tests for the new `storedAt` field and `with*` methods**

```java
// ScoredCbrCaseTest.java
@Test void storedAt_includedInCanonicalConstructor() {
    Instant now = Instant.now();
    var scored = new ScoredCbrCase<>(textCase(), "c1", 0.9, false, Map.of(), now);
    assertThat(scored.storedAt()).isEqualTo(now);
}

@Test void storedAt_nullableAndDefaultsToNull() {
    var scored = new ScoredCbrCase<>(textCase(), "c1", 0.9, false, Map.of(), null);
    assertThat(scored.storedAt()).isNull();
}

@Test void convenienceConstructors_defaultStoredAtToNull() {
    assertThat(new ScoredCbrCase<>(textCase(), "c1", 0.9).storedAt()).isNull();
    assertThat(new ScoredCbrCase<>(textCase(), 0.9).storedAt()).isNull();
    assertThat(new ScoredCbrCase<>(textCase(), 0.9, false).storedAt()).isNull();
    assertThat(new ScoredCbrCase<>(textCase(), 0.9, false, Map.of()).storedAt()).isNull();
}

@Test void withScore_preservesAllFieldsExceptScore() {
    Instant now = Instant.now();
    var original = new ScoredCbrCase<>(textCase(), "c1", 0.9, true, Map.of("f", 0.8), now);
    var modified = original.withScore(0.5);
    assertThat(modified.score()).isEqualTo(0.5);
    assertThat(modified.cbrCase()).isSameAs(original.cbrCase());
    assertThat(modified.caseId()).isEqualTo("c1");
    assertThat(modified.reranked()).isTrue();
    assertThat(modified.featureSimilarities()).isEqualTo(Map.of("f", 0.8));
    assertThat(modified.storedAt()).isEqualTo(now);
}

@Test void withReranked_preservesAllFieldsIncludingStoredAt() {
    Instant now = Instant.now();
    var original = new ScoredCbrCase<>(textCase(), "c1", 0.9, false, Map.of("f", 0.8), now);
    var reranked = original.withReranked();
    assertThat(reranked.reranked()).isTrue();
    assertThat(reranked.score()).isEqualTo(0.9);
    assertThat(reranked.storedAt()).isEqualTo(now);
    assertThat(reranked.featureSimilarities()).isEqualTo(Map.of("f", 0.8));
}

private TextualCbrCase textCase() {
    return new TextualCbrCase("problem", "solution", null, null);
}
```

- [ ] **Step 2: Run tests — verify they fail**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl memory-api -Dtest=ScoredCbrCaseTest -DfailIfNoTests=false
```

Expected: compilation errors — `storedAt` parameter doesn't exist, `withScore()` doesn't exist.

- [ ] **Step 3: Update `ScoredCbrCase` record**

Use `ide_edit_member` to replace the record declaration. The new canonical constructor has 6 fields. All existing convenience constructors delegate to canonical with `storedAt = null`. Replace `withReranked()` to use all 6 fields. Add `withScore(double)`.

New record declaration (replace entire record):
```java
public record ScoredCbrCase<C extends CbrCase>(C cbrCase, String caseId, double score, boolean reranked,
                                               Map<String, Double> featureSimilarities, Instant storedAt) {
    public ScoredCbrCase {
        Objects.requireNonNull(cbrCase, "cbrCase required");
        if (!(score >= -1.0 && score <= 1.0)) {
            throw new IllegalArgumentException("score must be in [-1,1], got: " + score);
        }
        featureSimilarities = featureSimilarities != null ? Map.copyOf(featureSimilarities) : Map.of();
    }

    public ScoredCbrCase(C cbrCase, String caseId, double score) {
        this(cbrCase, caseId, score, false, Map.of(), null);
    }

    public ScoredCbrCase(C cbrCase, double score) {
        this(cbrCase, null, score, false, Map.of(), null);
    }

    public ScoredCbrCase(C cbrCase, double score, boolean reranked) {
        this(cbrCase, null, score, reranked, Map.of(), null);
    }

    public ScoredCbrCase(C cbrCase, double score, boolean reranked,
                         Map<String, Double> featureSimilarities) {
        this(cbrCase, null, score, reranked, featureSimilarities, null);
    }

    public ScoredCbrCase<C> withScore(double newScore) {
        return new ScoredCbrCase<>(cbrCase, caseId, newScore, reranked, featureSimilarities, storedAt);
    }

    public ScoredCbrCase<C> withReranked() {
        return new ScoredCbrCase<>(cbrCase, caseId, score, true, featureSimilarities, storedAt);
    }
}
```

Add `import java.time.Instant;` to the file.

- [ ] **Step 4: Fix compilation across the project**

The canonical constructor gains a 6th param. All callers using the 5-arg canonical constructor `new ScoredCbrCase<>(cbrCase, caseId, score, reranked, featureSimilarities)` must add `, null` or `, storedAt`. Use `ide_find_references` on the `ScoredCbrCase` constructor to find all call sites. The convenience constructors (3-arg, 2-arg, etc.) are unchanged.

Key call sites that construct with 5 args (add `, null` for now — stores will populate properly in Task 3):
- `OutcomeWeightingCbrCaseMemoryStore:62-63` → replace with `scored.withScore(newScore)`
- `RerankingCbrCaseMemoryStore:101` → replace with `original.withScore(sigmoidScore).withReranked()`
- `InMemoryCbrCaseMemoryStore:135-136` → add storedAt from `stored.storedAt()`
- `JpaCbrCaseMemoryStore:150-151` → add storedAt from `entity.storedAt`
- `QdrantCbrCaseMemoryStore` multiple sites → add storedAt extraction (Task 3)

For OutcomeWeighting, use `ide_replace_member` on `retrieveSimilar`:
```java
// Replace the new ScoredCbrCase<> constructor call with:
weighted.add(scored.withScore(newScore));
```

For Reranking, use `ide_replace_member` on `retrieveSimilar`:
```java
// Replace line 101 with:
results.add(original.withScore(sigmoidScore).withReranked());
```

Also fix the reactive counterparts:
- `ReactiveOutcomeWeightingCbrCaseMemoryStore` — same `withScore()` migration
- `ReactiveRerankingCbrCaseMemoryStore` — same `withScore().withReranked()` migration

- [ ] **Step 5: Run full build to verify compilation**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 6: Run tests**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl memory-api -Dtest=ScoredCbrCaseTest
```

Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/neocortex add memory-api/src/
git -C /Users/mdproctor/claude/casehub/neocortex add memory/src/
git -C /Users/mdproctor/claude/casehub/neocortex add memory-cbr-crossencoder/src/
git -C /Users/mdproctor/claude/casehub/neocortex add memory-cbr-inmem/src/
git -C /Users/mdproctor/claude/casehub/neocortex add memory-cbr-jpa/src/
git -C /Users/mdproctor/claude/casehub/neocortex add memory-cbr-tracking/src/
git -C /Users/mdproctor/claude/casehub/neocortex add memory-qdrant/src/
```

```
feat(#152): add storedAt to ScoredCbrCase, migrate decorators to with* methods
```

---

### Task 2: `TemporalDecay` — null guard on `HalfLife`, add `Linear` and `Step` variants

**Files:**
- Modify: `memory-api/src/main/java/io/casehub/neocortex/memory/cbr/TemporalDecay.java`
- Modify: `memory-api/src/test/java/io/casehub/neocortex/memory/cbr/TemporalDecayTest.java` (create if absent)

**Interfaces:**
- Produces: `TemporalDecay.Linear(Duration zeroAt)` — `factor()` returns `max(0, 1 - age/zeroAt)`. `TemporalDecay.Step(Duration cutoff, double afterCutoff)` — `factor()` returns `1.0` before cutoff, `afterCutoff` after.

- [ ] **Step 1: Write tests**

```java
// TemporalDecayTest.java

// HalfLife null guard
@Test void halfLife_nullStoredAt_returnsOne() {
    var decay = new TemporalDecay.HalfLife(Duration.ofHours(1));
    assertThat(decay.factor(null, Instant.now())).isEqualTo(1.0);
}

@Test void halfLife_normalDecay() {
    var decay = new TemporalDecay.HalfLife(Duration.ofHours(1));
    Instant now = Instant.now();
    Instant oneHourAgo = now.minus(Duration.ofHours(1));
    assertThat(decay.factor(oneHourAgo, now)).isCloseTo(0.5, within(0.001));
}

// Linear
@Test void linear_nullStoredAt_returnsOne() {
    var decay = new TemporalDecay.Linear(Duration.ofDays(30));
    assertThat(decay.factor(null, Instant.now())).isEqualTo(1.0);
}

@Test void linear_zeroAge_returnsOne() {
    var decay = new TemporalDecay.Linear(Duration.ofDays(30));
    Instant now = Instant.now();
    assertThat(decay.factor(now, now)).isEqualTo(1.0);
}

@Test void linear_halfwayToZero() {
    var decay = new TemporalDecay.Linear(Duration.ofDays(30));
    Instant now = Instant.now();
    Instant fifteenDaysAgo = now.minus(Duration.ofDays(15));
    assertThat(decay.factor(fifteenDaysAgo, now)).isCloseTo(0.5, within(0.001));
}

@Test void linear_atZeroAt_returnsZero() {
    var decay = new TemporalDecay.Linear(Duration.ofDays(30));
    Instant now = Instant.now();
    Instant thirtyDaysAgo = now.minus(Duration.ofDays(30));
    assertThat(decay.factor(thirtyDaysAgo, now)).isCloseTo(0.0, within(0.001));
}

@Test void linear_beyondZeroAt_returnsZero() {
    var decay = new TemporalDecay.Linear(Duration.ofDays(30));
    Instant now = Instant.now();
    Instant sixtyDaysAgo = now.minus(Duration.ofDays(60));
    assertThat(decay.factor(sixtyDaysAgo, now)).isEqualTo(0.0);
}

@Test void linear_negativeAge_returnsOne() {
    var decay = new TemporalDecay.Linear(Duration.ofDays(30));
    Instant now = Instant.now();
    Instant future = now.plus(Duration.ofHours(1));
    assertThat(decay.factor(future, now)).isEqualTo(1.0);
}

@Test void linear_invalidZeroAt_throws() {
    assertThatThrownBy(() -> new TemporalDecay.Linear(Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new TemporalDecay.Linear(Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalArgumentException.class);
}

// Step
@Test void step_nullStoredAt_returnsOne() {
    var decay = new TemporalDecay.Step(Duration.ofDays(7), 0.3);
    assertThat(decay.factor(null, Instant.now())).isEqualTo(1.0);
}

@Test void step_beforeCutoff_returnsOne() {
    var decay = new TemporalDecay.Step(Duration.ofDays(7), 0.3);
    Instant now = Instant.now();
    Instant threeDaysAgo = now.minus(Duration.ofDays(3));
    assertThat(decay.factor(threeDaysAgo, now)).isEqualTo(1.0);
}

@Test void step_afterCutoff_returnsAfterCutoff() {
    var decay = new TemporalDecay.Step(Duration.ofDays(7), 0.3);
    Instant now = Instant.now();
    Instant tenDaysAgo = now.minus(Duration.ofDays(10));
    assertThat(decay.factor(tenDaysAgo, now)).isEqualTo(0.3);
}

@Test void step_negativeAge_returnsOne() {
    var decay = new TemporalDecay.Step(Duration.ofDays(7), 0.3);
    Instant now = Instant.now();
    assertThat(decay.factor(now.plus(Duration.ofHours(1)), now)).isEqualTo(1.0);
}

@Test void step_invalidCutoff_throws() {
    assertThatThrownBy(() -> new TemporalDecay.Step(Duration.ZERO, 0.5))
        .isInstanceOf(IllegalArgumentException.class);
}

@Test void step_afterCutoffOutOfRange_throws() {
    assertThatThrownBy(() -> new TemporalDecay.Step(Duration.ofDays(1), -0.1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new TemporalDecay.Step(Duration.ofDays(1), 1.1))
        .isInstanceOf(IllegalArgumentException.class);
}
```

- [ ] **Step 2: Run tests — verify they fail**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl memory-api -Dtest=TemporalDecayTest -DfailIfNoTests=false
```

- [ ] **Step 3: Implement**

Use `ide_edit_member` to update `HalfLife.factor()` with null guard. Use `ide_insert_member` to add `Linear` and `Step` records to the sealed interface.

```java
// Updated HalfLife.factor():
@Override
public double factor(Instant storedAt, Instant now) {
    if (storedAt == null) return 1.0;
    double ageSeconds = Duration.between(storedAt, now).toSeconds();
    if (ageSeconds <= 0) return 1.0;
    double halfLifeSeconds = halfLife.toSeconds();
    return Math.pow(0.5, ageSeconds / halfLifeSeconds);
}

// New Linear record:
record Linear(Duration zeroAt) implements TemporalDecay {
    public Linear {
        Objects.requireNonNull(zeroAt, "zeroAt required");
        if (zeroAt.isNegative() || zeroAt.isZero()) {
            throw new IllegalArgumentException("zeroAt must be positive, got " + zeroAt);
        }
    }

    @Override
    public double factor(Instant storedAt, Instant now) {
        if (storedAt == null) return 1.0;
        double ageSeconds = Duration.between(storedAt, now).toSeconds();
        if (ageSeconds <= 0) return 1.0;
        double zeroAtSeconds = zeroAt.toSeconds();
        return Math.max(0.0, 1.0 - ageSeconds / zeroAtSeconds);
    }
}

// New Step record:
record Step(Duration cutoff, double afterCutoff) implements TemporalDecay {
    public Step {
        Objects.requireNonNull(cutoff, "cutoff required");
        if (cutoff.isNegative() || cutoff.isZero()) {
            throw new IllegalArgumentException("cutoff must be positive, got " + cutoff);
        }
        if (afterCutoff < 0.0 || afterCutoff > 1.0) {
            throw new IllegalArgumentException("afterCutoff must be in [0,1], got " + afterCutoff);
        }
    }

    @Override
    public double factor(Instant storedAt, Instant now) {
        if (storedAt == null) return 1.0;
        double ageSeconds = Duration.between(storedAt, now).toSeconds();
        if (ageSeconds <= 0) return 1.0;
        return ageSeconds < cutoff.toSeconds() ? 1.0 : afterCutoff;
    }
}
```

Update the sealed interface permits clause: `sealed interface TemporalDecay permits TemporalDecay.HalfLife, TemporalDecay.Linear, TemporalDecay.Step`.

- [ ] **Step 4: Run tests**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl memory-api -Dtest=TemporalDecayTest
```

Expected: all pass.

- [ ] **Step 5: Commit**

```
feat(#152): add Linear/Step temporal decay variants, null guard on HalfLife
```

---

### Task 3: Stores populate `storedAt`, remove store-level decay

**Files:**
- Modify: `memory-cbr-inmem/src/main/java/io/casehub/neocortex/memory/cbr/inmem/InMemoryCbrCaseMemoryStore.java`
- Modify: `memory-cbr-jpa/src/main/java/io/casehub/neocortex/memory/cbr/jpa/JpaCbrCaseMemoryStore.java`
- Modify: `memory-qdrant/src/main/java/io/casehub/neocortex/memory/cbr/qdrant/QdrantCbrCaseMemoryStore.java`
- Test: existing contract tests validate `storedAt` population (added in Task 5)

**Interfaces:**
- Consumes: `ScoredCbrCase` with `storedAt` field (Task 1)
- Produces: All stores return `ScoredCbrCase` instances with `storedAt` populated

- [ ] **Step 1: `InMemoryCbrCaseMemoryStore` — remove decay, populate `storedAt`**

In `retrieveSimilar()` (lines 125-145):
1. Remove the decay block (lines 131-133): `if (query.temporalDecay() != null) { score *= ... }`
2. Change the `ScoredCbrCase` constructor call to include `stored.storedAt()`:

```java
// Before:
candidates.add(new ScoredCbrCase<>((C) stored.cbrCase(), stored.caseId(),
                                   score, false, breakdown.featureSimilarities()));
// After:
candidates.add(new ScoredCbrCase<>((C) stored.cbrCase(), stored.caseId(),
                                   score, false, breakdown.featureSimilarities(), stored.storedAt()));
```

Use `ide_replace_member` on `retrieveSimilar`.

- [ ] **Step 2: `JpaCbrCaseMemoryStore` — remove decay, populate `storedAt`**

In `retrieveSimilar()` (lines 97-157):
1. Remove the decay block (lines 145-147): `if (query.temporalDecay() != null) { score *= ... }`
2. Change the `ScoredCbrCase` constructor call to include `entity.storedAt`:

```java
// Before:
candidates.add(new ScoredCbrCase<>((C) reconstructed, entity.caseId,
                                   score, false, breakdown.featureSimilarities()));
// After:
candidates.add(new ScoredCbrCase<>((C) reconstructed, entity.caseId,
                                   score, false, breakdown.featureSimilarities(), entity.storedAt));
```

Use `ide_replace_member` on `retrieveSimilar`.

- [ ] **Step 3: `QdrantCbrCaseMemoryStore` — populate `storedAt` in all retrieval paths**

Three retrieval methods construct `ScoredCbrCase`:

**`retrieveFeatureOnly()`** (line 304): Extract `_stored_at` from the point payload during `reconstructAll()`. The `ReconstructedCandidate` record needs a `storedAt` field. Use `ide_edit_member` to update the record:

```java
private record ReconstructedCandidate<C extends CbrCase>(
    String pointId, C cbrCase, float vectorScore, String caseId, Instant storedAt) {}
```

Update `mergePoints()` and `reconstructAll()` to extract `_stored_at`:
```java
long storedAtMillis = point.getPayloadOrDefault("_stored_at",
    ValueFactory.value(0L)).getIntegerValue();
Instant storedAt = storedAtMillis > 0 ? Instant.ofEpochMilli(storedAtMillis) : null;
```

Then pass `rc.storedAt()` when constructing `ScoredCbrCase` in all three retrieval methods.

**`retrieveSemanticOnly()`** (line 322): Same extraction from the point's payload map.

**`retrieveHybrid()`** (line 340+): Uses `ReconstructedCandidate` which already carries `storedAt` after the record change. The `FusionEntry` inner record also needs `storedAt`. Pass it through to the final `ScoredCbrCase`.

- [ ] **Step 4: Build and run existing tests**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install
```

Expected: BUILD SUCCESS, all existing tests pass. The contract tests for temporal decay may need adjustment since store-level decay is removed — the in-memory contract test subclass (where decay was tested) will now show decay having no effect at store level. This is correct — decay is now the decorator's responsibility (Task 4).

- [ ] **Step 5: Commit**

```
feat(#152): populate storedAt in all stores, remove store-level temporal decay
```

---

### Task 4: `TemporalDecayCbrCaseMemoryStore` decorator + reactive parity

**Files:**
- Create: `memory/src/main/java/io/casehub/neocortex/memory/cbr/runtime/TemporalDecayCbrCaseMemoryStore.java`
- Create: `memory/src/main/java/io/casehub/neocortex/memory/cbr/runtime/ReactiveTemporalDecayCbrCaseMemoryStore.java`
- Create: `memory/src/test/java/io/casehub/neocortex/memory/cbr/runtime/TemporalDecayCbrCaseMemoryStoreTest.java`
- Create: `memory/src/test/java/io/casehub/neocortex/memory/cbr/runtime/ReactiveTemporalDecayCbrCaseMemoryStoreTest.java`

**Interfaces:**
- Consumes: `ScoredCbrCase.storedAt()` (Task 1), `TemporalDecay.factor()` (Task 2), `CbrQuery.temporalDecay()` (existing)
- Produces: `@Decorator @Priority(80)` — applies temporal decay factor to retrieval scores post-reranking

- [ ] **Step 1: Write blocking decorator tests**

```java
// TemporalDecayCbrCaseMemoryStoreTest.java
@Test void nullTemporalDecay_passThrough() {
    // query with no temporalDecay → results unchanged
}

@Test void halfLife_appliesExponentialFactor() {
    // case stored 1 hour ago, HalfLife(1h) → score * 0.5
}

@Test void linear_rampToZero() {
    // case stored at zeroAt boundary → score * 0.0
}

@Test void step_thresholdBehavior() {
    // case before cutoff → score unchanged, after → score * afterCutoff
}

@Test void refiltersMinSimilarity() {
    // two results, one drops below minSimilarity after decay → excluded
}

@Test void resorts() {
    // recent case with lower score overtakes old case with higher score after decay
}

@Test void nullStoredAt_factorIsOne() {
    // ScoredCbrCase with null storedAt → score unchanged
}
```

Set up a mock/stub delegate that returns controlled `ScoredCbrCase` instances with known `storedAt` values. Use constructor injection for testing (same pattern as `OutcomeWeightingCbrCaseMemoryStoreTest`).

- [ ] **Step 2: Run tests — verify they fail**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl memory -Dtest=TemporalDecayCbrCaseMemoryStoreTest -DfailIfNoTests=false
```

- [ ] **Step 3: Implement blocking decorator**

```java
@Decorator
@Priority(80)
public class TemporalDecayCbrCaseMemoryStore implements CbrCaseMemoryStore {

    private final CbrCaseMemoryStore delegate;

    @Inject
    TemporalDecayCbrCaseMemoryStore(@Delegate @Any CbrCaseMemoryStore delegate) {
        this.delegate = delegate;
    }

    @Override
    public <C extends CbrCase> List<ScoredCbrCase<C>> retrieveSimilar(
            CbrQuery query, Class<C> caseType) {
        List<ScoredCbrCase<C>> results = delegate.retrieveSimilar(query, caseType);
        if (query.temporalDecay() == null) {
            return results;
        }
        Instant now = Instant.now();
        TemporalDecay decay = query.temporalDecay();
        List<ScoredCbrCase<C>> decayed = new ArrayList<>(results.size());
        for (var scored : results) {
            double factor = (scored.storedAt() != null)
                ? decay.factor(scored.storedAt(), now) : 1.0;
            double adjustedScore = scored.score() * factor;
            if (adjustedScore >= query.minSimilarity()) {
                decayed.add(scored.withScore(adjustedScore));
            }
        }
        decayed.sort((a, b) -> Double.compare(b.score(), a.score()));
        return Collections.unmodifiableList(decayed);
    }

    // All other methods: delegate directly
    @Override public void registerSchema(CbrFeatureSchema schema) { delegate.registerSchema(schema); }
    @Override public String store(CbrCase c, String ct, String e, MemoryDomain d, String t, String ci) { return delegate.store(c, ct, e, d, t, ci); }
    @Override public Integer erase(EraseRequest r) { return delegate.erase(r); }
    @Override public Integer eraseEntity(String e, String t) { return delegate.eraseEntity(e, t); }
    @Override public void recordOutcome(String ci, String t, CbrOutcome o) { delegate.recordOutcome(ci, t, o); }
    @Override public Integer purge(CbrRetentionPolicy p) { return delegate.purge(p); }
}
```

- [ ] **Step 4: Implement reactive decorator**

Same pattern as `ReactiveOutcomeWeightingCbrCaseMemoryStore`. Wraps `retrieveSimilar()` with `Uni` transformation. All other methods delegate.

- [ ] **Step 5: Run tests**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl memory -Dtest="TemporalDecay*Test"
```

- [ ] **Step 6: Commit**

```
feat(#152): temporal decay decorator @Priority(80) with reactive parity
```

---

### Task 5: Supersession SPI — add `supersede()`/`reinstate()` to interfaces and all implementations

**Files:**
- Modify: `memory-api/src/main/java/io/casehub/neocortex/memory/cbr/CbrCaseMemoryStore.java`
- Modify: `memory-api/src/main/java/io/casehub/neocortex/memory/cbr/ReactiveCbrCaseMemoryStore.java`
- Modify: `memory/src/main/java/io/casehub/neocortex/memory/cbr/runtime/NoOpCbrCaseMemoryStore.java`
- Modify: `memory/src/main/java/io/casehub/neocortex/memory/cbr/runtime/BlockingToReactiveCbrBridge.java`
- Modify: all 4 blocking decorators (TrendEnrichment, OutcomeWeighting, Decay, Tracking) — add delegation
- Modify: all 4 reactive decorators — add delegation
- Modify: `memory-cbr-inmem/src/main/java/io/casehub/neocortex/memory/cbr/inmem/InMemoryCbrCaseMemoryStore.java`
- Modify: `memory-cbr-jpa/src/main/java/io/casehub/neocortex/memory/cbr/jpa/JpaCbrCaseMemoryStore.java` + `CbrCaseEntity.java`
- Modify: `memory-qdrant/src/main/java/io/casehub/neocortex/memory/cbr/qdrant/QdrantCbrCaseMemoryStore.java`
- Modify: `memory-qdrant/src/main/java/io/casehub/neocortex/memory/cbr/qdrant/CbrQueryTranslator.java`
- Create: `memory-cbr-jpa/src/main/resources/db/cbr/migration/V2__add_supersession.sql`
- Test: `memory-testing/src/main/java/io/casehub/neocortex/memory/cbr/testing/CbrCaseMemoryStoreContractTest.java`

**Interfaces:**
- Produces: `void supersede(String caseId, String tenantId, @Nullable String supersedingCaseId, @Nullable String reason)`, `void reinstate(String caseId, String tenantId)` on both blocking and reactive SPIs.

This is the largest task — it touches every implementation. Work systematically: SPI first, then implementations root-to-leaf, then decorators, then contract tests.

- [ ] **Step 1: Add methods to SPI interfaces**

`CbrCaseMemoryStore` — add:
```java
void supersede(String caseId, String tenantId, String supersedingCaseId, String reason);
void reinstate(String caseId, String tenantId);
```

`ReactiveCbrCaseMemoryStore` — add:
```java
Uni<Void> supersede(String caseId, String tenantId, String supersedingCaseId, String reason);
Uni<Void> reinstate(String caseId, String tenantId);
```

Use `ide_insert_member` on each interface.

- [ ] **Step 2: Build — expect compilation failures in all implementations**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install -DskipTests 2>&1 | head -50
```

This confirms all call sites that need updating.

- [ ] **Step 3: Implement in `InMemoryCbrCaseMemoryStore`**

Add `supersededAt`, `supersedingCaseId`, `supersessionReason` fields to the `StoredCase` record. Update the `store()` method to pass null for these new fields. Add filter in `retrieveSimilar()`: `if (stored.supersededAt() != null) continue;`.

Implement `supersede()`:
```java
@Override
public void supersede(String caseId, String tenantId, String supersedingCaseId, String reason) {
    Objects.requireNonNull(caseId, "caseId required");
    Objects.requireNonNull(tenantId, "tenantId required");
    for (int i = 0; i < cases.size(); i++) {
        StoredCase sc = cases.get(i);
        if (sc.caseId().equals(caseId) && sc.tenantId().equals(tenantId)) {
            if (sc.supersededAt() != null) {
                // Already superseded — update metadata only (timestamp preserved)
                String newSupersedingId = supersedingCaseId != null ? supersedingCaseId : sc.supersedingCaseId();
                String newReason = reason != null ? reason : sc.supersessionReason();
                cases.set(i, sc.withSupersessionMetadata(sc.supersededAt(), newSupersedingId, newReason));
            } else {
                cases.set(i, sc.withSupersessionMetadata(Instant.now(), supersedingCaseId, reason));
            }
            return;
        }
    }
}
```

Implement `reinstate()`:
```java
@Override
public void reinstate(String caseId, String tenantId) {
    Objects.requireNonNull(caseId, "caseId required");
    Objects.requireNonNull(tenantId, "tenantId required");
    for (int i = 0; i < cases.size(); i++) {
        StoredCase sc = cases.get(i);
        if (sc.caseId().equals(caseId) && sc.tenantId().equals(tenantId)) {
            cases.set(i, sc.withSupersessionMetadata(null, null, null));
            return;
        }
    }
}
```

The `StoredCase` record needs a `withSupersessionMetadata()` method.

- [ ] **Step 4: Implement in `JpaCbrCaseMemoryStore`**

Add columns to `CbrCaseEntity`:
```java
public Instant supersededAt;
public String supersedingCaseId;
public String supersessionReason;
```

Create Flyway migration `V2__add_supersession.sql`:
```sql
ALTER TABLE cbr_case ADD COLUMN superseded_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE cbr_case ADD COLUMN superseding_case_id VARCHAR(255);
ALTER TABLE cbr_case ADD COLUMN supersession_reason TEXT;
CREATE INDEX cbr_case_superseded_idx ON cbr_case (superseded_at) WHERE superseded_at IS NOT NULL;
```

Add `AND e.supersededAt IS NULL` to the JPQL query in `retrieveSimilar()`.

Implement `supersede()` and `reinstate()` using JPA queries.

- [ ] **Step 5: Implement in `QdrantCbrCaseMemoryStore`**

In `supersede()`: locate point by `CbrPointBuilder.pointId(tenantId, caseType, caseId)` (same pattern as `recordOutcome`). Set payload fields `_superseded_at`, `_superseding_case_id`, `_supersession_reason`. Handle already-superseded case (preserve timestamp, update non-null metadata).

In `reinstate()`: clear those payload fields (set to null).

In `CbrQueryTranslator.toIdentityFilter()`: add `IsNull("_superseded_at")` condition:
```java
builder.addMust(ConditionFactory.isNull("_superseded_at"));
```

Delete `toFilter()` method and its tests (dead code per spec §2.3).

- [ ] **Step 6: Add delegation to all decorators and bridges**

For each blocking decorator (`TrendEnrichment`, `OutcomeWeighting`, `TemporalDecay`, `Tracking`), add:
```java
@Override public void supersede(String caseId, String tenantId, String supersedingCaseId, String reason) {
    delegate.supersede(caseId, tenantId, supersedingCaseId, reason);
}
@Override public void reinstate(String caseId, String tenantId) {
    delegate.reinstate(caseId, tenantId);
}
```

Same for reactive decorators with `Uni<Void>` wrapping.

`NoOpCbrCaseMemoryStore` — empty implementations.
`BlockingToReactiveCbrBridge` — delegate to blocking store.

- [ ] **Step 7: Write contract tests**

Add to `CbrCaseMemoryStoreContractTest`:

```java
// storedAt population
@Test void storedAt_populatedOnRetrievedCases() {
    store.registerSchema(simpleSchema());
    store.store(featureCase(Map.of("severity", number(5))), "test", ENTITY, CBR, TENANT, "c1");
    var results = store.retrieveSimilar(simpleQuery(Map.of("severity", number(5)), 10), FeatureVectorCbrCase.class);
    assertThat(results).hasSize(1);
    assertThat(results.get(0).storedAt()).isNotNull();
    assertThat(results.get(0).storedAt()).isBefore(Instant.now().plusSeconds(1));
}

// Supersession
@Test void supersede_excludesFromRetrieval() { ... }
@Test void reinstate_restoresRetrievalVisibility() { ... }
@Test void supersede_alreadySuperseded_noThrow() { ... }
@Test void reinstate_idempotent() { ... }
@Test void supersede_nonExistentCase_noOp() { ... }
@Test void supersede_eraseStillWorks() { ... }
@Test void supersede_recordOutcomeStillWorks() { ... }
@Test void supersede_purgeStillApplies() { ... }
```

Each test follows the pattern: register schema → store case → supersede/reinstate → verify retrieval behavior.

- [ ] **Step 8: Build and run full test suite**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 9: Commit**

```
feat(#152): supersession SPI — supersede()/reinstate() with audit metadata, store filters, contract tests
```

---

### Task 6: Chain integration test + cleanup

**Files:**
- Modify: `memory-cbr-tracking/src/test/java/io/casehub/neocortex/memory/cbr/tracking/DecoratorChainIntegrationTest.java`
- Delete: `memory-qdrant/src/main/java/io/casehub/neocortex/memory/cbr/qdrant/CbrQueryTranslator.java` — `toFilter()` method (if not already deleted in Task 5)
- Delete: relevant `toFilter` tests in `CbrQueryTranslatorTest.java`

**Interfaces:**
- Consumes: Everything from Tasks 1-5

- [ ] **Step 1: Add `storedAt` chain preservation test**

In `DecoratorChainIntegrationTest`:
```java
@Test void storedAt_preservedThroughDecoratorChain() {
    // Set up a chain: Decay@80 → OutcomeWeighting@65 → stub store
    // Store returns ScoredCbrCase with known storedAt
    // Apply a query with temporalDecay
    // Verify decay was applied (score changed) AND storedAt is preserved in output
}
```

- [ ] **Step 2: Run tests**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl memory-cbr-tracking -Dtest=DecoratorChainIntegrationTest
```

- [ ] **Step 3: Final full build**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 4: Commit**

```
feat(#152): chain integration test for storedAt preservation through decorator stack
```

---

## Task Dependencies

```
Task 1 (ScoredCbrCase + with*) ─┬─→ Task 3 (stores populate storedAt) ──→ Task 4 (decay decorator)
                                │
Task 2 (TemporalDecay variants) ─┘
                                     Task 5 (supersession SPI) depends on Tasks 1-4
                                     Task 6 (integration test) depends on Tasks 1-5
```

Tasks 1 and 2 are independent and can run in parallel. Tasks 3-6 are sequential.

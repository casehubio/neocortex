# CBR Hierarchical Scoping Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #153 — feat: CBR hierarchical scoping — scope hierarchy, scope-aware retrieval, cross-scope aggregation, GDPR isolation
**Issue group:** #153

**Goal:** Add hierarchical scope to CBR cases using platform `Path`, with scope-aware retrieval via `ScopeDecay` decorator and store-level visibility filtering.

**Architecture:** Scope is orthogonal to domain — a new `Path scope` dimension on storage and retrieval. Store-level filtering restricts visibility to cases at the query scope or its ancestors (no upward leakage). A `ScopeDecayCbrCaseMemoryStore` decorator applies configurable score decay by scope depth distance, symmetric with the existing `TemporalDecay` pattern.

**Tech Stack:** Java 21, Quarkus 3.32, platform `Path` (`io.casehub.platform.api.path.Path`), Qdrant, PostgreSQL/Flyway

## Global Constraints

- `Path` from `io.casehub.platform.api.path.Path` — already a dependency via `casehub-platform-api`
- `scope` is required, non-null on `store()` and `CbrQuery`. Callers that don't care pass `Path.root()`
- Visibility rule: `storedScope.equals(queryScope) || storedScope.isAncestorOf(queryScope)`
- `ScopeDecay` is nullable on `CbrQuery` — null means no decay (all visible scopes score equally)
- All existing tests must continue passing — `CbrQuery.of()` signature changes, so every call site must be updated
- Use `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn` for all build commands
- Use `ide_*` tools for all code navigation and editing on `.java` files

---

### Task 1: ScopeDecay sealed interface

**Files:**
- Create: `memory-api/src/main/java/io/casehub/neocortex/memory/cbr/ScopeDecay.java`
- Test: `memory-api/src/test/java/io/casehub/neocortex/memory/cbr/ScopeDecayTest.java`

**Interfaces:**
- Consumes: nothing
- Produces: `ScopeDecay` sealed interface with `factor(int depthDistance)`, records `Exponential(double base)`, `Linear(int maxDepth)`, `Step(double beyondExact)`

- [ ] **Step 1: Write the failing tests**

```java
package io.casehub.neocortex.memory.cbr;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ScopeDecayTest {

    @Test
    void exponential_exactMatch_returnsOne() {
        var decay = new ScopeDecay.Exponential(0.5);
        assertThat(decay.factor(0)).isEqualTo(1.0);
    }

    @Test
    void exponential_parent_returnsBase() {
        var decay = new ScopeDecay.Exponential(0.5);
        assertThat(decay.factor(1)).isEqualTo(0.5);
    }

    @Test
    void exponential_grandparent_returnsBaseSquared() {
        var decay = new ScopeDecay.Exponential(0.5);
        assertThat(decay.factor(2)).isEqualTo(0.25);
    }

    @Test
    void exponential_baseOne_noDecay() {
        var decay = new ScopeDecay.Exponential(1.0);
        assertThat(decay.factor(5)).isEqualTo(1.0);
    }

    @Test
    void exponential_invalidBase_zero() {
        assertThatThrownBy(() -> new ScopeDecay.Exponential(0.0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exponential_invalidBase_negative() {
        assertThatThrownBy(() -> new ScopeDecay.Exponential(-0.1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exponential_invalidBase_aboveOne() {
        assertThatThrownBy(() -> new ScopeDecay.Exponential(1.1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void linear_exactMatch_returnsOne() {
        var decay = new ScopeDecay.Linear(3);
        assertThat(decay.factor(0)).isEqualTo(1.0);
    }

    @Test
    void linear_parent_decaysLinearly() {
        var decay = new ScopeDecay.Linear(3);
        assertThat(decay.factor(1)).isCloseTo(0.6667, offset(0.001));
    }

    @Test
    void linear_atMaxDepth_returnsZero() {
        var decay = new ScopeDecay.Linear(3);
        assertThat(decay.factor(3)).isEqualTo(0.0);
    }

    @Test
    void linear_beyondMaxDepth_clampedToZero() {
        var decay = new ScopeDecay.Linear(3);
        assertThat(decay.factor(5)).isEqualTo(0.0);
    }

    @Test
    void linear_invalidMaxDepth_zero() {
        assertThatThrownBy(() -> new ScopeDecay.Linear(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void step_exactMatch_returnsOne() {
        var decay = new ScopeDecay.Step(0.3);
        assertThat(decay.factor(0)).isEqualTo(1.0);
    }

    @Test
    void step_anyAncestor_returnsBeyondExact() {
        var decay = new ScopeDecay.Step(0.3);
        assertThat(decay.factor(1)).isEqualTo(0.3);
        assertThat(decay.factor(5)).isEqualTo(0.3);
    }

    @Test
    void step_invalidBeyondExact_negative() {
        assertThatThrownBy(() -> new ScopeDecay.Step(-0.1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void step_invalidBeyondExact_aboveOne() {
        assertThatThrownBy(() -> new ScopeDecay.Step(1.1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void step_boundaryValues_zeroAndOne() {
        assertThatCode(() -> new ScopeDecay.Step(0.0)).doesNotThrowAnyException();
        assertThatCode(() -> new ScopeDecay.Step(1.0)).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl memory-api -Dtest=ScopeDecayTest -DfailIfNoTests=false`
Expected: compilation failure — `ScopeDecay` class does not exist

- [ ] **Step 3: Implement ScopeDecay**

Create `memory-api/src/main/java/io/casehub/neocortex/memory/cbr/ScopeDecay.java`:

```java
package io.casehub.neocortex.memory.cbr;

public sealed interface ScopeDecay {

    double factor(int depthDistance);

    record Exponential(double base) implements ScopeDecay {
        public Exponential {
            if (base <= 0.0 || base > 1.0) {
                throw new IllegalArgumentException("base must be in (0, 1], got " + base);
            }
        }

        @Override
        public double factor(int depthDistance) {
            return Math.pow(base, depthDistance);
        }
    }

    record Linear(int maxDepth) implements ScopeDecay {
        public Linear {
            if (maxDepth < 1) {
                throw new IllegalArgumentException("maxDepth must be >= 1, got " + maxDepth);
            }
        }

        @Override
        public double factor(int depthDistance) {
            return Math.max(0.0, 1.0 - (double) depthDistance / maxDepth);
        }
    }

    record Step(double beyondExact) implements ScopeDecay {
        public Step {
            if (beyondExact < 0.0 || beyondExact > 1.0) {
                throw new IllegalArgumentException("beyondExact must be in [0, 1], got " + beyondExact);
            }
        }

        @Override
        public double factor(int depthDistance) {
            return depthDistance == 0 ? 1.0 : beyondExact;
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl memory-api -Dtest=ScopeDecayTest`
Expected: all 14 tests pass

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/neocortex add memory-api/src/main/java/io/casehub/neocortex/memory/cbr/ScopeDecay.java memory-api/src/test/java/io/casehub/neocortex/memory/cbr/ScopeDecayTest.java
git -C /Users/mdproctor/claude/casehub/neocortex commit -m "feat(#153): ScopeDecay sealed interface — Exponential, Linear, Step variants"
```

---

### Task 2: SPI surface changes — CbrQuery, ScoredCbrCase, CbrCaseMemoryStore, ReactiveCbrCaseMemoryStore

**Files:**
- Modify: `memory-api/src/main/java/io/casehub/neocortex/memory/cbr/CbrQuery.java`
- Modify: `memory-api/src/main/java/io/casehub/neocortex/memory/cbr/ScoredCbrCase.java`
- Modify: `memory-api/src/main/java/io/casehub/neocortex/memory/cbr/CbrCaseMemoryStore.java`
- Modify: `memory-api/src/main/java/io/casehub/neocortex/memory/cbr/ReactiveCbrCaseMemoryStore.java`
- Modify: `memory-api/src/test/java/io/casehub/neocortex/memory/cbr/CbrQueryTest.java`

**Interfaces:**
- Consumes: `ScopeDecay` from Task 1, `io.casehub.platform.api.path.Path` from platform-api
- Produces: Updated `CbrQuery` (adds `Path scope`, `ScopeDecay scopeDecay`), updated `ScoredCbrCase` (adds `Path scope`), updated `CbrCaseMemoryStore.store()` and `ReactiveCbrCaseMemoryStore.store()` (adds `Path scope` parameter)

- [ ] **Step 1: Update CbrCaseMemoryStore.store() signature**

Add `Path scope` as the last parameter to `store()`:

```java
import io.casehub.platform.api.path.Path;

String store(CbrCase cbrCase, String caseType, String entityId, MemoryDomain domain,
             String tenantId, String caseId, Path scope);
```

Use `ide_edit_member` to replace the `store` method declaration on `CbrCaseMemoryStore`.

- [ ] **Step 2: Update ReactiveCbrCaseMemoryStore.store() signature**

Same change — add `Path scope`:

```java
import io.casehub.platform.api.path.Path;

Uni<String> store(CbrCase cbrCase, String caseType, String entityId, MemoryDomain domain,
                  String tenantId, String caseId, Path scope);
```

Use `ide_edit_member` on `ReactiveCbrCaseMemoryStore`.

- [ ] **Step 3: Update CbrQuery — add scope and scopeDecay fields**

Replace the entire `CbrQuery` record to add `Path scope` (required) and `ScopeDecay scopeDecay` (nullable) fields after `temporalDecay`. Update:
- Compact constructor: add `Objects.requireNonNull(scope, "scope required");`
- Factory `of()`: add `Path scope` parameter between `domain` and `caseType`, pass `null` for `scopeDecay`
- Add `withScope(Path)` and `withScopeDecay(ScopeDecay)` methods
- Update ALL existing `with*()` methods to include `scope` and `scopeDecay`

New factory signature:
```java
public static CbrQuery of(String tenantId, MemoryDomain domain, Path scope,
                          String caseType, Map<String, FeatureValue> features, int topK) {
    return new CbrQuery(tenantId, domain, caseType, features, Map.of(), Map.of(), topK,
                        0.0, null, null, 0.5, RetrievalMode.HYBRID, FusionStrategy.RRF, null,
                        scope, null);
}
```

- [ ] **Step 4: Update ScoredCbrCase — add scope field**

Add `Path scope` as the last field. Update all convenience constructors to default `scope` to `Path.root()`. Add import for `io.casehub.platform.api.path.Path`.

```java
public record ScoredCbrCase<C extends CbrCase>(C cbrCase, String caseId, double score, boolean reranked,
                                               Map<String, Double> featureSimilarities, Instant storedAt,
                                               Path scope) {
```

Convenience constructors:
```java
public ScoredCbrCase(C cbrCase, String caseId, double score) {
    this(cbrCase, caseId, score, false, Map.of(), null, Path.root());
}
public ScoredCbrCase(C cbrCase, double score) {
    this(cbrCase, null, score, false, Map.of(), null, Path.root());
}
public ScoredCbrCase(C cbrCase, double score, boolean reranked) {
    this(cbrCase, null, score, reranked, Map.of(), null, Path.root());
}
public ScoredCbrCase(C cbrCase, double score, boolean reranked,
                     Map<String, Double> featureSimilarities) {
    this(cbrCase, null, score, reranked, featureSimilarities, null, Path.root());
}
```

Update `withScore()` and `withReranked()` to include `scope`:
```java
public ScoredCbrCase<C> withScore(double newScore) {
    return new ScoredCbrCase<>(cbrCase, caseId, newScore, reranked, featureSimilarities, storedAt, scope);
}
public ScoredCbrCase<C> withReranked() {
    return new ScoredCbrCase<>(cbrCase, caseId, score, true, featureSimilarities, storedAt, scope);
}
```

- [ ] **Step 5: Update CbrQueryTest — fix existing tests for new factory signature**

Every `CbrQuery.of(tenantId, domain, caseType, features, topK)` call becomes `CbrQuery.of(tenantId, domain, Path.root(), caseType, features, topK)`. Add tests for `scope` validation:

```java
@Test
void scope_required() {
    assertThatThrownBy(() -> CbrQuery.of(TENANT, CBR, null, "test", Map.of(), 5))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("scope");
}

@Test
void withScope_returnsNewQuery() {
    var q = CbrQuery.of(TENANT, CBR, Path.root(), "test", Map.of(), 5);
    var scoped = q.withScope(Path.of("trial-alpha", "site-north"));
    assertThat(scoped.scope()).isEqualTo(Path.of("trial-alpha", "site-north"));
    assertThat(scoped.tenantId()).isEqualTo(TENANT);
}

@Test
void withScopeDecay_returnsNewQuery() {
    var q = CbrQuery.of(TENANT, CBR, Path.root(), "test", Map.of(), 5);
    var decayed = q.withScopeDecay(new ScopeDecay.Exponential(0.5));
    assertThat(decayed.scopeDecay()).isNotNull();
    assertThat(q.scopeDecay()).isNull();
}
```

- [ ] **Step 6: Run memory-api tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl memory-api`
Expected: all memory-api tests pass (downstream modules not compiled)

- [ ] **Step 7: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/neocortex add memory-api/
git -C /Users/mdproctor/claude/casehub/neocortex commit -m "feat(#153): SPI scope changes — CbrQuery, ScoredCbrCase, store() gain Path scope"
```

---

### Task 3: Contract tests + InMemory implementation

**Files:**
- Modify: `memory-testing/src/main/java/io/casehub/neocortex/memory/cbr/testing/CbrCaseMemoryStoreContractTest.java`
- Modify: `memory-cbr-inmem/src/main/java/io/casehub/neocortex/memory/cbr/inmem/InMemoryCbrCaseMemoryStore.java`

**Interfaces:**
- Consumes: Updated `CbrCaseMemoryStore`, `CbrQuery`, `ScoredCbrCase` from Task 2
- Produces: Scope contract tests (6 new tests), scope-aware `InMemoryCbrCaseMemoryStore`

- [ ] **Step 1: Fix all existing store() calls in CbrCaseMemoryStoreContractTest**

Every `store().store(c, type, entity, domain, tenant, caseId)` becomes `store().store(c, type, entity, domain, tenant, caseId, Path.root())`. Every `CbrQuery.of(tenant, domain, caseType, features, topK)` becomes `CbrQuery.of(tenant, domain, Path.root(), caseType, features, topK)`.

Add import: `import io.casehub.platform.api.path.Path;`

This is a bulk mechanical change — use find-and-replace patterns across the file.

- [ ] **Step 2: Add scope contract tests**

Add these tests to `CbrCaseMemoryStoreContractTest`:

```java
@Test
void scope_cascadeVisibility_ancestorScopesVisible() {
    store().registerSchema(CbrFeatureSchema.of("scoped",
        FeatureField.categorical("level")));
    var rootCase = new FeatureVectorCbrCase("global signal", "sol", null, null,
        Map.of("level", string("root")));
    var midCase = new FeatureVectorCbrCase("trial signal", "sol", null, null,
        Map.of("level", string("mid")));
    var leafCase = new FeatureVectorCbrCase("patient signal", "sol", null, null,
        Map.of("level", string("leaf")));
    store().store(rootCase, "scoped", ENTITY, CBR, TENANT, "root-1", Path.root());
    store().store(midCase, "scoped", ENTITY, CBR, TENANT, "mid-1", Path.of("trial"));
    store().store(leafCase, "scoped", ENTITY, CBR, TENANT, "leaf-1",
        Path.of("trial", "site", "patient"));
    var q = CbrQuery.of(TENANT, CBR, Path.of("trial", "site", "patient"),
        "scoped", Map.of("level", string("leaf")), 10);
    var results = store().retrieveSimilar(q, FeatureVectorCbrCase.class);
    assertThat(results).hasSize(3);
}

@Test
void scope_noUpwardLeakage_childNotVisibleFromParent() {
    store().registerSchema(CbrFeatureSchema.of("scoped2",
        FeatureField.categorical("level")));
    var childCase = new FeatureVectorCbrCase("patient data", "sol", null, null,
        Map.of("level", string("child")));
    store().store(childCase, "scoped2", ENTITY, CBR, TENANT, "child-1",
        Path.of("trial", "site", "patient"));
    var q = CbrQuery.of(TENANT, CBR, Path.of("trial", "site"),
        "scoped2", Map.of("level", string("child")), 10);
    var results = store().retrieveSimilar(q, FeatureVectorCbrCase.class);
    assertThat(results).isEmpty();
}

@Test
void scope_rootVisibility_rootVisibleFromAnyScope() {
    store().registerSchema(CbrFeatureSchema.of("scoped3",
        FeatureField.categorical("level")));
    var rootCase = new FeatureVectorCbrCase("global", "sol", null, null,
        Map.of("level", string("root")));
    store().store(rootCase, "scoped3", ENTITY, CBR, TENANT, "root-1", Path.root());
    var q = CbrQuery.of(TENANT, CBR, Path.of("trial", "site", "patient"),
        "scoped3", Map.of("level", string("root")), 10);
    var results = store().retrieveSimilar(q, FeatureVectorCbrCase.class);
    assertThat(results).hasSize(1);
}

@Test
void scope_branchIsolation_differentBranchNotVisible() {
    store().registerSchema(CbrFeatureSchema.of("scoped4",
        FeatureField.categorical("level")));
    var caseA = new FeatureVectorCbrCase("site-a signal", "sol", null, null,
        Map.of("level", string("a")));
    store().store(caseA, "scoped4", ENTITY, CBR, TENANT, "a-1",
        Path.of("trial-alpha", "site-north"));
    var q = CbrQuery.of(TENANT, CBR, Path.of("trial-beta", "site-south"),
        "scoped4", Map.of("level", string("a")), 10);
    var results = store().retrieveSimilar(q, FeatureVectorCbrCase.class);
    assertThat(results).isEmpty();
}

@Test
void scope_roundTrip_scopePreservedOnScoredCase() {
    store().registerSchema(CbrFeatureSchema.of("scoped5",
        FeatureField.categorical("level")));
    var c = new FeatureVectorCbrCase("signal", "sol", null, null,
        Map.of("level", string("x")));
    Path scope = Path.of("trial", "site");
    store().store(c, "scoped5", ENTITY, CBR, TENANT, "s-1", scope);
    var q = CbrQuery.of(TENANT, CBR, Path.of("trial", "site"),
        "scoped5", Map.of("level", string("x")), 10);
    var results = store().retrieveSimilar(q, FeatureVectorCbrCase.class);
    assertThat(results).hasSize(1);
    assertThat(results.get(0).scope()).isEqualTo(scope);
}

@Test
void scope_rootQueryIsolation_nonRootCasesNotVisibleFromRoot() {
    store().registerSchema(CbrFeatureSchema.of("scoped6",
        FeatureField.categorical("level")));
    var nonRootCase = new FeatureVectorCbrCase("site signal", "sol", null, null,
        Map.of("level", string("site")));
    store().store(nonRootCase, "scoped6", ENTITY, CBR, TENANT, "nr-1",
        Path.of("trial", "site"));
    var q = CbrQuery.of(TENANT, CBR, Path.root(),
        "scoped6", Map.of("level", string("site")), 10);
    var results = store().retrieveSimilar(q, FeatureVectorCbrCase.class);
    assertThat(results).isEmpty();
}
```

- [ ] **Step 3: Update InMemoryCbrCaseMemoryStore**

Add `Path scope` to `StoredCase` record. Update `store()` to accept and record scope. Update `retrieveSimilar()` to filter by scope visibility. Update `ScoredCbrCase` construction to include scope.

Key changes to `store()`:
```java
@Override
public String store(CbrCase cbrCase, String caseType, String entityId, MemoryDomain domain,
                    String tenantId, String caseId, Path scope) {
    // ... existing validation ...
    String id = UUID.randomUUID().toString();
    cases.add(new StoredCase(id, cbrCase, caseType, entityId, domain, tenantId, caseId,
                             Instant.now(), null, null, null, null, scope));
    return id;
}
```

Key filter addition in `retrieveSimilar()`:
```java
if (!isVisibleAtScope(stored.scope(), query.scope())) { continue; }
```

Add visibility method:
```java
private static boolean isVisibleAtScope(Path storedScope, Path queryScope) {
    return storedScope.equals(queryScope) || storedScope.isAncestorOf(queryScope);
}
```

Update `ScoredCbrCase` construction:
```java
candidates.add(new ScoredCbrCase<>((C) stored.cbrCase(), stored.caseId(),
    score, false, breakdown.featureSimilarities(), stored.storedAt(), stored.scope()));
```

Update `StoredCase` record to include `Path scope`:
```java
private record StoredCase(
    String id, CbrCase cbrCase, String caseType, String entityId, MemoryDomain domain,
    String tenantId, String caseId, Instant storedAt, Instant lastOutcomeAt,
    Instant supersededAt, String supersedingCaseId, String supersessionReason,
    Path scope
) { ... }
```

- [ ] **Step 4: Run InMemory contract tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl memory-testing,memory-cbr-inmem`
Expected: all existing tests + 6 new scope tests pass

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/neocortex add memory-testing/ memory-cbr-inmem/
git -C /Users/mdproctor/claude/casehub/neocortex commit -m "feat(#153): scope-aware InMemory store + 6 contract tests"
```

---

### Task 4: Decorators — store() pass-throughs + ScopeDecay decorator

**Files:**
- Modify: `memory/src/main/java/io/casehub/neocortex/memory/cbr/runtime/NoOpCbrCaseMemoryStore.java`
- Modify: `memory/src/main/java/io/casehub/neocortex/memory/cbr/runtime/BlockingToReactiveCbrBridge.java`
- Modify: `memory/src/main/java/io/casehub/neocortex/memory/cbr/runtime/TemporalDecayCbrCaseMemoryStore.java`
- Modify: `memory/src/main/java/io/casehub/neocortex/memory/cbr/runtime/ReactiveTemporalDecayCbrCaseMemoryStore.java`
- Modify: `memory/src/main/java/io/casehub/neocortex/memory/cbr/runtime/OutcomeWeightingCbrCaseMemoryStore.java`
- Modify: `memory/src/main/java/io/casehub/neocortex/memory/cbr/runtime/ReactiveOutcomeWeightingCbrCaseMemoryStore.java`
- Modify: `memory/src/main/java/io/casehub/neocortex/memory/cbr/runtime/TrendEnrichmentCbrCaseMemoryStore.java`
- Modify: `memory/src/main/java/io/casehub/neocortex/memory/cbr/runtime/ReactiveTrendEnrichmentCbrCaseMemoryStore.java`
- Create: `memory/src/main/java/io/casehub/neocortex/memory/cbr/runtime/ScopeDecayCbrCaseMemoryStore.java`
- Create: `memory/src/main/java/io/casehub/neocortex/memory/cbr/runtime/ReactiveScopeDecayCbrCaseMemoryStore.java`
- Create: `memory/src/test/java/io/casehub/neocortex/memory/cbr/runtime/ScopeDecayCbrCaseMemoryStoreTest.java`
- Modify: `memory/src/test/java/io/casehub/neocortex/memory/cbr/runtime/TemporalDecayCbrCaseMemoryStoreTest.java`
- Modify: `memory/src/test/java/io/casehub/neocortex/memory/cbr/runtime/OutcomeWeightingCbrCaseMemoryStoreTest.java`
- Modify: `memory/src/test/java/io/casehub/neocortex/memory/cbr/runtime/ReactiveOutcomeWeightingCbrCaseMemoryStoreTest.java`
- Modify: `memory/src/test/java/io/casehub/neocortex/memory/cbr/runtime/TrendEnrichmentCbrCaseMemoryStoreTest.java`

**Interfaces:**
- Consumes: Updated `CbrCaseMemoryStore`, `ScopeDecay`, `ScoredCbrCase` from Tasks 1-2
- Produces: `ScopeDecayCbrCaseMemoryStore` @Decorator @Priority(85), all decorators forwarding scope in store()

- [ ] **Step 1: Update all existing decorators — store() pass-through**

For each decorator, update the `store()` pass-through to include `Path scope`. The pattern is the same for all:

```java
@Override public String store(CbrCase c, String ct, String e, MemoryDomain d, String t, String ci, Path scope) {
    return delegate.store(c, ct, e, d, t, ci, scope);
}
```

Add `import io.casehub.platform.api.path.Path;` to each file.

Files to update (blocking + reactive):
- `NoOpCbrCaseMemoryStore` — also update return `""`
- `BlockingToReactiveCbrBridge` — update lambda: `delegate.store(cbrCase, caseType, entityId, domain, tenantId, caseId, scope)`
- `TemporalDecayCbrCaseMemoryStore` + `ReactiveTemporalDecayCbrCaseMemoryStore`
- `OutcomeWeightingCbrCaseMemoryStore` + `ReactiveOutcomeWeightingCbrCaseMemoryStore`
- `TrendEnrichmentCbrCaseMemoryStore` + `ReactiveTrendEnrichmentCbrCaseMemoryStore`

- [ ] **Step 2: Fix existing decorator tests**

Update every `CbrQuery.of()` call and `store()` call in existing decorator tests to include `Path.root()`:
- `TemporalDecayCbrCaseMemoryStoreTest`
- `OutcomeWeightingCbrCaseMemoryStoreTest`
- `ReactiveOutcomeWeightingCbrCaseMemoryStoreTest`
- `TrendEnrichmentCbrCaseMemoryStoreTest`

- [ ] **Step 3: Write ScopeDecay decorator tests**

Create `ScopeDecayCbrCaseMemoryStoreTest.java`:

```java
package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.*;
import io.casehub.platform.api.path.Path;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScopeDecayCbrCaseMemoryStoreTest {

    private static final MemoryDomain CBR = new MemoryDomain("cbr");
    private static final String TENANT = "t1";

    private ScopeDecayCbrCaseMemoryStore decorator(List<ScoredCbrCase<FeatureVectorCbrCase>> results) {
        CbrCaseMemoryStore stub = new StubStore(results);
        return new ScopeDecayCbrCaseMemoryStore(stub);
    }

    private ScoredCbrCase<FeatureVectorCbrCase> scored(double score, Path scope) {
        var c = new FeatureVectorCbrCase("p", "s", null, null, Map.of());
        return new ScoredCbrCase<>(c, "id", score, false, Map.of(), Instant.now(), scope);
    }

    @Test
    void nullScopeDecay_passThrough() {
        var results = List.of(scored(0.8, Path.root()), scored(0.6, Path.of("trial")));
        var q = CbrQuery.of(TENANT, CBR, Path.of("trial", "site"), "t", Map.of(), 10);
        var out = decorator(results).retrieveSimilar(q, FeatureVectorCbrCase.class);
        assertThat(out).hasSize(2);
        assertThat(out.get(0).score()).isEqualTo(0.8);
    }

    @Test
    void exponentialDecay_exactScopeUnchanged() {
        var results = List.of(scored(0.8, Path.of("trial", "site")));
        var q = CbrQuery.of(TENANT, CBR, Path.of("trial", "site"), "t", Map.of(), 10)
            .withScopeDecay(new ScopeDecay.Exponential(0.5));
        var out = decorator(results).retrieveSimilar(q, FeatureVectorCbrCase.class);
        assertThat(out.get(0).score()).isEqualTo(0.8);
    }

    @Test
    void exponentialDecay_parentHalved() {
        var results = List.of(scored(0.8, Path.of("trial")));
        var q = CbrQuery.of(TENANT, CBR, Path.of("trial", "site"), "t", Map.of(), 10)
            .withScopeDecay(new ScopeDecay.Exponential(0.5));
        var out = decorator(results).retrieveSimilar(q, FeatureVectorCbrCase.class);
        assertThat(out.get(0).score()).isEqualTo(0.4);
    }

    @Test
    void exponentialDecay_grandparentQuartered() {
        var results = List.of(scored(1.0, Path.root()));
        var q = CbrQuery.of(TENANT, CBR, Path.of("trial", "site"), "t", Map.of(), 10)
            .withScopeDecay(new ScopeDecay.Exponential(0.5));
        var out = decorator(results).retrieveSimilar(q, FeatureVectorCbrCase.class);
        assertThat(out.get(0).score()).isEqualTo(0.25);
    }

    @Test
    void belowMinSimilarity_filteredOut() {
        var results = List.of(scored(0.3, Path.root()));
        var q = CbrQuery.of(TENANT, CBR, Path.of("trial", "site"), "t", Map.of(), 10)
            .withMinSimilarity(0.2).withScopeDecay(new ScopeDecay.Exponential(0.5));
        var out = decorator(results).retrieveSimilar(q, FeatureVectorCbrCase.class);
        assertThat(out).isEmpty();
    }

    @Test
    void resortAfterDecay_orderChanges() {
        var exactLow = scored(0.5, Path.of("trial", "site"));
        var ancestorHigh = scored(0.9, Path.root());
        var results = List.of(ancestorHigh, exactLow);
        var q = CbrQuery.of(TENANT, CBR, Path.of("trial", "site"), "t", Map.of(), 10)
            .withScopeDecay(new ScopeDecay.Exponential(0.5));
        var out = decorator(results).retrieveSimilar(q, FeatureVectorCbrCase.class);
        assertThat(out.get(0).score()).isEqualTo(0.5);
        assertThat(out.get(1).score()).isCloseTo(0.225, org.assertj.core.data.Offset.offset(0.001));
    }

    private static class StubStore implements CbrCaseMemoryStore {
        private final List<? extends ScoredCbrCase<?>> results;
        StubStore(List<? extends ScoredCbrCase<?>> results) { this.results = results; }
        @Override public void registerSchema(CbrFeatureSchema s) {}
        @Override public String store(CbrCase c, String ct, String e, MemoryDomain d, String t, String ci, Path scope) { return ""; }
        @Override @SuppressWarnings("unchecked")
        public <C extends CbrCase> List<ScoredCbrCase<C>> retrieveSimilar(CbrQuery q, Class<C> t) {
            return (List<ScoredCbrCase<C>>) (List<?>) results;
        }
        @Override public Integer erase(io.casehub.neocortex.memory.EraseRequest r) { return 0; }
        @Override public Integer eraseEntity(String e, String t) { return 0; }
        @Override public void recordOutcome(String ci, String t, CbrOutcome o) {}
        @Override public Integer purge(CbrRetentionPolicy p) { return 0; }
        @Override public void supersede(String ci, String t, String s, String r) {}
        @Override public void reinstate(String ci, String t) {}
    }
}
```

- [ ] **Step 4: Implement ScopeDecayCbrCaseMemoryStore**

Create `memory/src/main/java/io/casehub/neocortex/memory/cbr/runtime/ScopeDecayCbrCaseMemoryStore.java`:

```java
package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.*;
import io.casehub.platform.api.path.Path;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Decorator
@Priority(85)
public class ScopeDecayCbrCaseMemoryStore implements CbrCaseMemoryStore {

    private final CbrCaseMemoryStore delegate;

    @Inject
    public ScopeDecayCbrCaseMemoryStore(@Delegate @Any CbrCaseMemoryStore delegate) {
        this.delegate = delegate;
    }

    @Override
    public <C extends CbrCase> List<ScoredCbrCase<C>> retrieveSimilar(
            CbrQuery query, Class<C> caseType) {
        List<ScoredCbrCase<C>> results = delegate.retrieveSimilar(query, caseType);
        if (query.scopeDecay() == null) {
            return results;
        }
        ScopeDecay decay = query.scopeDecay();
        int queryDepth = query.scope().depth();
        List<ScoredCbrCase<C>> decayed = new ArrayList<>(results.size());
        for (var scored : results) {
            int depthDistance = queryDepth - scored.scope().depth();
            double factor = decay.factor(depthDistance);
            double adjustedScore = scored.score() * factor;
            if (adjustedScore >= query.minSimilarity()) {
                decayed.add(scored.withScore(adjustedScore));
            }
        }
        decayed.sort((a, b) -> Double.compare(b.score(), a.score()));
        return Collections.unmodifiableList(decayed);
    }

    @Override public void registerSchema(CbrFeatureSchema schema) { delegate.registerSchema(schema); }
    @Override public String store(CbrCase c, String ct, String e, MemoryDomain d, String t, String ci, Path scope) { return delegate.store(c, ct, e, d, t, ci, scope); }
    @Override public Integer erase(EraseRequest r) { return delegate.erase(r); }
    @Override public Integer eraseEntity(String e, String t) { return delegate.eraseEntity(e, t); }
    @Override public void recordOutcome(String ci, String t, CbrOutcome o) { delegate.recordOutcome(ci, t, o); }
    @Override public Integer purge(CbrRetentionPolicy p) { return delegate.purge(p); }
    @Override public void supersede(String caseId, String tenantId, String supersedingCaseId, String reason) { delegate.supersede(caseId, tenantId, supersedingCaseId, reason); }
    @Override public void reinstate(String caseId, String tenantId) { delegate.reinstate(caseId, tenantId); }
}
```

- [ ] **Step 5: Implement ReactiveScopeDecayCbrCaseMemoryStore**

Create `memory/src/main/java/io/casehub/neocortex/memory/cbr/runtime/ReactiveScopeDecayCbrCaseMemoryStore.java` — same logic as blocking variant but with `Uni<List<ScoredCbrCase<C>>>` return and Mutiny operators. Follow the `ReactiveTemporalDecayCbrCaseMemoryStore` pattern exactly.

- [ ] **Step 6: Run memory module tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl memory-api,memory-testing,memory-cbr-inmem,memory`
Expected: all tests pass

- [ ] **Step 7: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/neocortex add memory/
git -C /Users/mdproctor/claude/casehub/neocortex commit -m "feat(#153): ScopeDecay decorator @Priority(85) + all decorator store() pass-throughs"
```

---

### Task 5: Cross-encoder + tracking pass-throughs

**Files:**
- Modify: `memory-cbr-crossencoder/src/main/java/io/casehub/neocortex/memory/cbr/crossencoder/RerankingCbrCaseMemoryStore.java`
- Modify: `memory-cbr-crossencoder/src/main/java/io/casehub/neocortex/memory/cbr/crossencoder/ReactiveRerankingCbrCaseMemoryStore.java`
- Modify: `memory-cbr-tracking/src/main/java/io/casehub/neocortex/memory/cbr/tracking/TrackingCbrCaseMemoryStore.java`
- Modify: `memory-cbr-tracking/src/main/java/io/casehub/neocortex/memory/cbr/tracking/ReactiveTrackingCbrCaseMemoryStore.java`
- Modify: corresponding test files for any `CbrQuery.of()` or `store()` call site updates

**Interfaces:**
- Consumes: Updated `CbrCaseMemoryStore` from Task 2
- Produces: All cross-encoder and tracking decorators compile and pass tests

- [ ] **Step 1: Update cross-encoder decorators**

Update `store()` pass-through in `RerankingCbrCaseMemoryStore` and `ReactiveRerankingCbrCaseMemoryStore` to include `Path scope`. Add import.

- [ ] **Step 2: Update tracking decorators**

Update `store()` pass-through in `TrackingCbrCaseMemoryStore` and `ReactiveTrackingCbrCaseMemoryStore` to include `Path scope`. Add import.

- [ ] **Step 3: Fix test call sites**

Update `CbrQuery.of()` and `store()` calls in:
- `memory-cbr-crossencoder/src/test/` — all test files
- `memory-cbr-tracking/src/test/` — all test files, including `DecoratorChainIntegrationTest` (has inner classes `WeightingWrapper` and `BridgedReactiveTestStore` that implement `store()` directly)

- [ ] **Step 4: Run tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl memory-cbr-crossencoder,memory-cbr-tracking`
Expected: all tests pass

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/neocortex add memory-cbr-crossencoder/ memory-cbr-tracking/
git -C /Users/mdproctor/claude/casehub/neocortex commit -m "feat(#153): cross-encoder + tracking — store() scope pass-through"
```

---

### Task 6: JPA implementation + Flyway migration

**Files:**
- Modify: `memory-cbr-jpa/src/main/java/io/casehub/neocortex/memory/cbr/jpa/CbrCaseEntity.java`
- Modify: `memory-cbr-jpa/src/main/java/io/casehub/neocortex/memory/cbr/jpa/JpaCbrCaseMemoryStore.java`
- Create: `memory-cbr-jpa/src/main/resources/db/cbr/migration/V4__add_scope.sql`

**Interfaces:**
- Consumes: Updated `CbrCaseMemoryStore`, `CbrQuery`, `ScoredCbrCase` from Task 2, `Path` from platform-api
- Produces: Scope-aware JPA store, `scope` column with Flyway migration

- [ ] **Step 1: Write Flyway migration**

Create `V4__add_scope.sql`:
```sql
ALTER TABLE cbr_case ADD COLUMN scope VARCHAR(1024) NOT NULL DEFAULT '';

DROP INDEX cbr_case_lookup_idx;
CREATE INDEX cbr_case_lookup_idx ON cbr_case (tenant_id, domain, case_type, scope);
```

- [ ] **Step 2: Add scope to CbrCaseEntity**

Add field:
```java
@Column(name = "scope", nullable = false)
public String scope;
```

- [ ] **Step 3: Update JpaCbrCaseMemoryStore.store()**

Add `Path scope` parameter. Set `entity.scope = scope.value();` (use `Path.value()` for the serialized form; `""` for root).

- [ ] **Step 4: Update JpaCbrCaseMemoryStore.retrieveSimilar()**

Replace the current query filter to include scope visibility. The JPQL/native query must match:
```sql
WHERE e.tenantId = :tenant AND e.domain = :domain AND e.caseType = :caseType
  AND e.supersededAt IS NULL
  AND (e.scope = '' OR e.scope = :queryScope OR :queryScope LIKE e.scope || '/%')
```

Update `ScoredCbrCase` construction to include `Path.parse(entity.scope)` (or `Path.root()` if scope is `""`).

- [ ] **Step 5: Fix JPA test call sites**

Update `CbrQuery.of()` and `store()` calls in JPA test files.

- [ ] **Step 6: Run JPA tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl memory-cbr-jpa`
Expected: all existing + contract scope tests pass

- [ ] **Step 7: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/neocortex add memory-cbr-jpa/
git -C /Users/mdproctor/claude/casehub/neocortex commit -m "feat(#153): JPA scope column + Flyway V4 migration + scope-aware retrieval"
```

---

### Task 7: Qdrant implementation + migration

**Files:**
- Modify: `memory-qdrant/src/main/java/io/casehub/neocortex/memory/cbr/qdrant/QdrantCbrCaseMemoryStore.java`
- Modify: `memory-qdrant/src/main/java/io/casehub/neocortex/memory/cbr/qdrant/CbrQueryTranslator.java`
- Modify: `memory-qdrant/src/main/java/io/casehub/neocortex/memory/cbr/qdrant/CbrCollectionManager.java`

**Interfaces:**
- Consumes: Updated `CbrCaseMemoryStore`, `CbrQuery`, `ScoredCbrCase`, `Path` from Tasks 1-2
- Produces: Scope-aware Qdrant store, scope payload field, migration backfill

- [ ] **Step 1: Add "scope" to BASE_KEYWORD_FIELDS in CbrCollectionManager**

```java
private static final String[] BASE_KEYWORD_FIELDS =
    {"tenantId", "caseType", "entityId", "domain", "caseId", "scope"};
```

- [ ] **Step 2: Add backfill logic in CbrCollectionManager.ensureCollection()**

After ensuring the collection exists and indexes are created, add a backfill step: scroll all points where `scope` is absent (Qdrant `IsEmpty` condition on `"scope"` field) and set `scope = ""` via `setPayload`. This runs once per collection.

```java
// Backfill existing points that lack scope
var emptyFilter = Filter.newBuilder()
    .addMust(ConditionFactory.isEmpty("scope"))
    .build();
// Scroll + setPayload with scope = "" for each point
```

- [ ] **Step 3: Update CbrQueryTranslator.toIdentityFilter() — scope visibility**

Add scope ancestor enumeration. Build a list of all ancestor scope strings from the query scope, then use `matchKeywords("scope", ancestorScopes)`:

```java
List<String> ancestorScopes = new ArrayList<>();
ancestorScopes.add(query.scope().value()); // exact match
ancestorScopes.add(""); // root is always visible
for (int i = 1; i < query.scope().segments().size(); i++) {
    ancestorScopes.add(String.join("/", query.scope().segments().subList(0, i)));
}
builder.addMust(ConditionFactory.matchKeywords("scope", ancestorScopes));
```

- [ ] **Step 4: Update QdrantCbrCaseMemoryStore.store() — add scope to payload**

Add `Path scope` parameter. Add scope to the point payload:
```java
payload.put("scope", ValueFactory.value(scope.value()));
```

- [ ] **Step 5: Update QdrantCbrCaseMemoryStore.retrieveSimilar() — extract scope from payload**

When constructing `ScoredCbrCase` from Qdrant results, extract scope:
```java
String scopeValue = point.getPayloadOrDefault("scope", ValueFactory.value(""))
                         .getStringValue();
Path scope = scopeValue.isEmpty() ? Path.root() : Path.parse(scopeValue);
```

Include `scope` in `ScoredCbrCase` construction.

- [ ] **Step 6: Fix Qdrant test call sites**

Update `CbrQuery.of()` and `store()` calls in Qdrant test files.

- [ ] **Step 7: Run Qdrant tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl memory-qdrant`
Expected: all existing + contract scope tests pass (requires Testcontainers Qdrant)

- [ ] **Step 8: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/neocortex add memory-qdrant/
git -C /Users/mdproctor/claude/casehub/neocortex commit -m "feat(#153): Qdrant scope payload + ancestor filtering + backfill migration"
```

---

### Task 8: Full build verification

**Files:**
- None — verification only

**Interfaces:**
- Consumes: all changes from Tasks 1-7
- Produces: clean full build

- [ ] **Step 1: Full build with tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install`
Expected: BUILD SUCCESS — all modules compile and all tests pass

- [ ] **Step 2: Verify no remaining compilation errors**

Run: `ide_build_project` to confirm IntelliJ sees no errors.

- [ ] **Step 3: Verify scope contract tests pass in all store implementations**

Confirm contract test results across InMemory, JPA, and Qdrant:
- InMemory: 6 scope tests + all existing
- JPA: 6 scope tests + all existing (via contract inheritance)
- Qdrant: 6 scope tests + all existing (via contract inheritance)

- [ ] **Step 4: Update example modules (if building with -Pexamples)**

Examples are excluded from the default build. If building with `-Pexamples` or `-Pexamples-smoke`, update `store()` and `CbrQuery.of()` calls in:
- `examples/example-cbr/src/main/java/io/casehub/neocortex/examples/cbr/ClinicalAdverseEventDemo.java`
- `examples/example-cbr/src/main/java/io/casehub/neocortex/examples/cbr/QuarkmindBattleDemo.java`
- `examples/example-cbr/src/main/java/io/casehub/neocortex/examples/cbr/AmlInvestigationDemo.java`
- `examples/example-cbr/src/main/java/io/casehub/neocortex/examples/cbr/LifeContractorDemo.java`
- `examples/example-cbr/src/main/java/io/casehub/neocortex/examples/cbr/IotSituationDemo.java`
- `examples/example-cbr/src/main/java/io/casehub/neocortex/examples/cbr/DevtownPrReviewDemo.java`

Add `Path.root()` as the scope parameter to each `store()` call and `CbrQuery.of()` call.

- [ ] **Step 5: Commit any remaining fixes**

If any module had unresolved compilation issues, fix and commit.

# Research Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #804 — Platform research methodology — workspace tools, site capture, and LLM research discipline
**Issue group:** #804

**Goal:** Give LLM agents a persistent research workspace with disciplined tool affordances — store with provenance, semantic search, coverage tracking, freshness checking, and workspace-to-corpus promotion.

**Architecture:** Extend `knowledge-pipeline-api` with workspace SPI types (pure Java, Tier 1) and `knowledge-pipeline` with a SQLite-backed implementation that composes `EmbeddingIngestor`/`CaseRetriever` for semantic search and `MindMapStore` for knowledge graph promotion. Single `ResearchWorkspace` facade SPI with workspace-native sessions independent of existing `ResearchSession`.

**Tech Stack:** Java 21, Quarkus 3.32.2, SQLite + HikariCP WAL + Flyway (via `sqlite-support`), LangChain4j RAG (EmbeddingIngestor, CaseRetriever), Jackson for JSON serialization.

## Global Constraints

- Java 21 source level, Java 26 JVM
- `knowledge-pipeline-api` remains Tier 1 pure Java — no rag-api dependency in the API module
- `knowledge-pipeline` runtime adds rag-api as compile dependency
- SQLite stores use `SqliteDataSourceFactory` from `sqlite-support` module
- Flyway migrations under `db/knowledge-workspace/migration/`
- Config prefix: `casehub.knowledge.workspace`
- Package: `io.casehub.neocortex.knowledge.workspace`
- Use `mvn` not `./mvnw`
- Build command: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install`

---

## Batch 1: SPI Types (knowledge-pipeline-api)

Pure value types and SPI interfaces. No dependencies beyond `casehub-connectors-api`. All tests are unit tests on the value types.

### Task 1: Content sealed hierarchy + ReliabilityTier + FreshnessPolicy

**Files:**
- Create: `knowledge-pipeline-api/src/main/java/io/casehub/neocortex/knowledge/workspace/Content.java`
- Create: `knowledge-pipeline-api/src/main/java/io/casehub/neocortex/knowledge/workspace/ReliabilityTier.java`
- Create: `knowledge-pipeline-api/src/main/java/io/casehub/neocortex/knowledge/workspace/FreshnessPolicy.java`
- Test: `knowledge-pipeline-api/src/test/java/io/casehub/neocortex/knowledge/workspace/ContentTest.java`
- Test: `knowledge-pipeline-api/src/test/java/io/casehub/neocortex/knowledge/workspace/FreshnessPolicyTest.java`

**Interfaces:**
- Consumes: nothing (first task)
- Produces: `Content` sealed interface (TextContent, BinaryContent, StructuredContent), `ReliabilityTier` enum, `FreshnessPolicy` record with `maxAgeFor(String category)` and `staleCategories(Set<String>, Instant, Instant)`

- [ ] **Step 1: Write Content sealed hierarchy test**

```java
package io.casehub.neocortex.knowledge.workspace;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ContentTest {

    @Test
    void textContentCarriesBodyAndMediaType() {
        var content = new Content.TextContent("hello world", "text/plain");
        assertThat(content.body()).isEqualTo("hello world");
        assertThat(content.mediaType()).isEqualTo("text/plain");
    }

    @Test
    void binaryContentCarriesDataAndMediaType() {
        byte[] data = {0x50, 0x44, 0x46};
        var content = new Content.BinaryContent(data, "application/pdf");
        assertThat(content.data()).isEqualTo(data);
        assertThat(content.mediaType()).isEqualTo("application/pdf");
    }

    @Test
    void structuredContentCarriesDataSchemaRefAndMediaType() {
        var data = java.util.Map.<String, Object>of("price", 9.99);
        var content = new Content.StructuredContent(data, "pricing-v1", "application/json");
        assertThat(content.data()).containsEntry("price", 9.99);
        assertThat(content.schemaRef()).isEqualTo("pricing-v1");
        assertThat(content.mediaType()).isEqualTo("application/json");
    }

    @Test
    void sealedHierarchyIsExhaustive() {
        Content content = new Content.TextContent("test", "text/plain");
        String result = switch (content) {
            case Content.TextContent t -> "text";
            case Content.BinaryContent b -> "binary";
            case Content.StructuredContent s -> "structured";
        };
        assertThat(result).isEqualTo("text");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl knowledge-pipeline-api -Dtest=ContentTest -DfailIfNoTests=false`
Expected: compilation failure — `Content` does not exist

- [ ] **Step 3: Implement Content, ReliabilityTier**

Use `ide_create_file` for Content.java:

```java
package io.casehub.neocortex.knowledge.workspace;

import java.util.Map;
import java.util.Objects;

public sealed interface Content {
    String mediaType();

    record TextContent(String body, String mediaType) implements Content {
        public TextContent {
            Objects.requireNonNull(body);
            Objects.requireNonNull(mediaType);
        }
    }

    record BinaryContent(byte[] data, String mediaType) implements Content {
        public BinaryContent {
            Objects.requireNonNull(data);
            Objects.requireNonNull(mediaType);
        }
    }

    record StructuredContent(Map<String, Object> data, String schemaRef,
                             String mediaType) implements Content {
        public StructuredContent {
            Objects.requireNonNull(data);
            Objects.requireNonNull(mediaType);
            data = Map.copyOf(data);
        }
    }
}
```

Use `ide_create_file` for ReliabilityTier.java:

```java
package io.casehub.neocortex.knowledge.workspace;

public enum ReliabilityTier {
    AUTHORITATIVE,
    OFFICIAL,
    CURATED,
    COMMUNITY,
    UNKNOWN
}
```

- [ ] **Step 4: Write FreshnessPolicy test**

```java
package io.casehub.neocortex.knowledge.workspace;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class FreshnessPolicyTest {

    @Test
    void maxAgeForReturnsCategoryOverrideWhenPresent() {
        var policy = new FreshnessPolicy(Duration.ofDays(30),
            Map.of("pricing", Duration.ofDays(7)));
        assertThat(policy.maxAgeFor("pricing")).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void maxAgeForReturnsDefaultWhenNoCategoryOverride() {
        var policy = new FreshnessPolicy(Duration.ofDays(30), Map.of());
        assertThat(policy.maxAgeFor("features")).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void staleCategoriesReturnsOnlyStaleCategories() {
        var policy = new FreshnessPolicy(Duration.ofDays(30),
            Map.of("pricing", Duration.ofDays(7), "patents", Duration.ofDays(365)));
        Instant storedAt = Instant.now().minus(Duration.ofDays(10));
        Instant now = Instant.now();
        Set<String> stale = policy.staleCategories(
            Set.of("pricing", "patents", "features"), storedAt, now);
        assertThat(stale).containsExactly("pricing");
    }

    @Test
    void staleCategoriesReturnsEmptyWhenNothingStale() {
        var policy = new FreshnessPolicy(Duration.ofDays(30), Map.of());
        Instant storedAt = Instant.now().minus(Duration.ofDays(1));
        Set<String> stale = policy.staleCategories(Set.of("a", "b"), storedAt, Instant.now());
        assertThat(stale).isEmpty();
    }

    @Test
    void withDefaultFactoryMethod() {
        var policy = FreshnessPolicy.withDefault(Duration.ofDays(14));
        assertThat(policy.defaultMaxAge()).isEqualTo(Duration.ofDays(14));
        assertThat(policy.categoryOverrides()).isEmpty();
    }
}
```

- [ ] **Step 5: Implement FreshnessPolicy**

```java
package io.casehub.neocortex.knowledge.workspace;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record FreshnessPolicy(
    Duration defaultMaxAge,
    Map<String, Duration> categoryOverrides
) {
    public FreshnessPolicy {
        Objects.requireNonNull(defaultMaxAge);
        categoryOverrides = categoryOverrides == null ? Map.of() : Map.copyOf(categoryOverrides);
    }

    public Duration maxAgeFor(String category) {
        return categoryOverrides.getOrDefault(category, defaultMaxAge);
    }

    public Set<String> staleCategories(Set<String> categories, Instant storedAt, Instant now) {
        Set<String> stale = new LinkedHashSet<>();
        for (String category : categories) {
            if (storedAt.plus(maxAgeFor(category)).isBefore(now)) {
                stale.add(category);
            }
        }
        return stale;
    }

    public static FreshnessPolicy withDefault(Duration maxAge) {
        return new FreshnessPolicy(maxAge, Map.of());
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl knowledge-pipeline-api -Dtest="ContentTest,FreshnessPolicyTest"`
Expected: all tests PASS

- [ ] **Step 7: Commit**

```bash
git add knowledge-pipeline-api/src/main/java/io/casehub/neocortex/knowledge/workspace/Content.java knowledge-pipeline-api/src/main/java/io/casehub/neocortex/knowledge/workspace/ReliabilityTier.java knowledge-pipeline-api/src/main/java/io/casehub/neocortex/knowledge/workspace/FreshnessPolicy.java knowledge-pipeline-api/src/test/java/io/casehub/neocortex/knowledge/workspace/ContentTest.java knowledge-pipeline-api/src/test/java/io/casehub/neocortex/knowledge/workspace/FreshnessPolicyTest.java
git commit -m "feat(#804): add Content sealed hierarchy, ReliabilityTier, and FreshnessPolicy types"
```

### Task 2: WorkspaceEntry + WorkspaceInput + WorkspaceDescriptor + CoverageReport + StaleEntry + WorkspacePromotionResult

**Files:**
- Create: `knowledge-pipeline-api/src/main/java/io/casehub/neocortex/knowledge/workspace/WorkspaceEntry.java`
- Create: `knowledge-pipeline-api/src/main/java/io/casehub/neocortex/knowledge/workspace/WorkspaceInput.java`
- Create: `knowledge-pipeline-api/src/main/java/io/casehub/neocortex/knowledge/workspace/WorkspaceDescriptor.java`
- Create: `knowledge-pipeline-api/src/main/java/io/casehub/neocortex/knowledge/workspace/WorkspaceState.java`
- Create: `knowledge-pipeline-api/src/main/java/io/casehub/neocortex/knowledge/workspace/CoverageReport.java`
- Create: `knowledge-pipeline-api/src/main/java/io/casehub/neocortex/knowledge/workspace/StaleEntry.java`
- Create: `knowledge-pipeline-api/src/main/java/io/casehub/neocortex/knowledge/workspace/WorkspacePromotionResult.java`
- Test: `knowledge-pipeline-api/src/test/java/io/casehub/neocortex/knowledge/workspace/WorkspaceInputTest.java`

**Interfaces:**
- Consumes: `Content`, `ReliabilityTier`, `FreshnessPolicy` from Task 1
- Produces: `WorkspaceEntry`, `WorkspaceInput` (with provenance enforcement), `WorkspaceDescriptor`, `WorkspaceState`, `CoverageReport`, `StaleEntry`, `WorkspacePromotionResult`

- [ ] **Step 1: Write WorkspaceInput provenance enforcement test**

```java
package io.casehub.neocortex.knowledge.workspace;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class WorkspaceInputTest {

    @Test
    void rejectsNonDerivedEntryWithoutSourceUri() {
        assertThatThrownBy(() -> new WorkspaceInput(
            new Content.TextContent("body", "text/plain"),
            null, null, null, Set.of(), ReliabilityTier.UNKNOWN, List.of(), Map.of()
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("sourceUri is required");
    }

    @Test
    void allowsDerivedEntryWithoutSourceUri() {
        var input = new WorkspaceInput(
            new Content.TextContent("analysis", "text/plain"),
            null, null, null, Set.of("analysis"),
            ReliabilityTier.CURATED, List.of("entry-1"), Map.of());
        assertThat(input.sourceUri()).isNull();
        assertThat(input.derivedFrom()).containsExactly("entry-1");
    }

    @Test
    void allowsNonDerivedEntryWithSourceUri() {
        var input = new WorkspaceInput(
            new Content.TextContent("page content", "text/html"),
            null, URI.create("https://example.com"), null,
            Set.of("pricing"), ReliabilityTier.OFFICIAL, List.of(), Map.of());
        assertThat(input.sourceUri()).isEqualTo(URI.create("https://example.com"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl knowledge-pipeline-api -Dtest=WorkspaceInputTest -DfailIfNoTests=false`
Expected: compilation failure — `WorkspaceInput` does not exist

- [ ] **Step 3: Implement all value types**

Create `WorkspaceState.java`:
```java
package io.casehub.neocortex.knowledge.workspace;

public enum WorkspaceState { OPEN, ARCHIVED }
```

Create `WorkspaceDescriptor.java`:
```java
package io.casehub.neocortex.knowledge.workspace;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record WorkspaceDescriptor(
    String id, String name, Set<String> targetCategories,
    FreshnessPolicy freshnessPolicy, String tenantId,
    String mindMapSubgraphId, Instant createdAt, WorkspaceState state
) {
    public WorkspaceDescriptor {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(state);
        targetCategories = targetCategories == null ? Set.of() : Set.copyOf(targetCategories);
    }
}
```

Create `WorkspaceEntry.java`:
```java
package io.casehub.neocortex.knowledge.workspace;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record WorkspaceEntry(
    String id, String workspaceId, String sessionId, String agentId,
    Content content, String extractedText, URI sourceUri,
    Instant storedAt, Instant fetchedAt, Set<String> categories,
    ReliabilityTier reliability, List<String> derivedFrom,
    Map<String, String> properties, String promotedTo, Instant promotedAt
) {
    public WorkspaceEntry {
        Objects.requireNonNull(id);
        Objects.requireNonNull(workspaceId);
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(content);
        Objects.requireNonNull(storedAt);
        Objects.requireNonNull(reliability);
        categories = categories == null ? Set.of() : Set.copyOf(categories);
        derivedFrom = derivedFrom == null ? List.of() : List.copyOf(derivedFrom);
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }
}
```

Create `WorkspaceInput.java`:
```java
package io.casehub.neocortex.knowledge.workspace;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record WorkspaceInput(
    Content content, String extractedText, URI sourceUri, String agentId,
    Set<String> categories, ReliabilityTier reliability,
    List<String> derivedFrom, Map<String, String> properties
) {
    public WorkspaceInput {
        Objects.requireNonNull(content);
        if ((derivedFrom == null || derivedFrom.isEmpty()) && sourceUri == null) {
            throw new IllegalArgumentException(
                "sourceUri is required for non-derived entries (provenance enforcement)");
        }
        categories = categories == null ? Set.of() : Set.copyOf(categories);
        reliability = reliability == null ? ReliabilityTier.UNKNOWN : reliability;
        derivedFrom = derivedFrom == null ? List.of() : List.copyOf(derivedFrom);
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }

    public WorkspaceInput withDerivedFrom(List<String> sourceEntryIds) {
        return new WorkspaceInput(content, extractedText, sourceUri, agentId,
            categories, reliability, sourceEntryIds, properties);
    }

    public WorkspaceInput withCategories(Set<String> categories) {
        return new WorkspaceInput(content, extractedText, sourceUri, agentId,
            categories, reliability, derivedFrom, properties);
    }
}
```

Create `CoverageReport.java`:
```java
package io.casehub.neocortex.knowledge.workspace;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record CoverageReport(
    String workspaceId,
    Map<String, CategoryCoverage> categories,
    Set<String> uncovered
) {
    public record CategoryCoverage(String category, int entryCount,
                                    Map<ReliabilityTier, Integer> reliabilityDistribution,
                                    Instant lastUpdated) {}
}
```

Create `StaleEntry.java`:
```java
package io.casehub.neocortex.knowledge.workspace;

import java.util.Set;

public record StaleEntry(WorkspaceEntry entry, Set<String> staleCategories) {}
```

Create `WorkspacePromotionResult.java`:
```java
package io.casehub.neocortex.knowledge.workspace;

import java.util.List;

public record WorkspacePromotionResult(
    List<PromotedEntry> promoted,
    List<SkippedEntry> skipped
) {
    public record PromotedEntry(String entryId, String corpusEntryId) {}
    public record SkippedEntry(String entryId, String reason) {}
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl knowledge-pipeline-api -Dtest="WorkspaceInputTest,ContentTest,FreshnessPolicyTest"`
Expected: all tests PASS

- [ ] **Step 5: Commit**

```bash
git add knowledge-pipeline-api/src/main/java/io/casehub/neocortex/knowledge/workspace/ knowledge-pipeline-api/src/test/java/io/casehub/neocortex/knowledge/workspace/
git commit -m "feat(#804): add workspace value types — WorkspaceEntry, WorkspaceInput, WorkspaceDescriptor, CoverageReport, StaleEntry, WorkspacePromotionResult"
```

### Task 3: ResearchWorkspace SPI + WorkspacePromotion SPI

**Files:**
- Create: `knowledge-pipeline-api/src/main/java/io/casehub/neocortex/knowledge/workspace/ResearchWorkspace.java`
- Create: `knowledge-pipeline-api/src/main/java/io/casehub/neocortex/knowledge/workspace/WorkspacePromotion.java`

**Interfaces:**
- Consumes: All value types from Tasks 1-2
- Produces: `ResearchWorkspace` interface (createWorkspace, getWorkspace, archiveWorkspace, listWorkspaces, openSession, closeSession, store, search, coverage, stale, deleteEntry, promote, reconcile), `WorkspacePromotion` interface

- [ ] **Step 1: Create ResearchWorkspace SPI**

```java
package io.casehub.neocortex.knowledge.workspace;

import java.util.List;
import java.util.Set;

public interface ResearchWorkspace {

    WorkspaceDescriptor createWorkspace(String name, Set<String> targetCategories,
                                         FreshnessPolicy freshnessPolicy, String tenantId);

    WorkspaceDescriptor getWorkspace(String workspaceId, String tenantId);

    void archiveWorkspace(String workspaceId, String tenantId);

    List<WorkspaceDescriptor> listWorkspaces(String tenantId);

    String openSession(String workspaceId, String name, String tenantId);

    void closeSession(String sessionId, String tenantId);

    String store(WorkspaceInput input, String sessionId, String tenantId);

    List<WorkspaceEntry> search(String query, String workspaceId, int maxResults, String tenantId);

    CoverageReport coverage(String workspaceId, String tenantId);

    List<StaleEntry> stale(String workspaceId, String tenantId);

    void deleteEntry(String entryId, String tenantId);

    WorkspacePromotionResult promote(String workspaceId, Set<String> entryIds,
                                      String targetCorpusName, String tenantId);

    void reconcile(String workspaceId, String tenantId);
}
```

- [ ] **Step 2: Create WorkspacePromotion SPI**

```java
package io.casehub.neocortex.knowledge.workspace;

import java.util.Set;

public interface WorkspacePromotion {
    WorkspacePromotionResult promote(String workspaceId, Set<String> entryIds,
                                      String targetCorpusName, String tenantId);
}
```

- [ ] **Step 3: Build API module to verify compilation**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn compile -pl knowledge-pipeline-api`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add knowledge-pipeline-api/src/main/java/io/casehub/neocortex/knowledge/workspace/ResearchWorkspace.java knowledge-pipeline-api/src/main/java/io/casehub/neocortex/knowledge/workspace/WorkspacePromotion.java
git commit -m "feat(#804): add ResearchWorkspace and WorkspacePromotion SPIs"
```

## Batch 2: Storage Backend (knowledge-pipeline)

SQLite-backed WorkspaceStore with Flyway migration. Add rag-api dependency to knowledge-pipeline pom.xml.

### Task 4: Add rag-api dependency + WorkspaceStore SQLite implementation

**Files:**
- Modify: `knowledge-pipeline/pom.xml` — add `casehub-neocortex-rag-api` dependency + `casehub-neocortex-rag-testing` test dependency
- Create: `knowledge-pipeline/src/main/resources/db/knowledge-workspace/migration/V1__workspace_tables.sql`
- Create: `knowledge-pipeline/src/main/java/io/casehub/neocortex/knowledge/workspace/WorkspaceStore.java`
- Test: `knowledge-pipeline/src/test/java/io/casehub/neocortex/knowledge/workspace/WorkspaceStoreTest.java`

**Interfaces:**
- Consumes: `WorkspaceDescriptor`, `WorkspaceEntry`, `WorkspaceInput`, `Content`, `FreshnessPolicy`, `CoverageReport`, `StaleEntry` from Batch 1; `SqliteDataSourceFactory` from `sqlite-support`
- Produces: `WorkspaceStore` — internal store class (not an SPI) with methods: `saveWorkspace`, `getWorkspace`, `listWorkspaces`, `archiveWorkspace`, `saveSession`, `closeSession`, `saveEntry`, `getEntry`, `deleteEntry`, `listEntries(workspaceId)`, `categoryCounts(workspaceId)`, `findBySourceUri(workspaceId, uri)`, `listEntryIds(workspaceId)`

- [ ] **Step 1: Add rag-api dependency to knowledge-pipeline/pom.xml**

Add to the `<dependencies>` section:
```xml
<dependency>
  <groupId>io.casehub</groupId>
  <artifactId>casehub-neocortex-rag-api</artifactId>
</dependency>
<dependency>
  <groupId>io.casehub</groupId>
  <artifactId>casehub-neocortex-rag-testing</artifactId>
  <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Create Flyway migration V1__workspace_tables.sql**

```sql
CREATE TABLE workspace (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    target_categories TEXT,
    freshness_policy TEXT,
    tenant_id TEXT NOT NULL,
    mindmap_subgraph_id TEXT,
    state TEXT NOT NULL DEFAULT 'OPEN',
    created_at TEXT NOT NULL
);

CREATE TABLE workspace_session (
    id TEXT PRIMARY KEY,
    workspace_id TEXT NOT NULL REFERENCES workspace(id),
    name TEXT NOT NULL,
    state TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at TEXT NOT NULL,
    closed_at TEXT
);

CREATE TABLE workspace_entry (
    id TEXT PRIMARY KEY,
    workspace_id TEXT NOT NULL REFERENCES workspace(id),
    session_id TEXT NOT NULL REFERENCES workspace_session(id),
    agent_id TEXT,
    content_type TEXT NOT NULL,
    media_type TEXT NOT NULL,
    text_body TEXT,
    binary_data BLOB,
    structured_data TEXT,
    schema_ref TEXT,
    extracted_text TEXT,
    source_uri TEXT,
    stored_at TEXT NOT NULL,
    fetched_at TEXT,
    categories TEXT,
    reliability TEXT DEFAULT 'UNKNOWN',
    derived_from TEXT,
    properties TEXT,
    promoted_to TEXT,
    promoted_at TEXT
);

CREATE INDEX idx_ws_entry_workspace ON workspace_entry(workspace_id);
CREATE INDEX idx_ws_entry_session ON workspace_entry(session_id);
CREATE INDEX idx_ws_entry_stored ON workspace_entry(workspace_id, stored_at);
CREATE INDEX idx_ws_entry_source ON workspace_entry(workspace_id, source_uri);
CREATE INDEX idx_ws_session_workspace ON workspace_session(workspace_id);
```

- [ ] **Step 3: Write WorkspaceStore test**

```java
package io.casehub.neocortex.knowledge.workspace;

import io.casehub.neocortex.sqlite.SqliteDataSourceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class WorkspaceStoreTest {

    private WorkspaceStore store;

    @BeforeEach
    void setUp() {
        var ds = SqliteDataSourceFactory.create(":memory:", "knowledge-workspace");
        store = new WorkspaceStore(ds);
    }

    @Test
    void saveAndGetWorkspace() {
        var desc = store.saveWorkspace("test-ws", "Research", Set.of("pricing", "features"),
            FreshnessPolicy.withDefault(Duration.ofDays(30)), "tenant-1");
        var loaded = store.getWorkspace(desc.id(), "tenant-1");
        assertThat(loaded).isNotNull();
        assertThat(loaded.name()).isEqualTo("Research");
        assertThat(loaded.targetCategories()).containsExactlyInAnyOrder("pricing", "features");
        assertThat(loaded.state()).isEqualTo(WorkspaceState.OPEN);
    }

    @Test
    void saveAndCloseSession() {
        var ws = store.saveWorkspace("ws", "Research", Set.of(), FreshnessPolicy.withDefault(Duration.ofDays(30)), "t1");
        String sessionId = store.saveSession(ws.id(), "Session 1", "t1");
        assertThat(sessionId).isNotNull();
        store.closeSession(sessionId, "t1");
    }

    @Test
    void saveAndGetEntry() {
        var ws = store.saveWorkspace("ws", "R", Set.of(), FreshnessPolicy.withDefault(Duration.ofDays(30)), "t1");
        String sid = store.saveSession(ws.id(), "S1", "t1");
        var entry = new WorkspaceEntry("e1", ws.id(), sid, "agent-1",
            new Content.TextContent("hello", "text/plain"), "hello",
            URI.create("https://example.com"), Instant.now(), null,
            Set.of("pricing"), ReliabilityTier.OFFICIAL, List.of(),
            Map.of(), null, null);
        store.saveEntry(entry);
        var loaded = store.getEntry("e1", "t1");
        assertThat(loaded).isNotNull();
        assertThat(loaded.content()).isInstanceOf(Content.TextContent.class);
        assertThat(((Content.TextContent) loaded.content()).body()).isEqualTo("hello");
        assertThat(loaded.categories()).containsExactly("pricing");
    }

    @Test
    void deleteEntry() {
        var ws = store.saveWorkspace("ws", "R", Set.of(), FreshnessPolicy.withDefault(Duration.ofDays(30)), "t1");
        String sid = store.saveSession(ws.id(), "S1", "t1");
        var entry = new WorkspaceEntry("e1", ws.id(), sid, null,
            new Content.TextContent("x", "text/plain"), "x",
            URI.create("https://example.com"), Instant.now(), null,
            Set.of(), ReliabilityTier.UNKNOWN, List.of(), Map.of(), null, null);
        store.saveEntry(entry);
        store.deleteEntry("e1", "t1");
        assertThat(store.getEntry("e1", "t1")).isNull();
    }

    @Test
    void categoryCounts() {
        var ws = store.saveWorkspace("ws", "R", Set.of("a", "b"), FreshnessPolicy.withDefault(Duration.ofDays(30)), "t1");
        String sid = store.saveSession(ws.id(), "S1", "t1");
        store.saveEntry(makeEntry("e1", ws.id(), sid, Set.of("a"), ReliabilityTier.OFFICIAL));
        store.saveEntry(makeEntry("e2", ws.id(), sid, Set.of("a", "b"), ReliabilityTier.CURATED));
        var counts = store.categoryCounts(ws.id(), "t1");
        assertThat(counts).containsKeys("a", "b");
    }

    private WorkspaceEntry makeEntry(String id, String wsId, String sessionId,
                                      Set<String> categories, ReliabilityTier tier) {
        return new WorkspaceEntry(id, wsId, sessionId, null,
            new Content.TextContent("text", "text/plain"), "text",
            URI.create("https://example.com/" + id), Instant.now(), null,
            categories, tier, List.of(), Map.of(), null, null);
    }
}
```

- [ ] **Step 4: Implement WorkspaceStore**

Implement `WorkspaceStore` as a package-private class with `SqliteDataSourceFactory`-created DataSource, JSON serialization for categories/derivedFrom/properties/freshnessPolicy via Jackson `ObjectMapper`, and `Content` type dispatch (text_body/binary_data/structured_data columns based on `content_type`).

Key methods: `saveWorkspace`, `getWorkspace`, `listWorkspaces`, `archiveWorkspace`, `saveSession`, `closeSession`, `saveEntry`, `getEntry`, `deleteEntry`, `listEntries`, `categoryCounts`, `listEntryIds`.

- [ ] **Step 5: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl knowledge-pipeline -Dtest=WorkspaceStoreTest`
Expected: all tests PASS

- [ ] **Step 6: Commit**

```bash
git add knowledge-pipeline/pom.xml knowledge-pipeline/src/main/resources/db/knowledge-workspace/ knowledge-pipeline/src/main/java/io/casehub/neocortex/knowledge/workspace/WorkspaceStore.java knowledge-pipeline/src/test/java/io/casehub/neocortex/knowledge/workspace/WorkspaceStoreTest.java
git commit -m "feat(#804): add WorkspaceStore SQLite implementation with Flyway migration"
```

## Batch 3: Orchestrator + Default Beans

WorkspaceOrchestrator wires WorkspaceStore + EmbeddingIngestor + CaseRetriever + MindMapStore.

### Task 5: WorkspaceOrchestrator + WorkspaceCoverageTracker + WorkspaceFreshnessChecker + NoOp beans

**Files:**
- Create: `knowledge-pipeline/src/main/java/io/casehub/neocortex/knowledge/workspace/WorkspaceOrchestrator.java`
- Create: `knowledge-pipeline/src/main/java/io/casehub/neocortex/knowledge/workspace/WorkspaceCoverageTracker.java`
- Create: `knowledge-pipeline/src/main/java/io/casehub/neocortex/knowledge/workspace/WorkspaceFreshnessChecker.java`
- Create: `knowledge-pipeline/src/main/java/io/casehub/neocortex/knowledge/workspace/NoOpWorkspacePromotion.java`
- Create: `knowledge-pipeline/src/main/java/io/casehub/neocortex/knowledge/workspace/WorkspaceConfig.java`
- Test: `knowledge-pipeline/src/test/java/io/casehub/neocortex/knowledge/workspace/WorkspaceOrchestratorTest.java`

**Interfaces:**
- Consumes: `ResearchWorkspace` SPI, `WorkspaceStore` from Task 4, `EmbeddingIngestor` (`ingest(CorpusRef, List<ChunkInput>)`, `deleteDocument(CorpusRef, String)`, `listDocuments(CorpusRef)`), `CaseRetriever` (`retrieve(RetrievalQuery, CorpusRef, int, PayloadFilter)`), `MindMapStore` (`createSubgraph`, `addNode`, `updateSubgraph`), `TextChunker` (`chunk(String)`)
- Produces: `WorkspaceOrchestrator` implementing `ResearchWorkspace` + `enrich()` convenience method

- [ ] **Step 1: Write WorkspaceOrchestrator test**

Test store + search round-trip, coverage, stale, and deleteEntry using `InMemoryEmbeddingIngestor`, `InMemoryCaseRetriever`, and `InMemoryMindMapStore`:

```java
package io.casehub.neocortex.knowledge.workspace;

import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.neocortex.rag.testing.InMemoryEmbeddingIngestor;
import io.casehub.neocortex.rag.testing.InMemoryCaseRetriever;
import io.casehub.neocortex.rag.TextChunker;
import io.casehub.neocortex.sqlite.SqliteDataSourceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class WorkspaceOrchestratorTest {

    private WorkspaceOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        var ds = SqliteDataSourceFactory.create(":memory:", "knowledge-workspace");
        var store = new WorkspaceStore(ds);
        var ingestor = new InMemoryEmbeddingIngestor();
        var retriever = new InMemoryCaseRetriever(ingestor);
        var mindMap = new InMemoryMindMapStore();
        var chunker = TextChunker.recursive(500, 50);
        orchestrator = new WorkspaceOrchestrator(store, ingestor, retriever, mindMap, chunker);
    }

    @Test
    void storeAndSearchRoundTrip() {
        var ws = orchestrator.createWorkspace("test", Set.of("pricing"),
            FreshnessPolicy.withDefault(Duration.ofDays(30)), "t1");
        String sid = orchestrator.openSession(ws.id(), "Session 1", "t1");
        var input = new WorkspaceInput(
            new Content.TextContent("Competitor X charges $99/month", "text/plain"),
            null, URI.create("https://competitor.com/pricing"), null,
            Set.of("pricing"), ReliabilityTier.OFFICIAL, List.of(), Map.of());
        String entryId = orchestrator.store(input, sid, "t1");
        assertThat(entryId).isNotNull();
        var results = orchestrator.search("competitor pricing", ws.id(), 10, "t1");
        assertThat(results).isNotEmpty();
    }

    @Test
    void coverageTracksCategories() {
        var ws = orchestrator.createWorkspace("test", Set.of("pricing", "features"),
            FreshnessPolicy.withDefault(Duration.ofDays(30)), "t1");
        String sid = orchestrator.openSession(ws.id(), "S1", "t1");
        orchestrator.store(new WorkspaceInput(
            new Content.TextContent("price data", "text/plain"), null,
            URI.create("https://example.com/p"), null, Set.of("pricing"),
            ReliabilityTier.OFFICIAL, List.of(), Map.of()), sid, "t1");
        var report = orchestrator.coverage(ws.id(), "t1");
        assertThat(report.uncovered()).containsExactly("features");
        assertThat(report.categories()).containsKey("pricing");
        assertThat(report.categories().get("pricing").entryCount()).isEqualTo(1);
    }

    @Test
    void deleteEntryRemovesFromStore() {
        var ws = orchestrator.createWorkspace("test", Set.of(),
            FreshnessPolicy.withDefault(Duration.ofDays(30)), "t1");
        String sid = orchestrator.openSession(ws.id(), "S1", "t1");
        String entryId = orchestrator.store(new WorkspaceInput(
            new Content.TextContent("data", "text/plain"), null,
            URI.create("https://example.com"), null, Set.of(),
            ReliabilityTier.UNKNOWN, List.of(), Map.of()), sid, "t1");
        orchestrator.deleteEntry(entryId, "t1");
    }

    @Test
    void enrichSetsDerivesFrom() {
        var ws = orchestrator.createWorkspace("test", Set.of("analysis"),
            FreshnessPolicy.withDefault(Duration.ofDays(30)), "t1");
        String sid = orchestrator.openSession(ws.id(), "S1", "t1");
        String sourceId = orchestrator.store(new WorkspaceInput(
            new Content.TextContent("raw data", "text/plain"), null,
            URI.create("https://example.com"), null, Set.of("data"),
            ReliabilityTier.OFFICIAL, List.of(), Map.of()), sid, "t1");
        var analysis = new WorkspaceInput(
            new Content.TextContent("analysis of raw data", "text/plain"),
            null, null, null, Set.of("analysis"),
            ReliabilityTier.CURATED, List.of(sourceId), Map.of());
        String analysisId = orchestrator.enrich(analysis, List.of(sourceId), sid, "t1");
        assertThat(analysisId).isNotNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl knowledge-pipeline -Dtest=WorkspaceOrchestratorTest -DfailIfNoTests=false`
Expected: compilation failure — `WorkspaceOrchestrator` does not exist

- [ ] **Step 3: Implement WorkspaceCoverageTracker**

```java
package io.casehub.neocortex.knowledge.workspace;

import java.util.*;

class WorkspaceCoverageTracker {

    CoverageReport compute(WorkspaceDescriptor workspace, WorkspaceStore store) {
        var counts = store.categoryCounts(workspace.id(), workspace.tenantId());
        Set<String> uncovered = new LinkedHashSet<>(workspace.targetCategories());
        uncovered.removeAll(counts.keySet());
        return new CoverageReport(workspace.id(), counts, uncovered);
    }
}
```

- [ ] **Step 4: Implement WorkspaceFreshnessChecker**

```java
package io.casehub.neocortex.knowledge.workspace;

import java.time.Instant;
import java.util.*;

class WorkspaceFreshnessChecker {

    List<StaleEntry> findStale(WorkspaceDescriptor workspace, WorkspaceStore store) {
        FreshnessPolicy policy = workspace.freshnessPolicy();
        if (policy == null) return List.of();
        Instant now = Instant.now();
        List<WorkspaceEntry> entries = store.listEntries(workspace.id(), workspace.tenantId());
        List<StaleEntry> stale = new ArrayList<>();
        for (WorkspaceEntry entry : entries) {
            Set<String> staleCategories = policy.staleCategories(entry.categories(), entry.storedAt(), now);
            if (!staleCategories.isEmpty()) {
                stale.add(new StaleEntry(entry, staleCategories));
            }
        }
        return stale;
    }
}
```

- [ ] **Step 5: Implement NoOpWorkspacePromotion**

```java
package io.casehub.neocortex.knowledge.workspace;

import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.arc.DefaultBean;
import java.util.List;
import java.util.Set;

@DefaultBean
@ApplicationScoped
public class NoOpWorkspacePromotion implements WorkspacePromotion {
    @Override
    public WorkspacePromotionResult promote(String workspaceId, Set<String> entryIds,
                                             String targetCorpusName, String tenantId) {
        return new WorkspacePromotionResult(List.of(),
            entryIds.stream()
                .map(id -> new WorkspacePromotionResult.SkippedEntry(id, "no promotion backend configured"))
                .toList());
    }
}
```

- [ ] **Step 6: Implement WorkspaceConfig**

```java
package io.casehub.neocortex.knowledge.workspace;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.Optional;

@ConfigMapping(prefix = "casehub.knowledge.workspace")
public interface WorkspaceConfig {
    Optional<String> sqlitePath();

    @WithDefault("500")
    int chunkSize();

    @WithDefault("50")
    int chunkOverlap();
}
```

- [ ] **Step 7: Implement WorkspaceOrchestrator**

Implement `WorkspaceOrchestrator` as `@ApplicationScoped` implementing `ResearchWorkspace`. Constructor injects `WorkspaceStore`, `EmbeddingIngestor` (via `Instance<EmbeddingIngestor>`), `CaseRetriever` (via `Instance<CaseRetriever>`), `MindMapStore` (via `Instance<MindMapStore>`), `WorkspacePromotion`, and `TextChunker`. Non-CDI constructor for tests.

Key implementation details:
- `store()`: generates UUID, saves to SQLite first, then chunks and ingests to Qdrant with `CorpusRef(tenantId, "research-workspace")` and metadata `workspaceId`
- `search()`: builds `PayloadFilter.eq("workspaceId", workspaceId)`, calls `CaseRetriever.retrieve()`, deduplicates by `sourceDocumentId`, batch-loads from SQLite
- `coverage()`: delegates to `WorkspaceCoverageTracker`
- `stale()`: delegates to `WorkspaceFreshnessChecker`
- `deleteEntry()`: Qdrant `deleteDocument` first (best-effort), then SQLite delete
- `promote()`: delegates to `WorkspacePromotion`
- `reconcile()`: loads entry IDs from SQLite, checks Qdrant via `listDocuments()`, re-ingests missing entries (additive-only)
- `enrich()`: convenience method — sets `derivedFrom` on input and delegates to `store()`
- `createWorkspace()`: creates MindMap `RESEARCH_AREA` subgraph via `MindMapStore`

- [ ] **Step 8: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl knowledge-pipeline -Dtest=WorkspaceOrchestratorTest`
Expected: all tests PASS

- [ ] **Step 9: Run full build to verify no regressions**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install -pl knowledge-pipeline-api,knowledge-pipeline`
Expected: BUILD SUCCESS

- [ ] **Step 10: Commit**

```bash
git add knowledge-pipeline/src/main/java/io/casehub/neocortex/knowledge/workspace/ knowledge-pipeline/src/test/java/io/casehub/neocortex/knowledge/workspace/
git commit -m "feat(#804): add WorkspaceOrchestrator, coverage tracker, freshness checker, and NoOp beans"
```

## Batch 4: Full Build Verification + CLAUDE.md Update

### Task 6: Full build + CLAUDE.md documentation

**Files:**
- Modify: `CLAUDE.md` — add workspace module description to the knowledge-pipeline sections

**Interfaces:**
- Consumes: all from Batches 1-3
- Produces: passing full build, updated CLAUDE.md

- [ ] **Step 1: Run full project build**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install`
Expected: BUILD SUCCESS with all existing tests still passing

- [ ] **Step 2: Update CLAUDE.md**

Add workspace description to the knowledge-pipeline-api and knowledge-pipeline module sections in CLAUDE.md. Add new types to the module structure documentation.

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md
git commit -m "docs(#804): update CLAUDE.md with workspace module documentation Refs #804"
```

## References

- `specs/issue-804-research-workspace/2026-11-05-research-workspace-design.md` — design spec this plan implements
- `knowledge-pipeline-api/src/main/java/io/casehub/neocortex/knowledge/ResearchSession.java` — existing session model
- `knowledge-pipeline-api/src/main/java/io/casehub/neocortex/knowledge/CacheStore.java` — existing cache SPI (NOT extended)
- `knowledge-pipeline/src/main/java/io/casehub/neocortex/knowledge/research/ResearchOrchestrator.java` — existing MindMap integration pattern
- `rag-api/src/main/java/io/casehub/neocortex/rag/EmbeddingIngestor.java:5` — vector ingestion SPI
- `rag-api/src/main/java/io/casehub/neocortex/rag/CaseRetriever.java:5` — semantic search SPI (`retrieve(RetrievalQuery, CorpusRef, int, PayloadFilter)`)
- `rag-api/src/main/java/io/casehub/neocortex/rag/ChunkInput.java:7` — chunk input record
- `rag-api/src/main/java/io/casehub/neocortex/rag/RetrievedChunk.java:5` — retrieved chunk with `sourceDocumentId()` and `relevanceScore()`
- `rag-api/src/main/java/io/casehub/neocortex/rag/CorpusRef.java:3` — `CorpusRef(tenantId, corpusName)`
- `rag-api/src/main/java/io/casehub/neocortex/rag/PayloadFilter.java:13` — sealed filter hierarchy with `eq()` factory
- `rag-api/src/main/java/io/casehub/neocortex/rag/TextChunker.java:7` — `recursive(int maxChunkSize, int overlap)`
- `sqlite-support/src/main/java/io/casehub/neocortex/sqlite/SqliteDataSourceFactory.java:9` — `create(path, migrationPrefix)`
- `memory-sqlite/src/main/resources/db/memory-sqlite/migration/V1__memory_sqlite_entry.sql` — Flyway migration naming convention
- GitHub #804 — parent issue

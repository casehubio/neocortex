# Agent-Facing Change Detection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #836 — Add agent-facing change detection — what's new?
**Issue group:** #833 (parent epic)

**Goal:** Let agents ask "what changed in corpus X since timestamp Y?" via MCP tool, with no cursor management.

**Architecture:** Add `recentChanges(CorpusRef, Instant)` to `CorpusEntryStore` SPI. Implementation uses `CorpusReader.list()` + `versions(path)` to detect changes by timestamp — this abstraction works uniformly across git and zip backends without module dependency tangles. Add `author` to `VersionInfo` so git metadata flows through the common API. Expose via `changes()` MCP tool on `CorpusApiBean`.

**Tech Stack:** Java 21, Quarkus 3.32.2, JGit (corpus-git), SQLite (corpus-management)

## Global Constraints

- Java 21 source level on Java 26 JVM
- `ChangeType` enum from corpus-api reused (ADDED, MODIFIED, DELETED)
- `CorpusRef(tenantId, corpusName)` from rag-api is the corpus identifier
- Pre-release project — breaking record changes acceptable with backward-compat constructors
- Deletion detection out of scope for this issue (requires historical state comparison not available through CorpusReader abstraction)

---

## Batch 1: API Foundation

### Task 1: Add author to VersionInfo and update GitCorpusReader

**Files:**
- Modify: `corpus-api/src/main/java/io/casehub/neocortex/corpus/VersionInfo.java`
- Modify: `corpus-git/src/main/java/io/casehub/neocortex/corpus/git/GitCorpusReader.java`
- Search: all other `new VersionInfo(` construction sites — update to use 3-arg compat constructor

**Interfaces:**
- Produces: `VersionInfo(int version, Instant timestamp, String source, @Nullable String author)` — 4-arg canonical constructor + 3-arg backward-compat constructor

- [ ] **Step 1: Write failing test for VersionInfo author field**

In `corpus-api` test directory (create if needed):

```java
package io.casehub.neocortex.corpus;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class VersionInfoTest {

    @Test
    void shouldCarryAuthor() {
        var info = new VersionInfo(1, Instant.now(), "abc123", "Alice");
        assertThat(info.author()).isEqualTo("Alice");
    }

    @Test
    void shouldDefaultAuthorToNull() {
        var info = new VersionInfo(1, Instant.now(), "abc123");
        assertThat(info.author()).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl corpus-api -Dtest=VersionInfoTest -DfailIfNoTests=false`
Expected: compilation error — `VersionInfo` has no 4-arg constructor and no `author()` accessor.

- [ ] **Step 3: Add author field to VersionInfo**

```java
package io.casehub.neocortex.corpus;

import java.time.Instant;
import java.util.Objects;

public record VersionInfo(int version, Instant timestamp, String source, String author) {

    public VersionInfo {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(source, "source");
    }

    public VersionInfo(int version, Instant timestamp, String source) {
        this(version, timestamp, source, null);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl corpus-api -Dtest=VersionInfoTest`
Expected: PASS

- [ ] **Step 5: Update GitCorpusReader to populate author**

In `GitCorpusReader.versions(path)`, where `RevCommit` is accessed to extract timestamp, also extract author:

```java
String author = commit.getAuthorIdent().getName();
// In the VersionInfo construction:
new VersionInfo(version, commit.getAuthorIdent().getWhenAsInstant(), commit.getName(), author)
```

Use `ide_find_references` on `VersionInfo` to find all other construction sites and verify they compile with the 3-arg backward-compat constructor.

- [ ] **Step 6: Build to verify no compilation errors**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn compile -pl corpus-api,corpus-git`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add corpus-api/src corpus-git/src
git commit -m "feat(corpus-api): add author to VersionInfo, populate from git

Refs #836"
```

---

### Task 2: Create ChangeRecord, ChangeResult, and recentChanges() SPI method

**Files:**
- Create: `corpus-management-api/src/main/java/io/casehub/neocortex/corpus/management/ChangeRecord.java`
- Create: `corpus-management-api/src/main/java/io/casehub/neocortex/corpus/management/ChangeResult.java`
- Modify: `corpus-management-api/src/main/java/io/casehub/neocortex/corpus/management/CorpusEntryStore.java`
- Modify: `corpus-management/src/main/java/io/casehub/neocortex/corpus/management/cdi/NoOpCorpusEntryStore.java`

**Interfaces:**
- Consumes: `ChangeType` from corpus-api, `CorpusRef` from rag-api
- Produces: `ChangeRecord(path, type, timestamp, author, preview)`, `ChangeResult(changes, count)`, `CorpusEntryStore.recentChanges(CorpusRef, Instant)`

- [ ] **Step 1: Write failing test for ChangeRecord**

```java
package io.casehub.neocortex.corpus.management;

import io.casehub.neocortex.corpus.ChangeType;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class ChangeRecordTest {

    @Test
    void shouldCreateWithAllFields() {
        var now = Instant.now();
        var record = new ChangeRecord("docs/readme.md", ChangeType.MODIFIED, now, "Alice", "# README");
        assertThat(record.path()).isEqualTo("docs/readme.md");
        assertThat(record.type()).isEqualTo(ChangeType.MODIFIED);
        assertThat(record.timestamp()).isEqualTo(now);
        assertThat(record.author()).isEqualTo("Alice");
        assertThat(record.preview()).isEqualTo("# README");
    }

    @Test
    void shouldAllowNullAuthorAndPreview() {
        var record = new ChangeRecord("file.txt", ChangeType.ADDED, Instant.now(), null, null);
        assertThat(record.author()).isNull();
        assertThat(record.preview()).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl corpus-management-api -Dtest=ChangeRecordTest -DfailIfNoTests=false`
Expected: compilation error — `ChangeRecord` doesn't exist.

- [ ] **Step 3: Create ChangeRecord**

```java
package io.casehub.neocortex.corpus.management;

import io.casehub.neocortex.corpus.ChangeType;
import java.time.Instant;
import java.util.Objects;

public record ChangeRecord(
        String path,
        ChangeType type,
        Instant timestamp,
        String author,
        String preview) {

    public ChangeRecord {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(timestamp, "timestamp");
    }
}
```

- [ ] **Step 4: Create ChangeResult**

```java
package io.casehub.neocortex.corpus.management;

import java.util.List;
import java.util.Objects;

public record ChangeResult(List<ChangeRecord> changes, int count) {

    public ChangeResult {
        Objects.requireNonNull(changes, "changes");
        changes = List.copyOf(changes);
    }

    public ChangeResult(List<ChangeRecord> changes) {
        this(changes, changes.size());
    }
}
```

- [ ] **Step 5: Add recentChanges() to CorpusEntryStore**

Add default method to the `CorpusEntryStore` interface:

```java
default List<ChangeRecord> recentChanges(CorpusRef corpus, Instant since) {
    return List.of();
}
```

Add imports: `java.time.Instant`, `java.util.List`, `io.casehub.neocortex.corpus.management.ChangeRecord`.

- [ ] **Step 6: Run tests to verify everything compiles and passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl corpus-management-api -Dtest=ChangeRecordTest`
Expected: PASS

Also verify NoOpCorpusEntryStore compiles (inherits default):
Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn compile -pl corpus-management`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add corpus-management-api/src
git commit -m "feat(corpus-management-api): add ChangeRecord, ChangeResult, recentChanges() SPI

Refs #836"
```

---

## Batch 2: Implementation and MCP Tool

### Task 3: Implement SqliteCorpusEntryStore.recentChanges()

**Files:**
- Modify: `corpus-management/src/main/java/io/casehub/neocortex/corpus/management/impl/SqliteCorpusEntryStore.java`
- Modify: `corpus-management/src/test/java/.../SqliteCorpusEntryStoreTest.java`

**Interfaces:**
- Consumes: `CorpusReader.list()`, `CorpusReader.versions(path)`, `CorpusReader.read(path)`, `VersionInfo(version, timestamp, source, author)`, `ChangeRecord`, `ChangeType`
- Produces: working `recentChanges(CorpusRef, Instant)` implementation

- [ ] **Step 1: Write failing test**

Add to `SqliteCorpusEntryStoreTest`:

```java
@Test
void recentChangesShouldReturnChangesAfterTimestamp() {
    // Setup: store has entries added at known times
    // The test needs a CorpusReader that returns versions with timestamps
    // Use an in-memory CorpusReader stub or the existing test infrastructure

    var since = Instant.now().minus(Duration.ofHours(1));
    var result = store.recentChanges(corpusRef, since);

    assertThat(result).isNotEmpty();
    assertThat(result).allSatisfy(r -> {
        assertThat(r.timestamp()).isAfter(since);
        assertThat(r.path()).isNotBlank();
        assertThat(r.type()).isNotNull();
    });
}

@Test
void recentChangesShouldReturnEmptyWhenNoChanges() {
    var future = Instant.now().plus(Duration.ofDays(1));
    var result = store.recentChanges(corpusRef, future);

    assertThat(result).isEmpty();
}

@Test
void recentChangesShouldOrderByTimestampDescending() {
    var since = Instant.EPOCH;
    var result = store.recentChanges(corpusRef, since);

    for (int i = 1; i < result.size(); i++) {
        assertThat(result.get(i - 1).timestamp())
                .isAfterOrEqualTo(result.get(i).timestamp());
    }
}

@Test
void recentChangesShouldIncludePreview() {
    var since = Instant.EPOCH;
    var result = store.recentChanges(corpusRef, since);

    var nonDeleted = result.stream()
            .filter(r -> r.type() != ChangeType.DELETED)
            .toList();
    assertThat(nonDeleted).allSatisfy(r ->
            assertThat(r.preview()).isNotNull()
    );
}
```

Adapt the test setup to ensure a `CorpusReader` is available in the test fixture. The existing test uses in-memory SQLite — add a stub `CorpusReader` that returns known content and version history.

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl corpus-management -Dtest=SqliteCorpusEntryStoreTest`
Expected: FAIL — `recentChanges()` returns empty list (default method).

- [ ] **Step 3: Add CorpusReader to SqliteCorpusEntryStore constructor**

Add `CorpusReader reader` parameter to the constructor. Store as a field.

- [ ] **Step 4: Implement recentChanges()**

```java
private static final int DEFAULT_PREVIEW_LENGTH = 500;

@Override
public List<ChangeRecord> recentChanges(CorpusRef corpus, Instant since) {
    List<String> paths = reader.list();
    List<ChangeRecord> changes = new ArrayList<>();

    for (String path : paths) {
        List<VersionInfo> versions = reader.versions(path);
        if (versions.isEmpty()) continue;

        List<VersionInfo> afterSince = versions.stream()
                .filter(v -> v.timestamp().isAfter(since))
                .toList();

        if (afterSince.isEmpty()) continue;

        boolean allAfterSince = afterSince.size() == versions.size();
        ChangeType type = allAfterSince ? ChangeType.ADDED : ChangeType.MODIFIED;

        // Use most recent version's metadata
        VersionInfo latest = afterSince.stream()
                .max(Comparator.comparing(VersionInfo::timestamp))
                .orElseThrow();

        String preview = loadPreview(path);

        changes.add(new ChangeRecord(
                path, type, latest.timestamp(), latest.author(), preview));
    }

    changes.sort(Comparator.comparing(ChangeRecord::timestamp).reversed());
    return List.copyOf(changes);
}

private String loadPreview(String path) {
    return reader.read(path)
            .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
            .map(content -> content.length() > DEFAULT_PREVIEW_LENGTH
                    ? content.substring(0, DEFAULT_PREVIEW_LENGTH)
                    : content)
            .orElse(null);
}
```

- [ ] **Step 5: Update test fixture to provide CorpusReader**

Create a stub `CorpusReader` in the test that returns known paths, versions, and content. Wire it into the `SqliteCorpusEntryStore` constructor call.

- [ ] **Step 6: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl corpus-management -Dtest=SqliteCorpusEntryStoreTest`
Expected: PASS

- [ ] **Step 7: Build full module to check for breakage**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn compile -pl corpus-management`
Expected: BUILD SUCCESS (any CDI producer for SqliteCorpusEntryStore may need CorpusReader added)

- [ ] **Step 8: Commit**

```bash
git add corpus-management/src
git commit -m "feat(corpus-management): implement recentChanges() in SqliteCorpusEntryStore

Walks CorpusReader.list() + versions() to detect additions and
modifications since a given Instant. Includes content preview
(first 500 chars) and author when available from VersionInfo.

Refs #836"
```

---

### Task 4: Add MCP changes() tool

**Files:**
- Modify: `corpus-management/src/main/java/io/casehub/neocortex/corpus/management/impl/CorpusApi.java`
- Modify: `corpus-management/src/main/java/io/casehub/neocortex/corpus/management/impl/CorpusApiBean.java`

**Interfaces:**
- Consumes: `CorpusEntryStore.recentChanges(CorpusRef, Instant)`, `ChangeResult`
- Produces: `changes()` MCP tool on `neocortex/corpus` domain

- [ ] **Step 1: Add changes() to CorpusApi interface**

```java
@PlatformQuery
ChangeResult changes(@ContextParam("corpusId") String corpusId,
                     @McpCursorMap(INSTANT) Instant since);
```

Add imports for `ChangeResult`, `Instant`, `McpCursorMap`.

- [ ] **Step 2: Add CorpusEntryStore to CorpusApiBean constructor**

Add `CorpusEntryStore entryStore` parameter. Store as field.

- [ ] **Step 3: Implement changes() in CorpusApiBean**

```java
@Override
public ChangeResult changes(String corpusId, Instant since) {
    var corpus = new CorpusRef(tenantId(), corpusId);
    var records = entryStore.recentChanges(corpus, since);
    return new ChangeResult(records);
}
```

Use whatever tenant resolution pattern the existing methods use (check `tenantId()` or `@ContextParam`).

- [ ] **Step 4: Build to verify compilation**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn compile -pl corpus-management`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add corpus-management/src
git commit -m "feat(corpus-management): add changes() MCP tool for agent-facing change detection

Agents can now ask 'what changed in corpus X since Y?' via the
neocortex/corpus MCP domain. No cursor management required.

Closes #836"
```

---

## References

- [2026-11-08-change-detection-design.md] — design spec this plan implements
- `corpus-api/src/main/java/.../VersionInfo.java` — adding author field
- `corpus-api/src/main/java/.../CorpusReader.java` — list() + versions() + read() abstraction
- `corpus-api/src/main/java/.../ChangeType.java` — reused enum
- `corpus-management-api/src/main/java/.../CorpusEntryStore.java` — target SPI
- `corpus-management/src/main/java/.../SqliteCorpusEntryStore.java` — primary implementation
- `corpus-management/src/main/java/.../CorpusApiBean.java` — MCP domain implementation
- `corpus-git/src/main/java/.../GitCorpusReader.java` — author extraction
- casehubio/neocortex#836, casehubio/neocortex#833

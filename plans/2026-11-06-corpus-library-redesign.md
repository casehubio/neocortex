# Corpus Library Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #789 — Cognitive knowledge management (epic)
**Issue group:** New issues to be filed under #789 before implementation

**Goal:** Unify the corpus infrastructure with git-aware change detection, a shared ingestion pipeline, and backend-neutral SPI terminology.

**Architecture:** Three-layer model: content backends (git/zip/flat) implement corpus-api SPIs; a shared IngestionPipeline in rag-core handles metadata extraction, chunking, and embedding; corpus-management provides catalogue, search, and provenance on top. Two content-flow paths (programmatic via CorpusEntryStore, change-detected via CorpusIngestionService) converge through the shared pipeline.

**Tech Stack:** Java 21, JGit 7.x (Eclipse, Apache-2.0), Quarkus CDI, SQLite, Qdrant

## Global Constraints

- Java 21 source, Java 26 JVM
- All new modules must be Tier 1 pure Java (zero CDI) unless explicitly CDI-wired
- corpus-api and corpus-git must remain Hortora-eligible (zero casehub domain deps)
- rag-core must remain framework-neutral (zero CDI, zero Spring)
- All SPI renames are mechanical — use `ide_refactor_rename` for method/field renames
- Every task ends with `mvn clean install -pl <module> -DskipTests=false` green
- JGit groupId: `org.eclipse.jgit`, artifactId: `org.eclipse.jgit`

---

## Batch 1: SPI Foundation — rename append→write, add flush(), VersionInfo.source

After this batch: all corpus-api SPIs use the new terminology. All implementations and tests updated. Build green.

### Task 1: Rename CorpusStore.append() → write() and add flush()

**Files:**
- Modify: `corpus-api/src/main/java/io/casehub/neocortex/corpus/CorpusStore.java:6-11`
- Modify: `corpus/src/main/java/io/casehub/neocortex/corpus/zip/FlatCorpusStore.java:28`
- Modify: `corpus/src/main/java/io/casehub/neocortex/corpus/zip/ZipCorpusStore.java:36`
- Modify: `corpus/src/main/java/io/casehub/neocortex/corpus/zip/CompositeCorpusStore.java:27`
- Modify: `corpus/src/test/java/io/casehub/neocortex/corpus/testing/CorpusStoreContractTest.java:13`
- Modify: all test files referencing `append()` (use `ide_find_references` on each `append` method)
- Test: existing contract tests pass after rename

**Interfaces:**
- Produces: `CorpusStore` interface with `write(String, byte[])`, `write(String, InputStream)`, `write(String, Path)`, `delete(String)`, `flush()`

- [ ] **Step 1: Rename append → write in the SPI**

Use `ide_refactor_rename` on each of the three `append` methods in `CorpusStore.java` to rename them to `write`. This updates all implementations and callers atomically.

```java
// CorpusStore.java after rename:
public interface CorpusStore {
    void write(String path, byte[] content);
    void write(String path, InputStream content);
    void write(String path, Path file);
    void delete(String path);
}
```

- [ ] **Step 2: Add flush() to CorpusStore**

Add `flush()` method to the interface using `ide_edit_member`:

```java
public interface CorpusStore {
    void write(String path, byte[] content);
    void write(String path, InputStream content);
    void write(String path, Path file);
    void delete(String path);
    void flush();
}
```

- [ ] **Step 3: Implement flush() in FlatCorpusStore**

Add no-op `flush()` — flat writes are immediately durable:

```java
@Override
public void flush() {
    // Flat writes are immediately durable — no buffering
}
```

- [ ] **Step 4: Implement flush() in ZipCorpusStore**

Add `flush()` that finalizes any pending operations:

```java
@Override
public void flush() {
    // Zip writes are immediately durable via Zip4j — no buffering
}
```

- [ ] **Step 5: Implement flush() in CompositeCorpusStore**

Delegate to both stores:

```java
@Override
public void flush() {
    zipStore.flush();
    flatStore.flush();
}
```

- [ ] **Step 6: Add flush() test to CorpusStoreContractTest**

```java
@Test
void flushDoesNotThrow() {
    store().write("test.md", "content".getBytes(UTF_8));
    assertDoesNotThrow(() -> store().flush());
}

@Test
void writeAndFlushThenRead() {
    store().write("flush-test.md", "hello".getBytes(UTF_8));
    store().flush();
    Optional<byte[]> result = reader().read("flush-test.md");
    assertTrue(result.isPresent());
    assertEquals("hello", new String(result.get(), UTF_8));
}
```

- [ ] **Step 7: Verify all tests pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean test -pl corpus-api,corpus`
Expected: all tests PASS

- [ ] **Step 8: Commit**

```bash
git add corpus-api/ corpus/
git commit -m "refactor: rename CorpusStore.append() to write(), add flush()

Refs #789"
```

### Task 2: Rename VersionInfo.zipFile → source

**Files:**
- Modify: `corpus-api/src/main/java/io/casehub/neocortex/corpus/VersionInfo.java:5`
- Modify: all references to `zipFile` field (use `ide_find_references`)
- Test: existing tests pass after rename

**Interfaces:**
- Produces: `VersionInfo(int version, Instant timestamp, String source)`

- [ ] **Step 1: Rename zipFile → source**

Use `ide_refactor_rename` on the `zipFile` field in `VersionInfo.java`. This updates all callers — `FlatCorpusStore.versions()`, `ZipCorpusStore.versions()`, and any tests that reference `zipFile()`.

```java
public record VersionInfo(int version, Instant timestamp, String source) {
    public VersionInfo {
        if (version < 1) throw new IllegalArgumentException("version must be >= 1");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }
}
```

- [ ] **Step 2: Verify all tests pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean test -pl corpus-api,corpus`
Expected: all tests PASS

- [ ] **Step 3: Commit**

```bash
git add corpus-api/ corpus/
git commit -m "refactor: rename VersionInfo.zipFile to source for backend neutrality

Refs #789"
```

---

## Batch 2: IngestionPipeline Extraction — converge the two Qdrant paths

After this batch: a shared IngestionPipeline in rag-core handles metadata extraction, chunking, and embedding. Both CorpusIngestionService and SqliteCorpusEntryStore use it. No duplicate pipeline logic.

### Task 3: Extract IngestionPipeline and IngestionContext into rag-core

**Files:**
- Create: `rag-core/src/main/java/io/casehub/neocortex/rag/runtime/IngestionPipeline.java`
- Create: `rag-core/src/main/java/io/casehub/neocortex/rag/runtime/IngestionContext.java`
- Create: `rag-core/src/test/java/io/casehub/neocortex/rag/runtime/IngestionPipelineTest.java`
- Test: `rag-core/src/test/java/io/casehub/neocortex/rag/runtime/IngestionPipelineTest.java`

**Interfaces:**
- Consumes: `EmbeddingIngestor` (from rag-api), `MetadataExtractor` (from rag-api), `ExtractionResult` (from rag-api), `ChunkInput` (from rag-api), `CorpusReader` (from corpus-api), `CorpusRef` (from rag-api)
- Produces: `IngestionPipeline` class with three `ingest()` overloads and `delete()`. `IngestionContext` record.

- [ ] **Step 1: Write the failing test — raw bytes overload**

```java
class IngestionPipelineTest {

    @Test
    void ingestRawBytesExtractsMetadataAndEmbeds() {
        var capturedChunks = new ArrayList<List<ChunkInput>>();
        EmbeddingIngestor ingestor = new EmbeddingIngestor() {
            @Override
            public void ingest(CorpusRef corpus, List<ChunkInput> chunks) {
                capturedChunks.add(chunks);
            }
            @Override
            public void deleteDocument(CorpusRef corpus, String documentId) {}
        };
        MetadataExtractor extractor = (path, content) ->
            new ExtractionResult("extracted body", Map.of("title", "Test"));

        var pipeline = new IngestionPipeline(ingestor);
        var context = new IngestionContext(
            new CorpusRef("tenant", "corpus"), extractor, null);

        pipeline.ingest("doc.md", "raw content".getBytes(UTF_8), context);

        assertEquals(1, capturedChunks.size());
        assertEquals(1, capturedChunks.get(0).size());
        assertEquals("extracted body", capturedChunks.get(0).get(0).content());
        assertEquals("doc.md", capturedChunks.get(0).get(0).sourceDocumentId());
        assertEquals("Test", capturedChunks.get(0).get(0).metadata().get("title"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl rag-core -Dtest=IngestionPipelineTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — class does not exist

- [ ] **Step 3: Create IngestionContext record**

```java
package io.casehub.neocortex.rag.runtime;

import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.MetadataExtractor;
import io.casehub.neocortex.rag.TextChunker;
import java.util.Objects;

public record IngestionContext(
    CorpusRef corpusRef,
    MetadataExtractor metadataExtractor,
    TextChunker textChunker
) {
    public IngestionContext {
        Objects.requireNonNull(corpusRef, "corpusRef must not be null");
    }
}
```

- [ ] **Step 4: Create IngestionPipeline class**

Extract the logic from `CorpusIngestionService.doProcessBinding()` (line 177) and `chunkDocument()` (line 295):

```java
package io.casehub.neocortex.rag.runtime;

import io.casehub.neocortex.corpus.CorpusReader;
import io.casehub.neocortex.rag.*;
import java.util.*;

public class IngestionPipeline {

    private final EmbeddingIngestor ingestor;

    public IngestionPipeline(EmbeddingIngestor ingestor) {
        this.ingestor = Objects.requireNonNull(ingestor);
    }

    public void ingest(String path, byte[] content, IngestionContext context) {
        Objects.requireNonNull(context.metadataExtractor(),
            "metadataExtractor required for raw-bytes ingestion");
        ExtractionResult extracted = context.metadataExtractor().extract(path, content);
        ingest(path, extracted, context);
    }

    public void ingest(String path, CorpusReader reader, IngestionContext context) {
        Optional<byte[]> content = reader.read(path);
        if (content.isEmpty()) return;
        ingest(path, content.get(), context);
    }

    public void ingest(String path, ExtractionResult extracted, IngestionContext context) {
        if (extracted.body() == null || extracted.body().isBlank()) return;
        List<ChunkInput> chunks = chunkDocument(path, extracted, context.textChunker());
        if (!chunks.isEmpty()) {
            ingestor.ingest(context.corpusRef(), chunks);
        }
    }

    public void delete(String path, CorpusRef corpusRef) {
        ingestor.deleteDocument(corpusRef, path);
    }

    private List<ChunkInput> chunkDocument(String path, ExtractionResult extracted,
                                            TextChunker chunker) {
        if (chunker == null) {
            return List.of(new ChunkInput(
                extracted.body(), path, extracted.metadata(), extracted.listMetadata()));
        }
        List<String> segments = chunker.chunk(extracted.body());
        List<ChunkInput> chunks = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i);
            if (segment != null && !segment.isBlank()) {
                chunks.add(new ChunkInput(
                    segment, path, extracted.metadata(), extracted.listMetadata()));
            }
        }
        return chunks;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl rag-core -Dtest=IngestionPipelineTest`
Expected: PASS

- [ ] **Step 6: Add test for pre-extracted overload**

```java
@Test
void ingestPreExtractedSkipsExtraction() {
    var capturedChunks = new ArrayList<List<ChunkInput>>();
    EmbeddingIngestor ingestor = new EmbeddingIngestor() {
        @Override
        public void ingest(CorpusRef corpus, List<ChunkInput> chunks) {
            capturedChunks.add(chunks);
        }
        @Override
        public void deleteDocument(CorpusRef corpus, String documentId) {}
    };

    var pipeline = new IngestionPipeline(ingestor);
    var context = new IngestionContext(
        new CorpusRef("tenant", "corpus"), null, null);
    var extracted = new ExtractionResult("pre-extracted", Map.of("key", "val"));

    pipeline.ingest("doc.md", extracted, context);

    assertEquals(1, capturedChunks.size());
    assertEquals("pre-extracted", capturedChunks.get(0).get(0).content());
}
```

- [ ] **Step 7: Add test for delete**

```java
@Test
void deleteCallsIngestorDeleteDocument() {
    var deleted = new ArrayList<String>();
    EmbeddingIngestor ingestor = new EmbeddingIngestor() {
        @Override
        public void ingest(CorpusRef corpus, List<ChunkInput> chunks) {}
        @Override
        public void deleteDocument(CorpusRef corpus, String documentId) {
            deleted.add(documentId);
        }
    };

    var pipeline = new IngestionPipeline(ingestor);
    pipeline.delete("doc.md", new CorpusRef("tenant", "corpus"));

    assertEquals(List.of("doc.md"), deleted);
}
```

- [ ] **Step 8: Add test for CorpusReader overload**

```java
@Test
void ingestFromReaderReadsAndExtracts() {
    var capturedChunks = new ArrayList<List<ChunkInput>>();
    EmbeddingIngestor ingestor = new EmbeddingIngestor() {
        @Override
        public void ingest(CorpusRef corpus, List<ChunkInput> chunks) {
            capturedChunks.add(chunks);
        }
        @Override
        public void deleteDocument(CorpusRef corpus, String documentId) {}
    };
    MetadataExtractor extractor = (path, content) ->
        new ExtractionResult(new String(content, UTF_8), Map.of());
    CorpusReader reader = new CorpusReader() {
        @Override public Optional<byte[]> read(String path) {
            return Optional.of("from reader".getBytes(UTF_8));
        }
        @Override public Optional<InputStream> readStream(String p) { return Optional.empty(); }
        @Override public Optional<byte[]> readVersion(String p, int v) { return Optional.empty(); }
        @Override public List<VersionInfo> versions(String p) { return List.of(); }
        @Override public List<String> list() { return List.of(); }
        @Override public List<String> list(String prefix) { return List.of(); }
        @Override public boolean exists(String p) { return true; }
    };

    var pipeline = new IngestionPipeline(ingestor);
    var context = new IngestionContext(
        new CorpusRef("tenant", "corpus"), extractor, null);

    pipeline.ingest("doc.md", reader, context);

    assertEquals("from reader", capturedChunks.get(0).get(0).content());
}
```

- [ ] **Step 9: Run all tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl rag-core -Dtest=IngestionPipelineTest`
Expected: all PASS

- [ ] **Step 10: Commit**

```bash
git add rag-core/
git commit -m "feat: extract IngestionPipeline into rag-core

Shared pipeline for metadata extraction, chunking, and embedding.
Three overloads: raw bytes (needs extraction), CorpusReader (reads
then extracts), and pre-extracted ExtractionResult (skips extraction).
Converges the two Qdrant paths into one pipeline.

Refs #789"
```

### Task 4: Rework CorpusIngestionService to delegate to IngestionPipeline

**Files:**
- Modify: `rag-core/src/main/java/io/casehub/neocortex/rag/runtime/CorpusIngestionService.java:24`
- Modify: `rag/src/test/java/io/casehub/neocortex/rag/runtime/CorpusIngestionServiceTest.java:31`
- Test: existing `CorpusIngestionServiceTest` passes after rework

**Interfaces:**
- Consumes: `IngestionPipeline` (from Task 3), `IngestionContext` (from Task 3)

- [ ] **Step 1: Add IngestionPipeline field to CorpusIngestionService**

Replace inline pipeline logic in `doProcessBinding()` with delegation to `IngestionPipeline`. The constructor already takes `EmbeddingIngestor` — create `IngestionPipeline` from it:

```java
// In constructor:
this.pipeline = new IngestionPipeline(ingestor);
```

- [ ] **Step 2: Replace inline pipeline in doProcessBinding()**

Replace the inline metadata extraction → chunking → ingest loop (lines ~200-280) with:

```java
IngestionContext context = new IngestionContext(
    binding.corpusRef(), binding.metadataExtractor(), textChunker);

for (ChangedEntry change : changeSet.entries()) {
    switch (change.type()) {
        case DELETED -> pipeline.delete(change.path(), binding.corpusRef());
        case ADDED, MODIFIED -> {
            pipeline.delete(change.path(), binding.corpusRef());
            pipeline.ingest(change.path(), binding.corpusReader(), context);
        }
    }
}
```

- [ ] **Step 3: Remove extracted code**

Remove the now-unused private methods that were extracted into `IngestionPipeline`: the inline extraction/chunking/ingest logic. Keep `chunkDocument()` only if it has callers outside the extracted path — otherwise remove it.

- [ ] **Step 4: Run existing tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl rag-core,rag -Dtest=CorpusIngestionServiceTest`
Expected: PASS — behavior unchanged, only delegation changed

- [ ] **Step 5: Commit**

```bash
git add rag-core/ rag/
git commit -m "refactor: CorpusIngestionService delegates to IngestionPipeline

Replaces inline extraction/chunking/embedding logic with delegation
to the shared IngestionPipeline. Behavior unchanged.

Refs #789"
```

### Task 5: Rework SqliteCorpusEntryStore to use IngestionPipeline

**Files:**
- Modify: `corpus-management/src/main/java/io/casehub/neocortex/corpus/management/impl/SqliteCorpusEntryStore.java:24`
- Modify: `corpus-management/src/test/java/io/casehub/neocortex/corpus/management/impl/SqliteCorpusEntryStoreTest.java:17`
- Test: existing `SqliteCorpusEntryStoreTest` passes after rework

**Interfaces:**
- Consumes: `IngestionPipeline` (from Task 3), `IngestionContext` (from Task 3), `ExtractionResult` (from rag-api)

- [ ] **Step 1: Add IngestionPipeline to constructor**

Replace direct `EmbeddingIngestor` dependency with `IngestionPipeline`:

```java
public SqliteCorpusEntryStore(DataSource dataSource, IngestionPipeline pipeline,
                               IngestionContext defaultContext) {
    this.pipeline = pipeline;
    this.defaultContext = defaultContext;
    // ... existing Flyway setup
}
```

- [ ] **Step 2: Replace ingestEmbedding() with pipeline delegation**

Replace the bypass at line 121 with:

```java
private void ingestEntry(CorpusRef corpusRef, CorpusEntry entry) {
    ExtractionResult extracted = new ExtractionResult(
        entry.body(),
        entry.metadata().entrySet().stream()
            .filter(e -> e.getValue() instanceof String)
            .collect(Collectors.toMap(Map.Entry::getKey, e -> (String) e.getValue())),
        entry.metadata().entrySet().stream()
            .filter(e -> e.getValue() instanceof List)
            .collect(Collectors.toMap(Map.Entry::getKey,
                e -> ((List<?>) e.getValue()).stream()
                    .map(String::valueOf).collect(Collectors.toList())))
    );
    pipeline.ingest(entry.id(), extracted, defaultContext);
}
```

- [ ] **Step 3: Update add() and update() to use ingestEntry()**

Replace `ingestEmbedding()` calls with `ingestEntry()`.

- [ ] **Step 4: Update tests**

Update `SqliteCorpusEntryStoreTest` to provide `IngestionPipeline` and `IngestionContext` in the test setup instead of a direct `EmbeddingIngestor`.

- [ ] **Step 5: Run tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl corpus-management`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add corpus-management/
git commit -m "refactor: SqliteCorpusEntryStore uses shared IngestionPipeline

Replaces direct EmbeddingIngestor bypass with delegation to IngestionPipeline
using the pre-extracted overload. Metadata and chunking now consistent
between programmatic and ChangeSource paths.

Refs #789"
```

---

## Batch 3: corpus-git Module — JGit-backed backend

After this batch: a new corpus-git module provides GitCorpusStore, GitCorpusReader, and GitChangeSource. Passes the existing CorpusStoreContractTest.

### Task 6: Create corpus-git module with GitCorpusReader

**Files:**
- Create: `corpus-git/pom.xml`
- Create: `corpus-git/src/main/java/io/casehub/neocortex/corpus/git/GitCorpusReader.java`
- Create: `corpus-git/src/test/java/io/casehub/neocortex/corpus/git/GitCorpusReaderTest.java`
- Modify: `pom.xml` (parent — add module)
- Test: `corpus-git/src/test/java/io/casehub/neocortex/corpus/git/GitCorpusReaderTest.java`

**Interfaces:**
- Consumes: `CorpusReader` (from corpus-api), `VersionInfo` (from corpus-api)
- Produces: `GitCorpusReader` implementing `CorpusReader` — reads from a configured git ref via JGit

- [ ] **Step 1: Create pom.xml for corpus-git**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>io.casehub</groupId>
        <artifactId>casehub-neocortex-parent</artifactId>
        <version>${revision}</version>
    </parent>
    <artifactId>casehub-neocortex-corpus-git</artifactId>
    <name>casehub-neocortex-corpus-git</name>
    <description>Git-backed corpus backend via JGit — Hortora-eligible, zero CDI</description>
    <dependencies>
        <dependency>
            <groupId>io.casehub</groupId>
            <artifactId>casehub-neocortex-corpus-api</artifactId>
        </dependency>
        <dependency>
            <groupId>org.eclipse.jgit</groupId>
            <artifactId>org.eclipse.jgit</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

Add JGit to the parent pom's `<dependencyManagement>` and add `corpus-git` to the `<modules>` list.

- [ ] **Step 2: Write failing test for read()**

```java
class GitCorpusReaderTest {

    private Path repoDir;
    private Repository repository;

    @BeforeEach
    void setUp() throws Exception {
        repoDir = Files.createTempDirectory("git-corpus-test");
        Git git = Git.init().setDirectory(repoDir.toFile()).call();
        repository = git.getRepository();

        Files.writeString(repoDir.resolve("test.md"), "hello world");
        git.add().addFilepattern("test.md").call();
        git.commit().setMessage("initial").call();
        git.close();
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    @Test
    void readReturnsCommittedContent() {
        var reader = new GitCorpusReader(repository, "HEAD");
        Optional<byte[]> result = reader.read("test.md");
        assertTrue(result.isPresent());
        assertEquals("hello world", new String(result.get(), UTF_8));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl corpus-git -Dtest=GitCorpusReaderTest#readReturnsCommittedContent`
Expected: FAIL — class does not exist

- [ ] **Step 4: Implement GitCorpusReader**

```java
package io.casehub.neocortex.corpus.git;

import io.casehub.neocortex.corpus.CorpusReader;
import io.casehub.neocortex.corpus.VersionInfo;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.treewalk.*;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import java.io.*;
import java.util.*;

public class GitCorpusReader implements CorpusReader {

    private final Repository repository;
    private final String refSpec;

    public GitCorpusReader(Repository repository, String refSpec) {
        this.repository = Objects.requireNonNull(repository);
        this.refSpec = Objects.requireNonNull(refSpec);
    }

    @Override
    public Optional<byte[]> read(String path) {
        try {
            ObjectId commitId = repository.resolve(refSpec);
            if (commitId == null) return Optional.empty();
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(commitId);
                try (TreeWalk treeWalk = TreeWalk.forPath(repository, path, commit.getTree())) {
                    if (treeWalk == null) return Optional.empty();
                    ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
                    return Optional.of(loader.getBytes());
                }
            }
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<InputStream> readStream(String path) {
        return read(path).map(ByteArrayInputStream::new);
    }

    @Override
    public Optional<byte[]> readVersion(String path, int version) {
        List<VersionInfo> history = versions(path);
        if (version < 1 || version > history.size()) return Optional.empty();
        VersionInfo vi = history.get(version - 1);
        try {
            ObjectId commitId = repository.resolve(vi.source());
            if (commitId == null) return Optional.empty();
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(commitId);
                try (TreeWalk treeWalk = TreeWalk.forPath(repository, path, commit.getTree())) {
                    if (treeWalk == null) return Optional.empty();
                    ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
                    return Optional.of(loader.getBytes());
                }
            }
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<VersionInfo> versions(String path) {
        try {
            ObjectId head = repository.resolve(refSpec);
            if (head == null) return List.of();
            try (RevWalk revWalk = new RevWalk(repository)) {
                revWalk.markStart(revWalk.parseCommit(head));
                revWalk.setTreeFilter(PathFilter.create(path));
                List<VersionInfo> versions = new ArrayList<>();
                for (RevCommit commit : revWalk) {
                    versions.add(new VersionInfo(0, commit.getAuthorIdent().getWhenAsInstant(),
                        commit.getId().getName()));
                }
                Collections.reverse(versions);
                for (int i = 0; i < versions.size(); i++) {
                    VersionInfo vi = versions.get(i);
                    versions.set(i, new VersionInfo(i + 1, vi.timestamp(), vi.source()));
                }
                return versions;
            }
        } catch (IOException e) {
            return List.of();
        }
    }

    @Override
    public List<String> list() {
        return list("");
    }

    @Override
    public List<String> list(String prefix) {
        try {
            ObjectId head = repository.resolve(refSpec);
            if (head == null) return List.of();
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(head);
                try (TreeWalk treeWalk = new TreeWalk(repository)) {
                    treeWalk.addTree(commit.getTree());
                    treeWalk.setRecursive(true);
                    List<String> paths = new ArrayList<>();
                    while (treeWalk.next()) {
                        String path = treeWalk.getPathString();
                        if (prefix.isEmpty() || path.startsWith(prefix)) {
                            paths.add(path);
                        }
                    }
                    return paths;
                }
            }
        } catch (IOException e) {
            return List.of();
        }
    }

    @Override
    public boolean exists(String path) {
        return read(path).isPresent();
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl corpus-git -Dtest=GitCorpusReaderTest#readReturnsCommittedContent`
Expected: PASS

- [ ] **Step 6: Add tests for list, versions, readVersion, exists**

```java
@Test
void listReturnsAllFiles() {
    var reader = new GitCorpusReader(repository, "HEAD");
    List<String> files = reader.list();
    assertTrue(files.contains("test.md"));
}

@Test
void existsReturnsTrueForCommittedFile() {
    var reader = new GitCorpusReader(repository, "HEAD");
    assertTrue(reader.exists("test.md"));
    assertFalse(reader.exists("nonexistent.md"));
}

@Test
void versionsReturnsCommitHistory() throws Exception {
    // Add a second version
    Files.writeString(repoDir.resolve("test.md"), "updated");
    try (Git git = new Git(repository)) {
        git.add().addFilepattern("test.md").call();
        git.commit().setMessage("update").call();
    }

    var reader = new GitCorpusReader(repository, "HEAD");
    List<VersionInfo> versions = reader.versions("test.md");
    assertEquals(2, versions.size());
    assertEquals(1, versions.get(0).version());
    assertEquals(2, versions.get(1).version());
}

@Test
void readVersionReturnsHistoricalContent() throws Exception {
    Files.writeString(repoDir.resolve("test.md"), "v2 content");
    try (Git git = new Git(repository)) {
        git.add().addFilepattern("test.md").call();
        git.commit().setMessage("v2").call();
    }

    var reader = new GitCorpusReader(repository, "HEAD");
    Optional<byte[]> v1 = reader.readVersion("test.md", 1);
    Optional<byte[]> v2 = reader.readVersion("test.md", 2);
    assertTrue(v1.isPresent());
    assertTrue(v2.isPresent());
    assertEquals("hello world", new String(v1.get(), UTF_8));
    assertEquals("v2 content", new String(v2.get(), UTF_8));
}

@Test
void readUncommittedContentReturnsEmpty() {
    // Write but don't commit
    try { Files.writeString(repoDir.resolve("uncommitted.md"), "draft"); }
    catch (IOException e) { fail(e); }

    var reader = new GitCorpusReader(repository, "HEAD");
    assertFalse(reader.exists("uncommitted.md"));
}
```

- [ ] **Step 7: Run all tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl corpus-git`
Expected: all PASS

- [ ] **Step 8: Commit**

```bash
git add corpus-git/ pom.xml
git commit -m "feat: add corpus-git module with GitCorpusReader

JGit-backed CorpusReader that reads from a configurable git ref.
Supports version history via git log, read at specific versions,
and tree listing. Uncommitted content is invisible to reads.

Refs #789"
```

### Task 7: Add GitChangeSource

**Files:**
- Create: `corpus-git/src/main/java/io/casehub/neocortex/corpus/git/GitChangeSource.java`
- Create: `corpus-git/src/test/java/io/casehub/neocortex/corpus/git/GitChangeSourceTest.java`
- Test: `corpus-git/src/test/java/io/casehub/neocortex/corpus/git/GitChangeSourceTest.java`

**Interfaces:**
- Consumes: `WatchableChangeSource`, `ChangeSet`, `ChangedEntry`, `ChangeType` (from corpus-api)
- Produces: `GitChangeSource` implementing `WatchableChangeSource` — uses JGit DiffFormatter for change detection, commit SHA as cursor

- [ ] **Step 1: Write failing test for changesSince()**

```java
class GitChangeSourceTest {

    private Path repoDir;
    private Repository repository;

    @BeforeEach
    void setUp() throws Exception {
        repoDir = Files.createTempDirectory("git-change-test");
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            Files.writeString(repoDir.resolve("initial.md"), "hello");
            git.add().addFilepattern("initial.md").call();
            git.commit().setMessage("initial").call();
            repository = git.getRepository();
        }
    }

    @Test
    void changesSinceDetectsNewFile() throws Exception {
        String cursor;
        try (var cs = new GitChangeSource(repository, "HEAD")) {
            cursor = cs.currentCursor();
        }

        // Add a new file
        try (Git git = new Git(repository)) {
            Files.writeString(repoDir.resolve("new.md"), "new content");
            git.add().addFilepattern("new.md").call();
            git.commit().setMessage("add new").call();
        }

        try (var cs = new GitChangeSource(repository, "HEAD")) {
            ChangeSet changes = cs.changesSince(cursor);
            assertEquals(1, changes.entries().size());
            assertEquals("new.md", changes.entries().get(0).path());
            assertEquals(ChangeType.ADDED, changes.entries().get(0).type());
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL — class does not exist

- [ ] **Step 3: Implement GitChangeSource**

```java
package io.casehub.neocortex.corpus.git;

import io.casehub.neocortex.corpus.*;
import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.treewalk.*;
import java.io.*;
import java.util.*;

public class GitChangeSource implements WatchableChangeSource {

    private final Repository repository;
    private final String refSpec;

    public GitChangeSource(Repository repository, String refSpec) {
        this.repository = Objects.requireNonNull(repository);
        this.refSpec = Objects.requireNonNull(refSpec);
    }

    @Override
    public ChangeSet changesSince(String cursor) {
        try {
            ObjectId currentId = repository.resolve(refSpec);
            if (currentId == null) return new ChangeSet(List.of(), cursor);
            String newCursor = currentId.getName();
            if (cursor != null && cursor.equals(newCursor)) {
                return new ChangeSet(List.of(), newCursor);
            }

            ObjectId oldId = cursor != null ? ObjectId.fromString(cursor) : null;

            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit newCommit = revWalk.parseCommit(currentId);
                RevTree newTree = newCommit.getTree();
                RevTree oldTree = oldId != null
                    ? revWalk.parseCommit(oldId).getTree() : null;

                List<ChangedEntry> entries = diffTrees(oldTree, newTree);
                return new ChangeSet(entries, newCursor);
            }
        } catch (IOException e) {
            return new ChangeSet(List.of(), cursor);
        }
    }

    @Override
    public ChangeSet fullScan() {
        try {
            ObjectId head = repository.resolve(refSpec);
            if (head == null) return new ChangeSet(List.of(), null);
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(head);
                try (TreeWalk treeWalk = new TreeWalk(repository)) {
                    treeWalk.addTree(commit.getTree());
                    treeWalk.setRecursive(true);
                    List<ChangedEntry> entries = new ArrayList<>();
                    while (treeWalk.next()) {
                        entries.add(new ChangedEntry(treeWalk.getPathString(), ChangeType.ADDED));
                    }
                    return new ChangeSet(entries, head.getName());
                }
            }
        } catch (IOException e) {
            return new ChangeSet(List.of(), null);
        }
    }

    @Override
    public String currentCursor() {
        try {
            ObjectId head = repository.resolve(refSpec);
            return head != null ? head.getName() : null;
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void watch(ChangeListener listener) {
        // Poll-based: caller invokes changesSince() periodically
    }

    @Override
    public void close() {
        // No resources to release — repository lifecycle managed by caller
    }

    private List<ChangedEntry> diffTrees(RevTree oldTree, RevTree newTree) throws IOException {
        try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            df.setRepository(repository);
            List<DiffEntry> diffs = df.scan(oldTree, newTree);
            List<ChangedEntry> entries = new ArrayList<>(diffs.size());
            for (DiffEntry diff : diffs) {
                ChangeType type = switch (diff.getChangeType()) {
                    case ADD -> ChangeType.ADDED;
                    case DELETE -> ChangeType.DELETED;
                    case MODIFY, COPY, RENAME -> ChangeType.MODIFIED;
                };
                String path = diff.getChangeType() == DiffEntry.ChangeType.DELETE
                    ? diff.getOldPath() : diff.getNewPath();
                entries.add(new ChangedEntry(path, type));
            }
            return entries;
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl corpus-git -Dtest=GitChangeSourceTest#changesSinceDetectsNewFile`
Expected: PASS

- [ ] **Step 5: Add tests for modification, deletion, fullScan, currentCursor**

```java
@Test
void changesSinceDetectsModification() throws Exception {
    String cursor;
    try (var cs = new GitChangeSource(repository, "HEAD")) {
        cursor = cs.currentCursor();
    }
    try (Git git = new Git(repository)) {
        Files.writeString(repoDir.resolve("initial.md"), "modified");
        git.add().addFilepattern("initial.md").call();
        git.commit().setMessage("modify").call();
    }
    try (var cs = new GitChangeSource(repository, "HEAD")) {
        ChangeSet changes = cs.changesSince(cursor);
        assertEquals(1, changes.entries().size());
        assertEquals(ChangeType.MODIFIED, changes.entries().get(0).type());
    }
}

@Test
void changesSinceDetectsDeletion() throws Exception {
    String cursor;
    try (var cs = new GitChangeSource(repository, "HEAD")) {
        cursor = cs.currentCursor();
    }
    try (Git git = new Git(repository)) {
        git.rm().addFilepattern("initial.md").call();
        git.commit().setMessage("delete").call();
    }
    try (var cs = new GitChangeSource(repository, "HEAD")) {
        ChangeSet changes = cs.changesSince(cursor);
        assertEquals(1, changes.entries().size());
        assertEquals(ChangeType.DELETED, changes.entries().get(0).type());
    }
}

@Test
void fullScanListsAllFiles() throws Exception {
    try (var cs = new GitChangeSource(repository, "HEAD")) {
        ChangeSet scan = cs.fullScan();
        assertEquals(1, scan.entries().size());
        assertEquals("initial.md", scan.entries().get(0).path());
        assertEquals(ChangeType.ADDED, scan.entries().get(0).type());
        assertNotNull(scan.newCursor());
    }
}

@Test
void currentCursorReturnsSha() throws Exception {
    try (var cs = new GitChangeSource(repository, "HEAD")) {
        String cursor = cs.currentCursor();
        assertNotNull(cursor);
        assertEquals(40, cursor.length());
    }
}

@Test
void changesSinceWithSameCursorReturnsEmpty() throws Exception {
    try (var cs = new GitChangeSource(repository, "HEAD")) {
        String cursor = cs.currentCursor();
        ChangeSet changes = cs.changesSince(cursor);
        assertTrue(changes.entries().isEmpty());
    }
}
```

- [ ] **Step 6: Run all tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl corpus-git`
Expected: all PASS

- [ ] **Step 7: Commit**

```bash
git add corpus-git/
git commit -m "feat: add GitChangeSource — commit-SHA-based change detection

JGit DiffFormatter between commit SHAs for precise change detection.
Immune to clock skew and branch-switch false positives. Cursor is a
commit SHA — monotonic and deterministic.

Refs #789"
```

### Task 8: Add GitCorpusStore with write + flush

**Files:**
- Create: `corpus-git/src/main/java/io/casehub/neocortex/corpus/git/GitCorpusStore.java`
- Create: `corpus-git/src/test/java/io/casehub/neocortex/corpus/git/GitCorpusStoreTest.java`
- Create: `corpus-git/src/test/java/io/casehub/neocortex/corpus/git/GitCorpusStoreContractTest.java`
- Test: contract test + GitCorpusStoreTest

**Interfaces:**
- Consumes: `CorpusStore` (from corpus-api, with write() and flush()), `GitCorpusReader` (from Task 6)
- Produces: `GitCorpusStore` implementing `CorpusStore` — writes to working tree, flush() commits

- [ ] **Step 1: Write failing test for write + flush visibility**

```java
class GitCorpusStoreTest {

    private Path repoDir;
    private Repository repository;

    @BeforeEach
    void setUp() throws Exception {
        repoDir = Files.createTempDirectory("git-store-test");
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            Files.writeString(repoDir.resolve(".gitkeep"), "");
            git.add().addFilepattern(".gitkeep").call();
            git.commit().setMessage("init").call();
            repository = git.getRepository();
        }
    }

    @Test
    void writeAndFlushMakesContentVisibleToReader() {
        var store = new GitCorpusStore(repository, "HEAD");
        var reader = new GitCorpusReader(repository, "HEAD");

        store.write("doc.md", "hello git".getBytes(UTF_8));
        assertFalse(reader.exists("doc.md"), "uncommitted write should be invisible");

        store.flush();
        assertTrue(reader.exists("doc.md"), "flushed write should be visible");
        assertEquals("hello git", new String(reader.read("doc.md").orElseThrow(), UTF_8));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL — class does not exist

- [ ] **Step 3: Implement GitCorpusStore**

```java
package io.casehub.neocortex.corpus.git;

import io.casehub.neocortex.corpus.CorpusStore;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class GitCorpusStore implements CorpusStore {

    private final Repository repository;
    private final String refSpec;
    private final Set<String> pendingPaths = new LinkedHashSet<>();

    public GitCorpusStore(Repository repository, String refSpec) {
        this.repository = Objects.requireNonNull(repository);
        this.refSpec = Objects.requireNonNull(refSpec);
    }

    @Override
    public void write(String path, byte[] content) {
        Objects.requireNonNull(path);
        Objects.requireNonNull(content);
        try {
            Path target = repository.getWorkTree().toPath().resolve(path);
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            pendingPaths.add(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void write(String path, InputStream content) {
        try {
            write(path, content.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void write(String path, Path file) {
        try {
            write(path, Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void delete(String path) {
        try (Git git = new Git(repository)) {
            git.rm().addFilepattern(path).call();
            pendingPaths.add(path);
        } catch (GitAPIException e) {
            throw new RuntimeException("Failed to git rm " + path, e);
        }
    }

    @Override
    public void flush() {
        if (pendingPaths.isEmpty()) return;
        try (Git git = new Git(repository)) {
            for (String path : pendingPaths) {
                Path target = repository.getWorkTree().toPath().resolve(path);
                if (Files.exists(target)) {
                    git.add().addFilepattern(path).call();
                }
            }
            git.commit().setMessage("corpus: write " + pendingPaths.size() + " file(s)").call();
            pendingPaths.clear();
        } catch (GitAPIException e) {
            throw new RuntimeException("Failed to flush corpus writes", e);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl corpus-git -Dtest=GitCorpusStoreTest#writeAndFlushMakesContentVisibleToReader`
Expected: PASS

- [ ] **Step 5: Add contract test**

```java
class GitCorpusStoreContractTest extends CorpusStoreContractTest {

    private Path repoDir;
    private Repository repository;
    private GitCorpusStore store;
    private GitCorpusReader reader;

    @BeforeEach
    void setUp() throws Exception {
        repoDir = Files.createTempDirectory("git-contract-test");
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            Files.writeString(repoDir.resolve(".gitkeep"), "");
            git.add().addFilepattern(".gitkeep").call();
            git.commit().setMessage("init").call();
            repository = git.getRepository();
        }
        store = new GitCorpusStore(repository, "HEAD");
        reader = new GitCorpusReader(repository, "HEAD");
    }

    @Override protected CorpusStore store() { return store; }
    @Override protected CorpusReader reader() { return reader; }
}
```

Note: the contract tests call `write()` then immediately `read()`. Since GitCorpusReader reads from refs (committed state), the contract tests need `flush()` between write and read. If the contract test doesn't call `flush()`, either update the contract test to call `flush()` after `write()`, or add an auto-flush option to GitCorpusStore.

- [ ] **Step 6: Update CorpusStoreContractTest to call flush() after writes**

Add `store().flush()` after every `store().write()` call in the contract test. This ensures the contract works for all backends (flush is a no-op for flat/zip).

- [ ] **Step 7: Run contract tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl corpus-git,corpus`
Expected: all PASS (both git and existing backends)

- [ ] **Step 8: Add tests for batch write + delete**

```java
@Test
void batchWriteFlushesOneCommit() throws Exception {
    var store = new GitCorpusStore(repository, "HEAD");
    store.write("a.md", "aaa".getBytes(UTF_8));
    store.write("b.md", "bbb".getBytes(UTF_8));
    store.flush();

    try (Git git = new Git(repository)) {
        int commitCount = 0;
        for (var c : git.log().call()) commitCount++;
        assertEquals(2, commitCount); // init + one batch commit
    }
}

@Test
void deleteAndFlushRemovesFromReader() {
    var store = new GitCorpusStore(repository, "HEAD");
    var reader = new GitCorpusReader(repository, "HEAD");
    store.write("to-delete.md", "temp".getBytes(UTF_8));
    store.flush();
    assertTrue(reader.exists("to-delete.md"));

    store.delete("to-delete.md");
    store.flush();
    assertFalse(reader.exists("to-delete.md"));
}
```

- [ ] **Step 9: Run all corpus-git tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl corpus-git`
Expected: all PASS

- [ ] **Step 10: Commit**

```bash
git add corpus-git/ corpus/
git commit -m "feat: add GitCorpusStore — write-then-flush with auto-commit

Write stages files to working tree. Flush commits all pending writes
in one git commit. Passes CorpusStoreContractTest. Batch writes
produce a single commit for atomicity.

Refs #789"
```

---

## Batch 4: Access Tracking + ContentSerializer

After this batch: AccessTrackingReader provides provenance instrumentation. ContentSerializer SPI enables programmatic entry serialization for backend persistence.

### Task 9: Add AccessTrackingReader

**Files:**
- Create: `corpus/src/main/java/io/casehub/neocortex/corpus/AccessTrackingReader.java`
- Create: `corpus/src/main/java/io/casehub/neocortex/corpus/AccessEvent.java`
- Create: `corpus/src/test/java/io/casehub/neocortex/corpus/AccessTrackingReaderTest.java`
- Test: `corpus/src/test/java/io/casehub/neocortex/corpus/AccessTrackingReaderTest.java`

**Interfaces:**
- Consumes: `CorpusReader` (from corpus-api), `CorpusRef` (from rag-api)
- Produces: `AccessTrackingReader` wrapping `CorpusReader`, `AccessEvent` record

- [ ] **Step 1: Write failing test**

```java
class AccessTrackingReaderTest {

    @Test
    void readEmitsAccessEvent() {
        var events = new ArrayList<AccessEvent>();
        CorpusReader delegate = stubReader("doc.md", "content".getBytes(UTF_8));
        var tracked = new AccessTrackingReader(delegate, "test-corpus", events::add);

        tracked.read("doc.md");

        assertEquals(1, events.size());
        assertEquals("doc.md", events.get(0).path());
        assertEquals("test-corpus", events.get(0).corpusName());
    }

    @Test
    void readMissDoesNotEmitEvent() {
        var events = new ArrayList<AccessEvent>();
        CorpusReader delegate = stubReader(); // empty
        var tracked = new AccessTrackingReader(delegate, "test-corpus", events::add);

        tracked.read("missing.md");

        assertTrue(events.isEmpty());
    }
}
```

- [ ] **Step 2: Implement AccessEvent and AccessTrackingReader**

```java
// AccessEvent.java
package io.casehub.neocortex.corpus;

import java.time.Instant;

public record AccessEvent(String path, String corpusName, Instant timestamp) {}

// AccessTrackingReader.java
package io.casehub.neocortex.corpus;

import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

public class AccessTrackingReader implements CorpusReader {

    private final CorpusReader delegate;
    private final String corpusName;
    private final Consumer<AccessEvent> eventSink;

    public AccessTrackingReader(CorpusReader delegate, String corpusName,
                                 Consumer<AccessEvent> eventSink) {
        this.delegate = Objects.requireNonNull(delegate);
        this.corpusName = Objects.requireNonNull(corpusName);
        this.eventSink = Objects.requireNonNull(eventSink);
    }

    @Override
    public Optional<byte[]> read(String path) {
        Optional<byte[]> result = delegate.read(path);
        if (result.isPresent()) {
            eventSink.accept(new AccessEvent(path, corpusName, Instant.now()));
        }
        return result;
    }

    @Override
    public Optional<InputStream> readStream(String path) { return delegate.readStream(path); }
    @Override
    public Optional<byte[]> readVersion(String path, int version) { return delegate.readVersion(path, version); }
    @Override
    public List<VersionInfo> versions(String path) { return delegate.versions(path); }
    @Override
    public List<String> list() { return delegate.list(); }
    @Override
    public List<String> list(String prefix) { return delegate.list(prefix); }
    @Override
    public boolean exists(String path) { return delegate.exists(path); }
}
```

- [ ] **Step 3: Run tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl corpus`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add corpus/
git commit -m "feat: add AccessTrackingReader for read provenance

Construction-time wrapper emitting AccessEvent on successful reads.
Consumer<AccessEvent> callback decouples from CDI. Batch paths use
the unwrapped reader; MCP-facing paths use the tracked reader.

Refs #789"
```

### Task 10: Add ContentSerializer SPI

**Files:**
- Create: `corpus-management-api/src/main/java/io/casehub/neocortex/corpus/management/ContentSerializer.java`
- Create: `corpus-management/src/main/java/io/casehub/neocortex/corpus/management/impl/MarkdownContentSerializer.java`
- Create: `corpus-management/src/test/java/io/casehub/neocortex/corpus/management/impl/MarkdownContentSerializerTest.java`
- Test: `corpus-management/src/test/java/io/casehub/neocortex/corpus/management/impl/MarkdownContentSerializerTest.java`

**Interfaces:**
- Consumes: `CorpusEntry` (from corpus-management-api)
- Produces: `ContentSerializer` SPI, `MarkdownContentSerializer` default implementation

- [ ] **Step 1: Write failing test**

```java
class MarkdownContentSerializerTest {

    @Test
    void serializesEntryWithMetadataAndBody() {
        var entry = new CorpusEntry("test-id",
            Map.of("title", "Test", "domain", "tools"), "The body text.", null);
        var serializer = new MarkdownContentSerializer();

        byte[] result = serializer.serialize(entry);
        String content = new String(result, UTF_8);

        assertTrue(content.contains("---"));
        assertTrue(content.contains("title: Test"));
        assertTrue(content.contains("The body text."));
    }
}
```

- [ ] **Step 2: Create ContentSerializer SPI**

```java
package io.casehub.neocortex.corpus.management;

@FunctionalInterface
public interface ContentSerializer {
    byte[] serialize(CorpusEntry entry);
}
```

- [ ] **Step 3: Create MarkdownContentSerializer**

```java
package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.management.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

public class MarkdownContentSerializer implements ContentSerializer {

    @Override
    public byte[] serialize(CorpusEntry entry) {
        var sb = new StringBuilder();
        sb.append("---\n");
        new TreeMap<>(entry.metadata()).forEach((key, value) -> {
            if (value instanceof String s) {
                sb.append(key).append(": ").append(s).append("\n");
            }
        });
        sb.append("---\n\n");
        if (entry.body() != null) {
            sb.append(entry.body()).append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 4: Run tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl corpus-management-api,corpus-management`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add corpus-management-api/ corpus-management/
git commit -m "feat: add ContentSerializer SPI with markdown default

Inverse of MetadataExtractor — serializes CorpusEntry to backend format.
MarkdownContentSerializer writes YAML frontmatter + body for text corpora.

Refs #789"
```

---

## References

- `specs/issue-811-mcp-domain/2026-11-06-corpus-library-redesign.md` — design spec
- `corpus-api/src/main/java/io/casehub/neocortex/corpus/CorpusStore.java:6` — current SPI (append→write)
- `corpus-api/src/main/java/io/casehub/neocortex/corpus/VersionInfo.java:5` — current record (zipFile→source)
- `rag-core/src/main/java/io/casehub/neocortex/rag/runtime/CorpusIngestionService.java:177` — inline pipeline to extract
- `corpus-management/src/main/java/io/casehub/neocortex/corpus/management/impl/SqliteCorpusEntryStore.java:121` — bypass to rework
- `rag/src/main/java/io/casehub/neocortex/rag/runtime/CorpusBindingProducer.java:97` — backend wiring (deferred to follow-up plan)
- casehubio/neocortex#789 — parent epic
- casehubio/neocortex#811 — MCP domain

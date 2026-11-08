# Agent-Facing Change Detection Design

**Issue:** casehubio/neocortex#836
**Date:** 2026-11-08
**Parent:** casehubio/neocortex#833

## Problem

`ChangeSource.changesSince(cursor)` is internal ingestion plumbing. Agents
maintaining awareness over a corpus have no tool to ask "what changed?"
The MCP domain doesn't expose change detection, and cursor management is
stateful — agents don't manage state across sessions.

## Solution

Add a timestamp-based change query to `CorpusEntryStore` and expose it via
the MCP domain. The agent provides an `Instant`; the system maps it to the
backend's internal cursor format.

## New Type: ChangeRecord

In `corpus-management-api`, package `io.casehub.neocortex.corpus.management`:

```java
public record ChangeRecord(
    String path,
    ChangeType type,
    Instant timestamp,
    @Nullable String author,
    @Nullable String preview
) {}
```

- `ChangeType` reused from `corpus-api` (ADDED, MODIFIED, DELETED)
- `author` — populated by git backend from `RevCommit.getAuthorIdent().getName()`,
  null for zip backend
- `preview` — first N characters of current document content (default 500),
  null when content is unavailable or entry was deleted
- `timestamp` — commit timestamp for git, file mtime for zip

## SPI: CorpusEntryStore Addition

```java
List<ChangeRecord> recentChanges(CorpusRef corpus, Instant since);
```

- Returns all changes since the given instant, ordered by timestamp
  descending (newest first)
- Empty list when no changes exist or the backend doesn't support
  change detection
- `NoOpCorpusEntryStore` returns `List.of()`

## Instant-to-Cursor Mapping

Each store implementation handles the mapping internally. No changes to the
`ChangeSource` SPI.

### Git Backend (SqliteCorpusEntryStore wrapping git)

Does NOT delegate to `ChangeSource.changesSince()` — that does a tree diff
which loses per-file commit metadata. Instead, walks commits directly:

1. JGit `RevWalk` from HEAD, filtering by `commit.authorTime >= since`
2. For each commit, diff against its parent to get changed paths
3. Deduplicate by path — keep the **most recent** commit's metadata per path
4. Build `ChangeRecord` with:
   - `timestamp` from `RevCommit.getAuthorIdent().getWhenAsInstant()`
   - `author` from `RevCommit.getAuthorIdent().getName()`
   - `type` from diff entry type (ADD/MODIFY/DELETE)
5. Load content preview via `CorpusReader.read(path)`, truncate to limit
   (skip for DELETED entries — preview is null)

### Zip Backend (SqliteCorpusEntryStore wrapping ZipChangeSource)

1. Convert `since` to epoch millis
2. Build a synthetic mtime-map cursor with all entries having `since` as
   their mtime
3. Call `ChangeSource.changesSince(syntheticCursor)` — entries with
   newer mtimes appear as changes
4. `timestamp` from file mtime, `author` null
5. Load content preview via `CorpusReader.read(path)`, truncate to limit

## MCP Tool

New method on `CorpusApiBean`:

```java
@PlatformQuery
ChangeResult changes(@ContextParam("corpusId") String corpusId,
                     @McpCursorMap(INSTANT) Instant since);
```

`ChangeResult` wraps the list for consistent MCP response shape:

```java
public record ChangeResult(List<ChangeRecord> changes, int count) {
    public ChangeResult(List<ChangeRecord> changes) {
        this(changes, changes.size());
    }
}
```

Agent asks: "what changed in corpus X since 2026-11-01T00:00:00Z?"
No cursor management required from the agent side.

## Testing

- Contract test in `CorpusEntryStoreContractTest` (or new abstract base)
  for `recentChanges()` with known change scenarios
- Git-specific test: verify timestamp mapping, author extraction, preview
  content
- Zip-specific test: verify mtime-based filtering, null author
- MCP integration: verify `changes()` tool returns expected shape

## References

- `corpus-api/src/main/java/.../ChangeSource.java` — internal SPI
- `corpus-api/src/main/java/.../ChangedEntry.java` — internal change record
- `corpus-git/src/main/java/.../GitChangeSource.java` — git cursor implementation
- `corpus-management-api/src/main/java/.../CorpusEntryStore.java` — target SPI
- `corpus-management/src/main/java/.../CorpusApiBean.java` — MCP domain
- casehubio/neocortex#836 — issue
- casehubio/neocortex#833 — parent epic

## D1: SPI placement for change detection

**Choice:** Add `recentChanges(CorpusRef, Instant since)` to CorpusEntryStore
**Alternatives:**
- New dedicated SPI (CorpusChangeTracker) — extra SPI for one method, more wiring
- Enhance ChangeSource directly — blurs internal/external boundary, no access to CorpusReader for previews
**Rationale:** CorpusEntryStore is the agent-facing contract for corpus entries. "What changed?" is a natural query alongside CRUD. Implementation delegates to ChangeSource + CorpusReader internally.
**Trade-offs:** Slightly grows CorpusEntryStore interface (currently 4 methods).
**Sources:** corpus-management-api CorpusEntryStore.java, corpus-api ChangeSource.java, issue #836
**Exploration:** quick
**Status:** captured

## D2: ChangeRecord type design

**Choice:** New standalone record in corpus-management-api — path, type, timestamp, author (nullable), preview (nullable)
**Alternatives:**
- Extend/wrap ChangedEntry — ChangedEntry is a record (can't extend), coupling agent API to internal plumbing
**Rationale:** Clean separation between internal (ChangedEntry in corpus-api) and agent-facing (ChangeRecord in corpus-management-api). Purpose-built for agent consumption.
**Trade-offs:** Minor duplication of path + ChangeType fields vs ChangedEntry.
**Sources:** corpus-api ChangedEntry.java, issue #836
**Exploration:** quick
**Status:** captured

## D3: Instant to cursor mapping strategy

**Choice:** Backend-specific mapping in each store implementation — no changes to ChangeSource SPI
**Alternatives:**
- Add changesSince(Instant) overload to ChangeSource — modifies internal SPI for agent-facing feature, forces all impls to add the method
**Rationale:** Keeps mapping as implementation detail. ChangeSource stays cursor-only. Git uses RevWalk to find commit at/after Instant; zip converts Instant to epoch millis for mtime comparison.
**Trade-offs:** Each store implementation duplicates the bridging logic, but they're fundamentally different (SHA lookup vs mtime comparison) so sharing wouldn't help.
**Sources:** corpus-git GitChangeSource.java, corpus ChangeSource.java, issue #836
**Exploration:** quick
**Status:** captured

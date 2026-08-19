## D1: Version numbering scheme

**Choice:** V1 for all modules — every module starts at `V1__`
**Alternatives:**
- Keep V1000 for memory-jpa, V1 for memory-cbr-jpa only — less change but leaves inconsistency
**Rationale:** Each module has its own Flyway location (`classpath:db/<name>/migration`), so version numbers never collide across modules. V1000 in memory-jpa has no purpose — normalizing removes a future "why is this different?" question. All other modules (memory-cbr-tracking, memory-sqlite, rag-tracking, rag-cache) already use V1.
**Trade-offs:** None meaningful — no production database exists, no range-allocation protocol in effect
**Sources:** memory-jpa V1000 file, memory-cbr-jpa V1–V5 files, test application.properties confirming separate Flyway locations
**Exploration:** quick
**Status:** captured

# io.casehub.neocortex.rag.RetrievalTracker

**Package:** `io.casehub.neocortex.rag`

**Kind:** `interface`

## Methods

### `public abstract void feedback(java.lang.String retrievalId, java.lang.String sourceDocumentId, io.casehub.neocortex.rag.RetrievalOutcome outcome)`

#### Parameters

- `retrievalId` (`java.lang.String`)
- `sourceDocumentId` (`java.lang.String`)
- `outcome` (`io.casehub.neocortex.rag.RetrievalOutcome`)

### `public abstract java.util.List<io.casehub.neocortex.rag.RetrievalFeedback> findFeedback(io.casehub.neocortex.rag.CorpusRef corpus, java.time.Instant since, java.time.Instant until)`

#### Parameters

- `corpus` (`io.casehub.neocortex.rag.CorpusRef`)
- `since` (`java.time.Instant`)
- `until` (`java.time.Instant`)

### `public abstract java.util.List<io.casehub.neocortex.rag.RetrievalRecord> findRecords(io.casehub.neocortex.rag.CorpusRef corpus, java.time.Instant since, java.time.Instant until)`

#### Parameters

- `corpus` (`io.casehub.neocortex.rag.CorpusRef`)
- `since` (`java.time.Instant`)
- `until` (`java.time.Instant`)

### `public abstract java.util.Set<java.lang.String> findRetrievedDocumentIds(io.casehub.neocortex.rag.CorpusRef corpus, java.time.Instant since, java.time.Instant until)`

#### Parameters

- `corpus` (`io.casehub.neocortex.rag.CorpusRef`)
- `since` (`java.time.Instant`)
- `until` (`java.time.Instant`)

### `public abstract int purgeOlderThan(java.time.Instant cutoff)`

#### Parameters

- `cutoff` (`java.time.Instant`)

### `public abstract java.lang.String record(io.casehub.neocortex.rag.RetrievalQuery query, io.casehub.neocortex.rag.CorpusRef corpus, java.util.List<io.casehub.neocortex.rag.RetrievedChunk> results, int maxResults)`

#### Parameters

- `query` (`io.casehub.neocortex.rag.RetrievalQuery`)
- `corpus` (`io.casehub.neocortex.rag.CorpusRef`)
- `results` (`java.util.List<io.casehub.neocortex.rag.RetrievedChunk>`)
- `maxResults` (`int`)

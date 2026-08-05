# io.casehub.neocortex.rag.RetrievalRecord

**Package:** `io.casehub.neocortex.rag`

**Kind:** `record`

## Fields

### `corpus` (`io.casehub.neocortex.rag.CorpusRef`)

### `documents` (`java.util.List<io.casehub.neocortex.rag.RetrievedDocumentRef>`)

### `maxResults` (`int`)

### `query` (`io.casehub.neocortex.rag.RetrievalQuery`)

### `retrievalId` (`java.lang.String`)

### `timestamp` (`java.time.Instant`)

## Record Components

### `corpus` (`io.casehub.neocortex.rag.CorpusRef`)

### `documents` (`java.util.List<io.casehub.neocortex.rag.RetrievedDocumentRef>`)

### `maxResults` (`int`)

### `query` (`io.casehub.neocortex.rag.RetrievalQuery`)

### `retrievalId` (`java.lang.String`)

### `timestamp` (`java.time.Instant`)

## Constructors

### `public RetrievalRecord(java.lang.String retrievalId, io.casehub.neocortex.rag.RetrievalQuery query, io.casehub.neocortex.rag.CorpusRef corpus, java.util.List<io.casehub.neocortex.rag.RetrievedDocumentRef> documents, int maxResults, java.time.Instant timestamp)`

#### Parameters

- `retrievalId` (`java.lang.String`)
- `query` (`io.casehub.neocortex.rag.RetrievalQuery`)
- `corpus` (`io.casehub.neocortex.rag.CorpusRef`)
- `documents` (`java.util.List<io.casehub.neocortex.rag.RetrievedDocumentRef>`)
- `maxResults` (`int`)
- `timestamp` (`java.time.Instant`)

## Methods

### `public io.casehub.neocortex.rag.CorpusRef corpus()`

### `public java.util.List<io.casehub.neocortex.rag.RetrievedDocumentRef> documents()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public int maxResults()`

### `public io.casehub.neocortex.rag.RetrievalQuery query()`

### `public java.lang.String retrievalId()`

### `public java.time.Instant timestamp()`

### `public final java.lang.String toString()`

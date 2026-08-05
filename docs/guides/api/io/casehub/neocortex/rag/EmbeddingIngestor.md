# io.casehub.neocortex.rag.EmbeddingIngestor

**Package:** `io.casehub.neocortex.rag`

**Kind:** `interface`

## Methods

### `public abstract void deleteCorpus(io.casehub.neocortex.rag.CorpusRef corpus)`

#### Parameters

- `corpus` (`io.casehub.neocortex.rag.CorpusRef`)

### `public abstract void deleteDocument(io.casehub.neocortex.rag.CorpusRef corpus, java.lang.String sourceDocumentId)`

#### Parameters

- `corpus` (`io.casehub.neocortex.rag.CorpusRef`)
- `sourceDocumentId` (`java.lang.String`)

### `public abstract void ingest(io.casehub.neocortex.rag.CorpusRef corpus, java.util.List<io.casehub.neocortex.rag.ChunkInput> chunks)`

#### Parameters

- `corpus` (`io.casehub.neocortex.rag.CorpusRef`)
- `chunks` (`java.util.List<io.casehub.neocortex.rag.ChunkInput>`)

### `public abstract java.util.List<java.lang.String> listDocuments(io.casehub.neocortex.rag.CorpusRef corpus)`

#### Parameters

- `corpus` (`io.casehub.neocortex.rag.CorpusRef`)

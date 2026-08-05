# io.casehub.neocortex.rag.CaseRetriever

**Package:** `io.casehub.neocortex.rag`

**Kind:** `interface`

## Methods

### `public default java.util.List<io.casehub.neocortex.rag.RetrievedChunk> retrieve(io.casehub.neocortex.rag.RetrievalQuery query, io.casehub.neocortex.rag.CorpusRef corpus, int maxResults)`

#### Parameters

- `query` (`io.casehub.neocortex.rag.RetrievalQuery`)
- `corpus` (`io.casehub.neocortex.rag.CorpusRef`)
- `maxResults` (`int`)

### `public abstract java.util.List<io.casehub.neocortex.rag.RetrievedChunk> retrieve(io.casehub.neocortex.rag.RetrievalQuery query, io.casehub.neocortex.rag.CorpusRef corpus, int maxResults, io.casehub.neocortex.rag.PayloadFilter filter)`

#### Parameters

- `query` (`io.casehub.neocortex.rag.RetrievalQuery`)
- `corpus` (`io.casehub.neocortex.rag.CorpusRef`)
- `maxResults` (`int`)
- `filter` (`io.casehub.neocortex.rag.PayloadFilter`)

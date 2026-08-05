# io.casehub.neocortex.rag.RetrievalAnalyzer

**Package:** `io.casehub.neocortex.rag`

**Kind:** `class`

## Fields

### `MINHASH_THRESHOLD` (`int`)

## Constructors

### `private RetrievalAnalyzer()`

## Methods

### `private static java.util.Map<java.lang.String,java.util.Set<java.lang.String>> buildAdjacencyBruteForce(java.util.List<java.lang.String> queryKeys, java.util.Map<java.lang.String,java.util.Set<java.lang.String>> docSets, double jaccardThreshold)`

#### Parameters

- `queryKeys` (`java.util.List<java.lang.String>`)
- `docSets` (`java.util.Map<java.lang.String,java.util.Set<java.lang.String>>`)
- `jaccardThreshold` (`double`)

### `private static java.util.Map<java.lang.String,java.util.Set<java.lang.String>> buildAdjacencyMinHash(java.util.List<java.lang.String> queryKeys, java.util.Map<java.lang.String,java.util.Set<java.lang.String>> docSets, double jaccardThreshold)`

#### Parameters

- `queryKeys` (`java.util.List<java.lang.String>`)
- `docSets` (`java.util.Map<java.lang.String,java.util.Set<java.lang.String>>`)
- `jaccardThreshold` (`double`)

### `public static io.casehub.neocortex.rag.CorrelationGraph correlationGraph(io.casehub.neocortex.rag.RetrievalTracker tracker, io.casehub.neocortex.rag.CorpusRef corpus, java.time.Instant since, java.time.Instant until)`

#### Parameters

- `tracker` (`io.casehub.neocortex.rag.RetrievalTracker`)
- `corpus` (`io.casehub.neocortex.rag.CorpusRef`)
- `since` (`java.time.Instant`)
- `until` (`java.time.Instant`)

### `public static java.util.List<io.casehub.neocortex.rag.DocumentImpact> documentImpact(io.casehub.neocortex.rag.CorrelationGraph graph)`

#### Parameters

- `graph` (`io.casehub.neocortex.rag.CorrelationGraph`)

### `public static java.util.Map<java.lang.String,io.casehub.neocortex.rag.DocumentStats> documentStats(io.casehub.neocortex.rag.RetrievalTracker tracker, io.casehub.neocortex.rag.CorpusRef corpus, java.time.Instant since, java.time.Instant until)`

#### Parameters

- `tracker` (`io.casehub.neocortex.rag.RetrievalTracker`)
- `corpus` (`io.casehub.neocortex.rag.CorpusRef`)
- `since` (`java.time.Instant`)
- `until` (`java.time.Instant`)

### `private static double jaccard(java.util.Set<java.lang.String> a, java.util.Set<java.lang.String> b)`

#### Parameters

- `a` (`java.util.Set<java.lang.String>`)
- `b` (`java.util.Set<java.lang.String>`)

### `public static java.util.List<io.casehub.neocortex.rag.QueryQualitySignal> lowRelevanceQueries(io.casehub.neocortex.rag.RetrievalTracker tracker, io.casehub.neocortex.rag.CorpusRef corpus, java.time.Instant since, java.time.Instant until, double scoreThreshold)`

#### Parameters

- `tracker` (`io.casehub.neocortex.rag.RetrievalTracker`)
- `corpus` (`io.casehub.neocortex.rag.CorpusRef`)
- `since` (`java.time.Instant`)
- `until` (`java.time.Instant`)
- `scoreThreshold` (`double`)

### `public static java.util.List<io.casehub.neocortex.rag.DocumentQualitySignal> qualitySignals(io.casehub.neocortex.rag.RetrievalTracker tracker, io.casehub.neocortex.rag.EmbeddingIngestor ingestor, io.casehub.neocortex.rag.CorpusRef corpus, java.time.Instant since, java.time.Instant until, io.casehub.neocortex.rag.QualityThresholds thresholds)`

#### Parameters

- `tracker` (`io.casehub.neocortex.rag.RetrievalTracker`)
- `ingestor` (`io.casehub.neocortex.rag.EmbeddingIngestor`)
- `corpus` (`io.casehub.neocortex.rag.CorpusRef`)
- `since` (`java.time.Instant`)
- `until` (`java.time.Instant`)
- `thresholds` (`io.casehub.neocortex.rag.QualityThresholds`)

### `public static java.util.List<io.casehub.neocortex.rag.QueryCluster> queryClusters(io.casehub.neocortex.rag.CorrelationGraph graph, double jaccardThreshold)`

#### Parameters

- `graph` (`io.casehub.neocortex.rag.CorrelationGraph`)
- `jaccardThreshold` (`double`)

### `public static java.util.Map<java.lang.String,io.casehub.neocortex.rag.QueryFrequencyStats> queryFrequency(io.casehub.neocortex.rag.RetrievalTracker tracker, io.casehub.neocortex.rag.CorpusRef corpus, java.time.Instant since, java.time.Instant until)`

#### Parameters

- `tracker` (`io.casehub.neocortex.rag.RetrievalTracker`)
- `corpus` (`io.casehub.neocortex.rag.CorpusRef`)
- `since` (`java.time.Instant`)
- `until` (`java.time.Instant`)

### `public static java.util.Set<java.lang.String> unretrievedDocuments(io.casehub.neocortex.rag.RetrievalTracker tracker, io.casehub.neocortex.rag.EmbeddingIngestor ingestor, io.casehub.neocortex.rag.CorpusRef corpus, java.time.Instant since, java.time.Instant until)`

#### Parameters

- `tracker` (`io.casehub.neocortex.rag.RetrievalTracker`)
- `ingestor` (`io.casehub.neocortex.rag.EmbeddingIngestor`)
- `corpus` (`io.casehub.neocortex.rag.CorpusRef`)
- `since` (`java.time.Instant`)
- `until` (`java.time.Instant`)

### `public static java.util.List<io.casehub.neocortex.rag.QueryQualitySignal> zeroHitQueries(io.casehub.neocortex.rag.RetrievalTracker tracker, io.casehub.neocortex.rag.CorpusRef corpus, java.time.Instant since, java.time.Instant until)`

#### Parameters

- `tracker` (`io.casehub.neocortex.rag.RetrievalTracker`)
- `corpus` (`io.casehub.neocortex.rag.CorpusRef`)
- `since` (`java.time.Instant`)
- `until` (`java.time.Instant`)

# io.casehub.neocortex.rag.ColBertRelevanceEvaluator

**Package:** `io.casehub.neocortex.rag`

**Kind:** `class`

## Fields

### `correctThreshold` (`double`)

### `incorrectThreshold` (`double`)

## Constructors

### `public ColBertRelevanceEvaluator(double correctThreshold, double incorrectThreshold)`

#### Parameters

- `correctThreshold` (`double`)
- `incorrectThreshold` (`double`)

## Methods

### `public static io.casehub.neocortex.rag.ColBertRelevanceEvaluator calibrate(java.util.List<java.lang.Double> sampleScores)`

#### Parameters

- `sampleScores` (`java.util.List<java.lang.Double>`)

### `public static io.casehub.neocortex.rag.ColBertRelevanceEvaluator calibrate(java.util.List<java.lang.Double> sampleScores, int correctPercentile, int incorrectPercentile)`

#### Parameters

- `sampleScores` (`java.util.List<java.lang.Double>`)
- `correctPercentile` (`int`)
- `incorrectPercentile` (`int`)

### `public java.util.List<io.casehub.neocortex.rag.ScoredGrade> evaluateChunks(java.lang.String query, java.util.List<io.casehub.neocortex.rag.RetrievedChunk> chunks)`

#### Parameters

- `query` (`java.lang.String`)
- `chunks` (`java.util.List<io.casehub.neocortex.rag.RetrievedChunk>`)

### `private io.casehub.neocortex.rag.RelevanceGrade gradeFromScore(double score)`

#### Parameters

- `score` (`double`)

### `private static double percentile(double[] sorted, int p)`

#### Parameters

- `sorted` (`double[]`)
- `p` (`int`)

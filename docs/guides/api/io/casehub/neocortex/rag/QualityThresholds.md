# io.casehub.neocortex.rag.QualityThresholds

**Package:** `io.casehub.neocortex.rag`

**Kind:** `record`

## Fields

### `lowQualityRatio` (`double`)

### `minFeedbackForQualityCheck` (`int`)

### `minRetrievalsForQualityCheck` (`int`)

### `staleWindow` (`java.time.Duration`)

## Record Components

### `lowQualityRatio` (`double`)

### `minFeedbackForQualityCheck` (`int`)

### `minRetrievalsForQualityCheck` (`int`)

### `staleWindow` (`java.time.Duration`)

## Constructors

### `public QualityThresholds(int minRetrievalsForQualityCheck, int minFeedbackForQualityCheck, double lowQualityRatio, java.time.Duration staleWindow)`

#### Parameters

- `minRetrievalsForQualityCheck` (`int`)
- `minFeedbackForQualityCheck` (`int`)
- `lowQualityRatio` (`double`)
- `staleWindow` (`java.time.Duration`)

## Methods

### `public static io.casehub.neocortex.rag.QualityThresholds defaults()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public double lowQualityRatio()`

### `public int minFeedbackForQualityCheck()`

### `public int minRetrievalsForQualityCheck()`

### `public java.time.Duration staleWindow()`

### `public final java.lang.String toString()`

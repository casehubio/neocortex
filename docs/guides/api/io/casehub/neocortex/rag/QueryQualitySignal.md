# io.casehub.neocortex.rag.QueryQualitySignal

**Package:** `io.casehub.neocortex.rag`

**Kind:** `record`

## Fields

### `averageRelevanceScore` (`double`)

### `lastSeen` (`java.time.Instant`)

### `queryText` (`java.lang.String`)

### `retrievalCount` (`int`)

## Record Components

### `averageRelevanceScore` (`double`)

### `lastSeen` (`java.time.Instant`)

### `queryText` (`java.lang.String`)

### `retrievalCount` (`int`)

## Constructors

### `public QueryQualitySignal(java.lang.String queryText, double averageRelevanceScore, int retrievalCount, java.time.Instant lastSeen)`

#### Parameters

- `queryText` (`java.lang.String`)
- `averageRelevanceScore` (`double`)
- `retrievalCount` (`int`)
- `lastSeen` (`java.time.Instant`)

## Methods

### `public double averageRelevanceScore()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.time.Instant lastSeen()`

### `public java.lang.String queryText()`

### `public int retrievalCount()`

### `public final java.lang.String toString()`

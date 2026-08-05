# io.casehub.neocortex.rag.RetrievedChunk

**Package:** `io.casehub.neocortex.rag`

**Kind:** `record`

## Fields

### `content` (`java.lang.String`)

### `grade` (`io.casehub.neocortex.rag.RelevanceGrade`)

### `metadata` (`java.util.Map<java.lang.String,java.lang.String>`)

### `relevanceScore` (`double`)

### `sourceDocumentId` (`java.lang.String`)

## Record Components

### `content` (`java.lang.String`)

### `grade` (`io.casehub.neocortex.rag.RelevanceGrade`)

### `metadata` (`java.util.Map<java.lang.String,java.lang.String>`)

### `relevanceScore` (`double`)

### `sourceDocumentId` (`java.lang.String`)

## Constructors

### `public RetrievedChunk(java.lang.String content, java.lang.String sourceDocumentId, double relevanceScore, java.util.Map<java.lang.String,java.lang.String> metadata)`

#### Parameters

- `content` (`java.lang.String`)
- `sourceDocumentId` (`java.lang.String`)
- `relevanceScore` (`double`)
- `metadata` (`java.util.Map<java.lang.String,java.lang.String>`)

### `public RetrievedChunk(java.lang.String content, java.lang.String sourceDocumentId, double relevanceScore, java.util.Map<java.lang.String,java.lang.String> metadata, io.casehub.neocortex.rag.RelevanceGrade grade)`

#### Parameters

- `content` (`java.lang.String`)
- `sourceDocumentId` (`java.lang.String`)
- `relevanceScore` (`double`)
- `metadata` (`java.util.Map<java.lang.String,java.lang.String>`)
- `grade` (`io.casehub.neocortex.rag.RelevanceGrade`)

## Methods

### `public java.lang.String content()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.lang.String fusionKey()`

### `public io.casehub.neocortex.rag.RelevanceGrade grade()`

### `public final int hashCode()`

### `public java.util.Map<java.lang.String,java.lang.String> metadata()`

### `public double relevanceScore()`

### `public java.lang.String sourceDocumentId()`

### `public final java.lang.String toString()`

### `public io.casehub.neocortex.rag.RetrievedChunk withGrade(io.casehub.neocortex.rag.RelevanceGrade grade)`

#### Parameters

- `grade` (`io.casehub.neocortex.rag.RelevanceGrade`)

### `public io.casehub.neocortex.rag.RetrievedChunk withMetadata(java.util.Map<java.lang.String,java.lang.String> metadata)`

#### Parameters

- `metadata` (`java.util.Map<java.lang.String,java.lang.String>`)

### `public io.casehub.neocortex.rag.RetrievedChunk withRelevanceScore(double score)`

#### Parameters

- `score` (`double`)

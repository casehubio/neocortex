# io.casehub.neocortex.corpus.IntegrityReport

**Package:** `io.casehub.neocortex.corpus`

**Kind:** `record`

## Fields

### `chainLength` (`int`)

### `corpusName` (`java.lang.String`)

### `issues` (`java.util.List<io.casehub.neocortex.corpus.IntegrityIssue>`)

### `recovered` (`java.util.List<java.lang.String>`)

### `status` (`java.lang.String`)

### `totalEntries` (`long`)

## Record Components

### `chainLength` (`int`)

### `corpusName` (`java.lang.String`)

### `issues` (`java.util.List<io.casehub.neocortex.corpus.IntegrityIssue>`)

### `recovered` (`java.util.List<java.lang.String>`)

### `status` (`java.lang.String`)

### `totalEntries` (`long`)

## Constructors

### `public IntegrityReport(java.lang.String corpusName, int chainLength, long totalEntries, java.lang.String status, java.util.List<io.casehub.neocortex.corpus.IntegrityIssue> issues, java.util.List<java.lang.String> recovered)`

#### Parameters

- `corpusName` (`java.lang.String`)
- `chainLength` (`int`)
- `totalEntries` (`long`)
- `status` (`java.lang.String`)
- `issues` (`java.util.List<io.casehub.neocortex.corpus.IntegrityIssue>`)
- `recovered` (`java.util.List<java.lang.String>`)

## Methods

### `public int chainLength()`

### `public java.lang.String corpusName()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.List<io.casehub.neocortex.corpus.IntegrityIssue> issues()`

### `public java.util.List<java.lang.String> recovered()`

### `public java.lang.String status()`

### `public final java.lang.String toString()`

### `public long totalEntries()`

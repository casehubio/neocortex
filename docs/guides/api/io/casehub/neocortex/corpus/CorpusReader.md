# io.casehub.neocortex.corpus.CorpusReader

**Package:** `io.casehub.neocortex.corpus`

**Kind:** `interface`

## Methods

### `public abstract boolean exists(java.lang.String path)`

#### Parameters

- `path` (`java.lang.String`)

### `public abstract java.util.List<java.lang.String> list()`

### `public abstract java.util.List<java.lang.String> list(java.lang.String prefix)`

#### Parameters

- `prefix` (`java.lang.String`)

### `public abstract java.util.Optional<byte[]> read(java.lang.String path)`

#### Parameters

- `path` (`java.lang.String`)

### `public abstract java.util.Optional<java.io.InputStream> readStream(java.lang.String path)`

#### Parameters

- `path` (`java.lang.String`)

### `public abstract java.util.Optional<byte[]> readVersion(java.lang.String path, int version)`

#### Parameters

- `path` (`java.lang.String`)
- `version` (`int`)

### `public abstract java.util.List<io.casehub.neocortex.corpus.VersionInfo> versions(java.lang.String path)`

#### Parameters

- `path` (`java.lang.String`)

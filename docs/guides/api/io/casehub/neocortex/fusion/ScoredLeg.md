# io.casehub.neocortex.fusion.ScoreFusion.ScoredLeg

**Package:** `io.casehub.neocortex.fusion`

**Kind:** `record`

## Fields

### `items` (`java.util.List<T>`)

### `scoreExtractor` (`java.util.function.ToDoubleFunction<T>`)

### `weight` (`double`)

## Record Components

### `items` (`java.util.List<T>`)

### `scoreExtractor` (`java.util.function.ToDoubleFunction<T>`)

### `weight` (`double`)

## Constructors

### `public ScoredLeg(java.util.List<T> items, java.util.function.ToDoubleFunction<T> scoreExtractor, double weight)`

#### Parameters

- `items` (`java.util.List<T>`)
- `scoreExtractor` (`java.util.function.ToDoubleFunction<T>`)
- `weight` (`double`)

## Methods

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.List<T> items()`

### `public java.util.function.ToDoubleFunction<T> scoreExtractor()`

### `public final java.lang.String toString()`

### `public double weight()`

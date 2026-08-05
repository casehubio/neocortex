# io.casehub.neocortex.fusion.ScoreFusion

**Package:** `io.casehub.neocortex.fusion`

**Kind:** `class`

## Constructors

### `private ScoreFusion()`

## Methods

### `public static java.util.List<io.casehub.neocortex.fusion.ScoreFusion.FusedResult<T>> convexCombination(java.util.List<io.casehub.neocortex.fusion.ScoreFusion.ScoredLeg<T>> legs, java.util.function.Function<T,java.lang.String> idExtractor, int topK)`

#### Parameters

- `legs` (`java.util.List<io.casehub.neocortex.fusion.ScoreFusion.ScoredLeg<T>>`)
- `idExtractor` (`java.util.function.Function<T,java.lang.String>`)
- `topK` (`int`)

### `public static java.util.List<io.casehub.neocortex.fusion.ScoreFusion.FusedResult<T>> rrf(java.util.List<io.casehub.neocortex.fusion.ScoreFusion.ScoredLeg<T>> legs, java.util.function.Function<T,java.lang.String> idExtractor, int topK, double k)`

#### Parameters

- `legs` (`java.util.List<io.casehub.neocortex.fusion.ScoreFusion.ScoredLeg<T>>`)
- `idExtractor` (`java.util.function.Function<T,java.lang.String>`)
- `topK` (`int`)
- `k` (`double`)

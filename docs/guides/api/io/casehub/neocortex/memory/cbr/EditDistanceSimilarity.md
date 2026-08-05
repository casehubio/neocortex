# io.casehub.neocortex.memory.cbr.EditDistanceSimilarity

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `class`

## Constructors

### `private EditDistanceSimilarity()`

## Methods

### `private static java.util.List<io.casehub.neocortex.memory.cbr.EditStep> backtrace(double[][] dp, java.util.List<java.lang.String> query, java.util.List<java.lang.String> caseSeq)`

#### Parameters

- `dp` (`double[][]`)
- `query` (`java.util.List<java.lang.String>`)
- `caseSeq` (`java.util.List<java.lang.String>`)

### `public static io.casehub.neocortex.memory.cbr.EditDistanceResult compute(java.util.List<java.lang.String> query, java.util.List<java.lang.String> caseSeq)`

#### Parameters

- `query` (`java.util.List<java.lang.String>`)
- `caseSeq` (`java.util.List<java.lang.String>`)

### `public static io.casehub.neocortex.memory.cbr.EditDistanceResult compute(java.util.List<java.lang.String> query, java.util.List<java.lang.String> caseSeq, java.util.Map<java.lang.String,java.util.Map<java.lang.String,java.lang.Double>> substitutionSimilarities)`

#### Parameters

- `query` (`java.util.List<java.lang.String>`)
- `caseSeq` (`java.util.List<java.lang.String>`)
- `substitutionSimilarities` (`java.util.Map<java.lang.String,java.util.Map<java.lang.String,java.lang.Double>>`)

### `public static io.casehub.neocortex.memory.cbr.EditDistanceResult compute(java.util.List<java.lang.String> query, java.util.List<java.lang.String> caseSeq, java.util.Map<java.lang.String,java.util.Map<java.lang.String,java.lang.Double>> substitutionSimilarities, java.lang.Double insertCost, java.lang.Double deleteCost)`

#### Parameters

- `query` (`java.util.List<java.lang.String>`)
- `caseSeq` (`java.util.List<java.lang.String>`)
- `substitutionSimilarities` (`java.util.Map<java.lang.String,java.util.Map<java.lang.String,java.lang.Double>>`)
- `insertCost` (`java.lang.Double`)
- `deleteCost` (`java.lang.Double`)

### `private static double lookupSimilarity(java.lang.String a, java.lang.String b, java.util.Map<java.lang.String,java.util.Map<java.lang.String,java.lang.Double>> substitutionSimilarities)`

#### Parameters

- `a` (`java.lang.String`)
- `b` (`java.lang.String`)
- `substitutionSimilarities` (`java.util.Map<java.lang.String,java.util.Map<java.lang.String,java.lang.Double>>`)

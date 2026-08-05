# io.casehub.neocortex.memory.cbr.LocalSimilarityFunction

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `interface`

Computes local similarity between a query feature value and a case feature value.

<p>Implementations return a score in [0, 1], where:
<ul>
  <li>1.0 — perfect match (most similar)</li>
  <li>0.0 — no match (completely dissimilar)</li>
</ul>

<p>Pure Java, Tier 1 — zero external dependencies.

## Fields

### `EXACT_MATCH` (`io.casehub.neocortex.memory.cbr.LocalSimilarityFunction`)

## Methods

### `public abstract double compute(io.casehub.neocortex.memory.cbr.FeatureValue queryValue, io.casehub.neocortex.memory.cbr.FeatureValue caseValue)`

#### Parameters

- `queryValue` (`io.casehub.neocortex.memory.cbr.FeatureValue`)
- `caseValue` (`io.casehub.neocortex.memory.cbr.FeatureValue`)

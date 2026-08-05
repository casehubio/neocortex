# io.casehub.neocortex.memory.personality.PersonalityWeightedRetrieval

**Package:** `io.casehub.neocortex.memory.personality`

**Kind:** `class`

## Fields

### `HALF_LIFE_HOURS` (`double`)

## Constructors

### `private PersonalityWeightedRetrieval()`

## Methods

### `private static double recencyDecay(java.time.Instant createdAt, java.time.Instant now)`

#### Parameters

- `createdAt` (`java.time.Instant`)
- `now` (`java.time.Instant`)

### `public static java.util.List<io.casehub.neocortex.memory.Memory> reweight(java.util.List<io.casehub.neocortex.memory.Memory> memories, io.casehub.neocortex.memory.personality.PersonalityWeights weights, java.time.Instant now)`

#### Parameters

- `memories` (`java.util.List<io.casehub.neocortex.memory.Memory>`)
- `weights` (`io.casehub.neocortex.memory.personality.PersonalityWeights`)
- `now` (`java.time.Instant`)

### `private static double score(io.casehub.neocortex.memory.Memory memory, io.casehub.neocortex.memory.personality.PersonalityWeights weights, java.time.Instant now)`

#### Parameters

- `memory` (`io.casehub.neocortex.memory.Memory`)
- `weights` (`io.casehub.neocortex.memory.personality.PersonalityWeights`)
- `now` (`java.time.Instant`)

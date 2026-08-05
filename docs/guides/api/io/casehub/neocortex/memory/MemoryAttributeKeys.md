# io.casehub.neocortex.memory.MemoryAttributeKeys

**Package:** `io.casehub.neocortex.memory`

**Kind:** `class`

Reserved cross-domain attribute keys for `MemoryInput.attributes()`.

<p>Platform-reserved keys use <b>kebab-case</b>. Consumer applications should
follow the same convention for domain-specific keys to avoid collisions.

<p>These keys are conventions, not enforced constraints. Their purpose is to
allow tooling (GDPR sweeps, audit dashboards) to locate specific fact types
across domains without requiring domain knowledge. The <em>values</em> are
domain-specific and defined by each consumer application.

## Fields

### `ACTOR_ID` (`java.lang.String`)

Identity of the actor who produced this memory fact.
Use the OIDC subject (same as `CurrentPrincipal.actorId()`) when available.
This is the primary key for audit; `.ACTOR_ROLE` is supplementary.

### `ACTOR_ROLE` (`java.lang.String`)

Role of the actor within the domain (e.g. `"reviewer"`, `"investigator"`,
`"clinician"`). Supplementary to `.ACTOR_ID`.

### `CONFIDENCE` (`java.lang.String`)

Confidence score as a decimal string formatted to 4 decimal places.
Always use `.formatConfidence` to write and `.parseConfidence`
to read — do not format manually to avoid encoding variance.

### `OUTCOME` (`java.lang.String`)

Outcome of the action or case from which this memory was emitted.
The key is reserved so tooling can locate outcome facts across domains;
values are domain-specific (e.g. `"DONE"`/`"DECLINE"` in devtown).

### `SOLUTION` (`java.lang.String`)

Natural language description of the solution (action taken) for a CBR case entry.

### `VALID_FROM` (`java.lang.String`)

ISO-8601 Instant string — when this fact became valid (LLM-extracted by graph adapters).
Populated by `GraphCaseMemoryStore` adapters (e.g. Graphiti) from
`FactResult.valid_at` in the RELEVANCE result path.

### `VALID_UNTIL` (`java.lang.String`)

ISO-8601 Instant string — when this fact was invalidated; absent if still valid.
Populated by `GraphCaseMemoryStore` adapters from `FactResult.invalid_at`.

## Constructors

### `private MemoryAttributeKeys()`

## Methods

### `public static java.lang.String formatConfidence(double v)`

Formats a confidence value in [0.0, 1.0] to the canonical 4-decimal-place string.

#### Parameters

- `v` (`double`)

#### Throws

- `IllegalArgumentException` — if `v` is outside [0, 1]

### `public static double parseConfidence(java.lang.String s)`

Parses a confidence string previously written by `.formatConfidence`.

#### Parameters

- `s` (`java.lang.String`)

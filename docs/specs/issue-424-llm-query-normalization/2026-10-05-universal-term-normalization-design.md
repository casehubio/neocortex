# Universal Term Normalization for Knowledge Pipeline

**Issue:** casehubio/neocortex#424
**Parent:** casehubio/neocortex#418 (Knowledge Pipeline Phase 2)
**Date:** 2026-10-05
**Status:** Draft

## 1. Problem

The knowledge pipeline currently accepts pre-typed `KnowledgeQuery` instances (TextSearch, NearbySearch, CategorySearch) and dispatches them directly to `LocationPlatform` providers. There is no term normalization — "doll" and "dolly" are treated as unrelated queries with separate cache keys and separate provider calls. There is also no path for consumers to pass raw natural language and have the pipeline classify intent, extract parameters, and geocode locations.

This means:
- Cache hit rate is lower than necessary — equivalent queries fragment across synonym variants
- Search recall depends entirely on the provider's internal expansion, which varies by provider and is opaque
- Consumers must construct typed `KnowledgeQuery` instances themselves, even when their input is unstructured natural language

## 2. Scope

This design covers two tightly coupled capabilities that together replace the unwired `QueryNormalizer` SPI from Phase 1:

1. **Term normalization and expansion** — universal, applies to every query. Produces a canonical form (for cache identity) and a variant set (for search fan-out). Backed by WordNet via extJWNL. This is foundational infrastructure: without canonical forms, synonym queries fragment the cache regardless of how they're constructed (manually or via LLM classification).
2. **Natural language query classification** — LLM-backed fallback for when consumers pass raw text that needs intent classification, parameter extraction, and geocoding. This is the core #424 capability.

These are bundled because they replace the same SPI (`QueryNormalizer`) and share the same integration surface (`CacheKeyGenerator`, orchestrator search flow). Splitting them would require two overlapping spec revisions to the same code.

Additionally:
3. **Expansion strategy** — per-provider configuration governing whether to use canonical-only or canonical+variants, avoiding wasted API calls to providers that expand internally. Phase 1 is configuration-driven; a learning extension is a future enhancement.
4. **Cache key canonicalization** — `CacheKeyGenerator` uses canonical forms so synonym variants hit the same cache bucket.

### 2.1 Out of scope

- Multilingual normalization (WordNet 3.1 is English-only; other languages are a future concern)
- Provider-specific category taxonomy mapping (orthogonal to lexical normalization)
- Changes to `LocationPlatform` SPI or provider implementations
- Learning-based expansion strategy discovery (future enhancement — see §3.6)

## 3. Architecture

### 3.1 Two-layer normalization

Two SPIs with distinct characteristics:

| | TermNormalizer | QueryClassifier |
|---|---|---|
| **Purpose** | Lexical expansion — canonical form + synonyms | NL intent → KnowledgeQuery subtype |
| **Runs on** | Every query | NL input only |
| **Backed by** | WordNet (pure Java, in-memory) | AgentProvider (LLM) + Geocoding |
| **Module** | SPI in `knowledge-pipeline-api`, impl in `knowledge-pipeline` | SPI in `knowledge-pipeline-api`, impl in `knowledge-pipeline` |
| **Latency** | Sub-millisecond | Seconds (LLM round-trip) |
| **Failure mode** | Passthrough (return input unchanged) | Return empty, consumer falls back |

The existing `QueryNormalizer` SPI is replaced. `TermNormalizer` covers its lexical responsibility. `QueryClassifier` covers the NL→structured classification that `QueryNormalizer` was originally scoped for in the Phase 1 design spec (§3.2, `StructuredQueryNormalizer`).

Both SPIs live in `knowledge-pipeline-api` (Tier 1 pure Java) so consumers can depend on them without taking a runtime dependency. Both have `@DefaultBean` no-op implementations. Consumers use the two-step pattern: classify (optional, NL input only) → search.

### 3.2 TermNormalizer SPI

In `knowledge-pipeline-api` (Tier 1 pure Java, zero new dependencies):

```java
@FunctionalInterface
public interface TermNormalizer {
    ExpandedTerm normalize(String term, String domain);
}
```

```java
public record ExpandedTerm(String canonical, Set<String> variants) {
    public ExpandedTerm {
        Objects.requireNonNull(canonical);
        variants = Set.copyOf(variants);
    }

    public static ExpandedTerm passthrough(String term) {
        return new ExpandedTerm(term, Set.of(term));
    }
}
```

- `term` — the raw input term in human-readable form (e.g., "dolly", "café", "eatery", "coffee shop"). Multi-word phrases use spaces, not underscores — WordNet's internal `coffee_shop` convention is an implementation detail of `WordNetTermNormalizer`, not a contract requirement on callers (see §3.3).
- `domain` — a string hint guiding sense disambiguation. Well-known values are defined in `KnowledgeDomain`:
  ```java
  public final class KnowledgeDomain {
      public static final String PLACE = "place";
      public static final String THING = "thing";
      public static final String ACTIVITY = "activity";
      private KnowledgeDomain() {}
  }
  ```
  Unknown domain values (including `null`) result in passthrough — no disambiguation, return the input unchanged. This keeps the SPI open for alternative implementations without coupling it to a fixed enum.
- `canonical` — the lemma form chosen as the representative for the synset. Deterministic and stable. Used as the cache identity.
- `variants` — the full synonym set including the canonical form and morphological variants. Used for search expansion.

`NoOpTermNormalizer` `@DefaultBean` returns `ExpandedTerm.passthrough(term)` — no expansion, input is both canonical and sole variant.

### 3.3 WordNetTermNormalizer

In `knowledge-pipeline` (CDI runtime). `@ApplicationScoped`.

Implementation:
1. Loads extJWNL dictionary at construction time (`@PostConstruct` eager init). WordNet 3.1 dictionary data from `extjwnl-data-wn31` Maven dependency (~33MB classpath resource).
2. On `normalize(term, domain)`:
   a. Lemmatize the input term
   b. Look up the lemma in WordNet. For multi-word input, convert spaces to underscores internally for WordNet lookup (e.g., "coffee shop" → `coffee_shop`). This conversion is an implementation detail — the SPI accepts human-readable space-separated input.
   c. If found: select the synset matching the domain hint (by lexicographer file name). Return the first lemma in the synset as canonical (deterministic — WordNet lemma ordering is stable within a version), all words in the synset as variants.
   d. If not found or no matching sense: return `ExpandedTerm.passthrough(term)`
3. Sub-millisecond after initial load. Thread-safe (extJWNL dictionary is read-only after init).

**Domain-to-WordNet mapping:**

| Domain | WordNet lexicographer file | Example |
|---|---|---|
| `place` | `noun.location` | bank → financial institution (not riverbank) |
| `thing` | `noun.artifact` | doll → doll (toy) |
| `activity` | `noun.act` | run → running (exercise) |
| unknown/null | no filtering | first synset or passthrough |

This mapping covers the primary use cases for place/thing/activity disambiguation. WordNet's lexicographer files provide a coarse-grained categorization that aligns well with the pipeline's domain model. When no domain match exists, the implementation returns passthrough rather than risking an incorrect disambiguation.

**Coverage limitations:**

WordNet 3.1 (compiled 2012) has known gaps for this use case:
- **Regional variants:** "takeaway" (British) vs "takeout" (American) may not share a synset
- **Modern terms:** "gastropub", "coworking space", "food truck" may not exist in the dictionary
- **False synonyms:** WordNet may group terms (e.g., "restaurant" and "eating house") where the variant produces poor search results with some providers

These limitations are acceptable because:
- Unknown terms pass through unchanged — no degradation vs the current system
- The expansion strategy (§3.6) governs whether variants are actually used per provider, preventing false-synonym waste
- A curated domain-specific override mechanism is a potential future enhancement but not needed initially — the passthrough-plus-strategy combination handles the common failure modes

### 3.4 QueryClassifier

SPI in `knowledge-pipeline-api` (Tier 1 pure Java). Implementation in `knowledge-pipeline` (CDI runtime, `@ApplicationScoped`).

```java
public interface QueryClassifier {
    Optional<KnowledgeQuery> classify(String naturalLanguage, String domain);
}
```

- Returns `Optional.empty()` when classification fails or AgentProvider is unavailable
- `NoOpQueryClassifier` `@DefaultBean` returns `Optional.empty()` — consumers who depend only on the API module get the no-op. Runtime module activation provides the LLM-backed implementation.
- Uses `Instance<AgentProvider>` with graceful degradation (same pattern as `LlmSituationClassifier`)
- System prompt describes the three `KnowledgeQuery` subtypes and instructs the LLM to:
  - Identify intent (text search, nearby search, category search)
  - Extract parameters (category, location name, radius)
  - Include city/country qualifiers for location names to aid disambiguation (e.g., "King's Cross, London" rather than "King's Cross")
  - Return structured JSON
- Location names extracted by the LLM are geocoded via `LocationPlatform.Geocoding.geocode(address)`:
  - **Provider selection:** the first injected `LocationPlatform` that `supports(Geocoding.class)`, consistent with the orchestrator's existing provider iteration pattern
  - **Ambiguous results:** `geocode()` returns `List<GeocodingResult>`. The classifier uses `GeocodingResult.location()` (the `Coordinates` field) from the first result. LLM prompt engineering (city/country qualifiers) is the primary disambiguation mechanism. If geocoding returns an empty list: falls back to `TextSearch(originalInput, domain)`.
- **Domain propagation:** when the classifier produces a `TextSearch`, it passes its `domain` parameter through to `TextSearch.domain` (see §3.5 for the `TextSearch` change). This ensures the domain context provided by the consumer survives classification and flows into term normalization.

Consumer usage pattern:
```java
@Inject QueryClassifier classifier;
@Inject KnowledgePipelineService pipeline;

// NL input → classify → search (domain flows through TextSearch)
Optional<KnowledgeQuery> query = classifier.classify(userInput, KnowledgeDomain.PLACE);
List<CachedEntity> results = query.map(q -> pipeline.search(q, tenantId))
                                   .orElse(List.of());

// Direct query construction — domain explicit on TextSearch
pipeline.search(new KnowledgeQuery.TextSearch("dolly", KnowledgeDomain.THING), tenantId);
```

### 3.5 Cache key canonicalization

`CacheKeyGenerator.generate()` currently produces cache keys by serializing `KnowledgeQuery` fields directly. With term normalization, it must use canonical forms so that synonym variants resolve to the same cache bucket.

**KnowledgeQuery change — domain on TextSearch:**

`TextSearch` gains a nullable `domain` field so that the domain context travels with the query through all pipeline layers:

```java
record TextSearch(String query, String domain) implements KnowledgeQuery {
    public TextSearch { Objects.requireNonNull(query); }
}
```

- `domain` is nullable — `null` means no disambiguation (passthrough behavior, per §3.2)
- `CategorySearch` and `NearbySearch` do not gain a domain field. For `CategorySearch`, the orchestrator uses `KnowledgeDomain.PLACE` implicitly (categories in this pipeline are location-related). `NearbySearch` has no text terms to normalize.
- This makes every `TextSearch` self-describing: `TextSearch("dolly", KnowledgeDomain.THING)` disambiguates, `TextSearch("starbucks", null)` does not.
- Breaking change to the sealed hierarchy — all callers must be updated. Per the design philosophy, the migration is mechanical and the breakage forces every caller to be explicit about domain context.

**Domain extraction in CacheKeyGenerator:**

`CacheKeyGenerator` extracts the domain from the query itself — no domain parameter needed on `generate()`:

```java
String domain = switch (query) {
    case TextSearch t -> t.domain();
    case CategorySearch c -> KnowledgeDomain.PLACE;
    case NearbySearch n -> null;
};
```

This eliminates the domain-flow gap identified in R2-01: the domain travels WITH the query through `KnowledgePipelineService.search()` → `CacheKeyGenerator.generate()` → `TermNormalizer.normalize()` without any intermediate APIs needing domain parameters.

**Tokenization strategy:**

`CacheKeyGenerator` handles multi-word query tokenization before per-token normalization:

1. Unicode-normalize (NFKC), lowercase, collapse whitespace (existing `normalizeText()` behavior)
2. Split on whitespace into tokens
3. Greedy longest-match for compound terms: for each position, try the space-separated bigram through `TermNormalizer`. If the normalizer returns a non-passthrough result (i.e., canonical differs from input or variant count > 1), treat as a single compound token. Otherwise, fall back to unigram normalization.
4. Join canonical forms with space to produce the cache key text component

The compound detection in step 3 relies on the normalizer itself: `normalizer.normalize("coffee shop", domain)` either recognizes the compound (non-passthrough result) or doesn't (passthrough). No underscore joining at the tokenizer level — that's internal to `WordNetTermNormalizer` (see §3.3).

Stop words ("best", "near", "me") pass through `TermNormalizer` unchanged (WordNet returns passthrough for function words), so their presence in the cache key is consistent across all queries containing them. This is correct — "best pizza near me" and "pizza near me" are different queries and should have different cache keys. Semantic equivalence is a `QueryClassifier` concern, not a tokenization concern.

**NormalizationResult — internal return type:**

The new `generate()` overload returns an internal record in `knowledge-pipeline` (not in the API module):

```java
record NormalizationResult(NormalizedQuery normalizedQuery,
                           Map<String, ExpandedTerm> expansions) {}
```

The orchestrator receives both the public `NormalizedQuery` (for cache lookup and subsumption) and the internal expansion map (for variant dispatch):

```java
// In KnowledgePipelineOrchestrator.search():
NormalizationResult result = CacheKeyGenerator.generate(query, geohashPrecision, normalizer);
NormalizedQuery normalized = result.normalizedQuery();
Map<String, ExpandedTerm> expansions = result.expansions();

// Cache lookup uses normalized.cacheKey()
// Variant dispatch uses expansions
```

The existing no-arg overload `generate(KnowledgeQuery, int)` continues to return `NormalizedQuery` — backward compatible, no expansion data.

| Query type | What gets normalized | Domain source | Example |
|---|---|---|---|
| `TextSearch` | `query` field | `TextSearch.domain()` | "italian eatery" → canonicals ["italian", "restaurant"] → key `TEXT:italian restaurant` |
| `CategorySearch` | `category` field | `KnowledgeDomain.PLACE` (implicit) | "café" → canonical "cafe" → key `CATEGORY:cafe:geohash:500` |
| `NearbySearch` | No text terms | N/A | Unchanged — spatial key only |

`NormalizedQuery` remains unchanged — `record NormalizedQuery(KnowledgeQuery query, String cacheKey)` with its existing null checks.

**WordNet version stability:** WordNet 3.1 is frozen — published 2011, no updates since. The Maven artifact `extjwnl-data-wn31:1.2` is stable. Cache entries have TTL (via `CacheDecayPolicy`), so even in the hypothetical case of a dictionary change, orphaned entries expire naturally. This is a conscious acceptance of a negligible risk — version-stamping cache keys would add permanent format complexity for a scenario that will not occur.

### 3.6 Expansion strategy

The orchestrator does not blindly expand all queries for all providers. An expansion strategy governs whether to use canonical-only or canonical+variants per provider.

**Phase 1: Configuration-driven**

```java
class ExpansionStrategy {
    enum Mode { CANONICAL_ONLY, CANONICAL_PLUS_VARIANTS }

    Mode strategyFor(String termCanonical, String domain, String providerId);
}
```

Implementation reads from configuration:

```properties
casehub.knowledge.expansion.known-providers=google
```

- Providers listed in `known-providers` use `CANONICAL_ONLY` — these providers perform their own internal query expansion (e.g., Google Places)
- All other providers default to `CANONICAL_PLUS_VARIANTS`
- This is the right starting point: the supported providers (Google, Ref) have known, documented behavior. Configuration captures that knowledge explicitly.

**Future: Learning-based strategy** (separate issue, not Phase 1)

A learning extension would replace the default for unknown providers with an explore/exploit loop:
- For unseen term/domain/provider combinations, fire both strategies and compare results
- Use EMA-based confidence accumulation to converge on the winning strategy
- Once confident, exploit the learned strategy

This is deferred because all currently supported providers have known expansion behavior. The learning system becomes valuable only when providers with unknown expansion behavior are added.

**Variant dispatch mechanics:**

When the expansion strategy selects `CANONICAL_PLUS_VARIANTS`, the orchestrator reconstructs full query strings by substituting variants back into their original position in the query.

**Single-word query:**
```
TextSearch("dolly", "thing"), canonical_plus_variants:
  searchByText("doll", pageRequest)       ← canonical
  searchByText("dolly", pageRequest)      ← variant
  searchByText("dolls", pageRequest)      ← variant
  → 3 calls, results deduplicated by Place.id()
```

**Multi-word query — one-at-a-time reconstruction:**

For multi-word queries with multiple expanded tokens, the orchestrator uses one-at-a-time variant substitution to avoid combinatorial explosion. Each variant query changes ONE token from canonical while keeping all other tokens at their canonical form:

```
TextSearch("italian eatery", "place"), canonical_plus_variants:
  Tokenization: ["italian", "eatery"]
  Expansions: "eatery" → canonical "restaurant", variants {"restaurant","eatery","eating house"}
              "italian" → passthrough (no expansion)

  Variant queries:
    "italian restaurant"                  ← canonical reconstruction
    "italian eatery"                      ← vary "eatery" token
    "italian eating house"                ← vary "eatery" token
  → 3 calls (only one token expanded)

TextSearch("cheap eatery", "place"), canonical_plus_variants:
  Expansions: "cheap" → canonical "inexpensive", variants {"inexpensive","cheap","affordable"}
              "eatery" → canonical "restaurant", variants {"restaurant","eatery","eating house"}

  Variant queries (one-at-a-time):
    "inexpensive restaurant"              ← canonical reconstruction (always first)
    "cheap restaurant"                    ← vary "cheap" token, "eatery" at canonical
    "affordable restaurant"               ← vary "cheap" token, "eatery" at canonical
    "inexpensive eatery"                  ← vary "eatery" token, "cheap" at canonical
    "inexpensive eating house"            ← vary "eatery" token, "cheap" at canonical
  → 5 calls, not 3×3=9 Cartesian product
```

The one-at-a-time strategy gives O(Σ variant counts) queries instead of O(∏ variant counts). Cross-product queries like "affordable eatery" are excluded — the individual-axis substitutions ("affordable restaurant" and "inexpensive eatery") are sufficient to discover the same entities since providers match on each term independently.

**Variant query cap:** maximum 10 variant queries per provider per search. If the one-at-a-time count exceeds 10, truncate by keeping the canonical reconstruction first, then selecting variants in WordNet sense-frequency order (earlier lemmas in a synset correlate with higher usage frequency). Log a WARNING when truncation occurs.

- **Within-provider dedup:** results from multiple variant queries to the same provider are deduplicated by `Place.id()` (external ID) before passing to entity resolution. This is simpler than cross-provider dedup — same-provider results share an ID space.
- **CategorySearch:** category terms use canonical form only — no variant expansion. Category taxonomies are provider-specific and don't follow WordNet synsets. Sending WordNet synonyms (e.g., "coffeehouse") as category values would return zero results from providers expecting their own taxonomy (e.g., "cafe").
- **Rate limiting:** the orchestrator's existing sequential-per-provider execution provides natural rate limiting. With the one-at-a-time strategy and cap of 10, variant expansion is bounded and predictable.

### 3.7 Data flow

```
Consumer input: TextSearch("dolly", "thing")
  │
  ├─ CacheKeyGenerator.generate(TextSearch("dolly", "thing"), precision, normalizer)
  │    Internally:
  │      domain = query.domain() → "thing"
  │      tokenize("dolly") → ["dolly"]
  │      normalizer.normalize("dolly", "thing")
  │        → ExpandedTerm(canonical="doll", variants={"doll","dolly","dolls"})
  │    Returns: NormalizationResult(
  │      normalizedQuery = NormalizedQuery(TextSearch("dolly","thing"), "TEXT:doll"),
  │      expansions = {"dolly" → ExpandedTerm("doll", {"doll","dolly","dolls"})})
  │
  ├─ Cache lookup: key="TEXT:doll"
  │    ├─ HIT → return cached results
  │    └─ MISS ↓
  │
  ├─ Expansion strategy per provider:
  │    ├─ google in known-providers → CANONICAL_ONLY
  │    │    → searchByText("doll", pageRequest)
  │    └─ (other provider) → CANONICAL_PLUS_VARIANTS
  │         Reconstruct full queries with one-at-a-time substitution:
  │         → searchByText("doll", pageRequest)       ← canonical
  │         → searchByText("dolly", pageRequest)      ← variant
  │         → searchByText("dolls", pageRequest)      ← variant
  │         → dedup by Place.id()
  │
  ├─ Provider results → entity resolution → cache storage
  │    (cache key "TEXT:doll" — future lookups for "dolly" or "dolls" hit cache)
  │
  └─ Return results

Multi-word variant dispatch:
  Consumer input: TextSearch("italian eatery", "place")
  │
  ├─ CacheKeyGenerator.generate(TextSearch("italian eatery", "place"), precision, normalizer)
  │    domain = query.domain() → "place"
  │    tokenize: ["italian", "eatery"]
  │    normalizer.normalize("italian", "place") → passthrough
  │    normalizer.normalize("eatery", "place")
  │      → ExpandedTerm(canonical="restaurant", variants={"restaurant","eatery","eating house"})
  │    Returns: NormalizationResult(
  │      normalizedQuery = NormalizedQuery(..., "TEXT:italian restaurant"),
  │      expansions = {"eatery" → ExpandedTerm("restaurant", {"restaurant","eatery","eating house"})})
  │
  ├─ Cache lookup: key="TEXT:italian restaurant"
  │    └─ MISS ↓
  │
  ├─ Expansion strategy: CANONICAL_PLUS_VARIANTS
  │    Reconstruct full queries (one-at-a-time):
  │      → searchByText("italian restaurant", pageRequest)    ← canonical
  │      → searchByText("italian eatery", pageRequest)        ← vary "eatery"
  │      → searchByText("italian eating house", pageRequest)  ← vary "eatery"
  │    → 3 calls, dedup by Place.id()
  │
  └─ Provider results → entity resolution → cache storage

Natural language path:
  Consumer input: "somewhere nice for an anniversary near King's Cross"
  │
  ├─ QueryClassifier.classify(input, "place")
  │    → LLM extracts: intent=CategorySearch, category="restaurant",
  │      location="King's Cross, London"
  │    → Geocoding (first provider supporting Geocoding):
  │      geocode("King's Cross, London") → List<GeocodingResult>
  │      → first result: GeocodingResult.location() → Coordinates(51.5318, -0.1239)
  │    → CategorySearch("restaurant", Coordinates(51.5318, -0.1239), 1000)
  │
  ├─ CacheKeyGenerator.generate(CategorySearch(...), precision, normalizer)
  │    domain = KnowledgeDomain.PLACE (implicit for CategorySearch)
  │    normalizer.normalize("restaurant", "place")
  │      → ExpandedTerm(canonical="restaurant", variants={"restaurant","eatery","eating house"})
  │    Returns: NormalizationResult(
  │      normalizedQuery = NormalizedQuery(CategorySearch(...), "CATEGORY:restaurant:geohash:1000"),
  │      expansions = {"restaurant" → ExpandedTerm(...)})
  │    (CategorySearch: canonical form used for cache key, no variant expansion for categories)
  │
  └─ (continues through cache lookup → provider search as above)

Multi-word example:
  Consumer input: TextSearch("coffee shop", "place")
  │
  ├─ CacheKeyGenerator tokenization:
  │    domain = query.domain() → "place"
  │    tokens: ["coffee", "shop"]
  │    bigram attempt: normalizer.normalize("coffee shop", "place")
  │      → ExpandedTerm(canonical="coffeehouse", variants={"coffeehouse","coffee shop","cafe"})
  │      → non-passthrough result → treat "coffee shop" as single compound token
  │    Returns: NormalizationResult(
  │      normalizedQuery = NormalizedQuery(TextSearch("coffee shop","place"), "TEXT:coffeehouse"),
  │      expansions = {"coffee shop" → ExpandedTerm("coffeehouse", {...})})
  │
  └─ (continues through cache lookup → expansion strategy → provider search)
```

## 4. Error Handling

| Failure | Behavior |
|---|---|
| WordNet lookup error (corrupted dictionary, unexpected input) | Return `ExpandedTerm.passthrough(term)` — search proceeds without expansion. Log WARNING. |
| extJWNL fails to load at startup | `WordNetTermNormalizer` logs ERROR, all calls return passthrough. `NoOpTermNormalizer` serves as fallback if WordNet module not on classpath. |
| AgentProvider unavailable for QueryClassifier | Return `Optional.empty()`. Consumer handles absence. |
| LLM classification timeout | Return `Optional.empty()`. Configurable timeout (default 30s). |
| Geocoding returns empty for extracted location | QueryClassifier falls back to `TextSearch(originalInput, domain)` with the original input and caller's domain. |
| Expansion strategy: variant query fails | Log warning, return results from successful variant queries. One failed variant doesn't invalidate the others. |
| Multi-word tokenization: bigram lookup fails | Fall back to unigram tokenization for those tokens. No degradation — behaves like simple whitespace splitting. |

## 5. Testing

### 5.1 Contract tests

**TermNormalizerContractTest** — abstract base in `knowledge-pipeline-api` (pure Java):
- Single known term → canonical + variants populated
- Unknown term → passthrough (term is both canonical and sole variant)
- Null/blank term → passthrough
- Domain-aware disambiguation (if implementation supports it)
- Unknown domain → passthrough

**QueryClassifierContractTest** — abstract base in `knowledge-pipeline-api` (pure Java):
- Returns Optional — no null returns
- Empty input → Optional.empty()

### 5.2 Unit tests

**WordNetTermNormalizerTest** — in `knowledge-pipeline`:
- Known synonym sets: doll→{doll, dolly}, café→{cafe, coffee shop, coffeehouse}
- Morphological variants: running→{run, running}
- Domain sense disambiguation: bank+place → financial institution synset, bank+thing → passthrough (no artifact sense)
- Multi-word entries: "coffee shop" (space-separated) → canonical coffeehouse
- Terms not in WordNet → passthrough
- Unknown domain values → passthrough
- Thread safety under concurrent access

**extJWNL Java 26 smoke test** — loads dictionary, performs a lookup. Blocks adoption if it fails. Contingency if it fails (in priority order):
1. Fork and patch extJWNL — most likely fix is adding `--add-opens` or updating module-info for reflective access
2. JWI (MIT Java WordNet Interface) — requires dictionary files on disk rather than Maven-bundled, but functionally equivalent
3. Defer term normalization — QueryClassifier (#424 core) proceeds independently

**QueryClassifierTest** — in `knowledge-pipeline`:
- Mock AgentProvider, verify JSON parsing of LLM response
- Geocoding integration: mock LocationPlatform.Geocoding, verify `GeocodingResult.location()` extraction
- Provider selection: verify first provider supporting `Geocoding.class` is used
- Fallback to TextSearch when geocoding returns empty
- AgentProvider unavailable → Optional.empty()
- Timeout handling

**CacheKeyCanonicalTest** — in `knowledge-pipeline`:
- "eatery" and "restaurant" produce the same cache key
- "dolly" and "doll" produce the same cache key
- Multi-word tokenization: "coffee shop" and "coffeehouse" produce the same cache key
- Domain extracted from TextSearch.domain() and passed to TermNormalizer
- NormalizationResult carries both NormalizedQuery and expansion map
- Spatial components (geohash, radius) unaffected by normalization
- Backward compatibility: no-arg generate() produces same keys as before

**ExpansionStrategyTest** — in `knowledge-pipeline`:
- Known-provider → CANONICAL_ONLY
- Unknown provider → CANONICAL_PLUS_VARIANTS
- Configuration parsing

### 5.3 Integration test

End-to-end with `RefLocationPlatform`:
- Natural language input → classification → term expansion → cache key → provider search
- Verify cache hit on subsequent synonym query
- Verify canonical_plus_variants dispatches multiple searchByText calls to ref provider
- Verify within-provider dedup by Place.id()

## 6. Dependencies

### 6.1 New Maven dependencies

| Dependency | Module | Scope | Size |
|---|---|---|---|
| `net.sf.extjwnl:extjwnl:2.0.5` | knowledge-pipeline | compile | ~200KB |
| `net.sf.extjwnl:extjwnl-data-wn31:1.2` | knowledge-pipeline | compile | ~33MB |

### 6.2 Module dependency changes

- `knowledge-pipeline-api` gains no new dependencies — `TermNormalizer`, `ExpandedTerm`, `QueryClassifier`, `KnowledgeDomain`, and `NoOp` defaults are all pure Java
- `knowledge-pipeline` gains extJWNL dependencies for `WordNetTermNormalizer`
- No `memory-api` dependency needed — expansion strategy is self-contained within `knowledge-pipeline`

### 6.3 extJWNL contingency

extJWNL's last release was 2022. If the Java 26 smoke test fails:

1. **Fork and patch** (preferred) — extJWNL is BSD-licensed. The most likely failure is reflective access restrictions, fixable with targeted `--add-opens` or a module-info.java addition. Fork lives in the casehub org, patches tracked as PRs against upstream.
2. **JWI fallback** — MIT Java WordNet Interface. Requires dictionary files on disk (not Maven-bundled), which adds deployment complexity but is functionally equivalent.
3. **Defer term normalization** — `NoOpTermNormalizer` remains active. `QueryClassifier` (the core #424 capability) proceeds independently. Term normalization is re-scoped to a future issue.

## 7. Configuration

```properties
# Term normalization
casehub.knowledge.normalization.enabled=true

# Expansion strategy
casehub.knowledge.expansion.known-providers=google
casehub.knowledge.expansion.max-variant-queries=10

# Query classifier (LLM)
casehub.knowledge.classifier.enabled=true
casehub.knowledge.classifier.timeout=30s
```

## 8. Documentation Updates

Implementation must update documentation references to `QueryNormalizer`:

| File | Change |
|---|---|
| `docs/guides/consumer-guide.md` (line 103) | Replace `QueryNormalizer SPI` with `TermNormalizer SPI, QueryClassifier SPI` |
| `docs/guides/contributor-guide.md` (line 79) | Replace `QueryNormalizer SPI` with `TermNormalizer SPI, QueryClassifier SPI` |
| `CLAUDE.md` (line 143) | Auto-updated on code change — no manual update needed |

The Phase 1 design spec (`docs/specs/issue-413-knowledge-pipeline/2026-10-03-knowledge-pipeline-design.md`) is a historical record and should not be modified.

## References

- `knowledge-pipeline-api/.../QueryNormalizer.java` — existing SPI being replaced
- `knowledge-pipeline-api/.../KnowledgeQuery.java` — sealed query type hierarchy
- `knowledge-pipeline-api/.../NormalizedQuery.java` — query + cache key record (unchanged)
- `knowledge-pipeline/.../CacheKeyGenerator.java` — current static cache key generation
- `knowledge-pipeline/.../KnowledgePipelineOrchestrator.java` — search orchestration flow
- `caps-llm-classifier/.../LlmSituationClassifier.java` — AgentProvider integration pattern
- `connectors/location-spi/.../LocationPlatform.java` — Geocoding capability interface
- extJWNL — https://github.com/extjwnl/extjwnl (BSD license)
- WordNet 3.1 — Princeton University (Princeton WordNet license)

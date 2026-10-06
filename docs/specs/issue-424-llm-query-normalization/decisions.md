# Decisions — #424 LLM Query Normalization

## D1: Two-layer normalization architecture

**Choice:** Separate TermNormalizer SPI (lexical expansion) and QueryClassifier SPI (NL intent classification)
**Alternatives:**
- Unified QueryPipeline — single SPI handling both, simpler wiring but obscures the two distinct capabilities
- Transparent Decorator — @Decorator on KnowledgePipelineService, hides domain parameter consumers need
**Rationale:** The two capabilities have fundamentally different characteristics: TermNormalizer is pure Java, fast, runs on every query; QueryClassifier is LLM-backed, expensive, only for ambiguous NL. Clean separation enables independent testing and avoids coupling lexical lookup to LLM availability.
**Trade-offs:** Two SPIs to wire instead of one. Consumer needs to know which to call (though the orchestrator handles this internally).
**Module placement:** `TermNormalizer` SPI + `ExpandedTerm` record in `knowledge-pipeline-api` (Tier 1 pure Java, zero new deps). WordNet-backed implementation in `knowledge-pipeline` (runtime). `QueryClassifier` (LLM-backed) in `knowledge-pipeline` (runtime, CDI, uses `AgentProvider` via `Instance<>`). The existing `QueryNormalizer` SPI is replaced — not supplemented alongside. `TermNormalizer` handles the lexical expansion responsibility; `QueryClassifier` handles the NL→KnowledgeQuery classification that `QueryNormalizer` was originally scoped for.
**Sources:** KnowledgePipelineOrchestrator.java, QueryNormalizer.java, LlmSituationClassifier.java (AgentProvider pattern)
**Exploration:** quick
**Status:** revised (R1-03, R1-13 — clarified module placement and relationship to existing QueryNormalizer)

## D2: CBR-governed expansion strategy with exploration/exploitation

**Choice:** CBR learns per term/domain/provider whether to use canonical-only or canonical+variants, with periodic revision
**Alternatives:**
- Hardcoded policy per provider — simpler but can't adapt to provider changes or new providers
- Always expand — wasteful for smart providers that expand internally
**Rationale:** Makes expansion strategy empirical. During exploration, fire both canonical-only and canonical+variants, compare using overlap ratio (primary) and novel entity count (secondary). Accumulate confidence via EMA. Once confident, exploit the winning strategy. Re-test periodically unless provider is marked known-behavior (documented, deterministic — skip re-evaluation).
**Trade-offs:** Extra API calls during exploration phase. Requires CBR infrastructure dependency in knowledge-pipeline.
**Sources:** CbrRecordStore SPI, CbrOutcome, existing EMA-based recordOutcome
**Exploration:** quick
**Status:** captured

## D3: WordNet via extJWNL for lexical expansion

**Choice:** extJWNL 2.0.5 with bundled WordNet 3.1 dictionary (extjwnl-data-wn31)
**Alternatives:**
- JWI — CC-BY 4.0, requires separate dictionary files on disk, no Maven-bundled data
- Yawni — Apache 2.0, bundled data, but pre-release and stale
- ConceptNet — broader common-sense graph, but too noisy for term normalization and not easily embeddable
**Rationale:** Only library with dictionary data as a Maven dependency (classpath-embeddable, ~33MB). BSD license. Pure Java, sub-millisecond lookup. Feature-complete for synonym set lookups. Needs smoke test on Java 26.
**Trade-offs:** Last release 2022 — stable but not actively maintained. WordNet 3.1 is English-only. 33MB dictionary JAR adds to artifact size.
**Sources:** extJWNL GitHub, WordNet 3.1 documentation
**Exploration:** quick
**Status:** captured

## D4: Expansion quality metric — overlap ratio + novel entity count

**Choice:** Overlap ratio as primary metric, novel entity count as secondary signal
**Alternatives:**
- Result count — simple but rewards floods of irrelevant results
- Relevance scoring — requires ground truth labels we don't have
**Rationale:** If canonical+variants returns the same results as canonical-only, the provider already expands internally — low marginal value, canonical-only wins. Novel entity count captures the genuine benefit of expansion when it matters.
**Trade-offs:** Overlap ratio doesn't measure relevance quality of novel results.
**Sources:** KnowledgePipelineOrchestrator search flow, entity resolution dedup
**Exploration:** quick
**Status:** captured

## D5: Cache key canonicalization via TermNormalizer

**Choice:** CacheKeyGenerator uses canonical forms from TermNormalizer to produce cache keys, so "eatery", "restaurant", and "eating house" all resolve to the same cache bucket
**Alternatives:**
- Separate canonical cache alongside the existing key scheme — parallel lookup, but doubles cache complexity
- No canonicalization — each term variant gets its own cache key, accept lower hit rate
**Rationale:** The whole point of canonical forms is cache coherence. CacheKeyGenerator currently serializes KnowledgeQuery directly (e.g., `TEXT:italian restaurant`). With TermNormalizer, it normalizes each term to its canonical before key generation: `TEXT:italian restaurant` regardless of whether the user searched "eatery" or "restaurant". Without this, term expansion actively decreases cache hit rate by fragmenting lookups across synonym variants.
**Trade-offs:** CacheKeyGenerator now depends on TermNormalizer — previously it was a pure static utility. The canonical form must be deterministic and stable across WordNet versions.
**Depends on:** D1 (TermNormalizer SPI), D3 (WordNet canonical forms)
**Sources:** CacheKeyGenerator.java, NormalizedQuery.java, review finding R1-14
**Exploration:** quick
**Status:** captured

## D1: Facet scope — all 13 PID-5 facets, configurable

**Choice:** Include all 13 PID-5 facets by default, with a config property to restrict the subset
**Alternatives:**
- 7 perception-specific facets only — cleaner but hypothesis-driven; we don't know which facets help classification until empirical eval
- Dynamic filtering (only isHigh/isLow) — unpredictable cache key variation per agent
**Rationale:** We won't know which facets improve classification quality until we try. Configurability lets us narrow after eval without code changes.
**Trade-offs:** Longer prompts when all 13 are included; more cache key variation.
**Sources:** Issue #977 body (lists 7 facets), PersonalityFacet.java (13 constants), CognitiveDerivationEngine.java (uses 11 of 13)
**Exploration:** quick
**Status:** captured

## D2: Transport mechanism — metadata map extension

**Choice:** Extend the existing `Map<String, String>` metadata pattern with `pid5.*` and `appraisal.*` prefixed keys
**Alternatives:**
- Structured PersonalityContext record — would require SPI signature change on SituationClassifier
- Separate parameter alongside metadata — splits personality info across two arguments
**Rationale:** Established pattern (disposition axes use same approach), no SPI change needed, backward compatible.
**Trade-offs:** Doubles serialized as strings; metadata map grows larger.
**Sources:** BehavioralSynthesisPhase.java:394 (addDispositionMetadata pattern), PersonalityPromptBuilder.java (reads disposition.* keys)
**Exploration:** quick
**Status:** captured

## D3: CBR cache key precision — 2 decimal places

**Choice:** Round PID-5 scores and AppraisalWeights values to 2 decimal places for SHA-256 hash input
**Alternatives:**
- 1 decimal place — more cache reuse but meaningfully different profiles could share cached results
**Rationale:** Matches CognitiveDerivationEngine derivation precision. Real personality profiles don't cluster at 0.01 intervals, so cache fragmentation is minimal.
**Trade-offs:** Slightly fewer cache hits than 1dp quantization.
**Sources:** CbrCachingSituationDecorator.java (existing computeCaseId pattern), CognitiveDerivationEngine.java (derivation precision)
**Exploration:** quick
**Status:** captured

## D4: CBR filter strategy — hash only, no structural filters for numeric facets

**Choice:** Include PID-5 scores in the SHA-256 cache key hash but NOT as CbrFilter predicates on the query
**Alternatives:**
- Add numeric range filters for each facet — complex, and semantic embedding similarity already provides the right matching
**Rationale:** Disposition axes are categorical (exact-match semantics), so CbrFilter.contains() makes sense. PID-5 facets are continuous — the cache key hash plus embedding similarity provides sufficient discrimination. Adding numeric range filters per facet would multiply query complexity for marginal benefit.
**Trade-offs:** A cache miss when facets differ slightly will fall through to the LLM rather than finding a "close enough" cached result. This is correct behaviour — if the personality is different enough to change the hash, the classification should be fresh.
**Sources:** CbrCachingSituationDecorator.java:143-149 (existing filter pattern), CbrQuery (filter types)
**Exploration:** quick
**Status:** captured

## D5: Configuration property — facet inclusion list

**Choice:** `casehub.caps.classification.pid5-facets` config property, comma-separated facet names, default all 13
**Alternatives:**
- Hardcoded subset — inflexible, can't tune without code change
- Per-agent configuration — overkill; agents already have different facet scores, the config controls which facets the classifier sees
**Rationale:** Lightweight tuning knob for eval iteration. Single property read by both prompt builder and CBR cache.
**Trade-offs:** One more config property. Must validate facet names against known constants.
**Sources:** ClassificationCbrConfig.java (existing config pattern)
**Exploration:** quick
**Status:** captured

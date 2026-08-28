# Neocortex Cognitive Type Guide

A comprehensive reference for every memory, cognitive, and affective type in neocortex — what each does, how they compose, and what emerges from their combination.

---

## 1. Text Memory

**What it is.** The foundational memory layer. `CaseMemoryStore` stores free-text memories with domain tags, entity scoping, importance scores, and timestamps. `GraphCaseMemoryStore` extends this with semantic graph queries via Graphiti. Every other memory type ultimately serialises into this layer via converters (`ExperienceEvents.toMemoryInput`, `MoodEvents.toMemoryInput`, etc.).

**How it works.** `MemoryInput` carries text, domain, entityId, importance, and a `Map<String, String>` of attributes. The store persists it and returns a memoryId. Retrieval is by semantic search (text similarity), domain filtering, entity scoping, or tenant isolation. Backends: in-memory, SQLite (FTS5), JPA (PostgreSQL + `websearch_to_tsquery`), Mem0 (vector embeddings), Graphiti (temporal knowledge graph). `CaseEnrichmentDecorator` applies `CaseEnrichmentStep` pipelines before storage.

**Why it matters.** Without text memory, an agent has no recall. This is the substrate that everything else builds on — experiences, relationships, reflections, moods, and engagement all become domain-tagged text memories.

**Composition.** Feeds into: Personality (PersonalityWeightedRetrieval re-ranks memories by domain weights), Mood (MoodModulatedRetrieval boosts mood-congruent memories), MindMap (NodeRef cross-references memories from graph nodes). Fed by: Experience, Relationships, Reflection, Mood, Engagement — all converters write domain-tagged MemoryInputs into this store.

**Cognitive parallel.** Tulving's episodic memory — the ability to mentally re-experience past events, tagged by time, place, and context. The domain tag parallels Tulving's distinction between episodic and semantic memory systems.

**Research tags:** episodic memory, Tulving 1972, autobiographical memory, memory consolidation, semantic search, conversational AI memory, temporal context model

**Key insight.** The domain tag is the universal routing key. A single `CaseMemoryStore` instance holds experiences, relationships, reflections, moods, and engagement — differentiated only by `domain`. This means any backend that implements the SPI automatically supports all cognitive subsystems.

---

## 2. Experience

**What it is.** Typed agent actions modelled as a sealed hierarchy: `Observation` (what was perceived), `Action` (what the agent did), `Outcome` (what resulted). This gives the agent episodic memory — not just "what happened" but the role the agent played in it.

**How it works.** `ExperienceStream.record(ExperienceEvent)` converts via `ExperienceEvents.toMemoryInput` (domain="experience") with standardised attribute keys (`event-type`, `turn-id`, `subject`, `capability`, `result`, `target-agent`, `source-channel`), stores the memory, and fires `ExperienceRecorded` as a CDI event. `ExperienceQuery` provides factory helpers for retrieval: `forAgent`, `forAgentInCase`, `search`, `salient`.

**Why it matters.** Without typed experience, an agent can recall text but can't distinguish between things it observed, things it did, and things that resulted. This distinction is essential for learning from outcomes.

**Composition.** Feeds into: Relationships (RelationshipObserver observes ExperienceRecorded to detect inter-agent interactions), Reflection (ReflectionService queries experiences as source material), CBR (experiences inform case features). Fed by: engine/blocks orchestrators that call `ExperienceStream.record()`.

**Cognitive parallel.** Procedural memory and action schemas — the distinction between observing, acting, and experiencing outcomes maps to the perception-action-outcome cycle in cognitive psychology and BDI (Belief-Desire-Intention) agent architectures.

**Research tags:** procedural memory, action schemas, BDI architecture, Bratman 1987, experience replay, reinforcement learning, episodic control, cognitive robotics

**Key insight.** Experience events are the entry point for the entire social cognition pipeline. A single `ExperienceRecorded` CDI event cascades into relationship detection, reflection synthesis, and eventually mood and engagement updates — all without the recording agent knowing about any of these downstream consumers.

---

## 3. Relationships

**What it is.** Per-agent-pair interaction records that track the quality of inter-agent interactions. Each `RelationshipEvent` captures who interacted with whom, the source event type, and a `QualitySignal` (POSITIVE, NEGATIVE, NEUTRAL).

**How it works.** `RelationshipObserver` observes `ExperienceRecorded` CDI events, detects `target-agent` metadata, creates a `RelationshipEvent`, converts it via `RelationshipEvents.toMemoryInput` (domain="relationship"), and fires `RelationshipRecorded`. Self-referential events are rejected.

**Why it matters.** Agents can answer "how has my relationship with Agent X been?" — not from a single interaction, but from the accumulated pattern of quality signals over time.

**Composition.** Fed by: Experience (automatic via CDI observer). Feeds into: Trust (AgentTrustProvider can derive trust scores from relationship quality patterns), MindMap (relationship edges between agent nodes).

**Cognitive parallel.** Social cognition and theory of mind — tracking how interactions with specific individuals go over time, building a model of relationship quality that informs future social decisions.

**Research tags:** social cognition, theory of mind, Premack & Woodruff 1978, social network analysis, multi-agent systems, trust formation, interpersonal perception

**Key insight.** Relationship detection is fully automatic — no agent explicitly records relationships. The `RelationshipObserver` derives them from experience events that mention a `target-agent`. This means relationship tracking is zero-cost for the agent; it emerges from the experience stream.

---

## 4. Reflection

**What it is.** Meta-cognitive insight synthesis. `ReflectionEvent` captures an insight (text), its hierarchical level (higher = more abstract), and source memory IDs for traceability. `ReflectionSynthesizer` is the SPI that produces reflections from raw experiences.

**How it works.** `ReflectionService.reflect(agentId, tenantId, since)` queries recent experiences, passes them to `ReflectionSynthesizer.synthesize()`, stores each reflection with domain="reflection" and importance derived from level (`Math.min(0.3 + level*0.2, 1.0)`), and fires `ReflectionRecorded`. The `NoOpReflectionSynthesizer` @DefaultBean returns empty; a real LLM-backed implementation displaces it.

**Why it matters.** Reflection transforms episodic memory into abstract knowledge. An agent that only records experiences accumulates data; an agent that reflects extracts patterns and principles.

**Composition.** Fed by: Experience (source material). Feeds into: Text Memory (reflections are high-importance memories that surface in retrieval), Personality (reflections about interaction patterns inform disposition weights).

**Cognitive parallel.** Metacognition and self-monitoring — "thinking about thinking." Hierarchical reflection levels mirror the metacognitive hierarchy in Flavell's framework: level 1 is monitoring (what happened?), higher levels are regulation (what does this mean? what should I do differently?).

**Research tags:** metacognition, Flavell 1979, self-monitoring, reflective practice, Schon 1983, hierarchical reinforcement learning, introspection, cognitive architectures

**Key insight.** Importance scales with level — level 1 reflections (direct observations) get importance 0.5, level 3 (abstract principles) get 0.9. This means reflections naturally outcompete raw experiences in retrieval, ensuring that synthesised knowledge surfaces before episodic detail.

---

## 5. Mood & Emotion

**What it is.** Dynamic emotional state using the PAD (Pleasure-Arousal-Dominance) dimensional model. `MoodState` records the agent's current emotional state as three values in [-1, 1]. `MoodBaseline` defines the per-agent resting point. `MoodDecay` applies exponential decay back toward baseline.

**How it works.** `MoodEvents.toMemoryInput` converts MoodState to a memory with domain="mood" and attribute keys for each PAD dimension. `MoodModulatedRetrieval` extends `PersonalityWeightedRetrieval` with mood-congruent recall — memories annotated with PAD dimensions get boosted when their affect aligns with the agent's current mood. The `moodInfluence` parameter (0-1) controls how strongly mood biases retrieval.

**Why it matters.** Mood-congruent recall is a well-established cognitive phenomenon — people in positive moods recall positive memories more easily. This gives agents the same property, making their responses emotionally coherent rather than affectively flat.

**Composition.** Fed by: Engagement (sentiment shifts affect mood), blocks MoodOrchestrator. Feeds into: Text Memory retrieval (mood-congruent re-ranking), MindMap (PAD annotations on nodes/edges represent the emotional character of knowledge), Curiosity (affect-aware dampening suppresses probing of emotionally negative topics).

**Cognitive parallel.** Core affect theory (Russell 2003) and the PAD emotional model (Mehrabian 1996) — continuous dimensional representation of emotion rather than discrete categories, enabling mood-congruent processing as observed in Bower's (1981) associative network theory of mood and memory.

**Research tags:** PAD model, Mehrabian 1996, core affect, Russell 2003, mood-congruent memory, Bower 1981, affective computing, Picard 1997, emotion regulation, appraisal theory

**Key insight.** PAD appears in two distinct roles: on `MoodState` it represents the agent's subjective emotional state; on `MindMapNode`/`MindMapEdge` it represents the emotional character of the knowledge itself. These are different things — "Alice is sad" (agent mood) vs. "losing a job is negative" (knowledge affect) — but the same dimensional model enables mood-congruent retrieval across both.

---

## 6. Engagement

**What it is.** Per-interaction social outcome measurement. `EngagementEvent` captures granular signals about how an interaction went: whether the other party responded, how quickly, how long the response was, sentiment shift, reaction count, and whether the conversation continued.

**How it works.** `EngagementStream.record(EngagementEvent)` converts via `EngagementEvents.toMemoryInput` (domain="engagement") with attribute keys for each signal, fires `EngagementRecorded`. All signal fields are nullable — record what you can observe.

**Why it matters.** Engagement is the quantitative counterpart to Relationships' qualitative signals. "Was my message well-received?" is answered by engagement metrics, not by the experience stream's action/outcome model.

**Composition.** Fed by: blocks orchestrators that observe interaction outcomes. Feeds into: Mood (sentiment shifts), Relationships (engagement quality informs relationship signals), CuriosityDrive (blocks' CuriosityDrive already uses hygiene data; engagement could feed curiosity about why interactions succeed or fail).

**Cognitive parallel.** Social reward processing — the neural circuitry that tracks whether social interactions are rewarding or aversive, driving approach/avoidance behaviour in future interactions.

**Research tags:** social reward, social neuroscience, reciprocity, Axelrod 1984, conversational analysis, interaction quality, user engagement metrics, sentiment analysis

**Key insight.** Self-referential rejection prevents an agent from recording engagement with itself. This is a quiet but important invariant — without it, internal reasoning loops would pollute the engagement signal.

---

## 7. Personality

**What it is.** Disposition-weighted retrieval. `PersonalityWeights` maps each `MemoryDomain` to a weight multiplier. `PersonalityWeightedRetrieval` re-ranks retrieved memories by `domainWeight × recencyDecay × importance`, with a 7-day exponential half-life on recency.

**How it works.** Pure utility — no CDI, no storage. Callers pass their personality weights and retrieved memories; the function returns re-ranked results. This is per-query opt-in, not a global filter. Recency decay uses a 7-day half-life, meaning week-old memories score at 50% of fresh ones regardless of domain weight.

**Why it matters.** Different agents prioritise different types of knowledge. An analytically-oriented agent weights experience and reflection highly; a socially-oriented agent weights relationships and engagement. Same memories, different priorities.

**Composition.** Fed by: Agent configuration (weights come from eidos AgentDescriptor). Feeds into: Text Memory retrieval (re-ranking), Mood (MoodModulatedRetrieval extends this with mood-congruent recall).

**Cognitive parallel.** Trait theory in personality psychology — stable individual differences that bias perception and behaviour. While the Big Five (Costa & McCrae) uses fixed axes, neocortex uses configurable domain weights, closer to Allport's idiographic approach where each individual's trait structure is unique.

**Research tags:** trait theory, Big Five, Costa & McCrae 1992, Allport 1937, individual differences, personality-based retrieval, user modelling, recommender systems, cognitive style

**Key insight.** Personality is not stored — it's applied. The weights live in the agent descriptor (eidos), not in neocortex. This means personality is a lens on memory, not a memory itself. The same memories look different through different personality weights.

---

## 8. CBR — Case-Based Reasoning

**What it is.** Structured similarity search over past cases. Unlike text memory's semantic search, CBR compares cases by typed feature vectors with configurable per-field similarity functions. Three paradigms: Textual (problem text), Feature-Vector (structured fields), Plan-Based (step sequences for reuse).

**How it works.** `CbrCaseMemoryStore` registers schemas (`CbrFeatureSchema` with `FeatureField` definitions), stores cases with typed `FeatureValue` fields, and retrieves by weighted similarity (`CbrSimilarityScorer`). Seven feature value types (StringVal through StructListVal), ten field types (Categorical through DiscreteSequence), six similarity specs (CategoricalTable through EditDistanceSpec), and eight filter types (Contains through AllOf) provide the vocabulary for describing case structure. `CbrOutcome.recordOutcome` implements the Revise step of the CBR cycle via EMA confidence adjustment.

**Why it matters.** Text memory answers "what do I know about X?" CBR answers "what similar situations have I seen before, and what happened?" This is the foundation for learning from past cases — the agent doesn't just recall, it reasons by analogy.

**Composition.** Feeds into: Plan Adaptation (retrieved PlanCbrCases are adapted for the current context), Trust (trust scores modulate retrieval), Temporal (TemporalDecay applies recency bias). Fed by: blocks orchestrators that store domain-specific cases (mental models, strategies, user profiles, narratives).

**Cognitive parallel.** Analogical reasoning and Schank's dynamic memory theory — retrieving and adapting past experiences to solve new problems. The three CBR paradigms (textual, feature-vector, plan-based) correspond to surface similarity, structural similarity, and procedural similarity in analogical reasoning research.

**Research tags:** case-based reasoning, Kolodner 1993, Schank 1982, analogical reasoning, Gentner 1983, similarity-based retrieval, feature matching, prototype theory, nearest-neighbour, structured prediction

**Key insight.** The three-level similarity precedence (caller override → field SimilaritySpec → type default) means the same schema can produce different similarity rankings depending on the query context. A "find similar strategies" query might weight risk differently than a "find similar outcomes" query, even against the same case base.

---

## 9. CBR — Temporal & Trend

**What it is.** Time-aware reasoning over case histories. `TemporalDecay` (HalfLife, Linear, Step) applies smooth recency bias post-scoring. `TrendAnalyzer` computes derived metrics from TimeSeries fields: slope, volatility, acceleration, change-points (CUSUM), duration, and observation count. `DtwSimilarity` and `EditDistanceSimilarity` compare temporal sequences.

**How it works.** `TrendEnrichmentCbrCaseMemoryStore` (@Decorator @Priority(90)) intercepts schema registration to expand TimeSeries fields with derived trend features, enriches stored cases and queries with computed trend values, all transparently. `LbKeogh` provides O(n) lower-bound pruning to filter DTW candidates before the full O(n×m) DP computation.

**Why it matters.** Cases evolve over time. A strategy that worked last month may be declining. Trend detection surfaces "this pattern is accelerating" or "this approach has plateauing outcomes" — temporal context that flat similarity misses.

**Composition.** Fed by: CBR (operates on TimeSeries and DiscreteSequence fields). Feeds into: CBR retrieval (trend-enriched features participate in similarity scoring), Curiosity (stale nodes in the MindMap trigger temporal curiosity signals).

**Cognitive parallel.** Temporal reasoning and pattern recognition — the ability to detect trends, periodicities, and change-points in sequential data, mirroring how humans notice "things are getting worse" or "this pattern is accelerating" before they can articulate why.

**Research tags:** temporal reasoning, time-series analysis, dynamic time warping, Sakoe & Chiba 1978, change-point detection, CUSUM, trend analysis, temporal abstraction, sequence alignment

**Key insight.** Trend enrichment is schema-driven and transparent — the decorator reads `TrendSpec` from the field definition and enriches automatically. Callers don't know trends are being computed; they just see richer feature vectors.

---

## 10. CBR — Plan Adaptation

**What it is.** The Reuse step of the CBR cycle for plan-based cases. `PlanAdapter` transforms a retrieved `PlanCbrCase` into an `AdaptedPlan` for the current context, marking each step with an `AdaptationAction` (RETAINED, SUBSTITUTED, BOOSTED, SUPPRESSED, ADDED, REMOVED). `PlanEnsembleAnalyzer` synthesises across multiple adapted plans, computing `StepConsensus` with agreement levels (UNANIMOUS through UNIQUE).

**How it works.** For each retrieved plan, `PlanAdapter.adapt()` evaluates each step against the current features and decides what to change. `PlanEnsembleAnalyzer.analyze()` then examines all adapted plans together, looking for consensus (steps that appear in most plans), divergence (steps that appear in few), and contested areas (different plans disagree). The `EnsemblePlan` synthesises a recommended plan from the consensus.

**Why it matters.** Single-case adaptation can overfit to one past experience. Ensemble analysis across multiple retrieved cases reveals which steps are robustly applicable (they appear in many similar situations) and which are situation-specific (they appear in only one case).

**Composition.** Fed by: CBR retrieval (provides the scored cases to adapt). Feeds into: Traceability (AdaptationTrace and EnsembleTrace record the decisions for audit), engine/blocks (the adapted plan drives agent behaviour).

**Cognitive parallel.** Plan reuse and means-ends analysis (Newell & Simon 1972) — adapting known solutions to new problems rather than planning from scratch. Ensemble analysis parallels committee machines in ML and the "wisdom of crowds" effect in decision science.

**Research tags:** plan adaptation, Newell & Simon 1972, means-ends analysis, plan reuse, Kambhampati 1990, ensemble methods, committee machines, wisdom of crowds, Surowiecki 2004, case adaptation

**Key insight.** `StepAgreement` distinguishes UNANIMOUS (all plans agree) from CONSENSUS (most agree) from CONTESTED (evenly split). This gives the consumer a confidence signal per step — act confidently on UNANIMOUS steps, cautiously on CONTESTED ones.

---

## 11. CBR — Trust & Authority

**What it is.** Source-authority modulation on CBR retrieval. `AgentTrustProvider` supplies trust scores per agent. `TrustWeightingFunction` modulates similarity scores by trust authority and trajectory (is trust rising or falling?). `OutcomeWeightingFunction` modulates by case confidence (from `CbrOutcome` EMA).

**How it works.** `TrustWeightedCbrCaseMemoryStore` (@Decorator @Priority(60)) injects the trust provider and weighting function. For each retrieved case, it looks up the source agent's trust score, applies the weighting function (`score * (1-α + α*trustScore)`), and optionally adjusts for trust trajectory (declining trust further dampens scores). `ScopeDecay` (Exponential, Linear, Step) adds hierarchical scope-distance decay.

**Why it matters.** Not all past cases are equally trustworthy. A case from a highly-trusted agent should score higher than one from an untrusted source, even if feature similarity is identical. Trust weighting implements this.

**Composition.** Fed by: Relationships and Engagement (trust scores derive from interaction patterns). Feeds into: CBR retrieval (modulates final scores).

**Cognitive parallel.** Social trust and reputation systems — humans track not just "do I trust this person?" but "is my trust increasing or decreasing?" The trajectory sensitivity maps to research on trust repair and betrayal aversion in behavioural economics.

**Research tags:** social trust, reputation systems, trust formation, Marsh 1994, betrayal aversion, Bohnet & Zeckhauser 2004, multi-agent trust, computational trust models, authority weighting

**Key insight.** Trajectory sensitivity means a declining trust score dampens more than a stable low score. An agent whose trust is falling is treated with more suspicion than one whose trust has always been low — matching the human intuition that betrayal is worse than unfamiliarity.

---

## 12. Traceability

**What it is.** Audit infrastructure for compliance and debugging. `CbrRetrievalTrace` snapshots every retrieval event (query, scored cases, timestamp). `AdaptationTrace` records plan adaptation decisions. `EnsembleTrace` records ensemble analysis. `CbrCasesErased` and `MemoryEntityErased` fire CDI events after erasure operations for GDPR compliance.

**How it works.** Tracking decorators (@Priority(50) on CbrCaseMemoryStore, PlanAdapter, PlanEnsembleAnalyzer) fire CDI events after operations complete. `SqliteCbrRetrievalTracker` persists traces with retention-based purging (default 90 days, daily purge cycle). Domain filtering on `findTraces()` supports per-use-case audit.

**Why it matters.** Explainability — "why did the agent do that?" requires tracing back through retrieval, adaptation, and ensemble decisions. GDPR compliance requires proving that erasure cascaded to all stores.

**Composition.** Fed by: CBR retrieval, Plan Adaptation, Ensemble Analysis, Erasure operations. Feeds into: audit/compliance consumers, debugging tools, ExplanationRenderer (human-readable trace rendering).

**Cognitive parallel.** Autobiographical memory and memory provenance — not just "what happened" but "how do I know this?" and "where did this knowledge come from?" Source monitoring (Johnson et al. 1993) is the cognitive process of attributing memories to their origins.

**Research tags:** source monitoring, Johnson 1993, autobiographical memory, provenance tracking, audit trail, explainable AI, GDPR compliance, memory attribution, metacognitive monitoring

**Key insight.** The three trace types (retrieval, adaptation, ensemble) form a linked audit chain via `retrievalTraceId`. A single agent action can be traced from "which cases were retrieved?" through "how was each adapted?" to "what did the ensemble recommend?"

---

## 13. Structural Knowledge (MindMap)

**What it is.** A typed knowledge graph of entities and relationships. `MindMapNode` carries a name, confidence, PAD affect, traits, NodeRefs (cross-store references), temporal validity, and arbitrary properties. `MindMapEdge` carries a type (governed by `MindMapVocabulary`), ValidationTier, and its own confidence and affect. Nodes belong to typed `MindMapSubgraph`s (PERSON, PROJECT, RESEARCH_AREA, ORGANISATION, CONCEPT, GENERAL).

**How it works.** `MindMapStore` provides graph-structural CRUD: nodes, edges, aliases, subgraphs, merge, traversal, search, supersession, and erasure. Four decorators enrich operations transparently: VocabularyNormalization (alias resolution on addEdge), DerivedEdge (forward-chaining rules), TraitApplication (automatic trait classification), ConfidenceDecay (time-based confidence erosion). `ConfidenceOrigin` (STATED 1.0, INFERRED 0.7, SPECULATED 0.3) seeds initial confidence.

**Why it matters.** Text memory and CBR store isolated facts and cases. The MindMap connects them — "Alice works at Acme" is not just a fact but a relationship between two typed entities, traversable, mergeable, and queryable as a graph. This is the connective tissue that makes cross-subsystem queries possible.

**Composition.** Fed by: Graph Intelligence (MindMapExtractor populates the graph from conversations). Feeds into: Curiosity (MindMapAnalyzer produces structural signals), Trait System (traits are applied to nodes), Text Memory (NodeRef cross-references to memories), blocks orchestrators (graph traversal for context).

**Cognitive parallel.** Semantic memory and knowledge graphs — Quillian's (1968) semantic networks model concepts as nodes with typed relationships. The MindMap extends this with temporal validity, confidence decay, and affect annotations that semantic networks lack.

**Research tags:** semantic memory, Quillian 1968, knowledge graphs, semantic networks, ontology, RDF, property graphs, knowledge representation, Sowa 1984, concept maps

**Key insight.** `NodeRef` is the bridge pattern that keeps the graph loosely coupled to other stores. A MindMap node can reference a `CaseMemoryStore` memory, a `CbrCaseMemoryStore` case, or an external URL — all with the same `(scheme, id, qualifier)` tuple. The graph stores references, not copies.

---

## 14. Graph Intelligence

**What it is.** Analysis and extraction capabilities on the MindMap graph. `MindMapAnalyzer` provides 8 static analysis methods (orphanNodes, degreeCentrality, subgraphDensity, unvalidatedEdgeRatio, contradictions, lowConfidenceCluster, staleNodes, betweennessCentrality). `MindMapExtractor` uses AgentProvider to extract entities and relationships from conversation text. `DerivedEdgeRule` and `TraitRule` SPIs enable pluggable graph enrichment.

**How it works.** `MindMapExtractor.extract(text, tenantId)` queries the graph for context, builds an LLM prompt with JSON extraction schema, invokes AgentProvider, parses the response, resolves entities against existing nodes, and creates/updates graph structure. `DerivedEdgeDecorator` fires rules on every addEdge, creating inferred edges (e.g., has-child → parent-of) with provenance tracking and truth maintenance. `TraitApplicationDecorator` evaluates TraitRule beans on every mutation, applying/retracting traits automatically.

**Why it matters.** Raw graph structure is only half the value. Intelligence turns a passive knowledge store into an active knowledge system — the graph grows from conversations, infers new relationships from rules, classifies entities by traits, and detects structural anomalies.

**Composition.** Fed by: Text (conversation text for extraction), MindMap (graph structure for analysis). Feeds into: MindMap (new nodes, edges, traits), Curiosity (analysis signals), blocks (extracted entities inform orchestrators).

**Cognitive parallel.** Forward chaining in production systems (Newell 1990) and knowledge compilation (Anderson's ACT-R). The pattern of LLM-discovered rules being compiled into deterministic forward-chaining rules mirrors ACT-R's transition from declarative to procedural knowledge.

**Research tags:** forward chaining, production systems, Newell 1990, knowledge compilation, ACT-R, Anderson 1993, information extraction, named entity recognition, relation extraction, knowledge graph construction

**Key insight.** Derived edge rules implement "learned inference caching" — the LLM discovers a relationship pattern once (expensive), then the rule fires deterministically forever after (free). The explicit-to-transparent promotion path means the system gets cheaper over time as more patterns are codified.

---

## 15. Curiosity

**What it is.** Knowledge-seeking drive signals computed from graph analysis. `CuriositySignalGenerator` produces a ranked list of `CuriositySignal` records, each with a `SignalCategory` (STRUCTURAL, QUALITY, TEMPORAL, CENTRALITY, PROXIMITY), a score, a target entity, and a template-generated question.

**How it works.** The generator iterates all subgraphs, collects signals from MindMapAnalyzer (orphans, contradictions, stale nodes, etc.), adds temporal proximity signals for approaching events (`1/(1+d/7)` urgency ramp), applies PAD affect dampening (PROXIMITY bypasses dampening), applies topical distance dampening (BFS from recent conversation entities), and sorts by score descending.

**Why it matters.** Without curiosity, an agent only knows what it's been told. Curiosity identifies gaps ("I know Alice's name but nothing about her role"), contradictions ("two different employers"), and upcoming events ("meeting in 3 days — what should I prepare?") — driving the agent to ask questions proactively.

**Composition.** Fed by: MindMap (graph structure), Graph Intelligence (MindMapAnalyzer signals), Mood (PAD affect dampening). Feeds into: blocks CuriosityDrive (via CuriositySignalProvider SPI), active learning question generation, agent conversation planning.

**Cognitive parallel.** Epistemic curiosity and Loewenstein's (1994) information gap theory — curiosity arises from the perception of a gap between what one knows and what one wants to know. The signal categories map to different types of gaps: structural (missing connections), quality (uncertain knowledge), temporal (stale facts), and proximity (approaching relevance).

**Research tags:** epistemic curiosity, Loewenstein 1994, information gap theory, Berlyne 1960, intrinsic motivation, active learning, exploration-exploitation, knowledge gaps, question generation

**Key insight.** The topical distance dampening (D41) is the cycle-breaker. Without it, filling one knowledge gap creates adjacent gaps, creating a cascade of ever-more-peripheral questions. Dampening by hop distance from recent conversation topics ensures curiosity stays focused near what the agent is actually discussing.

---

## 16. Trait System

**What it is.** Dynamic typing for graph nodes. Trait interfaces (`Personable`, `Projectlike`, `Organisational`) define typed property accessors. `TraitProxy` generates JDK Proxy instances that map method names to node properties. `TraitRule` implementations determine when traits apply based on node properties and edges.

**How it works.** `TraitProxy.as(node, Personable.class)` returns a proxy where `birthday()` reads `node.property("birthday")`. `TraitApplicationDecorator` evaluates all TraitRule beans on every node/edge mutation, applying traits when rules match and retracting when no rules match. A `ThreadLocal<Boolean>` reentrancy guard prevents recursive evaluation.

**Why it matters.** Without traits, every consumer must understand the raw property bag. With traits, consumers work with typed interfaces — `personable.email()` instead of `node.property("email")`. Traits also enable trait-aware queries (search for nodes with specific traits).

**Composition.** Fed by: MindMap (node properties and edges trigger trait evaluation), Graph Intelligence (MindMapExtractor adds properties that trigger traits). Feeds into: MindMap (traits stored as node metadata), Graph Intelligence (trait-aware curiosity and analysis).

**Cognitive parallel.** Categorisation and prototype theory (Rosch 1973) — classifying entities by their properties and relationships, with categories defined by characteristic features rather than necessary-and-sufficient conditions. Traits are prototypes, not rigid classes.

**Research tags:** categorisation, prototype theory, Rosch 1973, trait inference, dynamic classification, Drools traits, ontology alignment, entity typing, knowledge graph typing

**Key insight.** Truth maintenance is the quiet killer feature. When an edge that triggered a trait is removed, the decorator re-evaluates and retracts the trait automatically. The graph's type classification stays consistent with its structure without any manual intervention.

---

## 17. RAG

**What it is.** Retrieval-Augmented Generation — the pipeline that feeds external documents into LLM context. `CaseRetriever` retrieves relevant document chunks for a query. `EmbeddingIngestor` ingests documents into vector stores. `QueryExpander` enriches queries (HyDE, step-back, multi-query). `RelevanceEvaluator` quality-gates retrieved chunks (CRAG). `RetrievalAnalyzer` computes corpus-level analytics.

**How it works.** Hybrid search: dense embeddings (LangChain4j + Qdrant), sparse embeddings (SPLADE via inference-splade), BM25 (server-side Qdrant inference), fused via weighted RRF or convex combination (`ScoreFusion` from fusion-api). Decorators add quality-gating (CRAG @Priority(100)), reranking (cross-encoder @Priority(75)), payload boosting (@Priority(60)), query expansion, and tracking. `CorpusIngestionService` bridges corpus modules to the vector store.

**Why it matters.** Agents need external knowledge beyond their memory. RAG provides it — corporate documents, knowledge bases, and domain corpora become queryable context for agent responses.

**Composition.** Fed by: Corpus (document storage), Inference (ONNX models for embeddings and reranking). Feeds into: engine/blocks (retrieved context for agent responses), CBR (RetrievalAnalyzer patterns inform future retrieval strategies).

**Cognitive parallel.** External memory augmentation and transactive memory (Wegner 1987) — offloading knowledge to external systems and knowing where to find it rather than storing it internally. RAG is the computational equivalent of "I don't know the answer, but I know where to look."

**Research tags:** transactive memory, Wegner 1987, retrieval-augmented generation, Lewis et al 2020, external memory, knowledge retrieval, vector search, hybrid search, dense retrieval, sparse retrieval

**Key insight.** The fusion-api module is shared between RAG and CBR — the same `ScoreFusion` algorithms (weighted RRF, convex combination) that fuse multi-leg document retrieval also fuse multi-leg case retrieval. This is not code reuse for its own sake; it's a recognition that "combine ranked lists from different signals" is a domain-independent operation.

---

## Composition Map

```
Conversation Text
       │
       ▼
┌─────────────────┐     ┌──────────────┐
│  MindMap         │◄────│  Graph       │
│  Extractor (14)  │     │  Intelligence│
└───────┬─────────┘     └──────┬───────┘
        │                      │
        ▼                      ▼
┌──────────────┐    ┌──────────────────┐
│  Structural  │───►│  Curiosity (15)  │
│  Knowledge   │    │  Signals         │
│  MindMap (13)│    └────────┬─────────┘
└──────┬───────┘             │
       │                     ▼
       │              ┌──────────────┐
       │              │  blocks      │
       │              │  CuriosityDrive
       │              └──────────────┘
       │
       ▼
┌──────────────┐    ┌───────────────┐
│  Trait       │    │  Experience   │──►┌──────────────┐
│  System (16) │    │  (2)          │   │ Relationship │
└──────────────┘    └───────┬───────┘   │ (3)          │
                            │           └──────┬───────┘
                            ▼                  │
                    ┌───────────────┐           ▼
                    │  Reflection   │   ┌──────────────┐
                    │  (4)          │   │  Trust (11)  │
                    └───────┬───────┘   └──────┬───────┘
                            │                  │
           ┌────────────────┼──────────────────┘
           ▼                ▼
    ┌──────────────┐  ┌──────────────┐
    │  Text Memory │  │  CBR (8-10)  │
    │  (1)         │  │              │
    └──────┬───────┘  └──────┬───────┘
           │                 │
           ▼                 ▼
    ┌──────────────┐  ┌──────────────┐
    │  Personality  │  │ Traceability │
    │  (7)         │  │ (12)         │
    └──────┬───────┘  └──────────────┘
           │
           ▼
    ┌──────────────┐  ┌──────────────┐
    │  Mood (5)    │  │ Engagement   │
    │              │◄─│ (6)          │
    └──────────────┘  └──────────────┘

    ┌──────────────┐
    │  RAG (17)    │ ← External document retrieval (parallel to memory)
    └──────────────┘
```

---

## Emergent Capabilities

**1. Mood-Congruent Recall** (Mood + Personality + Text Memory). An agent in a positive mood, with high personality weight on relationships, retrieves positive relationship memories first. The same query from the same agent in a negative mood retrieves negative ones. Three independent systems compose into emotionally coherent recall.

**2. Proactive Knowledge-Seeking** (Curiosity + MindMap + Mood). The curiosity engine detects sparse subgraphs ("I know Alice's name but nothing else"), affect-dampens emotionally negative topics, and generates focused questions. When combined with blocks' CuriosityDrive and the InnerLifeOrchestrator tick loop, the agent asks questions unprompted — but avoids insensitive probing.

**3. Trust-Weighted Analogical Reasoning** (CBR + Trust + Temporal). When retrieving similar past cases, trust weighting elevates cases from reliable agents, temporal decay deprioritises stale cases, and trend enrichment surfaces cases with improving outcomes. The result is not just "similar cases" but "similar cases from trustworthy sources with recent positive trajectories."

**4. Self-Improving Inference** (Graph Intelligence + DerivedEdgeRule + MindMap Extractor). The LLM extraction layer discovers relationship patterns, which get codified as DerivedEdgeRules. Each rule fires deterministically on future edge additions — zero token cost. The system's inference capability grows over time without growing its LLM budget.

**5. Cross-System Entity Understanding** (MindMap + Text Memory + CBR + NodeRef). "Tell me everything about Alice" traverses Alice's MindMap node, follows NodeRefs to her text memories and CBR cases, reads her engagement patterns, and checks her relationship quality signals. No single store has the full picture; the graph's cross-references unify them.

---

## Design Principles

**SPI + Decorator.** Every cognitive subsystem follows the same pattern: a pure-Java SPI in an `-api` module, CDI `@Decorator` beans for transparent enrichment, and backend-specific implementations selected by CDI priority. This means every subsystem is independently testable, backend-swappable, and transparently enrichable.

**CDI Events for Loose Coupling.** Subsystems communicate via CDI events (`ExperienceRecorded`, `RelationshipRecorded`, `CbrRetrievalRecorded`, `MemoryEntityErased`), not direct method calls. This means adding a new observer (e.g., MindMap listening for experience events) requires zero changes to the event producer.

**Sealed Hierarchies for Exhaustive Modelling.** `ExperienceEvent`, `FeatureValue`, `FeatureField`, `SimilaritySpec`, `CbrFilter`, `TemporalDecay`, `ScopeDecay`, `WarpingConstraint`, `AdaptationAction`, `StepAgreement`, `MemoryEntityErased`, `CbrCasesErased` — all sealed. The compiler enforces exhaustive handling. Adding a new variant is a deliberate SPI evolution, not a silent addition.

**Domain Tags as Universal Routing.** All cognitive subsystem data flows through `CaseMemoryStore` via domain-tagged `MemoryInput`. Experience, relationships, reflections, moods, and engagement are differentiated only by their `domain` string. Any backend that implements the store SPI automatically supports all subsystems.

**Pure Computation Utilities.** `PersonalityWeightedRetrieval`, `MoodModulatedRetrieval`, `MindMapAnalyzer`, `TrendAnalyzer`, `CbrSimilarityScorer`, `DtwSimilarity`, `ScoreFusion` — all pure static utilities with zero CDI dependencies. They can be used in tests, in non-Quarkus contexts, and in any combination without wiring overhead.

**Confidence as a First-Class Dimension (cf. ACT-R's activation).** Every knowledge type carries confidence: `MindMapNode.confidence()` with `ConfidenceOrigin`, `CbrCase` with `CbrOutcome` EMA, `MoodState` with PAD values, `ReflectionEvent` with level-derived importance. Confidence decays over time (ConfidenceDecayDecorator, TemporalDecay), gets reinforced by confirmation (`confirmedAt`), and modulates retrieval (minConfidence filters, trust weighting). The system knows what it doesn't know.

---

## Theoretical Foundations

Neocortex is not an implementation of any single cognitive architecture, but it draws from several and extends them in ways that reflect its nature as an LLM-era agent platform.

### ACT-R (Anderson 1993)

The strongest parallel is with ACT-R's memory system. ACT-R's **declarative memory** (chunks with activation levels that decay and get reinforced by use) maps directly to neocortex's text memory with confidence decay and confirmation-based reinforcement. ACT-R's **procedural memory** (production rules that compile from declarative knowledge) maps to the DerivedEdgeRule mechanism — patterns discovered by LLM extraction get compiled into deterministic forward-chaining rules, mirroring ACT-R's knowledge compilation process. ACT-R's **activation-based retrieval** (higher-activation chunks are retrieved first) maps to confidence-weighted retrieval plus personality and mood modulation.

Where neocortex diverges: ACT-R's subsymbolic layer uses mathematical activation functions; neocortex uses explicit confidence origins (STATED/INFERRED/SPECULATED) that carry provenance information ACT-R's activations do not.

### SOAR (Laird 2012)

SOAR's **working memory + long-term memory** architecture parallels neocortex's split between LLM context (working memory, managed by the engine) and persistent stores (long-term memory, managed by neocortex). SOAR's **chunking** (learning new rules from problem-solving episodes) parallels the reflection system — both transform episodic experience into reusable abstract knowledge. SOAR's **impasse-driven learning** parallels the curiosity engine — both detect knowledge gaps and drive the system to resolve them.

Where neocortex diverges: SOAR is a unified architecture; neocortex is a composable library. SOAR's modules are tightly coupled; neocortex's are independently deployable via CDI. This trades SOAR's architectural coherence for practical deployability.

### Global Workspace Theory (Baars 1988)

GWT's central claim — that consciousness arises from a "global workspace" that broadcasts information to specialised processors — has a structural parallel in neocortex's CDI event system. `ExperienceRecorded`, `RelationshipRecorded`, `CbrRetrievalRecorded`, and other CDI events are broadcast to all observers, each of which may or may not act on the information. The MindMap graph serves as a persistent global workspace — a shared substrate that any cognitive subsystem can read from and write to.

Where neocortex diverges: GWT's workspace is a bottleneck (only one thing is "conscious" at a time); neocortex's CDI events are concurrent and non-exclusive. Multiple observers can process the same event simultaneously.

### BDI Architecture (Bratman 1987, Rao & Georgeff 1995)

The Belief-Desire-Intention model underlies the blocks orchestrator layer (not in neocortex, but the primary consumer). Neocortex provides the **Belief** infrastructure — text memory, CBR, and MindMap all store the agent's beliefs about the world. The **Desire** infrastructure is partially here too — CuriosityDrive generates desires to know more, MoodState influences which desires surface. **Intentions** are managed by the engine/blocks layer.

### What's Novel

Three aspects of neocortex don't map cleanly to classical cognitive architectures:

1. **Affective knowledge** — PAD annotations on graph nodes/edges (not just on the agent's mood state) mean knowledge itself carries emotional valence. Classical architectures model agent mood but not knowledge affect.

2. **Trust-modulated memory** — no classical architecture includes source trust as a retrieval parameter. Neocortex's trust weighting with trajectory sensitivity is closer to computational models of social trust (Marsh 1994) than to cognitive architecture memory systems.

3. **LLM-backed knowledge compilation** — the pattern of using an LLM to discover rules that then execute deterministically is unique to the post-LLM era. Classical architectures learn rules from experience; neocortex learns them from language.

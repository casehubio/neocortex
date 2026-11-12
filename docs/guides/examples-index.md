# Capability-to-example index

Use this index to find a consumer API demonstration and its named test. Coverage here means an example exercises the capability; it does not certify every backend or every production configuration. Test links point to source files, with the method named explicitly.

The eleven modules are enabled by the root `examples-smoke` profile. Run with Java 21 or a compatible JDK:

```bash
mvn test -Pexamples-smoke
```

Smoke-tagged example tests use deterministic in-memory implementations. The `examples` profile enables model downloads and infrastructure-backed examples; `*IT` methods below require real models and/or Qdrant. `example-corpus-management` is currently included only in `examples-smoke`. See the [examples README](../../examples/README.md) for model sizes and module entry points.


## Neural text inference

| Capability / API | Example module | Test method |
|---|---|---|
| NLI entailment/contradiction and paired inference | `example-text-analysis` | [`NliDemoTest.allDomainsProduceResults`](../../examples/example-text-analysis/src/test/java/io/casehub/neocortex/examples/analysis/NliDemoTest.java) |
| Real ONNX NLI scores | `example-text-analysis` | [`NliDemoIT.entailmentScoredHighForSemanticImplication`](../../examples/example-text-analysis/src/test/java/io/casehub/neocortex/examples/analysis/NliDemoIT.java) |
| Zero-shot candidate ranking through NliClassifier | `example-text-analysis` | [`ZeroShotClassificationDemoTest.allTextsProduceRankedLabels`](../../examples/example-text-analysis/src/test/java/io/casehub/neocortex/examples/analysis/ZeroShotClassificationDemoTest.java) |
| Real zero-shot classification across domains | `example-text-analysis` | [`CrossDomainConsistencyIT.zeroShotProducesDistinctLabelsPerDomain`](../../examples/example-text-analysis/src/test/java/io/casehub/neocortex/examples/analysis/CrossDomainConsistencyIT.java) |
| TextClassifier sentiment scoring | `example-text-analysis` | [`ScoringDemoTest.sentimentDimensionProducesResults`](../../examples/example-text-analysis/src/test/java/io/casehub/neocortex/examples/analysis/ScoringDemoTest.java) |
| Real model sentiment ordering | `example-text-analysis` | [`ScoringDemoIT.positiveTextScoresHigherThanNegative`](../../examples/example-text-analysis/src/test/java/io/casehub/neocortex/examples/analysis/ScoringDemoIT.java) |
| CrossEncoderReranker ordering | `example-text-analysis` | [`RerankingDemoTest.rerankProducesOrderedResults`](../../examples/example-text-analysis/src/test/java/io/casehub/neocortex/examples/analysis/RerankingDemoTest.java) |
| Real cross-encoder changes result order | `example-text-analysis` | [`RerankingDemoIT.rerankingActuallyReorders`](../../examples/example-text-analysis/src/test/java/io/casehub/neocortex/examples/analysis/RerankingDemoIT.java) |
| SparseEmbedder / SPLADE sparse vectors | `example-text-analysis` | [`SparseEmbeddingDemoTest.allTextsProduceSparseVectors`](../../examples/example-text-analysis/src/test/java/io/casehub/neocortex/examples/analysis/SparseEmbeddingDemoTest.java) |
| InMemoryInferenceModel smoke fixtures | `example-text-analysis` | [`NliDemoTest.includesEntailmentAndContradictionPairs`](../../examples/example-text-analysis/src/test/java/io/casehub/neocortex/examples/analysis/NliDemoTest.java) |
| Empty-input handling | `example-text-analysis` | [`EdgeCaseTest.nliHandlesEmptyStrings`](../../examples/example-text-analysis/src/test/java/io/casehub/neocortex/examples/analysis/EdgeCaseTest.java) |
| Model-load failure handling | `example-text-analysis` | [`EdgeCaseTest.missingModelFileThrowsModelLoadException`](../../examples/example-text-analysis/src/test/java/io/casehub/neocortex/examples/analysis/EdgeCaseTest.java) |

## Corpus ingestion and RAG

| Capability / API | Example module | Test method |
|---|---|---|
| FlatCorpusStore, ChangeSource, CorpusReader, CorpusIngestionBinding, EmbeddingIngestor | `example-rag-pipeline` | [`FlatCorpusIngestSmokeTest.ingestsAllDocuments`](../../examples/example-rag-pipeline/src/test/java/io/casehub/neocortex/examples/rag/FlatCorpusIngestSmokeTest.java) |
| CursorStore incremental ingestion | `example-rag-pipeline` | [`FlatCorpusIngestSmokeTest.incrementalIngestProducesNoNewChunks`](../../examples/example-rag-pipeline/src/test/java/io/casehub/neocortex/examples/rag/FlatCorpusIngestSmokeTest.java) |
| MetadataExtractor / YamlFrontmatterExtractor | `example-rag-pipeline` | [`FlatCorpusIngestSmokeTest.metadataExtractedFromFrontmatter`](../../examples/example-rag-pipeline/src/test/java/io/casehub/neocortex/examples/rag/FlatCorpusIngestSmokeTest.java) |
| ZipCorpusStore, tombstones and TOMBSTONES_ONLY compaction | `example-rag-pipeline` | [`ZipCorpusIngestSmokeTest.zipIngestAndCompaction`](../../examples/example-rag-pipeline/src/test/java/io/casehub/neocortex/examples/rag/ZipCorpusIngestSmokeTest.java) |
| CaseRetriever query/output wiring with an in-memory retriever | `example-rag-pipeline` | [`HybridSearchSmokeTest.allQueriesProduceFormattedOutput`](../../examples/example-rag-pipeline/src/test/java/io/casehub/neocortex/examples/rag/HybridSearchSmokeTest.java) |
| Real dense+sparse retrieval, fusion and reranking with Qdrant/CDI wiring | `example-rag-pipeline` | [`RagPipelineIT.techQueryReturnsTechDocs`](../../examples/example-rag-pipeline/src/test/java/io/casehub/neocortex/examples/rag/RagPipelineIT.java) |
| Document metadata survives ingestion and retrieval | `example-rag-pipeline` | [`RagPipelineIT.metadataRoundTrips`](../../examples/example-rag-pipeline/src/test/java/io/casehub/neocortex/examples/rag/RagPipelineIT.java) |
| Legal/news corpus retrieval | `example-rag-pipeline` | [`RagPipelineIT.legalQueryReturnsLegalDocs`](../../examples/example-rag-pipeline/src/test/java/io/casehub/neocortex/examples/rag/RagPipelineIT.java) |
| Malformed frontmatter handling | `example-rag-pipeline` | [`RagEdgeCaseTest.corruptFrontmatterTreatedAsBody`](../../examples/example-rag-pipeline/src/test/java/io/casehub/neocortex/examples/rag/RagEdgeCaseTest.java) |
| Empty corpus ingestion | `example-rag-pipeline` | [`RagEdgeCaseTest.emptyCorpusProducesNoChunks`](../../examples/example-rag-pipeline/src/test/java/io/casehub/neocortex/examples/rag/RagEdgeCaseTest.java) |

## CBR memory

| Capability / API | Example module | Test method |
|---|---|---|
| Feature-vector CBR for AML investigations | `example-cbr` | [`AmlInvestigationDemoTest.structuringQueryReturnsMatchingCases`](../../examples/example-cbr/src/test/java/io/casehub/neocortex/examples/cbr/AmlInvestigationDemoTest.java) |
| Clinical adverse-event matching | `example-cbr` | [`ClinicalAdverseEventDemoTest.hepatotoxicityQueryReturnsMatchingCases`](../../examples/example-cbr/src/test/java/io/casehub/neocortex/examples/cbr/ClinicalAdverseEventDemoTest.java) |
| PR review / software change matching | `example-cbr` | [`DevtownPrReviewDemoTest.refactorQueryReturnsMatchingCases`](../../examples/example-cbr/src/test/java/io/casehub/neocortex/examples/cbr/DevtownPrReviewDemoTest.java) |
| IoT situation matching | `example-cbr` | [`IotSituationDemoTest.temperatureAnomalyKitchenQueryReturnsMatchingCases`](../../examples/example-cbr/src/test/java/io/casehub/neocortex/examples/cbr/IotSituationDemoTest.java) |
| Contractor case matching | `example-cbr` | [`LifeContractorDemoTest.plumbingHvacQueryReturnsMatchingCases`](../../examples/example-cbr/src/test/java/io/casehub/neocortex/examples/cbr/LifeContractorDemoTest.java) |
| Plan traces and plan analysis | `example-cbr` | [`QuarkmindBattleDemoTest.planAnalysisShowsScoutCorrelation`](../../examples/example-cbr/src/test/java/io/casehub/neocortex/examples/cbr/QuarkmindBattleDemoTest.java) |
| All six CBR domain demos | `example-cbr` | [`CbrDemoRunnerTest.allSixDemosRunWithoutError`](../../examples/example-cbr/src/test/java/io/casehub/neocortex/examples/cbr/CbrDemoRunnerTest.java) |
| Real dense-vector CBR retrieval through Qdrant | `example-cbr` | [`CbrIntegrationTest.denseVectorSearchRanksByProblemSimilarity`](../../examples/example-cbr/src/test/java/io/casehub/neocortex/examples/cbr/CbrIntegrationTest.java) |
| CBR minimum similarity filtering | `example-cbr` | [`CbrIntegrationTest.minSimilarityFiltersLowScores`](../../examples/example-cbr/src/test/java/io/casehub/neocortex/examples/cbr/CbrIntegrationTest.java) |
| CBR time filtering | `example-cbr` | [`CbrIntegrationTest.notBeforeFiltersOldCases`](../../examples/example-cbr/src/test/java/io/casehub/neocortex/examples/cbr/CbrIntegrationTest.java) |
| CBR domain isolation | `example-cbr` | [`CbrIntegrationTest.crossDomainIsolation`](../../examples/example-cbr/src/test/java/io/casehub/neocortex/examples/cbr/CbrIntegrationTest.java) |
| Plan trace persistence | `example-cbr` | [`CbrIntegrationTest.planTraceRoundTripsThroughQdrant`](../../examples/example-cbr/src/test/java/io/casehub/neocortex/examples/cbr/CbrIntegrationTest.java) |

## Thing and knowledge model

| Capability / API | Example module | Test method |
|---|---|---|
| Freeform Thing nodes, NodeInput and subgraph-derived type | `example-knowledge-model` | [`KnowledgeModelWalkthroughTest.phase1_rawNotes_capturedAsGeneralThings`](../../examples/example-knowledge-model/src/test/java/io/casehub/neocortex/examples/knowledge/KnowledgeModelWalkthroughTest.java) |
| Typed entity properties and typed graph edges | `example-knowledge-model` | [`KnowledgeModelWalkthroughTest.phase2_entityExtraction_createsTypedThings`](../../examples/example-knowledge-model/src/test/java/io/casehub/neocortex/examples/knowledge/KnowledgeModelWalkthroughTest.java) |
| TraitRule evaluation, Thing.is(), Thing.as(), traits as types | `example-knowledge-model` | [`KnowledgeModelWalkthroughTest.phase3_traitEmergence_rulesFireAsPropertiesAccumulate`](../../examples/example-knowledge-model/src/test/java/io/casehub/neocortex/examples/knowledge/KnowledgeModelWalkthroughTest.java) |
| TypeRegistry dynamic registration, hierarchy and Java class mapping | `example-knowledge-model` | [`KnowledgeModelWalkthroughTest.phase4_dynamicTypes_llmDiscoversNewType`](../../examples/example-knowledge-model/src/test/java/io/casehub/neocortex/examples/knowledge/KnowledgeModelWalkthroughTest.java) |
| TypeRegistry property schemas for core and dynamic types | `example-knowledge-model` | [`KnowledgeModelWalkthroughTest.phase5_propertySchema_schemaForDynamicAndCoreTypes`](../../examples/example-knowledge-model/src/test/java/io/casehub/neocortex/examples/knowledge/KnowledgeModelWalkthroughTest.java) |
| Text/subgraph queries, neighbor traversal and Subject-to-Thing bridge | `example-knowledge-model` | [`KnowledgeModelWalkthroughTest.phase6_graphQueries_queryEnrichedKnowledge`](../../examples/example-knowledge-model/src/test/java/io/casehub/neocortex/examples/knowledge/KnowledgeModelWalkthroughTest.java) |

## Cognitive index and agent memory

| Capability / API | Example module | Test method |
|---|---|---|
| Knowledge graph plus per-agent overlays | `example-cognitive-index` | [`CognitiveIndexWalkthroughTest.phase1_buildTheWorld`](../../examples/example-cognitive-index/src/test/java/io/casehub/neocortex/examples/cognitive/CognitiveIndexWalkthroughTest.java) |
| Single-entity cognitive resolution | `example-cognitive-index` | [`CognitiveIndexWalkthroughTest.phase2_singleEntityResolve`](../../examples/example-cognitive-index/src/test/java/io/casehub/neocortex/examples/cognitive/CognitiveIndexWalkthroughTest.java) |
| Perspectival resolution | `example-cognitive-index` | [`CognitiveIndexWalkthroughTest.phase3_perspectivalResolve`](../../examples/example-cognitive-index/src/test/java/io/casehub/neocortex/examples/cognitive/CognitiveIndexWalkthroughTest.java) |
| Multi-agent comparison | `example-cognitive-index` | [`CognitiveIndexWalkthroughTest.phase4_multiAgentCompare`](../../examples/example-cognitive-index/src/test/java/io/casehub/neocortex/examples/cognitive/CognitiveIndexWalkthroughTest.java) |
| Social comparison | `example-cognitive-index` | [`CognitiveIndexWalkthroughTest.phase5_socialComparison`](../../examples/example-cognitive-index/src/test/java/io/casehub/neocortex/examples/cognitive/CognitiveIndexWalkthroughTest.java) |
| Cross-domain cognitive correlation | `example-cognitive-index` | [`CognitiveIndexWalkthroughTest.phase6_crossDomainCorrelation`](../../examples/example-cognitive-index/src/test/java/io/casehub/neocortex/examples/cognitive/CognitiveIndexWalkthroughTest.java) |
| Recording agent experiences | `example-agent-memory` | [`AgentMemoryWalkthroughTest.phase1_recordExperiences`](../../examples/example-agent-memory/src/test/java/io/casehub/neocortex/examples/memory/AgentMemoryWalkthroughTest.java) |
| Mood tracking | `example-agent-memory` | [`AgentMemoryWalkthroughTest.phase2_moodTracking`](../../examples/example-agent-memory/src/test/java/io/casehub/neocortex/examples/memory/AgentMemoryWalkthroughTest.java) |
| Engagement measurement | `example-agent-memory` | [`AgentMemoryWalkthroughTest.phase3_engagementMeasurement`](../../examples/example-agent-memory/src/test/java/io/casehub/neocortex/examples/memory/AgentMemoryWalkthroughTest.java) |
| Memory query patterns | `example-agent-memory` | [`AgentMemoryWalkthroughTest.phase4_memoryQueryPatterns`](../../examples/example-agent-memory/src/test/java/io/casehub/neocortex/examples/memory/AgentMemoryWalkthroughTest.java) |
| Personality-weighted modulation | `example-agent-memory` | [`AgentMemoryWalkthroughTest.phase5_personalityWeightedModulation`](../../examples/example-agent-memory/src/test/java/io/casehub/neocortex/examples/memory/AgentMemoryWalkthroughTest.java) |
| Mood-congruent memory retrieval | `example-agent-memory` | [`AgentMemoryWalkthroughTest.phase6_moodCongruence`](../../examples/example-agent-memory/src/test/java/io/casehub/neocortex/examples/memory/AgentMemoryWalkthroughTest.java) |

## Advanced CBR and retrieval analytics

| Capability / API | Example module | Test method |
|---|---|---|
| Temporal feature schemas, DTW and trends | `example-cbr-advanced` | [`CbrAdvancedWalkthroughTest.schemaRegistersTemporalFieldsWithDtwAndTrends`](../../examples/example-cbr-advanced/src/test/java/io/casehub/neocortex/examples/cbr/advanced/CbrAdvancedWalkthroughTest.java) |
| Time-series similarity retrieval | `example-cbr-advanced` | [`CbrAdvancedWalkthroughTest.retrieveSimilarCasesByDtwOnHeartRate`](../../examples/example-cbr-advanced/src/test/java/io/casehub/neocortex/examples/cbr/advanced/CbrAdvancedWalkthroughTest.java) |
| Temporal decay | `example-cbr-advanced` | [`CbrAdvancedWalkthroughTest.temporalDecayPenalisesOlderCases`](../../examples/example-cbr-advanced/src/test/java/io/casehub/neocortex/examples/cbr/advanced/CbrAdvancedWalkthroughTest.java) |
| Hierarchical path/ward scoping | `example-cbr-advanced` | [`CbrAdvancedWalkthroughTest.filterByWardAndUseHierarchicalScoping`](../../examples/example-cbr-advanced/src/test/java/io/casehub/neocortex/examples/cbr/advanced/CbrAdvancedWalkthroughTest.java) |
| Outcome feedback / EMA confidence updates | `example-cbr-advanced` | [`CbrAdvancedWalkthroughTest.outcomesFeedbackAdjustsConfidenceViaEma`](../../examples/example-cbr-advanced/src/test/java/io/casehub/neocortex/examples/cbr/advanced/CbrAdvancedWalkthroughTest.java) |
| Supersession filtering | `example-cbr-advanced` | [`CbrAdvancedWalkthroughTest.supersessionExcludesCasesFromRetrieval`](../../examples/example-cbr-advanced/src/test/java/io/casehub/neocortex/examples/cbr/advanced/CbrAdvancedWalkthroughTest.java) |
| Discrete-sequence edit distance | `example-cbr-advanced` | [`CbrAdvancedWalkthroughTest.editDistanceComparessTreatmentStepSequences`](../../examples/example-cbr-advanced/src/test/java/io/casehub/neocortex/examples/cbr/advanced/CbrAdvancedWalkthroughTest.java) |
| RetrievalTracker / RetrievalAnalyzer document statistics | `example-rag-analytics` | [`RagAnalyticsWalkthroughTest.documentStatistics_revealRetrievalPatterns`](../../examples/example-rag-analytics/src/test/java/io/casehub/neocortex/examples/rag/analytics/RagAnalyticsWalkthroughTest.java) |
| Query gap analysis | `example-rag-analytics` | [`RagAnalyticsWalkthroughTest.queryAnalysis_findCorpusGaps`](../../examples/example-rag-analytics/src/test/java/io/casehub/neocortex/examples/rag/analytics/RagAnalyticsWalkthroughTest.java) |
| Query-document correlation graph | `example-rag-analytics` | [`RagAnalyticsWalkthroughTest.correlationGraph_revealsQueryDocumentRelationships`](../../examples/example-rag-analytics/src/test/java/io/casehub/neocortex/examples/rag/analytics/RagAnalyticsWalkthroughTest.java) |
| Query clustering | `example-rag-analytics` | [`RagAnalyticsWalkthroughTest.queryClusters_discoverRelatedQueryGroups`](../../examples/example-rag-analytics/src/test/java/io/casehub/neocortex/examples/rag/analytics/RagAnalyticsWalkthroughTest.java) |
| Document impact / centrality | `example-rag-analytics` | [`RagAnalyticsWalkthroughTest.documentImpact_ranksByCentrality`](../../examples/example-rag-analytics/src/test/java/io/casehub/neocortex/examples/rag/analytics/RagAnalyticsWalkthroughTest.java) |
| ScoreFusion RRF versus convex combination | `example-rag-analytics` | [`RagAnalyticsWalkthroughTest.scoreFusionComparison_rrfVsConvexCombination`](../../examples/example-rag-analytics/src/test/java/io/casehub/neocortex/examples/rag/analytics/RagAnalyticsWalkthroughTest.java) |
| Relevance calibration | `example-rag-analytics` | [`RagAnalyticsWalkthroughTest.relevanceCalibration_deriveThresholdsFromData`](../../examples/example-rag-analytics/src/test/java/io/casehub/neocortex/examples/rag/analytics/RagAnalyticsWalkthroughTest.java) |

## MindMap intelligence and goals

| Capability / API | Example module | Test method |
|---|---|---|
| Graph construction and trait emergence | `example-mindmap-intelligence` | [`MindMapIntelligenceWalkthroughTest.phase2_traitEmergence`](../../examples/example-mindmap-intelligence/src/test/java/io/casehub/neocortex/examples/mindmap/intelligence/MindMapIntelligenceWalkthroughTest.java) |
| Structural graph analysis | `example-mindmap-intelligence` | [`MindMapIntelligenceWalkthroughTest.phase3_structuralAnalysis`](../../examples/example-mindmap-intelligence/src/test/java/io/casehub/neocortex/examples/mindmap/intelligence/MindMapIntelligenceWalkthroughTest.java) |
| Graph quality analysis | `example-mindmap-intelligence` | [`MindMapIntelligenceWalkthroughTest.phase4_qualityAnalysis`](../../examples/example-mindmap-intelligence/src/test/java/io/casehub/neocortex/examples/mindmap/intelligence/MindMapIntelligenceWalkthroughTest.java) |
| Community detection | `example-mindmap-intelligence` | [`MindMapIntelligenceWalkthroughTest.phase5_communityDetection`](../../examples/example-mindmap-intelligence/src/test/java/io/casehub/neocortex/examples/mindmap/intelligence/MindMapIntelligenceWalkthroughTest.java) |
| Betweenness centrality | `example-mindmap-intelligence` | [`MindMapIntelligenceWalkthroughTest.phase6_betweennessCentrality`](../../examples/example-mindmap-intelligence/src/test/java/io/casehub/neocortex/examples/mindmap/intelligence/MindMapIntelligenceWalkthroughTest.java) |
| Temporal staleness | `example-mindmap-intelligence` | [`MindMapIntelligenceWalkthroughTest.phase7_temporalStaleness`](../../examples/example-mindmap-intelligence/src/test/java/io/casehub/neocortex/examples/mindmap/intelligence/MindMapIntelligenceWalkthroughTest.java) |
| Recurring thought generation | `example-mindmap-intelligence` | [`MindMapIntelligenceWalkthroughTest.phase8_recurrenceGeneration`](../../examples/example-mindmap-intelligence/src/test/java/io/casehub/neocortex/examples/mindmap/intelligence/MindMapIntelligenceWalkthroughTest.java) |
| Vocabulary and edge types | `example-mindmap-intelligence` | [`MindMapIntelligenceWalkthroughTest.phase9_vocabularyAndEdgeTypes`](../../examples/example-mindmap-intelligence/src/test/java/io/casehub/neocortex/examples/mindmap/intelligence/MindMapIntelligenceWalkthroughTest.java) |
| Goal graph and Goallike trait access | `example-goal-cognition` | [`GoalCognitionWalkthroughTest.phase2_goallikeTraitAccess`](../../examples/example-goal-cognition/src/test/java/io/casehub/neocortex/examples/goal/GoalCognitionWalkthroughTest.java) |
| Goal vocabulary / relationships | `example-goal-cognition` | [`GoalCognitionWalkthroughTest.phase3_goalVocabularyAndEdges`](../../examples/example-goal-cognition/src/test/java/io/casehub/neocortex/examples/goal/GoalCognitionWalkthroughTest.java) |
| Goal expansion | `example-goal-cognition` | [`GoalCognitionWalkthroughTest.phase4_expandApproachingGoal`](../../examples/example-goal-cognition/src/test/java/io/casehub/neocortex/examples/goal/GoalCognitionWalkthroughTest.java) |
| Goal pruning | `example-goal-cognition` | [`GoalCognitionWalkthroughTest.phase5_pruneDistantGoal`](../../examples/example-goal-cognition/src/test/java/io/casehub/neocortex/examples/goal/GoalCognitionWalkthroughTest.java) |
| Shared subgoal merging | `example-goal-cognition` | [`GoalCognitionWalkthroughTest.phase6_mergeSharedSubGoals`](../../examples/example-goal-cognition/src/test/java/io/casehub/neocortex/examples/goal/GoalCognitionWalkthroughTest.java) |
| Goal dependency revision | `example-goal-cognition` | [`GoalCognitionWalkthroughTest.phase7_reviseDependencies`](../../examples/example-goal-cognition/src/test/java/io/casehub/neocortex/examples/goal/GoalCognitionWalkthroughTest.java) |
| Goal affect | `example-goal-cognition` | [`GoalCognitionWalkthroughTest.phase8_goalAffect`](../../examples/example-goal-cognition/src/test/java/io/casehub/neocortex/examples/goal/GoalCognitionWalkthroughTest.java) |
| Goal priority | `example-goal-cognition` | [`GoalCognitionWalkthroughTest.phase9_goalPrioritization`](../../examples/example-goal-cognition/src/test/java/io/casehub/neocortex/examples/goal/GoalCognitionWalkthroughTest.java) |
| Goal retrieval modulation | `example-goal-cognition` | [`GoalCognitionWalkthroughTest.phase10_retrievalModulation`](../../examples/example-goal-cognition/src/test/java/io/casehub/neocortex/examples/goal/GoalCognitionWalkthroughTest.java) |

## Corpus management

| Capability / API | Example module | Test method |
|---|---|---|
| Domain lifecycle through CorpusApi: clinical | `example-corpus-management` | [`ClinicalScenarioTest.fullLifecycle`](../../examples/example-corpus-management/src/test/java/io/casehub/neocortex/examples/corpus/domains/clinical/ClinicalScenarioTest.java) |
| Domain lifecycle through CorpusApi: literature | `example-corpus-management` | [`LiteratureScenarioTest.fullLifecycle`](../../examples/example-corpus-management/src/test/java/io/casehub/neocortex/examples/corpus/domains/literature/LiteratureScenarioTest.java) |
| Domain lifecycle through CorpusApi: NPC | `example-corpus-management` | [`NpcScenarioTest.fullLifecycle`](../../examples/example-corpus-management/src/test/java/io/casehub/neocortex/examples/corpus/domains/npc/NpcScenarioTest.java) |
| Domain lifecycle through CorpusApi: garden | `example-corpus-management` | [`GardenScenarioTest.fullLifecycle`](../../examples/example-corpus-management/src/test/java/io/casehub/neocortex/examples/corpus/domains/garden/GardenScenarioTest.java) |
| CorpusEntryStore round trip | `example-corpus-management` | [`GardenScenarioTest.entryStoreRoundTrip`](../../examples/example-corpus-management/src/test/java/io/casehub/neocortex/examples/corpus/domains/garden/GardenScenarioTest.java) |
| Federated search across domains | `example-corpus-management` | [`FederatedSearchScenarioTest.federatedSearchAcrossDomains`](../../examples/example-corpus-management/src/test/java/io/casehub/neocortex/examples/corpus/federation/FederatedSearchScenarioTest.java) |
| Federation depth limits | `example-corpus-management` | [`FederatedSearchScenarioTest.federatedSearchWithDepthLimit`](../../examples/example-corpus-management/src/test/java/io/casehub/neocortex/examples/corpus/federation/FederatedSearchScenarioTest.java) |
| Archived corpora excluded as starting points | `example-corpus-management` | [`FederatedSearchScenarioTest.archivedCorpusNotAStartingPoint`](../../examples/example-corpus-management/src/test/java/io/casehub/neocortex/examples/corpus/federation/FederatedSearchScenarioTest.java) |
| Scoped federation | `example-corpus-management` | [`FederatedSearchScenarioTest.scopedFederatedSearch`](../../examples/example-corpus-management/src/test/java/io/casehub/neocortex/examples/corpus/federation/FederatedSearchScenarioTest.java) |
| Federated search reports | `example-corpus-management` | [`FederatedSearchScenarioTest.searchReportTracksSearchedCorpora`](../../examples/example-corpus-management/src/test/java/io/casehub/neocortex/examples/corpus/federation/FederatedSearchScenarioTest.java) |


## Demonstrations with an automated-coverage gap

| Capability | Current example | Missing verification |
|---|---|---|
| `CorpusIngestionService.reconcile()` | [`FlatCorpusIngestDemo.main`](../../examples/example-rag-pipeline/src/main/java/io/casehub/neocortex/examples/rag/FlatCorpusIngestDemo.java) | Named example test asserting reconciliation repairs drift |


## Capabilities needing an example

These consumer surfaces have implementation/unit tests elsewhere but lack a dedicated consumer walkthrough in the current example modules. Use this list to select future examples.

| Area | Example gap |
|---|---|
| Inference | `ScalarRegressor`, `TensorClassifier`, BGE-M3 multi-modal embeddings, ColBERT multi-vector retrieval, Bayesian inference / BJT and LLM-assisted Bayesian model construction |
| RAG extensions | Query expansion/augmentation, retrieval caching, payload quality boosts, ingest deduplication, PDF/Tika document parsing, and Spring wiring |
| Memory backends | JPA/PostgreSQL, SQLite, Mem0 and Graphiti backend configuration and persistence lifecycle |
| MindMap lifecycle | Alias resolution, node merge conflict handling, supersession/reinstatement and erasure through a consumer workflow |
| CAPS | Topology loading, settling, situation classification and classifier tier graduation as a standalone consumer example |
| Cognition runtime | Complete `CognitionCore.tick()` wiring, appraisal, emotion regulation, social goal routing, session lifecycle, inner life and consolidation |
| Observability | Pipeline snapshot persistence and querying across SQLite/Spring deployments |
| Corpus/web ingestion | Web capture, interventions/session transfer, images, Git sources and custom document-parser wiring |
| Extension wiring | Schema generation and consumer-defined knowledge-pipeline extensions |

When adding a capability, add a row pointing to an asserting example test, or retain an explicit gap. A demo that only prints output should not be recorded as an asserting test.

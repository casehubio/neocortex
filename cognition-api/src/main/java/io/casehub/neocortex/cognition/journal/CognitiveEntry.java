package io.casehub.neocortex.cognition.journal;

import io.casehub.neocortex.cognition.appraisal.AppraisalResult;
import io.casehub.neocortex.cognition.regulation.EmotionRegulationResult;
import io.casehub.neocortex.cognitive.observability.GraphMutation;
import io.casehub.neocortex.memory.mood.MoodBaseline;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public sealed interface CognitiveEntry {

    String tenantId();
    String agentId();
    EventRef eventRef();
    Instant timestamp();

    // --- Derivation domain ---

    record DerivationStarted(String tenantId, String agentId, EventRef eventRef, Instant timestamp,
                             String axes, List<String> profileTerms,
                             List<String> facetNames, List<String> goals) implements CognitiveEntry {}

    record StageComputed(String tenantId, String agentId, EventRef eventRef, Instant timestamp,
                         String method, Map<String, Double> baseValues,
                         List<FacetEffect> facetEffects,
                         Map<String, Double> finalValues) implements CognitiveEntry {}

    record DataFlowed(String tenantId, String agentId, EventRef eventRef, Instant timestamp,
                      String sourceMethod, String sourceField,
                      String targetMethod, String targetField,
                      double value) implements CognitiveEntry {}

    record DerivationCompleted(String tenantId, String agentId, EventRef eventRef,
                               Instant timestamp) implements CognitiveEntry {}

    // --- Runtime tick domain ---

    record AttentionBriefed(String tenantId, String agentId, EventRef eventRef, Instant timestamp,
                            @Nullable String briefingContent) implements CognitiveEntry {}

    record MoodShifted(String tenantId, String agentId, EventRef eventRef, Instant timestamp,
                       @Nullable MoodBaseline before, @Nullable MoodBaseline after,
                       String trigger) implements CognitiveEntry {}

    record DriveActivated(String tenantId, String agentId, EventRef eventRef, Instant timestamp,
                          List<String> driveCategories,
                          Map<String, Double> intensities) implements CognitiveEntry {}

    record AppraisalRecorded(String tenantId, String agentId, EventRef eventRef, Instant timestamp,
                             @Nullable AppraisalResult result,
                             Map<String, Double> concernSalience) implements CognitiveEntry {}

    record RegulationDecided(String tenantId, String agentId, EventRef eventRef, Instant timestamp,
                             @Nullable EmotionRegulationResult result) implements CognitiveEntry {}

    record GoalDecided(String tenantId, String agentId, EventRef eventRef, Instant timestamp,
                       List<String> goalsFormed,
                       List<String> goalsRevised) implements CognitiveEntry {}

    record CapsSettled(String tenantId, String agentId, EventRef eventRef, Instant timestamp,
                       Map<String, Double> inputActivations,
                       @Nullable CapsSettlingResult settling) implements CognitiveEntry {}
    // --- Stage tick domain ---

    record TemporalFocusTicked(String tenantId, String agentId, EventRef eventRef, Instant timestamp,
                               int subjectCount) implements CognitiveEntry {}

    record MemoryHygieneTicked(String tenantId, String agentId, EventRef eventRef,
                               Instant timestamp) implements CognitiveEntry {}

    record NarrativeUpdated(String tenantId, String agentId, EventRef eventRef,
                            Instant timestamp) implements CognitiveEntry {}

    record StrategyUpdated(String tenantId, String agentId, EventRef eventRef,
                           Instant timestamp) implements CognitiveEntry {}

    record ReflectionRetrieved(String tenantId, String agentId, EventRef eventRef,
                               Instant timestamp) implements CognitiveEntry {}

    record UserModelUpdated(String tenantId, String agentId, EventRef eventRef, Instant timestamp,
                            String subjectId) implements CognitiveEntry {}

    record MentalModelUpdated(String tenantId, String agentId, EventRef eventRef, Instant timestamp,
                              String subjectId) implements CognitiveEntry {}

    record TheoryOfMindInferred(String tenantId, String agentId, EventRef eventRef, Instant timestamp,
                                String subjectId, String dominantValence,
                                double dominantProbability) implements CognitiveEntry {}


    // --- Memory and consolidation domain ---

    record MemoryRecalled(String tenantId, String agentId, EventRef eventRef, Instant timestamp,
                          String domain, int resultCount) implements CognitiveEntry {}

    record MemoryFormed(String tenantId, String agentId, EventRef eventRef, Instant timestamp,
                        String domain, double salienceScore,
                        @Nullable String contentSummary) implements CognitiveEntry {}

    record GraphMutated(String tenantId, String agentId, EventRef eventRef, Instant timestamp,
                        GraphMutation mutation) implements CognitiveEntry {}

    record Consolidated(String tenantId, String agentId, EventRef eventRef, Instant timestamp,
                        int inputNodes, int outputNodes,
                        long elapsedMs, String phase) implements CognitiveEntry {}

    record ObservationCacheRefreshed(String tenantId, String agentId, EventRef eventRef,
                                     Instant timestamp,
                                     int condensedNodes, int recentNodes,
                                     int tokenBudget, int actualTokens) implements CognitiveEntry {}

    record SituationalModulation(String tenantId, String agentId, EventRef eventRef, Instant timestamp,
                                 Map<String, Double> modeIntensities,
                                 @Nullable String affectiveWinner,
                                 @Nullable String socialWinner,
                                 Map<String, Double> compositeDelta,
                                 int publishedFacetCount,
                                 String profileProvenance) implements CognitiveEntry {}

    record SituationalDeformationConsolidated(String tenantId, String agentId, EventRef eventRef,
                                              Instant timestamp,
                                              int hoursAdvanced,
                                              int facetsWithDrift) implements CognitiveEntry {}

    record DispositionReflected(String tenantId, String agentId, EventRef eventRef, Instant timestamp,
                                String outcomeType, double driftMagnitude,
                                @Nullable String previousLabel, @Nullable String newLabel) implements CognitiveEntry {}

    record DispositionEvolutionFailed(String tenantId, String agentId, EventRef eventRef, Instant timestamp,
                                      String step, String message) implements CognitiveEntry {}

    record DispositionSignalEpochReset(String tenantId, String agentId, EventRef eventRef, Instant timestamp,
                                       String oldFingerprint, String newFingerprint) implements CognitiveEntry {}

    record DispositionEvolutionEnablementChanged(String tenantId, String agentId, EventRef eventRef,
                                                 Instant timestamp, boolean enabled,
                                                 double relationalComplexity) implements CognitiveEntry {}

    record DispositionReauthored(String tenantId, String agentId, EventRef eventRef,
                                 Instant timestamp) implements CognitiveEntry {}

    record DispositionReauthorReverted(String tenantId, String agentId, EventRef eventRef,
                                       Instant timestamp) implements CognitiveEntry {}

    record ReflectionClockRegressed(String tenantId, String agentId, EventRef eventRef, Instant timestamp,
                                    Instant previous, Instant current) implements CognitiveEntry {}

    record DispositionReversal(String tenantId, String agentId, EventRef eventRef, Instant timestamp,
                               int streak) implements CognitiveEntry {}

    record DispositionTransitionAuditFailed(String tenantId, String agentId, EventRef eventRef,
                                            Instant timestamp, String intentId,
                                            int attempts) implements CognitiveEntry {}


}

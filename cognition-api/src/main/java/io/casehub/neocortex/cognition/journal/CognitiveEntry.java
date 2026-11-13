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
}

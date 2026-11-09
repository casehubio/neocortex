package io.casehub.neocortex.cognition.search;

import io.casehub.neocortex.cognition.appraisal.AppraisalResult;
import io.casehub.neocortex.cognition.appraisal.AppraisalTickParticipant;
import io.casehub.neocortex.cognition.appraisal.HabituationState;
import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.neocortex.cognition.drive.DriveIntensity;
import io.casehub.neocortex.cognition.drive.DriveOrchestrator;
import io.casehub.neocortex.cognition.drive.DriveProfile;
import io.casehub.neocortex.cognitive.CognitiveEmotion;
import io.casehub.neocortex.cognitive.EmotionSource;
import io.casehub.neocortex.cognitive.EmotionType;
import io.casehub.neocortex.cognitive.HabituationConfig;
import io.casehub.neocortex.cognitive.PadProjection;
import io.casehub.neocortex.cognitive.SearchBudgetConfig;
import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SearchBudgetPathologicalThresholdTest {

    private static final String AGENT = "agent1";
    private static final String TENANT = "tenant1";

    @Test
    void convergenceCollapsesWhenThresholdExceeded() {
        // anxiousness (high arousal) = 0.8, habituation failure = true (count 10 > tolerance 5, novelty 0.5 > threshold 0.3)
        // 0.8 + 1.0 = 1.8 > default pathologicalThreshold 1.4 → convergence must be 0.0
        var habituation = HabituationState.empty()
                .withObservation("hash1", 0.5)
                .withObservation("hash1", 0.5)
                .withObservation("hash1", 0.5)
                .withObservation("hash1", 0.5)
                .withObservation("hash1", 0.5)
                .withObservation("hash1", 0.5)
                .withObservation("hash1", 0.5)
                .withObservation("hash1", 0.5)
                .withObservation("hash1", 0.5)
                .withObservation("hash1", 0.5);
        // count=10 > repetitionTolerance=5, novelty=0.5 > noveltyThreshold=0.3

        var participant = withHabituation(habituation, 0.8);
        participant.tick(context());

        var budget = participant.currentBudget(AGENT, TENANT);
        assertTrue(budget.isPresent());
        assertEquals(0.0, budget.get().convergence(), 0.001,
                "Convergence should collapse to 0.0 when anxiousness + habituation failure > pathologicalThreshold");
    }

    @Test
    void convergenceUnchangedWhenBelowThreshold() {
        // anxiousness = 0.2, no habituation failure (count 2 < tolerance 5) → 0.2 + 0.0 = 0.2 < 1.4
        var habituation = HabituationState.empty()
                .withObservation("hash1", 0.1)
                .withObservation("hash1", 0.1);

        var participant = withHabituation(habituation, 0.2);
        participant.tick(context());

        var budget = participant.currentBudget(AGENT, TENANT);
        assertTrue(budget.isPresent());
        assertTrue(budget.get().convergence() > 0.0,
                "Convergence should not collapse when below pathological threshold");
    }

    @Test
    void nullHabituationSkipsCheck() {
        // No habituation state available → should not collapse even with high arousal
        var appraisal = mock(AppraisalTickParticipant.class);
        when(appraisal.currentResult(AGENT, TENANT)).thenReturn(
                Optional.of(AppraisalResult.empty()));
        when(appraisal.currentHabituation(AGENT, TENANT)).thenReturn(null);

        var participant = new SearchBudgetTickParticipant(
                mockDriveOrch(DriveAxis.CURIOSITY, 0.5),
                appraisal, null, null, registry(), null, null);
        participant.tick(context());

        var budget = participant.currentBudget(AGENT, TENANT);
        assertTrue(budget.isPresent());
        assertTrue(budget.get().convergence() > 0.0,
                "Convergence should not collapse when no habituation state available");
    }

    @Test
    void habituationFailureWithoutHighArousalDoesNotCollapse() {
        // Habituation failure present but low arousal: 0.1 + 1.0 = 1.1 < 1.4
        var habituation = HabituationState.empty()
                .withObservation("hash1", 0.5)
                .withObservation("hash1", 0.5)
                .withObservation("hash1", 0.5)
                .withObservation("hash1", 0.5)
                .withObservation("hash1", 0.5)
                .withObservation("hash1", 0.5);
        // count=6 > tolerance=5, novelty=0.5 > threshold=0.3 → failure=true, but arousal only 0.1

        var participant = withHabituation(habituation, 0.1);
        participant.tick(context());

        var budget = participant.currentBudget(AGENT, TENANT);
        assertTrue(budget.isPresent());
        assertTrue(budget.get().convergence() > 0.0,
                "Low arousal + habituation failure should stay below pathological threshold");
    }

    // --- helpers ---

    private HabituationState buildHabituation(String hash, int count, double novelty) {
        var state = HabituationState.empty();
        for (int i = 0; i < count; i++) {
            state = state.withObservation(hash, novelty);
        }
        return state;
    }

    private SearchBudgetTickParticipant withHabituation(HabituationState habituation,
                                                         double arousalScore) {
        var appraisal = mock(AppraisalTickParticipant.class);
        var emotion = new CognitiveEmotion(EmotionType.FEAR, Math.clamp(arousalScore, 0.0, 1.0),
                AGENT, Instant.now(), EmotionSource.INTRINSIC,
                new PadProjection(-0.5, arousalScore, -0.3));
        when(appraisal.currentResult(AGENT, TENANT)).thenReturn(
                Optional.of(new AppraisalResult(List.of(emotion), List.of(), habituation)));
        when(appraisal.currentHabituation(AGENT, TENANT)).thenReturn(habituation);

        return new SearchBudgetTickParticipant(
                mockDriveOrch(DriveAxis.CURIOSITY, 0.5),
                appraisal, null, null,
                registryWithHabConfig(), null, null);
    }

    private DriveOrchestrator mockDriveOrch(DriveAxis axis, double intensity) {
        var drives = Map.of(axis, new DriveIntensity(axis, intensity, "test"));
        var profile = new DriveProfile(AGENT, TENANT, drives, intensity, axis, Instant.now());
        var driveOrch = mock(DriveOrchestrator.class);
        when(driveOrch.currentDrives(AGENT, TENANT)).thenReturn(Optional.of(profile));
        return driveOrch;
    }

    private CognitiveDefaultsRegistry registry() {
        var registry = mock(CognitiveDefaultsRegistry.class);
        when(registry.forAgentOrDefaults(anyString())).thenReturn(
                CognitiveDefaults.empty(AGENT).withSearchBudgetConfig(SearchBudgetConfig.defaults()));
        return registry;
    }

    private CognitiveDefaultsRegistry registryWithHabConfig() {
        var registry = mock(CognitiveDefaultsRegistry.class);
        var defaults = CognitiveDefaults.empty(AGENT)
                .withSearchBudgetConfig(SearchBudgetConfig.defaults())
                .withHabituationConfig(HabituationConfig.defaults());
        when(registry.forAgentOrDefaults(anyString())).thenReturn(defaults);
        return registry;
    }

    private CognitionTickContext context() {
        var ctx = mock(CognitionTickContext.class);
        when(ctx.agentId()).thenReturn(AGENT);
        when(ctx.tenantId()).thenReturn(TENANT);
        when(ctx.observation()).thenReturn("test observation");
        return ctx;
    }
}

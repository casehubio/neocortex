package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognition.appraisal.AppraisalResult;
import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.neocortex.cognitive.CognitiveEmotion;
import io.casehub.neocortex.cognitive.EmotionSource;
import io.casehub.neocortex.cognitive.EmotionType;
import io.casehub.neocortex.cognitive.PadProjection;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.session.ManagedSession;
import io.casehub.platform.agent.session.SessionPipeline;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmotionRegulationGenerationPhaseTest {

    private SessionPipeline sessionPipeline;
    private ManagedSession managedSession;
    private CognitionTickContext context;

    @BeforeEach
    void setUp() {
        sessionPipeline = mock(SessionPipeline.class);
        managedSession = mock(ManagedSession.class);
        when(sessionPipeline.openSession(eq("emotion-regulation"), any(AgentSessionInit.class)))
                .thenReturn(managedSession);
        context = new CognitionTickContext("a1", "t1", null, (a, t) -> Set.of());
    }

    @Test
    void parsesValidResponseModulationResult() {
        var json = """
                {
                    "strategy": "RESPONSE_MODULATION",
                    "targetEmotions": ["anger"],
                    "targetDrives": ["gloating"],
                    "peakOriginalIntensity": 0.85,
                    "regulatedIntensity": 0.4,
                    "effectiveness": 0.6,
                    "narrative": "You hold back the triumph"
                }
                """;
        when(managedSession.query(any())).thenReturn(
                Multi.createFrom().item(new AgentEvent.TextDelta(json)));

        var emotion = new CognitiveEmotion(EmotionType.ANGER, 0.85, "subject1",
                Instant.now(), EmotionSource.INTRINSIC, PadProjection.NEUTRAL);
        var appraisal = new AppraisalResult(List.of(emotion), List.of(), null);

        var phase = new EmotionRegulationGenerationPhase(sessionPipeline);
        var result = phase.generate(context, appraisal, List.of());

        assertThat(result).isNotNull();
        assertThat(result.strategy()).isEqualTo(EmotionRegulationStrategy.RESPONSE_MODULATION);
        assertThat(result.targetEmotions()).containsExactly("anger");
        assertThat(result.targetDrives()).containsExactly("gloating");
        assertThat(result.peakOriginalIntensity()).isEqualTo(0.85);
        assertThat(result.regulatedIntensity()).isEqualTo(0.4);
        assertThat(result.effectiveness()).isEqualTo(0.6);
        assertThat(result.narrative()).isEqualTo("You hold back the triumph");
        assertThat(result.isEnvironmentalStrategy()).isFalse();
        assertThat(result.regulationAxis()).isNull();
        assertThat(result.environmentalDirective()).isNull();
    }

    @Test
    void parsesEnvironmentalStrategyWithAxisAndDirective() {
        var json = """
                {
                    "strategy": "SITUATION_SELECTION",
                    "targetEmotions": ["anxiety"],
                    "targetDrives": [],
                    "peakOriginalIntensity": 0.9,
                    "regulatedIntensity": 0.5,
                    "effectiveness": 0.7,
                    "narrative": "You decide to leave",
                    "regulationAxis": "AFFILIATION",
                    "environmentalDirective": "Avoid being alone with Muttley"
                }
                """;
        when(managedSession.query(any())).thenReturn(
                Multi.createFrom().item(new AgentEvent.TextDelta(json)));

        var emotion = new CognitiveEmotion(EmotionType.FEAR, 0.9, "subject1",
                Instant.now(), EmotionSource.INTRINSIC, PadProjection.NEUTRAL);
        var appraisal = new AppraisalResult(List.of(emotion), List.of(), null);

        var phase = new EmotionRegulationGenerationPhase(sessionPipeline);
        var result = phase.generate(context, appraisal, List.of());

        assertThat(result).isNotNull();
        assertThat(result.strategy()).isEqualTo(EmotionRegulationStrategy.SITUATION_SELECTION);
        assertThat(result.isEnvironmentalStrategy()).isTrue();
        assertThat(result.regulationAxis()).isEqualTo(DriveAxis.AFFILIATION);
        assertThat(result.environmentalDirective()).isEqualTo("Avoid being alone with Muttley");
    }

    @Test
    void returnsNullOnEmptyResponse() {
        when(managedSession.query(any())).thenReturn(Multi.createFrom().empty());

        var emotion = new CognitiveEmotion(EmotionType.ANGER, 0.85, "subject1",
                Instant.now(), EmotionSource.INTRINSIC, PadProjection.NEUTRAL);
        var appraisal = new AppraisalResult(List.of(emotion), List.of(), null);

        var phase = new EmotionRegulationGenerationPhase(sessionPipeline);
        var result = phase.generate(context, appraisal, List.of());

        assertThat(result).isNull();
    }

    @Test
    void returnsNullOnMalformedJson() {
        when(managedSession.query(any())).thenReturn(
                Multi.createFrom().item(new AgentEvent.TextDelta("not json at all")));

        var emotion = new CognitiveEmotion(EmotionType.ANGER, 0.85, "subject1",
                Instant.now(), EmotionSource.INTRINSIC, PadProjection.NEUTRAL);
        var appraisal = new AppraisalResult(List.of(emotion), List.of(), null);

        var phase = new EmotionRegulationGenerationPhase(sessionPipeline);
        var result = phase.generate(context, appraisal, List.of());

        assertThat(result).isNull();
    }

    @Test
    void returnsNullWhenNoPipeline() {
        var emotion = new CognitiveEmotion(EmotionType.ANGER, 0.85, "subject1",
                Instant.now(), EmotionSource.INTRINSIC, PadProjection.NEUTRAL);
        var appraisal = new AppraisalResult(List.of(emotion), List.of(), null);

        var phase = new EmotionRegulationGenerationPhase(null);
        var result = phase.generate(context, appraisal, List.of());

        assertThat(result).isNull();
    }

    @Test
    void systemPromptContainsGrossStrategies() {
        assertThat(EmotionRegulationGenerationPhase.SYSTEM_PROMPT)
                .contains("SITUATION_SELECTION")
                .contains("SITUATION_MODIFICATION")
                .contains("ATTENTIONAL_DEPLOYMENT")
                .contains("COGNITIVE_CHANGE")
                .contains("RESPONSE_MODULATION");
    }

    @Test
    void systemPromptContainsPetterSelectionFactors() {
        assertThat(EmotionRegulationGenerationPhase.SYSTEM_PROMPT)
                .contains("intensity")
                .contains("cognitive");
    }

    @Test
    void userMessageIncludesCognitiveLoad() {
        var emotion = new CognitiveEmotion(EmotionType.ANGER, 0.7, "rival",
                                           Instant.now(), EmotionSource.INTRINSIC, new PadProjection(-0.5, 0.6, 0.3));
        var appraisal = new AppraisalResult(List.of(emotion), List.of(), null);
        var load      = new CognitiveLoadEstimate(CognitiveLoadLevel.HIGH, 3, 3, 2);

        var message = EmotionRegulationGenerationPhase.buildUserMessage(
                context, appraisal, List.of(), 0.6, load, 0.0);

        assertThat(message).contains("Cognitive load: HIGH");
        assertThat(message).contains("drives=3");
        assertThat(message).contains("goals=3");
        assertThat(message).contains("ToM targets=2");
    }

    @Test
    void userMessageOmitsLoadWhenNull() {
        var emotion = new CognitiveEmotion(EmotionType.JOY, 0.5, "success",
                                           Instant.now(), EmotionSource.INTRINSIC, new PadProjection(0.5, 0.3, 0.4));
        var appraisal = new AppraisalResult(List.of(emotion), List.of(), null);

        var message = EmotionRegulationGenerationPhase.buildUserMessage(
                context, appraisal, List.of(), 0.3, null, 0.0);

        assertThat(message).doesNotContain("Cognitive load");
    }

    @Test
    void systemPromptIncludesCognitiveLoadGuidance() {
        assertThat(EmotionRegulationGenerationPhase.SYSTEM_PROMPT)
                .contains("Cognitive load constrains strategy");
    }

    @Test
    void userMessageIncludesFatigueWhenPresent() {
        var emotion = new CognitiveEmotion(EmotionType.ANGER, 0.7, "rival",
                                           Instant.now(), EmotionSource.INTRINSIC, new PadProjection(-0.5, 0.6, 0.3));
        var appraisal = new AppraisalResult(List.of(emotion), List.of(), null);

        var message = EmotionRegulationGenerationPhase.buildUserMessage(
                context, appraisal, List.of(), 0.6, null, 0.45);

        assertThat(message).contains("Regulation fatigue: 45%");
        assertThat(message).contains("depleting capacity");
        assertThat(message).doesNotContain("breakdown");
    }

    @Test
    void userMessageWarnsOfBreakdownAtHighFatigue() {
        var emotion = new CognitiveEmotion(EmotionType.ANGER, 0.7, "rival",
                                           Instant.now(), EmotionSource.INTRINSIC, new PadProjection(-0.5, 0.6, 0.3));
        var appraisal = new AppraisalResult(List.of(emotion), List.of(), null);

        var message = EmotionRegulationGenerationPhase.buildUserMessage(
                context, appraisal, List.of(), 0.6, null, 0.85);

        assertThat(message).contains("Regulation fatigue: 85%");
        assertThat(message).contains("breakdown");
    }

    @Test
    void userMessageOmitsFatigueWhenNegligible() {
        var emotion = new CognitiveEmotion(EmotionType.JOY, 0.5, "success",
                                           Instant.now(), EmotionSource.INTRINSIC, new PadProjection(0.5, 0.3, 0.4));
        var appraisal = new AppraisalResult(List.of(emotion), List.of(), null);

        var message = EmotionRegulationGenerationPhase.buildUserMessage(
                context, appraisal, List.of(), 0.3, null, 0.005);

        assertThat(message).doesNotContain("fatigue");
    }


}

package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.neocortex.cognition.goal.GoalProposalOrchestrator;
import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.cognitive.index.RegulationDefaults;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProactiveRegulationParticipantTest {

    private MindMapStore mindMapStore;
    private GoalProposalOrchestrator goalOrchestrator;
    private CognitiveDefaultsRegistry defaultsRegistry;
    private ProactiveRegulationParticipant participant;

    @BeforeEach
    void setUp() {
        mindMapStore = mock(MindMapStore.class);
        goalOrchestrator = mock(GoalProposalOrchestrator.class);
        defaultsRegistry = mock(CognitiveDefaultsRegistry.class);
        participant = new ProactiveRegulationParticipant(
                mindMapStore, goalOrchestrator, defaultsRegistry,
                "cog-subgraph", ProactiveAvoidanceMode.LOCATION_ONLY, 50, 10.0);
    }

    @Test
    void skipsWhenNoRegulationContext() {
        var ctx = new CognitionTickContext("agent1", "tenant1", null, null);
        participant.tick(ctx);
        verifyNoInteractions(goalOrchestrator);
    }

    @Test
    void skipsWhenNoPersonalityDefaults() {
        var regCtx = new RegulationContext("library", null, null);
        var ctx = new CognitionTickContext("agent1", "tenant1", null, null,
                null, null, null, regCtx);
        when(defaultsRegistry.forAgent("agent1")).thenReturn(Optional.empty());
        participant.tick(ctx);
        verifyNoInteractions(goalOrchestrator);
    }

    @Test
    void generatesAvoidanceGoalForMatchingLocation() {
        var regCtx = new RegulationContext("library", null, null);
        var ctx = new CognitionTickContext("agent1", "tenant1", null, null,
                null, null, null, regCtx);

        var defaults = CognitiveDefaults.empty("agent1")
                .withRegulationDefaults(new RegulationDefaults(0.5, 0.5, 0.8));
        when(defaultsRegistry.forAgent("agent1")).thenReturn(Optional.of(defaults));

        var node = mockRegulationNode("library", "ANGER",
                0.2, Instant.now().minusSeconds(30));
        when(mindMapStore.search(any())).thenReturn(List.of(node));

        participant.tick(ctx);

        verify(goalOrchestrator).registerParticipantGoals(eq("proactive-regulation"), eq("agent1"), eq("tenant1"), argThat(goals -> {
            assertThat(goals).hasSize(1);
            assertThat(goals.getFirst().goalName()).contains("proactive-avoidance");
            assertThat(goals.getFirst().goalName()).contains("library");
            return true;
        }));
    }

    @Test
    void noGoalWhenLocationDoesNotMatch() {
        var regCtx = new RegulationContext("kitchen", null, null);
        var ctx = new CognitionTickContext("agent1", "tenant1", null, null,
                null, null, null, regCtx);

        var defaults = CognitiveDefaults.empty("agent1")
                .withRegulationDefaults(new RegulationDefaults(0.5, 0.5, 0.8));
        when(defaultsRegistry.forAgent("agent1")).thenReturn(Optional.of(defaults));

        var node = mockRegulationNode("library", "ANGER",
                0.2, Instant.now().minusSeconds(30));
        when(mindMapStore.search(any())).thenReturn(List.of(node));

        participant.tick(ctx);
        verify(goalOrchestrator, never()).registerParticipantGoals(any(), any(), any(), any());
    }

    @Test
    void noGoalWhenEpisodeFullyDecayed() {
        var regCtx = new RegulationContext("library", null, null);
        var ctx = new CognitionTickContext("agent1", "tenant1", null, null,
                null, null, null, regCtx);

        var defaults = CognitiveDefaults.empty("agent1")
                .withRegulationDefaults(new RegulationDefaults(0.5, 0.5, 0.8));
        when(defaultsRegistry.forAgent("agent1")).thenReturn(Optional.of(defaults));

        // 1000 seconds ago / 10s = 100 ticks; exp(-100/50) = 0.135 → score = 0.8*0.135 = 0.108 < threshold 0.2
        var node = mockRegulationNode("library", "ANGER",
                0.2, Instant.now().minusSeconds(1000));
        when(mindMapStore.search(any())).thenReturn(List.of(node));

        participant.tick(ctx);
        verify(goalOrchestrator, never()).registerParticipantGoals(any(), any(), any(), any());
    }

    @Test
    void lowRedirectTendencyBlocksAvoidance() {
        var regCtx = new RegulationContext("library", null, null);
        var ctx = new CognitionTickContext("agent1", "tenant1", null, null,
                null, null, null, regCtx);

        var defaults = CognitiveDefaults.empty("agent1")
                .withRegulationDefaults(new RegulationDefaults(0.5, 0.5, 0.1));
        when(defaultsRegistry.forAgent("agent1")).thenReturn(Optional.of(defaults));

        // Moderate effectiveness (0.5) → avoidance score = (1-0.5)*decay ≈ 0.45
        // Threshold = 1.0 - 0.1 = 0.9 → score < threshold → no avoidance
        var node = mockRegulationNode("library", "ANGER",
                0.5, Instant.now().minusSeconds(30));
        when(mindMapStore.search(any())).thenReturn(List.of(node));

        participant.tick(ctx);
        verify(goalOrchestrator, never()).registerParticipantGoals(any(), any(), any(), any());
    }

    @Test
    void exponentialDecayAt30TicksStillProducesGoal() {
        var expParticipant = new ProactiveRegulationParticipant(
                mindMapStore, goalOrchestrator, defaultsRegistry,
                "cog-subgraph", ProactiveAvoidanceMode.LOCATION_ONLY, 30, 10.0);

        var regCtx = new RegulationContext("library", null, null);
        var ctx = new CognitionTickContext("agent1", "tenant1", null, null,
                                           null, null, null, regCtx);

        var defaults = CognitiveDefaults.empty("agent1")
                                        .withRegulationDefaults(new RegulationDefaults(0.5, 0.5, 0.8));
        when(defaultsRegistry.forAgent("agent1")).thenReturn(Optional.of(defaults));

        // 300 seconds ago / 10s per tick = 30 ticks = 1 time constant
        // Exponential: exp(-30/30) = 0.368 → avoidanceScore = 0.8*0.368 = 0.294 > threshold 0.2
        // Linear: 1.0 - 30/30 = 0.0 → no goal (fail!)
        var node = mockRegulationNode("library", "ANGER",
                                      0.2, Instant.now().minusSeconds(300));
        when(mindMapStore.search(any())).thenReturn(List.of(node));

        expParticipant.tick(ctx);
        verify(goalOrchestrator).registerParticipantGoals(eq("proactive-regulation"),
                                                          eq("agent1"), eq("tenant1"), argThat(goals -> {
                    assertThat(goals).hasSize(1);
                    assertThat(goals.getFirst().driveIntensity()).isGreaterThan(0.2);
                    assertThat(goals.getFirst().driveIntensity()).isLessThan(0.4);
                    return true;
                }));
    }


    private MindMapNode mockRegulationNode(String location, String emotionType,
                                            double effectiveness, Instant timestamp) {
        var node = mock(MindMapNode.class);
        var props = new HashMap<String, String>();
        props.put("cognitiveKind", "emotion-regulation-result");
        props.put("agent-id", "agent1");
        props.put("regulationLocation", location);
        props.put("dominantEmotionType", emotionType);
        props.put("effectiveness", String.valueOf(effectiveness));
        props.put("timestamp", timestamp.toString());
        props.put("regulationAxis", "AUTONOMY");
        when(node.properties()).thenReturn(props);
        return node;
    }
}

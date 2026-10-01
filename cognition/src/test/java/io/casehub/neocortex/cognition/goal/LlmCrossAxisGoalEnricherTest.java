package io.casehub.neocortex.cognition.goal;

import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.neocortex.cognition.narrative.DerivedTheme;
import io.casehub.neocortex.cognition.narrative.IndividualEpisode;
import io.casehub.neocortex.cognition.narrative.NarrativeScope;
import io.casehub.neocortex.cognition.narrative.NarrativeState;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmCrossAxisGoalEnricherTest {

    private AgentProvider agentProvider;
    private LlmCrossAxisGoalEnricher enricher;

    @BeforeEach
    void setUp() {
        agentProvider = mock(AgentProvider.class);
        enricher = new LlmCrossAxisGoalEnricher(agentProvider);
    }

    @Test
    void enriches_whenLlmReturnsText() {
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().items(
                        new AgentEvent.TextDelta("Explore and master new knowledge domains"),
                        new AgentEvent.InvocationComplete(10, 5, 0, 0, 0, 0.001, 200L, 150L, "test", 1, false)));

        var proposal = proposal(DriveAxis.CURIOSITY, 0.8);
        var theme = theme("knowledge-seeking", 0.8,
                Map.of(DriveAxis.CURIOSITY, 0.7, DriveAxis.COMPETENCE, 0.5));
        var narrative = narrative(theme);

        var result = enricher.enrich(proposal, narrative, theme);

        assertThat(result).isNotNull();
        assertThat(result.goalDescription()).isEqualTo("Explore and master new knowledge domains");
        assertThat(result.formationReason()).contains("LLM-enriched cross-axis");
        assertThat(result.formationReason()).contains("knowledge-seeking");
        assertThat(result.axis()).isEqualTo(DriveAxis.CURIOSITY);
        assertThat(result.goalName()).isEqualTo("test-goal");
        assertThat(result.driveIntensity()).isEqualTo(0.8);
    }

    @Test
    void returnsNull_whenLlmReturnsError() {
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().items(
                        new AgentEvent.InvocationComplete(0, 0, 0, 0, 0, null, 100L, 80L, "test", 1, true)));

        var proposal = proposal(DriveAxis.CURIOSITY, 0.8);
        var theme = theme("test", 0.8, Map.of(DriveAxis.CURIOSITY, 0.7));

        var result = enricher.enrich(proposal, narrative(theme), theme);

        assertThat(result).isNull();
    }

    @Test
    void returnsNull_whenLlmReturnsBlank() {
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().items(
                        new AgentEvent.TextDelta("   "),
                        new AgentEvent.InvocationComplete(10, 5, 0, 0, 0, 0.001, 200L, 150L, "test", 1, false)));

        var proposal = proposal(DriveAxis.CURIOSITY, 0.8);
        var theme = theme("test", 0.8, Map.of(DriveAxis.CURIOSITY, 0.7));

        var result = enricher.enrich(proposal, narrative(theme), theme);

        assertThat(result).isNull();
    }

    @Test
    void returnsNull_whenInvocationThrows() {
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().failure(new RuntimeException("timeout")));

        var proposal = proposal(DriveAxis.CURIOSITY, 0.8);
        var theme = theme("test", 0.8, Map.of(DriveAxis.CURIOSITY, 0.7));

        var result = enricher.enrich(proposal, narrative(theme), theme);

        assertThat(result).isNull();
    }

    @Test
    void promptIncludesEpisodes() {
        var configCaptor = ArgumentCaptor.forClass(AgentSessionConfig.class);
        when(agentProvider.invoke(configCaptor.capture()))
                .thenReturn(Multi.createFrom().items(
                        new AgentEvent.TextDelta("enriched goal"),
                        new AgentEvent.InvocationComplete(10, 5, 0, 0, 0, 0.001, 200L, 150L, "test", 1, false)));

        var episode = new IndividualEpisode("ep-1", Instant.now(), null,
                List.of(), "Had a breakthrough in research", 0.8, List.of());
        var theme = theme("research", 0.8, Map.of(DriveAxis.CURIOSITY, 0.7));
        var narrative = new NarrativeState("s1", "t1", NarrativeScope.INDIVIDUAL,
                List.of(episode, theme), Instant.now(), 0);

        enricher.enrich(proposal(DriveAxis.CURIOSITY, 0.8), narrative, theme);

        var config = configCaptor.getValue();
        assertThat(config.userPrompt()).contains("breakthrough in research");
    }

    private DriveGoalProposal proposal(DriveAxis axis, double intensity) {
        return new DriveGoalProposal(axis, "test-goal", "Test compound goal",
                "test reason", intensity);
    }

    private DerivedTheme theme(String label, double salience,
                                Map<DriveAxis, Double> weights) {
        return new DerivedTheme("t-" + label, Instant.now(), null,
                List.of(), label, salience, weights, List.of());
    }

    private NarrativeState narrative(DerivedTheme... themes) {
        return new NarrativeState("s1", "t1", NarrativeScope.INDIVIDUAL,
                List.of(themes), Instant.now(), 0);
    }
}

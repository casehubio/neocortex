package io.casehub.neocortex.cognition.goal;

import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.neocortex.cognition.drive.DriveProfile;
import io.casehub.neocortex.cognition.narrative.DerivedTheme;
import io.casehub.neocortex.cognition.narrative.NarrativeScope;
import io.casehub.neocortex.cognition.narrative.NarrativeState;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentGoal;
import io.casehub.eidos.api.GoalPriority;
import io.casehub.eidos.api.Visibility;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NarrativeGoalEscalationPolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

    private NarrativeGoalEscalationPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new NarrativeGoalEscalationPolicy(GoalEscalationConfig.defaults());
    }

    @Test
    void escalates_whenThemeAligns() {
        var proposal = proposal(DriveAxis.CURIOSITY, 0.8);
        var theme = theme("knowledge-seeking", 0.8, Map.of(DriveAxis.CURIOSITY, 0.7));
        var context = context(List.of(theme), List.of());

        var result = policy.evaluate(proposal, context);

        assertThat(result).isNotNull();
        assertThat(result.priority()).isEqualTo(GoalPriority.PRIMARY);
        assertThat(result.themeLabel()).isEqualTo("knowledge-seeking");
        assertThat(result.reason()).contains("knowledge-seeking");
    }

    @Test
    void returnsNull_whenNoThemeAboveSalienceThreshold() {
        var proposal = proposal(DriveAxis.CURIOSITY, 0.8);
        var theme = theme("low-salience", 0.3, Map.of(DriveAxis.CURIOSITY, 0.7));
        var context = context(List.of(theme), List.of());

        var result = policy.evaluate(proposal, context);

        assertThat(result).isNull();
    }

    @Test
    void returnsNull_whenAxisWeightBelowThreshold() {
        var proposal = proposal(DriveAxis.CURIOSITY, 0.8);
        var theme = theme("weak-axis", 0.8, Map.of(DriveAxis.CURIOSITY, 0.1));
        var context = context(List.of(theme), List.of());

        var result = policy.evaluate(proposal, context);

        assertThat(result).isNull();
    }

    @Test
    void returnsNull_whenAxisNotInTheme() {
        var proposal = proposal(DriveAxis.CURIOSITY, 0.8);
        var theme = theme("no-curiosity", 0.8, Map.of(DriveAxis.COMPETENCE, 0.7));
        var context = context(List.of(theme), List.of());

        var result = policy.evaluate(proposal, context);

        assertThat(result).isNull();
    }

    @Test
    void returnsNull_whenMaxPrimaryGoalsReached() {
        var proposal = proposal(DriveAxis.CURIOSITY, 0.8);
        var theme = theme("good-theme", 0.8, Map.of(DriveAxis.CURIOSITY, 0.7));
        var primaryGoal = new AgentGoal("existing-primary", "desc",
                GoalPriority.PRIMARY, Visibility.PUBLIC, List.of(),
                Map.of("source", "drive"));
        var context = context(List.of(theme), List.of(primaryGoal));

        var result = policy.evaluate(proposal, context);

        assertThat(result).isNull();
    }

    @Test
    void selectsBestScoringTheme() {
        var proposal = proposal(DriveAxis.CURIOSITY, 0.8);
        var weak = theme("weak", 0.7, Map.of(DriveAxis.CURIOSITY, 0.4));
        var strong = theme("strong", 0.9, Map.of(DriveAxis.CURIOSITY, 0.8));
        var context = context(List.of(weak, strong), List.of());

        var result = policy.evaluate(proposal, context);

        assertThat(result).isNotNull();
        assertThat(result.themeLabel()).isEqualTo("strong");
    }

    private DriveGoalProposal proposal(DriveAxis axis, double intensity) {
        return new DriveGoalProposal(axis, "test-goal", "Test goal", "test", intensity);
    }

    private DerivedTheme theme(String label, double salience,
                                Map<DriveAxis, Double> weights) {
        return new DerivedTheme("t-" + label, NOW, null,
                List.of(), label, salience, weights, List.of());
    }

    private GoalEscalationContext context(List<DerivedTheme> themes, List<AgentGoal> goals) {
        var narrative = new NarrativeState("s1", "t1", NarrativeScope.INDIVIDUAL,
                List.copyOf(themes), NOW, 0);
        var drives = new DriveProfile("a1", "t1", Map.of(), 0.0, DriveAxis.CURIOSITY, NOW);
        var descriptor = AgentDescriptor.builder()
                .agentId("a1").name("Agent").slot("default").tenancyId("t1")
                .goals(goals).build();
        return new GoalEscalationContext(narrative, drives, descriptor);
    }
}

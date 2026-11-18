package io.casehub.neocortex.cognition.prompt;

import io.casehub.neocortex.cognition.mentalmodel.AttributedState;
import io.casehub.neocortex.cognition.mentalmodel.BdiDimension;
import io.casehub.neocortex.cognition.mentalmodel.MentalModelConfig;
import io.casehub.neocortex.cognition.mentalmodel.MentalModelMemory;
import io.casehub.neocortex.cognition.mentalmodel.MentalModelOrchestrator;
import io.casehub.neocortex.cognition.mentalmodel.MentalModelSnapshot;
import io.casehub.neocortex.cognition.mentalmodel.TheoryOfMindPosterior;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class MentalModelPromptSectionTest {

    private static final String AGENT = "agent1";
    private static final String SUBJECT = "user1";
    private static final String TENANT = "t1";
    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");

    @Mock MentalModelMemory memory;
    MentalModelOrchestrator orchestrator;
    MentalModelPromptSection section;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        orchestrator = new MentalModelOrchestrator(memory, MentalModelConfig.defaults(), clock, null);
        section = new MentalModelPromptSection(orchestrator);
    }

    @Test
    void posteriorAboveThresholdIsRendered() {
        seedSnapshot();
        orchestrator.recordTomPosterior(AGENT, SUBJECT, TENANT, new TheoryOfMindPosterior(0.7, 0.2, 0.1));

        var result = section.render(new CognitionRenderContext(AGENT, TENANT, SUBJECT));

        assertThat(result).contains("Inferred emotional state (Bayesian):");
        assertThat(result).contains("positive: 70%");
        assertThat(result).contains("neutral: 20%");
        assertThat(result).contains("distressed: 10%");
        assertThat(result).contains("→ likely positive");
    }

    @Test
    void posteriorAtThresholdIsNotRendered() {
        seedSnapshot();
        orchestrator.recordTomPosterior(AGENT, SUBJECT, TENANT, new TheoryOfMindPosterior(0.6, 0.3, 0.1));

        var result = section.render(new CognitionRenderContext(AGENT, TENANT, SUBJECT));

        assertThat(result).doesNotContain("Inferred emotional state (Bayesian)");
    }

    @Test
    void posteriorBelowThresholdIsNotRendered() {
        seedSnapshot();
        orchestrator.recordTomPosterior(AGENT, SUBJECT, TENANT, new TheoryOfMindPosterior(0.5, 0.3, 0.2));

        var result = section.render(new CognitionRenderContext(AGENT, TENANT, SUBJECT));

        assertThat(result).doesNotContain("Inferred emotional state (Bayesian)");
    }

    @Test
    void nullPosteriorProducesNoPosteriorSection() {
        seedSnapshot();

        var result = section.render(new CognitionRenderContext(AGENT, TENANT, SUBJECT));

        assertThat(result).doesNotContain("Inferred emotional state (Bayesian)");
    }

    private void seedSnapshot() {
        var snapshot = new MentalModelSnapshot(AGENT, SUBJECT, TENANT,
                List.of(new AttributedState("trust", "They seem open", 0.8, 1, NOW, BdiDimension.BELIEF)),
                List.of(), List.of(), NOW, null, NOW);
        when(memory.lookup(AGENT, SUBJECT, TENANT)).thenReturn(Optional.of(snapshot));
        orchestrator.tick(AGENT, SUBJECT, TENANT);
    }
}

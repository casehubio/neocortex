package io.casehub.neocortex.cognition.goal;

import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.neocortex.cognition.drive.DriveIntensity;
import io.casehub.neocortex.cognition.drive.DriveProfile;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import io.casehub.neocortex.memory.mood.MoodState;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LlmDeductiveGoalFormationStrategyTest {

    private static final AgentEvent.InvocationComplete COMPLETE =
            new AgentEvent.InvocationComplete(0, 0, 0, 0, 0, null, 0L, 0L, null, 0, false);

    @Test
    void parsesMultiGoalJsonResponse() {
        var json = """
                [
                  {
                    "goalName": "seize-inheritance",
                    "description": "Eliminate benefactor to inherit wealth",
                    "reasoning": "Greed personality + belief about will + financial desperation",
                    "driveContributions": { "AUTONOMY": 0.9, "COMPETENCE": 0.4 }
                  },
                  {
                    "goalName": "cover-tracks",
                    "description": "Ensure no evidence links back",
                    "reasoning": "Self-preservation drive + past success covering up",
                    "driveContributions": { "AUTONOMY": 0.7 }
                  }
                ]""";
        var strategy = new LlmDeductiveGoalFormationStrategy(stubProvider(json));
        var ctx = contextWithDrives();

        var proposals = strategy.propose(ctx);

        assertThat(proposals).hasSize(2);
        assertThat(proposals.get(0).goalName()).isEqualTo("seize-inheritance");
        assertThat(proposals.get(0).driveContributions()).containsEntry(DriveAxis.AUTONOMY, 0.9);
        assertThat(proposals.get(1).goalName()).isEqualTo("cover-tracks");
    }

    @Test
    void includesDispositionInPrompt() {
        var capturedPrompt = new String[1];
        AgentProvider capturing = new AgentProvider() {
            @Override
            public Multi<AgentEvent> invoke(io.casehub.platform.agent.AgentSessionConfig config) {
                capturedPrompt[0] = config.userPrompt();
                return Multi.createFrom().items(
                        new AgentEvent.TextDelta("[]"), COMPLETE);
            }
            @Override
            public AgentSession openSession(AgentSessionInit init) {
                throw new UnsupportedOperationException();
            }
        };
        var strategy = new LlmDeductiveGoalFormationStrategy(capturing);
        var disposition = new DispositionAxes(
                "competitive", "flexible", "bold", "high", "competitive");
        var profile = new DriveProfile("a1", "t1",
                Map.of(DriveAxis.AUTONOMY, new DriveIntensity(DriveAxis.AUTONOMY, 0.9, "scheming")),
                0.9, DriveAxis.AUTONOMY, Instant.now());
        var ctx = new DeductiveFormationContext(
                "a1", "t1", profile, disposition,
                List.of("The will leaves everything to me"),
                List.of("I killed my partner and got away with it"),
                null, List.of(), 3);

        strategy.propose(ctx);

        assertThat(capturedPrompt[0]).contains("competitive");
        assertThat(capturedPrompt[0]).contains("bold");
        assertThat(capturedPrompt[0]).contains("will leaves everything");
        assertThat(capturedPrompt[0]).contains("killed my partner");
        assertThat(capturedPrompt[0]).contains("AUTONOMY");
    }

    @Test
    void includesMoodInPrompt() {
        var capturedPrompt = new String[1];
        AgentProvider capturing = new AgentProvider() {
            @Override
            public Multi<AgentEvent> invoke(io.casehub.platform.agent.AgentSessionConfig config) {
                capturedPrompt[0] = config.userPrompt();
                return Multi.createFrom().items(
                        new AgentEvent.TextDelta("[]"), COMPLETE);
            }
            @Override
            public AgentSession openSession(AgentSessionInit init) {
                throw new UnsupportedOperationException();
            }
        };
        var strategy = new LlmDeductiveGoalFormationStrategy(capturing);
        var mood = new MoodState("a1", "t1", Instant.now(), -0.5, 0.8, 0.3,
                "anxious", null, null, Map.of());
        var profile = new DriveProfile("a1", "t1", Map.of(), 0.0, DriveAxis.CURIOSITY, Instant.now());
        var ctx = new DeductiveFormationContext(
                "a1", "t1", profile, null, List.of(), List.of(), mood, List.of(), 3);

        strategy.propose(ctx);

        assertThat(capturedPrompt[0]).contains("pleasure=");
        assertThat(capturedPrompt[0]).contains("arousal=");
    }

    @Test
    void returnsEmptyOnLlmFailure() {
        var strategy = new LlmDeductiveGoalFormationStrategy(errorProvider());
        assertThat(strategy.propose(contextWithDrives())).isEmpty();
    }

    @Test
    void returnsEmptyOnMalformedJson() {
        var strategy = new LlmDeductiveGoalFormationStrategy(stubProvider("not json"));
        assertThat(strategy.propose(contextWithDrives())).isEmpty();
    }

    @Test
    void noOpReturnsEmpty() {
        var strategy = new NoOpDeductiveGoalFormationStrategy();
        assertThat(strategy.propose(contextWithDrives())).isEmpty();
    }

    @Test
    void handlesEmptyArrayResponse() {
        var strategy = new LlmDeductiveGoalFormationStrategy(stubProvider("[]"));
        assertThat(strategy.propose(contextWithDrives())).isEmpty();
    }

    @Test
    void skipsEntriesWithMissingGoalName() {
        var json = """
                [
                  { "description": "no name", "reasoning": "r", "driveContributions": {} },
                  { "goalName": "valid", "description": "d", "reasoning": "r", "driveContributions": { "CURIOSITY": 0.5 } }
                ]""";
        var strategy = new LlmDeductiveGoalFormationStrategy(stubProvider(json));
        var proposals = strategy.propose(contextWithDrives());
        assertThat(proposals).hasSize(1);
        assertThat(proposals.get(0).goalName()).isEqualTo("valid");
    }

    private static DeductiveFormationContext contextWithDrives() {
        var profile = new DriveProfile("a1", "t1",
                Map.of(DriveAxis.AUTONOMY, new DriveIntensity(DriveAxis.AUTONOMY, 0.9, "scheming")),
                0.9, DriveAxis.AUTONOMY, Instant.now());
        return new DeductiveFormationContext(
                "a1", "t1", profile, null, List.of(), List.of(), null, List.of(), 3);
    }

    private static AgentProvider stubProvider(String response) {
        return new AgentProvider() {
            @Override
            public Multi<AgentEvent> invoke(io.casehub.platform.agent.AgentSessionConfig config) {
                return Multi.createFrom().items(
                        new AgentEvent.TextDelta(response), COMPLETE);
            }
            @Override
            public AgentSession openSession(AgentSessionInit init) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static AgentProvider errorProvider() {
        return new AgentProvider() {
            @Override
            public Multi<AgentEvent> invoke(io.casehub.platform.agent.AgentSessionConfig config) {
                return Multi.createFrom().failure(new RuntimeException("LLM unavailable"));
            }
            @Override
            public AgentSession openSession(AgentSessionInit init) {
                throw new UnsupportedOperationException();
            }
        };
    }
}

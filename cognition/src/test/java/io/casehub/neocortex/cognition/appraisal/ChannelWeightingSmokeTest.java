package io.casehub.neocortex.cognition.appraisal;

import io.casehub.neocortex.caps.ClassificationResult;
import io.casehub.neocortex.caps.SituationActivation;
import io.casehub.neocortex.caps.SituationClassifier;
import io.casehub.neocortex.cognition.core.CognitionConfig;
import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.neocortex.cognition.drive.DriveIntensity;
import io.casehub.neocortex.cognition.drive.DriveOrchestrator;
import io.casehub.neocortex.cognition.drive.DriveProfile;
import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import io.casehub.neocortex.cognition.prompt.ChannelEmphasisPromptSection;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Scenario smoke tests: stub classifier → channel weights → prompt rendering.
 * No LLM, no CDI, milliseconds per test.
 */
class ChannelWeightingSmokeTest {

    private static final String AGENT = "agent-1";
    private static final String TENANT = "tenant-1";

    @Test
    void threatScenario_foregroundsSomaticAndActionTendency() {
        var result = runScenario(
                "A shadowy figure lunges with a knife",
                List.of(new SituationActivation("physical_threat", 0.8)));

        assertThat(result).isNotNull();
        assertThat(result).contains("Foreground your somatic");
        assertThat(result).contains("Foreground your action tendency");
        assertThat(result).contains("Background your attentional");
    }

    @Test
    void socialAcceptance_foregroundsAttentional() {
        var result = runScenario(
                "Everyone welcomes you warmly to the table",
                List.of(new SituationActivation("acceptance", 0.9)));

        assertThat(result).isNotNull();
        assertThat(result).contains("Foreground your attentional");
        assertThat(result).contains("Foreground your action tendency");
        assertThat(result).contains("Background your temporal");
    }

    @Test
    void resourceCompetition_foregroundsTemporalAndActionTendency() {
        var result = runScenario(
                "Two hands reach for the golden key at once",
                List.of(new SituationActivation("competition", 0.8)));

        assertThat(result).isNotNull();
        assertThat(result).contains("Foreground your temporal");
        assertThat(result).contains("Foreground your action tendency");
        assertThat(result).contains("Background your somatic");
    }

    @Test
    void autonomyPowerlessness_foregroundsActionTendency() {
        var result = runScenario(
                "The door is locked and nobody will listen",
                List.of(new SituationActivation("powerlessness", 0.7)));

        assertThat(result).isNotNull();
        assertThat(result).contains("Foreground your action tendency");
        assertThat(result).contains("Background your attentional");
    }

    @Test
    void mixedThreatAndSocial_blendsWeightsByConfidence() {
        var result = runScenario(
                "A fight breaks out at the dinner party",
                List.of(new SituationActivation("physical_threat", 0.6),
                        new SituationActivation("acceptance", 0.4)));

        assertThat(result).isNotNull();
        assertThat(result).contains("Foreground your action tendency");
    }

    @Test
    void noActivations_noChannelEmphasis() {
        var result = runScenario(
                "A quiet afternoon in the garden",
                List.of());

        assertThat(result).isNull();
    }

    @Test
    void noClassifier_noChannelEmphasis() {
        var participant = buildParticipant(null);
        tickParticipant(participant, "Something dramatic happens");

        var section = new ChannelEmphasisPromptSection(participant);
        var rendered = section.render(new CognitionRenderContext(AGENT, TENANT, null));
        assertThat(rendered).isNull();
    }

    private String runScenario(String observation, List<SituationActivation> activations) {
        SituationClassifier classifier = (desc, meta, ctx) -> new ClassificationResult(activations);
        var participant = buildParticipant(classifier);
        tickParticipant(participant, observation);

        var section = new ChannelEmphasisPromptSection(participant);
        return section.render(new CognitionRenderContext(AGENT, TENANT, null));
    }

    private AppraisalTickParticipant buildParticipant(SituationClassifier classifier) {
        var driveProfile = new DriveProfile(AGENT, TENANT,
                Map.of(DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "baseline")),
                0.5, DriveAxis.CURIOSITY, Instant.now());
        var drives = mock(DriveOrchestrator.class);
        when(drives.currentDrives(AGENT, TENANT)).thenReturn(Optional.of(driveProfile));
        var mood = mock(MoodOrchestrator.class);

        return new AppraisalTickParticipant(
                new NoOpSalienceStrategy(), new NoOpAppraisalStrategy(),
                drives, mood, null,
                CognitionConfig.none().with("appraisal", true),
                null, AppraisalThresholds.defaults(), classifier);
    }

    private void tickParticipant(AppraisalTickParticipant participant, String observation) {
        participant.tick(new CognitionTickContext(AGENT, TENANT, null, (a, t) -> Set.of(), observation));
    }
}

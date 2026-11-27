package io.casehub.neocortex.cognition.drive;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.neocortex.cognition.mood.MoodConfig;
import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DriveOrchestratorTest {

    private static final String AGENT = "agent-1";
    private static final String TENANT = "tenant-1";

    @Test
    void evictIdleRemovesStaleEntries() {
        var moodOrch = new MoodOrchestrator(MoodConfig.defaults());

        DriveSource curiosity = (a, t) -> new DriveIntensity(DriveAxis.CURIOSITY, 0.6, "gap");
        DriveSource competence = (a, t) -> new DriveIntensity(DriveAxis.COMPETENCE, 0.3, "task");
        DriveSource affiliation = (a, t) -> new DriveIntensity(DriveAxis.AFFILIATION, 0.2, "bond");
        DriveSource autonomy = (a, t) -> new DriveIntensity(DriveAxis.AUTONOMY, 0.4, "choice");

        var orchestrator = new DriveOrchestrator(
                curiosity, competence, affiliation, autonomy,
                moodOrch, new DriveComposer(), DriveConfig.defaults());

        var descriptor = mock(AgentDescriptor.class);
        when(descriptor.agentId()).thenReturn(AGENT);
        when(descriptor.disposition()).thenReturn(null);

        orchestrator.tick(AGENT, TENANT, descriptor);
        assertThat(orchestrator.currentDrives(AGENT, TENANT)).isPresent();

        orchestrator.evictIdle(Duration.ZERO);

        assertThat(orchestrator.currentDrives(AGENT, TENANT)).isEmpty();
    }

    @Test
    void twoAppraisalModulationsAccumulate() {
        var         moodOrch    = new MoodOrchestrator(MoodConfig.defaults());
        DriveSource curiosity   = (a, t) -> new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "gap");
        DriveSource competence  = (a, t) -> new DriveIntensity(DriveAxis.COMPETENCE, 0.5, "task");
        DriveSource affiliation = (a, t) -> new DriveIntensity(DriveAxis.AFFILIATION, 0.5, "bond");
        DriveSource autonomy    = (a, t) -> new DriveIntensity(DriveAxis.AUTONOMY, 0.5, "choice");

        var orchestrator = new DriveOrchestrator(
                curiosity, competence, affiliation, autonomy,
                moodOrch, new DriveComposer(), DriveConfig.defaults());

        orchestrator.recordAppraisalModulation(AGENT, TENANT,
                                               java.util.Map.of(DriveAxis.CURIOSITY, 0.1));
        orchestrator.recordAppraisalModulation(AGENT, TENANT,
                                               java.util.Map.of(DriveAxis.CURIOSITY, 0.3));

        var descriptor = mock(AgentDescriptor.class);
        when(descriptor.agentId()).thenReturn(AGENT);
        when(descriptor.disposition()).thenReturn(null);

        orchestrator.tick(AGENT, TENANT, descriptor);

        var drives = orchestrator.currentDrives(AGENT, TENANT);
        assertThat(drives).isPresent();
        var curiosityIntensity = drives.get().drives().get(DriveAxis.CURIOSITY).intensity();
        assertThat(curiosityIntensity)
                .as("two modulations should sum to 0.4, producing intensity > what 0.3 alone would give")
                .isGreaterThan(0.5 + Math.tanh(0.3));
    }

    @Test
    void consecutiveTicksDoubleSmoothOnDeactivation() {
        var         currentIntensity = new double[]{0.8};
        DriveSource curiosity        = (a, t) -> new DriveIntensity(DriveAxis.CURIOSITY, currentIntensity[0], "gap");
        DriveSource competence       = (a, t) -> new DriveIntensity(DriveAxis.COMPETENCE, 0.5, "task");
        DriveSource affiliation      = (a, t) -> new DriveIntensity(DriveAxis.AFFILIATION, 0.5, "bond");
        DriveSource autonomy         = (a, t) -> new DriveIntensity(DriveAxis.AUTONOMY, 0.5, "choice");

        var descriptor = mock(AgentDescriptor.class);
        when(descriptor.disposition()).thenReturn(null);

        // Orchestrator A: single tick with appraisal (reference — correct)
        var orchA = new DriveOrchestrator(curiosity, competence, affiliation, autonomy,
                                          new MoodOrchestrator(MoodConfig.defaults()), new DriveComposer(), DriveConfig.defaults());
        // Orchestrator B: double tick pattern (simulates CognitionCore bug)
        var orchB = new DriveOrchestrator(curiosity, competence, affiliation, autonomy,
                                          new MoodOrchestrator(MoodConfig.defaults()), new DriveComposer(), DriveConfig.defaults());

        // Establish baseline at curiosity = 0.8
        orchA.tick(AGENT, TENANT, descriptor);
        orchB.tick(AGENT, TENANT, descriptor);

        // Drop curiosity — triggers EMA smoothing on deactivation
        currentIntensity[0] = 0.2;

        // A: record appraisal then single tick
        orchA.recordAppraisalModulation(AGENT, TENANT,
                                        java.util.Map.of(DriveAxis.CURIOSITY, 0.1));
        orchA.tick(AGENT, TENANT, descriptor);
        var singleTickResult = orchA.currentDrives(AGENT, TENANT).get()
                                    .drives().get(DriveAxis.CURIOSITY).intensity();

        // B: tick, then record appraisal, then tick again (double-smooth bug)
        orchB.tick(AGENT, TENANT, descriptor);
        orchB.recordAppraisalModulation(AGENT, TENANT,
                                        java.util.Map.of(DriveAxis.CURIOSITY, 0.1));
        orchB.tick(AGENT, TENANT, descriptor);
        var doubleTickResult = orchB.currentDrives(AGENT, TENANT).get()
                                    .drives().get(DriveAxis.CURIOSITY).intensity();

        // The double-tick pattern over-dampens: second tick smooths against
        // the already-smoothed first tick result instead of the original baseline
        assertThat(doubleTickResult)
                .as("double tick applies EMA smoothing twice, producing lower intensity than single tick")
                .isLessThan(singleTickResult);
    }

    @Test
    void recomposeWithModulationsEquivalentToSingleTickWithAppraisal() {
        var         currentIntensity = new double[]{0.8};
        DriveSource curiosity        = (a, t) -> new DriveIntensity(DriveAxis.CURIOSITY, currentIntensity[0], "gap");
        DriveSource competence       = (a, t) -> new DriveIntensity(DriveAxis.COMPETENCE, 0.5, "task");
        DriveSource affiliation      = (a, t) -> new DriveIntensity(DriveAxis.AFFILIATION, 0.5, "bond");
        DriveSource autonomy         = (a, t) -> new DriveIntensity(DriveAxis.AUTONOMY, 0.5, "choice");

        var descriptor = mock(AgentDescriptor.class);
        when(descriptor.disposition()).thenReturn(null);

        // Orchestrator A: single tick with appraisal (reference — correct)
        var orchA = new DriveOrchestrator(curiosity, competence, affiliation, autonomy,
                                          new MoodOrchestrator(MoodConfig.defaults()), new DriveComposer(), DriveConfig.defaults());
        // Orchestrator B: tick + recomposeWithModulations (the fix)
        var orchB = new DriveOrchestrator(curiosity, competence, affiliation, autonomy,
                                          new MoodOrchestrator(MoodConfig.defaults()), new DriveComposer(), DriveConfig.defaults());

        // Establish baseline at curiosity = 0.8
        orchA.tick(AGENT, TENANT, descriptor);
        orchB.tick(AGENT, TENANT, descriptor);

        // Drop curiosity — triggers EMA smoothing on deactivation
        currentIntensity[0] = 0.2;

        // A: record appraisal then single tick (smooths once against 0.8 baseline)
        orchA.recordAppraisalModulation(AGENT, TENANT,
                                        java.util.Map.of(DriveAxis.CURIOSITY, 0.1));
        orchA.tick(AGENT, TENANT, descriptor);
        var referenceResult = orchA.currentDrives(AGENT, TENANT).get()
                                   .drives().get(DriveAxis.CURIOSITY).intensity();

        // B: tick first (no appraisal), then record appraisal, then recompose
        orchB.tick(AGENT, TENANT, descriptor);
        orchB.recordAppraisalModulation(AGENT, TENANT,
                                        java.util.Map.of(DriveAxis.CURIOSITY, 0.1));
        orchB.recomposeWithModulations(AGENT, TENANT, descriptor);
        var fixedResult = orchB.currentDrives(AGENT, TENANT).get()
                               .drives().get(DriveAxis.CURIOSITY).intensity();

        // Both should produce the same result — smoothed once against the same baseline
        assertThat(fixedResult)
                .as("recomposeWithModulations should produce same result as single tick with all modulations")
                .isCloseTo(referenceResult, org.assertj.core.data.Offset.offset(0.001));
    }


}

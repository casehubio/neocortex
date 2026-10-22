package io.quarkmind.inference.bayes;

import io.quarkmind.inference.bayes.benchmark.BayesNetworkFixtures;
import org.junit.jupiter.api.Test;

import static io.quarkmind.inference.bayes.IncrementalUpdateTest.assertSameMarginals;
import static org.assertj.core.api.Assertions.assertThat;

class HardEvidenceRetractionTest {

    @Test
    void hardEvidenceRetraction_staysOnPhase3() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(6);
        BayesInstance<?> instance = new BayesInstance<>(jt, true);

        // Initial calibration with soft evidence
        instance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2});
        instance.globalUpdate();

        // Switch to hard evidence — should use Phase 3 retraction
        instance.setHardEvidence(BayesNetworkFixtures.varName(0), "true");
        instance.globalUpdate();

        assertThat(instance.getStats().retractionCount())
                .as("Should use Phase 3 retraction for soft→hard transition")
                .isGreaterThan(0);
    }

    @Test
    void hardToSoftRetraction_correctPosteriors() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(6);
        BayesInstance<?> instance = new BayesInstance<>(jt, true);
        BayesInstance<?> baseline = new BayesInstance<>(jt);

        // Set hard evidence and calibrate
        instance.setHardEvidence(BayesNetworkFixtures.varName(0), "false");
        instance.globalUpdate();

        // Retract to soft evidence
        instance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.6, 0.4});
        instance.globalUpdate();

        baseline.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.6, 0.4});
        baseline.globalUpdateForced();

        assertSameMarginals(instance, baseline, 6);
    }

    @Test
    void hardToHardRetraction_differentState_correctPosteriors() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(6);
        BayesInstance<?> instance = new BayesInstance<>(jt, true);
        BayesInstance<?> baseline = new BayesInstance<>(jt);

        // Set hard evidence var_0=false
        instance.setHardEvidence(BayesNetworkFixtures.varName(0), "false");
        instance.globalUpdate();

        // Change to var_0=true
        instance.setHardEvidence(BayesNetworkFixtures.varName(0), "true");
        instance.globalUpdate();

        baseline.setHardEvidence(BayesNetworkFixtures.varName(0), "true");
        baseline.globalUpdateForced();

        assertSameMarginals(instance, baseline, 6);
    }

    @Test
    void mixedHardSoftRetraction_correctPosteriors() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> instance = new BayesInstance<>(jt, true);
        BayesInstance<?> baseline = new BayesInstance<>(jt);

        // Hard on var_0, soft on var_3
        instance.setHardEvidence(BayesNetworkFixtures.varName(0), "false");
        instance.setLikelyhood(BayesNetworkFixtures.varName(3), new double[]{0.3, 0.7});
        instance.globalUpdate();

        // Change var_0 to soft, var_3 to hard
        instance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.5, 0.5});
        instance.setHardEvidence(BayesNetworkFixtures.varName(3), "true");
        instance.globalUpdate();

        baseline.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.5, 0.5});
        baseline.setHardEvidence(BayesNetworkFixtures.varName(3), "true");
        baseline.globalUpdateForced();

        assertSameMarginals(instance, baseline, 8);
    }

    @Test
    void hardEvidenceRetraction_multipleVariables_correctPosteriors() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> instance = new BayesInstance<>(jt, true);
        BayesInstance<?> baseline = new BayesInstance<>(jt);

        // Multiple hard evidence
        instance.setHardEvidence(BayesNetworkFixtures.varName(0), "false");
        instance.setHardEvidence(BayesNetworkFixtures.varName(4), "true");
        instance.globalUpdate();

        // Change both
        instance.setHardEvidence(BayesNetworkFixtures.varName(0), "true");
        instance.setLikelyhood(BayesNetworkFixtures.varName(4), new double[]{0.4, 0.6});
        instance.globalUpdate();

        baseline.setHardEvidence(BayesNetworkFixtures.varName(0), "true");
        baseline.setLikelyhood(BayesNetworkFixtures.varName(4), new double[]{0.4, 0.6});
        baseline.globalUpdateForced();

        assertSameMarginals(instance, baseline, 8);
    }

    @Test
    void hardEvidenceRetraction_noRegression_softEvidenceStillWorks() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(6);
        BayesInstance<?> instance = new BayesInstance<>(jt, true);
        BayesInstance<?> baseline = new BayesInstance<>(jt);

        // Soft evidence
        instance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.7, 0.3});
        instance.globalUpdate();

        // Change soft evidence
        instance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.4, 0.6});
        instance.globalUpdate();

        baseline.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.4, 0.6});
        baseline.globalUpdateForced();

        assertSameMarginals(instance, baseline, 6);

        assertThat(instance.getStats().retractionCount())
                .as("Soft-only retraction should also use Phase 3")
                .isGreaterThan(0);
    }

    @Test
    void hardEvidenceRetraction_starTopology_correctPosteriors() {
        JunctionTree jt = BayesNetworkFixtures.buildStar(5);
        BayesInstance<?> instance = new BayesInstance<>(jt, true);
        BayesInstance<?> baseline = new BayesInstance<>(jt);

        // Hard evidence on hub
        instance.setHardEvidence(BayesNetworkFixtures.varName(0), "true");
        instance.globalUpdate();

        // Change hub to other state
        instance.setHardEvidence(BayesNetworkFixtures.varName(0), "false");
        instance.globalUpdate();

        baseline.setHardEvidence(BayesNetworkFixtures.varName(0), "false");
        baseline.globalUpdateForced();

        assertSameMarginals(instance, baseline, 6);
    }

    @Test
    void phase3RetractionCount_increases_forHardEvidence() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(4);
        BayesInstance<?> instance = new BayesInstance<>(jt, true);

        instance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2});
        instance.globalUpdate();
        int countBefore = instance.getStats().retractionCount();

        // Soft → hard should retract
        instance.setHardEvidence(BayesNetworkFixtures.varName(0), "true");
        instance.globalUpdate();

        assertThat(instance.getStats().retractionCount())
                .isGreaterThan(countBefore);
    }
}

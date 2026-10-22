package io.quarkmind.inference.bayes;

import io.quarkmind.inference.bayes.benchmark.BayesNetworkFixtures;
import org.junit.jupiter.api.Test;

import static io.quarkmind.inference.bayes.IncrementalUpdateTest.assertSameMarginals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Refs #654 — tests for BJT audit findings.
 */
public class AuditFixTest {

    // ── Fix #1: branching junction tree (createJunctionTreeGraph separator IDs) ──

    @Test
    void polyTree_buildAndPropagate_producesValidMarginals() {
        JunctionTree jt = BayesNetworkFixtures.buildPolyTree();
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood("A", new double[]{0.8, 0.2});
        instance.globalUpdate();

        for (String var : new String[]{"A", "B", "C", "D", "E", "F", "G"}) {
            double[] dist = instance.marginalize(var).getDistribution();
            double sum = 0;
            for (double p : dist) {
                assertThat(p).as("%s not NaN", var).isNotNaN();
                sum += p;
            }
            assertThat(sum).as("%s sums to 1", var).isCloseTo(1.0, within(1e-10));
        }
    }

    @Test
    void polyTree_incrementalUpdate_matchesForcedBaseline() {
        JunctionTree jt = BayesNetworkFixtures.buildPolyTree();
        BayesInstance<?> forced = new BayesInstance<>(jt);
        BayesInstance<?> inc = new BayesInstance<>(jt);

        forced.setLikelyhood("A", new double[]{0.8, 0.2});
        inc.setLikelyhood("A", new double[]{0.8, 0.2});
        forced.globalUpdateForced();
        inc.globalUpdate();
        assertPolyTreeMarginals(forced, inc);

        forced.setLikelyhood("D", new double[]{0.9, 0.1});
        inc.setLikelyhood("D", new double[]{0.9, 0.1});
        forced.globalUpdateForced();
        inc.globalUpdate();
        assertPolyTreeMarginals(forced, inc);
    }

    @Test
    void polyTree_retractionUpdate_matchesForcedBaseline() {
        JunctionTree jt = BayesNetworkFixtures.buildPolyTree();
        BayesInstance<?> forced = new BayesInstance<>(jt);
        BayesInstance<?> retract = new BayesInstance<>(jt, true);

        forced.setLikelyhood("A", new double[]{0.8, 0.2});
        retract.setLikelyhood("A", new double[]{0.8, 0.2});
        forced.globalUpdateForced();
        retract.globalUpdate();

        forced.setLikelyhood("A", new double[]{0.3, 0.7});
        retract.setLikelyhood("A", new double[]{0.3, 0.7});
        forced.globalUpdateForced();
        retract.globalUpdate();
        assertPolyTreeMarginals(forced, retract);
    }

    @Test
    void polyTree_multipleEvidenceAcrossBranches_matchesForcedBaseline() {
        JunctionTree jt = BayesNetworkFixtures.buildPolyTree();
        BayesInstance<?> forced = new BayesInstance<>(jt);
        BayesInstance<?> inc = new BayesInstance<>(jt);

        forced.setLikelyhood("D", new double[]{0.9, 0.1});
        forced.setLikelyhood("G", new double[]{0.2, 0.8});
        inc.setLikelyhood("D", new double[]{0.9, 0.1});
        inc.setLikelyhood("G", new double[]{0.2, 0.8});
        forced.globalUpdateForced();
        inc.globalUpdate();
        assertPolyTreeMarginals(forced, inc);

        forced.setLikelyhood("E", new double[]{0.4, 0.6});
        inc.setLikelyhood("E", new double[]{0.4, 0.6});
        forced.globalUpdateForced();
        inc.globalUpdate();
        assertPolyTreeMarginals(forced, inc);
    }

    @Test
    void polyTree_separatorIdsAreUnique() {
        JunctionTree jt = BayesNetworkFixtures.buildPolyTree();
        JunctionTreeSeparator[] seps = jt.getJunctionTreeSeparators();
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        for (JunctionTreeSeparator sep : seps) {
            assertThat(ids.add(sep.getId()))
                    .as("Duplicate separator ID: %d", sep.getId())
                    .isTrue();
        }
    }

    // ── Fix #2: removeLeafVariable with pending dirty evidence ──

    @Test
    void removeLeafVariable_withPendingDirtyEvidence_doesNotNpe() {
        JunctionTree jt = BayesNetworkFixtures.buildStar(5);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood("var_5", new double[]{0.8, 0.2});
        // Remove before globalUpdate — dirty bit set, varState nulled
        instance.removeLeafVariable("var_5");

        instance.setLikelyhood("var_0", new double[]{0.3, 0.7});
        instance.globalUpdate();

        double[] dist = instance.marginalize("var_0").getDistribution();
        double sum = 0;
        for (double p : dist) sum += p;
        assertThat(sum).isCloseTo(1.0, within(1e-10));
    }

    @Test
    void removeLeafVariable_afterCalibration_withPendingEvidence_doesNotNpe() {
        JunctionTree jt = BayesNetworkFixtures.buildStar(5);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood("var_0", new double[]{0.5, 0.5});
        instance.globalUpdate();

        instance.setLikelyhood("var_5", new double[]{0.9, 0.1});
        // Remove after calibration but before update propagates the new evidence
        instance.removeLeafVariable("var_5");

        instance.setLikelyhood("var_0", new double[]{0.3, 0.7});
        instance.globalUpdate();

        double[] dist = instance.marginalize("var_0").getDistribution();
        for (double p : dist) {
            assertThat(p).isNotNaN();
        }
    }

    // ── Fix #3: distribution length validation ──

    @Test
    void setLikelyhood_wrongDistributionLength_throwsIllegalArgument() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(4);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        assertThatThrownBy(() -> instance.setLikelyhood("var_0", new double[]{0.5, 0.3, 0.2}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3")
                .hasMessageContaining("2");
    }

    @Test
    void setLikelyhood_wrongDistributionLength_tooShort_throwsIllegalArgument() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(4);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        assertThatThrownBy(() -> instance.setLikelyhood("var_0", new double[]{1.0}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setLikelyhood_correctDistributionLength_succeeds() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(4);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood("var_0", new double[]{0.8, 0.2});
        instance.globalUpdate();
        double[] dist = instance.marginalize("var_0").getDistribution();
        assertThat(dist).hasSize(2);
    }

    @Test
    void setLikelyhood_ternaryVariable_wrongLength_throwsIllegalArgument() {
        JunctionTree jt = BayesNetworkFixtures.buildTernaryChain(4);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        assertThatThrownBy(() -> instance.setLikelyhood("var_0", new double[]{0.5, 0.5}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2")
                .hasMessageContaining("3");
    }

    // ── Gap #6: multi-state variables in incremental tests ──

    @Test
    void ternaryChain_phase2_incrementalMatchesForcedBaseline() {
        int n = 8;
        JunctionTree jt = BayesNetworkFixtures.buildTernaryChain(n);
        BayesInstance<?> forced = new BayesInstance<>(jt);
        BayesInstance<?> inc = new BayesInstance<>(jt);

        forced.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.7, 0.2, 0.1});
        inc.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.7, 0.2, 0.1});
        forced.globalUpdateForced();
        inc.globalUpdate();
        assertSameMarginals(forced, inc, n);

        forced.setLikelyhood(BayesNetworkFixtures.varName(4), new double[]{0.1, 0.3, 0.6});
        inc.setLikelyhood(BayesNetworkFixtures.varName(4), new double[]{0.1, 0.3, 0.6});
        forced.globalUpdateForced();
        inc.globalUpdate();
        assertSameMarginals(forced, inc, n);
    }

    @Test
    void ternaryChain_phase3_retractionMatchesForcedBaseline() {
        int n = 8;
        JunctionTree jt = BayesNetworkFixtures.buildTernaryChain(n);
        BayesInstance<?> forced = new BayesInstance<>(jt);
        BayesInstance<?> retract = new BayesInstance<>(jt, true);

        forced.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.7, 0.2, 0.1});
        retract.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.7, 0.2, 0.1});
        forced.globalUpdateForced();
        retract.globalUpdate();
        assertSameMarginals(forced, retract, n);

        forced.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.1, 0.5, 0.4});
        retract.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.1, 0.5, 0.4});
        forced.globalUpdateForced();
        retract.globalUpdate();
        assertSameMarginals(forced, retract, n);
    }

    @Test
    void ternaryChain_sequentialChanges_alwaysMatchForced() {
        int n = 8;
        JunctionTree jt = BayesNetworkFixtures.buildTernaryChain(n);
        BayesInstance<?> forced = new BayesInstance<>(jt);
        BayesInstance<?> inc = new BayesInstance<>(jt, true);

        double[][] evidenceSeq = {
                {0.7, 0.2, 0.1},
                {0.1, 0.5, 0.4},
                {0.3, 0.3, 0.4},
                {0.8, 0.1, 0.1},
        };

        for (double[] ev : evidenceSeq) {
            forced.setLikelyhood(BayesNetworkFixtures.varName(0), ev);
            inc.setLikelyhood(BayesNetworkFixtures.varName(0), ev);
            forced.globalUpdateForced();
            inc.globalUpdate();
            assertSameMarginals(forced, inc, n);
        }
    }

    @Test
    void ternaryChain_numericalStability_after500Cycles() {
        int n = 8;
        JunctionTree jt = BayesNetworkFixtures.buildTernaryChain(n);
        BayesInstance<?> forced = new BayesInstance<>(jt);
        BayesInstance<?> retract = new BayesInstance<>(jt, true);

        double[][] evidenceSeq = {
                {0.7, 0.2, 0.1}, {0.1, 0.5, 0.4}, {0.3, 0.3, 0.4},
                {0.8, 0.1, 0.1}, {0.2, 0.6, 0.2},
        };

        for (int cycle = 0; cycle < 500; cycle++) {
            double[] ev = evidenceSeq[cycle % evidenceSeq.length];
            forced.setLikelyhood(BayesNetworkFixtures.varName(0), ev);
            retract.setLikelyhood(BayesNetworkFixtures.varName(0), ev);
            forced.globalUpdateForced();
            retract.globalUpdate();
        }
        assertSameMarginals(forced, retract, n);
    }

    // ── Gap #9: mapInference on star topology ──

    @Test
    void mapInference_starTopology_noEvidence_returnsConsistentAssignment() {
        JunctionTree jt = BayesNetworkFixtures.buildStar(5);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        MapAssignment map = instance.mapInference();

        assertThat(map.assignment()).hasSize(6);
        for (int i = 0; i <= 5; i++) {
            assertThat(map.assignment()).containsKey(BayesNetworkFixtures.varName(i));
        }
        // Hub prior [0.5, 0.5], leaf CPT [[0.7, 0.3], [0.4, 0.6]]
        // MAP with all false hub: 0.5 * 0.7^5 = 0.084
        // MAP with all true hub:  0.5 * 0.6^5 = 0.039
        // So hub=false is MAP
        assertThat(map.assignment()).containsEntry("var_0", "false");
    }

    @Test
    void mapInference_starTopology_withEvidence_shiftsAssignment() {
        JunctionTree jt = BayesNetworkFixtures.buildStar(5);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        // Strong evidence on all leaves toward "true"
        for (int i = 1; i <= 5; i++) {
            instance.setLikelyhood(BayesNetworkFixtures.varName(i), new double[]{0.1, 0.9});
        }

        MapAssignment map = instance.mapInference();
        assertThat(map.assignment()).containsEntry("var_0", "true");
    }

    @Test
    void mapInference_starTopology_doesNotCorruptMarginals() {
        JunctionTree jt = BayesNetworkFixtures.buildStar(5);
        BayesInstance<?> instance = new BayesInstance<>(jt);
        BayesInstance<?> reference = new BayesInstance<>(jt);

        instance.setLikelyhood("var_0", new double[]{0.3, 0.7});
        reference.setLikelyhood("var_0", new double[]{0.3, 0.7});

        instance.globalUpdate();
        reference.globalUpdate();

        instance.mapInference();

        for (int i = 0; i <= 5; i++) {
            String name = BayesNetworkFixtures.varName(i);
            double[] instDist = instance.marginalize(name).getDistribution();
            double[] refDist = reference.marginalize(name).getDistribution();
            for (int j = 0; j < instDist.length; j++) {
                assertThat(instDist[j]).as("%s[%d]", name, j)
                        .isCloseTo(refDist[j], within(1e-10));
            }
        }
    }

    @Test
    void mapInference_ternaryChain_returnsValidAssignment() {
        JunctionTree jt = BayesNetworkFixtures.buildTernaryChain(4);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        MapAssignment map = instance.mapInference();
        assertThat(map.assignment()).hasSize(4);

        // Prior [0.5, 0.3, 0.2], CPT rows: [0.6,0.3,0.1], [0.2,0.5,0.3], [0.1,0.2,0.7]
        // MAP with all "low": 0.5 * 0.6^3 = 0.108 — dominant
        assertThat(map.assignment()).containsEntry("var_0", "low");
    }

    // ── Helper ──

    private static void assertPolyTreeMarginals(BayesInstance<?> expected, BayesInstance<?> actual) {
        for (String var : new String[]{"A", "B", "C", "D", "E", "F", "G"}) {
            double[] exp = expected.marginalize(var).getDistribution();
            double[] act = actual.marginalize(var).getDistribution();
            for (int j = 0; j < exp.length; j++) {
                assertThat(act[j]).as("%s[%d]", var, j).isCloseTo(exp[j], within(1e-10));
            }
        }
    }
}

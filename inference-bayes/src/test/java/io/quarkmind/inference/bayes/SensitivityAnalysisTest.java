package io.quarkmind.inference.bayes;

import io.quarkmind.inference.bayes.benchmark.BayesNetworkFixtures;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Refs #629 — sensitivity analysis (value of information per variable).
 */
public class SensitivityAnalysisTest {

    @Test
    void unobservedVariable_hasSensitivityZero() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2});
        instance.globalUpdate();

        double sens = instance.sensitivity(BayesNetworkFixtures.varName(4), BayesNetworkFixtures.varName(7));
        assertThat(sens).isEqualTo(0.0);
    }

    @Test
    void soleObservedVariable_hasPositiveSensitivity() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.9, 0.1});
        instance.globalUpdate();

        double sens = instance.sensitivity(BayesNetworkFixtures.varName(0), BayesNetworkFixtures.varName(7));
        assertThat(sens).isGreaterThan(0.0);
    }

    @Test
    void sensitivityDoesNotMutateInstanceState() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2});
        instance.setLikelyhood(BayesNetworkFixtures.varName(4), new double[]{0.6, 0.4});
        instance.globalUpdate();

        double[] beforeDist = instance.marginalize(BayesNetworkFixtures.varName(7)).getDistribution().clone();

        instance.sensitivity(BayesNetworkFixtures.varName(0), BayesNetworkFixtures.varName(7));
        instance.sensitivity(BayesNetworkFixtures.varName(4), BayesNetworkFixtures.varName(7));

        double[] afterDist = instance.marginalize(BayesNetworkFixtures.varName(7)).getDistribution();
        for (int i = 0; i < beforeDist.length; i++) {
            assertThat(afterDist[i]).isCloseTo(beforeDist[i], within(1e-10));
        }
    }

    @Test
    void closerEvidenceHasHigherSensitivity() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(16);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.9, 0.1});
        instance.setLikelyhood(BayesNetworkFixtures.varName(14), new double[]{0.9, 0.1});
        instance.globalUpdate();

        String target = BayesNetworkFixtures.varName(15);
        double sensNear = instance.sensitivity(BayesNetworkFixtures.varName(14), target);
        double sensFar = instance.sensitivity(BayesNetworkFixtures.varName(0), target);

        assertThat(sensNear).isGreaterThan(sensFar);
    }

    @Test
    void rankingsAreStableAcrossUpdateCycles() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.9, 0.1});
        instance.setLikelyhood(BayesNetworkFixtures.varName(4), new double[]{0.6, 0.4});
        instance.globalUpdate();

        String target = BayesNetworkFixtures.varName(7);
        double sens0First = instance.sensitivity(BayesNetworkFixtures.varName(0), target);
        double sens4First = instance.sensitivity(BayesNetworkFixtures.varName(4), target);

        // Update again with same evidence — sensitivities should be identical
        instance.globalUpdate();
        double sens0Second = instance.sensitivity(BayesNetworkFixtures.varName(0), target);
        double sens4Second = instance.sensitivity(BayesNetworkFixtures.varName(4), target);

        assertThat(sens0Second).isCloseTo(sens0First, within(1e-10));
        assertThat(sens4Second).isCloseTo(sens4First, within(1e-10));
    }

    @Test
    void klDivergence_identicalDistributions_isZero() {
        double[] p = {0.3, 0.7};
        assertThat(BayesInstance.klDivergence(p, p.clone())).isCloseTo(0.0, within(1e-15));
    }

    @Test
    void klDivergence_differentDistributions_isPositive() {
        double[] p = {0.8, 0.2};
        double[] q = {0.5, 0.5};
        assertThat(BayesInstance.klDivergence(p, q)).isGreaterThan(0.0);
    }

    @Test
    void evidenceFusionEngine_getVariableSensitivities_rankedByImpact() {
        BayesNetwork graph      = new BayesNetwork();
        var          graphNodes = new io.quarkmind.inference.bayes.graph.GraphNode[8];
        for (int i = 0; i < 8; i++) {graphNodes[i] = graph.addNode();}

        graphNodes[0].setContent(new BayesVariable<>("var_0", graphNodes[0].getId(),
                                                     new String[]{"false", "true"}, new double[][]{{0.5, 0.5}}));
        for (int i = 1; i < 8; i++) {
            graphNodes[i].setContent(new BayesVariable<>(BayesNetworkFixtures.varName(i), graphNodes[i].getId(),
                                                         new String[]{"false", "true"}, new double[][]{{0.7, 0.3}, {0.4, 0.6}}));
            io.quarkmind.inference.bayes.graph.impl.EdgeImpl edge = new io.quarkmind.inference.bayes.graph.impl.EdgeImpl();
            edge.setOutGraphNode(graphNodes[i - 1]);
            edge.setInGraphNode(graphNodes[i]);
        }

        io.quarkmind.perception.EvidenceFusionEngine engine = new io.quarkmind.perception.EvidenceFusionEngine();
        engine.initialize(graph, BayesNetworkFixtures.varName(7));

        engine.setEvidence(BayesNetworkFixtures.varName(6), new double[]{0.9, 0.1});
        engine.setEvidence(BayesNetworkFixtures.varName(0), new double[]{0.9, 0.1});
        engine.update();

        Map<String, Double> sensitivities = engine.getVariableSensitivities();
        assertThat(sensitivities).hasSize(2);

        var entries = sensitivities.entrySet().stream().toList();
        assertThat(entries.get(0).getKey()).isEqualTo(BayesNetworkFixtures.varName(6));
        assertThat(entries.get(0).getValue()).isGreaterThan(entries.get(1).getValue());
    }


}

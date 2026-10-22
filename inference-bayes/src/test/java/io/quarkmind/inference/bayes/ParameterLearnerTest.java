package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ParameterLearnerTest {

    @Test
    void rootNode_learnsPriorFromFrequencies() {
        BayesNetwork structure = BayesNetworkBuilder.create()
            .variable("Weather", "sunny", "rainy")
                .prior(0.5, 0.5)
            .build();

        List<Map<String, String>> data = List.of(
            Map.of("Weather", "sunny"),
            Map.of("Weather", "sunny"),
            Map.of("Weather", "sunny"),
            Map.of("Weather", "rainy")
        );

        // MLE with Laplace (alpha=1): sunny=(3+1)/(4+2)=4/6, rainy=(1+1)/(4+2)=2/6
        BayesNetwork learned = ParameterLearner.fit(structure, data);
        double[][] cpt = getVariable(learned, "Weather").getProbabilityTable();

        assertEquals(1, cpt.length);
        assertArrayEquals(new double[]{4.0 / 6, 2.0 / 6}, cpt[0], 1e-10);
    }

    @Test
    void singleParent_learnsConditionalProbabilities() {
        BayesNetwork structure = BayesNetworkBuilder.create()
            .variable("Sprinkler", "off", "on")
                .prior(0.5, 0.5)
            .variable("WetGrass", "dry", "wet")
                .given("Sprinkler")
                .cpt(0.5, 0.5, 0.5, 0.5)
            .build();

        List<Map<String, String>> data = List.of(
            Map.of("Sprinkler", "off", "WetGrass", "dry"),
            Map.of("Sprinkler", "off", "WetGrass", "dry"),
            Map.of("Sprinkler", "off", "WetGrass", "dry"),
            Map.of("Sprinkler", "off", "WetGrass", "wet"),
            Map.of("Sprinkler", "on",  "WetGrass", "wet"),
            Map.of("Sprinkler", "on",  "WetGrass", "wet"),
            Map.of("Sprinkler", "on",  "WetGrass", "wet"),
            Map.of("Sprinkler", "on",  "WetGrass", "dry")
        );

        BayesNetwork learned = ParameterLearner.fit(structure, data);
        double[][] wetGrassCpt = getVariable(learned, "WetGrass").getProbabilityTable();

        // Sprinkler=off: dry=3, wet=1 → (3+1)/(4+2)=4/6, (1+1)/(4+2)=2/6
        assertArrayEquals(new double[]{4.0 / 6, 2.0 / 6}, wetGrassCpt[0], 1e-10);
        // Sprinkler=on: dry=1, wet=3  → (1+1)/(4+2)=2/6, (3+1)/(4+2)=4/6
        assertArrayEquals(new double[]{2.0 / 6, 4.0 / 6}, wetGrassCpt[1], 1e-10);
    }

    @Test
    void multipleParents_correctRowIndexing() {
        BayesNetwork structure = BayesNetworkBuilder.create()
            .variable("A", "a0", "a1").prior(0.5, 0.5)
            .variable("B", "b0", "b1").prior(0.5, 0.5)
            .variable("C", "c0", "c1")
                .given("A", "B")
                .cpt(0.5, 0.5,  0.5, 0.5,  0.5, 0.5,  0.5, 0.5)
            .build();

        // 4 parent combos: (a0,b0)=row0, (a0,b1)=row1, (a1,b0)=row2, (a1,b1)=row3
        List<Map<String, String>> data = List.of(
            Map.of("A", "a0", "B", "b0", "C", "c0"),
            Map.of("A", "a0", "B", "b0", "C", "c0"),
            Map.of("A", "a0", "B", "b1", "C", "c1"),
            Map.of("A", "a1", "B", "b0", "C", "c0"),
            Map.of("A", "a1", "B", "b1", "C", "c1"),
            Map.of("A", "a1", "B", "b1", "C", "c1")
        );

        BayesNetwork learned = ParameterLearner.fit(structure, data);
        double[][] cCpt = getVariable(learned, "C").getProbabilityTable();

        // Row 0: A=a0,B=b0 → c0=2, c1=0 → (2+1)/(2+2)=3/4, (0+1)/(2+2)=1/4
        assertArrayEquals(new double[]{3.0 / 4, 1.0 / 4}, cCpt[0], 1e-10);
        // Row 1: A=a0,B=b1 → c0=0, c1=1 → (0+1)/(1+2)=1/3, (1+1)/(1+2)=2/3
        assertArrayEquals(new double[]{1.0 / 3, 2.0 / 3}, cCpt[1], 1e-10);
        // Row 2: A=a1,B=b0 → c0=1, c1=0 → (1+1)/(1+2)=2/3, (0+1)/(1+2)=1/3
        assertArrayEquals(new double[]{2.0 / 3, 1.0 / 3}, cCpt[2], 1e-10);
        // Row 3: A=a1,B=b1 → c0=0, c1=2 → (0+1)/(2+2)=1/4, (2+1)/(2+2)=3/4
        assertArrayEquals(new double[]{1.0 / 4, 3.0 / 4}, cCpt[3], 1e-10);
    }

    @Test
    void laplaceSmoothingPreventsZeroProbabilities() {
        BayesNetwork structure = BayesNetworkBuilder.create()
            .variable("X", "a", "b", "c").prior(0.33, 0.33, 0.34)
            .build();

        List<Map<String, String>> data = List.of(
            Map.of("X", "a"),
            Map.of("X", "a")
        );

        BayesNetwork learned = ParameterLearner.fit(structure, data);
        double[][] cpt = getVariable(learned, "X").getProbabilityTable();

        // a=2, b=0, c=0, alpha=1 → (3/5, 1/5, 1/5)
        for (double p : cpt[0]) {
            assertTrue(p > 0.0, "All probabilities must be > 0 with Laplace smoothing");
        }
        assertArrayEquals(new double[]{3.0 / 5, 1.0 / 5, 1.0 / 5}, cpt[0], 1e-10);
    }

    @Test
    void zeroAlpha_allowsZeroProbabilities() {
        BayesNetwork structure = BayesNetworkBuilder.create()
            .variable("X", "a", "b").prior(0.5, 0.5)
            .build();

        List<Map<String, String>> data = List.of(
            Map.of("X", "a"),
            Map.of("X", "a")
        );

        BayesNetwork learned = ParameterLearner.fit(structure, data, 0.0);
        double[][] cpt = getVariable(learned, "X").getProbabilityTable();

        assertArrayEquals(new double[]{1.0, 0.0}, cpt[0], 1e-10);
    }

    @Test
    void missingValues_skippedForThatVariable() {
        BayesNetwork structure = BayesNetworkBuilder.create()
            .variable("A", "a0", "a1").prior(0.5, 0.5)
            .variable("B", "b0", "b1")
                .given("A")
                .cpt(0.5, 0.5, 0.5, 0.5)
            .build();

        List<Map<String, String>> data = List.of(
            Map.of("A", "a0", "B", "b0"),
            Map.of("A", "a0", "B", "b1"),
            Map.of("A", "a1"),            // B missing — skip B for this row, count A
            Map.of("A", "a1", "B", "b1")
        );

        BayesNetwork learned = ParameterLearner.fit(structure, data);

        // A prior: a0=2, a1=2 → (3/6, 3/6) with alpha=1
        double[][] aCpt = getVariable(learned, "A").getProbabilityTable();
        assertArrayEquals(new double[]{3.0 / 6, 3.0 / 6}, aCpt[0], 1e-10);

        // B|A: row 3 excluded (A=a1 but B missing)
        // A=a0: b0=1, b1=1 → (2/4, 2/4)
        // A=a1: b0=0, b1=1 → (1/3, 2/3)
        double[][] bCpt = getVariable(learned, "B").getProbabilityTable();
        assertArrayEquals(new double[]{2.0 / 4, 2.0 / 4}, bCpt[0], 1e-10);
        assertArrayEquals(new double[]{1.0 / 3, 2.0 / 3}, bCpt[1], 1e-10);
    }

    @Test
    void missingParent_skipsChildRow() {
        BayesNetwork structure = BayesNetworkBuilder.create()
            .variable("A", "a0", "a1").prior(0.5, 0.5)
            .variable("B", "b0", "b1")
                .given("A")
                .cpt(0.5, 0.5, 0.5, 0.5)
            .build();

        List<Map<String, String>> data = List.of(
            Map.of("B", "b0"),             // A missing — can't determine parent config for B
            Map.of("A", "a0", "B", "b1"),
            Map.of("A", "a1", "B", "b0")
        );

        BayesNetwork learned = ParameterLearner.fit(structure, data);

        // B|A: row 1 excluded (A missing, can't index into B's CPT)
        // A=a0: b0=0, b1=1 → (1/3, 2/3)
        // A=a1: b0=1, b1=0 → (2/3, 1/3)
        double[][] bCpt = getVariable(learned, "B").getProbabilityTable();
        assertArrayEquals(new double[]{1.0 / 3, 2.0 / 3}, bCpt[0], 1e-10);
        assertArrayEquals(new double[]{2.0 / 3, 1.0 / 3}, bCpt[1], 1e-10);
    }

    @Test
    void learnedNetwork_producesCorrectPosteriors() {
        // Hand-specify a network with known CPTs
        BayesInstance<?> handSpecified = BayesNetworkBuilder.create()
            .variable("Rain",     "no", "yes").prior(0.8, 0.2)
            .variable("Sprinkler","off", "on").given("Rain").cpt(0.6, 0.4, 0.99, 0.01)
            .variable("WetGrass", "dry", "wet")
                .given("Sprinkler", "Rain")
                .cpt(1.0, 0.0,   // Sprinkler=off, Rain=no → always dry
                     0.2, 0.8,   // Sprinkler=off, Rain=yes → mostly wet
                     0.1, 0.9,   // Sprinkler=on, Rain=no → mostly wet
                     0.01, 0.99) // Sprinkler=on, Rain=yes → almost always wet
            .buildInstance();

        // Generate sufficient data from the same distribution
        // (use exact counts matching the CPTs for deterministic testing)
        BayesNetwork structure = BayesNetworkBuilder.create()
            .variable("Rain",     "no", "yes").prior(0.5, 0.5)
            .variable("Sprinkler","off", "on")
                .given("Rain")
                .cpt(0.5, 0.5, 0.5, 0.5)
            .variable("WetGrass", "dry", "wet")
                .given("Sprinkler", "Rain")
                .cpt(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5)
            .build();

        // 1000 rows matching the true distribution (exact, not sampled)
        List<Map<String, String>> data = generateExactData();

        BayesNetwork learned = ParameterLearner.fit(structure, data, 0.0);
        BayesInstance<?> learnedInstance = new BayesInstance<>(
            new JunctionTreeBuilder(learned).build());

        // Set evidence: WetGrass=wet, query Rain
        handSpecified.setLikelyhood("WetGrass", new double[]{0.0, 1.0});
        handSpecified.globalUpdate();
        double[] expectedRain = handSpecified.marginalize("Rain").getDistribution();

        learnedInstance.setLikelyhood("WetGrass", new double[]{0.0, 1.0});
        learnedInstance.globalUpdate();
        double[] learnedRain = learnedInstance.marginalize("Rain").getDistribution();

        assertArrayEquals(expectedRain, learnedRain, 1e-10);
    }

    @Test
    void worksWithBayesNetworkBuilderOutput() {
        BayesNetwork builderOutput = BayesNetworkBuilder.create("test-net")
            .variable("Cause", "low", "high")
                .prior(0.3, 0.7)
                .description("Root cause")
            .variable("Effect", "absent", "present")
                .given("Cause")
                .cpt(0.9, 0.1, 0.2, 0.8)
            .build();

        List<Map<String, String>> data = List.of(
            Map.of("Cause", "low",  "Effect", "absent"),
            Map.of("Cause", "low",  "Effect", "absent"),
            Map.of("Cause", "high", "Effect", "present"),
            Map.of("Cause", "high", "Effect", "present"),
            Map.of("Cause", "high", "Effect", "present")
        );

        BayesNetwork learned = ParameterLearner.fit(builderOutput, data);

        assertNotNull(learned);
        assertNotNull(getVariable(learned, "Cause"));
        assertNotNull(getVariable(learned, "Effect"));

        // Verify the learned network is functional
        BayesInstance<?> instance = new BayesInstance<>(
            new JunctionTreeBuilder(learned).build());
        instance.setLikelyhood("Cause", new double[]{1.0, 0.0});
        instance.globalUpdate();
        double[] effectDist = instance.marginalize("Effect").getDistribution();
        assertEquals(1.0, effectDist[0] + effectDist[1], 1e-10);
    }

    @Test
    void threeStateVariable_correctCounts() {
        BayesNetwork structure = BayesNetworkBuilder.create()
            .variable("Color", "red", "green", "blue").prior(0.33, 0.33, 0.34)
            .build();

        List<Map<String, String>> data = List.of(
            Map.of("Color", "red"),
            Map.of("Color", "green"),
            Map.of("Color", "green"),
            Map.of("Color", "blue"),
            Map.of("Color", "blue"),
            Map.of("Color", "blue")
        );

        BayesNetwork learned = ParameterLearner.fit(structure, data);
        double[][] cpt = getVariable(learned, "Color").getProbabilityTable();

        // red=1, green=2, blue=3, alpha=1 → (2/9, 3/9, 4/9)
        assertArrayEquals(new double[]{2.0 / 9, 3.0 / 9, 4.0 / 9}, cpt[0], 1e-10);
    }

    @Test
    void emptyObservations_fallsBackToUniformWithSmoothing() {
        BayesNetwork structure = BayesNetworkBuilder.create()
            .variable("X", "a", "b").prior(0.5, 0.5)
            .build();

        BayesNetwork learned = ParameterLearner.fit(structure, List.of());
        double[][] cpt = getVariable(learned, "X").getProbabilityTable();

        // No data, alpha=1 → (1/2, 1/2)
        assertArrayEquals(new double[]{0.5, 0.5}, cpt[0], 1e-10);
    }

    @Test
    void preservesNetworkName() {
        BayesNetwork structure = BayesNetworkBuilder.create("my-network")
            .variable("X", "a", "b").prior(0.5, 0.5)
            .build();

        BayesNetwork learned = ParameterLearner.fit(structure, List.of());
        assertEquals("my-network", learned.getName());
    }

    // --- helpers ---

    private static BayesVariable<?> getVariable(BayesNetwork network, String name) {
        for (var node : network) {
            if (node.getContent().getName().equals(name)) {
                return node.getContent();
            }
        }
        return null;
    }

    private static List<Map<String, String>> generateExactData() {
        // Generate data that exactly matches the CPTs of the hand-specified network:
        // P(Rain=no)=0.8, P(Rain=yes)=0.2
        // P(Sprinkler=off|Rain=no)=0.6, P(Sprinkler=on|Rain=no)=0.4
        // P(Sprinkler=off|Rain=yes)=0.99, P(Sprinkler=on|Rain=yes)=0.01
        // WetGrass CPT as specified
        //
        // Use 10000 total: distribute according to joint probabilities
        java.util.List<Map<String, String>> data = new java.util.ArrayList<>();

        // Rain=no (0.8 = 8000 rows)
        //   Sprinkler=off (0.6 = 4800)
        //     WetGrass: dry=1.0→4800, wet=0.0→0
        addRows(data, 4800, "no", "off", "dry");
        //   Sprinkler=on (0.4 = 3200)
        //     WetGrass: dry=0.1→320, wet=0.9→2880
        addRows(data, 320,  "no", "on", "dry");
        addRows(data, 2880, "no", "on", "wet");

        // Rain=yes (0.2 = 2000 rows)
        //   Sprinkler=off (0.99 = 1980)
        //     WetGrass: dry=0.2→396, wet=0.8→1584
        addRows(data, 396,  "yes", "off", "dry");
        addRows(data, 1584, "yes", "off", "wet");
        //   Sprinkler=on (0.01 = 20)
        //     WetGrass: dry=0.01→~0, wet=0.99→~20
        //     Use exact: 0 dry, 20 wet (round to keep total exact)
        addRows(data, 20,   "yes", "on", "wet");

        return data;
    }

    private static void addRows(List<Map<String, String>> data, int count,
                                String rain, String sprinkler, String wetGrass) {
        for (int i = 0; i < count; i++) {
            data.add(Map.of("Rain", rain, "Sprinkler", sprinkler, "WetGrass", wetGrass));
        }
    }
}

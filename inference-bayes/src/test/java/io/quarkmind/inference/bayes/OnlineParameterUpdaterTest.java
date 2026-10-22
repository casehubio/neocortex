package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class OnlineParameterUpdaterTest {

    private BayesNetwork buildSimpleNetwork() {
        return BayesNetworkBuilder.create("simple")
                .variable("Weather", "sunny", "rainy")
                    .prior(0.7, 0.3)
                .variable("Umbrella", "no", "yes")
                    .given("Weather")
                    .cpt(0.9, 0.1,    // sunny: rarely carry umbrella
                         0.2, 0.8)    // rainy: usually carry umbrella
                .build();
    }

    @Test
    void onlineConvergesToBatchMle() {
        BayesNetwork structure = buildSimpleNetwork();

        List<Map<String, String>> data = List.of(
                Map.of("Weather", "sunny", "Umbrella", "no"),
                Map.of("Weather", "sunny", "Umbrella", "no"),
                Map.of("Weather", "sunny", "Umbrella", "yes"),
                Map.of("Weather", "rainy", "Umbrella", "yes"),
                Map.of("Weather", "rainy", "Umbrella", "yes"),
                Map.of("Weather", "rainy", "Umbrella", "no"),
                Map.of("Weather", "sunny", "Umbrella", "no"),
                Map.of("Weather", "rainy", "Umbrella", "yes"),
                Map.of("Weather", "sunny", "Umbrella", "no"),
                Map.of("Weather", "sunny", "Umbrella", "no")
        );

        // Batch MLE
        BayesNetwork batchResult = ParameterLearner.fit(structure, data);
        BayesInstance<?> batchInstance = new BayesInstance<>(new JunctionTreeBuilder(batchResult).build());
        batchInstance.globalUpdate();
        double[] batchUmbrella = batchInstance.marginalize("Umbrella").getDistribution();

        // Online — feed same data one by one
        OnlineParameterUpdater updater = OnlineParameterUpdater.create(structure);
        for (Map<String, String> obs : data) {
            updater.update(obs);
        }
        BayesInstance<?> onlineInstance = updater.getInstance();
        onlineInstance.globalUpdate();
        double[] onlineUmbrella = onlineInstance.marginalize("Umbrella").getDistribution();

        assertArrayEquals(batchUmbrella, onlineUmbrella, 0.01,
                "Online should converge to batch MLE on same data");
    }

    @Test
    void exponentialDecayWeightsRecentObservations() {
        BayesNetwork structure = BayesNetworkBuilder.create()
                .variable("X", "a", "b").prior(0.5, 0.5)
                .build();

        // Decay factor 0.9: each old observation decays to 90% weight per update
        OnlineParameterUpdater updater = OnlineParameterUpdater.create(structure, 0.9);

        // First: 10 observations of X=a
        for (int i = 0; i < 10; i++) {
            updater.update(Map.of("X", "a"));
        }
        double[] afterA = updater.getCpt("X")[0].clone();
        assertTrue(afterA[0] > 0.9, "X=a should dominate after 10 a-observations");

        // Then: 10 observations of X=b
        for (int i = 0; i < 10; i++) {
            updater.update(Map.of("X", "b"));
        }
        double[] afterB = updater.getCpt("X")[0];
        assertTrue(afterB[1] > afterB[0],
                "X=b should dominate after 10 b-observations with decay");

        // Without decay, 10 a's + 10 b's = 50/50. With decay, b's dominate.
        assertTrue(afterB[1] > 0.5, "Decay should weight recent b-observations more");
    }

    @Test
    void noFullJunctionTreeRebuild() {
        BayesNetwork structure = buildSimpleNetwork();
        OnlineParameterUpdater updater = OnlineParameterUpdater.create(structure);

        JunctionTree treeBefore = updater.getJunctionTree();

        updater.update(Map.of("Weather", "sunny", "Umbrella", "no"));
        updater.update(Map.of("Weather", "rainy", "Umbrella", "yes"));

        JunctionTree treeAfter = updater.getJunctionTree();

        assertSame(treeBefore, treeAfter,
                "Junction tree structure should be reused, not rebuilt");
    }

    @Test
    void threadSafeConcurrentUpdates() throws Exception {
        BayesNetwork structure = buildSimpleNetwork();
        OnlineParameterUpdater updater = OnlineParameterUpdater.create(structure);

        int threadCount = 8;
        int updatesPerThread = 100;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        AtomicInteger errors = new AtomicInteger(0);

        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            threads.add(Thread.ofVirtual().start(() -> {
                try {
                    barrier.await();
                    for (int i = 0; i < updatesPerThread; i++) {
                        String weather = (threadId + i) % 2 == 0 ? "sunny" : "rainy";
                        String umbrella = weather.equals("rainy") ? "yes" : "no";
                        updater.update(Map.of("Weather", weather, "Umbrella", umbrella));
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            }));
        }

        for (Thread t : threads) {
            t.join(5000);
        }

        assertEquals(0, errors.get(), "No errors during concurrent updates");
        assertEquals(threadCount * updatesPerThread, updater.getObservationCount());

        // Instance should be queryable after concurrent updates
        BayesInstance<?> instance = updater.getInstance();
        instance.globalUpdate();
        double[] dist = instance.marginalize("Weather").getDistribution();
        assertEquals(1.0, dist[0] + dist[1], 0.001, "Distribution should sum to 1");
    }

    @Test
    void worksWithBuilderConstructedNetworks() {
        BayesInstance<?> original = BayesNetworkBuilder.create()
                .variable("A", "false", "true").prior(0.5, 0.5)
                .variable("B", "false", "true")
                    .given("A").cpt(0.8, 0.2, 0.3, 0.7)
                .variable("C", "false", "true")
                    .given("A", "B")
                    .cpt(0.9, 0.1, 0.6, 0.4, 0.4, 0.6, 0.1, 0.9)
                .buildInstance();

        BayesNetwork structure = BayesNetworkBuilder.create()
                .variable("A", "false", "true").prior(0.5, 0.5)
                .variable("B", "false", "true")
                    .given("A").cpt(0.8, 0.2, 0.3, 0.7)
                .variable("C", "false", "true")
                    .given("A", "B")
                    .cpt(0.9, 0.1, 0.6, 0.4, 0.4, 0.6, 0.1, 0.9)
                .build();

        OnlineParameterUpdater updater = OnlineParameterUpdater.create(structure);

        // Feed observations that match the original CPTs
        updater.update(Map.of("A", "true", "B", "true", "C", "true"));
        updater.update(Map.of("A", "false", "B", "false", "C", "false"));

        BayesInstance<?> instance = updater.getInstance();
        instance.setHardEvidence("A", "true");
        instance.globalUpdate();
        double[] bPosterior = instance.marginalize("B").getDistribution();

        // Should produce valid posteriors
        assertTrue(bPosterior[0] >= 0 && bPosterior[0] <= 1);
        assertEquals(1.0, bPosterior[0] + bPosterior[1], 0.001);
    }

    @Test
    void createFromHistoricalDataCarriesCounts() {
        BayesNetwork structure = buildSimpleNetwork();

        List<Map<String, String>> historical = List.of(
                Map.of("Weather", "sunny", "Umbrella", "no"),
                Map.of("Weather", "sunny", "Umbrella", "no"),
                Map.of("Weather", "rainy", "Umbrella", "yes")
        );

        OnlineParameterUpdater updater = OnlineParameterUpdater.create(structure, historical);
        assertEquals(3, updater.getObservationCount());

        // Add one more
        updater.update(Map.of("Weather", "rainy", "Umbrella", "yes"));
        assertEquals(4, updater.getObservationCount());

        // Should match batch on same 4 observations
        List<Map<String, String>> allData = new ArrayList<>(historical);
        allData.add(Map.of("Weather", "rainy", "Umbrella", "yes"));

        BayesNetwork batchResult = ParameterLearner.fit(structure, allData);
        BayesInstance<?> batchInstance = new BayesInstance<>(new JunctionTreeBuilder(batchResult).build());
        batchInstance.globalUpdate();

        BayesInstance<?> onlineInstance = updater.getInstance();
        onlineInstance.globalUpdate();

        assertArrayEquals(
                batchInstance.marginalize("Weather").getDistribution(),
                onlineInstance.marginalize("Weather").getDistribution(),
                0.01);
    }

    @Test
    void observationCountTracked() {
        BayesNetwork structure = buildSimpleNetwork();
        OnlineParameterUpdater updater = OnlineParameterUpdater.create(structure);

        assertEquals(0, updater.getObservationCount());
        updater.update(Map.of("Weather", "sunny", "Umbrella", "no"));
        assertEquals(1, updater.getObservationCount());
        updater.update(Map.of("Weather", "rainy", "Umbrella", "yes"));
        assertEquals(2, updater.getObservationCount());
    }

    @Test
    void partialObservationSkipsMissingVariables() {
        BayesNetwork structure = buildSimpleNetwork();
        OnlineParameterUpdater updater = OnlineParameterUpdater.create(structure);

        // Only Weather observed, Umbrella missing — should update Weather stats only
        updater.update(Map.of("Weather", "sunny"));
        updater.update(Map.of("Weather", "sunny"));
        updater.update(Map.of("Weather", "rainy"));

        double[] weatherCpt = updater.getCpt("Weather")[0];
        // With Laplace alpha=1: (2+1)/(3+2) = 0.6 for sunny, (1+1)/(3+2) = 0.4 for rainy
        assertEquals(0.6, weatherCpt[0], 0.01);
        assertEquals(0.4, weatherCpt[1], 0.01);
    }
}

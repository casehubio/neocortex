package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.platform.api.identity.PrincipalId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainActivationTest {

    private static final String TENANT = "test-tenant";
    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");
    private static final Confidence CONF = new Confidence(ConfidenceOrigin.STATED, 0.8, BASE);

    private InMemoryMindMapStore mindMapStore;
    private CognitiveProfileTest.TestMemoryStore memoryStore;
    private DomainActivation domainActivation;

    @BeforeEach
    void setUp() {
        mindMapStore = new InMemoryMindMapStore();
        memoryStore = new CognitiveProfileTest.TestMemoryStore();
        domainActivation = new DomainActivation(mindMapStore, memoryStore);
    }

    @Test
    void correlateTwoSubgraphsWithCorrelatedSignals() {
        String workSg = mindMapStore.createSubgraph(new SubgraphInput("work", "organisation", null), TENANT);
        String familySg = mindMapStore.createSubgraph(new SubgraphInput("family", "person", null), TENANT);

        String workEntity = mindMapStore.addNode(
            new NodeInput("Project", workSg, CONF, null, null, null, null, null, null, null, null, Map.of()), TENANT);
        String familyEntity = mindMapStore.addNode(
            new NodeInput("Spouse", familySg, CONF, null, null, null, null, null, null, null, null, Map.of()), TENANT);

        PrincipalId alice = PrincipalId.agent("alice");

        for (int day = 0; day < 10; day++) {
            double pleasure = 0.1 * day;
            memoryStore.storeAt(workEntity, pleasure, pleasure * 0.5, pleasure * 0.3,
                BASE.plus(Duration.ofDays(day)), TENANT);
            memoryStore.storeAt(familyEntity, pleasure * 0.8, pleasure * 0.4, pleasure * 0.2,
                BASE.plus(Duration.ofDays(day)), TENANT);
        }

        var query = DomainActivationQuery.between(alice, TENANT, workSg, familySg)
            .withFrom(BASE).withTo(BASE.plus(Duration.ofDays(10)));
        var result = domainActivation.correlate(query);

        assertThat(result).isPresent();
        var corr = result.get().correlations().values().iterator().next();
        assertThat(corr.strength()).isIn(CorrelationStrength.STRONG, CorrelationStrength.MODERATE);
        assertThat(corr.dtwSimilarity()).isGreaterThan(0.3);
    }

    @Test
    void correlateUncorrelatedSubgraphs() {
        String sgA = mindMapStore.createSubgraph(new SubgraphInput("sgA", "general", null), TENANT);
        String sgB = mindMapStore.createSubgraph(new SubgraphInput("sgB", "general", null), TENANT);

        String entityA = mindMapStore.addNode(
                new NodeInput("EntityA", sgA, CONF, null, null, null, null, null, null, null, null, Map.of()), TENANT);
        String entityB = mindMapStore.addNode(
                new NodeInput("EntityB", sgB, CONF, null, null, null, null, null, null, null, null, Map.of()), TENANT);

        PrincipalId alice = PrincipalId.agent("alice");

        for (int day = 0; day < 10; day++) {
            double v = 0.1 * day;
            memoryStore.storeAt(entityA, v, v, v,
                                BASE.plus(Duration.ofDays(day)), TENANT);
            memoryStore.storeAt(entityB, -v, -v, -v,
                                BASE.plus(Duration.ofDays(day)), TENANT);
        }

        var query = DomainActivationQuery.between(alice, TENANT, sgA, sgB)
                                         .withFrom(BASE).withTo(BASE.plus(Duration.ofDays(10)));
        var result = domainActivation.correlate(query);

        assertThat(result).isPresent();
        var corr = result.get().correlations().values().iterator().next();
        assertThat(corr.strength()).isIn(CorrelationStrength.WEAK, CorrelationStrength.NONE);}

    @Test
    void correlateRequiresPrincipal() {
        assertThatThrownBy(() -> new DomainActivationQuery(
            null, Set.of("a", "b"), TENANT, null, null, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void correlateEmptySubgraphReturnsEmpty() {
        String emptySg = mindMapStore.createSubgraph(new SubgraphInput("empty", "general", null), TENANT);
        String otherSg = mindMapStore.createSubgraph(new SubgraphInput("other", "general", null), TENANT);

        PrincipalId alice = PrincipalId.agent("alice");
        var query = DomainActivationQuery.between(alice, TENANT, emptySg, otherSg);
        assertThat(domainActivation.correlate(query)).isEmpty();
    }

    @Test
    void correlateSubgraphWithNoMemoriesReturnsEmpty() {
        String sgA = mindMapStore.createSubgraph(new SubgraphInput("sgA", "general", null), TENANT);
        String sgB = mindMapStore.createSubgraph(new SubgraphInput("sgB", "general", null), TENANT);

        mindMapStore.addNode(
            new NodeInput("EntityA", sgA, CONF, null, null, null, null, null, null, null, null, Map.of()), TENANT);
        mindMapStore.addNode(
            new NodeInput("EntityB", sgB, CONF, null, null, null, null, null, null, null, null, Map.of()), TENANT);

        PrincipalId alice = PrincipalId.agent("alice");
        var query = DomainActivationQuery.between(alice, TENANT, sgA, sgB);
        assertThat(domainActivation.correlate(query)).isEmpty();
    }

    @Test
    void gracefulDegradationNoMindMapStore() {
        var degraded = new DomainActivation(null, memoryStore);
        PrincipalId alice = PrincipalId.agent("alice");
        var query = DomainActivationQuery.between(alice, TENANT, "a", "b");
        assertThat(degraded.correlate(query)).isEmpty();
    }

    @Test
    void gracefulDegradationNoMemoryStore() {
        var degraded = new DomainActivation(mindMapStore, null);
        String sgA = mindMapStore.createSubgraph(new SubgraphInput("sgA", "general", null), TENANT);
        String sgB = mindMapStore.createSubgraph(new SubgraphInput("sgB", "general", null), TENANT);

        PrincipalId alice = PrincipalId.agent("alice");
        var query = DomainActivationQuery.between(alice, TENANT, sgA, sgB);
        assertThat(degraded.correlate(query)).isEmpty();
    }

    @Test
    void correlateThreeSubgraphsAllPairwise() {
        String sgA = mindMapStore.createSubgraph(new SubgraphInput("sgA", "general", null), TENANT);
        String sgB = mindMapStore.createSubgraph(new SubgraphInput("sgB", "general", null), TENANT);
        String sgC = mindMapStore.createSubgraph(new SubgraphInput("sgC", "general", null), TENANT);

        String entityA = mindMapStore.addNode(
            new NodeInput("A", sgA, CONF, null, null, null, null, null, null, null, null, Map.of()), TENANT);
        String entityB = mindMapStore.addNode(
            new NodeInput("B", sgB, CONF, null, null, null, null, null, null, null, null, Map.of()), TENANT);
        String entityC = mindMapStore.addNode(
            new NodeInput("C", sgC, CONF, null, null, null, null, null, null, null, null, Map.of()), TENANT);

        PrincipalId alice = PrincipalId.agent("alice");
        for (int day = 0; day < 5; day++) {
            double v = 0.1 * day;
            Instant t = BASE.plus(Duration.ofDays(day));
            memoryStore.storeAt(entityA, v, v * 0.5, v * 0.3, t, TENANT);
            memoryStore.storeAt(entityB, v * 0.9, v * 0.4, v * 0.2, t, TENANT);
            memoryStore.storeAt(entityC, v * 0.7, v * 0.3, v * 0.1, t, TENANT);
        }

        var query = new DomainActivationQuery(alice, Set.of(sgA, sgB, sgC),
            TENANT, BASE, BASE.plus(Duration.ofDays(5)), null);
        var result = domainActivation.correlate(query);

        assertThat(result).isPresent();
        assertThat(result.get().correlations()).hasSize(3);
        assertThat(result.get().domains()).hasSize(3);
    }

    @Test
    void domainSignalReportsCorrectCounts() {
        String sgA = mindMapStore.createSubgraph(new SubgraphInput("sgA", "general", null), TENANT);
        String sgB = mindMapStore.createSubgraph(new SubgraphInput("sgB", "general", null), TENANT);

        String e1 = mindMapStore.addNode(
            new NodeInput("E1", sgA, CONF, null, null, null, null, null, null, null, null, Map.of()), TENANT);
        String e2 = mindMapStore.addNode(
            new NodeInput("E2", sgA, CONF, null, null, null, null, null, null, null, null, Map.of()), TENANT);
        String e3 = mindMapStore.addNode(
            new NodeInput("E3", sgB, CONF, null, null, null, null, null, null, null, null, Map.of()), TENANT);

        PrincipalId alice = PrincipalId.agent("alice");
        for (int day = 0; day < 5; day++) {
            Instant t = BASE.plus(Duration.ofDays(day));
            memoryStore.storeAt(e1, 0.1 * day, 0.0, 0.0, t, TENANT);
            memoryStore.storeAt(e2, 0.2 * day, 0.0, 0.0, t, TENANT);
            memoryStore.storeAt(e3, 0.15 * day, 0.0, 0.0, t, TENANT);
        }

        var query = DomainActivationQuery.between(alice, TENANT, sgA, sgB)
            .withFrom(BASE).withTo(BASE.plus(Duration.ofDays(5)));
        var result = domainActivation.correlate(query);

        assertThat(result).isPresent();
        DomainSignal signalA = result.get().domains().get(sgA);
        assertThat(signalA.entityCount()).isEqualTo(2);
        assertThat(signalA.memoryCount()).isEqualTo(10);

        DomainSignal signalB = result.get().domains().get(sgB);
        assertThat(signalB.entityCount()).isEqualTo(1);
        assertThat(signalB.memoryCount()).isEqualTo(5);
    }
}
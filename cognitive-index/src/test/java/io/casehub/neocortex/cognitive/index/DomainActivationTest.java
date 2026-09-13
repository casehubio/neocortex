package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.experience.ExperienceAttributeKeys;
import io.casehub.neocortex.memory.experience.ExperienceEvents;
import io.casehub.neocortex.memory.mood.MoodAttributeKeys;
import io.casehub.neocortex.memory.mood.MoodEvents;
import io.casehub.neocortex.memory.mood.MoodState;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.platform.api.identity.PrincipalId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
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
            null, Set.of("a", "b"), TENANT, null, null, null, Set.of(), null))
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
            TENANT, BASE, BASE.plus(Duration.ofDays(5)), null, Set.of(), null);
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

    @Test
    void emptyContextDomainsProducesEmptyMaps() {
        PrincipalId alice = PrincipalId.agent("alice");
        String sgA = mindMapStore.createSubgraph(new SubgraphInput("sgA", "general", null), TENANT);
        String sgB = mindMapStore.createSubgraph(new SubgraphInput("sgB", "general", null), TENANT);
        String nodeA = mindMapStore.addNode(
            new NodeInput("eA", sgA, CONF, null, null, null, null, null, null, null, null, Map.of()), TENANT);
        String nodeB = mindMapStore.addNode(
            new NodeInput("eB", sgB, CONF, null, null, null, null, null, null, null, null, Map.of()), TENANT);
        for (int d = 0; d < 10; d++) {
            Instant t = BASE.plus(Duration.ofDays(d));
            memoryStore.storeAt(nodeA, d * 0.1, 0.0, 0.0, t, TENANT);
            memoryStore.storeAt(nodeB, d * 0.05, 0.0, 0.0, t, TENANT);
        }

        var query = DomainActivationQuery.between(alice, TENANT, sgA, sgB)
                        .withFrom(BASE).withTo(BASE.plus(Duration.ofDays(10)));
        var result = domainActivation.correlate(query);

        assertThat(result).isPresent();
        assertThat(result.get().contextCorrelations()).isEmpty();
        assertThat(result.get().eventImpacts()).isEmpty();
    }

    @Test
    void moodCorrelationWithContextAttribution() {
        PrincipalId alice = PrincipalId.agent("alice");
        String sgA = mindMapStore.createSubgraph(new SubgraphInput("sgA", "general", null), TENANT);
        String sgB = mindMapStore.createSubgraph(new SubgraphInput("sgB", "general", null), TENANT);
        String nodeA = mindMapStore.addNode(
            new NodeInput("eA", sgA, CONF, null, null, null, null, null, null, null, null, Map.of()), TENANT);
        String nodeB = mindMapStore.addNode(
            new NodeInput("eB", sgB, CONF, null, null, null, null, null, null, null, null, Map.of()), TENANT);

        for (int d = 0; d < 10; d++) {
            Instant t = BASE.plus(Duration.ofDays(d));
            double v = d * 0.1;
            memoryStore.storeAt(nodeA, v, 0.0, 0.0, t, TENANT);
            memoryStore.storeAt(nodeB, -v, 0.0, 0.0, t, TENANT);
            storeMoodAt(alice, v, 0.0, 0.0, t, Set.of(sgA));
        }

        var query = DomainActivationQuery.between(alice, TENANT, sgA, sgB)
                        .withContextDomains(Set.of(MoodEvents.DOMAIN))
                        .withFrom(BASE).withTo(BASE.plus(Duration.ofDays(10)));
        var result = domainActivation.correlate(query);

        assertThat(result).isPresent();
        var moodCorrelations = result.get().contextCorrelations().get(MoodEvents.DOMAIN);
        assertThat(moodCorrelations).isNotNull();
        assertThat(moodCorrelations).containsKey(sgA);
        assertThat(moodCorrelations).containsKey(sgB);
        assertThat(moodCorrelations.get(sgA).totalMoodCount()).isGreaterThan(0);
    }

    @Test
    void agentGlobalMoodCorrelatesWithAllSubgraphs() {
        PrincipalId alice = PrincipalId.agent("alice");
        String sgA = mindMapStore.createSubgraph(new SubgraphInput("sgA", "general", null), TENANT);
        String sgB = mindMapStore.createSubgraph(new SubgraphInput("sgB", "general", null), TENANT);
        String nodeA = mindMapStore.addNode(
            new NodeInput("eA", sgA, CONF, null, null, null, null, null, null, null, null, Map.of()), TENANT);
        String nodeB = mindMapStore.addNode(
            new NodeInput("eB", sgB, CONF, null, null, null, null, null, null, null, null, Map.of()), TENANT);

        for (int d = 0; d < 10; d++) {
            Instant t = BASE.plus(Duration.ofDays(d));
            memoryStore.storeAt(nodeA, d * 0.1, 0.0, 0.0, t, TENANT);
            memoryStore.storeAt(nodeB, d * 0.1, 0.0, 0.0, t, TENANT);
            storeMoodAt(alice, d * 0.1, 0.0, 0.0, t, null);
        }

        var query = DomainActivationQuery.between(alice, TENANT, sgA, sgB)
                        .withContextDomains(Set.of(MoodEvents.DOMAIN))
                        .withFrom(BASE).withTo(BASE.plus(Duration.ofDays(10)));
        var result = domainActivation.correlate(query);

        assertThat(result).isPresent();
        var moodCorrelations = result.get().contextCorrelations().get(MoodEvents.DOMAIN);
        assertThat(moodCorrelations.get(sgA).totalMoodCount()).isGreaterThan(0);
        assertThat(moodCorrelations.get(sgB).totalMoodCount()).isGreaterThan(0);
        assertThat(moodCorrelations.get(sgA).contextAttributedCount()).isEqualTo(0);
    }

    @Test
    void experienceCorrelationProducesEventImpact() {
        PrincipalId alice = PrincipalId.agent("alice");
        String sgA = mindMapStore.createSubgraph(new SubgraphInput("sgA", "general", null), TENANT);
        String sgB = mindMapStore.createSubgraph(new SubgraphInput("sgB", "general", null), TENANT);
        String nodeA = mindMapStore.addNode(
            new NodeInput("eA", sgA, CONF, null, null, null, null, null, null, null, null, Map.of()), TENANT);
        String nodeB = mindMapStore.addNode(
            new NodeInput("eB", sgB, CONF, null, null, null, null, null, null, null, null, Map.of()), TENANT);

        for (int d = 0; d < 20; d++) {
            Instant t = BASE.plus(Duration.ofDays(d));
            double v = d < 10 ? -0.3 : 0.5;
            memoryStore.storeAt(nodeA, v, 0.0, 0.0, t, TENANT);
            memoryStore.storeAt(nodeB, 0.0, 0.0, 0.0, t, TENANT);
        }
        storeExperienceAt(alice, BASE.plus(Duration.ofDays(10)), "outcome");

        var query = DomainActivationQuery.between(alice, TENANT, sgA, sgB)
                        .withContextDomains(Set.of(ExperienceEvents.DOMAIN))
                        .withEventWindow(Duration.ofDays(5))
                        .withFrom(BASE).withTo(BASE.plus(Duration.ofDays(20)));
        var result = domainActivation.correlate(query);

        assertThat(result).isPresent();
        var impacts = result.get().eventImpacts().get(ExperienceEvents.DOMAIN);
        assertThat(impacts).isNotNull().containsKey(sgA);
        assertThat(impacts.get(sgA).totalEvents()).isEqualTo(1);
        assertThat(impacts.get(sgA).byType()).containsKey("outcome");
    }

    @Test
    void singleSubgraphAllowedWithContextDomains() {
        PrincipalId alice = PrincipalId.agent("alice");
        String sgA = mindMapStore.createSubgraph(new SubgraphInput("sgA", "general", null), TENANT);
        mindMapStore.addNode(
            new NodeInput("eA", sgA, CONF, null, null, null, null, null, null, null, null, Map.of()), TENANT);

        var query = new DomainActivationQuery(alice, Set.of(sgA), TENANT,
                        null, null, null, Set.of(MoodEvents.DOMAIN), null);
        assertThat(query.subgraphIds()).hasSize(1);
    }

    @Test
    void singleSubgraphWithoutContextDomainsThrows() {
        PrincipalId alice = PrincipalId.agent("alice");
        assertThatThrownBy(() -> new DomainActivationQuery(alice, Set.of("sg"),
            TENANT, null, null, null, Set.of(), null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private void storeMoodAt(PrincipalId agent, double p, double a, double d,
                             Instant t, Set<String> contextIds) {
        var attrs = new HashMap<String, String>();
        attrs.put(MoodAttributeKeys.PLEASURE, String.valueOf(p));
        attrs.put(MoodAttributeKeys.AROUSAL, String.valueOf(a));
        attrs.put(MoodAttributeKeys.DOMINANCE, String.valueOf(d));
        attrs.put(MoodAttributeKeys.TIMESTAMP, t.toString());
        if (contextIds != null && !contextIds.isEmpty()) {
            attrs.put(MoodAttributeKeys.ACTIVE_CONTEXT_IDS, String.join(",", contextIds));
        }
        memoryStore.storeAtDomain(Subject.of("agent", agent.id()), MoodEvents.DOMAIN, TENANT,
                "mood", attrs, t, p, a, d, agent);
    }

    private void storeExperienceAt(PrincipalId agent, Instant t, String eventType) {
        var attrs = new HashMap<String, String>();
        attrs.put(ExperienceAttributeKeys.EVENT_TYPE, eventType);
        attrs.put(ExperienceAttributeKeys.TIMESTAMP, t.toString());
        memoryStore.storeAtDomain(Subject.of("agent", agent.id()), ExperienceEvents.DOMAIN, TENANT,
                "experience event", attrs, t, null, null, null, agent);
    }
}
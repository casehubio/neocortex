package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.*;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecurrenceGeneratorTest {

    private InMemoryMindMapStore store;
    private String subgraphId;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        subgraphId = store.createSubgraph(
            new SubgraphInput("Test", SubgraphType.GENERAL, null), "t1");
    }

    @Test
    void dailyRecurrence_generatesCorrectCount() {
        Instant start = Instant.parse("2026-09-01T09:00:00Z");
        String templateId = store.addNode(new NodeInput("Standup", subgraphId,
            null, "test", null, null,
            start, null, null, null, null,
            Map.of("rrule", "FREQ=DAILY;COUNT=3", "eventKind", "scheduled")), "t1");
        MindMapNode template = store.getNode(templateId, "t1");

        RecurrenceRule rule = RecurrenceRule.parse("FREQ=DAILY;COUNT=3");
        Instant horizon = start.plus(30, ChronoUnit.DAYS);

        List<NodeInput> instances = RecurrenceGenerator.generateInstances(template, rule, horizon);
        assertThat(instances).hasSize(3);
        assertThat(instances.get(0).validFrom()).isEqualTo(start.plus(1, ChronoUnit.DAYS));
        assertThat(instances.get(1).validFrom()).isEqualTo(start.plus(2, ChronoUnit.DAYS));
        assertThat(instances.get(2).validFrom()).isEqualTo(start.plus(3, ChronoUnit.DAYS));
    }

    @Test
    void instances_inheritTemplateProperties_exceptRrule() {
        Instant start = Instant.parse("2026-09-01T09:00:00Z");
        String templateId = store.addNode(new NodeInput("Meeting", subgraphId,
            null, "test", null, null,
            start, null, null, null, null,
            Map.of("rrule", "FREQ=DAILY;COUNT=1", "eventKind", "scheduled")), "t1");
        MindMapNode template = store.getNode(templateId, "t1");

        RecurrenceRule rule = RecurrenceRule.parse("FREQ=DAILY;COUNT=1");
        List<NodeInput> instances = RecurrenceGenerator.generateInstances(
            template, rule, start.plus(7, ChronoUnit.DAYS));

        NodeInput instance = instances.get(0);
        assertThat(instance.name()).isEqualTo("Meeting");
        assertThat(instance.subgraphId()).isEqualTo(subgraphId);
        assertThat(instance.properties()).containsEntry("eventKind", "scheduled");
        assertThat(instance.properties()).doesNotContainKey("rrule");
        assertThat(instance.properties()).containsEntry("template-node-id", templateId);
        assertThat(instance.properties()).containsEntry("recurrence-index", "0");
        assertThat(instance.properties()).containsEntry("status", "planned");
    }

    @Test
    void horizonLimitsGeneration() {
        Instant start = Instant.parse("2026-09-01T09:00:00Z");
        String templateId = store.addNode(new NodeInput("Daily", subgraphId,
            null, "test", null, null,
            start, null, null, null, null,
            Map.of("rrule", "FREQ=DAILY")), "t1");
        MindMapNode template = store.getNode(templateId, "t1");

        RecurrenceRule rule = RecurrenceRule.parse("FREQ=DAILY");
        List<NodeInput> instances = RecurrenceGenerator.generateInstances(
            template, rule, start.plus(3, ChronoUnit.DAYS));

        assertThat(instances).hasSize(3);
    }

    @Test
    void weeklyWithInterval() {
        Instant start = Instant.parse("2026-09-01T09:00:00Z");
        String templateId = store.addNode(new NodeInput("Biweekly", subgraphId,
            null, "test", null, null,
            start, null, null, null, null, Map.of()), "t1");
        MindMapNode template = store.getNode(templateId, "t1");

        RecurrenceRule rule = RecurrenceRule.parse("FREQ=WEEKLY;INTERVAL=2;COUNT=2");
        List<NodeInput> instances = RecurrenceGenerator.generateInstances(
            template, rule, start.plus(60, ChronoUnit.DAYS));

        assertThat(instances).hasSize(2);
        assertThat(instances.get(0).validFrom()).isEqualTo(start.plus(14, ChronoUnit.DAYS));
        assertThat(instances.get(1).validFrom()).isEqualTo(start.plus(28, ChronoUnit.DAYS));
    }

    @Test
    void emptyResult_whenTemplateHasNoValidFrom() {
        String templateId = store.addNode(new NodeInput("NoTime", subgraphId,
            null, "test", null, null,
            null, null, null, null, null, Map.of()), "t1");
        MindMapNode template = store.getNode(templateId, "t1");

        RecurrenceRule rule = RecurrenceRule.parse("FREQ=DAILY;COUNT=5");
        List<NodeInput> instances = RecurrenceGenerator.generateInstances(
            template, rule, Instant.now().plus(30, ChronoUnit.DAYS));

        assertThat(instances).isEmpty();
    }
}

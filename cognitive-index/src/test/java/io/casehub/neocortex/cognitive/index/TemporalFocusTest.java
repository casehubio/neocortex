package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.mood.AffectEvents;
import io.casehub.neocortex.mindmap.MindMapNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TemporalFocusTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");
    private static final String TENANT = "t1";
    private static final TemporalFocusConfig CONFIG = TemporalFocusConfig.defaults();

    @Test
    void upcomingEvent_scoresByProximity() {
        MindMapNode close = stubNode("n1", "Meeting Tomorrow",
            NOW.plus(1, ChronoUnit.DAYS));
        MindMapNode far = stubNode("n2", "Conference Next Month",
            NOW.plus(30, ChronoUnit.DAYS));

        List<TemporalEntry> entries = List.of(
            new TemporalEntry(close.validFrom(), new TemporalSource.FromMindMap(close), TENANT, null),
            new TemporalEntry(far.validFrom(), new TemporalSource.FromMindMap(far), TENANT, null)
        );

        List<AttentionItem> items = TemporalFocus.focus(entries, NOW, Map.of(), CONFIG);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).entry().source()).isInstanceOf(TemporalSource.FromMindMap.class);
        assertThat(items.get(0).salience()).isGreaterThan(items.get(1).salience());
        assertThat(items.get(0).reason()).isEqualTo("approaching event");
    }

    @Test
    void recentMemory_scoresByRecency() {
        Memory recent = stubMemory("m1", "entity1",
            NOW.minus(1, ChronoUnit.HOURS));
        Memory older = stubMemory("m2", "entity2",
            NOW.minus(24, ChronoUnit.HOURS));

        List<TemporalEntry> entries = List.of(
            new TemporalEntry(recent.createdAt(), new TemporalSource.FromMemory(recent), TENANT, null),
            new TemporalEntry(older.createdAt(), new TemporalSource.FromMemory(older), TENANT, null)
        );

        List<AttentionItem> items = TemporalFocus.focus(entries, NOW, Map.of(), CONFIG);

        assertThat(items.get(0).salience()).isGreaterThan(items.get(1).salience());
        assertThat(items.get(0).reason()).isEqualTo("recent experience");
    }

    @Test
    void worseningTrajectory_boostsSalience() {
        MindMapNode node = stubNode("n1", "Stressful Event",
            NOW.plus(3, ChronoUnit.DAYS));
        TemporalEntry entry = new TemporalEntry(
            node.validFrom(), new TemporalSource.FromMindMap(node), TENANT, null);

        AffectTrajectory worsening = new AffectTrajectory(-0.2, 0.1, -0.1,
            TrendDirection.WORSENING, 0.2, 5);

        List<AttentionItem> withTrajectory = TemporalFocus.focus(
            List.of(entry), NOW, Map.of("n1", worsening), CONFIG);
        List<AttentionItem> without = TemporalFocus.focus(
            List.of(entry), NOW, Map.of(), CONFIG);

        assertThat(withTrajectory.get(0).salience()).isGreaterThan(without.get(0).salience());
        assertThat(withTrajectory.get(0).reason()).contains("worsening affect");
    }

    @Test
    void improvingTrajectory_dampensSalience() {
        MindMapNode node = stubNode("n1", "Recovering",
            NOW.plus(5, ChronoUnit.DAYS));
        TemporalEntry entry = new TemporalEntry(
            node.validFrom(), new TemporalSource.FromMindMap(node), TENANT, null);

        AffectTrajectory improving = new AffectTrajectory(0.3, 0.1, 0.1,
            TrendDirection.IMPROVING, 0.3, 5);

        List<AttentionItem> withTrajectory = TemporalFocus.focus(
            List.of(entry), NOW, Map.of("n1", improving), CONFIG);
        List<AttentionItem> without = TemporalFocus.focus(
            List.of(entry), NOW, Map.of(), CONFIG);

        assertThat(withTrajectory.get(0).salience()).isLessThan(without.get(0).salience());
    }

    @Test
    void highVolatility_boostsSalience() {
        Memory memory = stubMemory("m1", "e1", NOW.minus(2, ChronoUnit.HOURS));
        TemporalEntry entry = new TemporalEntry(
            memory.createdAt(), new TemporalSource.FromMemory(memory), TENANT, null);

        AffectTrajectory volatile_ = new AffectTrajectory(0.0, 0.6, 0.0,
            TrendDirection.STABLE, 0.0, 5);

        List<AttentionItem> withVolatility = TemporalFocus.focus(
            List.of(entry), NOW, Map.of("e1", volatile_), CONFIG);
        List<AttentionItem> without = TemporalFocus.focus(
            List.of(entry), NOW, Map.of(), CONFIG);

        assertThat(withVolatility.get(0).salience()).isGreaterThan(without.get(0).salience());
        assertThat(withVolatility.get(0).reason()).contains("volatile");
    }

    @Test
    void ranker_producesComposableTemporalRanker() {
        MindMapNode node = stubNode("n1", "Event",
            NOW.plus(2, ChronoUnit.DAYS));
        TemporalEntry entry = new TemporalEntry(
            node.validFrom(), new TemporalSource.FromMindMap(node), TENANT, null);

        TemporalRanker ranker = TemporalFocus.ranker(Map.of(), CONFIG);
        double score = ranker.score(entry, NOW);

        assertThat(score).isGreaterThan(0).isLessThan(1);
    }

    @Test
    void emptyEntries_returnsEmptyList() {
        List<AttentionItem> items = TemporalFocus.focus(List.of(), NOW, Map.of(), CONFIG);
        assertThat(items).isEmpty();
    }

    @Test
    void customConfig_changesScoring() {
        MindMapNode node = stubNode("n1", "Event",
            NOW.plus(7, ChronoUnit.DAYS));
        TemporalEntry entry = new TemporalEntry(
            node.validFrom(), new TemporalSource.FromMindMap(node), TENANT, null);

        TemporalFocusConfig wideScale = new TemporalFocusConfig(30.0, 1.0, 0.5, 0.5, Map.of());
        TemporalFocusConfig narrowScale = new TemporalFocusConfig(1.0, 1.0, 0.5, 0.5, Map.of());

        List<AttentionItem> wide = TemporalFocus.focus(List.of(entry), NOW, Map.of(), wideScale);
        List<AttentionItem> narrow = TemporalFocus.focus(List.of(entry), NOW, Map.of(), narrowScale);

        // Wider scale = less decay = higher score for same distance
        assertThat(wide.get(0).salience()).isGreaterThan(narrow.get(0).salience());
    }

    private static MindMapNode stubNode(String id, String name, Instant validFrom) {
        return new MindMapNode() {
            @Override public String id() { return id; }
            @Override public String name() { return name; }
            @Override public String subgraphId() { return "sg1"; }
            @Override public io.casehub.neocortex.cognitive.Confidence confidence() { return null; }
            @Override public String provenance() { return "test"; }
            @Override public Instant createdAt() { return NOW; }
            @Override public Instant updatedAt() { return NOW; }
            @Override public Instant validFrom() { return validFrom; }
            @Override public Instant validUntil() { return null; }
            @Override public Set<String> traits() { return Set.of(); }
            @Override public Set<io.casehub.neocortex.mindmap.NodeRef> refs() { return Set.of(); }
            @Override public Double pleasure() { return null; }
            @Override public Double arousal() { return null; }
            @Override public Double dominance() { return null; }
            @Override public Optional<String> property(String key) { return Optional.empty(); }
            @Override public Map<String, String> properties() { return Map.of(); }
        };
    }

    private static Memory stubMemory(String memoryId, String entityId, Instant createdAt) {
        return new Memory(memoryId, entityId, AffectEvents.DOMAIN, TENANT,
            null, "test", Map.of(), createdAt, null, null, null, null);
    }
}

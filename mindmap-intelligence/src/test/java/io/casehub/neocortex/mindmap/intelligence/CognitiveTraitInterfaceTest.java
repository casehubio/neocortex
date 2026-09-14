package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.thing.Thing;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CognitiveTraitInterfaceTest {

    private Thing thing(Map<String, String> properties) {
        return new Thing() {
            @Override public String id() { return "n1"; }
            @Override public String name() { return "Test"; }
            @Override public String type() { return "cognitive"; }
            @Override public Optional<String> property(String key) {
                return Optional.ofNullable(properties.get(key));
            }
            @Override public Map<String, String> properties() { return properties; }
            @Override public Set<String> traits() { return Set.of(); }
        };
    }

    @Test
    void belieflike_returnsProperties() {
        var t = thing(Map.of("subject", "project success", "status", "active", "basis", "test results"));
        var b = t.as(Belieflike.class);
        assertThat(b.subject()).contains("project success");
        assertThat(b.status()).contains("active");
        assertThat(b.basis()).contains("test results");
    }

    @Test
    void belieflike_emptyWhenMissing() {
        var b = thing(Map.of()).as(Belieflike.class);
        assertThat(b.subject()).isEmpty();
        assertThat(b.status()).isEmpty();
        assertThat(b.basis()).isEmpty();
    }

    @Test
    void intentionlike_returnsProperties() {
        var t = thing(Map.of("goal", "ship feature", "status", "active", "priority", "high"));
        var i = t.as(Intentionlike.class);
        assertThat(i.goal()).contains("ship feature");
        assertThat(i.status()).contains("active");
        assertThat(i.priority()).contains("high");
    }

    @Test
    void predictive_returnsProperties() {
        var t = thing(Map.of("timeframe", "Q4 2026", "status", "pending", "basis", "market trends"));
        var p = t.as(Predictive.class);
        assertThat(p.timeframe()).contains("Q4 2026");
        assertThat(p.status()).contains("pending");
        assertThat(p.basis()).contains("market trends");
    }

    @Test
    void evaluative_returnsProperties() {
        var t = thing(Map.of("target", "Alice", "stance", "positive", "basis", "track record"));
        var e = t.as(Evaluative.class);
        assertThat(e.target()).contains("Alice");
        assertThat(e.stance()).contains("positive");
        assertThat(e.basis()).contains("track record");
    }

    @Test
    void fearlike_returnsProperties() {
        var t = thing(Map.of("threat", "server crash", "severity", "high", "status", "active"));
        var f = t.as(Fearlike.class);
        assertThat(f.threat()).contains("server crash");
        assertThat(f.severity()).contains("high");
        assertThat(f.status()).contains("active");
    }

    @Test
    void desirelike_returnsProperties() {
        var t = thing(Map.of("aspiration", "promotion", "status", "active", "urgency", "medium"));
        var d = t.as(Desirelike.class);
        assertThat(d.aspiration()).contains("promotion");
        assertThat(d.status()).contains("active");
        assertThat(d.urgency()).contains("medium");
    }
}

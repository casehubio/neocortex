package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.thing.Thing;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GoallikeTest {

    private Thing thing(Map<String, String> properties) {
        return new Thing() {
            @Override public String id() { return "n1"; }
            @Override public String name() { return "Test Goal"; }
            @Override public String type() { return "goal"; }
            @Override public Optional<String> property(String key) {
                return Optional.ofNullable(properties.get(key));
            }
            @Override public Map<String, String> properties() { return properties; }
            @Override public Set<String> traits() { return Set.of(); }
        };
    }

    @Test
    void allPropertiesReturned() {
        var t = thing(Map.of(
                "description", "find diamond",
                "status", "active",
                "horizon", "medium",
                "recognitionSource", "conversation",
                "resolution", "low",
                "urgency", "0.8",
                "feasibility", "0.6"));
        var g = t.as(Goallike.class);
        assertThat(g.description()).contains("find diamond");
        assertThat(g.status()).contains("active");
        assertThat(g.horizon()).contains("medium");
        assertThat(g.recognitionSource()).contains("conversation");
        assertThat(g.resolution()).contains("low");
        assertThat(g.urgency()).contains("0.8");
        assertThat(g.feasibility()).contains("0.6");
    }

    @Test
    void emptyWhenPropertiesMissing() {
        var g = thing(Map.of()).as(Goallike.class);
        assertThat(g.description()).isEmpty();
        assertThat(g.status()).isEmpty();
        assertThat(g.horizon()).isEmpty();
        assertThat(g.recognitionSource()).isEmpty();
        assertThat(g.resolution()).isEmpty();
        assertThat(g.urgency()).isEmpty();
        assertThat(g.feasibility()).isEmpty();
    }

    @Test
    void partialProperties() {
        var g = thing(Map.of("description", "learn quantum computing", "status", "active"))
                .as(Goallike.class);
        assertThat(g.description()).contains("learn quantum computing");
        assertThat(g.status()).contains("active");
        assertThat(g.horizon()).isEmpty();
        assertThat(g.urgency()).isEmpty();
    }

    @Test
    void contentDomainReturnsValue() {
        var g = thing(Map.of("contentDomain", "mastery-and-competence")).as(Goallike.class);
        assertThat(g.contentDomain()).contains("mastery-and-competence");
    }

    @Test
    void contentSubdomainReturnsValue() {
        var g = thing(Map.of("contentSubdomain", "achievement")).as(Goallike.class);
        assertThat(g.contentSubdomain()).contains("achievement");
    }

    @Test
    void motivationOriginReturnsValue() {
        var g = thing(Map.of("motivationOrigin", "intrinsic")).as(Goallike.class);
        assertThat(g.motivationOrigin()).contains("intrinsic");
    }

    @Test
    void clarityReturnsStringValue() {
        var g = thing(Map.of("clarity", "0.8")).as(Goallike.class);
        assertThat(g.clarity()).contains("0.8");
    }

    @Test
    void evidenceBasisReturnsValue() {
        var g = thing(Map.of("evidenceBasis", "Pattern of daily practice across §4-§8")).as(Goallike.class);
        assertThat(g.evidenceBasis()).contains("Pattern of daily practice across §4-§8");
    }

}

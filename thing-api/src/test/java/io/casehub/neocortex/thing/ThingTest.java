package io.casehub.neocortex.thing;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThingTest {

    interface Personable {
        Optional<String> birthday();
        Optional<String> role();
    }

    interface Measurable {
        int count();
        double score();
        boolean active();
        Long bigNumber();
    }

    private Thing thing(Map<String, String> properties, Set<String> traits) {
        return new Thing() {
            @Override public String id() { return "t1"; }
            @Override public String name() { return "Alice"; }
            @Override public String type() { return "person"; }
            @Override public Optional<String> property(String key) {
                return Optional.ofNullable(properties.get(key));
            }
            @Override public Map<String, String> properties() { return properties; }
            @Override public Set<String> traits() { return traits; }
        };
    }

    @Test
    void is_returnsTrueForPresentTrait() {
        Thing t = thing(Map.of(), Set.of("Personable"));
        assertThat(t.is("Personable")).isTrue();
    }

    @Test
    void is_returnsFalseForAbsentTrait() {
        Thing t = thing(Map.of(), Set.of("Personable"));
        assertThat(t.is("Projectlike")).isFalse();
    }

    @Test
    void is_isCaseSensitive() {
        Thing t = thing(Map.of(), Set.of("Personable"));
        assertThat(t.is("personable")).isFalse();
    }

    @Test
    void as_returnsProxyWithPropertyAccess() {
        Thing t = thing(Map.of("birthday", "1990-01-15", "role", "engineer"),
                        Set.of("Personable"));
        Personable p = t.as(Personable.class);
        assertThat(p.birthday()).contains("1990-01-15");
        assertThat(p.role()).contains("engineer");
    }

    @Test
    void as_returnsEmptyOptionalForMissingProperty() {
        Thing t = thing(Map.of(), Set.of());
        Personable p = t.as(Personable.class);
        assertThat(p.birthday()).isEmpty();
    }

    @Test
    void as_coercesPrimitiveTypes() {
        Thing t = thing(Map.of("count", "42", "score", "3.14", "active", "true"),
                        Set.of());
        Measurable m = t.as(Measurable.class);
        assertThat(m.count()).isEqualTo(42);
        assertThat(m.score()).isEqualTo(3.14);
        assertThat(m.active()).isTrue();
    }

    @Test
    void as_returnsDefaultsForMissingPrimitives() {
        Thing t = thing(Map.of(), Set.of());
        Measurable m = t.as(Measurable.class);
        assertThat(m.count()).isZero();
        assertThat(m.score()).isZero();
        assertThat(m.active()).isFalse();
    }

    @Test
    void as_returnsNullForMissingBoxedTypes() {
        Thing t = thing(Map.of(), Set.of());
        Measurable m = t.as(Measurable.class);
        assertThat(m.bigNumber()).isNull();
    }

    @Test
    void as_rejectsNonInterface() {
        Thing t = thing(Map.of(), Set.of());
        assertThatThrownBy(() -> t.as(String.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Trait must be an interface");
    }

    @Test
    void as_toStringIncludesIdAndTraitName() {
        Thing t = thing(Map.of(), Set.of());
        Personable p = t.as(Personable.class);
        assertThat(p.toString()).isEqualTo("Personable[t1]");
    }

    @Test
    void as_equalsComparesIdAndTraitClass() {
        Thing t1 = thing(Map.of(), Set.of());
        Personable p1 = t1.as(Personable.class);
        Personable p2 = t1.as(Personable.class);
        assertThat(p1).isEqualTo(p2);
    }
}

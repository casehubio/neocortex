package io.casehub.neocortex.cognitive.index;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemporalQueryTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Instant HOUR_AGO = NOW.minusSeconds(3600);

    @Test
    void since_createsQueryWithFromAndAllSources() {
        var query = TemporalQuery.since(List.of("t1"), HOUR_AGO, 50);
        assertThat(query.from()).isEqualTo(HOUR_AGO);
        assertThat(query.to()).isNull();
        assertThat(query.limit()).isEqualTo(50);
        assertThat(query.sources()).isEqualTo(EnumSet.allOf(TemporalQuery.StoreKind.class));
        assertThat(query.entityIds()).isEmpty();
    }

    @Test
    void window_setsFromAndTo() {
        var query = TemporalQuery.window(List.of("t1"), HOUR_AGO, NOW, 20);
        assertThat(query.from()).isEqualTo(HOUR_AGO);
        assertThat(query.to()).isEqualTo(NOW);
    }

    @Test
    void upcoming_queriesOnlyMindMap() {
        var query = TemporalQuery.upcoming(List.of("t1"), NOW, 10);
        assertThat(query.sources()).isEqualTo(EnumSet.of(TemporalQuery.StoreKind.MINDMAP));
        assertThat(query.from()).isEqualTo(NOW);
    }

    @Test
    void emptyTenantIds_throws() {
        assertThatThrownBy(() -> TemporalQuery.since(List.of(), HOUR_AGO, 10))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tenantId");
    }

    @Test
    void nonPositiveLimit_throws() {
        assertThatThrownBy(() -> TemporalQuery.since(List.of("t1"), HOUR_AGO, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("limit");
    }

    @Test
    void nullSources_defaultsToAll() {
        var query = new TemporalQuery(List.of("t1"), HOUR_AGO, null, 10, null, List.of(), false);
        assertThat(query.sources()).isEqualTo(EnumSet.allOf(TemporalQuery.StoreKind.class));
    }

    @Test
    void withSources_replacesSourceSet() {
        var query = TemporalQuery.since(List.of("t1"), HOUR_AGO, 10)
            .withSources(Set.of(TemporalQuery.StoreKind.CBR));
        assertThat(query.sources()).containsExactly(TemporalQuery.StoreKind.CBR);
    }

    @Test
    void withEntityIds_setsEntityIds() {
        var query = TemporalQuery.since(List.of("t1"), HOUR_AGO, 10)
            .withEntityIds(List.of("agent-1"));
        assertThat(query.entityIds()).containsExactly("agent-1");
    }

    @Test
    void tenantIds_defensiveCopy() {
        var tenants = new java.util.ArrayList<>(List.of("t1", "t2"));
        var query = TemporalQuery.since(tenants, HOUR_AGO, 10);
        tenants.add("t3");
        assertThat(query.tenantIds()).hasSize(2);
    }
}

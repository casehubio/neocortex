package io.casehub.neocortex.mindmap;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LifecycleResolverTest {

    @Test
    void person_bornAndDied() {
        Map<String, String> props = Map.of(
                "lifecycle.born", "1907-07-06",
                "lifecycle.died", "1954-07-13"
        );
        var result = LifecycleResolver.resolve(props, SubgraphTypes.PERSON);
        assertThat(result.validFrom()).isEqualTo(
                LocalDate.of(1907, 7, 6).atStartOfDay(ZoneOffset.UTC).toInstant());
        assertThat(result.validUntil()).isEqualTo(
                LocalDate.of(1954, 7, 13).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Test
    void person_bornOnly() {
        Map<String, String> props = Map.of("lifecycle.born", "1907-07-06");
        var result = LifecycleResolver.resolve(props, SubgraphTypes.PERSON);
        assertThat(result.validFrom()).isNotNull();
        assertThat(result.validUntil()).isNull();
    }

    @Test
    void person_diedOnly() {
        Map<String, String> props = Map.of("lifecycle.died", "1954-07-13");
        var result = LifecycleResolver.resolve(props, SubgraphTypes.PERSON);
        assertThat(result.validFrom()).isNull();
        assertThat(result.validUntil()).isNotNull();
    }

    @Test
    void project_completedOnly() {
        Map<String, String> props = Map.of("lifecycle.completed", "1939");
        var result = LifecycleResolver.resolve(props, SubgraphTypes.PROJECT);
        assertThat(result.validFrom()).isEqualTo(
                Year.of(1939).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        assertThat(result.validUntil()).isNull();
    }

    @Test
    void project_conceivedsBeforeCompleted() {
        Map<String, String> props = Map.of(
                "lifecycle.conceived", "1938",
                "lifecycle.completed", "1939"
        );
        var result = LifecycleResolver.resolve(props, SubgraphTypes.PROJECT);
        assertThat(result.validFrom()).isEqualTo(
                Year.of(1938).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Test
    void project_destroyed() {
        Map<String, String> props = Map.of(
                "lifecycle.completed", "1939",
                "lifecycle.destroyed", "1945"
        );
        var result = LifecycleResolver.resolve(props, SubgraphTypes.PROJECT);
        assertThat(result.validUntil()).isEqualTo(
                Year.of(1946).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Test
    void organisation_foundedAndDissolved() {
        Map<String, String> props = Map.of(
                "lifecycle.founded", "1910",
                "lifecycle.dissolved", "1950-06"
        );
        var result = LifecycleResolver.resolve(props, SubgraphTypes.ORGANISATION);
        assertThat(result.validFrom()).isNotNull();
        assertThat(result.validUntil()).isNotNull();
    }

    @Test
    void place_foundedAndDemolished() {
        Map<String, String> props = Map.of(
                "lifecycle.founded", "1929",
                "lifecycle.demolished", "1985"
        );
        var result = LifecycleResolver.resolve(props, SubgraphTypes.PLACE);
        assertThat(result.validFrom()).isNotNull();
        assertThat(result.validUntil()).isNotNull();
    }

    @Test
    void noLifecycleProperties_returnsNulls() {
        Map<String, String> props = Map.of("name", "Frida");
        var result = LifecycleResolver.resolve(props, SubgraphTypes.PERSON);
        assertThat(result.validFrom()).isNull();
        assertThat(result.validUntil()).isNull();
    }

    @Test
    void emptyProperties_returnsNulls() {
        var result = LifecycleResolver.resolve(Map.of(), SubgraphTypes.PERSON);
        assertThat(result.validFrom()).isNull();
        assertThat(result.validUntil()).isNull();
    }

    @Test
    void unknownSubgraphType_returnsNulls() {
        Map<String, String> props = Map.of("lifecycle.born", "1907");
        var result = LifecycleResolver.resolve(props, "unknown");
        assertThat(result.validFrom()).isNull();
        assertThat(result.validUntil()).isNull();
    }

    @Test
    void circa_datesStillResolve() {
        Map<String, String> props = Map.of("lifecycle.born", "1907~");
        var result = LifecycleResolver.resolve(props, SubgraphTypes.PERSON);
        assertThat(result.validFrom()).isEqualTo(
                Year.of(1907).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Test
    void firstExistence_picksEarliest() {
        Map<String, String> props = Map.of(
                "lifecycle.started", "1939",
                "lifecycle.conceived", "1938-06"
        );
        var result = LifecycleResolver.resolve(props, SubgraphTypes.PROJECT);
        assertThat(result.validFrom()).isEqualTo(
                LocalDate.of(1938, 6, 1).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Test
    void activity_occurredAndEnded() {
        Map<String, String> props = Map.of(
                "lifecycle.occurred", "1938-11",
                "lifecycle.ended", "1938-12"
        );
        var result = LifecycleResolver.resolve(props, SubgraphTypes.ACTIVITY);
        assertThat(result.validFrom()).isNotNull();
        assertThat(result.validUntil()).isNotNull();
    }

    @Test
    void concept_coined() {
        Map<String, String> props = Map.of("lifecycle.coined", "1925");
        var result = LifecycleResolver.resolve(props, SubgraphTypes.CONCEPT);
        assertThat(result.validFrom()).isNotNull();
        assertThat(result.validUntil()).isNull();
    }
}

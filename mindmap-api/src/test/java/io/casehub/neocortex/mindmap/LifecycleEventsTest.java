package io.casehub.neocortex.mindmap;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LifecycleEventsTest {

    @Test
    void prefix_constant() {
        assertThat(LifecycleEvents.PREFIX).isEqualTo("lifecycle.");
    }

    @Test
    void key_prefixes() {
        assertThat(LifecycleEvents.key("born")).isEqualTo("lifecycle.born");
        assertThat(LifecycleEvents.key("first-exhibited")).isEqualTo("lifecycle.first-exhibited");
    }

    @Test
    void standardLabels_person() {
        List<String> labels = LifecycleEvents.standardLabels(SubgraphTypes.PERSON);
        assertThat(labels).contains("born", "died");
    }

    @Test
    void standardLabels_project() {
        List<String> labels = LifecycleEvents.standardLabels(SubgraphTypes.PROJECT);
        assertThat(labels).contains("conceived", "started", "completed", "first-exhibited", "published", "sold", "destroyed", "lost");
    }

    @Test
    void standardLabels_place() {
        List<String> labels = LifecycleEvents.standardLabels(SubgraphTypes.PLACE);
        assertThat(labels).contains("founded", "built", "renamed", "demolished", "opened-as-museum");
    }

    @Test
    void standardLabels_organisation() {
        List<String> labels = LifecycleEvents.standardLabels(SubgraphTypes.ORGANISATION);
        assertThat(labels).contains("founded", "merged", "dissolved");
    }

    @Test
    void standardLabels_activity() {
        List<String> labels = LifecycleEvents.standardLabels(SubgraphTypes.ACTIVITY);
        assertThat(labels).contains("occurred", "started", "ended");
    }

    @Test
    void standardLabels_concept() {
        List<String> labels = LifecycleEvents.standardLabels(SubgraphTypes.CONCEPT);
        assertThat(labels).contains("coined", "popularised");
    }

    @Test
    void standardLabels_unknownType_returnsEmpty() {
        assertThat(LifecycleEvents.standardLabels("unknown-type")).isEmpty();
    }

    @Test
    void extractLifecycleEvents_filtersAndStripsPrefix() {
        Map<String, String> properties = Map.of(
                "lifecycle.born", "1907-07-06",
                "lifecycle.died", "1954-07-13",
                "some-other-prop", "value",
                "lifecycle.first-exhibited", "1938-11"
        );
        Map<String, String> events = LifecycleEvents.extract(properties);
        assertThat(events).hasSize(3)
                .containsEntry("born", "1907-07-06")
                .containsEntry("died", "1954-07-13")
                .containsEntry("first-exhibited", "1938-11");
    }

    @Test
    void extractLifecycleEvents_emptyProperties() {
        assertThat(LifecycleEvents.extract(Map.of())).isEmpty();
    }

    @Test
    void extractLifecycleEvents_noLifecycleKeys() {
        Map<String, String> props = Map.of("name", "Frida", "type", "person");
        assertThat(LifecycleEvents.extract(props)).isEmpty();
    }

    @Test
    void firstExistenceLabel_person() {
        assertThat(LifecycleEvents.firstExistenceLabels(SubgraphTypes.PERSON))
                .containsExactly("born");
    }

    @Test
    void lastExistenceLabel_person() {
        assertThat(LifecycleEvents.lastExistenceLabels(SubgraphTypes.PERSON))
                .containsExactly("died");
    }

    @Test
    void firstExistenceLabel_project() {
        assertThat(LifecycleEvents.firstExistenceLabels(SubgraphTypes.PROJECT))
                .containsExactly("conceived", "started", "completed");
    }

    @Test
    void lastExistenceLabel_project() {
        assertThat(LifecycleEvents.lastExistenceLabels(SubgraphTypes.PROJECT))
                .containsExactly("destroyed", "lost");
    }

    @Test
    void firstExistenceLabel_unknownType() {
        assertThat(LifecycleEvents.firstExistenceLabels("unknown")).isEmpty();
    }

    @Test
    void withLifecycleEvent_addsToProperties() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("name", "Frida");
        Map<String, String> result = LifecycleEvents.withEvent(props, "born", "1907-07-06");
        assertThat(result).containsEntry("lifecycle.born", "1907-07-06")
                .containsEntry("name", "Frida");
    }
}

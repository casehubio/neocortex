package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractionResultTest {

    @Test
    void emptyResult() {
        ExtractionResult result = ExtractionResult.EMPTY;
        assertThat(result.entities()).isEmpty();
        assertThat(result.relationships()).isEmpty();
        assertThat(result.contradictions()).isEmpty();
        assertThat(result.entityNames()).isEmpty();
    }

    @Test
    void resultWithEntitiesAndRelationships() {
        var entity = new ExtractedEntity("node-1", "Alice", true, "PERSON",
            Map.of("role", "engineer"));
        var rel = new ExtractedRelationship("edge-1", "Alice", "Acme",
            "works-at", ConfidenceOrigin.STATED);
        var contradiction = new Contradiction("Alice", "works-at",
            "OldCorp", "Acme", "Alice changed jobs");

        var result = new ExtractionResult(
            List.of(entity), List.of(rel),
            List.of(contradiction), List.of("Alice", "Acme"));

        assertThat(result.entities()).hasSize(1);
        assertThat(result.entities().get(0).name()).isEqualTo("Alice");
        assertThat(result.entities().get(0).created()).isTrue();
        assertThat(result.relationships()).hasSize(1);
        assertThat(result.relationships().get(0).edgeType()).isEqualTo("works-at");
        assertThat(result.contradictions()).hasSize(1);
        assertThat(result.entityNames()).containsExactly("Alice", "Acme");
    }

    @Test
    void extractedEntityProperties() {
        var entity = new ExtractedEntity("n1", "Bob", false, "PERSON",
            Map.of("email", "bob@example.com", "role", "manager"));
        assertThat(entity.nodeId()).isEqualTo("n1");
        assertThat(entity.created()).isFalse();
        assertThat(entity.properties()).containsEntry("email", "bob@example.com");
    }
}

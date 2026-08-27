package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.ConfidenceOrigin;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractionJsonParserTest {

    @Test
    void parseValidJson() {
        String json = """
            {
              "entities": [
                {"name": "Alice", "type": "PERSON", "properties": {"role": "engineer"}, "confidence": "STATED"}
              ],
              "relationships": [
                {"source": "Alice", "target": "Acme", "type": "works-at", "confidence": "STATED"}
              ],
              "contradictions": [
                {"entity": "Alice", "property": "works-at", "existing": "OldCorp", "extracted": "Acme", "explanation": "Changed jobs"}
              ]
            }
            """;
        ParsedExtraction result = ExtractionJsonParser.parse(json);
        assertThat(result).isNotNull();
        assertThat(result.entities()).hasSize(1);
        assertThat(result.entities().get(0).name()).isEqualTo("Alice");
        assertThat(result.entities().get(0).type()).isEqualTo("PERSON");
        assertThat(result.entities().get(0).properties()).containsEntry("role", "engineer");
        assertThat(result.entities().get(0).confidence()).isEqualTo(ConfidenceOrigin.STATED);
        assertThat(result.relationships()).hasSize(1);
        assertThat(result.relationships().get(0).source()).isEqualTo("Alice");
        assertThat(result.relationships().get(0).type()).isEqualTo("works-at");
        assertThat(result.contradictions()).hasSize(1);
        assertThat(result.contradictions().get(0).entity()).isEqualTo("Alice");
    }

    @Test
    void parseMarkdownWrappedJson() {
        String wrapped = """
            ```json
            {"entities": [{"name": "Bob", "type": "PERSON", "properties": {}, "confidence": "INFERRED"}], "relationships": [], "contradictions": []}
            ```
            """;
        ParsedExtraction result = ExtractionJsonParser.parse(wrapped);
        assertThat(result).isNotNull();
        assertThat(result.entities()).hasSize(1);
        assertThat(result.entities().get(0).name()).isEqualTo("Bob");
    }

    @Test
    void parseJsonWithTrailingText() {
        String withTrailing = """
            {"entities": [], "relationships": [], "contradictions": []}
            Here is some additional explanation the LLM added.
            """;
        ParsedExtraction result = ExtractionJsonParser.parse(withTrailing);
        assertThat(result).isNotNull();
        assertThat(result.entities()).isEmpty();
    }

    @Test
    void parseMissingFieldsDefaultsToEmpty() {
        String minimal = """
            {"entities": [{"name": "Alice", "type": "PERSON", "properties": {}, "confidence": "STATED"}]}
            """;
        ParsedExtraction result = ExtractionJsonParser.parse(minimal);
        assertThat(result).isNotNull();
        assertThat(result.entities()).hasSize(1);
        assertThat(result.relationships()).isEmpty();
        assertThat(result.contradictions()).isEmpty();
    }

    @Test
    void parseEmptyStringReturnsNull() {
        assertThat(ExtractionJsonParser.parse("")).isNull();
        assertThat(ExtractionJsonParser.parse(null)).isNull();
    }

    @Test
    void parseTotalGarbageReturnsNull() {
        assertThat(ExtractionJsonParser.parse("not json at all")).isNull();
    }

    @Test
    void parseUnknownConfidenceDefaultsToInferred() {
        String json = """
            {"entities": [{"name": "X", "type": "CONCEPT", "properties": {}, "confidence": "STRONG"}], "relationships": [], "contradictions": []}
            """;
        ParsedExtraction result = ExtractionJsonParser.parse(json);
        assertThat(result).isNotNull();
        assertThat(result.entities().get(0).confidence()).isEqualTo(ConfidenceOrigin.INFERRED);
    }

    @Test
    void parseEntityWithoutPropertiesDefaultsToEmptyMap() {
        String json = """
            {"entities": [{"name": "X", "type": "CONCEPT", "confidence": "STATED"}], "relationships": [], "contradictions": []}
            """;
        ParsedExtraction result = ExtractionJsonParser.parse(json);
        assertThat(result).isNotNull();
        assertThat(result.entities().get(0).properties()).isEmpty();
    }

    @Test
    void parseMultipleEntitiesAndRelationships() {
        String json = """
            {
              "entities": [
                {"name": "Alice", "type": "PERSON", "properties": {}, "confidence": "STATED"},
                {"name": "Bob", "type": "PERSON", "properties": {"role": "manager"}, "confidence": "STATED"},
                {"name": "Acme", "type": "ORGANISATION", "properties": {}, "confidence": "INFERRED"}
              ],
              "relationships": [
                {"source": "Alice", "target": "Acme", "type": "works-at", "confidence": "STATED"},
                {"source": "Bob", "target": "Acme", "type": "works-at", "confidence": "STATED"},
                {"source": "Bob", "target": "Alice", "type": "manages", "confidence": "INFERRED"}
              ],
              "contradictions": []
            }
            """;
        ParsedExtraction result = ExtractionJsonParser.parse(json);
        assertThat(result).isNotNull();
        assertThat(result.entities()).hasSize(3);
        assertThat(result.relationships()).hasSize(3);
        assertThat(result.contradictions()).isEmpty();
    }

    @Test
    void parseStringWithEscapedQuotes() {
        String json = """
            {"entities": [{"name": "O\\"Brien", "type": "PERSON", "properties": {}, "confidence": "STATED"}], "relationships": [], "contradictions": []}
            """;
        ParsedExtraction result = ExtractionJsonParser.parse(json);
        assertThat(result).isNotNull();
        assertThat(result.entities().get(0).name()).isEqualTo("O\"Brien");
    }
}

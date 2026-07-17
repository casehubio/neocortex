package io.casehub.neocortex.memory.cbr.qdrant;

import io.casehub.neocortex.fusion.FusionStrategy;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrFeatureSchema;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.FeatureField;
import io.casehub.neocortex.memory.cbr.RetrievalMode;
import io.qdrant.client.grpc.Common.Condition;
import io.qdrant.client.grpc.Common.Filter;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static io.casehub.neocortex.memory.cbr.FeatureValue.number;
import static io.casehub.neocortex.memory.cbr.FeatureValue.range;
import static io.casehub.neocortex.memory.cbr.FeatureValue.string;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CbrQueryTranslatorTest {

    private static final MemoryDomain CBR = new MemoryDomain("cbr");

    private final CbrFeatureSchema schema = CbrFeatureSchema.of("starcraft-game",
        FeatureField.categorical("opponent_race"),
        FeatureField.numeric("army_size_ratio", 0.0, 3.0),
        FeatureField.text("notes"));

    @Test
    void validateQueryFeatures_categoricalRequiresString() {
        assertThatThrownBy(() -> CbrQueryTranslator.validateQueryFeatures(
            Map.of("opponent_race", number(42)), schema))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Categorical")
            .hasMessageContaining("String");
    }

    @Test
    void validateQueryFeatures_numericRequiresNumber() {
        assertThatThrownBy(() -> CbrQueryTranslator.validateQueryFeatures(
            Map.of("army_size_ratio", string("high")), schema))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Numeric")
            .hasMessageContaining("Number");
    }

    @Test
    void validateQueryFeatures_textRequiresString() {
        assertThatThrownBy(() -> CbrQueryTranslator.validateQueryFeatures(
            Map.of("notes", number(123)), schema))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Text")
            .hasMessageContaining("String");
    }

    @Test
    void validateQueryFeatures_unknownFieldsDoNotThrow() {
        // Should not throw
        CbrQueryTranslator.validateQueryFeatures(
            Map.of("totally_unknown", string("anything")), schema);
    }

    @Test
    void validateQueryFeatures_numericAcceptsNumericRange() {
        CbrQueryTranslator.validateQueryFeatures(
            Map.of("army_size_ratio", range(0.5, 1.0)), schema);
    }

    @Test
    void toIdentityFilter_excludesFeatures() {
        var query = CbrQuery.of("tenant-1", CBR, io.casehub.platform.api.path.Path.root(), "starcraft-game",
            Map.of("opponent_race", string("Zerg"), "army_size_ratio", number(0.7)), 5);
        Filter filter = CbrQueryTranslator.toIdentityFilter(query);

        // 3 identity + 1 scope must + 1 supersession must_not — no feature filters
        assertThat(filter.getMustCount()).isEqualTo(4);
        assertKeywordCondition(filter.getMust(0), "tenantId", "tenant-1");
        assertKeywordCondition(filter.getMust(1), "domain", "cbr");
        assertKeywordCondition(filter.getMust(2), "caseType", "starcraft-game");
        assertThat(filter.getMustNotCount()).isEqualTo(1);
        assertThat(filter.getMustNot(0).getField().getKey()).isEqualTo("_superseded_at");
    }

    @Test
    void toIdentityFilter_includesNotBefore() {
        Instant notBefore = Instant.parse("2025-01-01T00:00:00Z");
        var query = new CbrQuery("tenant-1", CBR, "starcraft-game",
            Map.of("opponent_race", string("Zerg")), Map.of(), Map.of(), 5, 0.0, notBefore, null, 0.5,
            RetrievalMode.HYBRID, FusionStrategy.RRF, null, io.casehub.platform.api.path.Path.root(), null);
        Filter filter = CbrQueryTranslator.toIdentityFilter(query);

        // 3 identity + 1 scope + 1 notBefore (supersession is must_not)
        assertThat(filter.getMustCount()).isEqualTo(5);
        Condition storedAtCondition = filter.getMust(4);
        assertThat(storedAtCondition.getField().getKey()).isEqualTo("_stored_at");
    }

    private void assertKeywordCondition(Condition condition, String expectedField, String expectedValue) {
        assertThat(condition.getField().getKey()).isEqualTo(expectedField);
        assertThat(condition.getField().getMatch().getKeyword()).isEqualTo(expectedValue);
    }
}

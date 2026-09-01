/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.neocortex.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import io.casehub.neocortex.cognitive.TemporalMark;
import io.casehub.neocortex.memory.cbr.CbrFilter;
import io.casehub.neocortex.memory.cbr.FeatureField;
import io.casehub.neocortex.memory.cbr.SimilaritySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SealedHierarchyModuleTest {

    private SchemaGenerator generator(Map<Class<?>, Map<Class<?>, String>> overrides) {
        var builder = new SchemaGeneratorConfigBuilder(
            SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON);
        builder.with(Option.DEFINITIONS_FOR_ALL_OBJECTS);
        builder.with(new SealedHierarchyModule(overrides));
        return new SchemaGenerator(builder.build());
    }

    private SchemaGenerator generator() {
        return generator(Map.of());
    }

    private List<String> extractDiscriminatorValues(JsonNode schema) {
        var values = new ArrayList<String>();
        var oneOf = schema.get("oneOf");
        if (oneOf != null) {
            for (var entry : oneOf) {
                var props = entry.get("properties");
                if (props != null && props.has("type")) {
                    var constNode = props.get("type").get("const");
                    if (constNode != null) {
                        values.add(constNode.asText());
                    }
                }
            }
        }
        return values;
    }

    @Test
    void sealedInterface_generatesOneOf_withTypeDiscriminator() {
        var schema = generator().generateSchema(TemporalMark.class);

        assertThat(schema.has("oneOf")).isTrue();
        assertThat(schema.get("oneOf").size()).isEqualTo(3);
    }

    @Test
    void discriminatorValues_areLowerCamelCase_byDefault() {
        var schema = generator().generateSchema(TemporalMark.class);

        var values = extractDiscriminatorValues(schema);
        assertThat(values).containsExactlyInAnyOrder(
            "wallClock", "relative", "ordinal");
    }

    @Test
    void discriminatorOverrides_replaceDefaultValues() {
        var overrides = Map.<Class<?>, Map<Class<?>, String>>of(
            TemporalMark.class, Map.of(
                TemporalMark.WallClock.class, "wall-clock"
            )
        );
        var schema = generator(overrides).generateSchema(TemporalMark.class);

        var values = extractDiscriminatorValues(schema);
        assertThat(values).contains("wall-clock");
        assertThat(values).doesNotContain("wallClock");
    }

    @Test
    void nonSealedType_isNotIntercepted() {
        var schema = generator().generateSchema(String.class);
        assertThat(schema.has("oneOf")).isFalse();
    }

    @Test
    void featureField_generates9Variants() {
        var schema = generator().generateSchema(FeatureField.class);

        assertThat(schema.has("oneOf")).isTrue();
        assertThat(schema.get("oneOf").size()).isEqualTo(9);
    }

    @Test
    void cbrFilter_generates8Variants() {
        var schema = generator().generateSchema(CbrFilter.class);

        assertThat(schema.has("oneOf")).isTrue();
        assertThat(schema.get("oneOf").size()).isEqualTo(8);
    }

    @Test
    void similaritySpec_generates6Variants() {
        var schema = generator().generateSchema(SimilaritySpec.class);

        assertThat(schema.has("oneOf")).isTrue();
        assertThat(schema.get("oneOf").size()).isEqualTo(6);
    }

    @Test
    void eachOneOfEntry_hasDiscriminatorAndRef() {
        var schema = generator().generateSchema(TemporalMark.class);

        for (var entry : schema.get("oneOf")) {
            assertThat(entry.has("properties")).isTrue();
            assertThat(entry.get("properties").has("type")).isTrue();
            assertThat(entry.get("properties").get("type").has("const")).isTrue();
            assertThat(entry.has("required")).isTrue();
            assertThat(entry.has("$ref")).isTrue();
        }
    }

    @Test
    void defaultDiscriminator_lowerCamelCase() {
        assertThat(SealedHierarchyModule.defaultDiscriminator(
            TemporalMark.WallClock.class)).isEqualTo("wallClock");
        assertThat(SealedHierarchyModule.defaultDiscriminator(
            TemporalMark.Relative.class)).isEqualTo("relative");
        assertThat(SealedHierarchyModule.defaultDiscriminator(
            TemporalMark.Ordinal.class)).isEqualTo("ordinal");
    }
}

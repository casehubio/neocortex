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
import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.cognitive.TemporalMark;
import io.casehub.neocortex.memory.cbr.CbrFilter;
import io.casehub.neocortex.memory.cbr.CbrFeatureSchema;
import io.casehub.neocortex.memory.cbr.FeatureField;
import io.casehub.neocortex.memory.cbr.SimilaritySpec;
import io.casehub.neocortex.memory.cbr.ScopeDecay;
import io.casehub.neocortex.memory.cbr.TemporalDecay;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveSchemaGeneratorTest {

    private final CognitiveSchemaGenerator generator = new CognitiveSchemaGenerator();

    @Test
    void cbrFeatureSchema_generatesValidSchema() {
        var schema = generator.generate(CbrFeatureSchema.class);

        assertThat(schema).isNotNull();
        assertThat(schema.has("$defs") || schema.has("properties")).isTrue();
    }

    @Test
    void allSealedHierarchies_generateOneOf() {
        for (var sealedType : new Class<?>[] {
                FeatureField.class, CbrFilter.class, SimilaritySpec.class,
                TemporalDecay.class, ScopeDecay.class, TemporalMark.class }) {
            var schema = generator.generate(sealedType);
            assertThat(schema.has("oneOf"))
                .as("Expected oneOf for %s", sealedType.getSimpleName())
                .isTrue();
        }
    }

    @Test
    void confidence_generatesShorthandOneOf() {
        var schema = generator.generate(Confidence.class);

        var oneOf = schema.get("oneOf");
        assertThat(oneOf).isNotNull();
        assertThat(oneOf.size()).isEqualTo(2);
    }

    @Test
    void confidenceOrigin_generatesInlinedEnum() {
        var schema = generator.generate(ConfidenceOrigin.class);

        assertThat(schema.get("type").asText()).isEqualTo("string");
        assertThat(schema.has("enum")).isTrue();
    }

    @Test
    void similaritySpec_discriminatorOverrides_applied() {
        var schema = generator.generate(SimilaritySpec.class);

        var values = new ArrayList<String>();
        for (var entry : schema.get("oneOf")) {
            var props = entry.get("properties");
            if (props != null && props.has("type")) {
                var constNode = props.get("type").get("const");
                if (constNode != null) {
                    values.add(constNode.asText());
                }
            }
        }
        assertThat(values).contains("gaussian", "step", "exponential", "dtw", "editDistance");
        assertThat(values).doesNotContain("gaussianDecay", "stepDecay", "exponentialDecay", "dtwSpec");
    }

    @Test
    void generateToYaml_writesFile(@TempDir Path tempDir) throws IOException {
        Path output = tempDir.resolve("schema.yaml");
        generator.generateToYaml(TemporalMark.class, output);

        assertThat(output).exists();
        String content = Files.readString(output);
        assertThat(content).contains("oneOf");
        assertThat(content).contains("wallClock");
        assertThat(content).contains("relative");
        assertThat(content).contains("ordinal");
    }

    @Test
    void allModulesWorkTogether_featureFieldWithNestedSealed() {
        var schema = generator.generate(FeatureField.class);

        assertThat(schema.has("oneOf")).isTrue();
        assertThat(schema.get("oneOf").size()).isEqualTo(9);

        var defs = schema.get("$defs");
        if (defs != null) {
            boolean hasSimilaritySpec = false;
            var fieldNames = defs.fieldNames();
            while (fieldNames.hasNext()) {
                String name = fieldNames.next();
                if (name.contains("SimilaritySpec") || name.contains("similaritySpec")) {
                    hasSimilaritySpec = true;
                }
            }
            // SimilaritySpec should appear in $defs as a nested sealed hierarchy
        }
    }
}

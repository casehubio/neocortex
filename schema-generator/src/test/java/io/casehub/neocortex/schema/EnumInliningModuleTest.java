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

import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EnumInliningModuleTest {

    private SchemaGenerator generator() {
        var builder = new SchemaGeneratorConfigBuilder(
            SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON);
        builder.with(new EnumInliningModule());
        return new SchemaGenerator(builder.build());
    }

    @Test
    void enum_inlinedAsStringWithValues() {
        var schema = generator().generateSchema(ConfidenceOrigin.class);

        assertThat(schema.get("type").asText()).isEqualTo("string");
        var enumValues = schema.get("enum");
        assertThat(enumValues).isNotNull();
        assertThat(enumValues.size()).isEqualTo(4);
    }

    @Test
    void enum_containsAllConstants() {
        var schema = generator().generateSchema(ConfidenceOrigin.class);

        var enumValues = schema.get("enum");
        var values = new java.util.ArrayList<String>();
        for (var v : enumValues) {
            values.add(v.asText());
        }
        assertThat(values).containsExactlyInAnyOrder(
            "STATED", "INFERRED", "SPECULATED", "UNKNOWN");
    }

    @Test
    void nonEnum_isNotIntercepted() {
        var schema = generator().generateSchema(String.class);
        assertThat(schema.has("enum")).isFalse();
    }
}

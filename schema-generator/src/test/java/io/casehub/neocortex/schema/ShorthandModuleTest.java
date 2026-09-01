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
import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.mindmap.NodeRef;
import io.casehub.neocortex.mindmap.RecurrenceRule;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ShorthandModuleTest {

    private SchemaGenerator generator() {
        var builder = new SchemaGeneratorConfigBuilder(
            SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON);
        builder.with(Option.DEFINITIONS_FOR_ALL_OBJECTS);
        builder.with(new ShorthandModule());
        return new SchemaGenerator(builder.build());
    }

    @Test
    void confidence_hasOneOfWithNumberAndObject() {
        var schema = generator().generateSchema(Confidence.class);

        var oneOf = schema.get("oneOf");
        assertThat(oneOf).isNotNull();
        assertThat(oneOf.size()).isEqualTo(2);

        boolean hasNumber = false;
        boolean hasObject = false;
        for (var option : oneOf) {
            String type = option.path("type").asText();
            if ("number".equals(type)) hasNumber = true;
            if ("object".equals(type)) hasObject = true;
        }
        assertThat(hasNumber).as("Expected number shorthand").isTrue();
        assertThat(hasObject).as("Expected object full form").isTrue();
    }

    @Test
    void confidence_objectForm_hasOriginValueDecayReference() {
        var schema = generator().generateSchema(Confidence.class);

        for (var option : schema.get("oneOf")) {
            if ("object".equals(option.path("type").asText())) {
                var props = option.get("properties");
                assertThat(props.has("origin")).isTrue();
                assertThat(props.has("value")).isTrue();
                assertThat(props.has("decayReference")).isTrue();

                var required = option.get("required");
                assertThat(required.toString()).contains("origin");
                assertThat(required.toString()).contains("value");
                return;
            }
        }
        org.junit.jupiter.api.Assertions.fail("No object form found");
    }

    @Test
    void confidence_numberForm_hasBounds() {
        var schema = generator().generateSchema(Confidence.class);

        for (var option : schema.get("oneOf")) {
            if ("number".equals(option.path("type").asText())) {
                assertThat(option.get("minimum").asInt()).isEqualTo(0);
                assertThat(option.get("maximum").asInt()).isEqualTo(1);
                return;
            }
        }
        org.junit.jupiter.api.Assertions.fail("No number form found");
    }

    @Test
    void nodeRef_hasOneOfWithStringAndObject() {
        var schema = generator().generateSchema(NodeRef.class);

        var oneOf = schema.get("oneOf");
        assertThat(oneOf).isNotNull();
        assertThat(oneOf.size()).isEqualTo(2);

        boolean hasString = false;
        boolean hasObject = false;
        for (var option : oneOf) {
            String type = option.path("type").asText();
            if ("string".equals(type)) hasString = true;
            if ("object".equals(type)) hasObject = true;
        }
        assertThat(hasString).isTrue();
        assertThat(hasObject).isTrue();
    }

    @Test
    void nodeRef_stringForm_hasPattern() {
        var schema = generator().generateSchema(NodeRef.class);

        for (var option : schema.get("oneOf")) {
            if ("string".equals(option.path("type").asText())) {
                assertThat(option.has("pattern")).isTrue();
                return;
            }
        }
        org.junit.jupiter.api.Assertions.fail("No string form found");
    }

    @Test
    void nodeRef_objectForm_hasSchemeAndId() {
        var schema = generator().generateSchema(NodeRef.class);

        for (var option : schema.get("oneOf")) {
            if ("object".equals(option.path("type").asText())) {
                var props = option.get("properties");
                assertThat(props.has("scheme")).isTrue();
                assertThat(props.has("id")).isTrue();
                assertThat(props.has("qualifier")).isTrue();
                assertThat(option.get("required").toString())
                    .contains("scheme").contains("id");
                return;
            }
        }
        org.junit.jupiter.api.Assertions.fail("No object form found");
    }

    @Test
    void recurrenceRule_hasOneOfWithStringAndObject() {
        var schema = generator().generateSchema(RecurrenceRule.class);

        var oneOf = schema.get("oneOf");
        assertThat(oneOf).isNotNull();
        assertThat(oneOf.size()).isEqualTo(2);
    }

    @Test
    void recurrenceRule_objectForm_hasFreqRequired() {
        var schema = generator().generateSchema(RecurrenceRule.class);

        for (var option : schema.get("oneOf")) {
            if ("object".equals(option.path("type").asText())) {
                var props = option.get("properties");
                assertThat(props.has("freq")).isTrue();
                assertThat(props.has("interval")).isTrue();
                assertThat(props.has("byDay")).isTrue();
                assertThat(option.get("required").toString()).contains("freq");
                return;
            }
        }
        org.junit.jupiter.api.Assertions.fail("No object form found");
    }

    @Test
    void nonShorthandType_isNotIntercepted() {
        var schema = generator().generateSchema(String.class);
        assertThat(schema.has("oneOf")).isFalse();
    }
}

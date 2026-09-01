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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.CustomDefinition;
import com.github.victools.jsonschema.generator.Module;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.mindmap.NodeRef;
import io.casehub.neocortex.mindmap.RecurrenceRule;

public class ShorthandModule implements Module {

    @Override
    public void applyToConfigBuilder(SchemaGeneratorConfigBuilder builder) {
        builder.forTypesInGeneral()
            .withCustomDefinitionProvider((type, context) -> {
                Class<?> erasedType = type.getErasedType();
                if (erasedType == Confidence.class) {
                    return confidenceSchema(context.getGeneratorConfig());
                }
                if (erasedType == NodeRef.class) {
                    return nodeRefSchema(context.getGeneratorConfig());
                }
                if (erasedType == RecurrenceRule.class) {
                    return recurrenceRuleSchema(context.getGeneratorConfig());
                }
                return null;
            });
    }

    private CustomDefinition confidenceSchema(SchemaGeneratorConfig config) {
        ObjectNode schema = config.createObjectNode();
        ArrayNode oneOf = schema.putArray("oneOf");

        oneOf.addObject().put("type", "number")
            .put("minimum", 0).put("maximum", 1);

        ObjectNode full = oneOf.addObject();
        full.put("type", "object");
        ObjectNode props = full.putObject("properties");
        ObjectNode origin = props.putObject("origin");
        origin.put("type", "string");
        origin.putArray("enum")
            .add("STATED").add("INFERRED").add("SPECULATED").add("UNKNOWN");
        props.putObject("value").put("type", "number")
            .put("minimum", 0).put("maximum", 1);
        props.putObject("decayReference").put("type", "string")
            .put("format", "date-time");
        full.putArray("required").add("origin").add("value");

        return new CustomDefinition(schema);
    }

    private CustomDefinition nodeRefSchema(SchemaGeneratorConfig config) {
        ObjectNode schema = config.createObjectNode();
        ArrayNode oneOf = schema.putArray("oneOf");

        oneOf.addObject().put("type", "string")
            .put("pattern", "^[^:]+:.+$");

        ObjectNode full = oneOf.addObject();
        full.put("type", "object");
        ObjectNode props = full.putObject("properties");
        props.putObject("scheme").put("type", "string");
        props.putObject("id").put("type", "string");
        props.putObject("qualifier").put("type", "string");
        full.putArray("required").add("scheme").add("id");

        return new CustomDefinition(schema);
    }

    private CustomDefinition recurrenceRuleSchema(SchemaGeneratorConfig config) {
        ObjectNode schema = config.createObjectNode();
        ArrayNode oneOf = schema.putArray("oneOf");

        oneOf.addObject().put("type", "string")
            .put("pattern", "^FREQ=");

        ObjectNode full = oneOf.addObject();
        full.put("type", "object");
        ObjectNode props = full.putObject("properties");
        ObjectNode freq = props.putObject("freq");
        freq.put("type", "string");
        freq.putArray("enum")
            .add("DAILY").add("WEEKLY").add("MONTHLY").add("YEARLY");
        props.putObject("interval").put("type", "integer").put("minimum", 1);
        props.putObject("count").put("type", "integer").put("minimum", 1);
        props.putObject("until").put("type", "string").put("format", "date-time");
        ObjectNode byDay = props.putObject("byDay");
        byDay.put("type", "array");
        ObjectNode byDayItems = byDay.putObject("items");
        byDayItems.put("type", "string");
        byDayItems.putArray("enum")
            .add("MO").add("TU").add("WE").add("TH").add("FR").add("SA").add("SU");
        full.putArray("required").add("freq");

        return new CustomDefinition(schema);
    }
}

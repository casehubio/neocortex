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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.cbr.SimilaritySpec;
import io.casehub.neocortex.memory.cbr.WarpingConstraint;
import io.casehub.neocortex.mindmap.NodeRef;
import io.casehub.neocortex.mindmap.RecurrenceRule;
import io.casehub.schema.generator.module.EnumInliningModule;
import io.casehub.schema.generator.module.SealedHierarchyModule;
import io.casehub.schema.generator.module.ShorthandDefinition;
import io.casehub.schema.generator.module.ShorthandModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class CognitiveSchemaGenerator {

    static final         Map<Class<?>, ShorthandDefinition>   SHORTHAND_DEFINITIONS   = Map.of(
            Confidence.class, ShorthandDefinition.of(
                    config -> {
                        ObjectNode n = config.createObjectNode();
                        n.put("type", "number").put("minimum", 0).put("maximum", 1);
                        return n;
                    },
                    config -> {
                        ObjectNode full = config.createObjectNode();
                        full.put("type", "object");
                        ObjectNode props  = full.putObject("properties");
                        ObjectNode origin = props.putObject("origin");
                        origin.put("type", "string");
                        origin.putArray("enum")
                              .add("STATED").add("INFERRED").add("SPECULATED").add("UNKNOWN");
                        props.putObject("value").put("type", "number")
                             .put("minimum", 0).put("maximum", 1);
                        props.putObject("decayReference").put("type", "string")
                             .put("format", "date-time");
                        full.putArray("required").add("origin").add("value");
                        return full;
                    }
                                                    ),
            NodeRef.class, ShorthandDefinition.of(
                    config -> {
                        ObjectNode n = config.createObjectNode();
                        n.put("type", "string").put("pattern", "^[^:]+:.+$");
                        return n;
                    },
                    config -> {
                        ObjectNode full = config.createObjectNode();
                        full.put("type", "object");
                        ObjectNode props = full.putObject("properties");
                        props.putObject("scheme").put("type", "string");
                        props.putObject("id").put("type", "string");
                        props.putObject("qualifier").put("type", "string");
                        full.putArray("required").add("scheme").add("id");
                        return full;
                    }
                                                 ),
            RecurrenceRule.class, ShorthandDefinition.of(
                    config -> {
                        ObjectNode n = config.createObjectNode();
                        n.put("type", "string").put("pattern", "^FREQ=");
                        return n;
                    },
                    config -> {
                        ObjectNode full = config.createObjectNode();
                        full.put("type", "object");
                        ObjectNode props = full.putObject("properties");
                        ObjectNode freq  = props.putObject("freq");
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
                        return full;
                    }
                                                        )
                                                                                              );
    private static final Map<Class<?>, Map<Class<?>, String>> DISCRIMINATOR_OVERRIDES =
            Map.of(
                    SimilaritySpec.class, Map.of(
                            SimilaritySpec.CategoricalTable.class, "categoricalTable",
                            SimilaritySpec.GaussianDecay.class, "gaussian",
                            SimilaritySpec.StepDecay.class, "step",
                            SimilaritySpec.ExponentialDecay.class, "exponential",
                            SimilaritySpec.DtwSpec.class, "dtw",
                            SimilaritySpec.EditDistanceSpec.class, "editDistance"
                                                ),
                    WarpingConstraint.class, Map.of(
                            WarpingConstraint.ItakuraParallelogram.class, "itakura"
                                                   )
                  );
    private final SchemaGenerator schemaGenerator;

    public CognitiveSchemaGenerator() {
        var builder = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON);

        builder.with(Option.DEFINITIONS_FOR_ALL_OBJECTS);
        builder.with(Option.FLATTENED_ENUMS_FROM_TOSTRING);
        builder.with(new JacksonModule(JacksonOption.RESPECT_JSONPROPERTY_ORDER));
        builder.with(new EnumInliningModule());
        builder.with(new SealedHierarchyModule(DISCRIMINATOR_OVERRIDES));
        builder.with(new ShorthandModule(SHORTHAND_DEFINITIONS));

        this.schemaGenerator = new SchemaGenerator(builder.build());
    }

    public JsonNode generate(Class<?> rootType) {
        return schemaGenerator.generateSchema(rootType);
    }

    public void generateToYaml(Class<?> rootType, Path output) throws IOException {
        JsonNode schema = generate(rootType);
        ObjectMapper yamlMapper = new ObjectMapper(
                new YAMLFactory()
                        .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                        .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
        Files.createDirectories(output.getParent());
        yamlMapper.writerWithDefaultPrettyPrinter()
                  .writeValue(output.toFile(), schema);
    }
}

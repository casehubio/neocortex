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
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import io.casehub.neocortex.memory.cbr.SimilaritySpec;
import io.casehub.neocortex.memory.cbr.WarpingConstraint;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class CognitiveSchemaGenerator {

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
        builder.with(new ShorthandModule());

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

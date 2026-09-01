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
package io.casehub.neocortex.cognitive.index;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.personality.PersonalityWeights;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class PersonalityWeightsDeserializer extends StdDeserializer<PersonalityWeights> {

    public PersonalityWeightsDeserializer() {
        super(PersonalityWeights.class);
    }

    @Override
    public PersonalityWeights deserialize(JsonParser p, DeserializationContext ctx)
            throws IOException {
        Map<String, Double> raw = p.readValueAs(new TypeReference<Map<String, Double>>() {});
        Map<MemoryDomain, Double> weights = new LinkedHashMap<>();
        raw.forEach((k, v) -> weights.put(new MemoryDomain(k), v));
        return new PersonalityWeights(weights);
    }
}

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
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.casehub.neocortex.mindmap.RuleCondition;
import io.casehub.neocortex.mindmap.SubgraphType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

class RuleConditionDeserializer extends StdDeserializer<RuleCondition> {

    RuleConditionDeserializer() {
        super(RuleCondition.class);
    }

    @Override
    public RuleCondition deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        return parseCondition(node);
    }

    RuleCondition parseCondition(JsonNode node) {
        if (node.has("hasProperty")) {
            return new RuleCondition.HasProperty(node.get("hasProperty").asText());
        }
        if (node.has("notHasProperty")) {
            return new RuleCondition.NotHasProperty(node.get("notHasProperty").asText());
        }
        if (node.has("propertyEquals")) {
            JsonNode obj = node.get("propertyEquals");
            var entry = obj.fields().next();
            return new RuleCondition.PropertyEquals(entry.getKey(), entry.getValue().asText());
        }
        if (node.has("propertyIn")) {
            JsonNode obj = node.get("propertyIn");
            var entry = obj.fields().next();
            Set<String> values = new LinkedHashSet<>();
            entry.getValue().forEach(v -> values.add(v.asText()));
            return new RuleCondition.PropertyIn(entry.getKey(), values);
        }
        if (node.has("hasEdgeType")) {
            return new RuleCondition.HasEdgeType(node.get("hasEdgeType").asText());
        }
        if (node.has("hasEdgeTypes")) {
            Set<String> types = new LinkedHashSet<>();
            node.get("hasEdgeTypes").forEach(v -> types.add(v.asText()));
            return new RuleCondition.HasEdgeTypes(types);
        }
        if (node.has("hasAnyEdge")) {
            return new RuleCondition.HasAnyEdge();
        }
        if (node.has("inSubgraphType")) {
            return new RuleCondition.InSubgraphType(
                SubgraphType.valueOf(node.get("inSubgraphType").asText()));
        }
        if (node.has("anyOf")) {
            List<RuleCondition> conditions = new ArrayList<>();
            node.get("anyOf").forEach(c -> conditions.add(parseCondition(c)));
            return new RuleCondition.AnyOf(conditions);
        }
        if (node.has("allOf")) {
            List<RuleCondition> conditions = new ArrayList<>();
            node.get("allOf").forEach(c -> conditions.add(parseCondition(c)));
            return new RuleCondition.AllOf(conditions);
        }
        if (node.has("not")) {
            return new RuleCondition.Not(parseCondition(node.get("not")));
        }
        throw new IllegalArgumentException("Unknown rule condition: " + node);
    }
}

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
import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.mindmap.DeclarativeDerivedEdgeRule;
import io.casehub.neocortex.mindmap.EdgeDerivation;
import io.casehub.neocortex.mindmap.EdgeRef;
import io.casehub.neocortex.mindmap.TraversalSpec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class DeclarativeDerivedEdgeRuleDeserializer extends StdDeserializer<DeclarativeDerivedEdgeRule> {

    DeclarativeDerivedEdgeRuleDeserializer() {
        super(DeclarativeDerivedEdgeRule.class);
    }

    @Override
    public DeclarativeDerivedEdgeRule deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        JsonNode node = p.getCodec().readTree(p);

        String name = node.get("name").asText();

        JsonNode onNode = node.get("on");
        Set<String> triggerEdgeTypes = new LinkedHashSet<>();
        if (onNode.has("edgeType")) {
            triggerEdgeTypes.add(onNode.get("edgeType").asText());
        } else if (onNode.has("edgeTypes")) {
            onNode.get("edgeTypes").forEach(v -> triggerEdgeTypes.add(v.asText()));
        }

        TraversalSpec traverse = null;
        if (node.has("traverse")) {
            JsonNode tNode = node.get("traverse");
            String follow = tNode.get("follow").asText();
            EdgeRef from = resolveRef(tNode.get("from").asText());
            TraversalSpec.TraversalDirection direction = TraversalSpec.TraversalDirection.valueOf(
                tNode.get("direction").asText().toUpperCase());
            int maxDepth = tNode.has("maxDepth") ? tNode.get("maxDepth").asInt() : 3;
            traverse = new TraversalSpec(follow, from, direction, maxDepth);
        }

        List<EdgeDerivation> derivations = new ArrayList<>();
        node.get("derive").forEach(dNode -> {
            String edgeType = dNode.get("edgeType").asText();
            EdgeRef source = resolveRef(dNode.get("source").asText());
            EdgeRef target = resolveRef(dNode.get("target").asText());

            Confidence confidence = null;
            if (dNode.has("confidence")) {
                JsonNode confNode = dNode.get("confidence");
                if (confNode.isNumber()) {
                    confidence = new Confidence(ConfidenceOrigin.INFERRED, confNode.doubleValue(), null);
                } else if (confNode.isObject()) {
                    ConfidenceOrigin origin = confNode.has("origin")
                        ? ConfidenceOrigin.valueOf(confNode.get("origin").asText().toUpperCase())
                        : ConfidenceOrigin.INFERRED;
                    double value = confNode.get("value").doubleValue();
                    confidence = new Confidence(origin, value, null);
                }
            }

            Map<String, String> properties = new LinkedHashMap<>();
            if (dNode.has("properties")) {
                dNode.get("properties").fields().forEachRemaining(
                    entry -> properties.put(entry.getKey(), entry.getValue().asText()));
            }

            derivations.add(new EdgeDerivation(edgeType, source, target, confidence, properties));
        });

        return new DeclarativeDerivedEdgeRule(name, triggerEdgeTypes, traverse, derivations);
    }

    static EdgeRef resolveRef(String ref) {
        return switch (ref) {
            case "trigger.source" -> EdgeRef.TRIGGER_SOURCE;
            case "trigger.target" -> EdgeRef.TRIGGER_TARGET;
            case "traversal.node" -> EdgeRef.TRAVERSAL_NODE;
            default -> throw new IllegalArgumentException("Unknown edge reference: " + ref
                + ". Valid values: trigger.source, trigger.target, traversal.node");
        };
    }
}

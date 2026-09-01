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
package io.casehub.neocortex.mindmap;

import java.util.List;
import java.util.Set;

public sealed interface RuleCondition {

    boolean evaluate(MindMapNode node, List<MindMapEdge> edges);

    record HasProperty(String name) implements RuleCondition {
        public boolean evaluate(MindMapNode node, List<MindMapEdge> edges) {
            return node.property(name).isPresent();
        }
    }

    record PropertyEquals(String name, String value) implements RuleCondition {
        public boolean evaluate(MindMapNode node, List<MindMapEdge> edges) {
            return node.property(name).map(value::equals).orElse(false);
        }
    }

    record PropertyIn(String name, Set<String> values) implements RuleCondition {
        public PropertyIn { values = Set.copyOf(values); }
        public boolean evaluate(MindMapNode node, List<MindMapEdge> edges) {
            return node.property(name).map(values::contains).orElse(false);
        }
    }

    record NotHasProperty(String name) implements RuleCondition {
        public boolean evaluate(MindMapNode node, List<MindMapEdge> edges) {
            return node.property(name).isEmpty();
        }
    }

    record HasEdgeType(String edgeType) implements RuleCondition {
        public boolean evaluate(MindMapNode node, List<MindMapEdge> edges) {
            return edges.stream().anyMatch(e -> edgeType.equals(e.edgeType()));
        }
    }

    record HasEdgeTypes(Set<String> edgeTypes) implements RuleCondition {
        public HasEdgeTypes { edgeTypes = Set.copyOf(edgeTypes); }
        public boolean evaluate(MindMapNode node, List<MindMapEdge> edges) {
            return edges.stream().anyMatch(e -> edgeTypes.contains(e.edgeType()));
        }
    }

    record HasAnyEdge() implements RuleCondition {
        public boolean evaluate(MindMapNode node, List<MindMapEdge> edges) {
            return !edges.isEmpty();
        }
    }

    record InSubgraphType(SubgraphType type) implements RuleCondition {
        public boolean evaluate(MindMapNode node, List<MindMapEdge> edges) {
            return false;
        }
    }

    record AnyOf(List<RuleCondition> conditions) implements RuleCondition {
        public AnyOf { conditions = List.copyOf(conditions); }
        public boolean evaluate(MindMapNode node, List<MindMapEdge> edges) {
            return conditions.stream().anyMatch(c -> c.evaluate(node, edges));
        }
    }

    record AllOf(List<RuleCondition> conditions) implements RuleCondition {
        public AllOf { conditions = List.copyOf(conditions); }
        public boolean evaluate(MindMapNode node, List<MindMapEdge> edges) {
            return conditions.stream().allMatch(c -> c.evaluate(node, edges));
        }
    }

    record Not(RuleCondition condition) implements RuleCondition {
        public boolean evaluate(MindMapNode node, List<MindMapEdge> edges) {
            return !condition.evaluate(node, edges);
        }
    }
}

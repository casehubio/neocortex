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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.mindmap.DeclarativeDerivedEdgeRule;
import io.casehub.neocortex.mindmap.DeclarativeTraitRule;
import io.casehub.neocortex.mindmap.EdgeRef;
import io.casehub.neocortex.mindmap.RuleCondition;
import io.casehub.neocortex.mindmap.TraversalSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleConditionDeserializerTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = CognitiveDefaultsRegistry.createMapper();
    }

    @Test
    void deserialize_hasProperty() throws IOException {
        String yaml = "hasProperty: birthday";
        RuleCondition cond = mapper.readValue(yaml, RuleCondition.class);
        assertThat(cond).isInstanceOf(RuleCondition.HasProperty.class);
        assertThat(((RuleCondition.HasProperty) cond).name()).isEqualTo("birthday");
    }

    @Test
    void deserialize_notHasProperty() throws IOException {
        String yaml = "notHasProperty: deletedAt";
        RuleCondition cond = mapper.readValue(yaml, RuleCondition.class);
        assertThat(cond).isInstanceOf(RuleCondition.NotHasProperty.class);
        assertThat(((RuleCondition.NotHasProperty) cond).name()).isEqualTo("deletedAt");
    }

    @Test
    void deserialize_propertyEquals() throws IOException {
        String yaml = "propertyEquals:\n  eventKind: scheduled";
        RuleCondition cond = mapper.readValue(yaml, RuleCondition.class);
        assertThat(cond).isInstanceOf(RuleCondition.PropertyEquals.class);
        var pe = (RuleCondition.PropertyEquals) cond;
        assertThat(pe.name()).isEqualTo("eventKind");
        assertThat(pe.value()).isEqualTo("scheduled");
    }

    @Test
    void deserialize_propertyIn() throws IOException {
        String yaml = "propertyIn:\n  status: [ACTIVE, CONFIRMED]";
        RuleCondition cond = mapper.readValue(yaml, RuleCondition.class);
        assertThat(cond).isInstanceOf(RuleCondition.PropertyIn.class);
        var pi = (RuleCondition.PropertyIn) cond;
        assertThat(pi.name()).isEqualTo("status");
        assertThat(pi.values()).containsExactlyInAnyOrder("ACTIVE", "CONFIRMED");
    }

    @Test
    void deserialize_hasEdgeType() throws IOException {
        String yaml = "hasEdgeType: parent-of";
        RuleCondition cond = mapper.readValue(yaml, RuleCondition.class);
        assertThat(cond).isInstanceOf(RuleCondition.HasEdgeType.class);
        assertThat(((RuleCondition.HasEdgeType) cond).edgeType()).isEqualTo("parent-of");
    }

    @Test
    void deserialize_hasEdgeTypes() throws IOException {
        String yaml = "hasEdgeTypes: [parent-of, child-of, works-at]";
        RuleCondition cond = mapper.readValue(yaml, RuleCondition.class);
        assertThat(cond).isInstanceOf(RuleCondition.HasEdgeTypes.class);
        assertThat(((RuleCondition.HasEdgeTypes) cond).edgeTypes())
            .containsExactlyInAnyOrder("parent-of", "child-of", "works-at");
    }

    @Test
    void deserialize_hasAnyEdge() throws IOException {
        String yaml = "hasAnyEdge: true";
        RuleCondition cond = mapper.readValue(yaml, RuleCondition.class);
        assertThat(cond).isInstanceOf(RuleCondition.HasAnyEdge.class);
    }

    @Test
    void deserialize_inSubgraphType() throws IOException {
        String yaml = "inSubgraphType: PERSON";
        RuleCondition cond = mapper.readValue(yaml, RuleCondition.class);
        assertThat(cond).isInstanceOf(RuleCondition.InSubgraphType.class);
    }

    @Test
    void deserialize_anyOf() throws IOException {
        String yaml = """
            anyOf:
              - hasProperty: birthday
              - hasEdgeType: parent-of
            """;
        RuleCondition cond = mapper.readValue(yaml, RuleCondition.class);
        assertThat(cond).isInstanceOf(RuleCondition.AnyOf.class);
        var anyOf = (RuleCondition.AnyOf) cond;
        assertThat(anyOf.conditions()).hasSize(2);
        assertThat(anyOf.conditions().get(0)).isInstanceOf(RuleCondition.HasProperty.class);
        assertThat(anyOf.conditions().get(1)).isInstanceOf(RuleCondition.HasEdgeType.class);
    }

    @Test
    void deserialize_allOf() throws IOException {
        String yaml = """
            allOf:
              - hasProperty: status
              - hasEdgeType: contributes-to
            """;
        RuleCondition cond = mapper.readValue(yaml, RuleCondition.class);
        assertThat(cond).isInstanceOf(RuleCondition.AllOf.class);
        var allOf = (RuleCondition.AllOf) cond;
        assertThat(allOf.conditions()).hasSize(2);
    }

    @Test
    void deserialize_not() throws IOException {
        String yaml = """
            not:
              hasProperty: deletedAt
            """;
        RuleCondition cond = mapper.readValue(yaml, RuleCondition.class);
        assertThat(cond).isInstanceOf(RuleCondition.Not.class);
        var not = (RuleCondition.Not) cond;
        assertThat(not.condition()).isInstanceOf(RuleCondition.HasProperty.class);
    }

    @Test
    void deserialize_nestedCombinators() throws IOException {
        String yaml = """
            allOf:
              - propertyIn:
                  status: [ACTIVE, IN_PROGRESS]
              - not:
                  hasProperty: completedAt
              - hasEdgeType: contributes-to
            """;
        RuleCondition cond = mapper.readValue(yaml, RuleCondition.class);
        assertThat(cond).isInstanceOf(RuleCondition.AllOf.class);
        var allOf = (RuleCondition.AllOf) cond;
        assertThat(allOf.conditions()).hasSize(3);
        assertThat(allOf.conditions().get(0)).isInstanceOf(RuleCondition.PropertyIn.class);
        assertThat(allOf.conditions().get(1)).isInstanceOf(RuleCondition.Not.class);
        assertThat(allOf.conditions().get(2)).isInstanceOf(RuleCondition.HasEdgeType.class);
    }

    @Test
    void deserialize_unknownCondition_throws() {
        String yaml = "unknownPredicate: foo";
        assertThatThrownBy(() -> mapper.readValue(yaml, RuleCondition.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown rule condition");
    }

    @Test
    void deserialize_traitRule() throws IOException {
        String yaml = """
            trait: Personable
            when:
              anyOf:
                - hasProperty: birthday
                - hasProperty: role
                - hasEdgeTypes: [parent-of, child-of]
            """;
        DeclarativeTraitRule rule = mapper.readValue(yaml, DeclarativeTraitRule.class);
        assertThat(rule.traitName()).isEqualTo("Personable");
        assertThat(rule.when()).isInstanceOf(RuleCondition.AnyOf.class);
        assertThat(((RuleCondition.AnyOf) rule.when()).conditions()).hasSize(3);
    }

    @Test
    void deserialize_derivedEdgeRule_direct() throws IOException {
        String yaml = """
            name: inverse-knows
            on:
              edgeType: knows
            derive:
              - edgeType: known-by
                source: trigger.target
                target: trigger.source
            """;
        DeclarativeDerivedEdgeRule rule = mapper.readValue(yaml, DeclarativeDerivedEdgeRule.class);
        assertThat(rule.name()).isEqualTo("inverse-knows");
        assertThat(rule.triggerEdgeTypes()).containsExactly("knows");
        assertThat(rule.traverse()).isNull();
        assertThat(rule.derivations()).hasSize(1);
        assertThat(rule.derivations().get(0).edgeType()).isEqualTo("known-by");
        assertThat(rule.derivations().get(0).source()).isEqualTo(EdgeRef.TRIGGER_TARGET);
        assertThat(rule.derivations().get(0).target()).isEqualTo(EdgeRef.TRIGGER_SOURCE);
    }

    @Test
    void deserialize_derivedEdgeRule_withTraversal() throws IOException {
        String yaml = """
            name: descendant-chain
            on:
              edgeType: child-of
            traverse:
              follow: child-of
              from: trigger.target
              direction: outbound
              maxDepth: 3
            derive:
              - edgeType: descendant-of
                source: trigger.source
                target: traversal.node
            """;
        DeclarativeDerivedEdgeRule rule = mapper.readValue(yaml, DeclarativeDerivedEdgeRule.class);
        assertThat(rule.name()).isEqualTo("descendant-chain");
        assertThat(rule.traverse()).isNotNull();
        assertThat(rule.traverse().follow()).isEqualTo("child-of");
        assertThat(rule.traverse().from()).isEqualTo(EdgeRef.TRIGGER_TARGET);
        assertThat(rule.traverse().direction()).isEqualTo(TraversalSpec.TraversalDirection.OUTBOUND);
        assertThat(rule.traverse().maxDepth()).isEqualTo(3);
        assertThat(rule.derivations().get(0).target()).isEqualTo(EdgeRef.TRAVERSAL_NODE);
    }

    @Test
    void deserialize_derivedEdgeRule_withMultipleTriggers() throws IOException {
        String yaml = """
            name: multi-trigger
            on:
              edgeTypes: [knows, works-with]
            derive:
              - edgeType: inverse
                source: trigger.target
                target: trigger.source
            """;
        DeclarativeDerivedEdgeRule rule = mapper.readValue(yaml, DeclarativeDerivedEdgeRule.class);
        assertThat(rule.triggerEdgeTypes()).containsExactlyInAnyOrder("knows", "works-with");
    }

    @Test
    void deserialize_derivedEdgeRule_withProperties() throws IOException {
        String yaml = """
            name: bidi-colleague
            on:
              edgeType: works-with
            derive:
              - edgeType: works-with
                source: trigger.target
                target: trigger.source
                properties:
                  derived-reason: bidirectional
            """;
        DeclarativeDerivedEdgeRule rule = mapper.readValue(yaml, DeclarativeDerivedEdgeRule.class);
        assertThat(rule.derivations().get(0).properties())
            .containsEntry("derived-reason", "bidirectional");
    }

    @Test
    void deserialize_derivedEdgeRule_withConfidence() throws IOException {
        String yaml = """
            name: inferred-edge
            on:
              edgeType: knows
            derive:
              - edgeType: known-by
                source: trigger.target
                target: trigger.source
                confidence: 0.7
            """;
        DeclarativeDerivedEdgeRule rule = mapper.readValue(yaml, DeclarativeDerivedEdgeRule.class);
        assertThat(rule.derivations().get(0).confidence()).isNotNull();
        assertThat(rule.derivations().get(0).confidence().value()).isEqualTo(0.7);
    }

    @Test
    void deserialize_ruleFile_fromClasspath() throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("rules/test-rules.yaml")) {
            RuleFile ruleFile = mapper.readValue(is, RuleFile.class);
            assertThat(ruleFile.traitRules()).hasSize(2);
            assertThat(ruleFile.traitRules().get(0).traitName()).isEqualTo("Personable");
            assertThat(ruleFile.traitRules().get(1).traitName()).isEqualTo("Appointable");
            assertThat(ruleFile.derivedEdgeRules()).hasSize(2);
            assertThat(ruleFile.derivedEdgeRules().get(0).name()).isEqualTo("inverse-knows");
            assertThat(ruleFile.derivedEdgeRules().get(1).name()).isEqualTo("descendant-chain");
        }
    }
}

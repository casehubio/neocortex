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

import io.casehub.neocortex.mindmap.DerivedEdgeRule;
import io.casehub.neocortex.mindmap.TraitRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeclarativeRuleRegistryTest {

    private DeclarativeRuleRegistry registry;
    private CognitiveDefaultsRegistry cognitiveDefaults;

    @BeforeEach
    void setUp() throws IOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        cognitiveDefaults = CognitiveDefaultsRegistry.loadFromClasspath("cognitive-profiles/", cl);
        registry = DeclarativeRuleRegistry.loadFromClasspath("rules/", cognitiveDefaults, cl);
    }

    @Test
    void loadFromClasspath_loadsGlobalTraitRules() {
        List<TraitRule> rules = registry.traitRules(null);
        assertThat(rules).hasSize(2);
        assertThat(rules.stream().map(TraitRule::traitName))
            .containsExactly("Personable", "Appointable");
    }

    @Test
    void loadFromClasspath_loadsGlobalDerivedEdgeRules() {
        List<DerivedEdgeRule> rules = registry.derivedEdgeRules(null);
        assertThat(rules).hasSize(2);
        assertThat(rules.stream().map(DerivedEdgeRule::name))
            .containsExactly("inverse-knows", "descendant-chain");
    }

    @Test
    void traitRules_mergesAgentOverrides() {
        List<TraitRule> rules = registry.traitRules("alice");
        assertThat(rules.stream().map(TraitRule::traitName))
            .contains("Personable", "Appointable", "FamilyMember");
    }

    @Test
    void derivedEdgeRules_agentOverrideReplacesGlobal() {
        List<DerivedEdgeRule> rules = registry.derivedEdgeRules("alice");
        assertThat(rules.stream().map(DerivedEdgeRule::name))
            .containsExactly("inverse-knows", "descendant-chain");
        var inverseKnows = rules.stream()
            .filter(r -> "inverse-knows".equals(r.name()))
            .findFirst().orElseThrow();
        assertThat(inverseKnows).isInstanceOf(io.casehub.neocortex.mindmap.DeclarativeDerivedEdgeRule.class);
        var declarative = (io.casehub.neocortex.mindmap.DeclarativeDerivedEdgeRule) inverseKnows;
        assertThat(declarative.derivations().get(0).properties())
            .containsEntry("perspective", "alice");
    }

    @Test
    void traitRules_unknownAgent_returnsGlobalOnly() {
        List<TraitRule> rules = registry.traitRules("unknown");
        assertThat(rules).hasSize(2);
        assertThat(rules.stream().map(TraitRule::traitName))
            .containsExactly("Personable", "Appointable");
    }

    @Test
    void derivedEdgeRules_nullAgent_returnsGlobalOnly() {
        List<DerivedEdgeRule> rules = registry.derivedEdgeRules(null);
        assertThat(rules).hasSize(2);
    }

    @Test
    void allDerivedEdgeRules_mergesAllAgents() {
        List<DerivedEdgeRule> rules = registry.allDerivedEdgeRules();
        assertThat(rules.stream().map(DerivedEdgeRule::name))
            .containsExactly("inverse-knows", "descendant-chain");
        var inverseKnows = rules.stream()
            .filter(r -> "inverse-knows".equals(r.name()))
            .findFirst().orElseThrow();
        var declarative = (io.casehub.neocortex.mindmap.DeclarativeDerivedEdgeRule) inverseKnows;
        assertThat(declarative.derivations().get(0).properties())
            .containsEntry("perspective", "alice");
    }

    @Test
    void emptyRulesPath_producesEmptyRegistry() throws IOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        var emptyRegistry = DeclarativeRuleRegistry.loadFromClasspath(
            "rules-nonexistent/", cognitiveDefaults, cl);
        assertThat(emptyRegistry.traitRules(null)).isEmpty();
        assertThat(emptyRegistry.derivedEdgeRules(null)).isEmpty();
    }

    @Test
    void nullCognitiveDefaults_gracefulDegradation() throws IOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        var registryNoCognitive = DeclarativeRuleRegistry.loadFromClasspath(
            "rules/", null, cl);
        assertThat(registryNoCognitive.traitRules("alice")).hasSize(2);
        assertThat(registryNoCognitive.derivedEdgeRules("alice")).hasSize(2);
    }
}

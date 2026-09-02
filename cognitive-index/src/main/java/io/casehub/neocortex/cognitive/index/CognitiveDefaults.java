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

import io.casehub.neocortex.memory.mood.MoodBaseline;
import io.casehub.neocortex.memory.personality.PersonalityWeights;
import io.casehub.neocortex.mindmap.CuriosityConfig;
import io.casehub.neocortex.mindmap.DeclarativeDerivedEdgeRule;
import io.casehub.neocortex.mindmap.DeclarativeTraitRule;
import io.casehub.neocortex.mindmap.MindMapVocabulary;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record CognitiveDefaults(
        String agentId,
        String tenantId,
        PersonalityWeights personality,
        MoodBaseline moodBaseline,
        CuriosityConfig curiosity,
        TemporalFocusConfig temporalFocus,
        CbrStrategyDefaults cbrStrategy,
        SocialCognitionDefaults socialCognition,
        GraphStructureDefaults graphStructure,
        ExtractionBiasDefaults extractionBias,
        MindMapVocabulary vocabulary,
        Map<String, String> services,
        List<DeclarativeTraitRule> traitRules,
        List<DeclarativeDerivedEdgeRule> derivedEdgeRules,
        DescriptorView descriptor
) {
    public CognitiveDefaults {
        Objects.requireNonNull(agentId, "agentId required");
        services         = services != null ? Map.copyOf(services) : Map.of();
        traitRules       = traitRules != null ? List.copyOf(traitRules) : null;
        derivedEdgeRules = derivedEdgeRules != null ? List.copyOf(derivedEdgeRules) : null;
    }

    public static CognitiveDefaults empty(String agentId) {
        return new CognitiveDefaults(agentId, null, null, null, null, null, null, null, null, null, null, Map.of(), null, null, null);
    }

    public CognitiveDefaults withTenantId(String tenantId) {
        return new CognitiveDefaults(agentId, tenantId, personality, moodBaseline, curiosity, temporalFocus, cbrStrategy, socialCognition, graphStructure, extractionBias, vocabulary, services, traitRules, derivedEdgeRules, descriptor);
    }

    public CognitiveDefaults withPersonality(PersonalityWeights personality) {
        return new CognitiveDefaults(agentId, tenantId, personality, moodBaseline, curiosity, temporalFocus, cbrStrategy, socialCognition, graphStructure, extractionBias, vocabulary, services, traitRules, derivedEdgeRules, descriptor);
    }

    public CognitiveDefaults withMoodBaseline(MoodBaseline moodBaseline) {
        return new CognitiveDefaults(agentId, tenantId, personality, moodBaseline, curiosity, temporalFocus, cbrStrategy, socialCognition, graphStructure, extractionBias, vocabulary, services, traitRules, derivedEdgeRules, descriptor);
    }

    public CognitiveDefaults withCuriosity(CuriosityConfig curiosity) {
        return new CognitiveDefaults(agentId, tenantId, personality, moodBaseline, curiosity, temporalFocus, cbrStrategy, socialCognition, graphStructure, extractionBias, vocabulary, services, traitRules, derivedEdgeRules, descriptor);
    }

    public CognitiveDefaults withTemporalFocus(TemporalFocusConfig temporalFocus) {
        return new CognitiveDefaults(agentId, tenantId, personality, moodBaseline, curiosity, temporalFocus, cbrStrategy, socialCognition, graphStructure, extractionBias, vocabulary, services, traitRules, derivedEdgeRules, descriptor);
    }

    public CognitiveDefaults withCbrStrategy(CbrStrategyDefaults cbrStrategy) {
        return new CognitiveDefaults(agentId, tenantId, personality, moodBaseline, curiosity, temporalFocus, cbrStrategy, socialCognition, graphStructure, extractionBias, vocabulary, services, traitRules, derivedEdgeRules, descriptor);
    }

    public CognitiveDefaults withSocialCognition(SocialCognitionDefaults socialCognition) {
        return new CognitiveDefaults(agentId, tenantId, personality, moodBaseline, curiosity, temporalFocus, cbrStrategy, socialCognition, graphStructure, extractionBias, vocabulary, services, traitRules, derivedEdgeRules, descriptor);
    }

    public CognitiveDefaults withGraphStructure(GraphStructureDefaults graphStructure) {
        return new CognitiveDefaults(agentId, tenantId, personality, moodBaseline, curiosity, temporalFocus, cbrStrategy, socialCognition, graphStructure, extractionBias, vocabulary, services, traitRules, derivedEdgeRules, descriptor);
    }

    public CognitiveDefaults withExtractionBias(ExtractionBiasDefaults extractionBias) {
        return new CognitiveDefaults(agentId, tenantId, personality, moodBaseline, curiosity, temporalFocus, cbrStrategy, socialCognition, graphStructure, extractionBias, vocabulary, services, traitRules, derivedEdgeRules, descriptor);
    }

    public CognitiveDefaults withVocabulary(MindMapVocabulary vocabulary) {
        return new CognitiveDefaults(agentId, tenantId, personality, moodBaseline, curiosity, temporalFocus, cbrStrategy, socialCognition, graphStructure, extractionBias, vocabulary, services, traitRules, derivedEdgeRules, descriptor);
    }

    public CognitiveDefaults withServices(Map<String, String> services) {
        return new CognitiveDefaults(agentId, tenantId, personality, moodBaseline, curiosity, temporalFocus, cbrStrategy, socialCognition, graphStructure, extractionBias, vocabulary, services, traitRules, derivedEdgeRules, descriptor);
    }

    public CognitiveDefaults withTraitRules(List<DeclarativeTraitRule> traitRules) {
        return new CognitiveDefaults(agentId, tenantId, personality, moodBaseline, curiosity, temporalFocus, cbrStrategy, socialCognition, graphStructure, extractionBias, vocabulary, services, traitRules, derivedEdgeRules, descriptor);
    }

    public CognitiveDefaults withDerivedEdgeRules(List<DeclarativeDerivedEdgeRule> derivedEdgeRules) {
        return new CognitiveDefaults(agentId, tenantId, personality, moodBaseline, curiosity, temporalFocus, cbrStrategy, socialCognition, graphStructure, extractionBias, vocabulary, services, traitRules, derivedEdgeRules, descriptor);
    }

    public CognitiveDefaults withDescriptor(DescriptorView descriptor) {
        return new CognitiveDefaults(agentId, tenantId, personality, moodBaseline, curiosity, temporalFocus, cbrStrategy, socialCognition, graphStructure, extractionBias, vocabulary, services, traitRules, derivedEdgeRules, descriptor);
    }
}

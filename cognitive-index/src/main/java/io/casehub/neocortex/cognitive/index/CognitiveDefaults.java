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

import io.casehub.neocortex.cognitive.CognitiveCapacityConfig;
import io.casehub.neocortex.cognitive.CognitiveResourcePoolConfig;
import io.casehub.neocortex.cognitive.HabituationConfig;
import io.casehub.neocortex.cognitive.RecurringThoughtConfig;
import io.casehub.neocortex.cognitive.SearchBudgetConfig;
import io.casehub.neocortex.memory.mood.MoodBaseline;
import io.casehub.neocortex.memory.personality.PersonalityWeights;
import io.casehub.neocortex.mindmap.AppraisalWeights;
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
        PersonalityProfile personalityProfile,
        DescriptorView descriptor,
        EmotionDefaults emotionDefaults,
        CognitiveResourceDefaults cognitiveResourceDefaults,
        GraphDefaults graphDefaults,
        BehavioralDefaults behavioralDefaults,
        BriefingConfig briefing
) {
    public CognitiveDefaults {
        Objects.requireNonNull(agentId, "agentId required");
        if (emotionDefaults == null) emotionDefaults = EmotionDefaults.empty();
        if (cognitiveResourceDefaults == null) cognitiveResourceDefaults = CognitiveResourceDefaults.empty();
        if (graphDefaults == null) graphDefaults = GraphDefaults.empty();
        if (behavioralDefaults == null) behavioralDefaults = BehavioralDefaults.empty();
    }

    public static CognitiveDefaults empty(String agentId) {
        return new CognitiveDefaults(agentId, null, null, null, null, null, null, null, null);
    }

    // --- Leaf accessors (delegates) ---

    public PersonalityWeights personality() {
        return personalityProfile != null ? personalityProfile.personality() : null;
    }

    public Double personalityDominance() {
        return personalityProfile != null ? personalityProfile.personalityDominance() : null;
    }

    public ExpressionStyleDefaults expressionStyles() {
        return personalityProfile != null ? personalityProfile.expressionStyles() : null;
    }

    public MoodBaseline moodBaseline() { return emotionDefaults.moodBaseline(); }
    public AppraisalWeights appraisalWeights() { return emotionDefaults.appraisalWeights(); }
    public RegulationDefaults regulationDefaults() { return emotionDefaults.regulationDefaults(); }
    public CopingDefaults copingDefaults() { return emotionDefaults.copingDefaults(); }

    public CognitiveCapacityConfig capacityConfig() { return cognitiveResourceDefaults.capacityConfig(); }
    public CognitiveResourcePoolConfig cognitiveResourcePoolConfig() { return cognitiveResourceDefaults.cognitiveResourcePoolConfig(); }
    public SearchBudgetConfig searchBudgetConfig() { return cognitiveResourceDefaults.searchBudgetConfig(); }
    public RecurringThoughtConfig recurringThoughtConfig() { return cognitiveResourceDefaults.recurringThoughtConfig(); }
    public HabituationConfig habituationConfig() { return cognitiveResourceDefaults.habituationConfig(); }

    public MindMapVocabulary vocabulary() { return graphDefaults.vocabulary(); }
    public Map<String, String> services() { return graphDefaults.services(); }
    public List<DeclarativeTraitRule> traitRules() { return graphDefaults.traitRules(); }
    public List<DeclarativeDerivedEdgeRule> derivedEdgeRules() { return graphDefaults.derivedEdgeRules(); }
    public GraphStructureDefaults graphStructure() { return graphDefaults.graphStructure(); }
    public ExtractionBiasDefaults extractionBias() { return graphDefaults.extractionBias(); }

    public CuriosityConfig curiosity() { return behavioralDefaults.curiosity(); }
    public TemporalFocusConfig temporalFocus() { return behavioralDefaults.temporalFocus(); }
    public SocialCognitionDefaults socialCognition() { return behavioralDefaults.socialCognition(); }
    public CbrStrategyDefaults cbrStrategy() { return behavioralDefaults.cbrStrategy(); }

    // --- Group-level withX() methods ---

    public CognitiveDefaults withTenantId(String tenantId) {
        return new CognitiveDefaults(agentId, tenantId, personalityProfile, descriptor, emotionDefaults, cognitiveResourceDefaults, graphDefaults, behavioralDefaults, briefing);
    }

    public CognitiveDefaults withPersonalityProfile(PersonalityProfile personalityProfile) {
        return new CognitiveDefaults(agentId, tenantId, personalityProfile, descriptor, emotionDefaults, cognitiveResourceDefaults, graphDefaults, behavioralDefaults, briefing);
    }

    public CognitiveDefaults withDescriptor(DescriptorView descriptor) {
        return new CognitiveDefaults(agentId, tenantId, personalityProfile, descriptor, emotionDefaults, cognitiveResourceDefaults, graphDefaults, behavioralDefaults, briefing);
    }

    public CognitiveDefaults withEmotionDefaults(EmotionDefaults emotionDefaults) {
        return new CognitiveDefaults(agentId, tenantId, personalityProfile, descriptor, emotionDefaults, cognitiveResourceDefaults, graphDefaults, behavioralDefaults, briefing);
    }

    public CognitiveDefaults withCognitiveResourceDefaults(CognitiveResourceDefaults cognitiveResourceDefaults) {
        return new CognitiveDefaults(agentId, tenantId, personalityProfile, descriptor, emotionDefaults, cognitiveResourceDefaults, graphDefaults, behavioralDefaults, briefing);
    }

    public CognitiveDefaults withGraphDefaults(GraphDefaults graphDefaults) {
        return new CognitiveDefaults(agentId, tenantId, personalityProfile, descriptor, emotionDefaults, cognitiveResourceDefaults, graphDefaults, behavioralDefaults, briefing);
    }

    public CognitiveDefaults withBehavioralDefaults(BehavioralDefaults behavioralDefaults) {
        return new CognitiveDefaults(agentId, tenantId, personalityProfile, descriptor, emotionDefaults, cognitiveResourceDefaults, graphDefaults, behavioralDefaults, briefing);
    }

    public CognitiveDefaults withBriefing(BriefingConfig briefing) {
        return new CognitiveDefaults(agentId, tenantId, personalityProfile, descriptor, emotionDefaults, cognitiveResourceDefaults, graphDefaults, behavioralDefaults, briefing);
    }

    // --- Leaf-level withX() methods (delegates through groups) ---

    public CognitiveDefaults withPersonality(PersonalityWeights personality) {
        var pp = personalityProfile != null ? personalityProfile : new PersonalityProfile(null, null, null, null, List.of());
        return withPersonalityProfile(new PersonalityProfile(personality, pp.personalityDominance(), pp.expressionStyles(), pp.attachment(), pp.facets()));
    }

    public CognitiveDefaults withPersonalityDominance(Double personalityDominance) {
        var pp = personalityProfile != null ? personalityProfile : new PersonalityProfile(null, null, null, null, List.of());
        return withPersonalityProfile(new PersonalityProfile(pp.personality(), personalityDominance, pp.expressionStyles(), pp.attachment(), pp.facets()));
    }

    public CognitiveDefaults withExpressionStyles(ExpressionStyleDefaults expressionStyles) {
        var pp = personalityProfile != null ? personalityProfile : new PersonalityProfile(null, null, null, null, List.of());
        return withPersonalityProfile(new PersonalityProfile(pp.personality(), pp.personalityDominance(), expressionStyles, pp.attachment(), pp.facets()));
    }

    public CognitiveDefaults withMoodBaseline(MoodBaseline moodBaseline) {
        var e = emotionDefaults;
        return withEmotionDefaults(new EmotionDefaults(moodBaseline, e.appraisalWeights(), e.regulationDefaults(), e.copingDefaults()));
    }

    public CognitiveDefaults withAppraisalWeights(AppraisalWeights appraisalWeights) {
        var e = emotionDefaults;
        return withEmotionDefaults(new EmotionDefaults(e.moodBaseline(), appraisalWeights, e.regulationDefaults(), e.copingDefaults()));
    }

    public CognitiveDefaults withRegulationDefaults(RegulationDefaults regulationDefaults) {
        var e = emotionDefaults;
        return withEmotionDefaults(new EmotionDefaults(e.moodBaseline(), e.appraisalWeights(), regulationDefaults, e.copingDefaults()));
    }

    public CognitiveDefaults withCopingDefaults(CopingDefaults copingDefaults) {
        var e = emotionDefaults;
        return withEmotionDefaults(new EmotionDefaults(e.moodBaseline(), e.appraisalWeights(), e.regulationDefaults(), copingDefaults));
    }

    public CognitiveDefaults withCapacityConfig(CognitiveCapacityConfig capacityConfig) {
        var r = cognitiveResourceDefaults;
        return withCognitiveResourceDefaults(new CognitiveResourceDefaults(capacityConfig, r.cognitiveResourcePoolConfig(), r.searchBudgetConfig(), r.recurringThoughtConfig(), r.habituationConfig()));
    }

    public CognitiveDefaults withCognitiveResourcePoolConfig(CognitiveResourcePoolConfig cognitiveResourcePoolConfig) {
        var r = cognitiveResourceDefaults;
        return withCognitiveResourceDefaults(new CognitiveResourceDefaults(r.capacityConfig(), cognitiveResourcePoolConfig, r.searchBudgetConfig(), r.recurringThoughtConfig(), r.habituationConfig()));
    }

    public CognitiveDefaults withSearchBudgetConfig(SearchBudgetConfig searchBudgetConfig) {
        var r = cognitiveResourceDefaults;
        return withCognitiveResourceDefaults(new CognitiveResourceDefaults(r.capacityConfig(), r.cognitiveResourcePoolConfig(), searchBudgetConfig, r.recurringThoughtConfig(), r.habituationConfig()));
    }

    public CognitiveDefaults withRecurringThoughtConfig(RecurringThoughtConfig recurringThoughtConfig) {
        var r = cognitiveResourceDefaults;
        return withCognitiveResourceDefaults(new CognitiveResourceDefaults(r.capacityConfig(), r.cognitiveResourcePoolConfig(), r.searchBudgetConfig(), recurringThoughtConfig, r.habituationConfig()));
    }

    public CognitiveDefaults withHabituationConfig(HabituationConfig habituationConfig) {
        var r = cognitiveResourceDefaults;
        return withCognitiveResourceDefaults(new CognitiveResourceDefaults(r.capacityConfig(), r.cognitiveResourcePoolConfig(), r.searchBudgetConfig(), r.recurringThoughtConfig(), habituationConfig));
    }

    public CognitiveDefaults withVocabulary(MindMapVocabulary vocabulary) {
        var g = graphDefaults;
        return withGraphDefaults(new GraphDefaults(vocabulary, g.services(), g.traitRules(), g.derivedEdgeRules(), g.graphStructure(), g.extractionBias()));
    }

    public CognitiveDefaults withServices(Map<String, String> services) {
        var g = graphDefaults;
        return withGraphDefaults(new GraphDefaults(g.vocabulary(), services, g.traitRules(), g.derivedEdgeRules(), g.graphStructure(), g.extractionBias()));
    }

    public CognitiveDefaults withTraitRules(List<DeclarativeTraitRule> traitRules) {
        var g = graphDefaults;
        return withGraphDefaults(new GraphDefaults(g.vocabulary(), g.services(), traitRules, g.derivedEdgeRules(), g.graphStructure(), g.extractionBias()));
    }

    public CognitiveDefaults withDerivedEdgeRules(List<DeclarativeDerivedEdgeRule> derivedEdgeRules) {
        var g = graphDefaults;
        return withGraphDefaults(new GraphDefaults(g.vocabulary(), g.services(), g.traitRules(), derivedEdgeRules, g.graphStructure(), g.extractionBias()));
    }

    public CognitiveDefaults withGraphStructure(GraphStructureDefaults graphStructure) {
        var g = graphDefaults;
        return withGraphDefaults(new GraphDefaults(g.vocabulary(), g.services(), g.traitRules(), g.derivedEdgeRules(), graphStructure, g.extractionBias()));
    }

    public CognitiveDefaults withExtractionBias(ExtractionBiasDefaults extractionBias) {
        var g = graphDefaults;
        return withGraphDefaults(new GraphDefaults(g.vocabulary(), g.services(), g.traitRules(), g.derivedEdgeRules(), g.graphStructure(), extractionBias));
    }

    public CognitiveDefaults withCuriosity(CuriosityConfig curiosity) {
        var b = behavioralDefaults;
        return withBehavioralDefaults(new BehavioralDefaults(curiosity, b.temporalFocus(), b.socialCognition(), b.cbrStrategy()));
    }

    public CognitiveDefaults withTemporalFocus(TemporalFocusConfig temporalFocus) {
        var b = behavioralDefaults;
        return withBehavioralDefaults(new BehavioralDefaults(b.curiosity(), temporalFocus, b.socialCognition(), b.cbrStrategy()));
    }

    public CognitiveDefaults withSocialCognition(SocialCognitionDefaults socialCognition) {
        var b = behavioralDefaults;
        return withBehavioralDefaults(new BehavioralDefaults(b.curiosity(), b.temporalFocus(), socialCognition, b.cbrStrategy()));
    }

    public CognitiveDefaults withCbrStrategy(CbrStrategyDefaults cbrStrategy) {
        var b = behavioralDefaults;
        return withBehavioralDefaults(new BehavioralDefaults(b.curiosity(), b.temporalFocus(), b.socialCognition(), cbrStrategy));
    }
}

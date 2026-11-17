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
import io.casehub.neocortex.memory.personality.PersonalityWeights;
import io.casehub.neocortex.mindmap.CuriosityConfig;

import java.util.List;

public final class LearningProfile {

    private final CognitiveDefaults delegate;

    private LearningProfile(CognitiveDefaults delegate) {
        this.delegate = delegate;
    }

    public static LearningProfile from(CognitiveDefaults defaults) {
        return new LearningProfile(defaults);
    }

    public String agentId() { return delegate.agentId(); }

    public Double associativeReach() {
        var c = delegate.capacityConfig();
        return c != null ? c.associativeReach() : null;
    }

    public Double relationalComplexity() {
        var c = delegate.capacityConfig();
        return c != null ? c.relationalComplexity() : null;
    }

    public Double workingMemorySpan() {
        var c = delegate.capacityConfig();
        return c != null ? c.workingMemorySpan() : null;
    }

    public Double trustFormationRate() {
        var sc = delegate.socialCognition();
        return sc != null ? sc.trustFormationRate() : null;
    }

    public ConflictInterpretation conflictInterpretation() {
        var sc = delegate.socialCognition();
        return sc != null ? sc.conflictInterpretation() : null;
    }

    public DispositionAxes dispositionAxes() {
        var d = delegate.descriptor();
        return d != null ? d.disposition() : null;
    }

    public List<PersonalityFacet> facets() {
        var d = delegate.descriptor();
        if (d != null && !d.personalityFacets().isEmpty()) {
            return d.personalityFacets();
        }
        var pp = delegate.personalityProfile();
        return pp != null ? pp.facets() : List.of();
    }

    public AttachmentProfile attachment() {
        var pp = delegate.personalityProfile();
        return pp != null ? pp.attachment() : null;
    }


    public CopingDefaults copingDefaults() { return delegate.copingDefaults(); }
    public RegulationDefaults regulationDefaults() { return delegate.regulationDefaults(); }
    public RecurringThoughtConfig recurringThoughtConfig() { return delegate.recurringThoughtConfig(); }
    public HabituationConfig habituationConfig() { return delegate.habituationConfig(); }
    public CognitiveCapacityConfig capacityConfig() { return delegate.capacityConfig(); }
    public SearchBudgetConfig searchBudgetConfig() { return delegate.searchBudgetConfig(); }
    public CognitiveResourcePoolConfig cognitiveResourcePoolConfig() { return delegate.cognitiveResourcePoolConfig(); }
    public ExpressionStyleDefaults expressionStyles() { return delegate.expressionStyles(); }
    public PersonalityWeights personality() { return delegate.personality(); }
    public Double personalityDominance() { return delegate.personalityDominance(); }
    public CuriosityConfig curiosity() { return delegate.curiosity(); }
    public GraphStructureDefaults graphStructure() { return delegate.graphStructure(); }
    public ExtractionBiasDefaults extractionBias() { return delegate.extractionBias(); }
    public SocialCognitionDefaults socialCognition() { return delegate.socialCognition(); }
    public CbrStrategyDefaults cbrStrategy() { return delegate.cbrStrategy(); }
    public TemporalFocusConfig temporalFocus() { return delegate.temporalFocus(); }
}

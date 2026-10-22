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
package io.casehub.neocortex.cognition.core;

import io.casehub.neocortex.cognition.appraisal.AppraisalStrategy;
import io.casehub.neocortex.cognition.appraisal.SalienceStrategy;
import io.casehub.neocortex.cognition.drive.DriveOrchestrator;
import io.casehub.neocortex.cognition.drive.NeedTierMappingProvider;
import io.casehub.neocortex.cognition.goal.GoalProposalOrchestrator;
import io.casehub.neocortex.cognition.innerlife.InnerLifeOrchestrator;
import io.casehub.neocortex.cognition.memory.MemoryHygieneOrchestrator;
import io.casehub.neocortex.cognition.mentalmodel.MentalModelOrchestrator;
import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import io.casehub.neocortex.cognition.narrative.NarrativeOrchestrator;
import io.casehub.neocortex.cognition.strategy.StrategyLearningOrchestrator;
import io.casehub.neocortex.cognition.temporal.ReflectionRetrievalOrchestrator;
import io.casehub.neocortex.cognition.temporal.TemporalFocusOrchestrator;
import io.casehub.neocortex.cognition.usermodel.UserModelOrchestrator;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.knowledge.TermNormalizer;
import io.casehub.neocortex.cognition.subthought.SubThoughtTickParticipant;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.engagement.EngagementEvent;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.platform.agent.session.SessionPipeline;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

public final class CognitivePipelineBuilder {

    private final MoodOrchestrator mood;
    private final DriveOrchestrator drives;

    private @Nullable UserModelOrchestrator userModel;
    private @Nullable MentalModelOrchestrator mentalModel;
    private @Nullable StrategyLearningOrchestrator strategy;
    private @Nullable NarrativeOrchestrator narrative;
    private @Nullable GoalProposalOrchestrator goals;
    private @Nullable MemoryHygieneOrchestrator memoryHygiene;
    private @Nullable InnerLifeOrchestrator innerLife;
    private CognitionConfig config = CognitionConfig.all();
    private @Nullable MindMapStore mindMapStore;
    private @Nullable NeedTierMappingProvider needTierMappingProvider;
    private @Nullable CognitiveAttentionMediator attentionMediator;
    private @Nullable Consumer<EngagementEvent> engagementPersister;
    private @Nullable TemporalFocusOrchestrator temporalFocus;
    private @Nullable ReflectionRetrievalOrchestrator reflectionOrchestrator;
    private @Nullable ConsolidationMediator consolidationMediator;
    private @Nullable SessionPipeline sessionPipeline;

    private @Nullable CognitiveDefaultsRegistry defaultsRegistry;
    private @Nullable AppraisalStrategy appraisalStrategy;
    private @Nullable SalienceStrategy salienceStrategy;
    private @Nullable CaseMemoryStore gutFeelingMemoryStore;
    private @Nullable SubThoughtTickParticipant subThoughtParticipant;
    private @Nullable MindMapStore driveGoalBridgeMindMapStore;
    private @Nullable TermNormalizer driveGoalBridgeTermNormalizer;

    private CognitivePipelineBuilder(MoodOrchestrator mood, DriveOrchestrator drives) {
        this.mood = mood;
        this.drives = drives;
    }

    public static CognitivePipelineBuilder create(MoodOrchestrator mood, DriveOrchestrator drives) {
        return new CognitivePipelineBuilder(mood, drives);
    }

    public CognitivePipelineBuilder userModel(@Nullable UserModelOrchestrator userModel) {
        this.userModel = userModel;
        return this;
    }

    public CognitivePipelineBuilder mentalModel(@Nullable MentalModelOrchestrator mentalModel) {
        this.mentalModel = mentalModel;
        return this;
    }

    public CognitivePipelineBuilder strategy(@Nullable StrategyLearningOrchestrator strategy) {
        this.strategy = strategy;
        return this;
    }

    public CognitivePipelineBuilder narrative(@Nullable NarrativeOrchestrator narrative) {
        this.narrative = narrative;
        return this;
    }

    public CognitivePipelineBuilder goals(@Nullable GoalProposalOrchestrator goals) {
        this.goals = goals;
        return this;
    }

    public CognitivePipelineBuilder memoryHygiene(@Nullable MemoryHygieneOrchestrator memoryHygiene) {
        this.memoryHygiene = memoryHygiene;
        return this;
    }

    public CognitivePipelineBuilder innerLife(@Nullable InnerLifeOrchestrator innerLife) {
        this.innerLife = innerLife;
        return this;
    }

    public CognitivePipelineBuilder config(CognitionConfig config) {
        this.config = config;
        return this;
    }

    public CognitivePipelineBuilder mindMapStore(@Nullable MindMapStore mindMapStore) {
        this.mindMapStore = mindMapStore;
        return this;
    }

    public CognitivePipelineBuilder needTierMappingProvider(@Nullable NeedTierMappingProvider needTierMappingProvider) {
        this.needTierMappingProvider = needTierMappingProvider;
        return this;
    }

    public CognitivePipelineBuilder attentionMediator(@Nullable CognitiveAttentionMediator attentionMediator) {
        this.attentionMediator = attentionMediator;
        return this;
    }

    public CognitivePipelineBuilder engagementPersister(@Nullable Consumer<EngagementEvent> engagementPersister) {
        this.engagementPersister = engagementPersister;
        return this;
    }

    public CognitivePipelineBuilder temporalFocus(@Nullable TemporalFocusOrchestrator temporalFocus) {
        this.temporalFocus = temporalFocus;
        return this;
    }

    public CognitivePipelineBuilder reflectionOrchestrator(@Nullable ReflectionRetrievalOrchestrator reflectionOrchestrator) {
        this.reflectionOrchestrator = reflectionOrchestrator;
        return this;
    }

    public CognitivePipelineBuilder consolidationMediator(@Nullable ConsolidationMediator consolidationMediator) {
        this.consolidationMediator = consolidationMediator;
        return this;
    }

    public CognitivePipelineBuilder sessionPipeline(@Nullable SessionPipeline sessionPipeline) {
        this.sessionPipeline = sessionPipeline;
        return this;
    }

    public CognitivePipelineBuilder defaultsRegistry(@Nullable CognitiveDefaultsRegistry defaultsRegistry) {
        this.defaultsRegistry = defaultsRegistry;
        return this;
    }

    public CognitivePipelineBuilder appraisal(@Nullable AppraisalStrategy appraisalStrategy, @Nullable SalienceStrategy salienceStrategy) {
        this.appraisalStrategy = appraisalStrategy;
        this.salienceStrategy = salienceStrategy;
        return this;
    }

    public CognitivePipelineBuilder gutFeeling(@Nullable CaseMemoryStore memoryStore) {
        this.gutFeelingMemoryStore = memoryStore;
        return this;
    }

    public CognitivePipelineBuilder subThoughts(@Nullable SubThoughtTickParticipant participant) {
        this.subThoughtParticipant = participant;
        return this;
    }

    public CognitivePipelineBuilder driveGoalBridge(@Nullable MindMapStore mindMapStore, @Nullable TermNormalizer termNormalizer) {
        this.driveGoalBridgeMindMapStore = mindMapStore;
        this.driveGoalBridgeTermNormalizer = termNormalizer;
        return this;
    }

    public CognitionCore build() {
        var core = new CognitionCore(
                mood, drives, userModel, mentalModel, strategy, narrative,
                goals, memoryHygiene, innerLife, config,
                mindMapStore, needTierMappingProvider, attentionMediator,
                engagementPersister, temporalFocus, reflectionOrchestrator,
                consolidationMediator, sessionPipeline);

        if (defaultsRegistry != null) {
            core.setDefaultsRegistry(defaultsRegistry);
        }
        if (appraisalStrategy != null && salienceStrategy != null) {
            core.configureAppraisal(appraisalStrategy, salienceStrategy);
        }
        if (gutFeelingMemoryStore != null) {
            core.configureGutFeeling(gutFeelingMemoryStore, mood);
        }
        if (subThoughtParticipant != null) {
            core.configureSubThoughts(subThoughtParticipant);
        }
        if (driveGoalBridgeMindMapStore != null && driveGoalBridgeTermNormalizer != null) {
            core.configureDriveGoalBridge(driveGoalBridgeMindMapStore, driveGoalBridgeTermNormalizer);
        }

        return core;
    }
}

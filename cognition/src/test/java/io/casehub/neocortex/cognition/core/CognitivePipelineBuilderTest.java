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

import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.neocortex.cognition.drive.DriveComposer;
import io.casehub.neocortex.cognition.drive.DriveConfig;
import io.casehub.neocortex.cognition.drive.DriveIntensity;
import io.casehub.neocortex.cognition.drive.DriveOrchestrator;
import io.casehub.neocortex.cognition.drive.DriveSource;
import io.casehub.neocortex.cognition.mood.MoodConfig;
import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CognitivePipelineBuilderTest {

    private static final DriveSource NOOP_DRIVE = (a, t) -> new DriveIntensity(DriveAxis.CURIOSITY, 0.0, "none");

    private final MoodOrchestrator mood = new MoodOrchestrator(MoodConfig.defaults());
    private final DriveOrchestrator drives = new DriveOrchestrator(
            NOOP_DRIVE, NOOP_DRIVE, NOOP_DRIVE, NOOP_DRIVE,
            mood, new DriveComposer(), DriveConfig.defaults());

    private static io.casehub.neocortex.memory.inmem.InMemoryMemoryStore createMemoryStore() {
        return new io.casehub.neocortex.memory.inmem.InMemoryMemoryStore(
                new io.casehub.platform.api.identity.CurrentPrincipal() {
                    @Override
                    public String actorId()               {return "test-actor";}

                    @Override
                    public java.util.Set<String> groups() {return java.util.Set.of();}

                    @Override
                    public String tenancyId()             {return "test-tenant";}

                    @Override
                    public boolean isCrossTenantAdmin()   {return true;}
                });
    }


    @Test
    void buildWithRequiredParamsOnlyProducesCognitionCore() {
        var core = CognitivePipelineBuilder.create(mood, drives)
                .build();

        assertThat(core).isNotNull();
        assertThat(core.mood()).isSameAs(mood);
        assertThat(core.drives()).isSameAs(drives);
        assertThat(core.config()).isEqualTo(CognitionConfig.all());
    }

    @Test
    void buildWithOptionalOrchestratorsWiresThem() {
        var userModel   = new io.casehub.neocortex.cognition.usermodel.UserModelOrchestrator(null, io.casehub.neocortex.cognition.usermodel.UserModelConfig.defaults(), null);
        var mentalModel = new io.casehub.neocortex.cognition.mentalmodel.MentalModelOrchestrator(null, io.casehub.neocortex.cognition.mentalmodel.MentalModelConfig.defaults(), java.time.Clock.systemUTC(), null);
        var strategy    = new io.casehub.neocortex.cognition.strategy.StrategyLearningOrchestrator(null, null, io.casehub.neocortex.cognition.strategy.StrategyLearningConfig.defaults(), java.time.Clock.systemUTC(), null);
        var innerLife   = new io.casehub.neocortex.cognition.innerlife.InnerLifeOrchestrator(null, java.util.List.of(), io.casehub.neocortex.cognition.innerlife.InnerLifeConfig.defaults(), drives, null);
        var goals       = new io.casehub.neocortex.cognition.goal.GoalProposalOrchestrator(drives, java.util.List.of(), null, java.util.Optional.empty(), null, null, null, io.casehub.neocortex.cognition.goal.GoalProposalConfig.defaults(), io.casehub.neocortex.cognition.goal.GoalEscalationConfig.defaults(), java.time.Clock.systemUTC());

        var core = CognitivePipelineBuilder.create(mood, drives)
                                           .userModel(userModel)
                                           .mentalModel(mentalModel)
                                           .strategy(strategy)
                                           .innerLife(innerLife)
                                           .goals(goals)
                                           .build();

        assertThat(core.userModel()).isSameAs(userModel);
        assertThat(core.mentalModel()).isSameAs(mentalModel);
        assertThat(core.strategy()).isSameAs(strategy);
        assertThat(core.innerLife()).isSameAs(innerLife);
        assertThat(core.goals()).isSameAs(goals);
    }

    @Test
    void buildWithCustomConfigUsesIt() {
        var config = CognitionConfig.none();

        var core = CognitivePipelineBuilder.create(mood, drives)
                                           .config(config)
                                           .build();

        assertThat(core.config()).isSameAs(config);
    }

    @Test
    void buildDefaultsToAllConfig() {
        var core = CognitivePipelineBuilder.create(mood, drives).build();

        assertThat(core.config()).isEqualTo(CognitionConfig.all());
    }

    @Test
    void buildWithCognitiveProfileWiresParticipant() {
        var mindMapStore = new io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore();
        var memoryStore  = createMemoryStore();
        var profile      = new io.casehub.neocortex.cognitive.index.CognitiveProfile(mindMapStore, memoryStore);

        var core = CognitivePipelineBuilder.create(mood, drives)
                                           .cognitiveProfile(profile)
                                           .build();

        assertThat(core).isNotNull();
        assertThat(core.promptSections()).isNotNull();
    }

    @Test
    void buildWithDomainActivationAndMindMapStoreWiresParticipant() {
        var mindMapStore     = new io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore();
        var memoryStore      = createMemoryStore();
        var domainActivation = new io.casehub.neocortex.cognitive.index.DomainActivation(mindMapStore, memoryStore);

        var core = CognitivePipelineBuilder.create(mood, drives)
                                           .mindMapStore(mindMapStore)
                                           .domainActivation(domainActivation)
                                           .build();

        assertThat(core).isNotNull();
        assertThat(core.promptSections()).isNotNull();
    }

    @Test
    void buildWithDomainActivationButNoMindMapStoreIsNoOp() {
        var mindMapStore     = new io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore();
        var memoryStore      = createMemoryStore();
        var domainActivation = new io.casehub.neocortex.cognitive.index.DomainActivation(mindMapStore, memoryStore);

        var core = CognitivePipelineBuilder.create(mood, drives)
                                           .domainActivation(domainActivation)
                                           .build();

        assertThat(core).isNotNull();
        assertThat(core.promptSections()).isNotNull();
    }

    @Test
    void cognitiveProfileProducesEntityKnowledgeSectionAfterTick() {
        var mindMapStore = new io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore();
        var memoryStore  = createMemoryStore();
        var tenantId     = "test-tenant";

        var sgId = mindMapStore.createSubgraph(
                new io.casehub.neocortex.mindmap.SubgraphInput("people", io.casehub.neocortex.mindmap.SubgraphTypes.PERSON, null), tenantId);
        mindMapStore.addNode(io.casehub.neocortex.mindmap.NodeInput.of("Alice", sgId), tenantId);

        var profile = new io.casehub.neocortex.cognitive.index.CognitiveProfile(mindMapStore, memoryStore);

        var core = CognitivePipelineBuilder.create(mood, drives)
                                           .config(CognitionConfig.all())
                                           .cognitiveProfile(profile)
                                           .build();

        SubjectResolver resolver = (agentId, tid) -> java.util.Set.of("Alice");
        core.tick("agent-1", tenantId, null, resolver);

        var sections = core.promptSections();
        var hasEntityKnowledge = sections.stream()
                                         .anyMatch(s -> s instanceof io.casehub.neocortex.cognition.prompt.EntityKnowledgePromptSection);
        assertThat(hasEntityKnowledge)
                .as("EntityKnowledgePromptSection should be present after tick with known entity")
                .isTrue();
    }

    @Test
    void cognitiveProfileSectionAbsentWhenEntityKnowledgeDisabled() {
        var mindMapStore = new io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore();
        var memoryStore  = createMemoryStore();
        var tenantId     = "test-tenant";

        var sgId = mindMapStore.createSubgraph(
                new io.casehub.neocortex.mindmap.SubgraphInput("people", io.casehub.neocortex.mindmap.SubgraphTypes.PERSON, null), tenantId);
        mindMapStore.addNode(io.casehub.neocortex.mindmap.NodeInput.of("Bob", sgId), tenantId);

        var profile = new io.casehub.neocortex.cognitive.index.CognitiveProfile(mindMapStore, memoryStore);
        var config  = CognitionConfig.all().with("entityKnowledge", false);

        var core = CognitivePipelineBuilder.create(mood, drives)
                                           .config(config)
                                           .cognitiveProfile(profile)
                                           .build();

        SubjectResolver resolver = (agentId, tid) -> java.util.Set.of("Bob");
        core.tick("agent-1", tenantId, null, resolver);

        var sections = core.promptSections();
        var hasEntityKnowledge = sections.stream()
                                         .anyMatch(s -> s instanceof io.casehub.neocortex.cognition.prompt.EntityKnowledgePromptSection);
        assertThat(hasEntityKnowledge)
                .as("EntityKnowledgePromptSection should be absent when entityKnowledgeEnabled=false")
                .isFalse();
    }


}

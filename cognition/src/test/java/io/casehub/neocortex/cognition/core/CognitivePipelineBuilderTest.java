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

}

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
package io.casehub.neocortex.cognitive.eidos;

import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.cognitive.index.DescriptorView;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import io.casehub.neocortex.cognitive.index.WeightedTerm;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PersonalityDifferentiationTest {

    @Test
    void differentPersonalitiesProduceDifferentCognitiveDefaults() {
        var registry = CognitiveDefaultsRegistry.forTesting();
        var bridge = new EidosCognitiveProfileBridge(registry);

        bridge.registerFromDescriptor(DescriptorView.of(
                "hooded-claw",
                new DispositionAxes("competitive", "flexible", "bold", "high", "competitive"),
                List.of(new WeightedTerm("Se", 0.9), new WeightedTerm("Te", 0.7)),
                List.of("defeat enemies", "seize control")
        ));

        bridge.registerFromDescriptor(DescriptorView.of(
                "penelope-pitstop",
                new DispositionAxes("cooperative", "strict", "conservative", "moderate", "cooperative"),
                List.of(new WeightedTerm("Fe", 0.9), new WeightedTerm("Fi", 0.7)),
                List.of("help others", "win fairly")
        ));

        var claw = registry.forAgent("hooded-claw").orElseThrow();
        var penelope = registry.forAgent("penelope-pitstop").orElseThrow();

        assertNotEquals(claw.moodBaseline(), penelope.moodBaseline());
        assertNotEquals(claw.cbrStrategy(), penelope.cbrStrategy());
        assertNotEquals(claw.socialCognition(), penelope.socialCognition());
        assertNotEquals(claw.graphStructure(), penelope.graphStructure());

        assertNotNull(claw.personalityProfile());
        assertNotNull(claw.moodBaseline());
        assertNotNull(claw.curiosity());
        assertNotNull(claw.cbrStrategy());
        assertNotNull(claw.socialCognition());
        assertNotNull(claw.graphStructure());
        assertNotNull(claw.extractionBias());
        assertNotNull(claw.appraisalWeights());
        assertNotNull(claw.habituationConfig());
    }
}

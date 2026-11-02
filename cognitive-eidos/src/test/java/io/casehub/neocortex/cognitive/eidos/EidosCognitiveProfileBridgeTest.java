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
import io.casehub.neocortex.cognitive.index.CognitiveDerivationEngine;
import io.casehub.neocortex.cognitive.index.DescriptorView;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import io.casehub.neocortex.cognitive.index.WeightedTerm;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EidosCognitiveProfileBridgeTest {

    @Test
    void bridgeRegistersProfileForEachDescriptor() {
        var registry = CognitiveDefaultsRegistry.forTesting();

        var view = DescriptorView.of(
                "muttley",
                new DispositionAxes("competitive", "low", "high", "low", "aggressive"),
                List.of(new WeightedTerm("Se", 0.8), new WeightedTerm("Te", 0.6)),
                List.of("cause chaos", "avoid punishment")
        );

        var bridge = new EidosCognitiveProfileBridge(registry);
        bridge.registerFromDescriptor(view);

        var profile = registry.forAgent("muttley");
        assertTrue(profile.isPresent(), "profile should be registered");
        assertEquals("muttley", profile.get().agentId());
        assertNotNull(profile.get().personalityProfile());
        assertNotNull(profile.get().moodBaseline());
    }

    @Test
    void derivedPersonalityWeightsReflectDisposition() {
        var registry = CognitiveDefaultsRegistry.forTesting();

        var muttley = DescriptorView.of(
                "muttley",
                new DispositionAxes("competitive", "low", "high", "low", "aggressive"),
                List.of(new WeightedTerm("Se", 0.8), new WeightedTerm("Te", 0.6)),
                List.of("cause chaos")
        );

        var penelope = DescriptorView.of(
                "penelope",
                new DispositionAxes("nurturing", "high", "low", "moderate", "diplomatic"),
                List.of(new WeightedTerm("Fe", 0.9), new WeightedTerm("Fi", 0.7)),
                List.of("help others", "stay safe")
        );

        var bridge = new EidosCognitiveProfileBridge(registry);
        bridge.registerFromDescriptor(muttley);
        bridge.registerFromDescriptor(penelope);

        var muttleyProfile = registry.forAgent("muttley").orElseThrow();
        var penelopeProfile = registry.forAgent("penelope").orElseThrow();

        assertNotEquals(
                muttleyProfile.personalityProfile().personality(),
                penelopeProfile.personalityProfile().personality(),
                "different dispositions should produce different personality weights"
        );

        assertNotEquals(
                muttleyProfile.moodBaseline(),
                penelopeProfile.moodBaseline(),
                "different dispositions should produce different mood baselines"
        );
    }

    @Test
    void existingYamlProfileIsNotOverwritten() {
        var existing = CognitiveDerivationEngine.derive(
                DescriptorView.of(
                        "muttley",
                        new DispositionAxes("nurturing", "high", "low", "moderate", "diplomatic"),
                        List.of(new WeightedTerm("Fe", 0.9)),
                        List.of("be nice")
                )
        );
        var registry = CognitiveDefaultsRegistry.forTesting(existing);

        var view = DescriptorView.of(
                "muttley",
                new DispositionAxes("competitive", "low", "high", "low", "aggressive"),
                List.of(new WeightedTerm("Se", 0.8)),
                List.of("cause chaos")
        );

        var bridge = new EidosCognitiveProfileBridge(registry);
        bridge.registerFromDescriptor(view);

        var profile = registry.forAgent("muttley").orElseThrow();
        assertEquals(existing.moodBaseline(), profile.moodBaseline(),
                "existing YAML profile should not be overwritten by eidos derivation");
    }
}

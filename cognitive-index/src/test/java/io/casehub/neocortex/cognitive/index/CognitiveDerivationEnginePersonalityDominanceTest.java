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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CognitiveDerivationEnginePersonalityDominanceTest {

    @Test
    void hcPatternProducesHighDominance() {
        var summary = new FormationPadSummary(
                0.9 * 0.9 + 0.9 * 0.9,
                0.9 + 0.9,
                2
        );
        double result = CognitiveDerivationEngine.derivePersonalityDominance(summary);
        assertThat(result).isBetween(0.8, 0.99);
    }

    @Test
    void ppPatternProducesBalancedDominance() {
        var summary = new FormationPadSummary(
                0.8 * 0.7 + 0.6 * (-0.1) + 0.7 * 0.8 + 0.5 * (-0.2),
                0.8 + 0.6 + 0.7 + 0.5,
                4
        );
        double result = CognitiveDerivationEngine.derivePersonalityDominance(summary);
        assertThat(result).isBetween(0.45, 0.7);
    }

    @Test
    void nullSummaryReturnsDefault() {
        double result = CognitiveDerivationEngine.derivePersonalityDominance(null);
        assertThat(result).isEqualTo(0.5);
    }

    @Test
    void zeroMemoriesReturnsDefault() {
        var summary = new FormationPadSummary(0.0, 0.0, 0);
        double result = CognitiveDerivationEngine.derivePersonalityDominance(summary);
        assertThat(result).isEqualTo(0.5);
    }

    @Test
    void noPositivePleasureReturnsDefault() {
        var summary = new FormationPadSummary(-1.0, 0.0, 3);
        double result = CognitiveDerivationEngine.derivePersonalityDominance(summary);
        assertThat(result).isEqualTo(0.5);
    }

    @Test
    void extremeDominanceClampedToOne() {
        var summary = new FormationPadSummary(1.0, 1.0, 1);
        double result = CognitiveDerivationEngine.derivePersonalityDominance(summary);
        assertThat(result).isEqualTo(1.0);
    }

    @Test
    void deriveIncludesPersonalityDominance() {
        var summary = new FormationPadSummary(0.9 * 0.9 + 0.9 * 0.9, 0.9 + 0.9, 2);
        var view = new DescriptorView("hc", null, List.of(), List.of(), summary);
        var defaults = CognitiveDerivationEngine.derive(view);
        assertThat(defaults.personalityDominance()).isNotNull();
        assertThat(defaults.personalityDominance()).isBetween(0.8, 0.99);
    }

    @Test
    void deriveWithoutFormationPadSummaryDefaultsToHalf() {
        var view = DescriptorView.of("test", null, List.of(), List.of());
        var defaults = CognitiveDerivationEngine.derive(view);
        assertThat(defaults.personalityDominance()).isEqualTo(0.5);
    }

    @Test
    void deriveAndMergePreservesExplicitPersonalityDominance() {
        var view = new DescriptorView("test", null, List.of(), List.of(),
                new FormationPadSummary(0.9 * 0.9 + 0.9 * 0.9, 0.9 + 0.9, 2));
        var explicit = CognitiveDefaults.empty("test")
                .withDescriptor(view)
                .withPersonalityDominance(0.3);
        var merged = CognitiveDerivationEngine.deriveAndMerge(explicit);
        assertThat(merged.personalityDominance()).isEqualTo(0.3);
    }

    @Test
    void deriveAndMergeFallsThroughToDerived() {
        var view = new DescriptorView("test", null, List.of(), List.of(),
                new FormationPadSummary(0.9 * 0.9 + 0.9 * 0.9, 0.9 + 0.9, 2));
        var explicit = CognitiveDefaults.empty("test")
                .withDescriptor(view);
        var merged = CognitiveDerivationEngine.deriveAndMerge(explicit);
        assertThat(merged.personalityDominance()).isBetween(0.8, 0.99);
    }
}

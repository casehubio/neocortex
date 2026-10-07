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
package io.casehub.neocortex.cognition.prompt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TierFilterCustomizerTest {

    @Test
    void lowArousalRendersAllTiers() {
        var customizer = customizer(0.0, 0.5);
        var result = customizer.apply(allSections());
        assertThat(result).hasSize(allSections().size());
    }

    @Test
    void highArousalSuppressesSupplementary() {
        var customizer = customizer(0.6, 0.5);
        var result = customizer.apply(allSections());
        assertThat(result).allSatisfy(s ->
                assertThat(TierFilterCustomizer.tierOf(s)).isNotEqualTo(SectionTier.SUPPLEMENTARY));
    }

    @Test
    void extremeArousalSuppressesContextualAndSupplementary() {
        var customizer = customizer(0.9, 0.5);
        var result = customizer.apply(allSections());
        assertThat(result).allSatisfy(s ->
                assertThat(TierFilterCustomizer.tierOf(s)).isEqualTo(SectionTier.CORE));
    }

    @Test
    void personalityDominantAgentSuppressesSupplementaryEarly() {
        var customizer = customizer(0.2, 0.9);
        var result = customizer.apply(allSections());
        assertThat(result).allSatisfy(s ->
                assertThat(TierFilterCustomizer.tierOf(s)).isNotEqualTo(SectionTier.SUPPLEMENTARY));
    }

    @Test
    void coreAlwaysRendersRegardlessOfArousal() {
        var customizer = customizer(1.0, 1.0);
        var result = customizer.apply(allSections());
        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(s ->
                assertThat(TierFilterCustomizer.tierOf(s)).isEqualTo(SectionTier.CORE));
    }

    @Test
    void balancedAgentRendersSupplementaryAtLowArousal() {
        var customizer = customizer(0.3, 0.5);
        var result = customizer.apply(allSections());
        assertThat(result).hasSize(allSections().size());
    }

    @Test
    void tierOfClassifiesCorrectly() {
        assertThat(TierFilterCustomizer.tierOf(new MoodPromptSection(null))).isEqualTo(SectionTier.CORE);
        assertThat(TierFilterCustomizer.tierOf(new DrivePromptSection(null))).isEqualTo(SectionTier.CORE);
        assertThat(TierFilterCustomizer.tierOf(new UserModelPromptSection(null))).isEqualTo(SectionTier.CONTEXTUAL);
        assertThat(TierFilterCustomizer.tierOf(new MentalModelPromptSection(null))).isEqualTo(SectionTier.CONTEXTUAL);
        assertThat(TierFilterCustomizer.tierOf(new StrategyPromptSection(null))).isEqualTo(SectionTier.SUPPLEMENTARY);
        assertThat(TierFilterCustomizer.tierOf(new EmergentGoalPromptSection(null))).isEqualTo(SectionTier.SUPPLEMENTARY);
    }

    @Test
    void tierOfUnwrapsDirectiveSection() {
        var wrapped = DirectiveSection.wrap(new StrategyPromptSection(null));
        assertThat(TierFilterCustomizer.tierOf(wrapped)).isEqualTo(SectionTier.SUPPLEMENTARY);
    }

    @Test
    void unknownSectionDefaultsToCore() {
        CognitionPromptRenderer unknown = ctx -> "test";
        assertThat(TierFilterCustomizer.tierOf(unknown)).isEqualTo(SectionTier.CORE);
    }

    @Test
    void setPersonalityDominanceUpdatesThresholds() {
        var customizer = customizer(0.2, 0.9);
        // scalar=0.9 → suppThreshold=0.1, arousal=0.2 > 0.1 → supplementary suppressed
        var result1 = customizer.apply(allSections());
        assertThat(result1).allSatisfy(s ->
                                               assertThat(TierFilterCustomizer.tierOf(s)).isNotEqualTo(SectionTier.SUPPLEMENTARY));

        // Update to balanced — now suppThreshold=0.5, arousal=0.2 < 0.5 → all tiers
        customizer.setPersonalityDominance(0.5);
        var result2 = customizer.apply(allSections());
        assertThat(result2).hasSize(allSections().size());
    }


    private TierFilterCustomizer customizer(double arousal, double personalityDominance) {
        return new TierFilterCustomizer(() -> arousal, personalityDominance);
    }

    private List<CognitionPromptRenderer> allSections() {
        return List.of(
                new MoodPromptSection(null),
                new DrivePromptSection(null),
                new UserModelPromptSection(null),
                new MentalModelPromptSection(null),
                new StrategyPromptSection(null),
                new EmergentGoalPromptSection(null)
        );
    }
}

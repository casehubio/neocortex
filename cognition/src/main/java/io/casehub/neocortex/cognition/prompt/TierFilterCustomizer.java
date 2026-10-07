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

import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;
import java.util.function.UnaryOperator;

public class TierFilterCustomizer implements UnaryOperator<List<CognitionPromptRenderer>> {

    private static final Map<Class<?>, SectionTier> TIER_MAP = Map.ofEntries(
            Map.entry(MoodPromptSection.class, SectionTier.CORE),
            Map.entry(DrivePromptSection.class, SectionTier.CORE),
            Map.entry(AppraisalPromptSection.class, SectionTier.CORE),
            Map.entry(BehavioralPromptSection.class, SectionTier.CORE),
            Map.entry(CharacterDrivePromptSection.class, SectionTier.CORE),
            Map.entry(NeedsPyramidPromptSection.class, SectionTier.CORE),
            Map.entry(UserModelPromptSection.class, SectionTier.CONTEXTUAL),
            Map.entry(MentalModelPromptSection.class, SectionTier.CONTEXTUAL),
            Map.entry(NarrativePromptSection.class, SectionTier.CONTEXTUAL),
            Map.entry(AttentionPromptSection.class, SectionTier.CONTEXTUAL),
            Map.entry(TemporalFocusPromptSection.class, SectionTier.CONTEXTUAL),
            Map.entry(StrategyPromptSection.class, SectionTier.SUPPLEMENTARY),
            Map.entry(EmergentGoalPromptSection.class, SectionTier.SUPPLEMENTARY),
            Map.entry(ReflectionPromptSection.class, SectionTier.SUPPLEMENTARY),
            Map.entry(ConsolidationPromptSection.class, SectionTier.SUPPLEMENTARY),
            Map.entry(ConstraintPromptSection.class, SectionTier.SUPPLEMENTARY)
    );

    private final DoubleSupplier arousalSupplier;
    private final DoubleSupplier personalityDominanceSupplier;
    private volatile double personalityDominance;

    public TierFilterCustomizer(DoubleSupplier arousalSupplier, double personalityDominance) {
        this.arousalSupplier = arousalSupplier;
        this.personalityDominanceSupplier = null;
        this.personalityDominance = personalityDominance;
    }

    public TierFilterCustomizer(DoubleSupplier arousalSupplier, DoubleSupplier personalityDominanceSupplier) {
        this.arousalSupplier = arousalSupplier;
        this.personalityDominanceSupplier = personalityDominanceSupplier;
        this.personalityDominance = 0.5;
    }

    public void setPersonalityDominance(double personalityDominance) {
        this.personalityDominance = personalityDominance;
    }

    @Override
    public List<CognitionPromptRenderer> apply(List<CognitionPromptRenderer> sections) {
        double arousal = arousalSupplier.getAsDouble();
        double pd = personalityDominanceSupplier != null ? personalityDominanceSupplier.getAsDouble() : personalityDominance;
        double suppThreshold = 1.0 - pd;
        double ctxThreshold = Math.min(suppThreshold + 0.3, 1.0);

        return sections.stream()
                .filter(s -> {
                    var tier = tierOf(s);
                    return switch (tier) {
                        case CORE -> true;
                        case CONTEXTUAL -> arousal < ctxThreshold;
                        case SUPPLEMENTARY -> arousal < suppThreshold;
                    };
                })
                .toList();
    }

    public static SectionTier tierOf(CognitionPromptRenderer section) {
        var clazz = section instanceof DirectiveSection ds
                ? ds.delegate().getClass()
                : section.getClass();
        return TIER_MAP.getOrDefault(clazz, SectionTier.CORE);
    }
}

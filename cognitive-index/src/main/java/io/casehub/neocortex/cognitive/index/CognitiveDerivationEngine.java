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

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.RetrievalMode;
import io.casehub.neocortex.memory.mood.MoodBaseline;
import io.casehub.neocortex.memory.personality.PersonalityWeights;
import io.casehub.neocortex.mindmap.CuriosityConfig;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CognitiveDerivationEngine {

    private static final Map<String, Map<String, Double>> FUNCTION_WEIGHTS = Map.of(
        "ni", Map.of("reflection", 1.5, "experience", 0.8),
        "te", Map.of("experience", 1.3, "reflection", 1.0),
        "fi", Map.of("reflection", 1.2, "relationship", 1.1),
        "fe", Map.of("relationship", 1.4, "engagement", 1.3),
        "se", Map.of("experience", 1.5, "mood", 0.7),
        "si", Map.of("experience", 1.2, "reflection", 0.9),
        "ne", Map.of("reflection", 1.3, "experience", 1.1),
        "ti", Map.of("reflection", 1.4, "experience", 0.9)
    );

    private static final Map<String, Double> RISK_PLEASURE = Map.of(
        "conservative", -0.1,
        "calculated", 0.1,
        "bold", 0.3
    );

    private static final Map<String, Double> SOCIAL_AROUSAL = Map.of(
        "independent", -0.1,
        "cooperative", 0.1,
        "competitive", 0.2
    );

    private static final Map<String, Double> AUTONOMY_DOMINANCE = Map.of(
        "low", -0.2,
        "moderate", 0.2,
        "high", 0.4
    );
    private static final Map<String, Double> AUTONOMY_STRUCTURAL = Map.of(
        "low", 0.8,
        "moderate", 1.0,
        "high", 1.3
    );

    private static final Map<String, Double> RULE_QUALITY = Map.of(
        "flexible", 0.8,
        "moderate", 1.0,
        "strict", 1.3
    );

    private static final Map<String, Double> SOCIAL_CENTRALITY = Map.of(
        "independent", 0.8,
        "cooperative", 1.2,
        "competitive", 1.0
    );

    private static final Map<String, String> GOAL_SUBGRAPH_MAPPING = Map.of(
        "career", "PROJECT",
        "professional", "PROJECT",
        "work", "PROJECT",
        "family", "PERSON",
        "personal", "PERSON",
        "relationship", "PERSON",
        "research", "RESEARCH_AREA",
        "academic", "RESEARCH_AREA",
        "business", "ORGANISATION"
    );

    private static final Map<String, Double> RULE_MIN_SIMILARITY = Map.of(
        "flexible", 0.35,
        "moderate", 0.5,
        "strict", 0.65
    );

    private static final Map<String, Integer> RISK_DECAY_DAYS = Map.of(
        "conservative", 60,
        "calculated", 90,
        "bold", 180
    );

    private static final Map<String, RetrievalMode> RULE_RETRIEVAL_MODE = Map.of(
        "flexible", RetrievalMode.SEMANTIC_ONLY,
        "moderate", RetrievalMode.HYBRID,
        "strict", RetrievalMode.FEATURE_ONLY
    );

    private static final Map<String, Double> SOCIAL_TRUST_RATE = Map.of(
        "independent", 0.3,
        "cooperative", 0.7,
        "competitive", 0.4
    );

    private static final Map<String, ConflictInterpretation> CONFLICT_INTERPRETATION = Map.of(
        "cooperative", ConflictInterpretation.REPAIR,
        "competitive", ConflictInterpretation.INFORMATION,
        "analytical", ConflictInterpretation.NEUTRAL,
        "avoidant", ConflictInterpretation.DISENGAGE
    );

    private CognitiveDerivationEngine() {}

    public static CognitiveDefaults derive(DescriptorView descriptor) {
        PersonalityWeights  personality   = derivePersonality(descriptor.dispositionProfile());
        MoodBaseline        mood          = deriveMoodBaseline(descriptor.disposition());
        CuriosityConfig     curiosity     = deriveCuriosity(descriptor.disposition(), descriptor.goals());
        TemporalFocusConfig temporalFocus = deriveTemporalFocus(descriptor.goals());
        CbrStrategyDefaults     cbrStrategy     = deriveCbrStrategy(descriptor.disposition());
        SocialCognitionDefaults socialCognition  = deriveSocialCognition(descriptor.disposition());
        GraphStructureDefaults  graphStructure   = deriveGraphStructure(descriptor.dispositionProfile());
        ExtractionBiasDefaults  extractionBias   = deriveExtractionBias(descriptor.dispositionProfile());

        return CognitiveDefaults.empty(descriptor.agentId())
                .withPersonality(personality)
                .withMoodBaseline(mood)
                .withCuriosity(curiosity)
                .withTemporalFocus(temporalFocus)
                .withCbrStrategy(cbrStrategy)
                .withSocialCognition(socialCognition)
                .withGraphStructure(graphStructure)
                .withExtractionBias(extractionBias);
    }

    public static CognitiveDefaults deriveAndMerge(CognitiveDefaults explicit) {
        if (explicit.descriptor() == null) {
            return explicit;
        }
        CognitiveDefaults derived = derive(explicit.descriptor());
        return CognitiveDefaults.empty(explicit.agentId())
                .withTenantId(explicit.tenantId())
                .withPersonality(explicit.personality() != null ? explicit.personality() : derived.personality())
                .withMoodBaseline(explicit.moodBaseline() != null ? explicit.moodBaseline() : derived.moodBaseline())
                .withCuriosity(explicit.curiosity() != null ? explicit.curiosity() : derived.curiosity())
                .withTemporalFocus(explicit.temporalFocus() != null ? explicit.temporalFocus() : derived.temporalFocus())
                .withCbrStrategy(explicit.cbrStrategy() != null ? explicit.cbrStrategy() : derived.cbrStrategy())
                .withSocialCognition(explicit.socialCognition() != null ? explicit.socialCognition() : derived.socialCognition())
                .withGraphStructure(explicit.graphStructure() != null ? explicit.graphStructure() : derived.graphStructure())
                .withExtractionBias(explicit.extractionBias() != null ? explicit.extractionBias() : derived.extractionBias())
                .withVocabulary(explicit.vocabulary())
                .withServices(explicit.services())
                .withTraitRules(explicit.traitRules())
                .withDerivedEdgeRules(explicit.derivedEdgeRules())
                .withDescriptor(explicit.descriptor());
    }


    static PersonalityWeights derivePersonality(List<WeightedTerm> profile) {
        if (profile == null || profile.isEmpty()) {
            return null;
        }

        // Collect all domains mentioned by any function in the profile
        var    allDomains  = new LinkedHashSet<String>();
        double totalWeight = 0.0;
        for (WeightedTerm term : profile) {
            Map<String, Double> functionMap = FUNCTION_WEIGHTS.get(term.term().toLowerCase());
            if (functionMap != null) {
                allDomains.addAll(functionMap.keySet());
                totalWeight += term.weight();
            }
        }
        if (totalWeight == 0.0) {return null;}

        // Weighted average per domain — absent function defaults to 1.0 (neutral)
        Map<MemoryDomain, Double> domainWeights = new HashMap<>();
        for (String domain : allDomains) {
            double weightedSum = 0.0;
            for (WeightedTerm term : profile) {
                Map<String, Double> functionMap = FUNCTION_WEIGHTS.get(term.term().toLowerCase());
                if (functionMap == null) {continue;}
                double domainValue = functionMap.getOrDefault(domain, 1.0);
                weightedSum += term.weight() * domainValue;
            }
            double result = weightedSum / totalWeight;
            if (result > 0.0) {
                domainWeights.put(new MemoryDomain(domain), result);
            }
        }

        return domainWeights.isEmpty() ? null : new PersonalityWeights(domainWeights);
    }

    static MoodBaseline deriveMoodBaseline(DispositionAxes axes) {
        if (axes == null) return null;

        double pleasure = RISK_PLEASURE.getOrDefault(
            lower(axes.riskAppetite()), 0.0);
        double arousal = SOCIAL_AROUSAL.getOrDefault(
            lower(axes.socialOrient()), 0.0);
        double dominance = AUTONOMY_DOMINANCE.getOrDefault(
            lower(axes.autonomy()), 0.0);

        return new MoodBaseline(pleasure, arousal, dominance);
    }

    static CuriosityConfig deriveCuriosity(DispositionAxes axes, List<String> goals) {
        if (axes == null) {return null;}

        Map<String, Double> weights = new HashMap<>();
        weights.put("STRUCTURAL", AUTONOMY_STRUCTURAL.getOrDefault(lower(axes.autonomy()), 1.0));
        weights.put("QUALITY", RULE_QUALITY.getOrDefault(lower(axes.ruleFollowing()), 1.0));
        weights.put("CENTRALITY", SOCIAL_CENTRALITY.getOrDefault(lower(axes.socialOrient()), 1.0));

        if (goals != null) {
            for (String goal : goals) {
                if (goal != null && goal.toLowerCase().contains("strategic")) {
                    weights.merge("CENTRALITY", 0.2, Double::sum);
                    weights.merge("STRUCTURAL", 0.2, Double::sum);
                    break;
                }
            }
        }

        return CuriosityConfig.defaults().withCategoryWeights(weights);
    }

    static TemporalFocusConfig deriveTemporalFocus(List<String> goals) {
        if (goals == null || goals.isEmpty()) {return null;}

        Map<String, Double> weights = new HashMap<>();
        for (String goal : goals) {
            if (goal == null) {continue;}
            String lowerGoal = goal.toLowerCase();
            for (var entry : GOAL_SUBGRAPH_MAPPING.entrySet()) {
                if (lowerGoal.contains(entry.getKey())) {
                    weights.merge(entry.getValue(), 0.3, Double::sum);
                }
            }
        }

        if (weights.isEmpty()) {return null;}

        weights.replaceAll((k, v) -> 1.0 + v);
        return TemporalFocusConfig.defaults().withSubgraphProximityWeights(weights);
    }

    static CbrStrategyDefaults deriveCbrStrategy(DispositionAxes axes) {
        if (axes == null) return null;

        double minSimilarity = RULE_MIN_SIMILARITY.getOrDefault(lower(axes.ruleFollowing()), 0.5);
        int decayDays = RISK_DECAY_DAYS.getOrDefault(lower(axes.riskAppetite()), 90);
        RetrievalMode mode = RULE_RETRIEVAL_MODE.getOrDefault(lower(axes.ruleFollowing()), RetrievalMode.HYBRID);

        return new CbrStrategyDefaults(minSimilarity, decayDays, mode);
    }

    static SocialCognitionDefaults deriveSocialCognition(DispositionAxes axes) {
        if (axes == null) return null;

        double trustRate = SOCIAL_TRUST_RATE.getOrDefault(lower(axes.socialOrient()), 0.5);
        ConflictInterpretation conflictMode = CONFLICT_INTERPRETATION.getOrDefault(lower(axes.conflictMode()), ConflictInterpretation.NEUTRAL);

        return new SocialCognitionDefaults(trustRate, conflictMode);
    }

    private static final Set<String> HOLISTIC_FUNCTIONS = Set.of("ni", "fe", "ne", "fi");

    static GraphStructureDefaults deriveGraphStructure(List<WeightedTerm> profile) {
        if (profile == null || profile.isEmpty()) return null;

        double holisticWeight = 0.0;
        double totalWeight = 0.0;
        for (WeightedTerm term : profile) {
            totalWeight += term.weight();
            if (HOLISTIC_FUNCTIONS.contains(term.term().toLowerCase())) {
                holisticWeight += term.weight();
            }
        }
        if (totalWeight == 0.0) return null;

        double connectiveBias = holisticWeight / totalWeight;
        GraphStructureDefaults.InferenceStyle style;
        if (connectiveBias > 0.6) {
            style = GraphStructureDefaults.InferenceStyle.CONNECTIVE;
        } else if (connectiveBias < 0.4) {
            style = GraphStructureDefaults.InferenceStyle.CATEGORICAL;
        } else {
            style = GraphStructureDefaults.InferenceStyle.BALANCED;
        }

        return new GraphStructureDefaults(style, connectiveBias);
    }

    private static final Set<String> ANALYTICAL_FUNCTIONS = Set.of("ni", "te", "ti", "si");
    private static final Set<String> EMPATHETIC_FUNCTIONS = Set.of("fe", "fi");

    static ExtractionBiasDefaults deriveExtractionBias(List<WeightedTerm> profile) {
        if (profile == null || profile.isEmpty()) return null;

        double analyticalWeight = 0.0;
        double empatheticWeight = 0.0;
        double totalWeight = 0.0;
        for (WeightedTerm term : profile) {
            String fn = term.term().toLowerCase();
            totalWeight += term.weight();
            if (ANALYTICAL_FUNCTIONS.contains(fn)) analyticalWeight += term.weight();
            if (EMPATHETIC_FUNCTIONS.contains(fn)) empatheticWeight += term.weight();
        }
        if (totalWeight == 0.0) return null;

        double relationshipBias = 0.8 + 0.6 * (analyticalWeight / totalWeight);
        double affectSensitivity = 0.8 + 0.6 * (empatheticWeight / totalWeight);

        return new ExtractionBiasDefaults(relationshipBias, affectSensitivity);
    }

    private static String lower(String s) {
        return s != null ? s.toLowerCase() : "";
    }
}

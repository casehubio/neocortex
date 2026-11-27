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

import io.casehub.neocortex.cognitive.ChannelModulationConfig;
import io.casehub.neocortex.cognitive.CognitiveCapacityConfig;
import io.casehub.neocortex.cognitive.CognitiveResourcePoolConfig;
import io.casehub.neocortex.cognitive.HabituationConfig;
import io.casehub.neocortex.cognitive.RecurringThoughtConfig;
import io.casehub.neocortex.cognitive.ExtremeInteractionConfig;
import io.casehub.neocortex.cognitive.SearchBudgetConfig;
import io.casehub.neocortex.cognitive.SearchFatigueEffects;
import io.casehub.neocortex.cognitive.observability.DerivationObserver;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.RetrievalMode;
import io.casehub.neocortex.memory.mood.MoodBaseline;
import io.casehub.neocortex.memory.personality.PersonalityWeights;
import io.casehub.neocortex.mindmap.AppraisalWeights;
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
        "measured", 0.1,
        "bold", 0.3
    );

    private static final Map<String, Double> SOCIAL_AROUSAL = Map.of(
        "independent", -0.1,
        "collaborative", 0.1
    );

    private static final Map<String, Double> AUTONOMY_DOMINANCE = Map.of(
        "directed", -0.2,
        "semi-autonomous", 0.2,
        "autonomous", 0.4
    );
    private static final Map<String, Double> AUTONOMY_STRUCTURAL = Map.of(
        "directed", 0.8,
        "semi-autonomous", 1.0,
        "autonomous", 1.3
    );

    private static final Map<String, Double> RULE_QUALITY = Map.of(
        "flexible", 0.8,
        "principled", 1.0,
        "strict", 1.3
    );

    private static final Map<String, Double> SOCIAL_CENTRALITY = Map.of(
        "independent", 0.8,
        "collaborative", 1.2
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
        "principled", 0.5,
        "strict", 0.65
    );

    private static final Map<String, Integer> RISK_DECAY_DAYS = Map.of(
        "conservative", 60,
        "measured", 90,
        "bold", 180
    );
    private static final Map<String, Double>  RISK_HABITUATION_RATE = Map.of(
            "conservative", 0.1,
            "measured", 0.2,
            "bold", 0.4
                                                                            );
    private static final Map<String, Double>  RISK_NOVELTY_THRESHOLD = Map.of(
            "conservative", 0.2,
            "measured", 0.3,
            "bold", 0.5
                                                                             );
    private static final Map<String, Double>  RULE_REPETITION_TOLERANCE = Map.of(
            "flexible", 3.0,
            "principled", 5.0,
            "strict", 8.0
                                                                                );


    private static final Map<String, RetrievalMode> RULE_RETRIEVAL_MODE = Map.of(
        "flexible", RetrievalMode.SEMANTIC_ONLY,
        "principled", RetrievalMode.HYBRID,
        "strict", RetrievalMode.FEATURE_ONLY
    );

    private static final Map<String, Double> SOCIAL_TRUST_RATE = Map.of(
        "independent", 0.3,
        "collaborative", 0.7
    );

    private static final Map<String, ConflictInterpretation> CONFLICT_INTERPRETATION = Map.of(
        "collaborating", ConflictInterpretation.REPAIR,
        "competing", ConflictInterpretation.INFORMATION,
        "compromising", ConflictInterpretation.NEUTRAL,
        "avoiding", ConflictInterpretation.DISENGAGE
    );
    private static final Map<String, Double>                 REAPPRAISAL_CONFLICT    = Map.of(
            "compromising", 0.30, "collaborating", 0.15, "avoiding", -0.15, "competing", 0.05);
    private static final Map<String, Double>                 REAPPRAISAL_AUTONOMY    = Map.of(
            "autonomous", 0.25, "semi-autonomous", 0.05, "directed", -0.10);
    private static final Map<String, Double>                 REAPPRAISAL_RULES       = Map.of(
            "strict", 0.15, "flexible", 0.15, "principled", 0.05);
    private static final Map<String, Double>                 REAPPRAISAL_RISK        = Map.of(
            "bold", 0.20, "measured", 0.05, "conservative", -0.10);
    private static final Map<String, Double>                 REAPPRAISAL_SOCIAL      = Map.of(
            "collaborative", 0.10, "independent", 0.05);

    private static final Map<String, Double> SUPPRESSION_CONFLICT = Map.of(
            "avoiding", 0.10, "competing", 0.15, "compromising", -0.10, "collaborating", -0.05);
    private static final Map<String, Double> SUPPRESSION_SOCIAL   = Map.of(
            "independent", 0.05, "collaborative", -0.05);
    private static final Map<String, Double> SUPPRESSION_RISK     = Map.of(
            "conservative", 0.15, "measured", 0.05, "bold", -0.10);
    private static final Map<String, Double> SUPPRESSION_AUTONOMY = Map.of(
            "directed", 0.10, "semi-autonomous", 0.05, "autonomous", -0.05);
    private static final Map<String, Double> SUPPRESSION_RULES    = Map.of(
            "strict", 0.05, "principled", 0.0, "flexible", -0.05);

    private static final Map<String, Double> REDIRECT_CONFLICT = Map.of(
            "avoiding", 0.25, "collaborating", 0.05, "compromising", -0.15, "competing", -0.10);
    private static final Map<String, Double> REDIRECT_RISK     = Map.of(
            "conservative", 0.20, "measured", 0.05, "bold", -0.15);
    private static final Map<String, Double> REDIRECT_AUTONOMY = Map.of(
            "directed", 0.10, "semi-autonomous", 0.05, "autonomous", -0.10);
    private static final Map<String, Double> REDIRECT_SOCIAL   = Map.of(
            "independent", 0.05, "collaborative", 0.0);
    private static final Map<String, Double> REDIRECT_RULES    = Map.of(
            "strict", 0.0, "principled", 0.0, "flexible", -0.10);
    private static final Map<String, Double> SOCIAL_REG_SOCIAL = Map.of(
            "gregarious", 0.20, "reserved", -0.15, "selective", 0.05,
            "collaborative", 0.10, "independent", -0.10);
    private static final Map<String, Double> SOCIAL_REG_CONFLICT = Map.of(
            "harmonizing", 0.15, "confrontational", -0.10, "compromising", 0.0, "avoiding", -0.05);
    private static final Map<String, Double> SOCIAL_REG_AUTONOMY = Map.of(
            "independent", -0.10, "collaborative", 0.10, "dependent", 0.15,
            "autonomous", -0.10, "semi-autonomous", 0.05, "directed", 0.10);


    private CognitiveDerivationEngine() {}


    static final class Ctx {
        static final  Ctx                                                 NOOP         = new Ctx("", "", DerivationObserver.NOOP);
        final         String                                              agentId;
        final         String                                              eventRef;
        final         DerivationObserver                                  observer;
        private final boolean                                             active;
        private       String                                              currentMethod;
        private final java.util.LinkedHashMap<String, Double>             baseValues   = new java.util.LinkedHashMap<>();
        private final java.util.ArrayList<DerivationObserver.FacetEffect> facetEffects = new java.util.ArrayList<>();
        private final java.util.LinkedHashMap<String, Double>             finalValues  = new java.util.LinkedHashMap<>();

        Ctx(String agentId, String eventRef, DerivationObserver observer) {
            this.agentId  = agentId;
            this.eventRef = eventRef;
            this.observer = observer;
            this.active   = observer != DerivationObserver.NOOP;
        }

        void stageStart(String method) {
            if (!active) {return;}
            currentMethod = method;
            baseValues.clear();
            facetEffects.clear();
            finalValues.clear();
        }

        void base(String field, double value) {if (active) {baseValues.put(field, value);}}

        void mod(String facet, double facetScore, String field, double before, double after) {
            if (active && before != after) {
                facetEffects.add(new DerivationObserver.FacetEffect(facet, facetScore, field, before, after));
            }
        }

        void output(String field, double value) {if (active) {finalValues.put(field, value);}}

        void stageEnd() {
            if (!active) {return;}
            observer.onStageComputation(agentId, eventRef,
                                        new DerivationObserver.StageComputation(currentMethod,
                                                                                java.util.Map.copyOf(baseValues),
                                                                                java.util.List.copyOf(facetEffects),
                                                                                java.util.Map.copyOf(finalValues)));
        }

        void flow(String sourceMethod, String sourceField, String targetMethod, String targetField, double value) {
            if (!active) {return;}
            observer.onDataFlow(agentId, eventRef,
                                new DerivationObserver.DataFlow(sourceMethod, sourceField, targetMethod, targetField, value));
        }
    }

    public static CognitiveDefaults derive(DescriptorView descriptor) {
        return derive(descriptor, DerivationObserver.NOOP);
    }

    public static CognitiveDefaults derive(DescriptorView descriptor, DerivationObserver observer) {
        return derive(descriptor, "init", observer);
    }

    public static CognitiveDefaults derive(DescriptorView descriptor, String eventRef, DerivationObserver observer) {
        var ctx    = new Ctx(descriptor.agentId(), eventRef, observer);
        var facets = descriptor.personalityFacets();

        observer.onPipelineStart(descriptor.agentId(), eventRef, new DerivationObserver.PipelineInput(
                descriptor.disposition() != null ? descriptor.disposition().toString() : null,
                descriptor.dispositionProfile().stream().map(t -> t.term() + "=" + t.weight()).toList(),
                facets.stream().map(f -> f.facet() + "=" + f.score()).toList(),
                descriptor.goals()));

        PersonalityWeights      personality     = derivePersonality(descriptor.dispositionProfile());
        MoodBaseline            mood            = deriveMoodBaseline(descriptor.disposition(), facets, ctx);
        CuriosityConfig         curiosity       = deriveCuriosity(descriptor.disposition(), descriptor.goals(), facets, ctx);
        TemporalFocusConfig     temporalFocus   = deriveTemporalFocus(descriptor.goals());
        CbrStrategyDefaults     cbrStrategy     = deriveCbrStrategy(descriptor.disposition(), facets, ctx);
        SocialCognitionDefaults socialCognition = deriveSocialCognition(descriptor.disposition(), facets, ctx);
        GraphStructureDefaults  graphStructure  = deriveGraphStructure(descriptor.dispositionProfile(), facets, ctx);
        ExtractionBiasDefaults  extractionBias  = deriveExtractionBias(descriptor.dispositionProfile(), facets, ctx);
        AppraisalWeights        appraisal       = deriveAppraisalWeights(descriptor.dispositionProfile(), descriptor.disposition(), facets, ctx);
        HabituationConfig       habituation     = deriveHabituationConfig(descriptor.disposition(), descriptor.goals(), facets, ctx);
        RegulationDefaults      regulation      = deriveRegulationDefaults(descriptor.disposition(), facets, ctx);
        CopingDefaults          coping          = deriveCopingDefaults(descriptor.disposition(), facets, ctx);

        if (graphStructure != null) {
            ctx.flow("deriveGraphStructure", "connectiveBias", "deriveSearchBudgetDefaults", "connectiveBias", graphStructure.connectiveBias());
        }
        SearchBudgetConfig          searchBudget     = deriveSearchBudgetDefaults(facets, descriptor.disposition(), graphStructure != null ? graphStructure.connectiveBias() : 0.5, ctx);
        CognitiveResourcePoolConfig poolConfig       = derivePoolConfig(facets, descriptor.disposition(), ctx);
        RecurringThoughtConfig      recurringThought = deriveRecurringThoughtConfig(facets, descriptor.disposition(), ctx);
        CognitiveCapacityConfig     capacity         = deriveCapacity(descriptor.dispositionProfile(), facets, ctx);
        double                      personalityDom   = derivePersonalityDominance(descriptor.formationPadSummary());
        ExpressionStyleDefaults     expressionStyles = deriveExpressionStyles(facets, ctx);
        PersonalityProfile          personalityProf  = new PersonalityProfile(personality, personalityDom, expressionStyles, null, facets);

        var emotionDefaults = new EmotionDefaults(mood, appraisal, regulation, coping);
        var resourceDefaults = new CognitiveResourceDefaults(capacity, poolConfig, searchBudget, recurringThought, habituation);
        var graphDefs = new GraphDefaults(null, Map.of(), null, null, graphStructure, extractionBias);
        var behavioralDefaults = new BehavioralDefaults(curiosity, temporalFocus, socialCognition, cbrStrategy);

        observer.onPipelineComplete(descriptor.agentId(), eventRef);

        return new CognitiveDefaults(descriptor.agentId(), null, personalityProf, descriptor,
                emotionDefaults, resourceDefaults, graphDefs, behavioralDefaults, null);
    }


    static CognitiveCapacityConfig deriveCapacity(List<WeightedTerm> profile) {
        return deriveCapacity(profile, List.of());
    }

    // Tier 1: Impulsivity degrades WM — Heydarloo et al. (2024), Kofler et al. (2024)
// Tier 2: Anxiousness narrows associative reach — attentional narrowing via Negative Affectivity (McCrae & Costa 1989, Gore & Widiger 2013)
    static CognitiveCapacityConfig deriveCapacity(List<WeightedTerm> profile, List<PersonalityFacet> facets) {
        return deriveCapacity(profile, facets, Ctx.NOOP);
    }

    static CognitiveCapacityConfig deriveCapacity(List<WeightedTerm> profile, List<PersonalityFacet> facets, Ctx ctx) {
        if (profile == null || profile.isEmpty()) {return null;}
        ctx.stageStart("deriveCapacity");
        double tiWeight = 0.0, teWeight = 0.0, neWeight = 0.0, niWeight = 0.0, siWeight = 0.0;
        for (WeightedTerm term : profile) {
            String fn = term.term().toLowerCase();
            switch (fn) {
                case "ti" -> tiWeight = term.weight();
                case "te" -> teWeight = term.weight();
                case "ne" -> neWeight = term.weight();
                case "ni" -> niWeight = term.weight();
                case "si" -> siWeight = term.weight();
            }
        }
        double relational  = compressCapacity(0.5 * (tiWeight + teWeight));
        double associative = compressCapacity(0.5 * (neWeight + niWeight));
        double wmSpan      = compressCapacity(0.5 * (teWeight + siWeight));
        ctx.base("relationalComplexity", relational);
        ctx.base("associativeReach", associative);
        ctx.base("workingMemorySpan", wmSpan);
        if (facets != null && !facets.isEmpty()) {
            double im  = facetScore(facets, PersonalityFacet.IMPULSIVITY, 0.5);
            double ax  = facetScore(facets, PersonalityFacet.ANXIOUSNESS, 0.5);
            double wm0 = wmSpan, ar0 = associative;
            wmSpan -= (im - 0.5) * 0.2;
            associative -= (ax - 0.5) * 0.2;
            ctx.mod(PersonalityFacet.IMPULSIVITY, im, "workingMemorySpan", wm0, wmSpan);
            ctx.mod(PersonalityFacet.ANXIOUSNESS, ax, "associativeReach", ar0, associative);
            // Depressivity — Nikolin 2021 meta (d=0.34-0.65)
            double dp = facetScore(facets, PersonalityFacet.DEPRESSIVITY, 0.5);
            wm0 = wmSpan; wmSpan -= (dp - 0.5) * 0.1;
            ctx.mod(PersonalityFacet.DEPRESSIVITY, dp, "workingMemorySpan", wm0, wmSpan);
            // Distractibility — Martinussen 2005 meta (d=0.85-1.06 spatial)
            double dt = facetScore(facets, PersonalityFacet.DISTRACTIBILITY, 0.5);
            wm0 = wmSpan; wmSpan -= (dt - 0.5) * 0.2;
            ctx.mod(PersonalityFacet.DISTRACTIBILITY, dt, "workingMemorySpan", wm0, wmSpan);
            // Cognitive Perceptual Dysregulation — Siddi 2017 meta (67 studies)
            double cd = facetScore(facets, PersonalityFacet.COGNITIVE_PERCEPTUAL_DYSREGULATION, 0.5);
            wm0 = wmSpan; wmSpan -= (cd - 0.5) * 0.1;
            ctx.mod(PersonalityFacet.COGNITIVE_PERCEPTUAL_DYSREGULATION, cd, "workingMemorySpan", wm0, wmSpan);
            // Perseveration — BMC Psychology 2026 (set-shifting -> divergent thinking)
            double pv = facetScore(facets, PersonalityFacet.PERSEVERATION, 0.5);
            ar0 = associative; associative -= (pv - 0.5) * 0.1;
            ctx.mod(PersonalityFacet.PERSEVERATION, pv, "associativeReach", ar0, associative);
        }
        ctx.output("relationalComplexity", relational);
        ctx.output("associativeReach", associative);
        ctx.output("workingMemorySpan", wmSpan);
        ctx.stageEnd();
        return new CognitiveCapacityConfig(relational, associative, wmSpan);
    }


    private static double compressCapacity(double raw) {
        return 0.55 + raw * 0.4;
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

    static MoodBaseline deriveMoodBaseline(DispositionAxes axes, List<PersonalityFacet> facets) {
        return deriveMoodBaseline(axes, facets, Ctx.NOOP);
    }

    static MoodBaseline deriveMoodBaseline(DispositionAxes axes, List<PersonalityFacet> facets, Ctx ctx) {
        if (axes == null) {return null;}
        ctx.stageStart("deriveMoodBaseline");
        double pleasure  = RISK_PLEASURE.getOrDefault(lower(axes.riskAppetite()), 0.0);
        double arousal   = SOCIAL_AROUSAL.getOrDefault(lower(axes.socialOrient()), 0.0);
        double dominance = AUTONOMY_DOMINANCE.getOrDefault(lower(axes.autonomy()), 0.0);
        ctx.base("pleasure", pleasure);
        ctx.base("arousal", arousal);
        ctx.base("dominance", dominance);
        if (facets != null && !facets.isEmpty()) {
            double rt = facetScore(facets, PersonalityFacet.RISK_TAKING, 0.5);
            double el = facetScore(facets, PersonalityFacet.EMOTIONAL_LABILITY, 0.5);
            double wd = facetScore(facets, PersonalityFacet.WITHDRAWAL, 0.5);
            double p0 = pleasure;
            pleasure += (rt - 0.5) * 0.4;
            double a0 = arousal;
            arousal += (el - 0.5) * 0.4;
            double d0 = dominance;
            dominance -= (wd - 0.5) * 0.4;
            ctx.mod(PersonalityFacet.RISK_TAKING, rt, "pleasure", p0, pleasure);
            ctx.mod(PersonalityFacet.EMOTIONAL_LABILITY, el, "arousal", a0, arousal);
            ctx.mod(PersonalityFacet.WITHDRAWAL, wd, "dominance", d0, dominance);
            // Anhedonia — Meehl 1975; Germans & Kring 2000
            double an = facetScore(facets, PersonalityFacet.ANHEDONIA, 0.5);
            p0 = pleasure; pleasure -= (an - 0.5) * 0.4;
            ctx.mod(PersonalityFacet.ANHEDONIA, an, "pleasure", p0, pleasure);
            a0 = arousal; arousal -= (an - 0.5) * 0.3;
            ctx.mod(PersonalityFacet.ANHEDONIA, an, "arousal", a0, arousal);
            // Depressivity — Mehrabian 1996; Seligman 1975
            double dp = facetScore(facets, PersonalityFacet.DEPRESSIVITY, 0.5);
            p0 = pleasure; pleasure -= (dp - 0.5) * 0.4;
            ctx.mod(PersonalityFacet.DEPRESSIVITY, dp, "pleasure", p0, pleasure);
            d0 = dominance; dominance -= (dp - 0.5) * 0.4;
            ctx.mod(PersonalityFacet.DEPRESSIVITY, dp, "dominance", d0, dominance);
            // Separation Insecurity — Mikulincer & Shaver 2003
            double si = facetScore(facets, PersonalityFacet.SEPARATION_INSECURITY, 0.5);
            a0 = arousal; arousal += (si - 0.5) * 0.15;
            ctx.mod(PersonalityFacet.SEPARATION_INSECURITY, si, "arousal", a0, arousal);
            // Attention Seeking — HPD clinical literature
            double as = facetScore(facets, PersonalityFacet.ATTENTION_SEEKING, 0.5);
            a0 = arousal; arousal += (as - 0.5) * 0.1;
            ctx.mod(PersonalityFacet.ATTENTION_SEEKING, as, "arousal", a0, arousal);
        }
        ctx.output("pleasure", pleasure);
        ctx.output("arousal", arousal);
        ctx.output("dominance", dominance);
        ctx.stageEnd();
        return new MoodBaseline(pleasure, arousal, dominance);
    }


    static CuriosityConfig deriveCuriosity(DispositionAxes axes, List<String> goals, List<PersonalityFacet> facets) {
        return deriveCuriosity(axes, goals, facets, Ctx.NOOP);
    }

    static CuriosityConfig deriveCuriosity(DispositionAxes axes, List<String> goals, List<PersonalityFacet> facets, Ctx ctx) {
        if (axes == null) {return null;}
        ctx.stageStart("deriveCuriosity");
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
        ctx.base("STRUCTURAL", weights.get("STRUCTURAL"));
        ctx.base("QUALITY", weights.get("QUALITY"));
        ctx.base("CENTRALITY", weights.get("CENTRALITY"));
        if (facets != null && !facets.isEmpty()) {
            double wd = facetScore(facets, PersonalityFacet.WITHDRAWAL, 0.5);
            double im = facetScore(facets, PersonalityFacet.IMPULSIVITY, 0.5);
            double rt = facetScore(facets, PersonalityFacet.RISK_TAKING, 0.5);
            double s0 = weights.get("STRUCTURAL"), q0 = weights.get("QUALITY"), c0 = weights.get("CENTRALITY");
            weights.merge("STRUCTURAL", -(wd - 0.5) * 0.2, Double::sum);
            weights.merge("QUALITY", -(im - 0.5) * 0.2, Double::sum);
            weights.merge("CENTRALITY", (rt - 0.5) * 0.2, Double::sum);
            ctx.mod(PersonalityFacet.WITHDRAWAL, wd, "STRUCTURAL", s0, weights.get("STRUCTURAL"));
            ctx.mod(PersonalityFacet.IMPULSIVITY, im, "QUALITY", q0, weights.get("QUALITY"));
            ctx.mod(PersonalityFacet.RISK_TAKING, rt, "CENTRALITY", c0, weights.get("CENTRALITY"));
            // Anhedonia — Silvia 2006: reduced hedonic tone dampens curiosity uniformly
            double an = facetScore(facets, PersonalityFacet.ANHEDONIA, 0.5);
            double delta = -(an - 0.5) * 0.15;
            for (String key : List.of("STRUCTURAL", "QUALITY", "CENTRALITY")) {
                double before = weights.get(key);
                weights.merge(key, delta, Double::sum);
                ctx.mod(PersonalityFacet.ANHEDONIA, an, key, before, weights.get(key));
            }
        }
        ctx.output("STRUCTURAL", weights.get("STRUCTURAL"));
        ctx.output("QUALITY", weights.get("QUALITY"));
        ctx.output("CENTRALITY", weights.get("CENTRALITY"));
        ctx.stageEnd();
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

    static CbrStrategyDefaults deriveCbrStrategy(DispositionAxes axes, List<PersonalityFacet> facets) {
        return deriveCbrStrategy(axes, facets, Ctx.NOOP);
    }

    static CbrStrategyDefaults deriveCbrStrategy(DispositionAxes axes, List<PersonalityFacet> facets, Ctx ctx) {
        if (axes == null) {return null;}
        ctx.stageStart("deriveCbrStrategy");
        double        minSimilarity = RULE_MIN_SIMILARITY.getOrDefault(lower(axes.ruleFollowing()), 0.5);
        int           decayDays     = RISK_DECAY_DAYS.getOrDefault(lower(axes.riskAppetite()), 90);
        RetrievalMode mode          = RULE_RETRIEVAL_MODE.getOrDefault(lower(axes.ruleFollowing()), RetrievalMode.HYBRID);
        ctx.base("minSimilarity", minSimilarity);
        ctx.base("decayDays", decayDays);
        if (facets != null && !facets.isEmpty()) {
            double im  = facetScore(facets, PersonalityFacet.IMPULSIVITY, 0.5);
            double rt  = facetScore(facets, PersonalityFacet.RISK_TAKING, 0.5);
            double ms0 = minSimilarity;
            int    dd0 = decayDays;
            minSimilarity -= (im - 0.5) * 0.2;
            minSimilarity = Math.max(0.1, Math.min(0.9, minSimilarity));
            decayDays += (int) ((rt - 0.5) * 60);
            decayDays     = Math.max(30, decayDays);
            ctx.mod(PersonalityFacet.IMPULSIVITY, im, "minSimilarity", ms0, minSimilarity);
            ctx.mod(PersonalityFacet.RISK_TAKING, rt, "decayDays", dd0, decayDays);
            // Anhedonia — Neural delay discounting 2025
            double an = facetScore(facets, PersonalityFacet.ANHEDONIA, 0.5);
            dd0 = decayDays; decayDays += (int) ((an - 0.5) * 30);
            decayDays = Math.max(30, decayDays);
            ctx.mod(PersonalityFacet.ANHEDONIA, an, "decayDays", dd0, decayDays);
            // Irresponsibility — Manning 2014 (C->patience r=.47)
            double ir = facetScore(facets, PersonalityFacet.IRRESPONSIBILITY, 0.5);
            dd0 = decayDays; decayDays += (int) ((ir - 0.5) * 20);
            decayDays = Math.max(30, decayDays);
            ctx.mod(PersonalityFacet.IRRESPONSIBILITY, ir, "decayDays", dd0, decayDays);
        }
        ctx.output("minSimilarity", minSimilarity);
        ctx.output("decayDays", decayDays);
        ctx.stageEnd();
        return new CbrStrategyDefaults(minSimilarity, decayDays, mode);
    }


    static SocialCognitionDefaults deriveSocialCognition(DispositionAxes axes, List<PersonalityFacet> facets) {
        return deriveSocialCognition(axes, facets, Ctx.NOOP);
    }

    static SocialCognitionDefaults deriveSocialCognition(DispositionAxes axes, List<PersonalityFacet> facets, Ctx ctx) {
        if (axes == null) {return null;}
        ctx.stageStart("deriveSocialCognition");
        double trustRate = SOCIAL_TRUST_RATE.getOrDefault(lower(axes.socialOrient()), 0.5);
        ctx.base("trustRate", trustRate);
        if (facets != null && !facets.isEmpty()) {
            double su = facetScore(facets, PersonalityFacet.SUSPICIOUSNESS, 0.5);
            double t0 = trustRate;
            trustRate -= (su - 0.5) * 0.4;
            trustRate = Math.max(0.05, Math.min(0.95, trustRate));
            ctx.mod(PersonalityFacet.SUSPICIOUSNESS, su, "trustRate", t0, trustRate);
        }
        ctx.output("trustRate", trustRate);
        ctx.stageEnd();
        return new SocialCognitionDefaults(trustRate, CONFLICT_INTERPRETATION.getOrDefault(lower(axes.conflictMode()), ConflictInterpretation.NEUTRAL));
    }


    private static final Set<String> HOLISTIC_FUNCTIONS = Set.of("ni", "fe", "ne", "fi");

    // Tier 2: Withdrawal reduces connective bias — Detachment → reduced social node connectivity (McCrae & Costa 1989, Gore & Widiger 2013)
    static GraphStructureDefaults deriveGraphStructure(List<WeightedTerm> profile, List<PersonalityFacet> facets) {
        return deriveGraphStructure(profile, facets, Ctx.NOOP);
    }

    static GraphStructureDefaults deriveGraphStructure(List<WeightedTerm> profile, List<PersonalityFacet> facets, Ctx ctx) {
        if (profile == null || profile.isEmpty()) {return null;}
        ctx.stageStart("deriveGraphStructure");
        double holisticWeight = 0.0, totalWeight = 0.0;
        for (WeightedTerm term : profile) {
            totalWeight += term.weight();
            if (HOLISTIC_FUNCTIONS.contains(term.term().toLowerCase())) {holisticWeight += term.weight();}
        }
        if (totalWeight == 0.0) {return null;}
        double connectiveBias = holisticWeight / totalWeight;
        ctx.base("connectiveBias", connectiveBias);
        if (facets != null && !facets.isEmpty()) {
            double wd = facetScore(facets, PersonalityFacet.WITHDRAWAL, 0.5);
            double cb0 = connectiveBias;
            connectiveBias -= (wd - 0.5) * 0.2;
            connectiveBias = Math.max(0.0, Math.min(1.0, connectiveBias));
            ctx.mod(PersonalityFacet.WITHDRAWAL, wd, "connectiveBias", cb0, connectiveBias);
        }
        GraphStructureDefaults.InferenceStyle style;
        if (connectiveBias > 0.6) {
            style = GraphStructureDefaults.InferenceStyle.CONNECTIVE;
        } else if (connectiveBias < 0.4) {style = GraphStructureDefaults.InferenceStyle.CATEGORICAL;} else {
            style = GraphStructureDefaults.InferenceStyle.BALANCED;
        }
        ctx.output("connectiveBias", connectiveBias);
        ctx.stageEnd();
        return new GraphStructureDefaults(style, connectiveBias);
    }


    private static final Set<String> ANALYTICAL_FUNCTIONS = Set.of("ni", "te", "ti", "si");
    private static final Set<String> EMPATHETIC_FUNCTIONS = Set.of("fe", "fi");

    // Tier 1: Callousness reduces relationship bias — Blair (2005, 2008, 2013) Nature Reviews Neuroscience
// Tier 2: Emotional Lability amplifies affect sensitivity — McCrae & Costa 1989, Gore & Widiger 2013
    static ExtractionBiasDefaults deriveExtractionBias(List<WeightedTerm> profile, List<PersonalityFacet> facets) {
        return deriveExtractionBias(profile, facets, Ctx.NOOP);
    }

    static ExtractionBiasDefaults deriveExtractionBias(List<WeightedTerm> profile, List<PersonalityFacet> facets, Ctx ctx) {
        if (profile == null || profile.isEmpty()) {return null;}
        ctx.stageStart("deriveExtractionBias");
        double analyticalWeight = 0.0, empatheticWeight = 0.0, totalWeight = 0.0;
        for (WeightedTerm term : profile) {
            String fn = term.term().toLowerCase();
            totalWeight += term.weight();
            if (ANALYTICAL_FUNCTIONS.contains(fn)) {analyticalWeight += term.weight();}
            if (EMPATHETIC_FUNCTIONS.contains(fn)) {empatheticWeight += term.weight();}
        }
        if (totalWeight == 0.0) {return null;}
        double relationshipBias  = 0.8 + 0.6 * (analyticalWeight / totalWeight);
        double affectSensitivity = 0.8 + 0.6 * (empatheticWeight / totalWeight);
        ctx.base("relationshipBias", relationshipBias);
        ctx.base("affectSensitivity", affectSensitivity);
        if (facets != null && !facets.isEmpty()) {
            double ca  = facetScore(facets, PersonalityFacet.CALLOUSNESS, 0.5);
            double el  = facetScore(facets, PersonalityFacet.EMOTIONAL_LABILITY, 0.5);
            double rb0 = relationshipBias, as0 = affectSensitivity;
            relationshipBias -= (ca - 0.5) * 0.2;
            affectSensitivity += (el - 0.5) * 0.2;
            ctx.mod(PersonalityFacet.CALLOUSNESS, ca, "relationshipBias", rb0, relationshipBias);
            ctx.mod(PersonalityFacet.EMOTIONAL_LABILITY, el, "affectSensitivity", as0, affectSensitivity);
            // Separation Insecurity — Mikulincer & Shaver 2003
            double si = facetScore(facets, PersonalityFacet.SEPARATION_INSECURITY, 0.5);
            rb0 = relationshipBias; relationshipBias += (si - 0.5) * 0.15;
            ctx.mod(PersonalityFacet.SEPARATION_INSECURITY, si, "relationshipBias", rb0, relationshipBias);
            // Restricted Affectivity — Trevisan 2019 meta (66 samples, r=-.162)
            double ra = facetScore(facets, PersonalityFacet.RESTRICTED_AFFECTIVITY, 0.5);
            as0 = affectSensitivity; affectSensitivity -= (ra - 0.5) * 0.2;
            ctx.mod(PersonalityFacet.RESTRICTED_AFFECTIVITY, ra, "affectSensitivity", as0, affectSensitivity);
            // Cognitive Perceptual Dysregulation — Ozdemir 2025 meta
            double cd = facetScore(facets, PersonalityFacet.COGNITIVE_PERCEPTUAL_DYSREGULATION, 0.5);
            as0 = affectSensitivity; affectSensitivity -= (cd - 0.5) * 0.1;
            ctx.mod(PersonalityFacet.COGNITIVE_PERCEPTUAL_DYSREGULATION, cd, "affectSensitivity", as0, affectSensitivity);
        }
        ctx.output("relationshipBias", relationshipBias);
        ctx.output("affectSensitivity", affectSensitivity);
        ctx.stageEnd();
        return new ExtractionBiasDefaults(relationshipBias, affectSensitivity);
    }


    private static final double SCALE_FACTOR = 1.5;

    private static final Map<String, Double> URGENCY_CONTRIBUTION = Map.of(
        "te", 1.0,  "fe", 1.0,
        "ne", -1.0, "se", -1.0
    );

    private static final Map<String, Double> RELATIONSHIP_CONTRIBUTION = Map.of(
        "fe", 1.0,   "fi", 0.665,
        "ti", -1.0,  "te", -0.665
    );

    private static final Map<String, Double> FEAR_CONTRIBUTION = Map.of(
        "ni", -1.0,  "ne", -0.747,
        "si", 0.747,  "se", 1.0
    );


    static AppraisalWeights deriveAppraisalWeights(List<WeightedTerm> profile, DispositionAxes axes) {
        return deriveAppraisalWeights(profile, axes, List.of());
    }

    // Tier 1: Anxiousness amplifies fear — Bar-Haim et al. (2007) Psychological Bulletin, 172-study meta-analysis, d=0.45
// Tier 2: Risk Taking reduces fear — Disinhibition → reduced threat sensitivity (McCrae & Costa 1989, Gore & Widiger 2013)
// Tier 2: Impulsivity amplifies urgency — Disinhibition → immediate threat/opportunity weighting
    static AppraisalWeights deriveAppraisalWeights(List<WeightedTerm> profile, DispositionAxes axes, List<PersonalityFacet> facets) {
        return deriveAppraisalWeights(profile, axes, facets, Ctx.NOOP);
    }

    static AppraisalWeights deriveAppraisalWeights(List<WeightedTerm> profile, DispositionAxes axes, List<PersonalityFacet> facets, Ctx ctx) {
        if (profile == null || profile.isEmpty()) {return null;}
        ctx.stageStart("deriveAppraisalWeights");
        double urgencyRaw = 0.0, relRaw = 0.0, fearRaw = 0.0, totalWeight = 0.0;
        for (WeightedTerm term : profile) {
            String fn = term.term().toLowerCase();
            double w  = term.weight();
            totalWeight += w;
            urgencyRaw += w * URGENCY_CONTRIBUTION.getOrDefault(fn, 0.0);
            relRaw += w * RELATIONSHIP_CONTRIBUTION.getOrDefault(fn, 0.0);
            fearRaw += w * FEAR_CONTRIBUTION.getOrDefault(fn, 0.0);
        }
        if (totalWeight == 0.0) {return null;}
        urgencyRaw /= totalWeight;
        relRaw /= totalWeight;
        fearRaw /= totalWeight;
        double selfStrictness = 1.0, otherStrictness = 1.0;
        if (axes != null) {
            if (axes.ruleFollowing() != null) {
                switch (axes.ruleFollowing()) {
                    case "strict" -> {
                        selfStrictness  = 1.6;
                        otherStrictness = 1.4;
                    }
                    case "flexible" -> {
                        selfStrictness  = 0.7;
                        otherStrictness = 0.6;
                    }
                    default -> {}
                }
            }
            if (axes.socialOrient() != null) {
                switch (axes.socialOrient()) {
                    case "collaborative" -> otherStrictness -= 0.2;
                    default -> {}
                }
            }
        }
        ctx.base("urgencyWeight", 1.0 + urgencyRaw * SCALE_FACTOR);
        ctx.base("fearOnsetThreshold", 1.0 + fearRaw * SCALE_FACTOR);
        if (facets != null && !facets.isEmpty()) {
            double ax = facetScore(facets, PersonalityFacet.ANXIOUSNESS, 0.5);
            double rt = facetScore(facets, PersonalityFacet.RISK_TAKING, 0.5);
            double im = facetScore(facets, PersonalityFacet.IMPULSIVITY, 0.5);
            double f0 = fearRaw, u0 = urgencyRaw;
            fearRaw -= (ax - 0.5) * 0.2;
            fearRaw -= (rt - 0.5) * 0.2;
            urgencyRaw += (im - 0.5) * 0.2;
            ctx.mod(PersonalityFacet.ANXIOUSNESS, ax, "fearOnsetThreshold", 1.0 + f0 * SCALE_FACTOR, 1.0 + (f0 - (ax - 0.5) * 0.2) * SCALE_FACTOR);
            ctx.mod(PersonalityFacet.RISK_TAKING, rt, "fearOnsetThreshold", 1.0 + (f0 - (ax - 0.5) * 0.2) * SCALE_FACTOR, 1.0 + fearRaw * SCALE_FACTOR);
            ctx.mod(PersonalityFacet.IMPULSIVITY, im, "urgencyWeight", 1.0 + u0 * SCALE_FACTOR, 1.0 + urgencyRaw * SCALE_FACTOR);
            // Depressivity — Scherer 2022 (N=4859): appraisal bias amplifies threat/loss sensitivity
            double dp = facetScore(facets, PersonalityFacet.DEPRESSIVITY, 0.5);
            double f1 = fearRaw; fearRaw -= (dp - 0.5) * 0.2;
            ctx.mod(PersonalityFacet.DEPRESSIVITY, dp, "fearOnsetThreshold", 1.0 + f1 * SCALE_FACTOR, 1.0 + fearRaw * SCALE_FACTOR);
            // Separation Insecurity — Downey & Feldman 1996: rejection sensitivity amplifies relevance
            double si = facetScore(facets, PersonalityFacet.SEPARATION_INSECURITY, 0.5);
            double r0 = relRaw; relRaw += (si - 0.5) * 0.15;
            ctx.mod(PersonalityFacet.SEPARATION_INSECURITY, si, "relevanceWeight", 1.0 + r0 * SCALE_FACTOR, 1.0 + relRaw * SCALE_FACTOR);
        }
        ctx.output("urgencyWeight", Math.max(1.0 + urgencyRaw * SCALE_FACTOR, 0.1));
        ctx.output("fearOnsetThreshold", Math.max(1.0 + fearRaw * SCALE_FACTOR, 0.1));
        ctx.stageEnd();
        return new AppraisalWeights(Math.max(1.0 + urgencyRaw * SCALE_FACTOR, 0.1), Math.max(1.0 + relRaw * SCALE_FACTOR, 0.1), Math.max(1.0 + fearRaw * SCALE_FACTOR, 0.1), Math.clamp(selfStrictness, 0.5, 2.0), Math.clamp(otherStrictness, 0.5, 2.0));
    }


    static HabituationConfig deriveHabituationConfig(DispositionAxes axes, List<String> goals, List<PersonalityFacet> facets) {
        return deriveHabituationConfig(axes, goals, facets, Ctx.NOOP);
    }

    static HabituationConfig deriveHabituationConfig(DispositionAxes axes, List<String> goals, List<PersonalityFacet> facets, Ctx ctx) {
        if (axes == null) {return null;}
        ctx.stageStart("deriveHabituationConfig");
        double habRate = RISK_HABITUATION_RATE.getOrDefault(lower(axes.riskAppetite()), 0.2);
        double novelty = RISK_NOVELTY_THRESHOLD.getOrDefault(lower(axes.riskAppetite()), 0.3);
        double repTol  = RULE_REPETITION_TOLERANCE.getOrDefault(lower(axes.ruleFollowing()), 5.0);
        if ("autonomous".equalsIgnoreCase(axes.autonomy())) {repTol -= 1.0;}
        ctx.base("habituationRate", habRate);
        ctx.base("noveltyThreshold", novelty);
        ctx.base("repetitionTolerance", repTol);
        if (facets != null && !facets.isEmpty()) {
            double im = facetScore(facets, PersonalityFacet.IMPULSIVITY, 0.5);
            double rt = facetScore(facets, PersonalityFacet.RISK_TAKING, 0.5);
            double hr0 = habRate, nt0 = novelty, rt0 = repTol;
            habRate += (im - 0.5) * 0.2;
            novelty += (rt - 0.5) * 0.2;
            repTol -= (im - 0.5) * 2.0;
            ctx.mod(PersonalityFacet.IMPULSIVITY, im, "habituationRate", hr0, habRate);
            ctx.mod(PersonalityFacet.RISK_TAKING, rt, "noveltyThreshold", nt0, novelty);
            ctx.mod(PersonalityFacet.IMPULSIVITY, im, "repetitionTolerance", rt0, repTol);
            // Perseveration — PERCS framework 2026
            double pv = facetScore(facets, PersonalityFacet.PERSEVERATION, 0.5);
            hr0 = habRate; habRate -= (pv - 0.5) * 0.1;
            ctx.mod(PersonalityFacet.PERSEVERATION, pv, "habituationRate", hr0, habRate);
            rt0 = repTol; repTol += (pv - 0.5) * 1.0;
            ctx.mod(PersonalityFacet.PERSEVERATION, pv, "repetitionTolerance", rt0, repTol);
            // Rigid Perfectionism — Stahl 2015 (ERP); Bouchard 2009 (NJR)
            double rp = facetScore(facets, PersonalityFacet.RIGID_PERFECTIONISM, 0.5);
            hr0 = habRate; habRate -= (rp - 0.5) * 0.1;
            ctx.mod(PersonalityFacet.RIGID_PERFECTIONISM, rp, "habituationRate", hr0, habRate);
            rt0 = repTol; repTol += (rp - 0.5) * 1.0;
            ctx.mod(PersonalityFacet.RIGID_PERFECTIONISM, rp, "repetitionTolerance", rt0, repTol);
        }
        ctx.output("habituationRate", habRate);
        ctx.output("noveltyThreshold", novelty);
        ctx.output("repetitionTolerance", Math.max(2.0, repTol));
        Map<String, Double> domainMod = new HashMap<>();
        if (goals != null) {
            for (String goal : goals) {
                if (goal == null) {continue;}
                String lowerGoal = goal.toLowerCase();
                for (var entry : GOAL_SUBGRAPH_MAPPING.entrySet()) {
                    if (lowerGoal.contains(entry.getKey())) {domainMod.put(entry.getValue(), 0.5);}
                }
            }
        }
        ctx.stageEnd();
        return new HabituationConfig(habRate, novelty, Math.max(2.0, repTol), domainMod);
    }


    public static RegulationDefaults deriveRegulationDefaults(DispositionAxes axes) {
        return deriveRegulationDefaults(axes, List.of());
    }

    public static RegulationDefaults deriveRegulationDefaults(DispositionAxes axes, List<PersonalityFacet> facets) {
        return deriveRegulationDefaults(axes, facets, Ctx.NOOP);
    }

    public static RegulationDefaults deriveRegulationDefaults(DispositionAxes axes, List<PersonalityFacet> facets, Ctx ctx) {
        if (axes == null) {return null;}
        ctx.stageStart("deriveRegulationDefaults");
        double reappraisal = 0.5 + REAPPRAISAL_CONFLICT.getOrDefault(lower(axes.conflictMode()), 0.0) + REAPPRAISAL_AUTONOMY.getOrDefault(lower(axes.autonomy()), 0.0) + REAPPRAISAL_RULES.getOrDefault(lower(axes.ruleFollowing()), 0.0) + REAPPRAISAL_RISK.getOrDefault(lower(axes.riskAppetite()), 0.0) + REAPPRAISAL_SOCIAL.getOrDefault(lower(axes.socialOrient()), 0.0);
        double suppression = 0.5 + SUPPRESSION_CONFLICT.getOrDefault(lower(axes.conflictMode()), 0.0) + SUPPRESSION_SOCIAL.getOrDefault(lower(axes.socialOrient()), 0.0) + SUPPRESSION_RISK.getOrDefault(lower(axes.riskAppetite()), 0.0) + SUPPRESSION_AUTONOMY.getOrDefault(lower(axes.autonomy()), 0.0) + SUPPRESSION_RULES.getOrDefault(lower(axes.ruleFollowing()), 0.0);
        double redirect    = 0.3 + REDIRECT_CONFLICT.getOrDefault(lower(axes.conflictMode()), 0.0) + REDIRECT_RISK.getOrDefault(lower(axes.riskAppetite()), 0.0) + REDIRECT_AUTONOMY.getOrDefault(lower(axes.autonomy()), 0.0) + REDIRECT_SOCIAL.getOrDefault(lower(axes.socialOrient()), 0.0) + REDIRECT_RULES.getOrDefault(lower(axes.ruleFollowing()), 0.0);
        double social      = 0.5 + SOCIAL_REG_SOCIAL.getOrDefault(lower(axes.socialOrient()), 0.0) + SOCIAL_REG_CONFLICT.getOrDefault(lower(axes.conflictMode()), 0.0) + SOCIAL_REG_AUTONOMY.getOrDefault(lower(axes.autonomy()), 0.0);
        ctx.base("reappraisalCapacity", reappraisal);
        ctx.base("socialRegulationTendency", social);
        if (facets != null && !facets.isEmpty()) {
            double el = facetScore(facets, PersonalityFacet.EMOTIONAL_LABILITY, 0.5);
            double ca = facetScore(facets, PersonalityFacet.CALLOUSNESS, 0.5);
            double wd = facetScore(facets, PersonalityFacet.WITHDRAWAL, 0.5);
            double r0 = reappraisal, s0 = social;
            reappraisal -= (el - 0.5) * 0.2;
            social -= (ca - 0.5) * 0.3;
            double s1 = social;
            social -= (wd - 0.5) * 0.2;
            ctx.mod(PersonalityFacet.EMOTIONAL_LABILITY, el, "reappraisalCapacity", r0, reappraisal);
            ctx.mod(PersonalityFacet.CALLOUSNESS, ca, "socialRegulationTendency", s0, s1);
            ctx.mod(PersonalityFacet.WITHDRAWAL, wd, "socialRegulationTendency", s1, social);
            // Depressivity — Joormann & Gotlib 2010: WM updating deficit impairs reappraisal
            double dp = facetScore(facets, PersonalityFacet.DEPRESSIVITY, 0.5);
            r0 = reappraisal; reappraisal -= (dp - 0.5) * 0.1;
            ctx.mod(PersonalityFacet.DEPRESSIVITY, dp, "reappraisalCapacity", r0, reappraisal);
            // Rigid Perfectionism — Vois 2026 (3-wave, N=454): ECP predicts suppression over reappraisal
            double rp = facetScore(facets, PersonalityFacet.RIGID_PERFECTIONISM, 0.5);
            r0 = reappraisal; reappraisal -= (rp - 0.5) * 0.1;
            ctx.mod(PersonalityFacet.RIGID_PERFECTIONISM, rp, "reappraisalCapacity", r0, reappraisal);
            // Separation Insecurity — PLOS ONE 2018: social anxiety mediation
            double si = facetScore(facets, PersonalityFacet.SEPARATION_INSECURITY, 0.5);
            r0 = reappraisal; reappraisal -= (si - 0.5) * 0.1;
            ctx.mod(PersonalityFacet.SEPARATION_INSECURITY, si, "reappraisalCapacity", r0, reappraisal);
            // Restricted Affectivity — Preece 2022 (N=501): alexithymia reduces adaptive regulation
            double ra = facetScore(facets, PersonalityFacet.RESTRICTED_AFFECTIVITY, 0.5);
            r0 = reappraisal; reappraisal -= (ra - 0.5) * 0.15;
            ctx.mod(PersonalityFacet.RESTRICTED_AFFECTIVITY, ra, "reappraisalCapacity", r0, reappraisal);
        }
        ctx.output("reappraisalCapacity", Math.max(0.0, Math.min(1.0, reappraisal)));
        ctx.output("socialRegulationTendency", Math.max(0.0, Math.min(1.0, social)));
        ctx.stageEnd();
        return new RegulationDefaults(Math.max(0.0, Math.min(1.0, reappraisal)), Math.max(0.0, Math.min(1.0, suppression)), Math.max(0.0, Math.min(1.0, redirect)), Math.max(0.0, Math.min(1.0, social)));
    }

    // Connor-Smith & Flachsbart (2007) meta-analysis: Big Five → coping strategy correlations
// Big Five composites derived from PID-5 facets following existing CDE patterns
    public static CopingDefaults deriveCopingDefaults(DispositionAxes axes, List<PersonalityFacet> facets) {
        return deriveCopingDefaults(axes, facets, Ctx.NOOP);
    }

    public static CopingDefaults deriveCopingDefaults(DispositionAxes axes, List<PersonalityFacet> facets, Ctx ctx) {
        if (facets == null || facets.isEmpty()) {return null;}
        ctx.stageStart("deriveCopingDefaults");

        double impulsivity       = facetScore(facets, PersonalityFacet.IMPULSIVITY, 0.5);
        double anxiousness       = facetScore(facets, PersonalityFacet.ANXIOUSNESS, 0.5);
        double emotionalLability = facetScore(facets, PersonalityFacet.EMOTIONAL_LABILITY, 0.5);
        double withdrawal        = facetScore(facets, PersonalityFacet.WITHDRAWAL, 0.5);
        double riskTaking        = facetScore(facets, PersonalityFacet.RISK_TAKING, 0.5);
        double suspiciousness    = facetScore(facets, PersonalityFacet.SUSPICIOUSNESS, 0.5);
        double callousness       = facetScore(facets, PersonalityFacet.CALLOUSNESS, 0.5);
        double hostility         = facetScore(facets, PersonalityFacet.HOSTILITY, 0.5);
        double intimacyAvoidance = facetScore(facets, PersonalityFacet.INTIMACY_AVOIDANCE, 0.5);
        double manipulativeness  = facetScore(facets, PersonalityFacet.MANIPULATIVENESS, 0.5);

        double ruleFollowingNumeric = axes != null
                                      ? RULE_FOLLOWING_NUMERIC.getOrDefault(lower(axes.ruleFollowing()), 0.5) : 0.5;

        double conscientiousness = (ruleFollowingNumeric + 1.0 - impulsivity) / 2.0;
        double neuroticism       = (anxiousness + emotionalLability) / 2.0;
        double openness          = (1.0 - withdrawal + 1.0 - suspiciousness + riskTaking) / 3.0;
        double agreeableness     = (1.0 - callousness + 1.0 - hostility + 1.0 - manipulativeness) / 3.0;
        double extraversion      = (1.0 - withdrawal + 1.0 - intimacyAvoidance) / 2.0;

        ctx.base("conscientiousness", conscientiousness);
        ctx.base("neuroticism", neuroticism);
        ctx.base("openness", openness);
        ctx.base("agreeableness", agreeableness);
        ctx.base("extraversion", extraversion);

        double planful          = conscientiousness;
        double reinterpretation = openness;
        double avoidance        = neuroticism;
        double supportSeeking   = (agreeableness + extraversion) / 2.0;
        double acceptance       = 1.0 - neuroticism;
        double confrontation    = (extraversion + conscientiousness) / 2.0;
        double disengagement    = (neuroticism + (1.0 - conscientiousness)) / 2.0;
        double suppression      = (neuroticism + ruleFollowingNumeric) / 2.0;
        // Irresponsibility — Connor-Smith 2007 meta (N=33,094): low C -> disengagement coping
        double irresponsibility = facetScore(facets, PersonalityFacet.IRRESPONSIBILITY, 0.5);
        double dg0 = disengagement;
        disengagement += (irresponsibility - 0.5) * 0.2;
        ctx.mod(PersonalityFacet.IRRESPONSIBILITY, irresponsibility, "disengagementTendency", dg0, disengagement);

        planful          = clamp01(planful);
        reinterpretation = clamp01(reinterpretation);
        avoidance        = clamp01(avoidance);
        supportSeeking   = clamp01(supportSeeking);
        acceptance       = clamp01(acceptance);
        confrontation    = clamp01(confrontation);
        disengagement    = clamp01(disengagement);
        suppression      = clamp01(suppression);

        ctx.output("planfulTendency", planful);
        ctx.output("reinterpretationTendency", reinterpretation);
        ctx.output("avoidanceTendency", avoidance);
        ctx.output("supportSeekingTendency", supportSeeking);
        ctx.output("acceptanceTendency", acceptance);
        ctx.output("confrontationTendency", confrontation);
        ctx.output("disengagementTendency", disengagement);
        ctx.output("suppressionTendency", suppression);
        ctx.stageEnd();

        return new CopingDefaults(planful, reinterpretation, avoidance, supportSeeking, acceptance, confrontation, disengagement, suppression);
    }


    static RecurringThoughtConfig deriveRecurringThoughtConfig(List<PersonalityFacet> facets, DispositionAxes axes) {
        return deriveRecurringThoughtConfig(facets, axes, Ctx.NOOP);
    }

    static RecurringThoughtConfig deriveRecurringThoughtConfig(List<PersonalityFacet> facets, DispositionAxes axes, Ctx ctx) {
        if (facets == null || facets.isEmpty()) {return null;}
        ctx.stageStart("deriveRecurringThoughtConfig");

        var defaults = RecurringThoughtConfig.defaults();

        double anxiousness       = facetScore(facets, PersonalityFacet.ANXIOUSNESS, 0.5);
        double emotionalLability = facetScore(facets, PersonalityFacet.EMOTIONAL_LABILITY, 0.5);
        double withdrawal        = facetScore(facets, PersonalityFacet.WITHDRAWAL, 0.5);
        double impulsivity       = facetScore(facets, PersonalityFacet.IMPULSIVITY, 0.5);
        double riskTaking        = facetScore(facets, PersonalityFacet.RISK_TAKING, 0.5);
        double suspiciousness    = facetScore(facets, PersonalityFacet.SUSPICIOUSNESS, 0.5);
        double intimacyAvoidance = facetScore(facets, PersonalityFacet.INTIMACY_AVOIDANCE, 0.5);

        double ruleFollowingNumeric = axes != null
                                      ? RULE_FOLLOWING_NUMERIC.getOrDefault(lower(axes.ruleFollowing()), 0.5) : 0.5;

        // Eysenck (1967): neuroticism amplifies negative affect sensitivity
        double fixation0 = defaults.fixationThreshold();
        ctx.base("fixationThreshold", fixation0);
        double fixation = fixation0
                          - (anxiousness - 0.5) * 0.2
                          - (emotionalLability - 0.5) * 0.1;
        ctx.mod(PersonalityFacet.ANXIOUSNESS, anxiousness, "fixationThreshold", fixation0, fixation);

        // Roberts et al. (2014): self-regulation buffers against loss of control
        double conscientiousness = (ruleFollowingNumeric + 1.0 - impulsivity) / 2.0;
        double obsession0        = defaults.obsessionThreshold();
        ctx.base("obsessionThreshold", obsession0);
        double obsession = obsession0 + (conscientiousness - 0.5) * 0.15;
        ctx.mod(PersonalityFacet.IMPULSIVITY, impulsivity, "obsessionThreshold", obsession0, obsession);

        // Nolen-Hoeksema (1991): social isolation amplifies circular thinking
        double isolation   = (withdrawal + intimacyAvoidance) / 2.0;
        double rumination0 = defaults.ruminationThreshold();
        ctx.base("ruminationThreshold", rumination0);
        double rumination = rumination0 - (isolation - 0.5) * 0.15;
        ctx.mod(PersonalityFacet.WITHDRAWAL, withdrawal, "ruminationThreshold", rumination0, rumination);

        // Depressivity — Nolen-Hoeksema 1991: depressive rumination
        double depressivity = facetScore(facets, PersonalityFacet.DEPRESSIVITY, 0.5);
        double rum0 = rumination;
        rumination -= (depressivity - 0.5) * 0.1;
        ctx.mod(PersonalityFacet.DEPRESSIVITY, depressivity, "ruminationThreshold", rum0, rumination);
        // Perseveration — Davis & Nolen-Hoeksema 2000 (WCST perseverative errors)
        double perseveration = facetScore(facets, PersonalityFacet.PERSEVERATION, 0.5);
        double fix1 = fixation;
        fixation -= (perseveration - 0.5) * 0.15;
        ctx.mod(PersonalityFacet.PERSEVERATION, perseveration, "fixationThreshold", fix1, fixation);
        // Rigid Perfectionism — Xie 2019 meta (k=12, rumination)
        double rigidPerf = facetScore(facets, PersonalityFacet.RIGID_PERFECTIONISM, 0.5);
        fix1 = fixation;
        fixation -= (rigidPerf - 0.5) * 0.1;
        ctx.mod(PersonalityFacet.RIGID_PERFECTIONISM, rigidPerf, "fixationThreshold", fix1, fixation);
        // Separation Insecurity — Stanton & Campbell 2015
        double sepInsec = facetScore(facets, PersonalityFacet.SEPARATION_INSECURITY, 0.5);
        fix1 = fixation;
        fixation -= (sepInsec - 0.5) * 0.1;
        ctx.mod(PersonalityFacet.SEPARATION_INSECURITY, sepInsec, "fixationThreshold", fix1, fixation);

        // DeYoung (2015): open individuals shift attention more fluidly
        double openness       = (1.0 - withdrawal + 1.0 - suspiciousness + riskTaking) / 3.0;
        int    recurrenceDays = (int) Math.round(7.0 + (openness - 0.5) * 4.0);

        Map<String, Double> weights = new HashMap<>();
        if (anxiousness > 0.6) {
            weights.put("arousalTrajectory", 1.0 + (anxiousness - 0.5));
            weights.put("valence", 1.0 + (emotionalLability - 0.5));
        }
        if (conscientiousness > 0.6) {
            weights.put("attentionalCapture", 1.0 - (conscientiousness - 0.5) * 0.5);
        }

        fixation   = Math.max(0.1, fixation);
        obsession  = Math.max(fixation + 0.1, obsession);
        rumination = Math.max(obsession + 0.05, rumination);

        ctx.output("fixationThreshold", fixation);
        ctx.output("obsessionThreshold", obsession);
        ctx.output("ruminationThreshold", rumination);
        ctx.output("recurrenceDays", recurrenceDays);
        ctx.stageEnd();

        return new RecurringThoughtConfig(
                weights,
                fixation,
                obsession,
                rumination,
                java.time.Duration.ofDays(Math.max(3, recurrenceDays)),
                3);
    }


    static double derivePersonalityDominance(FormationPadSummary summary) {
        if (summary == null || summary.memoryCount() == 0 || summary.totalPositivePleasure() == 0.0) {
            return 0.5;
        }
        double ratio = summary.dominanceWeightedReward() / summary.totalPositivePleasure();
        return Math.clamp((ratio + 1.0) / 2.0, 0.0, 1.0);
    }

    static ExpressionStyleDefaults deriveExpressionStyles(List<PersonalityFacet> facets) {
        return deriveExpressionStyles(facets, Ctx.NOOP);
    }

    static ExpressionStyleDefaults deriveExpressionStyles(List<PersonalityFacet> facets, Ctx ctx) {
        if (facets == null || facets.isEmpty()) {return null;}
        ctx.stageStart("deriveExpressionStyles");

        double grandiosity      = facetScore(facets, PersonalityFacet.GRANDIOSITY, 0.5);
        double callousness      = facetScore(facets, PersonalityFacet.CALLOUSNESS, 0.5);
        double manipulativeness = facetScore(facets, PersonalityFacet.MANIPULATIVENESS, 0.5);
        double suspiciousness   = facetScore(facets, PersonalityFacet.SUSPICIOUSNESS, 0.5);
        double hostility        = facetScore(facets, PersonalityFacet.HOSTILITY, 0.5);
        double riskTaking       = facetScore(facets, PersonalityFacet.RISK_TAKING, 0.5);

        ctx.base(PersonalityFacet.GRANDIOSITY, grandiosity);
        ctx.base(PersonalityFacet.CALLOUSNESS, callousness);
        ctx.base(PersonalityFacet.MANIPULATIVENESS, manipulativeness);
        ctx.base(PersonalityFacet.SUSPICIOUSNESS, suspiciousness);
        ctx.base(PersonalityFacet.HOSTILITY, hostility);
        ctx.base(PersonalityFacet.RISK_TAKING, riskTaking);

        var styles = new HashMap<String, String>();

        // McAdams (2001) Level 2 characteristic adaptations: same trait produces
        // different behavioral signatures depending on co-occurring traits
        if (isHigh(facets, PersonalityFacet.GRANDIOSITY) && isHigh(facets, PersonalityFacet.CALLOUSNESS)) {
            styles.put("triumph", "public, theatrical, needs audience to witness");
        } else if (isHigh(facets, PersonalityFacet.GRANDIOSITY) && isLow(facets, PersonalityFacet.CALLOUSNESS)) {
            styles.put("triumph", "expansive, generous, wants to share the glory");
        } else if (isLow(facets, PersonalityFacet.GRANDIOSITY)) {
            styles.put("triumph", "private, quiet satisfaction, deflects credit");
        } else {
            styles.put("triumph", "measured acknowledgment, neither deflecting nor amplifying");
        }

        // Paulhus & Williams (2002) Dark Triad: convergence of narcissism, psychopathy,
        // Machiavellianism predicts schadenfreude and dominance displays
        if (isHigh(facets, PersonalityFacet.GRANDIOSITY) && isHigh(facets, PersonalityFacet.CALLOUSNESS)
            && isHigh(facets, PersonalityFacet.MANIPULATIVENESS)) {
            styles.put("gloating", "savoring, deliberate, wants the other to know");
        } else if (isHigh(facets, PersonalityFacet.CALLOUSNESS) && !isHigh(facets, PersonalityFacet.GRANDIOSITY)) {
            styles.put("gloating", "cold satisfaction, indifferent rather than triumphant");
        } else if (isLow(facets, PersonalityFacet.CALLOUSNESS)) {
            styles.put("gloating", "uncomfortable with others' misfortune, suppresses it");
        } else {
            styles.put("gloating", "notices but does not dwell, moves on quickly");
        }

        // Mischel & Shoda (1995) CAPS: if-then profiles predict situation-specific strategy selection
        if (isHigh(facets, PersonalityFacet.MANIPULATIVENESS) && isHigh(facets, PersonalityFacet.SUSPICIOUSNESS)) {
            styles.put("scheming", "elaborate, multi-step, assumes others are doing the same");
        } else if (isHigh(facets, PersonalityFacet.MANIPULATIVENESS) && isLow(facets, PersonalityFacet.SUSPICIOUSNESS)) {
            styles.put("scheming", "opportunistic, charm-based, reads openings not threats");
        } else if (isLow(facets, PersonalityFacet.MANIPULATIVENESS)) {
            styles.put("scheming", "transparent, prefers direct negotiation");
        } else {
            styles.put("scheming", "pragmatic, considers angles but prefers straightforward approaches");
        }

        // Krueger et al. (2012) AMPD: Grandiosity facet reflects both compensatory
        // and genuine self-enhancement depending on co-occurring Negative Affectivity
        if (isHigh(facets, PersonalityFacet.GRANDIOSITY) && isHigh(facets, PersonalityFacet.HOSTILITY)) {
            styles.put("proving-worth", "combative, proving through dominance and comparison");
        } else if (isHigh(facets, PersonalityFacet.GRANDIOSITY) && isLow(facets, PersonalityFacet.HOSTILITY)) {
            styles.put("proving-worth", "demonstrative, seeking admiration through competence display");
        } else if (isLow(facets, PersonalityFacet.GRANDIOSITY)) {
            styles.put("proving-worth", "quiet competence, lets work speak, uncomfortable with praise");
        } else {
            styles.put("proving-worth", "balanced, acknowledges contributions without seeking spotlight");
        }

        // Costa & McCrae (1992) FFM: high Agreeableness (low AMPD Antagonism) predicts
        // prosocial helping motivated by empathic concern vs. reciprocity expectations
        if (isLow(facets, PersonalityFacet.CALLOUSNESS) && isLow(facets, PersonalityFacet.SUSPICIOUSNESS)) {
            styles.put("gallantry", "genuine, protective, feels others' vulnerability");
        } else if (isLow(facets, PersonalityFacet.CALLOUSNESS) && isHigh(facets, PersonalityFacet.SUSPICIOUSNESS)) {
            styles.put("gallantry", "cautious protectiveness, helps but watches for betrayal");
        } else if (isHigh(facets, PersonalityFacet.CALLOUSNESS)) {
            styles.put("gallantry", "instrumental, helps when it serves a purpose");
        } else {
            styles.put("gallantry", "willing to help when asked, neither driven nor reluctant");
        }

        // Krueger et al. (2012): Suspiciousness loads on both Detachment and Psychoticism,
        // co-occurring Hostility shifts from avoidant vigilance to aggressive preemption
        if (isHigh(facets, PersonalityFacet.SUSPICIOUSNESS) && isHigh(facets, PersonalityFacet.HOSTILITY)) {
            styles.put("distrust", "aggressive vigilance, reads threats into neutral behavior");
        } else if (isHigh(facets, PersonalityFacet.SUSPICIOUSNESS) && isLow(facets, PersonalityFacet.HOSTILITY)) {
            styles.put("distrust", "watchful withdrawal, avoids rather than confronts");
        } else if (isLow(facets, PersonalityFacet.SUSPICIOUSNESS)) {
            styles.put("distrust", "trusts by default, slow to suspect");
        } else {
            styles.put("distrust", "measured caution, verifies then trusts");
        }

        // Patrick et al. (2009) Triarchic: boldness + meanness interaction
        // determines whether resource defense is proactive or reactive
        if (isHigh(facets, PersonalityFacet.HOSTILITY) && isHigh(facets, PersonalityFacet.RISK_TAKING)) {
            styles.put("territorial", "aggressive, preemptive, expands boundaries");
        } else if (isHigh(facets, PersonalityFacet.HOSTILITY) && isLow(facets, PersonalityFacet.RISK_TAKING)) {
            styles.put("territorial", "defensive, reactive, guards existing ground");
        } else if (isLow(facets, PersonalityFacet.HOSTILITY)) {
            styles.put("territorial", "shares space easily, low boundary sensitivity");
        } else {
            styles.put("territorial", "maintains clear boundaries without rigidity");
        }

        ctx.output("stylesResolved", styles.size());
        ctx.stageEnd();

        return styles.isEmpty() ? null : new ExpressionStyleDefaults(styles);
    }


    private static final Map<String, Double> RULE_FOLLOWING_NUMERIC = Map.of(
            "strict", 0.8, "principled", 0.5, "flexible", 0.2
    );

    /**
     * Derives personality-modulated search budget from PID-5 facets and
     * disposition axes. Each parameter mapping traces to published findings.
     * <p>
     * Breadth ← connectiveBias + Risk Taking (DeYoung 2015, Grajzel et al. 2023 r=0.20 DT).
     * Laterality ← low Withdrawal + low Intimacy Avoidance + low Suspiciousness (DeYoung 2015).
     * Depth ← ruleFollowing + low Impulsivity (Krueger et al. 2012 PID-5 dimensional alignment).
     * Convergence ← ruleFollowing modulated by Anxiousness + Emotional Lability (Davis &amp; Nolen-Hoeksema 1998).
     */
    static SearchBudgetConfig deriveSearchBudgetDefaults(
            List<PersonalityFacet> facets, DispositionAxes axes, double connectiveBias) {
        return deriveSearchBudgetDefaults(facets, axes, connectiveBias, Ctx.NOOP);
    }

    static SearchBudgetConfig deriveSearchBudgetDefaults(
            List<PersonalityFacet> facets, DispositionAxes axes, double connectiveBias, Ctx ctx) {
        if (facets == null || facets.isEmpty()) {return null;}
        ctx.stageStart("deriveSearchBudgetDefaults");

        var defaults = SearchBudgetConfig.defaults();

        double riskTaking        = facetScore(facets, PersonalityFacet.RISK_TAKING, 0.5);
        double impulsivity       = facetScore(facets, PersonalityFacet.IMPULSIVITY, 0.5);
        double withdrawal        = facetScore(facets, PersonalityFacet.WITHDRAWAL, 0.5);
        double intimacyAvoidance = facetScore(facets, PersonalityFacet.INTIMACY_AVOIDANCE, 0.5);
        double suspiciousness    = facetScore(facets, PersonalityFacet.SUSPICIOUSNESS, 0.5);
        double anxiousness       = facetScore(facets, PersonalityFacet.ANXIOUSNESS, 0.5);
        double emotionalLability = facetScore(facets, PersonalityFacet.EMOTIONAL_LABILITY, 0.5);

        double ruleFollowingNumeric = axes != null
                                      ? RULE_FOLLOWING_NUMERIC.getOrDefault(lower(axes.ruleFollowing()), 0.5) : 0.5;

        double breadthSteepness0 = defaults.breadthSteepness();
        ctx.base("breadthSteepness", breadthSteepness0);
        double breadthSteepness = breadthSteepness0 + (connectiveBias - 0.5) * 4.0 + (riskTaking - 0.5) * 2.0;
        ctx.mod(PersonalityFacet.RISK_TAKING, riskTaking, "breadthSteepness", breadthSteepness0, breadthSteepness);

        double breadthMidpoint0 = defaults.breadthMidpoint();
        double breadthMidpoint  = breadthMidpoint0 - (connectiveBias - 0.5) * 0.2;

        double openness            = (1.0 - withdrawal + 1.0 - intimacyAvoidance + 1.0 - suspiciousness) / 3.0;
        double lateralityMidpoint0 = defaults.lateralityMidpoint();
        ctx.base("lateralityMidpoint", lateralityMidpoint0);
        double lateralityMidpoint = lateralityMidpoint0 - (openness - 0.5) * 0.3;
        ctx.mod(PersonalityFacet.WITHDRAWAL, withdrawal, "lateralityMidpoint", lateralityMidpoint0, lateralityMidpoint);

        double conscientiousness = (ruleFollowingNumeric + 1.0 - impulsivity) / 2.0;
        double depthMidpoint0    = defaults.depthMidpoint();
        ctx.base("depthMidpoint", depthMidpoint0);
        double depthMidpoint = depthMidpoint0 - (conscientiousness - 0.5) * 0.3;
        ctx.mod(PersonalityFacet.IMPULSIVITY, impulsivity, "depthMidpoint", depthMidpoint0, depthMidpoint);

        double convergenceMidpoint0 = defaults.convergenceMidpoint();
        double convergenceMidpoint  = convergenceMidpoint0 - (conscientiousness - 0.5) * 0.2;

        double pathologicalThreshold0 = defaults.pathologicalThreshold();
        ctx.base("pathologicalThreshold", pathologicalThreshold0);
        double pathologicalThreshold = pathologicalThreshold0
                                       - (anxiousness - 0.5) * 0.6
                                       - (emotionalLability - 0.5) * 0.4;
        pathologicalThreshold = Math.max(0.5, pathologicalThreshold);
        ctx.mod(PersonalityFacet.ANXIOUSNESS, anxiousness, "pathologicalThreshold", pathologicalThreshold0, pathologicalThreshold);

        // Anhedonia — Pillny 2026 meta (g=-0.28): reduced sustained engagement
        double anhedonia = facetScore(facets, PersonalityFacet.ANHEDONIA, 0.5);
        double dm0 = depthMidpoint; depthMidpoint -= (anhedonia - 0.5) * 0.15;
        ctx.mod(PersonalityFacet.ANHEDONIA, anhedonia, "depthMidpoint", dm0, depthMidpoint);
        // Perseveration — Chamberlain 2006: set-shifting failure
        double perseveration = facetScore(facets, PersonalityFacet.PERSEVERATION, 0.5);
        double lm0 = lateralityMidpoint; lateralityMidpoint -= (perseveration - 0.5) * 0.15;
        ctx.mod(PersonalityFacet.PERSEVERATION, perseveration, "lateralityMidpoint", lm0, lateralityMidpoint);
        double cm0 = convergenceMidpoint; convergenceMidpoint += (perseveration - 0.5) * 0.1;
        ctx.mod(PersonalityFacet.PERSEVERATION, perseveration, "convergenceMidpoint", cm0, convergenceMidpoint);
        // Rigid Perfectionism — Cognitive flexibility 2025 (r=-.48)
        double rigidPerf = facetScore(facets, PersonalityFacet.RIGID_PERFECTIONISM, 0.5);
        cm0 = convergenceMidpoint; convergenceMidpoint += (rigidPerf - 0.5) * 0.1;
        ctx.mod(PersonalityFacet.RIGID_PERFECTIONISM, rigidPerf, "convergenceMidpoint", cm0, convergenceMidpoint);
        lm0 = lateralityMidpoint; lateralityMidpoint -= (rigidPerf - 0.5) * 0.1;
        ctx.mod(PersonalityFacet.RIGID_PERFECTIONISM, rigidPerf, "lateralityMidpoint", lm0, lateralityMidpoint);
        // Distractibility — Esterman 2014: sustained attention fluctuations
        double distractibility = facetScore(facets, PersonalityFacet.DISTRACTIBILITY, 0.5);
        dm0 = depthMidpoint; depthMidpoint -= (distractibility - 0.5) * 0.15;
        ctx.mod(PersonalityFacet.DISTRACTIBILITY, distractibility, "depthMidpoint", dm0, depthMidpoint);
        double bs0 = breadthSteepness; breadthSteepness += (distractibility - 0.5) * 0.1;
        ctx.mod(PersonalityFacet.DISTRACTIBILITY, distractibility, "breadthSteepness", bs0, breadthSteepness);
        // Eccentricity — Carson 2003 (latent inhibition); Wang 2018 (overinclusion)
        double eccentricity = facetScore(facets, PersonalityFacet.ECCENTRICITY, 0.5);
        lm0 = lateralityMidpoint; lateralityMidpoint += (eccentricity - 0.5) * 0.1;
        ctx.mod(PersonalityFacet.ECCENTRICITY, eccentricity, "lateralityMidpoint", lm0, lateralityMidpoint);
        bs0 = breadthSteepness; breadthSteepness += (eccentricity - 0.5) * 0.1;
        ctx.mod(PersonalityFacet.ECCENTRICITY, eccentricity, "breadthSteepness", bs0, breadthSteepness);
        // Deceitfulness — Bereczkei 2014/2017 (Machiavellian cognitive flexibility)
        double deceitfulness = facetScore(facets, PersonalityFacet.DECEITFULNESS, 0.5);
        lm0 = lateralityMidpoint; lateralityMidpoint += (deceitfulness - 0.5) * 0.1;
        ctx.mod(PersonalityFacet.DECEITFULNESS, deceitfulness, "lateralityMidpoint", lm0, lateralityMidpoint);
        dm0 = depthMidpoint; depthMidpoint += (deceitfulness - 0.5) * 0.1;
        ctx.mod(PersonalityFacet.DECEITFULNESS, deceitfulness, "depthMidpoint", dm0, depthMidpoint);
        // Cognitive Perceptual Dysregulation — Guralnik 2000 (DPD attention deficits)
        double cogDys = facetScore(facets, PersonalityFacet.COGNITIVE_PERCEPTUAL_DYSREGULATION, 0.5);
        dm0 = depthMidpoint; depthMidpoint -= (cogDys - 0.5) * 0.1;
        ctx.mod(PersonalityFacet.COGNITIVE_PERCEPTUAL_DYSREGULATION, cogDys, "depthMidpoint", dm0, depthMidpoint);

        var channelModulation = deriveChannelModulationDefaults(facets, axes);

        double extremePeak = 0.85 - (anxiousness - 0.5) * 0.3 - (emotionalLability - 0.5) * 0.2;
        extremePeak = Math.max(0.5, Math.min(0.95, extremePeak));

        double depthPeak       = clampPeak(extremePeak - 0.05 + conscientiousness * 0.1);
        double breadthPeak     = clampPeak(extremePeak + openness * 0.05);
        double lateralityPeak  = clampPeak(extremePeak - 0.1 + openness * 0.08);
        double convergencePeak = clampPeak(extremePeak + conscientiousness * 0.08);

        double depthDecay               = Math.max(0, (anxiousness - 0.3) * 2.0 - conscientiousness * 1.5);
        double breadthDecay             = 1.0 + (1.0 - openness) * 2.0;
        double lateralityDecay          = 1.5 + (1.0 - openness) * 1.5 + impulsivity * 1.0;
        double convergenceDecay         = Math.max(0, anxiousness * 1.5 - conscientiousness * 1.0);
        double depthAmplification       = conscientiousness * 1.0 - anxiousness * 0.5;
        double breadthAmplification     = -(1.0 - openness) * 1.5;
        double lateralityAmplification  = -(1.0 - openness) * 1.2;
        double convergenceAmplification = -(anxiousness * 2.0 - conscientiousness * 0.8);
        double amplificationThreshold   = clampPeak(extremePeak - conscientiousness * 0.1);
        var extremeInteraction = new ExtremeInteractionConfig(depthDecay, breadthDecay,
                                                              lateralityDecay, convergenceDecay, depthAmplification, breadthAmplification,
                                                              lateralityAmplification, convergenceAmplification,
                                                              depthPeak, breadthPeak, lateralityPeak, convergencePeak,
                                                              amplificationThreshold);

        double fatigueDepth   = Math.min(0.0, -0.08 * (1.0 - conscientiousness));
        double fatigueBreadth = -(0.15 + (1.0 - openness) * 0.15);
        double fatigueLat     = -(0.1 + (1.0 - openness) * 0.1) + impulsivity * 0.15;
        double fatigueConv    = conscientiousness * 0.2 - anxiousness * 0.15;
        var    fatigueEffects = new SearchFatigueEffects(fatigueDepth, fatigueBreadth, fatigueLat, fatigueConv);

        ctx.output("breadthSteepness", Math.max(1.0, breadthSteepness));
        ctx.output("breadthMidpoint", clamp01(breadthMidpoint));
        ctx.output("lateralityMidpoint", clamp01(lateralityMidpoint));
        ctx.output("depthMidpoint", clamp01(depthMidpoint));
        ctx.output("convergenceMidpoint", clamp01(convergenceMidpoint));
        ctx.output("pathologicalThreshold", pathologicalThreshold);
        ctx.output("extremePeak", extremePeak);
        ctx.stageEnd();

        return new SearchBudgetConfig(
                Math.max(1.0, breadthSteepness),
                clamp01(breadthMidpoint),
                defaults.lateralitySteepness(),
                clamp01(lateralityMidpoint),
                defaults.lateralityThreatFloor(),
                defaults.depthSteepness(),
                clamp01(depthMidpoint),
                defaults.convergenceSteepness(),
                clamp01(convergenceMidpoint),
                pathologicalThreshold,
                channelModulation,
                extremePeak,
                extremeInteraction,
                fatigueEffects);
    }


    static CognitiveResourcePoolConfig derivePoolConfig(List<PersonalityFacet> facets, DispositionAxes axes) {
        return derivePoolConfig(facets, axes, Ctx.NOOP);
    }

    static CognitiveResourcePoolConfig derivePoolConfig(List<PersonalityFacet> facets, DispositionAxes axes, Ctx ctx) {
        if (facets == null || facets.isEmpty()) {return null;}
        ctx.stageStart("derivePoolConfig");
        double impulsivity = facetScore(facets, PersonalityFacet.IMPULSIVITY, 0.5);
        double ruleFollowingNumeric = axes != null
                                      ? RULE_FOLLOWING_NUMERIC.getOrDefault(lower(axes.ruleFollowing()), 0.5) : 0.5;
        double conscientiousness = (ruleFollowingNumeric + 1.0 - impulsivity) / 2.0;
        ctx.base("conscientiousness", conscientiousness);
        int    maxEffort    = (int) (6 + conscientiousness * 8);
        double recoveryRate = 0.3 + conscientiousness * 0.4;
        ctx.mod(PersonalityFacet.IMPULSIVITY, impulsivity, "maxEffort", 6.0, maxEffort);
        // Anhedonia — Oxford/Cerebral Cortex 2024: aberrant effort-based reward dynamics
        double an = facetScore(facets, PersonalityFacet.ANHEDONIA, 0.5);
        double me0 = maxEffort; maxEffort -= (int) Math.round((an - 0.5) * 3.0);
        ctx.mod(PersonalityFacet.ANHEDONIA, an, "maxEffort", me0, maxEffort);
        // Depressivity — Pillny 2026 meta (g=-0.28)
        double dp = facetScore(facets, PersonalityFacet.DEPRESSIVITY, 0.5);
        me0 = maxEffort; maxEffort -= (int) Math.round((dp - 0.5) * 2.0);
        ctx.mod(PersonalityFacet.DEPRESSIVITY, dp, "maxEffort", me0, maxEffort);
        // Rigid Perfectionism — Philp 2012: overcommitment mediation
        double rp = facetScore(facets, PersonalityFacet.RIGID_PERFECTIONISM, 0.5);
        me0 = maxEffort; maxEffort += (int) Math.round((rp - 0.5) * 3.0);
        ctx.mod(PersonalityFacet.RIGID_PERFECTIONISM, rp, "maxEffort", me0, maxEffort);
        // Distractibility — Esterman & Rothlein 2019
        double dt = facetScore(facets, PersonalityFacet.DISTRACTIBILITY, 0.5);
        me0 = maxEffort; maxEffort -= (int) Math.round((dt - 0.5) * 2.0);
        ctx.mod(PersonalityFacet.DISTRACTIBILITY, dt, "maxEffort", me0, maxEffort);
        // Irresponsibility — Crede 2017 meta (N=66,807)
        double ir = facetScore(facets, PersonalityFacet.IRRESPONSIBILITY, 0.5);
        me0 = maxEffort; maxEffort -= (int) Math.round((ir - 0.5) * 3.0);
        ctx.mod(PersonalityFacet.IRRESPONSIBILITY, ir, "maxEffort", me0, maxEffort);
        maxEffort = Math.max(3, maxEffort);
        ctx.output("maxEffort", maxEffort);
        ctx.output("recoveryRate", recoveryRate);
        ctx.stageEnd();
        return new CognitiveResourcePoolConfig(maxEffort, recoveryRate);
    }


    private static ChannelModulationConfig deriveChannelModulationDefaults(
            List<PersonalityFacet> facets, DispositionAxes axes) {
        double withdrawal = facetScore(facets, PersonalityFacet.WITHDRAWAL, 0.5);
        double intimacyAvoidance = facetScore(facets, PersonalityFacet.INTIMACY_AVOIDANCE, 0.5);
        double suspiciousness = facetScore(facets, PersonalityFacet.SUSPICIOUSNESS, 0.5);
        double impulsivity = facetScore(facets, PersonalityFacet.IMPULSIVITY, 0.5);
        double anxiousness = facetScore(facets, PersonalityFacet.ANXIOUSNESS, 0.5);

        double ruleFollowingNumeric = axes != null
                ? RULE_FOLLOWING_NUMERIC.getOrDefault(lower(axes.ruleFollowing()), 0.5) : 0.5;

        double openness = (1.0 - withdrawal + 1.0 - intimacyAvoidance + 1.0 - suspiciousness) / 3.0;
        double conscientiousness = (ruleFollowingNumeric + 1.0 - impulsivity) / 2.0;

        var d = ChannelModulationConfig.defaults();
        return new ChannelModulationConfig(
                d.specificDepthBoost() + (conscientiousness - 0.5) * 0.08,
                d.specificBreadthPenalty() + (conscientiousness - 0.5) * 0.06,
                d.diversiveBreadthBoost() + (openness - 0.5) * 0.10,
                d.diversiveLateralityBoost() + (openness - 0.5) * 0.08,
                d.diversiveConvergencePenalty() + (openness - 0.5) * 0.06,
                d.deprivationConvergenceBoost() + (anxiousness - 0.5) * 0.08,
                d.lowComprehensionLateralityBoost() + (openness - 0.5) * 0.08,
                d.highComprehensionConvergenceBoost() + (conscientiousness - 0.5) * 0.08);
    }

    private static double facetScore(List<PersonalityFacet> facets, String name, double defaultValue) {
        return facets.stream().filter(f -> name.equals(f.facet())).findFirst()
                .map(PersonalityFacet::score).orElse(defaultValue);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double clampPeak(double value) {
        return Math.max(0.5, Math.min(0.95, value));
    }


    private static boolean isHigh(List<PersonalityFacet> facets, String name) {
        return facets.stream().filter(f -> name.equals(f.facet())).findFirst()
                .map(PersonalityFacet::isHigh).orElse(false);
    }

    private static boolean isLow(List<PersonalityFacet> facets, String name) {
        return facets.stream().filter(f -> name.equals(f.facet())).findFirst()
                .map(PersonalityFacet::isLow).orElse(false);
    }


    private static String lower(String s) {
        return s != null ? s.toLowerCase() : "";
    }
}

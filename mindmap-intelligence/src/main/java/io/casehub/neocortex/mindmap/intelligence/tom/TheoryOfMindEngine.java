package io.casehub.neocortex.mindmap.intelligence.tom;

import io.quarkmind.inference.bayes.BayesNetwork;
import io.quarkmind.inference.bayes.BayesVariable;
import io.quarkmind.inference.bayes.graph.GraphNode;
import io.quarkmind.perception.EvidenceFusionEngine;

/**
 * Bayesian Theory of Mind engine for inferring another character's emotional
 * state from observed behavior and situational threat level.
 *
 * Network structure: [SituationThreat] → [EmotionalState] → [ObservedBehavior]
 *
 * CPTs grounded in appraisal theory (Scherer 2001; Ellsworth & Scherer 2003):
 * - Threat causes emotional state (goal-incongruence → distress)
 * - Emotional state causes observable behavior (distress → avoidance)
 *
 * The engine handles diagnostic inference: given observed behavior and threat,
 * infer the latent emotional state via junction tree exact inference.
 */
public class TheoryOfMindEngine {

    private static final String VAR_THREAT = "SituationThreat";
    private static final String VAR_EMOTION = "EmotionalState";
    private static final String VAR_BEHAVIOR = "ObservedBehavior";

    private final EvidenceFusionEngine engine;

    public TheoryOfMindEngine(TomPersonalityModulation modulation) {
        engine = new EvidenceFusionEngine();
        engine.initialize(buildNetwork(modulation), VAR_EMOTION);
    }

    public TheoryOfMindEngine() {
        this(TomPersonalityModulation.NEUTRAL);
    }

    public void observeBehavior(BehaviorType type) {
        double[] likelihood = new double[3];
        likelihood[type.ordinal()] = 1.0;
        engine.setEvidence(VAR_BEHAVIOR, likelihood);
    }

    public void observeBehavior(double[] softEvidence) {
        engine.setEvidence(VAR_BEHAVIOR, softEvidence);
    }

    public void observeThreat(ThreatLevel level) {
        double[] likelihood = new double[3];
        likelihood[level.ordinal()] = 1.0;
        engine.setEvidence(VAR_THREAT, likelihood);
    }

    public void observeThreat(double[] softEvidence) {
        engine.setEvidence(VAR_THREAT, softEvidence);
    }

    public double[] inferEmotionalState() {
        engine.update();
        return engine.marginalize();
    }

    public void reset() {
        engine.reset();
    }

    private static BayesNetwork buildNetwork(TomPersonalityModulation mod) {
        var graph = new BayesNetwork("TheoryOfMind");

        double ax = mod.anxiousness();
        double ca = mod.callousness();

        // Threat prior -- anxiety shifts toward elevated threat (Bar-Haim et al. 2007)
        double[] baselinePrior = {0.60, 0.30, 0.10};
        double[] anxiousPrior  = {0.30, 0.40, 0.30};
        double[] relaxedPrior  = {0.80, 0.15, 0.05};
        double[] threatPrior = new double[3];
        for (int i = 0; i < 3; i++) {
            if (ax >= 0.5) {
                threatPrior[i] = baselinePrior[i] + (ax - 0.5) * 2.0 * (anxiousPrior[i] - baselinePrior[i]);
            } else {
                threatPrior[i] = baselinePrior[i] + (0.5 - ax) * 2.0 * (relaxedPrior[i] - baselinePrior[i]);
            }
        }

        GraphNode<BayesVariable> threatNode = graph.addNode();
        threatNode.setContent(new BayesVariable<>(VAR_THREAT,
            threatNode.getId(),
            new String[]{"NONE", "MILD", "SEVERE"},
            new double[][]{threatPrior}));

        // Emotion | Threat CPT -- appraisal theory (Scherer 2001)
        GraphNode<BayesVariable> emotionNode = graph.addNode();
        emotionNode.setContent(new BayesVariable<>(VAR_EMOTION,
            emotionNode.getId(),
            new String[]{"POSITIVE", "NEUTRAL", "DISTRESSED"},
            new double[][]{
                {0.50, 0.40, 0.10},
                {0.15, 0.40, 0.45},
                {0.05, 0.15, 0.80}
            },
            new String[]{VAR_THREAT}));

        // Behavior | Emotion CPT -- callousness weakens distress->avoidant coupling (Blair 2005/2013)
        double[] baseDistressed = {0.05, 0.20, 0.75};
        double[] callousDistressed = {0.15, 0.50, 0.35};
        double[] distressedRow = new double[3];
        for (int i = 0; i < 3; i++) {
            distressedRow[i] = baseDistressed[i] + ca * (callousDistressed[i] - baseDistressed[i]);
        }

        GraphNode<BayesVariable> behaviorNode = graph.addNode();
        behaviorNode.setContent(new BayesVariable<>(VAR_BEHAVIOR,
            behaviorNode.getId(),
            new String[]{"APPROACH", "NEUTRAL", "AVOIDANT"},
            new double[][]{
                {0.70, 0.25, 0.05},
                {0.25, 0.60, 0.15},
                distressedRow
            },
            new String[]{VAR_EMOTION}));

        graph.addEdge(threatNode, emotionNode);
        graph.addEdge(emotionNode, behaviorNode);

        return graph;
    }
}

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

    public TheoryOfMindEngine() {
        engine = new EvidenceFusionEngine();
        engine.initialize(buildNetwork(), VAR_EMOTION);
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

    private static BayesNetwork buildNetwork() {
        var graph = new BayesNetwork("TheoryOfMind");

        // Root: SituationThreat — prior distribution
        // Baseline assumption: most situations are non-threatening
        GraphNode<BayesVariable> threatNode = graph.addNode();
        threatNode.setContent(new BayesVariable<>(VAR_THREAT,
            threatNode.getId(),
            new String[]{"NONE", "MILD", "SEVERE"},
            new double[][]{{0.60, 0.30, 0.10}}));

        // Hidden: EmotionalState — conditioned on SituationThreat
        // P(emotion | threat) — appraisal theory: threat → distress
        GraphNode<BayesVariable> emotionNode = graph.addNode();
        emotionNode.setContent(new BayesVariable<>(VAR_EMOTION,
            emotionNode.getId(),
            new String[]{"POSITIVE", "NEUTRAL", "DISTRESSED"},
            new double[][]{
                // P(positive, neutral, distressed | threat=NONE)
                {0.50, 0.40, 0.10},
                // P(positive, neutral, distressed | threat=MILD)
                {0.15, 0.40, 0.45},
                // P(positive, neutral, distressed | threat=SEVERE)
                {0.05, 0.15, 0.80}
            },
            new String[]{VAR_THREAT}));

        // Observable: ObservedBehavior — conditioned on EmotionalState
        // P(behavior | emotion) — distress → avoidance, positive → approach
        GraphNode<BayesVariable> behaviorNode = graph.addNode();
        behaviorNode.setContent(new BayesVariable<>(VAR_BEHAVIOR,
            behaviorNode.getId(),
            new String[]{"APPROACH", "NEUTRAL", "AVOIDANT"},
            new double[][]{
                // P(approach, neutral, avoidant | emotion=POSITIVE)
                {0.70, 0.25, 0.05},
                // P(approach, neutral, avoidant | emotion=NEUTRAL)
                {0.25, 0.60, 0.15},
                // P(approach, neutral, avoidant | emotion=DISTRESSED)
                {0.05, 0.20, 0.75}
            },
            new String[]{VAR_EMOTION}));

        graph.addEdge(threatNode, emotionNode);
        graph.addEdge(emotionNode, behaviorNode);

        return graph;
    }
}

package io.quarkmind.inference.bayes;

import io.quarkmind.inference.bayes.graph.GraphNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InfluenceDiagramEngine {

    public static PolicyResult solve(InfluenceDiagram diagram) {
        return solve(diagram, new String[0]);
    }

    public static PolicyResult solve(InfluenceDiagram diagram,
                                      Map<String, String> evidence) {
        String decisionVar = findSingleDecision(diagram);
        BayesNetwork network = diagram.network();
        BayesVariable decision = findVariable(network, decisionVar);
        Object[] actions = decision.getOutcomes();

        Map<String, Double> actionEUs = new LinkedHashMap<>();
        for (Object action : actions) {
            JunctionTree tree = new JunctionTreeBuilder(network).build();
            BayesInstance<?> instance = new BayesInstance<>(tree);
            for (Map.Entry<String, String> entry : evidence.entrySet()) {
                instance.setHardEvidence(entry.getKey(), entry.getValue());
            }
            instance.setHardEvidence(decisionVar, String.valueOf(action));
            instance.globalUpdate();

            double eu = computeTotalEU(instance, diagram.utilityNodes(),
                                        diagram.decisionNodeNames());
            actionEUs.put(String.valueOf(action), eu);
        }

        String bestAction = null;
        double bestEU = Double.NEGATIVE_INFINITY;
        for (Map.Entry<String, Double> entry : actionEUs.entrySet()) {
            if (entry.getValue() > bestEU) {
                bestEU = entry.getValue();
                bestAction = entry.getKey();
            }
        }

        Map<Map<String, String>, String> policy = new LinkedHashMap<>();
        policy.put(Map.copyOf(evidence), bestAction);

        return new PolicyResult(decisionVar, policy, bestEU, actionEUs);
    }

    public static PolicyResult solve(InfluenceDiagram diagram,
                                      String... informationVariables) {
        if (informationVariables.length == 0) {
            return solve(diagram, Map.of());
        }

        String decisionVar = findSingleDecision(diagram);
        BayesNetwork network = diagram.network();
        BayesVariable decision = findVariable(network, decisionVar);
        Object[] actions = decision.getOutcomes();

        BayesVariable[] infoVars = new BayesVariable[informationVariables.length];
        for (int i = 0; i < informationVariables.length; i++) {
            infoVars[i] = findVariable(network, informationVariables[i]);
        }

        Map<Map<String, String>, String> policy = new LinkedHashMap<>();
        Map<String, Double> aggregateActionEUs = new LinkedHashMap<>();
        for (Object action : actions) {
            aggregateActionEUs.put(String.valueOf(action), 0.0);
        }
        double maxPolicyEU = 0.0;

        List<Map<String, String>> configs = cartesianProduct(infoVars, informationVariables);

        JunctionTree priorTree = new JunctionTreeBuilder(network).build();
        BayesInstance<?> priorInstance = new BayesInstance<>(priorTree);
        priorInstance.globalUpdate();

        for (Map<String, String> config : configs) {
            double configProb = computeConfigProbability(priorInstance, config);

            Map<String, Double> actionEUs = new LinkedHashMap<>();
            for (Object action : actions) {
                JunctionTree tree = new JunctionTreeBuilder(network).build();
                BayesInstance<?> instance = new BayesInstance<>(tree);
                for (Map.Entry<String, String> entry : config.entrySet()) {
                    instance.setHardEvidence(entry.getKey(), entry.getValue());
                }
                instance.setHardEvidence(decisionVar, String.valueOf(action));
                instance.globalUpdate();

                double eu = computeTotalEU(instance, diagram.utilityNodes(),
                                            diagram.decisionNodeNames());
                actionEUs.put(String.valueOf(action), eu);
            }

            String bestAction = null;
            double bestEU = Double.NEGATIVE_INFINITY;
            for (Map.Entry<String, Double> entry : actionEUs.entrySet()) {
                if (entry.getValue() > bestEU) {
                    bestEU = entry.getValue();
                    bestAction = entry.getKey();
                }
            }

            policy.put(config, bestAction);
            maxPolicyEU += configProb * bestEU;

            for (Map.Entry<String, Double> entry : actionEUs.entrySet()) {
                aggregateActionEUs.merge(entry.getKey(), configProb * entry.getValue(), Double::sum);
            }
        }

        return new PolicyResult(decisionVar, policy, maxPolicyEU, aggregateActionEUs);
    }

    private static double computeTotalEU(BayesInstance<?> instance,
                                           List<UtilityNode> utilityNodes,
                                           Set<String> decisionNames) {
        double total = 0.0;
        for (UtilityNode util : utilityNodes) {
            total += computeExpectedUtility(instance, util, decisionNames);
        }
        return total;
    }

    private static double computeExpectedUtility(BayesInstance<?> instance,
                                                   UtilityNode util,
                                                   Set<String> decisionNames) {
        String[] parents = util.parents();
        double[] utilities = util.utilities();
        Map<String, BayesVariable> variables = instance.getVariables();

        List<String> chanceParents = new ArrayList<>();
        Map<String, Integer> fixedDecisionIndices = new LinkedHashMap<>();

        for (String parentName : parents) {
            if (decisionNames.contains(parentName)) {
                Map<String, double[]> evidence = instance.getEvidenceVariables();
                double[] dist = evidence.get(parentName);
                int fixedIndex = argmax(dist);
                fixedDecisionIndices.put(parentName, fixedIndex);
            } else {
                chanceParents.add(parentName);
            }
        }

        if (chanceParents.isEmpty()) {
            int configIndex = computeConfigIndex(parents, variables,
                                                  fixedDecisionIndices, Map.of());
            return utilities[configIndex];
        }

        if (chanceParents.size() == 1) {
            String chanceParent = chanceParents.get(0);
            double[] dist = instance.marginalize(chanceParent).getDistribution();
            double eu = 0.0;
            for (int s = 0; s < dist.length; s++) {
                Map<String, Integer> chanceIndices = Map.of(chanceParent, s);
                int configIndex = computeConfigIndex(parents, variables,
                                                      fixedDecisionIndices, chanceIndices);
                eu += dist[s] * utilities[configIndex];
            }
            return eu;
        }

        return chainRuleEU(instance, util, chanceParents, fixedDecisionIndices);
    }

    private static double chainRuleEU(BayesInstance<?> instance,
                                        UtilityNode util,
                                        List<String> chanceParents,
                                        Map<String, Integer> fixedDecisionIndices) {
        Map<String, BayesVariable> variables = instance.getVariables();
        String[] parents = util.parents();
        double[] utilities = util.utilities();

        String firstChance = chanceParents.get(0);
        List<String> remainingChance = chanceParents.subList(1, chanceParents.size());

        double[] firstDist = instance.marginalize(firstChance).getDistribution();
        BayesVariable firstVar = variables.get(firstChance);

        double eu = 0.0;
        for (int s = 0; s < firstDist.length; s++) {
            double pFirst = firstDist[s];
            if (pFirst < 1e-15) continue;

            JunctionTree condTree = new JunctionTreeBuilder(
                instance.getJunctionTree().getGraph()).build();
            BayesInstance<?> condInstance = new BayesInstance<>(condTree);

            Map<String, double[]> currentEvidence = instance.getEvidenceVariables();
            for (Map.Entry<String, double[]> entry : currentEvidence.entrySet()) {
                condInstance.setLikelyhood(entry.getKey(), entry.getValue());
            }
            condInstance.setHardEvidence(firstChance, String.valueOf(firstVar.getOutcomes()[s]));
            condInstance.globalUpdate();

            if (remainingChance.size() == 1) {
                String remaining = remainingChance.get(0);
                double[] condDist = condInstance.marginalize(remaining).getDistribution();
                for (int r = 0; r < condDist.length; r++) {
                    Map<String, Integer> chanceIndices = new LinkedHashMap<>();
                    chanceIndices.put(firstChance, s);
                    chanceIndices.put(remaining, r);
                    int configIndex = computeConfigIndex(parents, variables,
                                                          fixedDecisionIndices, chanceIndices);
                    eu += pFirst * condDist[r] * utilities[configIndex];
                }
            } else {
                Map<String, Integer> augmented = new LinkedHashMap<>(fixedDecisionIndices);
                augmented.put(firstChance, s);
                eu += pFirst * chainRuleEU(condInstance, util,
                                            remainingChance, augmented);
            }
        }
        return eu;
    }

    static int computeConfigIndex(String[] parents,
                                    Map<String, BayesVariable> variables,
                                    Map<String, Integer> fixedIndices,
                                    Map<String, Integer> chanceIndices) {
        int index = 0;
        int multiplier = 1;
        for (int i = parents.length - 1; i >= 0; i--) {
            String parentName = parents[i];
            int stateIndex;
            if (fixedIndices.containsKey(parentName)) {
                stateIndex = fixedIndices.get(parentName);
            } else if (chanceIndices.containsKey(parentName)) {
                stateIndex = chanceIndices.get(parentName);
            } else {
                throw new IllegalStateException("No index for parent: " + parentName);
            }
            index += stateIndex * multiplier;
            multiplier *= variables.get(parentName).getOutcomes().length;
        }
        return index;
    }

    private static double computeConfigProbability(BayesInstance<?> instance,
                                                     Map<String, String> config) {
        if (config.isEmpty()) return 1.0;

        List<String> varNames = new ArrayList<>(config.keySet());
        double prob = 1.0;

        BayesInstance<?> current = instance;
        for (int i = 0; i < varNames.size(); i++) {
            String varName = varNames.get(i);
            String state = config.get(varName);
            double[] dist = current.marginalize(varName).getDistribution();
            BayesVariable var = current.getVariables().get(varName);
            Object[] outcomes = var.getOutcomes();
            for (int j = 0; j < outcomes.length; j++) {
                if (state.equals(String.valueOf(outcomes[j]))) {
                    prob *= dist[j];
                    break;
                }
            }

            if (i < varNames.size() - 1) {
                JunctionTree condTree = new JunctionTreeBuilder(
                    current.getJunctionTree().getGraph()).build();
                BayesInstance<?> condInst = new BayesInstance<>(condTree);
                Map<String, double[]> ev = current.getEvidenceVariables();
                for (Map.Entry<String, double[]> entry : ev.entrySet()) {
                    condInst.setLikelyhood(entry.getKey(), entry.getValue());
                }
                condInst.setHardEvidence(varName, state);
                condInst.globalUpdate();
                current = condInst;
            }
        }
        return prob;
    }

    static List<Map<String, String>> cartesianProduct(BayesVariable[] vars,
                                                        String[] varNames) {
        List<Map<String, String>> configs = new ArrayList<>();
        int total = 1;
        for (BayesVariable var : vars) {
            total *= var.getOutcomes().length;
        }

        for (int i = 0; i < total; i++) {
            Map<String, String> config = new LinkedHashMap<>();
            int remainder = i;
            for (int v = vars.length - 1; v >= 0; v--) {
                Object[] outcomes = vars[v].getOutcomes();
                int stateIndex = remainder % outcomes.length;
                remainder /= outcomes.length;
                config.put(varNames[v], String.valueOf(outcomes[stateIndex]));
            }
            configs.add(config);
        }
        return configs;
    }

    private static String findSingleDecision(InfluenceDiagram diagram) {
        Set<String> decisions = diagram.decisionNodeNames();
        if (decisions.size() != 1) {
            throw new IllegalArgumentException(
                "Expected exactly 1 decision variable, found " + decisions.size());
        }
        return decisions.iterator().next();
    }

    private static BayesVariable findVariable(BayesNetwork network, String name) {
        for (GraphNode<BayesVariable> node : network) {
            if (name.equals(node.getContent().getName())) {
                return node.getContent();
            }
        }
        throw new IllegalArgumentException("Variable not found: " + name);
    }

    private static int argmax(double[] array) {
        int best = 0;
        for (int i = 1; i < array.length; i++) {
            if (array[i] > array[best]) best = i;
        }
        return best;
    }
}

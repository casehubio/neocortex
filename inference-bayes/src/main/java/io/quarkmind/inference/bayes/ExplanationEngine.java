package io.quarkmind.inference.bayes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ExplanationEngine {

    public static ExplanationTrace explain(BayesNetwork network,
                                            BayesInstance<?> instance,
                                            String targetVar) {
        BayesVariable tgtVar = instance.getVariables().get(targetVar);
        if (tgtVar == null) {
            throw new IllegalArgumentException("Target variable not found: " + targetVar);
        }

        Map<String, double[]> evidence = instance.getEvidenceVariables();
        if (evidence.isEmpty()) {
            throw new IllegalArgumentException("No evidence set on instance");
        }

        JunctionTree tree = instance.getJunctionTree();

        BayesInstance<?> priorInstance = new BayesInstance<>(tree);
        priorInstance.globalUpdateForced();
        double[] prior = priorInstance.marginalize(targetVar).getDistribution().clone();

        double[] posterior = instance.marginalize(targetVar).getDistribution().clone();

        int argmax = argmax(posterior);

        String[] targetOutcomes = toStringArray(tgtVar.getOutcomes());

        List<EvidenceInfluence> influences = new ArrayList<>();
        for (Map.Entry<String, double[]> entry : evidence.entrySet()) {
            String evName = entry.getKey();

            BayesInstance<?> without = new BayesInstance<>(tree);
            for (Map.Entry<String, double[]> other : evidence.entrySet()) {
                if (!other.getKey().equals(evName)) {
                    without.setLikelyhood(other.getKey(), other.getValue());
                }
            }
            without.globalUpdateForced();
            double[] withoutDist = without.marginalize(targetVar).getDistribution();

            double influence = BayesInstance.klDivergence(posterior, withoutDist);

            Direction direction = withoutDist[argmax] < posterior[argmax]
                    ? Direction.INCREASED : Direction.DECREASED;

            List<String> messagePath = findMessagePath(tree, instance, evName, targetVar);

            boolean confounded = false;
            double causalEffect = 0.0;
            try {
                String observedState = findObservedState(instance, evName);
                CounterfactualResult cfResult = CausalEngine.counterfactual(
                        network, evName, observedState, targetVar);
                confounded = cfResult.hasConfounding(0.01);
                causalEffect = cfResult.causalEffect(argmax);
            } catch (Exception e) {
                // Counterfactual analysis may fail for disconnected mutilated graphs
            }

            influences.add(new EvidenceInfluence(
                    evName, influence, direction, messagePath,
                    confounded, causalEffect));
        }

        influences.sort(Comparator.comparingDouble(EvidenceInfluence::influence).reversed());

        return new ExplanationTrace(targetVar, prior, posterior, targetOutcomes, influences);
    }

    static List<String> findMessagePath(JunctionTree tree,
                                         BayesInstance<?> instance,
                                         String evidenceVar, String targetVar) {
        BayesVariable evVar = instance.getVariables().get(evidenceVar);
        BayesVariable tgtVar = instance.getVariables().get(targetVar);

        int sourceCliqueId = evVar.getFamily();
        int targetCliqueId = tgtVar.getFamily();

        if (sourceCliqueId == targetCliqueId) {
            JunctionTreeClique clique = tree.getJunctionTreeNodes()[sourceCliqueId];
            return List.of(formatCliqueVars(clique));
        }

        JunctionTreeClique[] cliques = tree.getJunctionTreeNodes();

        int n = cliques.length;
        int[] parent = new int[n];
        boolean[] visited = new boolean[n];
        Arrays.fill(parent, -1);
        visited[sourceCliqueId] = true;

        java.util.Queue<Integer> queue = new java.util.ArrayDeque<>();
        queue.add(sourceCliqueId);

        while (!queue.isEmpty()) {
            int current = queue.poll();
            if (current == targetCliqueId) break;

            JunctionTreeClique clique = cliques[current];
            if (clique == null) continue;

            for (JunctionTreeSeparator sep : clique.getChildren()) {
                int childId = sep.getChild().getId();
                if (!visited[childId]) {
                    visited[childId] = true;
                    parent[childId] = current;
                    queue.add(childId);
                }
            }

            JunctionTreeSeparator parentSep = clique.getParentSeparator();
            if (parentSep != null) {
                int parentId = parentSep.getParent().getId();
                if (!visited[parentId]) {
                    visited[parentId] = true;
                    parent[parentId] = current;
                    queue.add(parentId);
                }
            }
        }

        List<Integer> cliqueIds = new ArrayList<>();
        int current = targetCliqueId;
        while (current != -1) {
            cliqueIds.add(current);
            current = parent[current];
        }
        Collections.reverse(cliqueIds);

        List<String> path = new ArrayList<>();
        for (int i = 0; i < cliqueIds.size(); i++) {
            JunctionTreeClique clique = cliques[cliqueIds.get(i)];
            path.add(formatCliqueVars(clique));

            if (i < cliqueIds.size() - 1) {
                JunctionTreeClique nextClique = cliques[cliqueIds.get(i + 1)];
                JunctionTreeSeparator sep = findSeparator(clique, nextClique);
                if (sep != null) {
                    path.add(formatSeparatorVars(sep));
                }
            }
        }

        return path;
    }

    private static JunctionTreeSeparator findSeparator(JunctionTreeClique a,
                                                        JunctionTreeClique b) {
        for (JunctionTreeSeparator sep : a.getChildren()) {
            if (sep.getChild().getId() == b.getId()) return sep;
        }
        for (JunctionTreeSeparator sep : b.getChildren()) {
            if (sep.getChild().getId() == a.getId()) return sep;
        }
        return null;
    }

    private static String formatCliqueVars(JunctionTreeClique clique) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < clique.getValues().size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(clique.getValues().get(i).getName());
        }
        return sb.toString();
    }

    private static String formatSeparatorVars(JunctionTreeSeparator sep) {
        StringBuilder sb = new StringBuilder();
        List<BayesVariable> vars = sep.getValues();
        for (int i = 0; i < vars.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(vars.get(i).getName());
        }
        return sb.toString();
    }

    static String findObservedState(BayesInstance<?> instance, String varName) {
        Map<String, double[]> evidence = instance.getEvidenceVariables();
        double[] dist = evidence.get(varName);
        if (dist == null) {
            throw new IllegalArgumentException("No evidence for variable: " + varName);
        }
        BayesVariable var = instance.getVariables().get(varName);
        int argmax = argmax(dist);
        return String.valueOf(var.getOutcomes()[argmax]);
    }

    private static int argmax(double[] array) {
        int best = 0;
        for (int i = 1; i < array.length; i++) {
            if (array[i] > array[best]) best = i;
        }
        return best;
    }

    private static String[] toStringArray(Object[] outcomes) {
        String[] result = new String[outcomes.length];
        for (int i = 0; i < outcomes.length; i++) {
            result[i] = String.valueOf(outcomes[i]);
        }
        return result;
    }
}

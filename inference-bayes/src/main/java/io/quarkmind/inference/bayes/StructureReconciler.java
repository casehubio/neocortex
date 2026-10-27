package io.quarkmind.inference.bayes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class StructureReconciler {

    private StructureReconciler() {}

    public static ReconciliationResult reconcile(
            NetworkSpec prior,
            NetworkSpec learned,
            ConflictPolicy policy) {

        Set<String> learnedVarNames = varNames(learned);

        Map<String, EdgeSpec> priorEdges = edgeMap(prior);
        Map<String, EdgeSpec> learnedEdges = edgeMap(learned);

        Set<String> allEdgeKeys = new HashSet<>();
        allEdgeKeys.addAll(priorEdges.keySet());
        allEdgeKeys.addAll(learnedEdges.keySet());

        var edgeResolutions = new ArrayList<EdgeResolution>();
        var conflicts = new ArrayList<StructuralConflict>();
        var mergedEdges = new ArrayList<EdgeSpec>();

        for (String key : allEdgeKeys) {
            EdgeSpec pe = priorEdges.get(key);
            EdgeSpec le = learnedEdges.get(key);

            if (pe != null && le != null) {
                edgeResolutions.add(new EdgeResolution(pe.from(), pe.to(),
                    EdgeProvenance.DATA_CONFIRMED,
                    "Prior: " + pe.rationale() + "; Data: " + le.rationale()));
                mergedEdges.add(new EdgeSpec(pe.from(), pe.to(),
                    "Prior: " + pe.rationale() + "; Data: " + le.rationale(),
                    SpecStatus.ACCEPTED));
            } else if (pe != null) {
                handlePriorOnlyEdge(pe, learnedEdges, learnedVarNames, policy,
                    edgeResolutions, conflicts, mergedEdges);
            } else {
                String[] parts = key.split("->");
                String from = parts[0];
                String to = parts[1];
                String reverseKey = to + "->" + from;
                if (priorEdges.containsKey(reverseKey)) {
                    continue;
                }
                edgeResolutions.add(new EdgeResolution(from, to,
                    EdgeProvenance.DATA_DISCOVERED, le.rationale()));
                mergedEdges.add(new EdgeSpec(from, to, le.rationale(),
                    SpecStatus.PROPOSED));
            }
        }

        var mergedVars = mergeVariables(prior, learned);
        var mergedSpec = new NetworkSpec(
            prior.name() != null ? prior.name() : learned.name(),
            mergedVars, mergedEdges,
            prior.domainDescription());

        return new ReconciliationResult(mergedSpec, edgeResolutions, conflicts);
    }

    private static void handlePriorOnlyEdge(EdgeSpec pe,
                                             Map<String, EdgeSpec> learnedEdges,
                                             Set<String> learnedVarNames,
                                             ConflictPolicy policy,
                                             List<EdgeResolution> edgeResolutions,
                                             List<StructuralConflict> conflicts,
                                             List<EdgeSpec> mergedEdges) {
        String reverseKey = pe.to() + "->" + pe.from();
        EdgeSpec reversed = learnedEdges.get(reverseKey);

        if (reversed != null) {
            conflicts.add(new StructuralConflict(pe.from(), pe.to(),
                ConflictType.DIRECTION, pe.rationale(), reversed.rationale(),
                resolveDirectionConflict(policy)));
            EdgeSpec winner = switch (policy) {
                case TRUST_DATA -> new EdgeSpec(reversed.from(), reversed.to(),
                    "Direction conflict resolved: " + reversed.rationale(),
                    SpecStatus.ACCEPTED);
                case TRUST_PRIOR -> new EdgeSpec(pe.from(), pe.to(),
                    "Direction conflict resolved: " + pe.rationale(),
                    pe.status() == SpecStatus.LOCKED ? SpecStatus.LOCKED : SpecStatus.ACCEPTED);
                case FLAG_FOR_REVIEW -> new EdgeSpec(pe.from(), pe.to(),
                    "Direction conflict — flagged for review. Prior: " + pe.rationale()
                        + "; Data: " + reversed.rationale(),
                    SpecStatus.PROPOSED);
            };
            mergedEdges.add(winner);
            edgeResolutions.add(new EdgeResolution(winner.from(), winner.to(),
                EdgeProvenance.LLM_REJECTED_BY_DATA, winner.rationale()));
        } else if (pe.status() == SpecStatus.LOCKED) {
            edgeResolutions.add(new EdgeResolution(pe.from(), pe.to(),
                EdgeProvenance.LLM_PROPOSED, pe.rationale()));
            mergedEdges.add(new EdgeSpec(pe.from(), pe.to(), pe.rationale(),
                SpecStatus.LOCKED));
        } else if (!learnedVarNames.contains(pe.from())
                   || !learnedVarNames.contains(pe.to())) {
            edgeResolutions.add(new EdgeResolution(pe.from(), pe.to(),
                EdgeProvenance.LLM_PROPOSED, pe.rationale()));
            mergedEdges.add(new EdgeSpec(pe.from(), pe.to(), pe.rationale(),
                SpecStatus.PROPOSED));
        } else {
            conflicts.add(new StructuralConflict(pe.from(), pe.to(),
                ConflictType.PRESENCE, pe.rationale(), null,
                resolvePresenceConflict(policy)));
            switch (policy) {
                case TRUST_DATA -> edgeResolutions.add(new EdgeResolution(
                    pe.from(), pe.to(), EdgeProvenance.LLM_REJECTED_BY_DATA,
                    pe.rationale()));
                case TRUST_PRIOR -> {
                    edgeResolutions.add(new EdgeResolution(pe.from(), pe.to(),
                        EdgeProvenance.LLM_PROPOSED, pe.rationale()));
                    mergedEdges.add(new EdgeSpec(pe.from(), pe.to(),
                        pe.rationale(), pe.status()));
                }
                case FLAG_FOR_REVIEW -> {
                    edgeResolutions.add(new EdgeResolution(pe.from(), pe.to(),
                        EdgeProvenance.LLM_REJECTED_BY_DATA,
                        "Flagged for review: " + pe.rationale()));
                    mergedEdges.add(new EdgeSpec(pe.from(), pe.to(),
                        "Flagged for review: " + pe.rationale(),
                        SpecStatus.PROPOSED));
                }
            }
        }
    }

    private static Set<String> varNames(NetworkSpec spec) {
        var names = new HashSet<String>();
        for (var v : spec.variables()) names.add(v.name());
        return names;
    }

    private static Map<String, EdgeSpec> edgeMap(NetworkSpec spec) {
        var map = new HashMap<String, EdgeSpec>();
        for (var e : spec.edges()) {
            if (e.status() != SpecStatus.REJECTED) {
                map.put(e.from() + "->" + e.to(), e);
            }
        }
        return map;
    }

    private static List<VariableSpec> mergeVariables(NetworkSpec prior, NetworkSpec learned) {
        var byName = new HashMap<String, VariableSpec>();
        for (var v : prior.variables()) byName.put(v.name(), v);
        for (var v : learned.variables()) byName.putIfAbsent(v.name(), v);
        var merged = new ArrayList<VariableSpec>();
        for (var v : byName.values()) {
            merged.add(new VariableSpec(v.name(), v.outcomes(), v.description(),
                SpecStatus.PROPOSED, new CptSpec.Uniform()));
        }
        return merged;
    }

    private static String resolvePresenceConflict(ConflictPolicy policy) {
        return switch (policy) {
            case TRUST_DATA -> "Removed per TRUST_DATA policy";
            case TRUST_PRIOR -> "Kept per TRUST_PRIOR policy";
            case FLAG_FOR_REVIEW -> "Flagged for review";
        };
    }

    private static String resolveDirectionConflict(ConflictPolicy policy) {
        return switch (policy) {
            case TRUST_DATA -> "Data direction used per TRUST_DATA policy";
            case TRUST_PRIOR -> "Prior direction kept per TRUST_PRIOR policy";
            case FLAG_FOR_REVIEW -> "Direction conflict flagged for review";
        };
    }
}

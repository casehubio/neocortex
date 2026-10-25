package io.quarkmind.inference.bayes;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public record NetworkComposition(
    String name,
    List<NetworkSlot> slots,
    List<NetworkLink> links
) {
    public NetworkComposition {
        slots = List.copyOf(slots);
        links = List.copyOf(links);
        validateSlotNames(slots);
        validateLinkReferences(slots, links);
        validateNoDuplicateLinks(links);
        validateNoConflictingTargets(links);
        validateAcyclic(slots, links);
    }

    public List<String> topologicalOrder() {
        return computeTopologicalOrder(slots, links);
    }

    private static void validateNoDuplicateLinks(List<NetworkLink> links) {
        Set<String> seen = new HashSet<>();
        for (var link : links) {
            for (var entry : link.variableMapping().entrySet()) {
                String key = link.sourceSlot() + "." + entry.getKey() + "->" + link.targetSlot() + "." + entry.getValue();
                if (!seen.add(key)) {
                    throw new IllegalArgumentException("Duplicate link: " + link.sourceSlot()
                        + "." + entry.getKey() + " -> " + link.targetSlot() + "." + entry.getValue());
                }
            }
        }
    }

    private static void validateNoConflictingTargets(List<NetworkLink> links) {
        Set<String> targetVars = new HashSet<>();
        for (var link : links) {
            for (var entry : link.variableMapping().entrySet()) {
                String target = link.targetSlot() + "." + entry.getValue();
                if (!targetVars.add(target)) {
                    throw new IllegalArgumentException(
                        "Multiple links target the same variable: " + link.targetSlot()
                        + "." + entry.getValue() + " — each target variable must have exactly one upstream source");
                }
            }
        }
    }

    public NetworkSlot slotByName(String name) {
        for (var slot : slots) {
            if (slot.name().equals(name)) return slot;
        }
        throw new IllegalArgumentException("Slot not found: " + name);
    }

    private static void validateSlotNames(List<NetworkSlot> slots) {
        Set<String> names = new HashSet<>();
        for (var slot : slots) {
            if (!names.add(slot.name())) {
                throw new IllegalArgumentException("Duplicate slot name: " + slot.name());
            }
        }
    }

    private static void validateLinkReferences(List<NetworkSlot> slots, List<NetworkLink> links) {
        Set<String> names = new HashSet<>();
        for (var slot : slots) names.add(slot.name());
        for (var link : links) {
            if (!names.contains(link.sourceSlot())) {
                throw new IllegalArgumentException("Link references unknown source slot: " + link.sourceSlot());
            }
            if (!names.contains(link.targetSlot())) {
                throw new IllegalArgumentException("Link references unknown target slot: " + link.targetSlot());
            }
        }
    }

    private static void validateAcyclic(List<NetworkSlot> slots, List<NetworkLink> links) {
        List<String> order = computeTopologicalOrder(slots, links);
        if (order.size() != slots.size()) {
            throw new IllegalArgumentException("Composition contains a cycle — not a valid DAG");
        }
    }

    private static List<String> computeTopologicalOrder(List<NetworkSlot> slots, List<NetworkLink> links) {
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> children = new HashMap<>();
        for (var slot : slots) {
            inDegree.put(slot.name(), 0);
            children.put(slot.name(), new ArrayList<>());
        }
        for (var link : links) {
            inDegree.merge(link.targetSlot(), 1, Integer::sum);
            children.get(link.sourceSlot()).add(link.targetSlot());
        }

        Queue<String> queue = new ArrayDeque<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.add(entry.getKey());
        }
        List<String> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String node = queue.poll();
            sorted.add(node);
            for (String child : children.get(node)) {
                int remaining = inDegree.get(child) - 1;
                inDegree.put(child, remaining);
                if (remaining == 0) queue.add(child);
            }
        }
        return List.copyOf(sorted);
    }
}

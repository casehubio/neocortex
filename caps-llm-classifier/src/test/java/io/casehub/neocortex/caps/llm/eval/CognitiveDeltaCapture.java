package io.casehub.neocortex.caps.llm.eval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class CognitiveDeltaCapture {

    private final Map<String, Map<String, String>> previousState = new ConcurrentHashMap<>();
    private final List<DeltaRecord> deltaList = new ArrayList<>();

    record DeltaRecord(int tick, String agentId,
                       Map<String, String> addedSections,
                       Map<String, String> changedSections,
                       List<String> removedSections) {}

    void record(int tick, String agentId, Map<String, String> currentSections) {
        var prev = previousState.get(agentId);
        if (prev != null) {
            var added = new LinkedHashMap<String, String>();
            var changed = new LinkedHashMap<String, String>();
            var removed = new ArrayList<String>();
            for (var e : currentSections.entrySet()) {
                if (!prev.containsKey(e.getKey())) {
                    added.put(e.getKey(), e.getValue());
                } else if (!prev.get(e.getKey()).equals(e.getValue())) {
                    changed.put(e.getKey(), e.getValue());
                }
            }
            for (var key : prev.keySet()) {
                if (!currentSections.containsKey(key)) {
                    removed.add(key);
                }
            }
            if (!added.isEmpty() || !changed.isEmpty() || !removed.isEmpty()) {
                deltaList.add(new DeltaRecord(tick, agentId, added, changed, removed));
            }
        } else if (!currentSections.isEmpty()) {
            deltaList.add(new DeltaRecord(tick, agentId,
                    new LinkedHashMap<>(currentSections), Map.of(), List.of()));
        }
        previousState.put(agentId, new LinkedHashMap<>(currentSections));
    }

    List<DeltaRecord> deltas() { return List.copyOf(deltaList); }

    void clear() {
        previousState.clear();
        deltaList.clear();
    }
}

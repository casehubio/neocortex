package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.cbr.CbrOutcomeData;
import io.casehub.neocortex.memory.cbr.CbrOutcome;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;

import java.util.Map;
import java.util.stream.Collectors;

public class CbrOutcomeProcessor {

    private final CbrRecordStore store;

    public CbrOutcomeProcessor(CbrRecordStore store) {
        this.store = store;
    }

    public void onCbrOutcome(CbrOutcomeData data) {
        CbrOutcome outcome = CbrOutcome.of(
                data.successRate(),
                summarize(data.nodeOutcomes()),
                data.observedAt());
        store.recordOutcome(data.sourceId(), data.tenancyId(), outcome);
    }

    private static String summarize(Map<String, String> nodeOutcomes) {
        if (nodeOutcomes == null || nodeOutcomes.isEmpty()) { return null; }
        return nodeOutcomes.entrySet().stream()
                           .map(e -> e.getKey() + "=" + e.getValue())
                           .collect(Collectors.joining(", "));
    }
}

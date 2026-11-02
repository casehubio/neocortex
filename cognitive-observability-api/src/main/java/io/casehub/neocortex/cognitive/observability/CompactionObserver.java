package io.casehub.neocortex.cognitive.observability;

@FunctionalInterface
public interface CompactionObserver {

    void onCompaction(String agentId, int inputNodes, int outputNodes, long elapsedMs);
}

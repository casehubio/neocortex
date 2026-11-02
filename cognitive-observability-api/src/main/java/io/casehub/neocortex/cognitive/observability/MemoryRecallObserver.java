package io.casehub.neocortex.cognitive.observability;

@FunctionalInterface
public interface MemoryRecallObserver {

    void onRecall(String agentId, String domain, int resultCount);
}

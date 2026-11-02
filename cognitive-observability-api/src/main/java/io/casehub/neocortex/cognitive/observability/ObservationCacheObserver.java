package io.casehub.neocortex.cognitive.observability;

@FunctionalInterface
public interface ObservationCacheObserver {

    void onRefresh(String agentId, int condensedNodes, int recentNodes, int tokenBudget, int actualTokens);
}

package io.casehub.neocortex.cognitive.observability;

public interface DerivationObserver {

    DerivationObserver NOOP = new DerivationObserver() {};

    default void onFacetMapped(String agentId, String facet, int rawScore, double normalized) {}

    default void onFacetModulation(String agentId, String method, String facet,
                                    String field, double before, double after) {}

    default void onStageComplete(String agentId, String method) {}
}

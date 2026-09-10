package io.casehub.neocortex.rag;

import java.util.List;

public interface FederationStrategy {
    List<FederatedResult> federate(FederationQuery query, List<FederatedResult> localResults);
}

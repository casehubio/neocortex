package io.casehub.neocortex.cognitive.index;

import java.util.Objects;

public record DomainPair(String subgraphIdA, String subgraphIdB) {
    public DomainPair {
        Objects.requireNonNull(subgraphIdA);
        Objects.requireNonNull(subgraphIdB);
        if (subgraphIdA.compareTo(subgraphIdB) > 0) {
            String temp = subgraphIdA;
            subgraphIdA = subgraphIdB;
            subgraphIdB = temp;
        }
    }
}
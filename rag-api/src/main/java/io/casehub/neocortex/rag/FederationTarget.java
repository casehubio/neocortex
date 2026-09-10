package io.casehub.neocortex.rag;

import java.util.Objects;

public record FederationTarget(String url, String id, Relationship relationship) {
    public enum Relationship { UPSTREAM, PEER }

    public FederationTarget {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(relationship, "relationship");
    }
}

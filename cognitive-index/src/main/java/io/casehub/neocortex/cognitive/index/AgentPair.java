package io.casehub.neocortex.cognitive.index;

import io.casehub.platform.api.identity.PrincipalId;

import java.util.Objects;

public record AgentPair(PrincipalId a, PrincipalId b) {
    public AgentPair {
        Objects.requireNonNull(a);
        Objects.requireNonNull(b);
        if (a.value().compareTo(b.value()) > 0) {
            PrincipalId temp = a;
            a = b;
            b = temp;
        }
    }

    public static AgentPair of(PrincipalId a, PrincipalId b) {
        return new AgentPair(a, b);
    }
}
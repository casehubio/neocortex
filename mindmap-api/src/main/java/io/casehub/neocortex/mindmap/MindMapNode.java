package io.casehub.neocortex.mindmap;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.thing.Thing;
import io.casehub.platform.api.identity.PrincipalId;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface MindMapNode extends Thing {

    String subgraphType();

    @Override
    default String type() { return subgraphType(); }

    String id();

    String name();

    String subgraphId();

    Confidence confidence();

    String provenance();

    Instant createdAt();

    Instant updatedAt();

    Instant validFrom();

    Instant validUntil();

    Set<String> traits();

    Set<NodeRef> refs();

    Double pleasure();

    Double arousal();

    Double dominance();

    Optional<String> property(String key);

    Map<String, String> properties();

    PrincipalId principalId();

    Set<String> sharedWith();
}

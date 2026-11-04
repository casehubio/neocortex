package io.casehub.neocortex.mindmap.intelligence;

import java.util.Optional;

public interface Personable {
    Optional<String> birthday();

    Optional<String> role();

    Optional<String> email();

    Optional<String> phone();

    Optional<String> gender();

    Optional<String> canonicalFirst();

    Optional<String> canonicalLast();

    Optional<String> canonicalTitlePrefix();

    Optional<String> pronounsSubject();

    Optional<String> pronounsObject();

    Optional<String> pronounsPossessive();

    Optional<String> pronounsReflexive();

    Optional<String> preferredShort();

    Optional<String> preferredLong();
}

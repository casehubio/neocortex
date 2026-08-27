package io.casehub.neocortex.mindmap.intelligence;

import java.util.Optional;

public interface Personable {
    Optional<String> birthday();
    Optional<String> role();
    Optional<String> email();
    Optional<String> phone();
}

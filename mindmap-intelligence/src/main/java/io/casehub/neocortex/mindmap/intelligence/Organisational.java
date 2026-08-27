package io.casehub.neocortex.mindmap.intelligence;

import java.util.Optional;

public interface Organisational {
    Optional<String> industry();
    Optional<String> size();
    Optional<String> location();
}

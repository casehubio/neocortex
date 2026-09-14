package io.casehub.neocortex.mindmap.intelligence;

import java.util.Optional;

public interface Evaluative {
    Optional<String> target();
    Optional<String> stance();
    Optional<String> basis();
}

package io.casehub.neocortex.mindmap.intelligence;

import java.util.Optional;

public interface Belieflike {
    Optional<String> subject();
    Optional<String> status();
    Optional<String> basis();
}

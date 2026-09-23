package io.casehub.neocortex.mindmap.intelligence;

import java.util.Optional;

public interface Goallike {
    Optional<String> description();
    Optional<String> status();
    Optional<String> horizon();
    Optional<String> origin();
    Optional<String> resolution();
    Optional<String> urgency();
    Optional<String> feasibility();
}

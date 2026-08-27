package io.casehub.neocortex.mindmap.intelligence;

import java.util.Optional;

public interface Projectlike {
    Optional<String> status();
    Optional<String> startDate();
    Optional<String> endDate();
    Optional<String> description();
}

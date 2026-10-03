package io.casehub.neocortex.mindmap.intelligence;

import java.util.Optional;

public interface Temporal {
    Optional<String> date();
    Optional<String> duration();
    Optional<String> activityType();
    Optional<String> notes();
}

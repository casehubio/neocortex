package io.casehub.neocortex.mindmap.intelligence;

import java.util.Optional;

public interface Eventlike {
    Optional<String> eventKind();
    Optional<String> eventValence();
    Optional<String> status();
    Optional<String> rrule();
}

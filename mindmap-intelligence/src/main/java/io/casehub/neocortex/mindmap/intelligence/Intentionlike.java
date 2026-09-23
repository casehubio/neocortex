package io.casehub.neocortex.mindmap.intelligence;

import java.util.Optional;

@Deprecated(forRemoval = true)
public interface Intentionlike {
    Optional<String> goal();

    Optional<String> status();

    Optional<String> priority();
}

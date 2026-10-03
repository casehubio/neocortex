package io.casehub.neocortex.mindmap.intelligence;

import java.util.Optional;

public interface Locatable {
    Optional<String> lat();
    Optional<String> lng();
    Optional<String> address();
    Optional<String> category();
    Optional<String> hours();
    Optional<String> phone();
    Optional<String> website();
}

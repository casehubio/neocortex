package io.casehub.neocortex.mindmap.intelligence;

import java.util.Optional;

public interface Goallike {
    Optional<String> description();
    Optional<String> status();
    Optional<String> horizon();
    Optional<String> recognitionSource();
    Optional<String> resolution();
    Optional<String> urgency();
    Optional<String> feasibility();

    Optional<String> contentDomain();

    Optional<String> contentSubdomain();

    Optional<String> motivationOrigin();

    Optional<String> clarity();

    Optional<String> evidenceBasis();

}

package io.casehub.neocortex.mindmap.intelligence;

import java.util.Optional;

public interface Reviewable {
    Optional<String> rating();
    Optional<String> reviewCount();
    Optional<String> priceRange();
}

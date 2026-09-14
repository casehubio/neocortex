package io.casehub.neocortex.mindmap.intelligence;

import java.util.Optional;

public interface Predictive {
    Optional<String> timeframe();
    Optional<String> status();
    Optional<String> basis();
}

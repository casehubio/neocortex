package io.casehub.neocortex.rag;

import java.util.List;
import java.util.Optional;

public interface DocumentQueryAugmenter {
    Optional<List<String>> generateQueries(String title, String body, String path);
}

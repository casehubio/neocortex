package io.casehub.neocortex.cognition.subthought;

import java.util.List;

public record SubThoughtResult(
    List<SubThought> subThoughts,
    String observationHash
) {
    public static final SubThoughtResult EMPTY = new SubThoughtResult(List.of(), "");

    public SubThoughtResult {
        subThoughts = List.copyOf(subThoughts);
    }

    public boolean isEmpty() {
        return subThoughts.isEmpty();
    }
}

package io.casehub.neocortex.memory;

import java.util.Objects;

public record Subject(String type, String id) {

    public Subject {
        Objects.requireNonNull(type, "type required");
        Objects.requireNonNull(id, "id required");
        type = type.strip().toLowerCase();
        id = id.strip();
        if (type.isEmpty()) throw new IllegalArgumentException("type must not be blank");
        if (id.isEmpty()) throw new IllegalArgumentException("id must not be blank");
    }

    public static Subject of(String type, String id) {
        return new Subject(type, id);
    }
}

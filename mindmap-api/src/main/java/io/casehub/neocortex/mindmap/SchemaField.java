package io.casehub.neocortex.mindmap;

import java.util.List;

public record SchemaField(
    String name,
    String type,
    boolean required,
    boolean collection,
    String description,
    List<String> enumValues
) {
    public SchemaField(String name, String type, boolean required) {
        this(name, type, required, false, null, null);
    }
}

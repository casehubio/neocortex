package io.casehub.neocortex.cognitive.observability;

public record FieldChange(String field, Object oldValue, Object newValue) {
}

package io.casehub.neocortex.cognition.memory;

@FunctionalInterface
public interface RetentionConfigSupplier {
    RetentionConfig current();
}

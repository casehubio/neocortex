package io.casehub.neocortex.rag.runtime;

public sealed interface MigrationAction {
    record Compatible() implements MigrationAction {}
    record DimensionMismatch(int actual, int expected) implements MigrationAction {}
    record MissingSparseVectors() implements MigrationAction {}
    record MissingColBert() implements MigrationAction {}
}

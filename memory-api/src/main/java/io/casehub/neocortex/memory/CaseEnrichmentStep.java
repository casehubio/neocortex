package io.casehub.neocortex.memory;

/**
 * SPI for enriching {@link MemoryInput} before storage.
 *
 * <p><b>Progressive routing:</b> {@code appliesTo} and {@code enrich} receive the
 * progressively enriched input — the result of all prior steps (ordered by {@code priority()}),
 * not the original input.
 */
public interface CaseEnrichmentStep {
    boolean appliesTo(MemoryInput input);
    MemoryInput enrich(MemoryInput input);
    default int priority() { return 0; }
    default boolean required() { return false; }
}

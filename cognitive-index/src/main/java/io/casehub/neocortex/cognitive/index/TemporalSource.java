package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import io.casehub.neocortex.mindmap.MindMapNode;

/**
 * The originating store and full source object for a {@link TemporalEntry}.
 * Sealed — exhaustive switch coverage ensures new store variants are a
 * compile error at every consumer.
 *
 * <p>This is part of a derived view over existing store data. The source
 * objects are owned by their originating stores, not by this type.
 */
public sealed interface TemporalSource {
    record FromMindMap(MindMapNode node) implements TemporalSource {}
    record FromMemory(Memory memory) implements TemporalSource {}
    record FromCbr(ScoredCbrCase<?> cbrCase) implements TemporalSource {}
}

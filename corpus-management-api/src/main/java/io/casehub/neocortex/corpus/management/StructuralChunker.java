package io.casehub.neocortex.corpus.management;

import java.util.List;

public interface StructuralChunker {
    List<StructuredChunk> chunk(String positionalMarkdown, int maxChunkSize);
}

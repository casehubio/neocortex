package io.casehub.neocortex.rag;

import java.util.List;

public interface RelevanceEvaluator {
    List<ScoredGrade> evaluateChunks(String query, List<RetrievedChunk> chunks);
}

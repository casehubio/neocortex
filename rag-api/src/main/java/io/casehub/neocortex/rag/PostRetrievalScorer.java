package io.casehub.neocortex.rag;

public interface PostRetrievalScorer {
    double adjust(RetrievedChunk chunk, RetrievalQuery query, ScoringContext context);
}

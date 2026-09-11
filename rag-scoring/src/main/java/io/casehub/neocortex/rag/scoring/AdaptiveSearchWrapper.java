package io.casehub.neocortex.rag.scoring;

import io.casehub.neocortex.rag.AdaptiveFilter;
import io.casehub.neocortex.rag.AdaptiveFilterOptions;
import io.casehub.neocortex.rag.AdaptiveSearchConfig;
import io.casehub.neocortex.rag.CaseRetriever;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.PostRetrievalScorer;
import io.casehub.neocortex.rag.RetrievalQuery;
import io.casehub.neocortex.rag.RetrievedChunk;
import io.casehub.neocortex.rag.ScoringContext;

import java.util.List;

public class AdaptiveSearchWrapper {

    private final CaseRetriever retriever;
    private final List<PostRetrievalScorer> scorers;
    private final AdaptiveSearchConfig config;

    public AdaptiveSearchWrapper(CaseRetriever retriever,
                                  List<PostRetrievalScorer> scorers,
                                  AdaptiveSearchConfig config) {
        this.retriever = retriever;
        this.scorers = List.copyOf(scorers);
        this.config = config;
    }

    public List<RetrievedChunk> search(RetrievalQuery query, CorpusRef corpus,
                                        int maxResults, ScoringContext context) {
        int overfetchLimit = (int) Math.ceil(maxResults * config.overfetchMultiplier());
        List<RetrievedChunk> raw = retriever.retrieve(query, corpus, overfetchLimit);

        List<RetrievedChunk> scored = raw.stream()
            .map(chunk -> {
                double multiplier = 1.0;
                for (var scorer : scorers) {
                    multiplier *= scorer.adjust(chunk, query, context);
                }
                return chunk.withRelevanceScore(chunk.relevanceScore() * multiplier);
            })
            .toList();

        var options = AdaptiveFilterOptions.of(config, RetrievedChunk::relevanceScore);
        return AdaptiveFilter.filter(scored, maxResults, options);
    }
}

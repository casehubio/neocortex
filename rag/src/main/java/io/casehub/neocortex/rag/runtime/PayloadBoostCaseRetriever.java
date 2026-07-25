package io.casehub.neocortex.rag.runtime;

import io.casehub.neocortex.fusion.FusionStrategy;
import io.casehub.neocortex.rag.CaseRetriever;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.PayloadFilter;
import io.casehub.neocortex.rag.RetrievalQuery;
import io.casehub.neocortex.rag.RetrievedChunk;
import io.quarkus.arc.Unremovable;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Decorator
@Priority(60)
@Unremovable
public class PayloadBoostCaseRetriever implements CaseRetriever {

    private final CaseRetriever delegate;
    private final RagConfig config;

    @Inject
    PayloadBoostCaseRetriever(@Delegate @Any CaseRetriever delegate, RagConfig config) {
        this.delegate = delegate;
        this.config = config;
    }



    @Override
    public List<RetrievedChunk> retrieve(RetrievalQuery query, CorpusRef corpus,
                                          int maxResults, PayloadFilter filter) {
        List<RetrievedChunk> results = delegate.retrieve(query, corpus, maxResults, filter);

        double qualityWeight = config.retrieval().weights().quality();
        if (qualityWeight <= 0) return results;
        if (config.retrieval().fusionStrategy() == FusionStrategy.CC) return results;

        Optional<String> fieldOpt = config.retrieval().qualityPayloadField();
        if (fieldOpt.isEmpty()) return results;
        String qualityField = fieldOpt.get();
        double qualityMax = config.retrieval().qualityMax();

        boolean anyBoosted = false;
        List<RetrievedChunk> boosted = new ArrayList<>(results.size());
        for (RetrievedChunk chunk : results) {
            String rawValue = chunk.metadata().get(qualityField);
            if (rawValue == null) {
                boosted.add(chunk);
                continue;
            }
            try {
                double payloadValue = Double.parseDouble(rawValue);
                double normalized = Math.min(payloadValue / qualityMax, 1.0);
                double boostedScore = chunk.relevanceScore() * (1 + normalized * qualityWeight);
                boosted.add(chunk.withRelevanceScore(boostedScore));
                anyBoosted = true;
            } catch (NumberFormatException e) {
                boosted.add(chunk);
            }
        }

        if (!anyBoosted) return results;

        boosted.sort(Comparator.comparingDouble(RetrievedChunk::relevanceScore).reversed());
        return List.copyOf(boosted);
    }
}

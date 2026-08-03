package io.casehub.neocortex.rag.expansion;

import io.casehub.neocortex.fusion.ScoreFusion;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;
import io.casehub.neocortex.rag.CaseRetriever;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.PayloadFilter;
import io.casehub.neocortex.rag.QueryExpander;
import io.casehub.neocortex.rag.RetrievalQuery;
import io.casehub.neocortex.rag.RetrievedChunk;
import io.quarkus.arc.Unremovable;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Decorator
@Priority(200)
@Unremovable
@IfBuildProperty(name = "casehub.rag.expansion.enabled", stringValue = "true")
public class QueryExpandingCaseRetriever implements CaseRetriever {

    private static final Logger LOG = Logger.getLogger(QueryExpandingCaseRetriever.class.getName());

    private final CaseRetriever delegate;
    private final QueryExpander expander;
    @Inject
    @Any
    Instance<EmbeddingModel> embeddingModel;

    @Inject
    Instance<MeterRegistry> meterRegistry;

    @Inject
    ExpansionConfig config;


    @Inject
    public QueryExpandingCaseRetriever(@Delegate @Any CaseRetriever delegate,
                                       QueryExpander expander) {
        this.delegate = delegate;
        this.expander = expander;
        LOG.fine(() -> "Query expansion decorator active, expander: " + expander.getClass().getSimpleName());
    }

    @Override
    public List<RetrievedChunk> retrieve(RetrievalQuery query, CorpusRef corpus,
                                          int maxResults, PayloadFilter filter) {
        LOG.fine(() -> "Intercepting retrieve for corpus " + corpus + ", query: " + query.text());

        List<RetrievalQuery> expanded;
        try {
            expanded = expander.expand(query);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Query expansion failed, using original query", e);
            expanded = List.of(query);
        }

        if (expanded.isEmpty()) {
            expanded = List.of(query);
        }

        // Ensure original query is in the expanded set
        if (!expanded.contains(query)) {
            var withOriginal = new ArrayList<RetrievalQuery>(expanded.size() + 1);
            withOriginal.add(query);
            withOriginal.addAll(expanded);
            expanded = withOriginal;
        }

        expanded = filterByDrift(query, expanded);

        // Single-query fast path: skip RRF fusion
        if (expanded.size() == 1) {
            return delegate.retrieve(expanded.get(0), corpus, maxResults, filter);
        }

        // Multi-query path: fan out retrievals and merge via RRF
        var resultSets = new ArrayList<List<RetrievedChunk>>(expanded.size());
        for (var expandedQuery : expanded) {
            var results = delegate.retrieve(expandedQuery, corpus, maxResults, filter);
            resultSets.add(results);
        }

        List<ScoreFusion.ScoredLeg<RetrievedChunk>> legs = resultSets.stream()
            .map(rs -> new ScoreFusion.ScoredLeg<>(rs, RetrievedChunk::relevanceScore, 1.0))
            .toList();
        return ScoreFusion.rrf(legs, RetrievedChunk::fusionKey, maxResults, 60)
            .stream().map(f -> f.item().withRelevanceScore(f.score())).toList();
    }

    private List<RetrievalQuery> filterByDrift(RetrievalQuery original, List<RetrievalQuery> expanded) {
        if (config == null || !config.drift().enabled()
            || embeddingModel == null || !embeddingModel.isResolvable()) {
            return expanded;
        }

        try {
            List<RetrievalQuery> expandedOnly = expanded.stream()
                                                        .filter(q -> q.expandedText() != null)
                                                        .toList();

            if (expandedOnly.isEmpty()) {
                return expanded;
            }

            List<TextSegment> segments = new ArrayList<>(expandedOnly.size() + 1);
            segments.add(TextSegment.from(original.text()));
            for (var q : expandedOnly) {
                segments.add(TextSegment.from(q.searchText()));
            }

            List<Embedding> embeddings        = embeddingModel.get().embedAll(segments).content();
            Embedding       originalEmbedding = embeddings.get(0);

            String                      mode  = config.mode().orElse("unknown");
            ExpansionConfig.DriftConfig drift = config.drift();

            if (meterRegistry != null && meterRegistry.isResolvable()) {
                meterRegistry.get().counter("casehub.rag.expansion.total", "mode", mode).increment();
            }

            Set<RetrievalQuery> toDrop = new HashSet<>();
            for (int i = 0; i < expandedOnly.size(); i++) {
                double    similarity = CosineSimilarity.between(originalEmbedding, embeddings.get(i + 1));
                final int idx        = i;

                LOG.fine(() -> String.format("Drift: similarity=%.4f threshold=%.4f query='%s'",
                                             similarity, drift.threshold(), expandedOnly.get(idx).searchText()));

                if (meterRegistry != null && meterRegistry.isResolvable()) {
                    meterRegistry.get().summary("casehub.rag.expansion.drift", "mode", mode)
                                 .record(similarity);
                }

                if (similarity < drift.threshold()) {
                    LOG.warning(() -> String.format(
                            "Expansion drift detected: similarity=%.4f below threshold=%.4f for query='%s'",
                            similarity, drift.threshold(), expandedOnly.get(idx).searchText()));

                    if (drift.action() == DriftAction.DROP) {
                        toDrop.add(expandedOnly.get(idx));
                        if (meterRegistry != null && meterRegistry.isResolvable()) {
                            meterRegistry.get().counter("casehub.rag.expansion.drift.fallback", "mode", mode)
                                         .increment();
                        }
                    }
                }
            }

            if (toDrop.isEmpty()) {
                return expanded;
            }

            return expanded.stream().filter(q -> !toDrop.contains(q)).toList();

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Drift detection failed, using unfiltered expansion list", e);
            return expanded;
        }
    }

}

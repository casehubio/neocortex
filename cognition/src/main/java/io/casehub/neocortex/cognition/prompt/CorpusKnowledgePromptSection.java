package io.casehub.neocortex.cognition.prompt;

import io.casehub.neocortex.cognitive.index.CognitiveMemoryRetriever;
import io.casehub.neocortex.cognitive.index.ModulationContext;
import io.casehub.neocortex.corpus.management.CorpusQuery;
import io.casehub.neocortex.corpus.management.CorpusSearchResult;
import io.casehub.neocortex.rag.CorpusRef;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CorpusKnowledgePromptSection implements CognitionPromptRenderer {

    private static final int MAX_RESULTS = 10;
    private static final int MAX_PER_CORPUS = 5;

    private final CognitiveMemoryRetriever retriever;
    private final String queryText;

    public CorpusKnowledgePromptSection(CognitiveMemoryRetriever retriever,
                                         String queryText) {
        this.retriever = retriever;
        this.queryText = queryText;
    }

    @Override
    public @Nullable String render(CognitionRenderContext context) {
        var modCtx = new ModulationContext(context.agentId(), context.tenantId(),
            null, null, null);
        List<CorpusSearchResult> results = retriever.queryCorpora(
            CorpusQuery.of(queryText, MAX_RESULTS), context.tenantId(), modCtx);
        if (results.isEmpty()) return null;

        Map<CorpusRef, List<CorpusSearchResult>> grouped = groupByCorpus(results);

        var sb = new StringBuilder("## Relevant Knowledge\n");
        for (var entry : grouped.entrySet()) {
            sb.append("\nFrom ").append(entry.getKey().corpusName()).append(":\n");
            int count = 0;
            for (var result : entry.getValue()) {
                if (count >= MAX_PER_CORPUS) break;
                String title = result.entry().stringField("title");
                String id = result.entry().id();
                sb.append("- ").append(title != null ? title : "(untitled)")
                    .append(" [").append(id).append("]\n");
                count++;
            }
        }
        return sb.toString().strip();
    }

    private Map<CorpusRef, List<CorpusSearchResult>> groupByCorpus(
            List<CorpusSearchResult> results) {
        var grouped = new LinkedHashMap<CorpusRef, List<CorpusSearchResult>>();
        for (var result : results) {
            grouped.computeIfAbsent(result.sourceCorpus(), k -> new java.util.ArrayList<>())
                .add(result);
        }
        return grouped;
    }
}

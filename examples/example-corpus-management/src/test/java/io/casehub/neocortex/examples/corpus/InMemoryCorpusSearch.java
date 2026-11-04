package io.casehub.neocortex.examples.corpus;

import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.CorpusQuery;
import io.casehub.neocortex.corpus.management.CorpusSearch;
import io.casehub.neocortex.corpus.management.CorpusSearchResult;
import io.casehub.neocortex.rag.CorpusRef;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryCorpusSearch implements CorpusSearch {

    private final Map<CorpusRef, List<CorpusEntry>> entries = new ConcurrentHashMap<>();

    public void index(CorpusRef corpus, CorpusEntry entry) {
        entries.computeIfAbsent(corpus, k -> new ArrayList<>()).add(entry);
    }

    public void remove(CorpusRef corpus, String entryId) {
        var list = entries.get(corpus);
        if (list != null) {
            list.removeIf(e -> e.id().equals(entryId));
        }
    }

    @Override
    public List<CorpusSearchResult> search(CorpusRef corpus, CorpusQuery query) {
        var corpusEntries = entries.getOrDefault(corpus, List.of());
        var results = new ArrayList<CorpusSearchResult>();
        String queryText = query.text().toLowerCase();

        for (var entry : corpusEntries) {
            double score = 0.0;
            String body = entry.body() != null ? entry.body().toLowerCase() : "";
            String title = entry.stringField("title");
            if (title != null) title = title.toLowerCase();
            else title = "";

            if (body.contains(queryText) || title.contains(queryText)) {
                score = 1.0;
            } else {
                for (String word : queryText.split("\\s+")) {
                    if (body.contains(word) || title.contains(word)) {
                        score += 0.5;
                    }
                }
            }

            if (score > 0) {
                results.add(new CorpusSearchResult(entry, score, corpus, 0, Instant.now()));
            }
        }

        results.sort((a, b) -> Double.compare(b.score(), a.score()));
        return results.stream().limit(query.maxResults()).toList();
    }
}

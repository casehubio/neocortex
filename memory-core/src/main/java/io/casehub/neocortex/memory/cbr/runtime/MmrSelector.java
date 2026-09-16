package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;

import java.util.ArrayList;
import java.util.List;

public final class MmrSelector {

    private MmrSelector() {}

    @FunctionalInterface
    public interface PairwiseSimilarity<C extends CbrCase> {
        double similarity(ScoredCbrCase<C> a, ScoredCbrCase<C> b);
    }

    public static <C extends CbrCase> List<ScoredCbrCase<C>> select(
            List<ScoredCbrCase<C>> candidates,
            int topK,
            double lambda,
            PairwiseSimilarity<C> similarity) {
        if (candidates.size() <= topK) {
            var sorted = new ArrayList<>(candidates);
            sorted.sort((a, b) -> Double.compare(b.score(), a.score()));
            return List.copyOf(sorted);
        }

        var remaining = new ArrayList<>(candidates);
        var selected = new ArrayList<ScoredCbrCase<C>>(topK);

        remaining.sort((a, b) -> Double.compare(b.score(), a.score()));
        selected.add(remaining.remove(0));

        while (selected.size() < topK && !remaining.isEmpty()) {
            double bestMmr = Double.NEGATIVE_INFINITY;
            int bestIdx = 0;

            for (int i = 0; i < remaining.size(); i++) {
                ScoredCbrCase<C> candidate = remaining.get(i);
                double relevance = candidate.score();
                double maxSim = 0.0;
                for (ScoredCbrCase<C> sel : selected) {
                    maxSim = Math.max(maxSim,
                        similarity.similarity(candidate, sel));
                }
                double mmr = lambda * relevance - (1.0 - lambda) * maxSim;
                if (mmr > bestMmr) {
                    bestMmr = mmr;
                    bestIdx = i;
                }
            }
            selected.add(remaining.remove(bestIdx));
        }

        selected.sort((a, b) -> Double.compare(b.score(), a.score()));
        return List.copyOf(selected);
    }
}

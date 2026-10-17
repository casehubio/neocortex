package io.casehub.neocortex.cognition.salience;

import io.casehub.neocortex.cognition.appraisal.PerceivedSituation;
import io.casehub.neocortex.cognition.appraisal.SalienceContext;
import io.casehub.neocortex.cognition.appraisal.SalienceStrategy;
import io.casehub.neocortex.memory.Memory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Klinger Current Concerns salience strategy.
 *
 * Matches observation text against active concern descriptions via keyword overlap.
 * Concerns with non-zero overlap appear in PerceivedSituation.salience, enabling
 * downstream appraisal and drive foregrounding to respond to situationally relevant
 * motivational states.
 *
 * Literature: Klinger (1971, 2004) — active concerns modulate attentional bias
 * toward concern-relevant stimuli.
 */
public class ConcernBasedSalienceStrategy implements SalienceStrategy {

    private static final int MIN_TOKEN_LENGTH = 3;

    @Override
    public PerceivedSituation perceive(SalienceContext context) {
        var salience = new HashMap<String, Double>();
        var obsWords = tokenize(context.observation());

        if (!obsWords.isEmpty()) {
            for (Memory concern : context.activeConcerns()) {
                String text = concern.text();
                if (text == null || text.isBlank()) continue;
                double score = keywordOverlap(obsWords, tokenize(text));
                if (score > 0) {
                    salience.put(concern.memoryId(), score);
                }
            }
        }

        return new PerceivedSituation(context.observation(), salience);
    }

    // Asymmetric overlap normalized by larger set — Jaccard variant
    private static double keywordOverlap(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        long shared = a.stream().filter(b::contains).count();
        return (double) shared / Math.max(a.size(), b.size());
    }

    private static Set<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase().split("\\W+"))
                     .filter(w -> w.length() > MIN_TOKEN_LENGTH)
                     .collect(Collectors.toSet());
    }
}

package io.casehub.neocortex.cognition.appraisal;

import java.util.Set;

public class ImplicationCheck implements SecCheck {

    private static final Set<String> POSITIVE = Set.of(
            "success", "achieved", "found", "helped", "solved",
            "gained", "won", "progressed", "improved", "completed");
    private static final Set<String> NEGATIVE = Set.of(
            "failed", "lost", "blocked", "threatened", "broken",
            "damaged", "missing", "danger", "destroyed", "collapsed");

    @Override
    public SecResult evaluate(AppraisalContext context) {
        String lower = context.situation().narrative().toLowerCase();

        long pos = POSITIVE.stream().filter(lower::contains).count();
        long neg = NEGATIVE.stream().filter(lower::contains).count();
        long total = pos + neg;

        double conduciveness = total > 0 ? (double) (pos - neg) / total : 0.0;

        return SecResult.of("implication", SecDimensions.CONDUCIVENESS, conduciveness);
    }
}

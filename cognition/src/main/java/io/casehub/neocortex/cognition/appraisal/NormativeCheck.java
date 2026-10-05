package io.casehub.neocortex.cognition.appraisal;

import java.util.Set;

public class NormativeCheck implements SecCheck {

    private static final Set<String> VIOLATIONS = Set.of(
            "wrong", "unfair", "unjust", "violation", "betrayal", "dishonest", "corrupt");
    private static final Set<String> CONFORMITY = Set.of(
            "fair", "just", "honest", "proper", "right", "ethical", "principled");

    @Override
    public SecResult evaluate(AppraisalContext context) {
        String lower = context.situation().narrative().toLowerCase();

        long violations = VIOLATIONS.stream().filter(lower::contains).count();
        long conformity = CONFORMITY.stream().filter(lower::contains).count();

        double selfStrictness = context.weights() != null
                ? context.weights().selfStandardsStrictness() : 1.0;
        double otherStrictness = context.weights() != null
                ? context.weights().otherStandardsStrictness() : 1.0;

        double internal = Math.max(0, Math.min(1.0,
                1.0 - violations * 0.3 * selfStrictness + conformity * 0.2));
        double external = Math.max(0, Math.min(1.0,
                1.0 - violations * 0.3 * otherStrictness + conformity * 0.2));

        return SecResult.of("normative",
                SecDimensions.INTERNAL_STANDARDS, internal,
                SecDimensions.EXTERNAL_STANDARDS, external);
    }
}

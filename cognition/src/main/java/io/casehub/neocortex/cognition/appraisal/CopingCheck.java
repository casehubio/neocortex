package io.casehub.neocortex.cognition.appraisal;

import java.util.Set;

public class CopingCheck implements SecCheck {

    private static final Set<String> AGENCY = Set.of(
            "can", "able", "possible", "option", "choose", "decide", "control");
    private static final Set<String> HELPLESSNESS = Set.of(
            "impossible", "trapped", "stuck", "unable", "forced", "inevitable", "overwhelming");

    @Override
    public SecResult evaluate(AppraisalContext context) {
        String lower = context.situation().narrative().toLowerCase();

        long agency = AGENCY.stream().filter(lower::contains).count();
        long helpless = HELPLESSNESS.stream().filter(lower::contains).count();
        long total = agency + helpless;

        double controllability = total > 0 ? (double) agency / total : 0.5;

        long activeDrives = context.drives().stream()
                .filter(d -> d.intensity() > 0.3).count();
        double adjustability = Math.min(1.0, activeDrives / 4.0);

        return SecResult.of("coping",
                SecDimensions.CONTROLLABILITY, controllability,
                SecDimensions.ADJUSTABILITY, adjustability);
    }
}

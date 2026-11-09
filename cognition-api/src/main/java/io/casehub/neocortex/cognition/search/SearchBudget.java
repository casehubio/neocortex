package io.casehub.neocortex.cognition.search;

import java.time.Instant;
import java.util.Objects;

/**
 * 4-dimensional search envelope shaping LLM exploration style.
 * <p>
 * Parameters are orthogonal (Guilford, 1967): breadth (fluency),
 * laterality (flexibility), depth (elaboration), convergence
 * (MVT patch-leaving, Charnov, 1976; Pirolli & Card, 1999).
 * <p>
 * Personality sets the static baseline via
 * {@link io.casehub.neocortex.cognitive.SearchBudgetConfig}. Dynamic
 * modulation from drives, mood, appraisal, and goal urgency shapes
 * the transient budget at tick time.
 *
 * @param breadth             [0,1] parallel angles to pursue (Guilford fluency;
 *                            DeYoung 2015 Openness/Intellect)
 * @param laterality          [0,1] distance from original topic (De Bono 1970
 *                            lateral thinking; DeYoung 2015 "leaky cognitive filtering")
 * @param depth               [0,1] thread follow-through (Guilford elaboration;
 *                            Conscientiousness → systematic search)
 * @param convergence         [0,1] synthesis pressure; 0 = fixation/rumination
 *                            (Pirolli & Card 1999 MVT; Davis & Nolen-Hoeksema 1998)
 * @param source              dominant motivation shaping search character
 * @param extremeActivation   true when extreme-intensity features are active
 *                            (intensity exceeds personality-specific peak)
 * @param capacityConstrained true when any capacity ceiling materially caps a parameter
 *                            (gap > 0.05 threshold)
 * @param depthGap            magnitude by which motivation exceeds relational complexity ceiling
 * @param breadthGap          magnitude by which motivation exceeds working memory span ceiling
 * @param lateralityGap       magnitude by which motivation exceeds associative reach ceiling
 * @param evaluatedAt         when this budget was computed
 */
public record SearchBudget(
        double breadth,
        double laterality,
        double depth,
        double convergence,
        SearchMotivation source,
        boolean extremeActivation,
        boolean capacityConstrained,
        double depthGap,
        double breadthGap,
        double lateralityGap,
        Instant evaluatedAt) {

    public SearchBudget {
        Objects.requireNonNull(source, "source required");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt required");
        validateUnit("breadth", breadth);
        validateUnit("laterality", laterality);
        validateUnit("depth", depth);
        validateUnit("convergence", convergence);
    }

    private static void validateUnit(String name, double value) {
        if (value < 0.0 || value > 1.0) {throw new IllegalArgumentException(name + " must be in [0,1], got " + value);}
    }
}

package io.casehub.neocortex.memory.cbr;

import java.util.Map;

/**
 * Computes CBR similarity scores using per-field local similarity functions
 * and configurable per-field weights.
 *
 * <p>Local similarity functions are derived from field type:
 * <ul>
 *   <li>{@link FeatureField.Categorical} — exact match (1.0 or 0.0)</li>
 *   <li>{@link FeatureField.Numeric} — linear decay: {@code 1.0 - |query - case| / range},
 *       with {@link NumericRange} support (1.0 inside range, linear decay outside)</li>
 *   <li>{@link FeatureField.Text} — exact match (1.0 or 0.0)</li>
 * </ul>
 *
 * <p>Pure Java, Tier 1 — zero external dependencies.
 */
public final class CbrSimilarityScorer {

    private CbrSimilarityScorer() {}

    /**
     * Compute weighted similarity between query features and case features.
     *
     * @param queryFeatures the query's feature values
     * @param caseFeatures  the stored case's feature values
     * @param weights       per-field weights (default 1.0 for unspecified fields)
     * @param schema        the feature schema (null → return 1.0 for backward compat)
     * @return similarity in [0, 1]
     */
    public static double score(Map<String, Object> queryFeatures,
                                Map<String, Object> caseFeatures,
                                Map<String, Double> weights,
                                CbrFeatureSchema schema) {
        if (queryFeatures.isEmpty()) return 1.0;
        if (schema == null) return 1.0;

        double weightedSum = 0.0;
        double totalWeight = 0.0;

        for (Map.Entry<String, Object> entry : queryFeatures.entrySet()) {
            FeatureField field = findField(schema, entry.getKey());
            if (field == null) continue;

            double weight = weights.getOrDefault(entry.getKey(), 1.0);
            Object caseValue = caseFeatures.get(entry.getKey());
            double localSim = caseValue == null ? 0.0
                : localSimilarity(field, entry.getValue(), caseValue);

            weightedSum += weight * localSim;
            totalWeight += weight;
        }

        return totalWeight > 0 ? weightedSum / totalWeight : 1.0;
    }

    /**
     * Compute composite score blending feature similarity and vector similarity.
     *
     * @param featureScore feature-based similarity in [0, 1]
     * @param vectorScore  vector-based similarity (e.g. cosine)
     * @param vectorWeight β: balance between vector (β) and feature (1-β) similarity
     * @return composite score
     */
    public static double compositeScore(double featureScore, double vectorScore,
                                         double vectorWeight) {
        return vectorWeight * vectorScore + (1.0 - vectorWeight) * featureScore;
    }

    private static double localSimilarity(FeatureField field, Object queryVal, Object caseVal) {
        if (field instanceof FeatureField.Numeric n) {
            return numericSimilarity(n, queryVal, caseVal);
        }
        // Categorical and Text: exact match
        return queryVal.equals(caseVal) ? 1.0 : 0.0;
    }

    private static double numericSimilarity(FeatureField.Numeric field,
                                             Object queryVal, Object caseVal) {
        double range = field.max() - field.min();
        if (range <= 0) return queryVal.equals(caseVal) ? 1.0 : 0.0;

        double caseNum = ((Number) caseVal).doubleValue();

        if (queryVal instanceof NumericRange nr) {
            if (caseNum >= nr.min() && caseNum <= nr.max()) return 1.0;
            double dist = caseNum < nr.min() ? nr.min() - caseNum : caseNum - nr.max();
            return Math.max(0.0, 1.0 - dist / range);
        }

        double queryNum = ((Number) queryVal).doubleValue();
        return Math.max(0.0, 1.0 - Math.abs(queryNum - caseNum) / range);
    }

    private static FeatureField findField(CbrFeatureSchema schema, String name) {
        for (FeatureField f : schema.fields()) {
            if (f.name().equals(name)) return f;
        }
        return null;
    }
}

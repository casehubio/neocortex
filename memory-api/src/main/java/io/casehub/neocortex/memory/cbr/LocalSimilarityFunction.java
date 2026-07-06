package io.casehub.neocortex.memory.cbr;

/**
 * Computes local similarity between a query feature value and a case feature value.
 *
 * <p>Implementations return a score in [0, 1], where:
 * <ul>
 *   <li>1.0 — perfect match (most similar)</li>
 *   <li>0.0 — no match (completely dissimilar)</li>
 * </ul>
 *
 * <p>Pure Java, Tier 1 — zero external dependencies.
 */
@FunctionalInterface
public interface LocalSimilarityFunction {

    /**
     * Compute similarity between query value and case value.
     *
     * @param queryValue the query's feature value
     * @param caseValue  the stored case's feature value
     * @return similarity in [0, 1]
     */
    double compute(Object queryValue, Object caseValue);

    /**
     * Exact match similarity function: 1.0 if equal, 0.0 otherwise.
     */
    LocalSimilarityFunction EXACT_MATCH = (q, c) -> q.equals(c) ? 1.0 : 0.0;
}

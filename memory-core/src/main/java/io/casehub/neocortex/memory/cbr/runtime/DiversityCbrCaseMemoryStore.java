package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.cbr.CaseTypeScope;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrFeatureSchema;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.CbrSimilarityScorer;
import io.casehub.neocortex.memory.cbr.DelegatingCbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.FeatureField;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class DiversityCbrCaseMemoryStore extends DelegatingCbrCaseMemoryStore {

    private static final Logger LOG = Logger.getLogger(
        DiversityCbrCaseMemoryStore.class.getName());

    private final double lambda;
    private final double overFetchFactor;
    private final boolean enabled;
    private final ConcurrentHashMap<String, CbrFeatureSchema> schemaCache =
        new ConcurrentHashMap<>();

    public DiversityCbrCaseMemoryStore(CbrCaseMemoryStore delegate,
                                        double lambda,
                                        double overFetchFactor,
                                        boolean enabled) {
        super(delegate);
        this.lambda = lambda;
        this.overFetchFactor = overFetchFactor;
        this.enabled = enabled;
    }

    @Override
    public void registerSchema(CbrFeatureSchema schema) {
        schemaCache.put(schema.caseType(), schema);
        super.registerSchema(schema);
    }

    @Override
    public <C extends CbrCase> List<ScoredCbrCase<C>> retrieveSimilar(
            CbrQuery query, Class<C> caseType) {
        if (!enabled) return super.retrieveSimilar(query, caseType);

        String ct = switch (query.caseTypeScope()) {
            case CaseTypeScope.Specific s -> s.caseType();
            case CaseTypeScope.AllInDomain a -> null;
        };

        CbrFeatureSchema schema = ct != null ? schemaCache.get(ct) : null;
        if (schema == null) {
            if (ct != null) {
                LOG.fine("No cached schema for caseType '" + ct
                    + "' — diversity skipped");
            }
            return super.retrieveSimilar(query, caseType);
        }

        int originalTopK = query.topK();
        int inflatedTopK = (int) Math.ceil(originalTopK * overFetchFactor);
        CbrQuery inflated = query.withTopK(inflatedTopK);

        List<ScoredCbrCase<C>> candidates = super.retrieveSimilar(
            inflated, caseType);
        if (candidates.size() <= originalTopK) return candidates;

        Map<String, Double> uniformWeights = buildUniformWeights(schema);

        return MmrSelector.select(candidates, originalTopK, lambda,
            (a, b) -> CbrSimilarityScorer.score(
                a.cbrCase().features(),
                b.cbrCase().features(),
                uniformWeights, schema));
    }

    private Map<String, Double> buildUniformWeights(CbrFeatureSchema schema) {
        var weights = new HashMap<String, Double>();
        for (FeatureField field : schema.fields()) {
            weights.put(field.name(), 1.0);
        }
        return Map.copyOf(weights);
    }
}

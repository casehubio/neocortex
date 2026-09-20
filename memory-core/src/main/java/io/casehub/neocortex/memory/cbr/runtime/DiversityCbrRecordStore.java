package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.cbr.CaseTypeScope;
import io.casehub.neocortex.memory.cbr.CbrMatch;
import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.CbrRecordSchema;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.CbrSimilarityScorer;
import io.casehub.neocortex.memory.cbr.DelegatingCbrRecordStore;
import io.casehub.neocortex.memory.cbr.FeatureField;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class DiversityCbrRecordStore extends DelegatingCbrRecordStore {

    private static final Logger LOG = Logger.getLogger(
        DiversityCbrRecordStore.class.getName());

    private final double lambda;
    private final double overFetchFactor;
    private final boolean                                    enabled;
    private final ConcurrentHashMap<String, CbrRecordSchema> schemaCache =
        new ConcurrentHashMap<>();

    public DiversityCbrRecordStore(CbrRecordStore delegate,
                                   double lambda,
                                   double overFetchFactor,
                                   boolean enabled) {
        super(delegate);
        this.lambda = lambda;
        this.overFetchFactor = overFetchFactor;
        this.enabled = enabled;
    }

    @Override
    public void registerSchema(CbrRecordSchema schema) {
        schemaCache.put(schema.caseType(), schema);
        super.registerSchema(schema);
    }

    @Override
    public <C extends CbrRecord> List<CbrMatch<C>> retrieveSimilar(
            CbrQuery query, Class<C> caseType) {
        if (!enabled) return super.retrieveSimilar(query, caseType);

        String ct = switch (query.caseTypeScope()) {
            case CaseTypeScope.Specific s -> s.caseType();
            case CaseTypeScope.AllInDomain a -> null;
        };

        CbrRecordSchema schema = ct != null ? schemaCache.get(ct) : null;
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

        List<CbrMatch<C>> candidates = super.retrieveSimilar(
            inflated, caseType);
        if (candidates.size() <= originalTopK) return candidates;

        Map<String, Double> uniformWeights = buildUniformWeights(schema);

        return MmrSelector.select(candidates, originalTopK, lambda,
            (a, b) -> CbrSimilarityScorer.score(
                a.cbrRecord().features(),
                b.cbrRecord().features(),
                uniformWeights, schema));
    }

    private Map<String, Double> buildUniformWeights(CbrRecordSchema schema) {
        var weights = new HashMap<String, Double>();
        for (FeatureField field : schema.fields()) {
            weights.put(field.name(), 1.0);
        }
        return Map.copyOf(weights);
    }
}

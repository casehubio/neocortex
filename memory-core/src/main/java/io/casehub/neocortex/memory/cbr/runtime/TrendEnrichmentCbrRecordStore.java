package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.CaseTypeScope;
import io.casehub.neocortex.memory.cbr.CbrRecordSchema;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.DelegatingCbrRecordStore;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.CbrMatch;
import io.casehub.neocortex.memory.cbr.TrendAnalyzer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TrendEnrichmentCbrRecordStore extends DelegatingCbrRecordStore {

    private final ConcurrentHashMap<String, CbrRecordSchema> expandedSchemas = new ConcurrentHashMap<>();

    public TrendEnrichmentCbrRecordStore(CbrRecordStore delegate) {
        super(delegate);
    }

    @Override
    public void registerSchema(CbrRecordSchema schema) {
        CbrRecordSchema expanded = TrendAnalyzer.expandSchema(schema);
        expandedSchemas.put(schema.caseType(), expanded);
        delegate.registerSchema(expanded);
    }

    @Override
    public String store(CbrRecord cbrRecord, String caseType, String entityId,
                        MemoryDomain domain, String tenantId, String caseId, io.casehub.platform.api.path.Path scope) {
        CbrRecordSchema schema = expandedSchemas.get(caseType);
        if (schema != null) {
            Map<String, FeatureValue> enriched = TrendAnalyzer.enrichFeatures(cbrRecord.features(), schema);
            if (enriched != cbrRecord.features()) {
                cbrRecord = cbrRecord.withFeatures(enriched);
            }
        }
        return delegate.store(cbrRecord, caseType, entityId, domain, tenantId, caseId, scope);
    }

    @Override
    public <C extends CbrRecord> List<CbrMatch<C>> retrieveSimilar(
            CbrQuery query, Class<C> caseClass) {
        CbrRecordSchema schema = switch (query.caseTypeScope()) {
            case CaseTypeScope.Specific s -> expandedSchemas.get(s.caseType());
            case CaseTypeScope.AllInDomain a -> null;
        };
        if (schema != null) {
            Map<String, FeatureValue> enriched = TrendAnalyzer.enrichFeatures(query.features(), schema);
            if (enriched != query.features()) {
                query = query.withFeatures(enriched);
            }
        }
        return delegate.retrieveSimilar(query, caseClass);
    }
}

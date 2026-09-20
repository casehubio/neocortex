package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrMatch;
import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.CbrRecordSchema;
import io.casehub.neocortex.memory.cbr.CbrOutcome;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.CbrRetentionPolicy;
import io.casehub.neocortex.memory.cbr.CbrFeatureRecord;
import io.casehub.neocortex.memory.cbr.ScopeDecay;
import io.casehub.platform.api.path.Path;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class ScopeDecayCbrRecordStoreTest {

    private static final MemoryDomain CBR = new MemoryDomain("cbr");
    private static final String TENANT = "t1";

    private ScopeDecayCbrRecordStore decorator(List<CbrMatch<CbrFeatureRecord>> results) {
        return new ScopeDecayCbrRecordStore(new StubStore(results));
    }

    private CbrMatch<CbrFeatureRecord> scored(double score, Path scope) {
        var c = new CbrFeatureRecord("p", "s", null, null, Map.of(), null, null);
        return new CbrMatch<>(c, "id", "test-type", score, false, Map.of(), Instant.now(), scope, null);
    }

    @Test void nullScopeDecay_passThrough() {
        var results = List.of(scored(0.8, Path.root()), scored(0.6, Path.of("trial")));
        var q = CbrQuery.of(TENANT, CBR, Path.of("trial", "site"), "t", Map.of(), 10);
        var out = decorator(results).retrieveSimilar(q, CbrFeatureRecord.class);
        assertThat(out).hasSize(2);
        assertThat(out.get(0).score()).isEqualTo(0.8);
    }

    @Test void exponentialDecay_exactScopeUnchanged() {
        var results = List.of(scored(0.8, Path.of("trial", "site")));
        var q = CbrQuery.of(TENANT, CBR, Path.of("trial", "site"), "t", Map.of(), 10)
                .withScopeDecay(new ScopeDecay.Exponential(0.5));
        var out = decorator(results).retrieveSimilar(q, CbrFeatureRecord.class);
        assertThat(out.get(0).score()).isEqualTo(0.8);
    }

    @Test void exponentialDecay_parentHalved() {
        var results = List.of(scored(0.8, Path.of("trial")));
        var q = CbrQuery.of(TENANT, CBR, Path.of("trial", "site"), "t", Map.of(), 10)
                .withScopeDecay(new ScopeDecay.Exponential(0.5));
        var out = decorator(results).retrieveSimilar(q, CbrFeatureRecord.class);
        assertThat(out.get(0).score()).isEqualTo(0.4);
    }

    @Test void exponentialDecay_grandparentQuartered() {
        var results = List.of(scored(1.0, Path.root()));
        var q = CbrQuery.of(TENANT, CBR, Path.of("trial", "site"), "t", Map.of(), 10)
                .withScopeDecay(new ScopeDecay.Exponential(0.5));
        var out = decorator(results).retrieveSimilar(q, CbrFeatureRecord.class);
        assertThat(out.get(0).score()).isEqualTo(0.25);
    }

    @Test void belowMinSimilarity_filteredOut() {
        var results = List.of(scored(0.3, Path.root()));
        var q = CbrQuery.of(TENANT, CBR, Path.of("trial", "site"), "t", Map.of(), 10)
                .withMinSimilarity(0.2).withScopeDecay(new ScopeDecay.Exponential(0.5));
        var out = decorator(results).retrieveSimilar(q, CbrFeatureRecord.class);
        assertThat(out).isEmpty();
    }

    @Test void resortAfterDecay_orderChanges() {
        var exactLow = scored(0.5, Path.of("trial", "site"));
        var ancestorHigh = scored(0.9, Path.root());
        var results = List.of(ancestorHigh, exactLow);
        var q = CbrQuery.of(TENANT, CBR, Path.of("trial", "site"), "t", Map.of(), 10)
                .withScopeDecay(new ScopeDecay.Exponential(0.5));
        var out = decorator(results).retrieveSimilar(q, CbrFeatureRecord.class);
        assertThat(out.get(0).score()).isEqualTo(0.5);
        assertThat(out.get(1).score()).isCloseTo(0.225, offset(0.001));
    }

    private static class StubStore implements CbrRecordStore {
        private final List<? extends CbrMatch<?>> results;
        StubStore(List<? extends CbrMatch<?>> results)                                                                   {this.results = results; }
        @Override public void registerSchema(CbrRecordSchema s)                                                          {}
        @Override public String store(CbrRecord c, String ct, String e, MemoryDomain d, String t, String ci, Path scope) { return ""; }
        @Override @SuppressWarnings("unchecked")
        public <C extends CbrRecord> List<CbrMatch<C>> retrieveSimilar(CbrQuery q, Class<C> t) { return (List<CbrMatch<C>>) (List<?>) results; }
        @Override public Integer erase(EraseRequest r) { return 0; }
        @Override public Integer eraseEntity(String e, String t) { return 0; }
        @Override public Integer eraseByScope(Path scope, String t) { return 0; }
        @Override public void recordOutcome(String ci, String t, CbrOutcome o) {}
        @Override public Integer purge(CbrRetentionPolicy p) { return 0; }
        @Override public boolean supersede(String ci, String t, String s, String r) { return false; }
        @Override public boolean reinstate(String ci, String t) { return false; }
        @Override public io.casehub.neocortex.memory.cbr.SupersessionStatus getSupersessionStatus(String caseId, String tenantId) { return io.casehub.neocortex.memory.cbr.SupersessionStatus.NOT_SUPERSEDED; }
        @Override public java.util.List<io.casehub.neocortex.memory.cbr.SupersessionStatus> findSupersededCases(String tenantId, MemoryDomain domain) { return java.util.List.of(); }
        @Override public java.util.List<String> findCaseIds(String t, MemoryDomain d, String ct, java.util.Map<String, io.casehub.neocortex.memory.cbr.CbrFilter> f) { return java.util.List.of(); }
        @Override public int supersedeMatching(String t, MemoryDomain d, String ct, java.util.Map<String, io.casehub.neocortex.memory.cbr.CbrFilter> f, String r) { return 0; }
        @Override public int supersedeAll(java.util.Collection<String> ids, String t, String r) { return 0; }
        @Override public int reinstateMatching(String t, MemoryDomain d, String ct, java.util.Map<String, io.casehub.neocortex.memory.cbr.CbrFilter> f) { return 0; }
        @Override public int reinstateAll(java.util.Collection<String> ids, String t) { return 0; }
    }
}

package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrFilter;
import io.casehub.neocortex.memory.cbr.CbrMatch;
import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.CbrRecordSchema;
import io.casehub.neocortex.memory.cbr.CbrOutcome;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.OutcomeWeightingFunction;
import io.casehub.neocortex.memory.cbr.CbrFeatureRecord;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class OutcomeWeightingCbrRecordStoreTest {

    private final DefaultOutcomeWeightingFunction fn = new DefaultOutcomeWeightingFunction(0.3);

    @Test void successfulCaseRanksHigher() {
        var highConf = testCase("high", 0.9);
        var lowConf = testCase("low", 0.5);
        var delegate = stubDelegate(List.of(
                new CbrMatch<>(lowConf, "c1", "test-type", 0.8, false, Map.of(), null, io.casehub.platform.api.path.Path.root(), null),
                new CbrMatch<>(highConf, "c2", "test-type", 0.8, false, Map.of(), null, io.casehub.platform.api.path.Path.root(), null)));
        var decorator = new OutcomeWeightingCbrRecordStore(delegate, fn);
        var results = decorator.retrieveSimilar(testQuery(), CbrFeatureRecord.class);
        assertThat(results.getFirst().cbrRecord().confidence().value()).isEqualTo(0.9);
    }

    @Test void nullConfidence_treatedAsOne() {
        var noOutcome = testCase("none", null);
        var delegate = stubDelegate(List.of(
                new CbrMatch<>(noOutcome, "c1", "test-type", 0.8, false, Map.of(), null, io.casehub.platform.api.path.Path.root(), null)));
        var decorator = new OutcomeWeightingCbrRecordStore(delegate, fn);
        var results = decorator.retrieveSimilar(testQuery(), CbrFeatureRecord.class);
        assertThat(results.getFirst().score()).isCloseTo(0.8, within(1e-9));
    }

    @Test void allConfidenceOne_orderUnchanged() {
        var a = testCase("a", 1.0);
        var b = testCase("b", 1.0);
        var delegate = stubDelegate(List.of(
                new CbrMatch<>(a, "c1", "test-type", 0.9, false, Map.of(), null, io.casehub.platform.api.path.Path.root(), null),
                new CbrMatch<>(b, "c2", "test-type", 0.7, false, Map.of(), null, io.casehub.platform.api.path.Path.root(), null)));
        var decorator = new OutcomeWeightingCbrRecordStore(delegate, fn);
        var results = decorator.retrieveSimilar(testQuery(), CbrFeatureRecord.class);
        assertThat(results.get(0).cbrRecord().problem()).isEqualTo("a");
        assertThat(results.get(1).cbrRecord().problem()).isEqualTo("b");
    }

    @Test void influenceZero_noEffect() {
        var lowConf = testCase("low", 0.1);
        var noEffect = new DefaultOutcomeWeightingFunction(0.0);
        var delegate = stubDelegate(List.of(
                new CbrMatch<>(lowConf, "c1", "test-type", 0.8, false, Map.of(), null, io.casehub.platform.api.path.Path.root(), null)));
        var decorator = new OutcomeWeightingCbrRecordStore(delegate, noEffect);
        var results = decorator.retrieveSimilar(testQuery(), CbrFeatureRecord.class);
        assertThat(results.getFirst().score()).isCloseTo(0.8, within(1e-9));
    }

    @Test void preservesCaseIdAndRerankedFlag() {
        var c = testCase("p", 0.8);
        var delegate = stubDelegate(List.of(
                new CbrMatch<>(c, "case-42", "test-type", 0.9, true, Map.of("f", 0.95), null, io.casehub.platform.api.path.Path.root(), null)));
        var decorator = new OutcomeWeightingCbrRecordStore(delegate, fn);
        var result = decorator.retrieveSimilar(testQuery(), CbrFeatureRecord.class).getFirst();
        assertThat(result.caseId()).isEqualTo("case-42");
        assertThat(result.reranked()).isTrue();
        assertThat(result.featureSimilarities()).containsEntry("f", 0.95);
    }

    @Test void emptyResults_noError() {
        var delegate = stubDelegate(List.of());
        var decorator = new OutcomeWeightingCbrRecordStore(delegate, fn);
        var results = decorator.retrieveSimilar(testQuery(), CbrFeatureRecord.class);
        assertThat(results).isEmpty();
    }

    @Test void customWeightingFunction_applied() {
        var c = testCase("p", 0.8);
        var delegate = stubDelegate(List.of(
                new CbrMatch<>(c, "c1", "test-type", 0.9, false, Map.of(), null, io.casehub.platform.api.path.Path.root(), null)));
        OutcomeWeightingFunction custom = (sim, conf) -> sim * conf;
        var decorator = new OutcomeWeightingCbrRecordStore(delegate, custom);
        var results = decorator.retrieveSimilar(testQuery(), CbrFeatureRecord.class);
        assertThat(results.getFirst().score()).isCloseTo(0.9 * 0.8, within(1e-9));
    }

    @Test void resortsAfterWeighting() {
        var highSim = testCase("highSim", 0.3);
        var lowSim = testCase("lowSim", 1.0);
        var delegate = stubDelegate(List.of(
                new CbrMatch<>(highSim, "c1", "test-type", 0.9, false, Map.of(), null, io.casehub.platform.api.path.Path.root(), null),
                new CbrMatch<>(lowSim, "c2", "test-type", 0.5, false, Map.of(), null, io.casehub.platform.api.path.Path.root(), null)));
        var decorator = new OutcomeWeightingCbrRecordStore(delegate, fn);
        var results = decorator.retrieveSimilar(testQuery(), CbrFeatureRecord.class);
        assertThat(results.get(0).score()).isGreaterThanOrEqualTo(results.get(1).score());
    }

    private CbrFeatureRecord testCase(String problem, Double confidence) {
        return new CbrFeatureRecord(problem, "sol", null,
                confidence != null ? Confidence.unknown(confidence) : null, Map.of(), null, null);
    }

    private CbrQuery testQuery() {
        return CbrQuery.of("t1", new MemoryDomain("cbr"), io.casehub.platform.api.path.Path.root(), "default", Map.of(), 10);
    }

    @SuppressWarnings("unchecked")
    private CbrRecordStore stubDelegate(List<CbrMatch<CbrFeatureRecord>> results) {
        return new CbrRecordStore() {
            @Override public void registerSchema(CbrRecordSchema schema)                                                                                    {}
            @Override public String store(CbrRecord c, String t, String e, MemoryDomain d, String tid, String cid, io.casehub.platform.api.path.Path scope) { return "id"; }
            @Override public <C extends CbrRecord> List<CbrMatch<C>> retrieveSimilar(CbrQuery q, Class<C> cl) {
                return (List<CbrMatch<C>>) (List<?>) results;
            }
            @Override public int erase(EraseRequest r) { return 0; }
            @Override public int eraseEntity(String e, String t) { return 0; }
            @Override public int eraseByScope(io.casehub.platform.api.path.Path scope, String t) { return 0; }
            @Override public void recordOutcome(String c, CbrOutcome o, String t)                {}
            @Override public int purge(io.casehub.neocortex.memory.cbr.CbrRetentionPolicy p)     { return 0; }
            @Override public boolean supersede(String c, String s, String r, String t)       { return false; }
            @Override public boolean reinstate(String c, String t)                           { return false; }

        @Override public io.casehub.neocortex.memory.cbr.SupersessionStatus getSupersessionStatus(String caseId, String tenantId)                                                                { return io.casehub.neocortex.memory.cbr.SupersessionStatus.NOT_SUPERSEDED; }
        @Override public java.util.List<io.casehub.neocortex.memory.cbr.SupersessionStatus> findSupersededCases(MemoryDomain domain, String tenantId) { return java.util.List.of(); }
        @Override public java.util.List<String> findCaseIds(MemoryDomain d, String ct, Map<String, CbrFilter> f, String t)                            { return java.util.List.of(); }
        @Override public int supersedeMatching(MemoryDomain d, String ct, Map<String, CbrFilter> f, String r, String t)                               { return 0; }
        @Override public int supersedeAll(Collection<String> ids, String r, String t)                                   { return 0; }
        @Override public int reinstateMatching(MemoryDomain d, String ct, Map<String, CbrFilter> filters, String t)     { return 0; }
        @Override public int reinstateAll(java.util.Collection<String> ids, String t)                               { return 0; }

        };
    }
}

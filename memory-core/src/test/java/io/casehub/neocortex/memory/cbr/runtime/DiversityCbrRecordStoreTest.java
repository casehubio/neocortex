package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.cbr.CbrMatch;
import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.CbrRecordSchema;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.DelegatingCbrRecordStore;
import io.casehub.neocortex.memory.cbr.FeatureField;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.MemoryDomain;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiversityCbrRecordStoreTest {

    record TestRecord(String problem, Map<String, FeatureValue> features) implements CbrRecord {
        @Override public String recordType() { return "test"; }
        @Override public String problem()    { return problem; }
        @Override public String solution()                                            { return null; }
        @Override public String outcome()                                             { return null; }
        @Override public Confidence confidence()                                      { return null; }
        @Override public Map<String, FeatureValue> features()                         { return features; }
        @Override public CbrRecord withOutcome(String outcome, Confidence confidence) { return this; }
    }

    private static final MemoryDomain DOMAIN = new MemoryDomain("test");

    private StubCbrStore stubWithResults(List<CbrMatch<TestRecord>> results) {
        return new StubCbrStore(results);
    }

    @SuppressWarnings("unchecked")
    static class StubCbrStore extends DelegatingCbrRecordStore {
        private final List<? extends CbrMatch<?>> results;
        int lastRequestedTopK = -1;

        StubCbrStore(List<? extends CbrMatch<?>> results) {
            super(new NoOpDelegate());
            this.results = results;
        }

        @Override
        public <C extends CbrRecord> List<CbrMatch<C>> retrieveSimilar(
                CbrQuery query, Class<C> caseType) {
            lastRequestedTopK = query.topK();
            return (List<CbrMatch<C>>) (List<?>) results.stream()
                                                        .limit(query.topK())
                                                        .toList();
        }

        @Override
        public void registerSchema(CbrRecordSchema schema) {}
    }

    static class NoOpDelegate extends DelegatingCbrRecordStore {
        NoOpDelegate()                                                                                    { super(null); }
        @Override public <C extends CbrRecord> List<CbrMatch<C>> retrieveSimilar(CbrQuery q, Class<C> ct) { return List.of(); }
        @Override public void registerSchema(CbrRecordSchema schema)                                      {}
    }

    private List<CbrMatch<TestRecord>> makeCases(int count, String... colors) {
        var list = new ArrayList<CbrMatch<TestRecord>>();
        for (int i = 0; i < count; i++) {
            String color = colors[i % colors.length];
            double score = 1.0 - (i * 0.05);
            list.add(new CbrMatch<>(
                new TestRecord("case " + i, Map.of("color", FeatureValue.string(color))),
                "c" + i, "test-type", score));
        }
        return list;
    }

    private CbrQuery query(int topK) {
        return CbrQuery.of("t1", DOMAIN, io.casehub.platform.api.path.Path.root(),
            "test-type", Map.of("color", FeatureValue.string("red")), topK);
    }

    @Test
    void disabled_passesThrough() {
        var stub = stubWithResults(makeCases(10, "red", "red", "red"));
        var decorator = new DiversityCbrRecordStore(stub, 0.7, 1.5, false);
        decorator.registerSchema(CbrRecordSchema.of("test-type",
                                                    new FeatureField.Categorical("color")));

        var results = decorator.retrieveSimilar(query(3), CbrRecord.class);
        assertThat(results).hasSize(3);
        assertThat(stub.lastRequestedTopK).isEqualTo(3);
    }

    @Test
    void enabled_overFetchesFromDelegate() {
        var stub = stubWithResults(makeCases(10, "red", "blue", "green"));
        var decorator = new DiversityCbrRecordStore(stub, 0.7, 1.5, true);
        decorator.registerSchema(CbrRecordSchema.of("test-type",
                                                    new FeatureField.Categorical("color")));

        decorator.retrieveSimilar(query(4), CbrRecord.class);
        assertThat(stub.lastRequestedTopK).isEqualTo(6);
    }

    @Test
    void enabled_returnsRequestedTopK() {
        var stub = stubWithResults(makeCases(10, "red", "blue", "green"));
        var decorator = new DiversityCbrRecordStore(stub, 0.7, 1.5, true);
        decorator.registerSchema(CbrRecordSchema.of("test-type",
                                                    new FeatureField.Categorical("color")));

        var results = decorator.retrieveSimilar(query(3), CbrRecord.class);
        assertThat(results).hasSize(3);
    }

    @Test
    void noSchema_passesThrough() {
        var stub = stubWithResults(makeCases(5, "red"));
        var decorator = new DiversityCbrRecordStore(stub, 0.7, 1.5, true);

        var results = decorator.retrieveSimilar(query(3), CbrRecord.class);
        assertThat(results).hasSize(3);
        assertThat(stub.lastRequestedTopK).isEqualTo(3);
    }

    @Test
    void preservesCaseFields() {
        var stub = stubWithResults(makeCases(6, "red", "blue"));
        var decorator = new DiversityCbrRecordStore(stub, 0.7, 1.5, true);
        decorator.registerSchema(CbrRecordSchema.of("test-type",
                                                    new FeatureField.Categorical("color")));

        var results = decorator.retrieveSimilar(query(3), CbrRecord.class);
        for (var r : results) {
            assertThat(r.caseId()).isNotNull();
            assertThat(r.caseType()).isEqualTo("test-type");
        }
    }

    @Test
    void diversityPromotesDifferentColors() {
        var cases = List.of(
            new CbrMatch<>(new TestRecord("a", Map.of("color", FeatureValue.string("red"))), "c0", "test-type", 0.95),
            new CbrMatch<>(new TestRecord("b", Map.of("color", FeatureValue.string("red"))), "c1", "test-type", 0.90),
            new CbrMatch<>(new TestRecord("c", Map.of("color", FeatureValue.string("red"))), "c2", "test-type", 0.85),
            new CbrMatch<>(new TestRecord("d", Map.of("color", FeatureValue.string("blue"))), "c3", "test-type", 0.80),
            new CbrMatch<>(new TestRecord("e", Map.of("color", FeatureValue.string("green"))), "c4", "test-type", 0.75)
        );
        var stub = stubWithResults(cases);
        var decorator = new DiversityCbrRecordStore(stub, 0.5, 2.0, true);
        decorator.registerSchema(CbrRecordSchema.of("test-type",
                                                    new FeatureField.Categorical("color")));

        var results = decorator.retrieveSimilar(query(3), CbrRecord.class);
        var selectedColors = results.stream()
            .map(r -> r.cbrRecord().features().get("color"))
            .map(v -> ((FeatureValue.StringVal) v).value())
            .toList();

        assertThat(selectedColors).contains("red");
        assertThat(selectedColors).doesNotHaveDuplicates();
    }
}

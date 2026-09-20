package io.casehub.neocortex.memory.cbr;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CbrRecordFilterMatcherTest {

    @Test
    void emptyFiltersMatchesAnything() {
        var cbrRecord = new CbrFeatureRecord("problem", "solution", null, null,
                                           Map.of("color", new FeatureValue.StringVal("red")), null, null);
        assertTrue(CbrRecordFilterMatcher.matchesFilters(cbrRecord, Map.of(), null));
    }

    @Test
    void containsFilterMatchesStringVal() {
        var cbrRecord = new CbrFeatureRecord("problem", "solution", null, null,
                                           Map.of("color", new FeatureValue.StringVal("red")), null, null);
        var schema = CbrRecordSchema.of("test", new FeatureField.Categorical("color"));
        assertTrue(CbrRecordFilterMatcher.matchesFilters(cbrRecord,
                                                         Map.of("color", CbrFilter.contains("red")), schema));
        assertFalse(CbrRecordFilterMatcher.matchesFilters(cbrRecord,
                                                          Map.of("color", CbrFilter.contains("blue")), schema));
    }

    @Test
    void containsFilterMatchesStringListVal() {
        var cbrRecord = new CbrFeatureRecord("problem", "solution", null, null,
                                           Map.of("tags", new FeatureValue.StringListVal(List.of("a", "b", "c"))),
                                           null, null);
        var schema = CbrRecordSchema.of("test", new FeatureField.CategoricalList("tags"));
        assertTrue(CbrRecordFilterMatcher.matchesFilters(cbrRecord,
                                                         Map.of("tags", CbrFilter.contains("b")), schema));
        assertFalse(CbrRecordFilterMatcher.matchesFilters(cbrRecord,
                                                          Map.of("tags", CbrFilter.contains("z")), schema));
    }

    @Test
    void containsAllFilterMatches() {
        var cbrRecord = new CbrFeatureRecord("problem", "solution", null, null,
                                           Map.of("tags", new FeatureValue.StringListVal(List.of("a", "b", "c"))),
                                           null, null);
        var schema = CbrRecordSchema.of("test", new FeatureField.CategoricalList("tags"));
        assertTrue(CbrRecordFilterMatcher.matchesFilters(cbrRecord,
                                                         Map.of("tags", CbrFilter.containsAll(List.of("a", "b"))), schema));
        assertFalse(CbrRecordFilterMatcher.matchesFilters(cbrRecord,
                                                          Map.of("tags", CbrFilter.containsAll(List.of("a", "z"))), schema));
    }

    @Test
    void notContainsFilterMatches() {
        var cbrRecord = new CbrFeatureRecord("problem", "solution", null, null,
                                           Map.of("tags", new FeatureValue.StringListVal(List.of("a", "b"))),
                                           null, null);
        var schema = CbrRecordSchema.of("test", new FeatureField.CategoricalList("tags"));
        assertTrue(CbrRecordFilterMatcher.matchesFilters(cbrRecord,
                                                         Map.of("tags", CbrFilter.notContains("z")), schema));
        assertFalse(CbrRecordFilterMatcher.matchesFilters(cbrRecord,
                                                          Map.of("tags", CbrFilter.notContains("a")), schema));
    }

    @Test
    void notContainsAnyFilterMatches() {
        var cbrRecord = new CbrFeatureRecord("problem", "solution", null, null,
                                           Map.of("tags", new FeatureValue.StringListVal(List.of("a", "b"))),
                                           null, null);
        var schema = CbrRecordSchema.of("test", new FeatureField.CategoricalList("tags"));
        assertTrue(CbrRecordFilterMatcher.matchesFilters(cbrRecord,
                                                         Map.of("tags", CbrFilter.notContainsAny(List.of("x", "y"))), schema));
        assertFalse(CbrRecordFilterMatcher.matchesFilters(cbrRecord,
                                                          Map.of("tags", CbrFilter.notContainsAny(List.of("a", "x"))), schema));
    }

    @Test
    void containsAnyFilterMatches() {
        var cbrRecord = new CbrFeatureRecord("problem", "solution", null, null,
                                           Map.of("tags", new FeatureValue.StringListVal(List.of("a", "b"))),
                                           null, null);
        var schema = CbrRecordSchema.of("test", new FeatureField.CategoricalList("tags"));
        assertTrue(CbrRecordFilterMatcher.matchesFilters(cbrRecord,
                                                         Map.of("tags", CbrFilter.containsAny(List.of("a", "z"))), schema));
        assertFalse(CbrRecordFilterMatcher.matchesFilters(cbrRecord,
                                                          Map.of("tags", CbrFilter.containsAny(List.of("x", "y"))), schema));
    }

    @Test
    void allOfFilterCombines() {
        var cbrRecord = new CbrFeatureRecord("problem", "solution", null, null,
                                           Map.of("tags", new FeatureValue.StringListVal(List.of("a", "b", "c"))),
                                           null, null);
        var schema = CbrRecordSchema.of("test", new FeatureField.CategoricalList("tags"));
        var filter = CbrFilter.allOf(CbrFilter.contains("a"), CbrFilter.notContains("z"));
        assertTrue(CbrRecordFilterMatcher.matchesFilters(cbrRecord,
                                                         Map.of("tags", filter), schema));
        var failing = CbrFilter.allOf(CbrFilter.contains("a"), CbrFilter.notContains("b"));
        assertFalse(CbrRecordFilterMatcher.matchesFilters(cbrRecord,
                                                          Map.of("tags", failing), schema));
    }

    @Test
    void missingFeatureReturnsFalse() {
        var cbrRecord = new CbrFeatureRecord("problem", "solution", null, null,
                                           Map.of(), null, null);
        var schema = CbrRecordSchema.of("test", new FeatureField.Categorical("color"));
        assertFalse(CbrRecordFilterMatcher.matchesFilters(cbrRecord,
                                                          Map.of("color", CbrFilter.contains("red")), schema));
    }

    @Test
    void containsRangeFilterMatches() {
        var cbrRecord = new CbrFeatureRecord("problem", "solution", null, null,
                                           Map.of("scores", new FeatureValue.NumberListVal(List.of(1.0, 5.0, 10.0))),
                                           null, null);
        var schema = CbrRecordSchema.of("test", new FeatureField.NumericList("scores", 0.0, 100.0));
        assertTrue(CbrRecordFilterMatcher.matchesFilters(cbrRecord,
                                                         Map.of("scores", CbrFilter.containsRange(new NumericRange(4.0, 6.0))), schema));
        assertFalse(CbrRecordFilterMatcher.matchesFilters(cbrRecord,
                                                          Map.of("scores", CbrFilter.containsRange(new NumericRange(20.0, 30.0))), schema));
    }
}

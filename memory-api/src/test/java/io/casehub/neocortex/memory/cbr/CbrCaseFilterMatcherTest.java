package io.casehub.neocortex.memory.cbr;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CbrCaseFilterMatcherTest {

    @Test
    void emptyFiltersMatchesAnything() {
        var cbrCase = new FeatureVectorCbrCase("problem", "solution", null, null,
                Map.of("color", new FeatureValue.StringVal("red")), null, null);
        assertTrue(CbrCaseFilterMatcher.matchesFilters(cbrCase, Map.of(), null));
    }

    @Test
    void containsFilterMatchesStringVal() {
        var cbrCase = new FeatureVectorCbrCase("problem", "solution", null, null,
                Map.of("color", new FeatureValue.StringVal("red")), null, null);
        var schema = CbrFeatureSchema.of("test", new FeatureField.Categorical("color"));
        assertTrue(CbrCaseFilterMatcher.matchesFilters(cbrCase,
                Map.of("color", CbrFilter.contains("red")), schema));
        assertFalse(CbrCaseFilterMatcher.matchesFilters(cbrCase,
                Map.of("color", CbrFilter.contains("blue")), schema));
    }

    @Test
    void containsFilterMatchesStringListVal() {
        var cbrCase = new FeatureVectorCbrCase("problem", "solution", null, null,
                Map.of("tags", new FeatureValue.StringListVal(List.of("a", "b", "c"))),
                null, null);
        var schema = CbrFeatureSchema.of("test", new FeatureField.CategoricalList("tags"));
        assertTrue(CbrCaseFilterMatcher.matchesFilters(cbrCase,
                Map.of("tags", CbrFilter.contains("b")), schema));
        assertFalse(CbrCaseFilterMatcher.matchesFilters(cbrCase,
                Map.of("tags", CbrFilter.contains("z")), schema));
    }

    @Test
    void containsAllFilterMatches() {
        var cbrCase = new FeatureVectorCbrCase("problem", "solution", null, null,
                Map.of("tags", new FeatureValue.StringListVal(List.of("a", "b", "c"))),
                null, null);
        var schema = CbrFeatureSchema.of("test", new FeatureField.CategoricalList("tags"));
        assertTrue(CbrCaseFilterMatcher.matchesFilters(cbrCase,
                Map.of("tags", CbrFilter.containsAll(List.of("a", "b"))), schema));
        assertFalse(CbrCaseFilterMatcher.matchesFilters(cbrCase,
                Map.of("tags", CbrFilter.containsAll(List.of("a", "z"))), schema));
    }

    @Test
    void notContainsFilterMatches() {
        var cbrCase = new FeatureVectorCbrCase("problem", "solution", null, null,
                Map.of("tags", new FeatureValue.StringListVal(List.of("a", "b"))),
                null, null);
        var schema = CbrFeatureSchema.of("test", new FeatureField.CategoricalList("tags"));
        assertTrue(CbrCaseFilterMatcher.matchesFilters(cbrCase,
                Map.of("tags", CbrFilter.notContains("z")), schema));
        assertFalse(CbrCaseFilterMatcher.matchesFilters(cbrCase,
                Map.of("tags", CbrFilter.notContains("a")), schema));
    }

    @Test
    void notContainsAnyFilterMatches() {
        var cbrCase = new FeatureVectorCbrCase("problem", "solution", null, null,
                Map.of("tags", new FeatureValue.StringListVal(List.of("a", "b"))),
                null, null);
        var schema = CbrFeatureSchema.of("test", new FeatureField.CategoricalList("tags"));
        assertTrue(CbrCaseFilterMatcher.matchesFilters(cbrCase,
                Map.of("tags", CbrFilter.notContainsAny(List.of("x", "y"))), schema));
        assertFalse(CbrCaseFilterMatcher.matchesFilters(cbrCase,
                Map.of("tags", CbrFilter.notContainsAny(List.of("a", "x"))), schema));
    }

    @Test
    void containsAnyFilterMatches() {
        var cbrCase = new FeatureVectorCbrCase("problem", "solution", null, null,
                Map.of("tags", new FeatureValue.StringListVal(List.of("a", "b"))),
                null, null);
        var schema = CbrFeatureSchema.of("test", new FeatureField.CategoricalList("tags"));
        assertTrue(CbrCaseFilterMatcher.matchesFilters(cbrCase,
                Map.of("tags", CbrFilter.containsAny(List.of("a", "z"))), schema));
        assertFalse(CbrCaseFilterMatcher.matchesFilters(cbrCase,
                Map.of("tags", CbrFilter.containsAny(List.of("x", "y"))), schema));
    }

    @Test
    void allOfFilterCombines() {
        var cbrCase = new FeatureVectorCbrCase("problem", "solution", null, null,
                Map.of("tags", new FeatureValue.StringListVal(List.of("a", "b", "c"))),
                null, null);
        var schema = CbrFeatureSchema.of("test", new FeatureField.CategoricalList("tags"));
        var filter = CbrFilter.allOf(CbrFilter.contains("a"), CbrFilter.notContains("z"));
        assertTrue(CbrCaseFilterMatcher.matchesFilters(cbrCase,
                Map.of("tags", filter), schema));
        var failing = CbrFilter.allOf(CbrFilter.contains("a"), CbrFilter.notContains("b"));
        assertFalse(CbrCaseFilterMatcher.matchesFilters(cbrCase,
                Map.of("tags", failing), schema));
    }

    @Test
    void missingFeatureReturnsFalse() {
        var cbrCase = new FeatureVectorCbrCase("problem", "solution", null, null,
                Map.of(), null, null);
        var schema = CbrFeatureSchema.of("test", new FeatureField.Categorical("color"));
        assertFalse(CbrCaseFilterMatcher.matchesFilters(cbrCase,
                Map.of("color", CbrFilter.contains("red")), schema));
    }

    @Test
    void containsRangeFilterMatches() {
        var cbrCase = new FeatureVectorCbrCase("problem", "solution", null, null,
                Map.of("scores", new FeatureValue.NumberListVal(List.of(1.0, 5.0, 10.0))),
                null, null);
        var schema = CbrFeatureSchema.of("test", new FeatureField.NumericList("scores", 0.0, 100.0));
        assertTrue(CbrCaseFilterMatcher.matchesFilters(cbrCase,
                Map.of("scores", CbrFilter.containsRange(new NumericRange(4.0, 6.0))), schema));
        assertFalse(CbrCaseFilterMatcher.matchesFilters(cbrCase,
                Map.of("scores", CbrFilter.containsRange(new NumericRange(20.0, 30.0))), schema));
    }
}

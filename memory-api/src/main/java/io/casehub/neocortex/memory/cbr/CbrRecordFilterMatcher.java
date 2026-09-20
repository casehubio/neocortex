package io.casehub.neocortex.memory.cbr;

import java.util.Map;

public final class CbrRecordFilterMatcher {

    private CbrRecordFilterMatcher() {}

    public static boolean matchesFilters(CbrRecord storedCase, Map<String, CbrFilter> filters,
                                         CbrRecordSchema schema) {
        if (filters.isEmpty()) return true;
        for (var entry : filters.entrySet()) {
            FeatureValue storedValue = storedCase.features().get(entry.getKey());
            if (storedValue == null) return false;
            FeatureField field = CbrRecordValidator.findField(schema, entry.getKey());
            if (!matchesSingleFilter(storedValue, entry.getValue(), field)) return false;
        }
        return true;
    }

    private static boolean matchesSingleFilter(FeatureValue storedValue, CbrFilter filter,
                                                FeatureField field) {
        return switch (filter) {
            case CbrFilter.Contains c ->
                    (storedValue instanceof FeatureValue.StringListVal sl && sl.values().contains(c.value()))
                    || (storedValue instanceof FeatureValue.StringVal sv && sv.value().equals(c.value()));
            case CbrFilter.ContainsAll ca ->
                    storedValue instanceof FeatureValue.StringListVal sl && sl.values().containsAll(ca.values());
            case CbrFilter.ContainsAny ca ->
                    (storedValue instanceof FeatureValue.StringListVal sl && ca.values().stream().anyMatch(sl.values()::contains))
                    || (storedValue instanceof FeatureValue.StringVal sv && ca.values().contains(sv.value()));
            case CbrFilter.NotContains nc ->
                    storedValue instanceof FeatureValue.StringListVal sl && !sl.values().contains(nc.value());
            case CbrFilter.NotContainsAny nca ->
                    storedValue instanceof FeatureValue.StringListVal sl && nca.values().stream().noneMatch(sl.values()::contains);
            case CbrFilter.ContainsRange cr ->
                    storedValue instanceof FeatureValue.NumberListVal nl && nl.values().stream()
                            .anyMatch(n -> n >= cr.range().min() && n <= cr.range().max());
            case CbrFilter.HasMatch hm -> matchesHasMatch(storedValue, hm, field);
            case CbrFilter.AllOf allOf -> {
                for (CbrFilter inner : allOf.filters()) {
                    if (!matchesSingleFilter(storedValue, inner, field)) yield false;
                }
                yield true;
            }
        };
    }

    private static boolean matchesHasMatch(FeatureValue storedValue, CbrFilter.HasMatch hm,
                                            FeatureField field) {
        if (field instanceof FeatureField.ObjectList) {
            if (!(storedValue instanceof FeatureValue.StructListVal sl)) return false;
            return sl.items().stream().anyMatch(elem -> allSubFieldsMatch(elem, hm.subFields()));
        } else {
            if (!(storedValue instanceof FeatureValue.StructVal sv)) return false;
            return allSubFieldsMatch(sv.fields(), hm.subFields());
        }
    }

    private static boolean allSubFieldsMatch(Map<String, FeatureValue> stored,
                                              Map<String, FeatureValue> subFields) {
        for (var sub : subFields.entrySet()) {
            FeatureValue storedVal = stored.get(sub.getKey());
            if (storedVal == null) return false;
            FeatureValue queryVal = sub.getValue();
            if (queryVal instanceof FeatureValue.RangeVal range) {
                if (!(storedVal instanceof FeatureValue.NumberVal num)) return false;
                double d = num.value();
                if (d < range.min() || d > range.max()) return false;
            } else if (queryVal instanceof FeatureValue.NumberVal qn) {
                if (!(storedVal instanceof FeatureValue.NumberVal sn)) return false;
                if (Double.compare(qn.value(), sn.value()) != 0) return false;
            } else {
                if (!queryVal.equals(storedVal)) return false;
            }
        }
        return true;
    }
}

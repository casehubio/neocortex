package io.casehub.neocortex.cognition.subthought;

import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.experience.SubThoughtAttributeKeys;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SubThoughts {

    private SubThoughts() {}

    public static List<SubThought> extract(Memory memory) {
        var attrs = memory.attributes();
        var countStr = attrs.get(SubThoughtAttributeKeys.COUNT);
        if (countStr == null) return List.of();
        int count;
        try { count = Integer.parseInt(countStr); } catch (NumberFormatException e) { return List.of(); }
        var result = new ArrayList<SubThought>(count);
        for (int i = 0; i < count; i++) {
            var type = attrs.get(SubThoughtAttributeKeys.type(i));
            var text = attrs.get(SubThoughtAttributeKeys.text(i));
            if (type == null || text == null) continue;
            var entity = attrs.get(SubThoughtAttributeKeys.entity(i));
            var confStr = attrs.get(SubThoughtAttributeKeys.confidence(i));
            double confidence;
            try { confidence = confStr != null ? Double.parseDouble(confStr) : 0.8; }
            catch (NumberFormatException e) { confidence = 0.8; }
            result.add(new SubThought(type, text, entity, confidence, SubThought.Source.ASYNC));
        }
        return List.copyOf(result);
    }

    public static List<SubThought> merge(List<SubThought> sync, List<SubThought> async) {
        Map<String, SubThought> byText = new LinkedHashMap<>();
        for (var st : sync) {
            byText.put(normalizeText(st.text()), st);
        }
        for (var st : async) {
            byText.put(normalizeText(st.text()), st);
        }
        return List.copyOf(byText.values());
    }

    public static List<SubThought> ofType(List<SubThought> subThoughts, String type) {
        return subThoughts.stream().filter(st -> type.equals(st.type())).toList();
    }

    public static List<SubThought> forEntity(List<SubThought> subThoughts, String entity) {
        return subThoughts.stream().filter(st -> entity.equals(st.entity())).toList();
    }

    private static String normalizeText(String text) {
        return text.strip().replaceAll("\\s+", " ").toLowerCase();
    }
}

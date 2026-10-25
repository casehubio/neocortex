package io.casehub.neocortex.caps.llm.eval;

import io.casehub.yaml.jackson.YamlMappers;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class EvalCharacterLoader {

    private EvalCharacterLoader() {}

    static Map<String, EvalCharacterData> load() {
        return load("eval-characters.yaml");
    }

    static Map<String, EvalCharacterData> loadGeneric() {
        return load("eval-characters-generic.yaml");
    }

    @SuppressWarnings("unchecked")
    static Map<String, EvalCharacterData> load(String resourceName) {
        InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resourceName);
        if (is == null) {
            throw new IllegalStateException("Eval character data not found: " + resourceName);
        }

        try (is) {
            Map<String, Map<String, Object>> raw = YamlMappers.create().readValue(is, Map.class);
            var result = new HashMap<String, EvalCharacterData>();
            for (var entry : raw.entrySet()) {
                result.put(entry.getKey(), parseCharacter(entry.getValue()));
            }
            return Collections.unmodifiableMap(result);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse eval character data: " + resourceName, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static EvalCharacterData parseCharacter(Map<String, Object> raw) {
        var drives = raw.containsKey("drives")
                ? ((List<Map<String, Object>>) raw.get("drives")).stream()
                        .map(m -> new EvalCharacterData.Drive(
                                (String) m.get("type"),
                                ((Number) m.get("intensity")).doubleValue(),
                                (String) m.get("description"),
                                (String) m.get("context")))
                        .toList()
                : List.<EvalCharacterData.Drive>of();

        var memories = raw.containsKey("formation-memories")
                ? ((List<Map<String, Object>>) raw.get("formation-memories")).stream()
                        .map(m -> {
                            var annotations = List.<EvalCharacterData.DriveAnnotation>of();
                            if (m.containsKey("serves")) {
                                String driveType = (String) m.get("serves");
                                String episode = (String) m.get("episode");
                                annotations = List.of(new EvalCharacterData.DriveAnnotation(
                                        driveType, "strong", episode, 0, episode.length()));
                            }
                            return new EvalCharacterData.FormationMemory(
                                    ((Number) m.get("age")).intValue(),
                                    (String) m.get("episode"),
                                    ((Number) m.getOrDefault("pleasure", 0.0)).doubleValue(),
                                    ((Number) m.getOrDefault("arousal", 0.0)).doubleValue(),
                                    ((Number) m.getOrDefault("dominance", 0.0)).doubleValue(),
                                    annotations);
                        })
                        .toList()
                : List.<EvalCharacterData.FormationMemory>of();

        return new EvalCharacterData(drives, memories);
    }
}

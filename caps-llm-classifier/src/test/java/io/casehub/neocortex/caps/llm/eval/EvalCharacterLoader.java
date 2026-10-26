package io.casehub.neocortex.caps.llm.eval;

import io.casehub.yaml.jackson.YamlMappers;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class EvalCharacterLoader {
    private static final Pattern INLINE_ANNOTATION =
            Pattern.compile("\\{\\{serves:\\s*([\\w-]+)\\|(\\w+):\\s*(.*?)\\}\\}");


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
                                                                                        String episode = (String) m.get("episode");
                                                                                        String serves  = (String) m.get("serves");
                                                                                        return new EvalCharacterData.FormationMemory(
                                                                                                ((Number) m.get("age")).intValue(),
                                                                                                episode,
                                                                                                ((Number) m.getOrDefault("pleasure", 0.0)).doubleValue(),
                                                                                                ((Number) m.getOrDefault("arousal", 0.0)).doubleValue(),
                                                                                                ((Number) m.getOrDefault("dominance", 0.0)).doubleValue(),
                                                                                                parseAnnotations(serves, episode));
                                                                                    })
                                                                                    .toList()
                       : List.<EvalCharacterData.FormationMemory>of();

        var tendencies = raw.containsKey("tendencies")
                         ? ((List<String>) raw.get("tendencies"))
                         : List.<String>of();

        var norms = raw.containsKey("norms")
                    ? ((List<Map<String, Object>>) raw.get("norms")).stream()
                            .map(m -> new EvalCharacterData.NormEntry(
                                    (String) m.get("rule"),
                                    ((Number) m.get("priority")).intValue()))
                            .toList()
                    : List.<EvalCharacterData.NormEntry>of();

        var beliefs = raw.containsKey("initial-beliefs")
                      ? ((List<Map<String, Object>>) raw.get("initial-beliefs")).stream()
                              .map(m -> new EvalCharacterData.InitialBelief(
                                      (String) m.get("key"), (String) m.get("value")))
                              .toList()
                      : List.<EvalCharacterData.InitialBelief>of();

        var facets = raw.containsKey("personality-facets")
                     ? ((List<Map<String, Object>>) raw.get("personality-facets")).stream()
                             .map(m -> new EvalCharacterData.PersonalityFacet(
                                     (String) m.get("facet"),
                                     ((Number) m.get("score")).intValue(),
                                     (String) m.get("origin")))
                             .toList()
                     : List.<EvalCharacterData.PersonalityFacet>of();

        var schemas = raw.containsKey("relational-schemas")
                      ? ((List<Map<String, Object>>) raw.get("relational-schemas")).stream()
                              .map(m -> new EvalCharacterData.RelationalSchema(
                                      (String) m.get("target"), (String) m.get("role"),
                                      ((Number) m.get("trust")).doubleValue(),
                                      ((Number) m.get("intimacy")).doubleValue(),
                                      ((Number) m.get("utility")).doubleValue(),
                                      (String) m.get("description")))
                              .toList()
                      : List.<EvalCharacterData.RelationalSchema>of();

        EvalCharacterData.AttachmentStyle attachment = null;
        if (raw.containsKey("attachment")) {
            var a = (Map<String, Object>) raw.get("attachment");
            attachment = new EvalCharacterData.AttachmentStyle(
                    (String) a.get("style"),
                    ((Number) a.get("anxiety")).doubleValue(),
                    ((Number) a.get("avoidance")).doubleValue());
        }

        var substrate = (String) raw.get("psychological-substrate");

        return new EvalCharacterData(drives, memories, tendencies, norms, beliefs,
                facets, schemas, attachment, substrate);
    }

    private static List<EvalCharacterData.DriveAnnotation> parseAnnotations(String serves, String episode) {
        var annotations = new ArrayList<EvalCharacterData.DriveAnnotation>();
        if (serves != null) {
            var parts  = serves.split("\\|", 2);
            var drive  = parts[0].strip();
            var degree = parts.length > 1 ? parts[1].strip() : "strong";
            annotations.add(new EvalCharacterData.DriveAnnotation(drive, degree, episode, 0, episode.length()));
        }
        var matcher = INLINE_ANNOTATION.matcher(episode);
        while (matcher.find()) {
            annotations.add(new EvalCharacterData.DriveAnnotation(
                    matcher.group(1), matcher.group(2), matcher.group(3).strip(),
                    matcher.start(), matcher.end()));
        }
        return List.copyOf(annotations);
    }

}

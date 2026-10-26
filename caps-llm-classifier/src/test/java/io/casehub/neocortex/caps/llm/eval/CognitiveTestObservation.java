package io.casehub.neocortex.caps.llm.eval;

import io.casehub.neocortex.cognition.prompt.observation.CognitionObservationBuilder;
import io.casehub.neocortex.cognition.prompt.observation.DriveSnapshot;
import io.casehub.neocortex.cognition.prompt.observation.ObservationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Assembles observation text from {@link EvalCharacterData}, replicating the
 * section ordering of CharacterCognition.renderCognitiveSections() without
 * any manor dependency.
 *
 * <p>Lazarus (1991) appraisal process flow determines section order:
 * tendencies → beliefs → norms → personality → drives → memories → substrate → schemas → attachment.
 */
final class CognitiveTestObservation {

    private static final Pattern INLINE_ANNOTATION =
            Pattern.compile("\\{\\{serves:\\s*[\\w-]+\\|\\w+:\\s*|\\}\\}");

    private CognitiveTestObservation() {}

    static String render(String agentId, EvalCharacterData data,
                         Collection<String> nearbyAgentIds, Map<String, String> agentNames) {
        return render(agentId, data, nearbyAgentIds, agentNames, null);
    }

    static String render(String agentId, EvalCharacterData data,
                         Collection<String> nearbyAgentIds, Map<String, String> agentNames,
                         CognitionObservationBuilder builder) {
        return render(agentId, data, nearbyAgentIds, agentNames, builder, null);
    }

    static String render(String agentId, EvalCharacterData data,
                         Collection<String> nearbyAgentIds, Map<String, String> agentNames,
                         CognitionObservationBuilder builder, String situationText) {
        var sections = new ArrayList<ObservationSection>();

        if (!data.tendencies().isEmpty()) {
            sections.add(ObservationSection.items("Your Behavioral Tendencies", null, data.tendencies()));
        }

        if (!data.initialBeliefs().isEmpty()) {
            var items = data.initialBeliefs().stream()
                    .map(EvalCharacterData.InitialBelief::value)
                    .toList();
            sections.add(ObservationSection.items("Your Beliefs", null, items));
        }

        if (!data.norms().isEmpty()) {
            var items = data.norms().stream()
                    .map(EvalCharacterData.NormEntry::rule)
                    .toList();
            sections.add(ObservationSection.items("Social Rules", null, items));
        }

        if (!data.personalityFacets().isEmpty()) {
            var sb = new StringBuilder();
            for (var facet : data.personalityFacets()) {
                if (!facet.origin().isEmpty()) {
                    sb.append(facet.origin()).append(" ");
                }
            }
            sections.add(ObservationSection.text("Who You Are", sb.toString().strip()));
        }

        if (builder != null) {
            for (var s : builder.renderTheoryOfMind(nearbyAgentIds, agentNames)) {
                sections.add(s);
            }

            var situationContext = (situationText != null && !situationText.isBlank())
                    ? builder.classifySituation(situationText, Map.of())
                    : io.casehub.neocortex.caps.SituationContext.EMPTY;
            var activatedCategories = List.<String>of();
            var internalState = builder.renderInternalState(
                    activatedCategories, situationText, nearbyAgentIds, situationContext);
            if (internalState != null) {
                sections.add(internalState);
            }
        } else {
            var driveDescriptions = data.drives().stream()
                    .filter(d -> d.intensity() > 0.2 && !d.description().isBlank())
                    .sorted(Comparator.comparingDouble(EvalCharacterData.Drive::intensity).reversed())
                    .map(EvalCharacterData.Drive::description)
                    .toList();
            if (!driveDescriptions.isEmpty()) {
                sections.add(ObservationSection.text("What Pulls at You",
                        String.join("\n\n", driveDescriptions)));
            }
        }

        if (!data.formationMemories().isEmpty()) {
            var sb = new StringBuilder();
            sb.append("These are the experiences that made you who you are.\n\n");
            var sorted = data.formationMemories().stream()
                    .sorted(Comparator.comparingDouble(
                            (EvalCharacterData.FormationMemory m) -> Math.abs(m.pleasure()) + m.arousal()).reversed())
                    .toList();
            for (var mem : sorted) {
                sb.append("**Age ").append(mem.age()).append(":** ")
                        .append(stripAnnotations(mem.episode())).append("\n\n");
            }
            sections.add(ObservationSection.text("Where You Come From", sb.toString().strip()));
        }

        if (data.substrate() != null && !data.substrate().isBlank()) {
            sections.add(ObservationSection.text("What Shaped You", data.substrate()));
        }

        if (!data.relationalSchemas().isEmpty() && !nearbyAgentIds.isEmpty()) {
            var relevant = data.relationalSchemas().stream()
                    .filter(rs -> nearbyAgentIds.contains(rs.target()))
                    .toList();
            if (!relevant.isEmpty()) {
                var items = new ArrayList<String>();
                for (var rs : relevant) {
                    String name = agentNames.getOrDefault(rs.target(), rs.target());
                    items.add(name + " (" + rs.role() + "): " + rs.description());
                }
                sections.add(ObservationSection.items("How You See Others", null, items));
            }
        }

        if (data.attachment() != null) {
            var a = data.attachment();
            var desc = String.format("Attachment style: %s (anxiety: %.1f, avoidance: %.1f)",
                    a.style(), a.anxiety(), a.avoidance());
            sections.add(ObservationSection.text("How You Attach", desc));
        }

        if (builder != null) {
            for (var s : builder.renderCognitionCoreSections()) {
                sections.add(s);
            }
        }

        return renderSections(sections);
    }

    static List<DriveSnapshot> toDriveSnapshots(EvalCharacterData data) {
        return data.drives().stream()
                .map(d -> new DriveSnapshot(d.type(), d.intensity(), d.description(), d.context()))
                .toList();
    }

    static String renderSections(List<ObservationSection> sections) {
        var sb = new StringBuilder();
        for (var section : sections) {
            sb.append("== ").append(section.header()).append(" ==\n");
            switch (section) {
                case ObservationSection.TextBlock tb -> sb.append(tb.content()).append("\n");
                case ObservationSection.ItemList il -> {
                    for (var item : il.items()) sb.append("- ").append(item).append("\n");
                }
                case ObservationSection.EntityGroup eg -> {
                    for (var entity : eg.entities()) sb.append("- ").append(entity.displayName()).append("\n");
                }
                default -> {}
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static String stripAnnotations(String episode) {
        return INLINE_ANNOTATION.matcher(episode).replaceAll("").strip();
    }
}
